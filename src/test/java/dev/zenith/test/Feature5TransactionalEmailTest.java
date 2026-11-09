package dev.zenith.test;

import dev.zenith.analyzer.AnalysisException;
import dev.zenith.ast.MailDef;
import dev.zenith.ast.ZenithFile;
import dev.zenith.generator.JavaSourceFile;
import dev.zenith.generator.ProjectGenerator;
import dev.zenith.parser.ParseException;

import java.util.List;

import static dev.zenith.test.TestHarness.*;

/**
 * Feature #5: Transactional Email Service (`mail`, `send`) Opaque-Box Tests.
 *
 * Tier 1: DSL Parsing & AST Modeling
 * Tier 2: Semantic Validation & Boundaries
 * Tier 3: Asynchronous Spring Mail & Thymeleaf Generator
 * Tier 4: Service & Action Block Integration
 */
public class Feature5TransactionalEmailTest {

    private static String source(List<JavaSourceFile> files, String suffix) {
        for (JavaSourceFile s : files) {
            if (s.getRelativePath().replace('\\', '/').endsWith(suffix)) {
                return s.getContent();
            }
        }
        throw new AssertionError("Generated file not found with suffix: " + suffix);
    }

    // =========================================================================
    // Tier 1: DSL Parsing & AST Modeling
    // =========================================================================

    public static void T1_01_parse_top_level_mail_declaration() {
        String dsl = """
            config {
                app: "MailApp"
                package: "com.example.mail"
                mail: smtp
                mailFrom: "orders@example.com"
            }
            model Order {
                id: UUID @id @auto
                orderNumber: String
            }
            model Customer {
                id: UUID @id @auto
                email: String @email
            }
            mail OrderConfirmation(order: Order, customer: Customer) {
                to: customer.email
                subject: "Your order #" + order.orderNumber + " is confirmed!"
                template: "emails/order-confirmation.html"
            }
            """;
        ZenithFile file = analyze(dsl);
        assertTrue(file.hasMails(), "Expected file to have mails");
        assertEquals(1, file.getMails().size(), "Expected 1 declared mail contract");

        MailDef mail = file.findMail("OrderConfirmation");
        assertNotNull(mail, "Expected OrderConfirmation mail contract");
        assertEquals(2, mail.getParameters().size(), "Expected 2 parameters");
        assertEquals("Order", mail.findParameter("order").getType().getName(), "param 1 type");
        assertEquals("Customer", mail.findParameter("customer").getType().getName(), "param 2 type");
        assertTrue(mail.getToExpression().contains("customer.email"), "Expected to expression");
        assertTrue(mail.getSubjectExpression().contains("confirmed"), "Expected subject expression");
        assertEquals("emails/order-confirmation.html", mail.getTemplatePath(), "template path");
    }

    public static void T1_02_parse_mail_with_from_override() {
        String dsl = """
            config {
                app: "MailApp"
                package: "com.example.mail"
            }
            mail AlertMail(recipientEmail: String) {
                to: recipientEmail
                subject: "Security Alert"
                template: "emails/alert.html"
                from: "security@company.com"
            }
            """;
        ZenithFile file = analyze(dsl);
        MailDef mail = file.findMail("AlertMail");
        assertNotNull(mail, "Expected AlertMail");
        assertEquals("security@company.com", mail.getFromOverride(), "from override");
    }

    public static void T1_03_parse_endpoint_send_action() {
        String dsl = """
            config {
                app: "MailApp"
                package: "com.example.mail"
            }
            model Order {
                id: UUID @id @auto
                endpoints {
                    post /checkout {
                        action {
                            send WelcomeNotice(recipient = "user@test.com")
                        }
                    }
                }
            }
            mail WelcomeNotice(recipient: String) {
                to: recipient
                subject: "Welcome!"
                template: "emails/welcome.html"
            }
            """;
        ZenithFile file = analyze(dsl);
        var order = file.findModel("Order");
        var cep = order.getCustomEndpoints().get(0);
        assertEquals(1, cep.getActionStatements().size(), "action statement count");
        assertTrue(cep.getActionStatements().get(0).startsWith("send WelcomeNotice"), "starts with send WelcomeNotice");
    }

    public static void T1_04_parse_job_send_action() {
        String dsl = """
            config {
                app: "MailApp"
                package: "com.example.mail"
            }
            mail DailyDigest(targetEmail: String) {
                to: targetEmail
                subject: "Daily Summary"
                template: "emails/digest.html"
            }
            job NightlyReport {
                cron: "0 0 0 * * ?"
                action {
                    send DailyDigest(targetEmail = "admin@example.com")
                }
            }
            """;
        ZenithFile file = analyze(dsl);
        assertEquals(1, file.getJobs().size(), "job count");
        var job = file.getJobs().get(0);
        assertTrue(job.getActionStatements().get(0).startsWith("send DailyDigest"), "job action starts with send DailyDigest");
    }

