package dev.zenith.test;

import dev.zenith.ast.ZenithFile;
import dev.zenith.generator.JavaSourceFile;
import dev.zenith.generator.ProjectGenerator;
import dev.zenith.lexer.Lexer;
import dev.zenith.parser.Parser;

import java.util.List;

public class Feature14NginxProxyTest {

    public static void testParseAndGenerateNginxProxy() {
        String code = "config {\n" +
                "    app: \"ShopApp\"\n" +
                "    package: \"com.shop\"\n" +
                "    port: 8080\n" +
                "    database: postgres\n" +
                "    proxy: nginx\n" +
                "    replicas: 3\n" +
                "    ssl: true\n" +
                "}\n\n" +
                "model Product {\n" +
                "    name: String\n" +
                "    price: Float\n" +
                "}\n";

        Lexer lexer = new Lexer(code);
        Parser parser = new Parser(lexer.tokenize(), "test.zen");
        ZenithFile file = parser.parse();

        TestHarness.assertTrue(file.getConfig().isProxyEnabled(), "Proxy should be enabled");
        TestHarness.assertEquals("nginx", file.getConfig().getProxyType(), "Proxy type should be nginx");
        TestHarness.assertEquals(3, file.getConfig().getProxyReplicas(), "Proxy replicas should be 3");
        TestHarness.assertTrue(file.getConfig().isProxySslEnabled(), "Proxy SSL should be enabled");

        List<JavaSourceFile> generated = ProjectGenerator.generateAll(file);
        
        boolean foundNginxConf = false;
        boolean foundDockerCompose = false;

        for (JavaSourceFile src : generated) {
            if ("nginx/nginx.conf".equals(src.getRelativePath())) {
                foundNginxConf = true;
                TestHarness.assertTrue(src.getContent().contains("server shopapp-1:8080"), "nginx.conf should contain shopapp-1");
                TestHarness.assertTrue(src.getContent().contains("server shopapp-2:8080"), "nginx.conf should contain shopapp-2");
                TestHarness.assertTrue(src.getContent().contains("server shopapp-3:8080"), "nginx.conf should contain shopapp-3");
                TestHarness.assertTrue(src.getContent().contains("least_conn;"), "nginx.conf should use least_conn");
                TestHarness.assertTrue(src.getContent().contains("client_max_body_size 50M;"), "nginx.conf should set client_max_body_size 50M");
            }
            if ("docker-compose.yml".equals(src.getRelativePath())) {
                foundDockerCompose = true;
                TestHarness.assertTrue(src.getContent().contains("nginx:"), "docker-compose should have nginx service");
                TestHarness.assertTrue(src.getContent().contains("shopapp-1:"), "docker-compose should have shopapp-1");
                TestHarness.assertTrue(src.getContent().contains("shopapp-2:"), "docker-compose should have shopapp-2");
                TestHarness.assertTrue(src.getContent().contains("shopapp-3:"), "docker-compose should have shopapp-3");
                TestHarness.assertTrue(src.getContent().contains("\"80:80\""), "docker-compose should map port 80");
                TestHarness.assertTrue(src.getContent().contains("\"443:443\""), "docker-compose should map port 443");
            }
        }

        TestHarness.assertTrue(foundNginxConf, "Expected nginx/nginx.conf to be generated");
        TestHarness.assertTrue(foundDockerCompose, "Expected docker-compose.yml to be generated with proxy setup");
        System.out.println("Feature14NginxProxyTest passed successfully!");
    }

    public static void main(String[] args) {
        testParseAndGenerateNginxProxy();
    }
}
