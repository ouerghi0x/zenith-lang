package dev.zenith.test;

import dev.zenith.analyzer.AnalysisException;
import dev.zenith.ast.CustomEndpointDef;
import dev.zenith.ast.ModelDef;
import dev.zenith.ast.ZenithFile;
import dev.zenith.generator.JavaSourceFile;
import dev.zenith.generator.ProjectGenerator;
import dev.zenith.parser.ParseException;

import java.util.List;

import static dev.zenith.test.TestHarness.*;

/**
 * Feature #10 / ISSUE-010: Unified Role-Based Access Control (RBAC) Syntax.
 *
 * Tier 1: DSL Parsing & AST Modeling
 * Tier 2: Semantic Validation & Boundaries
 * Tier 3: Spring Security Controller Code Generation
 * Tier 4: Cross-Feature Integration with CRUD & Guards
 */
public class Feature10UnifiedRbacTest {

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

    public static void T1_01_parse_roles_array_in_custom_endpoint() {
        String dsl = """
            config {
                app: "RbacApp"
                package: "com.example.rbac"
                auth: jwt
            }
            enum Role { ADMIN, MANAGER, USER }
            model User {
                id: UUID @id @auto
                email: String @email
                password: String @password
                role: Role @role
            }
            model Order {
                id: UUID @id @auto
                endpoints {
                    post /ship {
                        roles: [ADMIN, MANAGER]
                    }
                }
            }
            """;
        ZenithFile file = analyze(dsl);
        ModelDef order = file.findModel("Order");
        CustomEndpointDef cep = order.getCustomEndpoints().get(0);
        assertTrue(cep.hasRoles(), "Expected endpoint to have roles");
        assertEquals(2, cep.getRoles().size(), "Expected 2 roles in list");
        assertTrue(cep.getRoles().contains("ADMIN"), "Expected ADMIN role");
        assertTrue(cep.getRoles().contains("MANAGER"), "Expected MANAGER role");
        assertTrue(Boolean.TRUE.equals(cep.getRequiresAuth()), "roles implies requiresAuth = true");
    }

    public static void T1_02_parse_single_role_scalar_in_custom_endpoint() {
        String dsl = """
            config {
                app: "RbacApp"
                package: "com.example.rbac"
                auth: jwt
            }
            enum Role { ADMIN, USER }
            model User {
                id: UUID @id @auto
                email: String @email
                password: String @password
                role: Role @role
            }
            model Order {
                id: UUID @id @auto
                endpoints {
                    delete "/{id}" {
                        roles: ADMIN
                    }
                }
            }
            """;
        ZenithFile file = analyze(dsl);
        ModelDef order = file.findModel("Order");
        CustomEndpointDef cep = order.getCustomEndpoints().get(0);
        assertTrue(cep.hasRoles(), "Expected endpoint to have roles");
        assertEquals(1, cep.getRoles().size(), "Expected 1 role");
        assertEquals("ADMIN", cep.getRoles().get(0), "Expected ADMIN");
    }

    public static void T1_03_parse_auth_false_as_public_route() {
        String dsl = """
            config {
                app: "RbacApp"
                package: "com.example.rbac"
                auth: jwt
            }
            model Order {
                id: UUID @id @auto
                endpoints {
                    get /track {
                        auth: false
                    }
                }
            }
            """;
        ZenithFile file = analyze(dsl);
        ModelDef order = file.findModel("Order");
        CustomEndpointDef cep = order.getCustomEndpoints().get(0);
        assertTrue(cep.isPublic(), "Expected endpoint to be public");
        assertTrue(Boolean.FALSE.equals(cep.getRequiresAuth()), "requiresAuth is false");
    }

    // =========================================================================
    // Tier 2: Semantic Validation & Boundaries
    // =========================================================================

    public static void T2_01_reject_roles_public_anti_pattern() {
        String dsl = """
            config {
                app: "RbacApp"
                package: "com.example.rbac"
                auth: jwt
            }
            model Order {
                id: UUID @id @auto
                endpoints {
                    get /public-route {
                        roles: public
                    }
                }
            }
            """;
        try {
            analyze(dsl);
            throw new AssertionError("Expected ParseException for roles: public anti-pattern");
        } catch (ParseException ex) {
            assertContains(ex.getMessage(), "auth: false", "Error must suggest 'auth: false'");
        }
    }

    public static void T2_02_reject_roles_authenticated_anti_pattern() {
        String dsl = """
            config {
                app: "RbacApp"
                package: "com.example.rbac"
                auth: jwt
            }
            model Order {
                id: UUID @id @auto
                endpoints {
                    get /logged-in {
                        roles: authenticated
                    }
                }
            }
            """;
        try {
            analyze(dsl);
            throw new AssertionError("Expected ParseException for roles: authenticated anti-pattern");
        } catch (ParseException ex) {
            assertContains(ex.getMessage(), "auth: true", "Error must suggest 'auth: true'");
        }
    }

