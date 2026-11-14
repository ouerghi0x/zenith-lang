package dev.zenith.generator;

import dev.zenith.ast.ZenithFile;
import dev.zenith.ast.StreamDef;
import dev.zenith.ast.StreamConsumerDef;

public class PropertiesGenerator {

    public static JavaSourceFile generate(ZenithFile file) {
        int port = file.getConfig().getPort();
        String db = file.getConfig().getDatabase();
        boolean flyway = file.getConfig().isFlywayEnabled();
        boolean swagger = file.getConfig().isSwaggerEnabled();
        boolean hasAuth = file.hasAuth();

        StringBuilder sb = new StringBuilder();
        sb.append("server:\n");
        sb.append("  port: ").append(port).append("\n\n");

        String appName = file.getConfig().getApp().toLowerCase().replaceAll("[^a-z0-9]", "");
        String defaultDbName = appName.isEmpty() ? "appdb" : appName + "_db";
        String customUrl = file.getConfig().getString("dbUrl", null);
        String customUser = file.getConfig().getString("dbUser", null);
        String customPass = file.getConfig().getString("dbPassword", null);

        sb.append("spring:\n");
        sb.append("  devtools:\n");
        sb.append("    restart:\n");
        sb.append("      trigger-file: .reloadtrigger\n");
        sb.append("  datasource:\n");
        if ("postgres".equalsIgnoreCase(db) || "postgresql".equalsIgnoreCase(db)) {
            String defaultUrl = "jdbc:postgresql://localhost:5432/" + defaultDbName;
            String url = customUrl != null ? customUrl : defaultUrl;
            String user = customUser != null ? customUser : "zenith";
            String pass = customPass != null ? customPass : "zenith_secret";

            sb.append("    url: ${SPRING_DATASOURCE_URL:").append(url).append("}\n");
            sb.append("    username: ${SPRING_DATASOURCE_USERNAME:").append(user).append("}\n");
            sb.append("    password: ${SPRING_DATASOURCE_PASSWORD:").append(pass).append("}\n");
            sb.append("    driver-class-name: org.postgresql.Driver\n");
        } else if ("mysql".equalsIgnoreCase(db)) {
            String defaultUrl = "jdbc:mysql://localhost:3306/" + defaultDbName
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            String url = customUrl != null ? customUrl : defaultUrl;
            String user = customUser != null ? customUser : "zenith";
            String pass = customPass != null ? customPass : "zenith_secret";

            sb.append("    url: ${SPRING_DATASOURCE_URL:").append(url).append("}\n");
            sb.append("    username: ${SPRING_DATASOURCE_USERNAME:").append(user).append("}\n");
            sb.append("    password: ${SPRING_DATASOURCE_PASSWORD:").append(pass).append("}\n");
            sb.append("    driver-class-name: com.mysql.cj.jdbc.Driver\n");
        } else {
            sb.append("    url: ${SPRING_DATASOURCE_URL:jdbc:h2:mem:").append(defaultDbName)
                    .append(";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=KEY,VALUE}\n");
            sb.append("    driver-class-name: org.h2.Driver\n");
            sb.append("    username: ${SPRING_DATASOURCE_USERNAME:sa}\n");
            sb.append("    password: ${SPRING_DATASOURCE_PASSWORD:\"\"}\n");
        }

        if (!"postgres".equalsIgnoreCase(db) && !"postgresql".equalsIgnoreCase(db) && !"mysql".equalsIgnoreCase(db)
                && !"mariadb".equalsIgnoreCase(db)) {
            sb.append("  h2:\n");
            sb.append("    console:\n");
            sb.append("      enabled: true\n");
            sb.append("      path: /h2-console\n");
        }

        if (flyway) {
            sb.append("  flyway:\n");
            sb.append("    enabled: true\n");
            sb.append("    baseline-on-migrate: true\n");
        }

        sb.append("  jpa:\n");
        sb.append("    hibernate:\n");
        if (flyway) {
            sb.append("      ddl-auto: validate\n");
        } else {
            sb.append("      ddl-auto: update\n");
        }
        sb.append("    show-sql: true\n");
        sb.append("    properties:\n");
        sb.append("      hibernate:\n");
        sb.append("        format_sql: true\n");
        if ("mysql".equalsIgnoreCase(db) || "mariadb".equalsIgnoreCase(db)) {
            sb.append("        dialect: org.hibernate.dialect.MySQLDialect\n");
        } else if ("postgres".equalsIgnoreCase(db) || "postgresql".equalsIgnoreCase(db)) {
            sb.append("        dialect: org.hibernate.dialect.PostgreSQLDialect\n");
        }
        if (file.hasCache()) {
            String provider = file.getConfig().getCacheProvider();
            boolean isRedis = "redis".equalsIgnoreCase(provider);
            String springCacheType = isRedis ? "redis" : "caffeine";

            sb.append("  cache:\n");
            sb.append("    type: ").append(springCacheType).append("\n");

            if (isRedis) {
                sb.append("  data:\n");
                sb.append("    redis:\n");
                sb.append("      host: ${REDIS_HOST:localhost}\n");
                sb.append("      port: ${REDIS_PORT:6379}\n");
                sb.append("      timeout: 2000ms\n");
            }
        }

        if (file.hasMails()) {
            String mailFrom = file.getConfig().getMailFrom();
            String mailHost = file.getConfig().getMailHost();
            int mailPort = file.getConfig().getMailPort();
            String mailUser = file.getConfig().getMailUsername();
            String mailPass = file.getConfig().getMailPassword();

            sb.append("  mail:\n");
            sb.append("    host: ${SPRING_MAIL_HOST:").append(mailHost).append("}\n");
            sb.append("    port: ${SPRING_MAIL_PORT:").append(mailPort).append("}\n");
            sb.append("    username: ${SPRING_MAIL_USERNAME:").append(mailUser).append("}\n");
            if (mailPass != null && !mailPass.isEmpty()) {
                String cleanPass = mailPass.trim();
                if ((cleanPass.startsWith("\"") && cleanPass.endsWith("\"")) || (cleanPass.startsWith("'") && cleanPass.endsWith("'"))) {
                    cleanPass = cleanPass.substring(1, cleanPass.length() - 1);
                }
                sb.append("    password: ${SPRING_MAIL_PASSWORD:").append(cleanPass).append("}\n");
            } else {
                sb.append("    password: ${SPRING_MAIL_PASSWORD:}\n");
            }
            sb.append("    from: ${SPRING_MAIL_FROM:").append(mailFrom).append("}\n");
            sb.append("    properties:\n");
            sb.append("      mail:\n");
            sb.append("        smtp:\n");
            sb.append("          auth: ${SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH:true}\n");
            sb.append("          starttls:\n");
            sb.append("            enable: ${SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE:true}\n");
        }
        sb.append("\n");

        if (file.getConfig().isOutboxEnabled()) {
            sb.append("  modulith:\n");
            sb.append("    events:\n");
            sb.append("      registry-trigger-annotation: org.springframework.modulith.events.ApplicationModuleListener\n");
            sb.append("      jdbc:\n");
            sb.append("        schema-initialization:\n");
            sb.append("          enabled: true\n");
            sb.append("      republish-outstanding-events-on-restart: true\n\n");
        }

        if (file.hasStreamIntegration()) {
            String broker = !file.getStreams().isEmpty() && file.getStreams().get(0).getBroker() != null ? file.getStreams().get(0).getBroker() : file.getConfig().getBroker();
            if (broker == null) broker = file.getConfig().getBroker();
            sb.append("  cloud:\n");
            if (file.hasConsumers()) {
                sb.append("    function:\n      definition: ");
                for (int i = 0; i < file.getConsumers().size(); i++) {
                    if (i > 0) sb.append(";");
                    sb.append(functionName(file.getConsumers().get(i)));
                }
                sb.append("\n");
            }
            sb.append("    stream:\n      bindings:\n");
            for (StreamDef stream : file.getStreams()) {
                String binding = Character.toLowerCase(stream.getName().charAt(0)) + stream.getName().substring(1) + "-out-0";
                sb.append("        ").append(binding).append(":\n          destination: ").append(stream.getTopic()).append("\n          content-type: application/json\n          producer:\n");
                if (stream.getPartitions() != null) sb.append("            partition-count: ").append(stream.getPartitions()).append("\n");
            }
            for (StreamConsumerDef consumer : file.getConsumers()) {
                StreamDef stream = file.getStreams().stream().filter(s -> s.getName().equalsIgnoreCase(consumer.getStream())).findFirst().orElse(null);
                String binding = functionName(consumer) + "-in-0";
                sb.append("        ").append(binding).append(":\n          destination: ").append(stream.getTopic()).append("\n          content-type: application/json\n          group: ").append(consumer.getGroup()).append("\n          consumer:\n            max-attempts: ").append(consumer.getMaxAttempts()).append("\n            back-off-initial-interval: ").append(consumer.getBackoffMs()).append("\n            back-off-multiplier: ").append(consumer.getBackoffMultiplier()).append("\n            back-off-max-interval: ").append(consumer.getMaxBackoffMs()).append("\n");
                if (consumer.getConcurrency() != null) sb.append("            concurrency: ").append(consumer.getConcurrency()).append("\n");
            }
            if (file.getConsumers().stream().anyMatch(c -> c.getDeadLetter() != null)) {
                if ("kafka".equalsIgnoreCase(broker)) {
                    sb.append("      kafka:\n");
                    sb.append("        binder:\n          brokers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}\n        bindings:\n");
                    for (StreamConsumerDef consumer : file.getConsumers()) if (consumer.getDeadLetter() != null) {
                        sb.append("          ").append(functionName(consumer)).append("-in-0:\n            consumer:\n              enable-dlq: true\n              dlq-name: ").append(consumer.getDeadLetter()).append("\n");
                    }
                } else {
                    sb.append("      rabbit:\n");
                    sb.append("        binder:\n          nodes: ${RABBITMQ_HOST:localhost}\n          username: ${RABBITMQ_USERNAME:guest}\n          password: ${RABBITMQ_PASSWORD:guest}\n        bindings:\n");
                    for (StreamConsumerDef consumer : file.getConsumers()) if (consumer.getDeadLetter() != null) {
                        sb.append("          ").append(functionName(consumer)).append("-in-0:\n            consumer:\n              auto-bind-dlq: true\n              dead-letter-queue-name: ").append(consumer.getDeadLetter()).append("\n");
                    }
                }
            }
            if (file.getConsumers().stream().noneMatch(c -> c.getDeadLetter() != null)) {
                if ("kafka".equalsIgnoreCase(broker)) sb.append("      kafka:\n        binder:\n          brokers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}\n");
                else sb.append("      rabbit:\n        binder:\n          nodes: ${RABBITMQ_HOST:localhost}\n          username: ${RABBITMQ_USERNAME:guest}\n          password: ${RABBITMQ_PASSWORD:guest}\n");
            }
            sb.append("\n");
        }

        if (swagger) {
            sb.append("springdoc:\n");
            sb.append("  swagger-ui:\n");
            sb.append("    path: /swagger-ui.html\n");
            sb.append("    operationsSorter: method\n");
            sb.append("  api-docs:\n");
            sb.append("    path: /v3/api-docs\n\n");
        }

        if (hasAuth) {
            sb.append("jwt:\n");
            sb.append("  secret: ${JWT_SECRET:\"").append(file.getConfig().getJwtSecret()).append("\"}\n");
            sb.append("  expiration: ${JWT_EXPIRATION_MS:").append(file.getConfig().getJwtExpirationMs())
                    .append("}\n\n");
        }

        if (file.hasStorage()) {
            String defProvider = file.getConfig().getStorageProvider();
            String defBucket = file.getConfig().getStorageBucket();
            sb.append("zenith:\n");
            sb.append("  storage:\n");
            sb.append("    provider: ${STORAGE_PROVIDER:").append(defProvider).append("}\n");
            sb.append("    bucket: ${STORAGE_BUCKET:").append(defBucket).append("}\n");
            sb.append("    minio:\n");
            sb.append("      endpoint: ${STORAGE_ENDPOINT:http://localhost:9000}\n");
            sb.append("      access-key: ${STORAGE_ACCESS_KEY:minioadmin}\n");
            sb.append("      secret-key: ${STORAGE_SECRET_KEY:minioadmin}\n");
            sb.append("    s3:\n");
            sb.append("      region: ${STORAGE_REGION:us-east-1}\n");
            sb.append("      access-key: ${STORAGE_ACCESS_KEY:}\n");
            sb.append("      secret-key: ${STORAGE_SECRET_KEY:}\n");
        }

        if (file.hasJobs()) {
            sb.append("\njobs:\n");
            for (dev.zenith.ast.JobDef j : file.getJobs()) {
                String kebab = j.getName().replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
                sb.append("  ").append(kebab).append(":\n");
                sb.append("    enabled: ").append(j.isEnabled()).append("\n");
                if (j.getCronExpression() != null && !j.getCronExpression().trim().isEmpty()
                        && !j.getCronExpression().equalsIgnoreCase("null")) {
                    sb.append("    cron: \"").append(j.getCronExpression()).append("\"\n");
                }
                if (j.getFixedRate() != null && !j.getFixedRate().trim().isEmpty()
                        && !j.getFixedRate().equalsIgnoreCase("null")) {
                    sb.append("    fixed-rate: ").append(j.getFixedRate()).append("\n");
                }
            }
        }

        if (file.hasMails()) {
            String provider = file.getConfig().getMailProvider();
            sb.append("\nzenith:\n");
            sb.append("  mail:\n");
            sb.append("    provider: ${ZENITH_MAIL_PROVIDER:").append(provider).append("}\n");
        }

        return new JavaSourceFile("src/main/resources/application.yml", sb.toString());
    }

    private static String functionName(StreamConsumerDef consumer) {
        return "consume" + consumer.getName();
    }
}