    // =========================================================================
    // Tier 2: Semantic Validation & Boundaries
    // =========================================================================

    public static void T2_01_reject_send_to_undeclared_mail() {
        String dsl = """
            config {
                app: "MailApp"
                package: "com.example.mail"
            }
            model Order {
                id: UUID @id @auto
                endpoints {
                    post /confirm {
                        action {
                            send UnknownMail(order = entity)
                        }
                    }
                }
            }
            """;
        try {
            analyze(dsl);
            throw new AssertionError("Expected AnalysisException for undeclared mail contract");
        } catch (AnalysisException ex) {
            assertContains(ex.getMessage(), "UnknownMail", "Error must name unknown mail contract");
        }
    }

    public static void T2_02_reject_send_missing_required_arguments() {
        String dsl = """
            config {
                app: "MailApp"
                package: "com.example.mail"
            }
            mail InvoiceMail(invoiceId: Long, customerEmail: String) {
                to: customerEmail
                subject: "Invoice"
                template: "emails/invoice.html"
            }
            model Invoice {
                id: UUID @id @auto
                endpoints {
                    post /send {
                        action {
                            send InvoiceMail(invoiceId = 123)
                        }
                    }
                }
            }
            """;
        try {
            analyze(dsl);
            throw new AssertionError("Expected AnalysisException for missing argument 'customerEmail'");
        } catch (AnalysisException ex) {
            assertContains(ex.getMessage(), "customerEmail", "Error must mention missing parameter customerEmail");
        }
    }

    public static void T2_03_reject_mail_with_no_parameters() {
        String dsl = """
            config {
                app: "MailApp"
                package: "com.example.mail"
            }
            mail EmptyContract() {
                to: "admin@test.com"
                subject: "Ping"
                template: "emails/ping.html"
            }
            """;
        try {
            analyze(dsl);
            throw new AssertionError("Expected AnalysisException for mail contract with zero parameters");
        } catch (AnalysisException ex) {
            assertContains(ex.getMessage(), "EmptyContract", "Error must mention EmptyContract");
        }
    }

    public static void T2_04_reject_mail_missing_to_or_subject() {
        String dsl = """
            config {
                app: "MailApp"
                package: "com.example.mail"
            }
            mail NoSubject(recipient: String) {
                to: recipient
                template: "emails/no-subj.html"
            }
            """;
        try {
            analyze(dsl);
            throw new AssertionError("Expected AnalysisException for mail missing subject expression");
        } catch (AnalysisException ex) {
            assertContains(ex.getMessage(), "subject", "Error must mention subject expression");
        }
    }

    public static void T2_05_reject_invalid_mail_provider() {
        String dsl = """
            config {
                app: "MailApp"
                package: "com.example.mail"
                mail: invalidProvider
            }
            """;
        try {
            analyze(dsl);
            throw new AssertionError("Expected ParseException for invalid mail provider");
        } catch (ParseException ex) {
            assertContains(ex.getMessage(), "invalidProvider", "Error must report invalidProvider");
        }
    }

    // =========================================================================
    // Tier 3: Asynchronous Spring Mail & Thymeleaf Generator
    // =========================================================================

    public static void T3_01_pom_includes_mail_and_thymeleaf_dependencies() {
        String dsl = """
            config {
                app: "MailApp"
                package: "com.example.mail"
            }
            mail Notification(toEmail: String) {
                to: toEmail
                subject: "Hello"
                template: "emails/hello.html"
            }
            """;
        ZenithFile file = analyze(dsl);
        List<JavaSourceFile> files = ProjectGenerator.generateAll(file);
        String pom = source(files, "pom.xml");
        assertTrue(pom.contains("spring-boot-starter-mail"), "Expected spring-boot-starter-mail in pom.xml");
        assertTrue(pom.contains("spring-boot-starter-thymeleaf"), "Expected spring-boot-starter-thymeleaf in pom.xml");
    }