    public static void T2_03_reject_undefined_role_in_roles_list() {
        String dsl = """
            config {
                app: "RbacApp"
                package: "com.example.rbac"
                auth: jwt
            }
            enum Role { ADMIN, USER }
            model User {
                id: UUID @id @auto
                email: String @email
                password: String @password
                role: Role @role
            }
            model Order {
                id: UUID @id @auto
                endpoints {
                    post /ship {
                        roles: [ADMIN, NON_EXISTENT_ROLE]
                    }
                }
            }
            """;
        try {
            analyze(dsl);
            throw new AssertionError("Expected AnalysisException for unknown role");
        } catch (AnalysisException ex) {
            assertContains(ex.getMessage(), "NON_EXISTENT_ROLE", "Error must name NON_EXISTENT_ROLE");
        }
    }

    // =========================================================================
    // Tier 3: Spring Security Controller Code Generation
    // =========================================================================

    public static void T3_01_generates_has_any_role_pre_authorize() {
        String dsl = """
            config {
                app: "RbacApp"
                package: "com.example.rbac"
                auth: jwt
            }
            enum Role { ADMIN, MANAGER, USER }
            model User {
                id: UUID @id @auto
                email: String @email
                password: String @password
                role: Role @role
            }
            model Order {
                id: UUID @id @auto
                endpoints {
                    post /ship {
                        roles: [ADMIN, MANAGER]
                    }
                }
            }
            """;
        ZenithFile file = analyze(dsl);
        List<JavaSourceFile> files = ProjectGenerator.generateAll(file);
        String controller = source(files, "controller/OrderController.java");
        assertTrue(controller.contains("@PreAuthorize(\"hasAnyRole('ADMIN', 'MANAGER')\")"),
                "Expected hasAnyRole with ADMIN and MANAGER");
    }

    public static void T3_02_generates_has_single_role_pre_authorize() {
        String dsl = """
            config {
                app: "RbacApp"
                package: "com.example.rbac"
                auth: jwt
            }
            enum Role { ADMIN, USER }
            model User {
                id: UUID @id @auto
                email: String @email
                password: String @password
                role: Role @role
            }
            model Order {
                id: UUID @id @auto
                endpoints {
                    delete "/{id}" {
                        roles: ADMIN
                    }
                }
            }
            """;
        ZenithFile file = analyze(dsl);
        List<JavaSourceFile> files = ProjectGenerator.generateAll(file);
        String controller = source(files, "controller/OrderController.java");
        assertTrue(controller.contains("@PreAuthorize(\"hasRole('ADMIN')\")"),
                "Expected hasRole('ADMIN') for single role");
    }

    public static void T3_03_generates_permit_all_for_auth_false() {
        String dsl = """
            config {
                app: "RbacApp"
                package: "com.example.rbac"
                auth: jwt
            }
            enum Role { ADMIN, USER }
            model User {
                id: UUID @id @auto
                email: String @email
                password: String @password
                role: Role @role
            }
            model Order {
                id: UUID @id @auto
                endpoints {
                    post /public-action {
                        auth: false
                    }
                }
            }
            """;
        ZenithFile file = analyze(dsl);
        List<JavaSourceFile> files = ProjectGenerator.generateAll(file);
        String controller = source(files, "controller/OrderController.java");
        assertTrue(controller.contains("@PreAuthorize(\"permitAll()\")"),
                "Expected permitAll() for auth: false");
    }

    // =========================================================================
    // Tier 4: Cross-Feature Integration with CRUD & Domain Guards
    // =========================================================================

    public static void T4_01_crud_and_custom_endpoints_combined_rbac() {
        String dsl = """
            config {
                app: "StoreApp"
                package: "com.store"
                auth: jwt
            }
            enum UserRole { ADMIN, SELLER, CUSTOMER }
            model User {
                id: UUID @id @auto
                email: String @email
                password: String @password
                role: UserRole @role
            }
            model Product {
                id: UUID @id @auto
                title: String
                endpoints {
                    crud {
                        roles: [ADMIN, SELLER]
                        list: public
                        get: public
                    }
                    post /special-publish {
                        roles: [ADMIN]
                    }
                }
            }
            """;
        ZenithFile file = analyze(dsl);
        List<JavaSourceFile> files = ProjectGenerator.generateAll(file);
        String controller = source(files, "controller/ProductController.java");

        // list and get are public
        assertTrue(controller.contains("getAll"), "has getAll");
        // create has roles [ADMIN, SELLER]
        assertTrue(controller.contains("@PreAuthorize(\"hasAnyRole('ADMIN', 'SELLER')\")"),
                "Expected CRUD create to have ADMIN & SELLER roles");
        // custom endpoint has role ADMIN
        assertTrue(controller.contains("@PreAuthorize(\"hasRole('ADMIN')\")"),
                "Expected custom special-publish to have ADMIN role");
    }
}
