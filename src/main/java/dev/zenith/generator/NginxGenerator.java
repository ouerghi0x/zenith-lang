package dev.zenith.generator;

import dev.zenith.ast.AppConfig;
import dev.zenith.ast.ZenithFile;

/**
 * Generates production-tuned Nginx reverse proxy configuration (`nginx/nginx.conf`).
 */
public class NginxGenerator {

    public static JavaSourceFile generate(ZenithFile file) {
        AppConfig config = file.getConfig();
        int replicas = config.getProxyReplicas();
        if (replicas < 1) replicas = 2;
        String appName = config.getApp().toLowerCase().replaceAll("[^a-z0-9]", "");

        StringBuilder sb = new StringBuilder();
        sb.append("user nginx;\n");
        sb.append("worker_processes auto;\n");
        sb.append("error_log /var/log/nginx/error.log warn;\n");
        sb.append("pid /var/run/nginx.pid;\n\n");
        sb.append("events {\n");
        sb.append("    worker_connections 1024;\n");
        sb.append("}\n\n");
        sb.append("http {\n");
        sb.append("    include /etc/nginx/mime.types;\n");
        sb.append("    default_type application/octet-stream;\n\n");
        sb.append("    log_format main '$remote_addr - $remote_user [$time_local] \"$request\" '\n");
        sb.append("                    '$status $body_bytes_sent \"$http_referer\" '\n");
        sb.append("                    '\"$http_user_agent\" \"$http_x_forwarded_for\"';\n\n");
        sb.append("    access_log /var/log/nginx/access.log main;\n\n");
        sb.append("    sendfile on;\n");
        sb.append("    tcp_nopush on;\n");
        sb.append("    keepalive_timeout 65;\n");
        sb.append("    client_max_body_size 50M;\n\n");
        sb.append("    # Gzip Compression\n");
        sb.append("    gzip on;\n");
        sb.append("    gzip_vary on;\n");
        sb.append("    gzip_proxied any;\n");
        sb.append("    gzip_comp_level 6;\n");
        sb.append("    gzip_buffers 16 8k;\n");
        sb.append("    gzip_http_version 1.1;\n");
        sb.append("    gzip_types application/json application/javascript text/css text/plain application/xml text/javascript;\n\n");
        sb.append("    upstream backend_servers {\n");
        sb.append("        least_conn;\n");
        for (int i = 1; i <= replicas; i++) {
            sb.append("        server ").append(appName).append("-").append(i).append(":8080 max_fails=3 fail_timeout=10s;\n");
        }
        sb.append("    }\n\n");
        sb.append("    server {\n");
        sb.append("        listen 80;\n");
        sb.append("        server_name localhost;\n\n");
        sb.append("        location / {\n");
        sb.append("            proxy_pass http://backend_servers;\n");
        sb.append("            proxy_http_version 1.1;\n");
        sb.append("            proxy_set_header Upgrade $http_upgrade;\n");
        sb.append("            proxy_set_header Connection \"upgrade\";\n");
        sb.append("            proxy_set_header Host $host;\n");
        sb.append("            proxy_set_header X-Real-IP $remote_addr;\n");
        sb.append("            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;\n");
        sb.append("            proxy_set_header X-Forwarded-Proto $scheme;\n");
        sb.append("            proxy_set_header X-Forwarded-Port $server_port;\n");
        sb.append("            proxy_buffering on;\n");
        sb.append("            proxy_buffer_size 16k;\n");
        sb.append("            proxy_buffers 8 16k;\n");
        sb.append("            proxy_connect_timeout 60s;\n");
        sb.append("            proxy_send_timeout 60s;\n");
        sb.append("            proxy_read_timeout 60s;\n");
        sb.append("        }\n\n");
        sb.append("        # Actuator health check passthrough\n");
        sb.append("        location /actuator/health {\n");
        sb.append("            proxy_pass http://backend_servers/actuator/health;\n");
        sb.append("            proxy_http_version 1.1;\n");
        sb.append("            proxy_set_header Host $host;\n");
        sb.append("            proxy_set_header X-Real-IP $remote_addr;\n");
        sb.append("            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;\n");
        sb.append("            proxy_set_header X-Forwarded-Proto $scheme;\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");

        return new JavaSourceFile("nginx/nginx.conf", sb.toString());
    }
}
