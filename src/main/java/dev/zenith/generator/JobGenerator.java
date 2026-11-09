package dev.zenith.generator;

import dev.zenith.ast.*;

import java.util.*;

public class JobGenerator {

    public static List<JavaSourceFile> generate(ZenithFile file) {
        List<JavaSourceFile> list = new ArrayList<>();
        if (file.getJobs().isEmpty()) {
            return list;
        }

        String basePkg = file.getConfig().getPackage();
        String pkgDir = "src/main/java/" + basePkg.replace('.', '/');

        // 1. SchedulerConfig.java
        list.add(generateSchedulerConfig(basePkg, pkgDir));

        // 2. Individual Job classes
        for (JobDef job : file.getJobs()) {
            list.add(generateJobClass(job, file, basePkg, pkgDir));
        }

        return list;
    }

    private static JavaSourceFile generateSchedulerConfig(String basePkg, String pkgDir) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".config;\n\n");
        sb.append("import org.springframework.context.annotation.Bean;\n");
        sb.append("import org.springframework.context.annotation.Configuration;\n");
        sb.append("import org.springframework.scheduling.annotation.EnableAsync;\n");
        sb.append("import org.springframework.scheduling.annotation.EnableScheduling;\n");
        sb.append("import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;\n\n");
        sb.append("@Configuration\n");
        sb.append("@EnableScheduling\n");
        sb.append("@EnableAsync\n");
        sb.append("public class SchedulerConfig {\n\n");
        sb.append("    @Bean\n");
        sb.append("    public ThreadPoolTaskScheduler taskScheduler() {\n");
        sb.append("        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();\n");
        sb.append("        scheduler.setPoolSize(5);\n");
        sb.append("        scheduler.setThreadNamePrefix(\"zenith-job-\");\n");
        sb.append("        scheduler.initialize();\n");
        sb.append("        return scheduler;\n");
        sb.append("    }\n");
        sb.append("}\n");

        return new JavaSourceFile(pkgDir + "/config/SchedulerConfig.java", sb.toString());
    }

    private static JavaSourceFile generateJobClass(JobDef job, ZenithFile file, String basePkg, String pkgDir) {
        String jobClassName = job.getName().endsWith("Job") ? job.getName() : job.getName() + "Job";
        String kebabName = toKebabCase(job.getName());

        // Find which repositories to inject based on models referenced in statements
        Set<String> neededModels = new LinkedHashSet<>();
        String joinedActions = String.join(" ", job.getActionStatements());
        for (ModelDef model : file.getModels()) {
            if (joinedActions.contains(model.getName())) {
                neededModels.add(model.getName());
            }
        }

        boolean usesEmail = false;
        if (file.hasMails()) {
            for (String stmt : job.getActionStatements()) {
                if (stmt.trim().startsWith("send ") || stmt.trim().matches("^send\\s+[A-Za-z0-9_]+\\s*\\(.*")) {
                    usesEmail = true;
                    break;
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".job;\n\n");

        sb.append("import ").append(basePkg).append(".entity.*;\n");
        if (!file.getEnums().isEmpty()) {
            sb.append("import ").append(basePkg).append(".model.*;\n");
        }
        sb.append("import ").append(basePkg).append(".repository.*;\n");
        if (usesEmail) {
            sb.append("import ").append(basePkg).append(".service.EmailService;\n");
        }
        sb.append("import org.slf4j.Logger;\n");
        sb.append("import org.slf4j.LoggerFactory;\n");
        sb.append("import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;\n");
        sb.append("import org.springframework.scheduling.annotation.Scheduled;\n");
        sb.append("import org.springframework.stereotype.Component;\n");
        sb.append("import org.springframework.transaction.annotation.Transactional;\n");
        sb.append("import java.time.LocalDateTime;\n");
        sb.append("import java.time.LocalDate;\n\n");

        if (job.getDescription() != null && !job.getDescription().isEmpty()) {
            sb.append("/**\n");
            sb.append(" * ").append(job.getDescription()).append("\n");
            sb.append(" */\n");
        }
        sb.append("@Component\n");
        sb.append("@ConditionalOnProperty(name = \"jobs.").append(kebabName).append(".enabled\", havingValue = \"true\", matchIfMissing = ")
          .append(job.isEnabled()).append(")\n");
        sb.append("public class ").append(jobClassName).append(" {\n\n");

        sb.append("    private static final Logger log = LoggerFactory.getLogger(").append(jobClassName).append(".class);\n\n");

        // Repository dependencies
        for (String mName : neededModels) {
            String repoType = mName + "Repository";
            String repoField = Character.toLowerCase(mName.charAt(0)) + mName.substring(1) + "Repository";
            sb.append("    private final ").append(repoType).append(" ").append(repoField).append(";\n");
        }
        if (usesEmail) {
            sb.append("    private final EmailService emailService;\n");
        }
        if (!neededModels.isEmpty() || usesEmail) {
            sb.append("\n");
            sb.append("    public ").append(jobClassName).append("(");
            int idx = 0;
            for (String mName : neededModels) {
                if (idx > 0) sb.append(", ");
                String repoType = mName + "Repository";
                String repoField = Character.toLowerCase(mName.charAt(0)) + mName.substring(1) + "Repository";
                sb.append(repoType).append(" ").append(repoField);
                idx++;
            }
            if (usesEmail) {
                if (idx > 0) sb.append(", ");
                sb.append("EmailService emailService");
            }
            sb.append(") {\n");
            for (String mName : neededModels) {
                String repoField = Character.toLowerCase(mName.charAt(0)) + mName.substring(1) + "Repository";
                sb.append("        this.").append(repoField).append(" = ").append(repoField).append(";\n");
            }
            if (usesEmail) {
                sb.append("        this.emailService = emailService;\n");
            }
            sb.append("    }\n\n");
        }

        // Scheduled execute method
        if (job.getCronExpression() != null) {
            sb.append("    @Scheduled(cron = \"${jobs.").append(kebabName).append(".cron:").append(job.getCronExpression()).append("}\")\n");
        } else if (job.getFixedRate() != null) {
            sb.append("    @Scheduled(fixedRateString = \"${jobs.").append(kebabName).append(".fixed-rate:").append(job.getFixedRate()).append("}\")\n");
        }
        sb.append("    @Transactional\n");
        sb.append("    public void execute() {\n");
        sb.append("        log.info(\"Starting scheduled job: ").append(job.getName()).append("\");\n");

        // Action statements
        for (String stmt : job.getActionStatements()) {
            String transpiled = LogicTranspiler.transpileStatement(stmt, null, file);
            if (!transpiled.isEmpty()) {
                String[] lines = transpiled.split("\n");
                for (String l : lines) {
                    sb.append("        ").append(l).append("\n");
                }
            }
        }

        sb.append("        log.info(\"Completed scheduled job: ").append(job.getName()).append("\");\n");
        sb.append("    }\n");
        sb.append("}\n");

        return new JavaSourceFile(pkgDir + "/job/" + jobClassName + ".java", sb.toString());
    }

    private static String toKebabCase(String name) {
        if (name == null) return "job";
        return name.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }
}
