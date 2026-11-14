package dev.zenith.test;

import dev.zenith.deploy.ServerProfileManager;
import dev.zenith.deploy.SshDeployer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class Feature35RemoteSshDeploymentTest {

    public static void testServerProfileAndDeploymentConfig() {
        // Test saving and loading server profiles
        String testServerName = "test-prod-server";
        ServerProfileManager.ServerConfig config = new ServerProfileManager.ServerConfig();
        config.host = "192.168.1.100";
        config.port = 2222;
        config.user = "zenith";
        config.auth = "key";
        config.keyPath = "~/.ssh/id_rsa";
        config.remoteDir = "/var/www/zenith-app";

        ServerProfileManager.saveServer(testServerName, config);

        Map<String, ServerProfileManager.ServerConfig> loadedServers = ServerProfileManager.loadServers();
        TestHarness.assertTrue(loadedServers.containsKey(testServerName), "Loaded servers should contain test-prod-server");
        
        ServerProfileManager.ServerConfig loaded = loadedServers.get(testServerName);
        TestHarness.assertEquals("192.168.1.100", loaded.host, "Host should match");
        TestHarness.assertEquals(2222, loaded.port, "Port should match");
        TestHarness.assertEquals("zenith", loaded.user, "User should match");
        TestHarness.assertEquals("key", loaded.auth, "Auth should match");
        TestHarness.assertEquals("~/.ssh/id_rsa", loaded.keyPath, "KeyPath should match");
        TestHarness.assertEquals("/var/www/zenith-app", loaded.remoteDir, "RemoteDir should match");

        // Verify that SshDeployer handles missing servers gracefully
        boolean nonExistentTest = SshDeployer.testConnection("non-existent-server");
        TestHarness.assertTrue(!nonExistentTest, "Test connection for non-existent server should return false");

        boolean nonExistentDeploy = SshDeployer.deployProject("non-existent-server", "generated-app");
        TestHarness.assertTrue(!nonExistentDeploy, "Deploy project for non-existent server should return false");

        System.out.println("Feature35RemoteSshDeploymentTest passed successfully!");
    }

    public static void main(String[] args) {
        testServerProfileAndDeploymentConfig();
    }
}
