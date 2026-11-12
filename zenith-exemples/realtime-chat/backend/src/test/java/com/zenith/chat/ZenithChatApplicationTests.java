package com.zenith.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ZenithChatApplicationTests {

    @Test
    @DisplayName("Verify Spring Boot application context loads cleanly")
    void contextLoads() {
    }
}
