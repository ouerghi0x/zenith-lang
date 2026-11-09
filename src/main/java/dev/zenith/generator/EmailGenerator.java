package dev.zenith.generator;

import dev.zenith.ast.FieldDef;
import dev.zenith.ast.MailDef;
import dev.zenith.ast.ZenithFile;

import java.util.ArrayList;
import java.util.List;

/**
 * Generator for asynchronous Spring Boot transactional email services and
 * Thymeleaf HTML templates.
 */
public class EmailGenerator {

    public static List<JavaSourceFile> generate(ZenithFile file) {
        List<JavaSourceFile> list = new ArrayList<>();
        if (!file.hasMails()) {
            return list;
        }

        String basePkg = file.getConfig().getPackage();
        String pkgDir = "src/main/java/" + basePkg.replace('.', '/') + "/service/";

        // 1. EmailService.java interface
        list.add(generateEmailServiceInterface(file, basePkg, pkgDir));

        // 2. EmailServiceImpl.java implementation
        list.add(generateEmailServiceImpl(file, basePkg, pkgDir));

        // 3. Default Thymeleaf HTML templates in src/main/resources/templates/
        for (MailDef mail : file.getMails()) {
            list.add(generateEmailTemplate(mail, file));
        }

        return list;
    }

    private static JavaSourceFile generateEmailServiceInterface(ZenithFile file, String basePkg, String pkgDir) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".service;\n\n");
        sb.append("import ").append(basePkg).append(".entity.*;\n");
        sb.append("import ").append(basePkg).append(".dto.*;\n");
        if (!file.getEnums().isEmpty()) {
            sb.append("import ").append(basePkg).append(".model.*;\n");
        }
        sb.append("import java.util.concurrent.CompletableFuture;\n\n");

        sb.append("public interface EmailService {\n\n");

        for (MailDef mail : file.getMails()) {
            sb.append("    CompletableFuture<Void> send").append(mail.getName()).append("(");
            int idx = 0;
            for (FieldDef param : mail.getParameters()) {
                if (idx > 0) sb.append(", ");
                String pType = TypeHelper.toSimpleJavaType(param.getType());
                sb.append(pType).append(" ").append(param.getName());
                idx++;
            }
            sb.append(");\n");
        }

        sb.append("}\n");
        return new JavaSourceFile(pkgDir + "EmailService.java", sb.toString());
    }

    private static JavaSourceFile generateEmailServiceImpl(ZenithFile file, String basePkg, String pkgDir) {
        String provider = file.getConfig().getMailProvider();
        String defaultFrom = file.getConfig().getMailFrom();

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".service.impl;\n\n");
        sb.append("import ").append(basePkg).append(".service.EmailService;\n");
        sb.append("import ").append(basePkg).append(".entity.*;\n");
        sb.append("import ").append(basePkg).append(".dto.*;\n");
        if (!file.getEnums().isEmpty()) {
            sb.append("import ").append(basePkg).append(".model.*;\n");
        }
        sb.append("import org.slf4j.Logger;\n");
        sb.append("import org.slf4j.LoggerFactory;\n");
        sb.append("import org.springframework.beans.factory.annotation.Autowired;\n");
        sb.append("import org.springframework.beans.factory.annotation.Value;\n");
        sb.append("import org.springframework.mail.javamail.JavaMailSender;\n");
        sb.append("import org.springframework.mail.javamail.MimeMessageHelper;\n");
        sb.append("import org.springframework.scheduling.annotation.Async;\n");
        sb.append("import org.springframework.stereotype.Service;\n");
        sb.append("import org.thymeleaf.TemplateEngine;\n");
        sb.append("import org.thymeleaf.context.Context;\n\n");
        sb.append("import jakarta.mail.internet.MimeMessage;\n");
        sb.append("import java.nio.charset.StandardCharsets;\n");
        sb.append("import java.util.concurrent.CompletableFuture;\n\n");

        sb.append("@Service\n");
        sb.append("public class EmailServiceImpl implements EmailService {\n\n");
        sb.append("    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);\n\n");

        sb.append("    @Autowired(required = false)\n");
        sb.append("    private JavaMailSender mailSender;\n\n");

        sb.append("    @Autowired\n");
        sb.append("    private TemplateEngine templateEngine;\n\n");

        sb.append("    @Value(\"${spring.mail.from:").append(defaultFrom).append("}\")\n");
        sb.append("    private String defaultFromAddress;\n\n");

        sb.append("    @Value(\"${zenith.mail.provider:").append(provider).append("}\")\n");
        sb.append("    private String mailProvider;\n\n");

        for (MailDef mail : file.getMails()) {
            sb.append("    @Override\n");
            sb.append("    @Async\n");
            sb.append("    public CompletableFuture<Void> send").append(mail.getName()).append("(");
            int idx = 0;
            for (FieldDef param : mail.getParameters()) {
                if (idx > 0) sb.append(", ");
                String pType = TypeHelper.toSimpleJavaType(param.getType());
                sb.append(pType).append(" ").append(param.getName());
                idx++;
            }
            sb.append(") {\n");
            sb.append("        try {\n");

            // Evaluate recipient 'to'
            String toExpr = transpileMailFieldExpr(mail.getToExpression(), mail.getParameters());
            sb.append("            String recipientTo = ").append(toExpr).append(";\n");

            // Evaluate subject
            String subjectExpr = transpileMailFieldExpr(mail.getSubjectExpression(), mail.getParameters());
            sb.append("            String subject = ").append(subjectExpr).append(";\n");

            // From override
            String fromExpr = (mail.getFromOverride() != null && !mail.getFromOverride().trim().isEmpty())
                    ? "\"" + mail.getFromOverride().replace("\"", "\\\"") + "\""
                    : "defaultFromAddress";
            sb.append("            String fromAddress = ").append(fromExpr).append(";\n");

            // Prepare Thymeleaf Context
            sb.append("            Context context = new Context();\n");
            for (FieldDef param : mail.getParameters()) {
                sb.append("            context.setVariable(\"").append(param.getName()).append("\", ").append(param.getName()).append(");\n");
            }

            // Template rendering
            String tplPath = mail.getTemplatePath();
            if (tplPath.endsWith(".html")) {
                tplPath = tplPath.substring(0, tplPath.length() - 5);
            }
            sb.append("            String htmlBody = templateEngine.process(\"").append(tplPath).append("\", context);\n\n");

            // Console provider vs JavaMailSender
            sb.append("            if (\"console\".equalsIgnoreCase(mailProvider) || mailSender == null) {\n");
            sb.append("                log.info(\"\\n===================== [ZENITH EMAIL PREVIEW] =====================\\n\" +\n");
            sb.append("                         \"To:      \" + recipientTo + \"\\n\" +\n");
            sb.append("                         \"From:    \" + fromAddress + \"\\n\" +\n");
            sb.append("                         \"Subject: \" + subject + \"\\n\" +\n");
            sb.append("                         \"Body:\\n\" + htmlBody + \"\\n\" +\n");
            sb.append("                         \"==================================================================\");\n");
            sb.append("                return CompletableFuture.completedFuture(null);\n");
            sb.append("            }\n\n");

            // SMTP / Remote delivery
            sb.append("            MimeMessage message = mailSender.createMimeMessage();\n");
            sb.append("            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());\n");
            sb.append("            helper.setTo(recipientTo);\n");
            sb.append("            helper.setFrom(fromAddress);\n");
            sb.append("            helper.setSubject(subject);\n");
            sb.append("            helper.setText(htmlBody, true);\n");
            sb.append("            mailSender.send(message);\n");
            sb.append("            log.info(\"Dispatched transactional email '{}' to {}\", \"").append(mail.getName()).append("\", recipientTo);\n");
            sb.append("        } catch (Exception e) {\n");
            sb.append("            log.error(\"Failed to send transactional email '").append(mail.getName()).append("': \" + e.getMessage(), e);\n");
            sb.append("        }\n");
            sb.append("        return CompletableFuture.completedFuture(null);\n");
            sb.append("    }\n\n");
        }

        sb.append("}\n");
        return new JavaSourceFile(pkgDir + "impl/EmailServiceImpl.java", sb.toString());
    }

    private static JavaSourceFile generateEmailTemplate(MailDef mail, ZenithFile file) {
        String tplPath = mail.getTemplatePath();
        if (!tplPath.endsWith(".html")) {
            tplPath = tplPath + ".html";
        }
        String fullPath = "src/main/resources/templates/" + tplPath;

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html xmlns:th=\"http://www.thymeleaf.org\">\n");
        sb.append("<head>\n");
        sb.append("    <meta charset=\"UTF-8\">\n");
        sb.append("    <title th:text=\"${title}\">Notification</title>\n");
        sb.append("    <style>\n");
        sb.append("        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; color: #1e293b; padding: 24px; }\n");
        sb.append("        .card { max-width: 580px; margin: 0 auto; background: #ffffff; border-radius: 8px; border: 1px solid #e2e8f0; padding: 32px; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05); }\n");
        sb.append("        .header { border-bottom: 2px solid #6366f1; padding-bottom: 16px; margin-bottom: 24px; }\n");
        sb.append("        .header h1 { margin: 0; font-size: 20px; color: #0f172a; }\n");
        sb.append("        .content { font-size: 15px; line-height: 1.6; }\n");
        sb.append("        .footer { margin-top: 32px; padding-top: 16px; border-top: 1px solid #e2e8f0; font-size: 12px; color: #64748b; text-align: center; }\n");
        sb.append("    </style>\n");
        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append("    <div class=\"card\">\n");
        sb.append("        <div class=\"header\">\n");
        sb.append("            <h1>").append(mail.getName()).append("</h1>\n");
        sb.append("        </div>\n");
        sb.append("        <div class=\"content\">\n");
        for (FieldDef param : mail.getParameters()) {
            sb.append("            <p><strong>").append(param.getName()).append(":</strong> <span th:text=\"${").append(param.getName()).append("}\"></span></p>\n");
        }
        sb.append("        </div>\n");
        sb.append("        <div class=\"footer\">\n");
        sb.append("            <p>Sent automatically by ").append(file.getConfig().getApp()).append(" &bull; Zenith Transactional Email Service</p>\n");
        sb.append("        </div>\n");
        sb.append("    </div>\n");
        sb.append("</body>\n");
        sb.append("</html>\n");

        return new JavaSourceFile(fullPath, sb.toString());
    }

    private static String transpileMailFieldExpr(String expr, List<FieldDef> params) {
        if (expr == null || expr.trim().isEmpty()) return "\"\"";
        String s = expr.trim();
        // Translate customer.email or order.orderNumber or customer.getEmail()
        for (FieldDef p : params) {
            String pName = p.getName();
            // Match pName.prop
            java.util.regex.Pattern pat = java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(pName) + "\\.([a-zA-Z0-9_]+)(?!\\()\\b");
            java.util.regex.Matcher m = pat.matcher(s);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String prop = m.group(1);
                String cap = Character.toUpperCase(prop.charAt(0)) + prop.substring(1);
                m.appendReplacement(sb, pName + ".get" + cap + "()");
            }
            m.appendTail(sb);
            s = sb.toString();
        }
        return s;
    }
}

