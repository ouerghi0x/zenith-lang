package com.zenith.chat.repository;

import com.zenith.chat.entity.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ChatRoomRepositoryTest {

    @Autowired
    private ChatRoomRepository repository;

    @Test
    @DisplayName("Verify ChatRoomRepository bean is injected and functional")
    void testRepositoryInjected() {
        assertNotNull(repository, "Repository must be injected by Spring Data JPA");
        long count = repository.count();
        assertTrue(count >= 0, "Initial entity count should be non-negative");
    }
}