    public static void T3_02_generates_email_service_interface_and_impl() {
        String dsl = """
            config {
                app: "MailApp"
                package: "com.example.mail"
                mail: console
                mailFrom: "system@example.com"
            }
            model User {
                id: UUID @id @auto
                email: String @email
            }
            mail PasswordReset(user: User, token: String) {
                to: user.email
                subject: "Reset your password"
                template: "emails/password-reset.html"
            }
            """;
        ZenithFile file = analyze(dsl);
        List<JavaSourceFile> files = ProjectGenerator.generateAll(file);

        String emailService = source(files, "service/EmailService.java");
        assertTrue(emailService.contains("CompletableFuture<Void> sendPasswordReset(User user, String token)"),
                "Expected sendPasswordReset method in EmailService");

        String emailServiceImpl = source(files, "service/impl/EmailServiceImpl.java");
        assertTrue(emailServiceImpl.contains("@Service"), "Expected @Service annotation on EmailServiceImpl");
        assertTrue(emailServiceImpl.contains("@Async"), "Expected @Async annotation on dispatch method");
        assertTrue(emailServiceImpl.contains("TemplateEngine templateEngine"), "Expected Thymeleaf TemplateEngine injection");
        assertTrue(emailServiceImpl.contains("[ZENITH EMAIL PREVIEW]"), "Expected console preview mode in EmailServiceImpl");
    }

    public static void T3_03_generates_default_thymeleaf_templates() {
        String dsl = """
            config {
                app: "ShopSphere"
                package: "com.shopsphere"
            }
            mail OrderShipped(trackingCode: String, customerEmail: String) {
                to: customerEmail
                subject: "Your package is on its way!"
                template: "emails/order-shipped.html"
            }
            """;
        ZenithFile file = analyze(dsl);
        List<JavaSourceFile> files = ProjectGenerator.generateAll(file);

        String template = source(files, "templates/emails/order-shipped.html");
        assertTrue(template.contains("xmlns:th=\"http://www.thymeleaf.org\""), "Expected Thymeleaf namespace");
        assertTrue(template.contains("th:text=\"${trackingCode}\""), "Expected trackingCode binding in HTML");
        assertTrue(template.contains("OrderShipped"), "Expected mail contract name in HTML");
    }

    public static void T3_04_application_properties_emits_mail_configuration() {
        String dsl = """
            config {
                app: "ShopSphere"
                package: "com.shopsphere"
                mail: smtp
                mailFrom: "notifications@shopsphere.io"
            }
            mail Ping(email: String) {
                to: email
                subject: "Ping"
                template: "emails/ping.html"
            }
            """;
        ZenithFile file = analyze(dsl);
        List<JavaSourceFile> files = ProjectGenerator.generateAll(file);

        String yaml = source(files, "application.yml");
        assertTrue(yaml.contains("SPRING_MAIL_HOST:localhost"), "Expected spring.mail.host");
        assertTrue(yaml.contains("notifications@shopsphere.io"), "Expected configured default mailFrom in yaml");
        assertTrue(yaml.contains("ZENITH_MAIL_PROVIDER:smtp"), "Expected smtp provider configuration");
    }

    // =========================================================================
    // Tier 4: Service & Action Block Integration
    // =========================================================================

    public static void T4_01_service_injects_email_service_when_send_action_used() {
        String dsl = """
            config {
                app: "ShopSphere"
                package: "com.shopsphere"
            }
            model Order {
                id: UUID @id @auto
                orderNumber: String
                endpoints {
                    post /checkout {
                        action {
                            send OrderConfirmation(order = entity, email = "customer@example.com")
                        }
                    }
                }
            }
            mail OrderConfirmation(order: Order, email: String) {
                to: email
                subject: "Order Confirmed"
                template: "emails/order-confirmed.html"
            }
            """;
        ZenithFile file = analyze(dsl);
        List<JavaSourceFile> files = ProjectGenerator.generateAll(file);

        String orderServiceImpl = source(files, "service/impl/OrderServiceImpl.java");
        assertTrue(orderServiceImpl.contains("private final EmailService emailService;"),
                "Expected EmailService injection in OrderServiceImpl");
        assertTrue(orderServiceImpl.contains("emailService.sendOrderConfirmation(entity, \"customer@example.com\");"),
                "Expected emailService.sendOrderConfirmation call inside checkout action");
    }

    public static void T4_02_job_injects_email_service_when_send_action_used() {
        String dsl = """
            config {
                app: "ShopSphere"
                package: "com.shopsphere"
            }
            mail StatusReport(adminEmail: String) {
                to: adminEmail
                subject: "Hourly System Report"
                template: "emails/hourly-report.html"
            }
            job HeartbeatJob {
                fixedRate: 3600000
                action {
                    send StatusReport(adminEmail = "devops@example.com")
                }
            }
            """;
        ZenithFile file = analyze(dsl);
        List<JavaSourceFile> files = ProjectGenerator.generateAll(file);

        String jobCode = source(files, "job/HeartbeatJob.java");
        assertTrue(jobCode.contains("private final EmailService emailService;"),
                "Expected EmailService injection in HeartbeatJob");
        assertTrue(jobCode.contains("emailService.sendStatusReport(\"devops@example.com\");"),
                "Expected emailService.sendStatusReport call inside HeartbeatJob execute()");
    }
}
