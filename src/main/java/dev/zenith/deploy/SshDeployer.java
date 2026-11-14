package dev.zenith.deploy;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class SshDeployer {

    public static boolean testConnection(String serverName) {
        Map<String, ServerProfileManager.ServerConfig> servers = ServerProfileManager.loadServers();
        ServerProfileManager.ServerConfig cfg = servers.get(serverName);
        if (cfg == null) {
            System.err.println(
                    "[ERROR] Server profile '" + serverName + "' not found. Register it first using 'zenith server add "
                            + serverName + " --host <ip> --user <user>'.");
            return false;
        }

        System.out.println(">> Testing SSH connection to " + cfg.user + "@" + cfg.host + ":" + cfg.port + "...");

        // Execute system ssh test command or native client check
        boolean success = executeSshCommand(cfg,
                "echo 'SSH Connection Successful' && docker --version && docker compose version");
        if (success) {
            System.out.println(">> Server '" + serverName
                    + "' is fully online, reachable, and has Docker & Docker Compose installed!");
        } else {
            System.err.println(
                    ">> [WARNING] SSH connection test returned non-zero exit code. Verify host, user, and SSH key permissions.");
        }
        return success;
    }

    public static boolean deployProject(String serverName, String appDir) {
        Map<String, ServerProfileManager.ServerConfig> servers = ServerProfileManager.loadServers();
        ServerProfileManager.ServerConfig cfg = servers.get(serverName);
        if (cfg == null) {
            System.err.println("[ERROR] Server profile '" + serverName + "' not found.");
            return false;
        }

        String remoteDir = cfg.remoteDir != null && !cfg.remoteDir.isEmpty() ? cfg.remoteDir : "~/.zenith/apps/app";
        System.out.println(
                ">> Deploying project from " + appDir + " to " + cfg.user + "@" + cfg.host + ":" + remoteDir + "...");

        // 1. Create remote directory
        boolean mkdirs = executeSshCommand(cfg, "mkdir -p " + remoteDir);
        if (!mkdirs) {
            System.err.println("[ERROR] Failed to create remote directory " + remoteDir);
            return false;
        }

        // 2. Transfer files via scp
        boolean transferred = executeScpUpload(cfg, appDir, remoteDir);
        if (!transferred) {
            System.err.println("[ERROR] File transfer (SCP) failed.");
            return false;
        }

        // 3. Execute remote docker compose up
        System.out.println(">> Building and starting containers on remote server...");
        boolean booted = executeSshCommand(cfg,
                "cd " + remoteDir + " && docker compose pull || true && docker compose up -d --build --remove-orphans");
        if (booted) {
            System.out.println(">> Deployment successful! All containers are running on server '" + serverName + "'.");
        } else {
            System.err.println("[ERROR] Remote docker compose up failed.");
        }
        return booted;
    }

    public static void streamLogs(String serverName, boolean follow) {
        Map<String, ServerProfileManager.ServerConfig> servers = ServerProfileManager.loadServers();
        ServerProfileManager.ServerConfig cfg = servers.get(serverName);
        if (cfg == null) {
            System.err.println("[ERROR] Server profile '" + serverName + "' not found.");
            return;
        }
        String remoteDir = cfg.remoteDir != null && !cfg.remoteDir.isEmpty() ? cfg.remoteDir : "~/.zenith/apps/app";
        String cmd = "cd " + remoteDir + " && docker compose logs" + (follow ? " -f" : "");
        executeSshCommand(cfg, cmd);
    }

    public static void checkStatus(String serverName) {
        Map<String, ServerProfileManager.ServerConfig> servers = ServerProfileManager.loadServers();
        ServerProfileManager.ServerConfig cfg = servers.get(serverName);
        if (cfg == null) {
            System.err.println("[ERROR] Server profile '" + serverName + "' not found.");
            return;
        }
        String remoteDir = cfg.remoteDir != null && !cfg.remoteDir.isEmpty() ? cfg.remoteDir : "~/.zenith/apps/app";
        executeSshCommand(cfg, "cd " + remoteDir + " && docker compose ps");
    }

    public static void restartApp(String serverName) {
        Map<String, ServerProfileManager.ServerConfig> servers = ServerProfileManager.loadServers();
        ServerProfileManager.ServerConfig cfg = servers.get(serverName);
        if (cfg == null) {
            System.err.println("[ERROR] Server profile '" + serverName + "' not found.");
            return;
        }
        String remoteDir = cfg.remoteDir != null && !cfg.remoteDir.isEmpty() ? cfg.remoteDir : "~/.zenith/apps/app";
        executeSshCommand(cfg, "cd " + remoteDir + " && docker compose restart");
    }

    private static boolean executeSshCommand(ServerProfileManager.ServerConfig cfg, String remoteCmd) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("ssh");
            cmd.add("-o");
            cmd.add("StrictHostKeyChecking=no");
            if (cfg.keyPath != null && !cfg.keyPath.isEmpty()) {
                String keyPath = cfg.keyPath.replace("~", System.getProperty("user.home"));
                cmd.add("-i");
                cmd.add(keyPath);
            }
            if (cfg.port > 0 && cfg.port != 22) {
                cmd.add("-p");
                cmd.add(String.valueOf(cfg.port));
            }
            cmd.add(cfg.user + "@" + cfg.host);
            cmd.add(remoteCmd);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.inheritIO();
            Process p = pb.start();
            int exitCode = p.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            System.err.println("[ERROR] SSH execution error: " + e.getMessage());
            return false;
        }
    }

    private static boolean executeScpUpload(ServerProfileManager.ServerConfig cfg, String localDir, String remoteDir) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("scp");
            cmd.add("-r");
            cmd.add("-o");
            cmd.add("StrictHostKeyChecking=no");
            if (cfg.keyPath != null && !cfg.keyPath.isEmpty()) {
                String keyPath = cfg.keyPath.replace("~", System.getProperty("user.home"));
                cmd.add("-i");
                cmd.add(keyPath);
            }
            if (cfg.port > 0 && cfg.port != 22) {
                cmd.add("-P");
                cmd.add(String.valueOf(cfg.port));
            }
            // Upload contents of localDir (e.g. generated-app/*)
            cmd.add(localDir + File.separator + "*");
            cmd.add(cfg.user + "@" + cfg.host + ":" + remoteDir);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.inheritIO();
            Process p = pb.start();
            int exitCode = p.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            System.err.println("[ERROR] SCP upload error: " + e.getMessage());
            return false;
        }
    }
}
