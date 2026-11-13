package dev.zenith.studio;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Embedded Interactive Terminal Execution Manager (Spec #21).
 * Executes shell commands in workspace or generated Spring Boot directories,
 * captures streaming stdout/stderr with ANSI codes, and provides process cancellation.
 */
public class TerminalManager {

    private static final int MAX_BUFFER_LINES = 2000;
    private static final long PROCESS_TIMEOUT_MINUTES = 5;

    public static class TerminalLine {
        public final int index;
        public final long timestamp;
        public final String text;
        public final String type; // "prompt", "stdout", "system", "error", "exit"

        public TerminalLine(int index, long timestamp, String text, String type) {
            this.index = index;
            this.timestamp = timestamp;
            this.text = text;
            this.type = type;
        }

        public String toJson() {
            return "{\"index\":" + index + ",\"timestamp\":" + timestamp
                    + ",\"text\":\"" + escapeJson(text) + "\",\"type\":\"" + type + "\"}";
        }
    }

    private final Path workspaceDir;
    private final Path projectDir;
    private final List<TerminalLine> outputLines = new CopyOnWriteArrayList<>();
    private final List<String> commandHistory = new CopyOnWriteArrayList<>();
    private final AtomicInteger lineIndexCounter = new AtomicInteger(0);

    private Process activeProcess = null;
    private String activeCommand = null;
    private String activeWorkingDirKey = "workspace";
    private long activePid = 0;
    private long startTime = 0;
    private Integer lastExitCode = null;

    private final ScheduledExecutorService watchdogExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "zenith-terminal-watchdog");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> timeoutFuture = null;

    public TerminalManager(Path workspaceDir) {
        this.workspaceDir = workspaceDir != null ? workspaceDir.toAbsolutePath().normalize()
                : Path.of(".").toAbsolutePath().normalize();
        this.projectDir = this.workspaceDir.resolve("target").resolve("studio-run").toAbsolutePath().normalize();

        // Initial welcome message
        appendLine("Zenith Studio Embedded Terminal [Interactive Shell]", "system");
        appendLine("Working Directory: " + this.workspaceDir.toString(), "system");
        appendLine("Type commands like 'mvn test', 'git status', 'docker ps', or shell utilities.", "system");
        appendLine("", "system");
    }

    public synchronized boolean isRunning() {
        return activeProcess != null && activeProcess.isAlive();
    }

    public synchronized long getActivePid() {
        return isRunning() ? activePid : 0;
    }

    public synchronized String getActiveCommand() {
        return activeCommand;
    }

    public synchronized String getActiveWorkingDirKey() {
        return activeWorkingDirKey;
    }

    public synchronized Integer getLastExitCode() {
        return lastExitCode;
    }

    public Path getWorkspaceDir() {
        return workspaceDir;
    }

    public Path getProjectDir() {
        return projectDir;
    }

    public List<String> getCommandHistory() {
        return Collections.unmodifiableList(commandHistory);
    }

    /**
     * Executes a shell command asynchronously in the requested working directory.
     */
    public synchronized boolean executeCommand(String command, String workingDirKey) {
        if (command == null || command.trim().isEmpty()) {
            return false;
        }

        command = command.trim();

        if (isRunning()) {
            return false; // Process already executing
        }

        // Add to history (deduplicated against immediate previous)
        if (commandHistory.isEmpty() || !commandHistory.get(commandHistory.size() - 1).equals(command)) {
            commandHistory.add(command);
            if (commandHistory.size() > 100) {
                commandHistory.remove(0);
            }
        }

        this.activeCommand = command;
        this.activeWorkingDirKey = (workingDirKey != null && workingDirKey.equalsIgnoreCase("project")) ? "project" : "workspace";
        this.lastExitCode = null;
        this.startTime = System.currentTimeMillis();

        Path runDir = activeWorkingDirKey.equals("project") ? projectDir : workspaceDir;

        // Auto-create directory if missing (e.g. target/studio-run before first compilation)
        if (!Files.exists(runDir)) {
            try {
                Files.createDirectories(runDir);
            } catch (Exception ignored) {
            }
        }

        String promptPrefix = activeWorkingDirKey.equals("project") ? "target/studio-run ❯ " : "workspace ❯ ";
        appendLine(promptPrefix + command, "prompt");

        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        ProcessBuilder pb;
        if (isWindows) {
            pb = new ProcessBuilder("cmd.exe", "/c", command);
        } else {
            pb = new ProcessBuilder("/bin/sh", "-c", command);
        }

        pb.directory(runDir.toFile());
        pb.redirectErrorStream(true);

        try {
            activeProcess = pb.start();
            try {
                activePid = activeProcess.pid();
            } catch (Throwable t) {
                activePid = -1;
            }

            // Schedule watchdog to kill hanging commands after 5 minutes
            timeoutFuture = watchdogExecutor.schedule(() -> {
                synchronized (TerminalManager.this) {
                    if (isRunning()) {
                        appendLine("\n[Zenith Terminal] ⏱ Command timed out after " + PROCESS_TIMEOUT_MINUTES + " minutes. Terminating...", "error");
                        terminateActiveProcess();
                    }
                }
            }, PROCESS_TIMEOUT_MINUTES, TimeUnit.MINUTES);

            // Stream reader thread
            final Process p = activeProcess;
            Thread readerThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        appendLine(line, "stdout");
                    }
                } catch (Exception ignored) {
                }

                int code = -1;
                try {
                    code = p.waitFor();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                synchronized (TerminalManager.this) {
                    lastExitCode = code;
                    if (timeoutFuture != null) {
                        timeoutFuture.cancel(false);
                        timeoutFuture = null;
                    }
                    if (code == 0) {
                        appendLine("[Exited with code 0]", "exit");
                    } else if (code == 130) {
                        appendLine("[Process terminated by user (code 130)]", "exit");
                    } else {
                        appendLine("[Process exited with error code " + code + "]", "error");
                    }
                    activeProcess = null;
                }
            }, "zenith-terminal-reader");
            readerThread.setDaemon(true);
            readerThread.start();

            return true;
        } catch (Exception e) {
            appendLine("[Zenith Terminal] Failed to launch command: " + e.getMessage(), "error");
            activeProcess = null;
            lastExitCode = -1;
            return false;
        }
    }

    /**
     * Forcibly terminates the running terminal process and all descendant processes.
     */
    public synchronized boolean terminateActiveProcess() {
        if (!isRunning()) {
            return false;
        }

        appendLine("^C", "system");

        try {
            // Destroy descendants first (Java 9+)
            activeProcess.descendants().forEach(ph -> {
                try {
                    ph.destroyForcibly();
                } catch (Exception ignored) {
                }
            });
            activeProcess.destroyForcibly();

            // Extra kill for Windows processes (taskkill /F /T /PID)
            boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
            if (isWindows && activePid > 0) {
                try {
                    new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(activePid)).start().waitFor();
                } catch (Exception ignored) {
                }
            }

            if (timeoutFuture != null) {
                timeoutFuture.cancel(false);
                timeoutFuture = null;
            }

            lastExitCode = 130;
            appendLine("[Terminated by user]", "system");
            activeProcess = null;
            return true;
        } catch (Exception e) {
            appendLine("[Zenith Terminal] Error terminating process: " + e.getMessage(), "error");
            activeProcess = null;
            return false;
        }
    }

    public synchronized void clearOutput() {
        outputLines.clear();
        lineIndexCounter.set(0);
        appendLine("[Terminal buffer cleared]", "system");
    }

    private void appendLine(String text, String type) {
        int idx = lineIndexCounter.getAndIncrement();
        TerminalLine line = new TerminalLine(idx, System.currentTimeMillis(), text, type);
        outputLines.add(line);

        if (outputLines.size() > MAX_BUFFER_LINES) {
            // Remove first 200 lines to avoid frequent resizing
            int removeCount = Math.min(200, outputLines.size() - MAX_BUFFER_LINES + 50);
            for (int i = 0; i < removeCount && !outputLines.isEmpty(); i++) {
                outputLines.remove(0);
            }
        }
    }

    /**
     * Returns lines since the given index for incremental polling.
     */
    public synchronized List<TerminalLine> getLinesSince(int sinceIndex) {
        if (sinceIndex <= 0) {
            return new ArrayList<>(outputLines);
        }
        List<TerminalLine> sub = new ArrayList<>();
        for (TerminalLine l : outputLines) {
            if (l.index >= sinceIndex) {
                sub.add(l);
            }
        }
        return sub;
    }

    private static String escapeJson(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}

