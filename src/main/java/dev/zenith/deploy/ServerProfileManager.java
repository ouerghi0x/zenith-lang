package dev.zenith.deploy;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ServerProfileManager {
    private static final Path SERVERS_FILE = Paths.get(".zenith", "servers.json");

    public static class ServerConfig {
        public String host;
        public int port = 22;
        public String user;
        public String auth = "key";
        public String keyPath;
        public String password;
        public String remoteDir;
    }

    public static Map<String, ServerConfig> loadServers() {
        Map<String, ServerConfig> map = new HashMap<>();
        if (!Files.exists(SERVERS_FILE)) {
            return map;
        }
        try {
            String json = Files.readString(SERVERS_FILE, StandardCharsets.UTF_8);
            // Simple robust JSON parsing for servers.json without external libs if needed, or regex/custom parse
            // Format: {"servers": {"name": {"host": "...", "port": 22, ...}}}
            // Let's implement a clean helper or standard property/json parsing
            int serversIdx = json.indexOf("\"servers\"");
            if (serversIdx == -1) return map;
            
            // For robust standalone usage, let's parse keys or use standard Java properties/json
            // Since we want zero fragile deps, let's write a clean parser or use simple scanner
            parseSimpleJsonServers(json, map);
        } catch (Exception e) {
            // Ignore parse errors
        }
        return map;
    }

    private static void parseSimpleJsonServers(String json, Map<String, ServerConfig> map) {
        // Basic parser for .zenith/servers.json
        try {
            int idx = json.indexOf("\"servers\"");
            if (idx == -1) return;
            int brace = json.indexOf("{", idx);
            if (brace == -1) return;
            // Extract top-level server entries
            // e.g. "production": { ... }
            String sub = json.substring(brace + 1);
            // Simple line-based or block parser
            String[] lines = sub.split("\n");
            String currentServer = null;
            ServerConfig currentConfig = null;
            for (String line : lines) {
                line = line.trim();
                if (line.endsWith(":{") || (line.startsWith("\"") && line.endsWith(":") ) || (line.startsWith("\"") && line.contains("\": {"))) {
                    if (currentServer != null && currentConfig != null) {
                        map.put(currentServer, currentConfig);
                    }
                    int quote1 = line.indexOf("\"");
                    int quote2 = line.indexOf("\"", quote1 + 1);
                    if (quote1 != -1 && quote2 != -1) {
                        currentServer = line.substring(quote1 + 1, quote2);
                    } else {
                        currentServer = line.replaceAll("[\":\\{]", "").trim();
                    }
                    currentConfig = new ServerConfig();
                } else if (currentConfig != null) {
                    if (line.contains("\"host\"")) {
                        currentConfig.host = extractJsonVal(line);
                    } else if (line.contains("\"port\"")) {
                        try { currentConfig.port = Integer.parseInt(extractJsonVal(line)); } catch (Exception ignored) {}
                    } else if (line.contains("\"user\"")) {
                        currentConfig.user = extractJsonVal(line);
                    } else if (line.contains("\"auth\"")) {
                        currentConfig.auth = extractJsonVal(line);
                    } else if (line.contains("\"keyPath\"")) {
                        currentConfig.keyPath = extractJsonVal(line);
                    } else if (line.contains("\"remoteDir\"")) {
                        currentConfig.remoteDir = extractJsonVal(line);
                    }
                }
            }
            if (currentServer != null && currentConfig != null) {
                map.put(currentServer, currentConfig);
            }
        } catch (Exception ignored) {}
    }

    private static String extractJsonVal(String line) {
        int colon = line.indexOf(":");
        if (colon == -1) return "";
        String val = line.substring(colon + 1).trim();
        if (val.endsWith(",")) val = val.substring(0, val.length() - 1).trim();
        if (val.startsWith("\"") && val.endsWith("\"")) {
            val = val.substring(1, val.length() - 1);
        }
        return val;
    }

    public static void saveServer(String name, ServerConfig config) {
        Map<String, ServerConfig> servers = loadServers();
        servers.put(name, config);

        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"servers\": {\n");
        int i = 0;
        for (Map.Entry<String, ServerConfig> entry : servers.entrySet()) {
            ServerConfig sc = entry.getValue();
            sb.append("    \"").append(entry.getKey()).append("\": {\n");
            sb.append("      \"host\": \"").append(sc.host != null ? sc.host : "").append("\",\n");
            sb.append("      \"port\": ").append(sc.port).append(",\n");
            sb.append("      \"user\": \"").append(sc.user != null ? sc.user : "").append("\",\n");
            sb.append("      \"auth\": \"").append(sc.auth != null ? sc.auth : "key").append("\",\n");
            sb.append("      \"keyPath\": \"").append(sc.keyPath != null ? sc.keyPath : "").append("\",\n");
            sb.append("      \"remoteDir\": \"").append(sc.remoteDir != null ? sc.remoteDir : "").append("\"\n");
            sb.append("    }").append(++i < servers.size() ? "," : "").append("\n");
        }
        sb.append("  }\n}\n");

        try {
            Files.createDirectories(SERVERS_FILE.getParent());
            Files.writeString(SERVERS_FILE, sb.toString(), StandardCharsets.UTF_8);
            
            // Also ensure .gitignore includes .zenith/servers.json or .zenith/
            Path gitignore = Paths.get(".gitignore");
            if (Files.exists(gitignore)) {
                String gi = Files.readString(gitignore, StandardCharsets.UTF_8);
                if (!gi.contains(".zenith")) {
                    Files.writeString(gitignore, gi + "\n.zenith/\n", StandardCharsets.UTF_8);
                }
            } else {
                Files.writeString(gitignore, ".zenith/\n", StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save server profile: " + e.getMessage(), e);
        }
    }
}
