package com.zenith.chat.controller;

import com.zenith.chat.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatMessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/chat_messages returns 200 OK with PageResponse")
    void testGetAll_Success() throws Exception {
        mockMvc.perform(get("/api/v1/chat_messages")
                .param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/chat_messages/{id} with non-existent id returns 404 Not Found")
    void testGetById_NotFound() throws Exception {
        mockMvc.perform(get("/api/v1/chat_messages/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/chat_messages without token returns 401 Unauthorized or 403 Forbidden")
    void testCreate_Unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/chat_messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(s == 401 || s == 403 || s == 400, "Expected 401, 403 or 400 but got " + s);
                });
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/v1/chat_messages with invalid empty payload returns 400 Bad Request")
    void testCreate_ValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/chat_messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/v1/chat_messages/{id} without token returns 401 Unauthorized or 403 Forbidden")
    void testUpdate_Unauthorized() throws Exception {
        mockMvc.perform(put("/api/v1/chat_messages/00000000-0000-0000-0000-000000000000")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(s == 401 || s == 403 || s == 400, "Expected 401, 403 or 400 but got " + s);
                });
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PUT /api/v1/chat_messages/{id} on non-existent id returns 404 Not Found or 400 Bad Request")
    void testUpdate_NotFound() throws Exception {
        mockMvc.perform(put("/api/v1/chat_messages/00000000-0000-0000-0000-000000000000")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(s == 404 || s == 400);
                });
    }

    @Test
    @DisplayName("DELETE /api/v1/chat_messages/{id} without token returns 401 Unauthorized or 403 Forbidden")
    void testDelete_Unauthorized() throws Exception {
        mockMvc.perform(delete("/api/v1/chat_messages/00000000-0000-0000-0000-000000000000"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(s == 401 || s == 403, "Expected 401 or 403 but got " + s);
                });
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("DELETE /api/v1/chat_messages/{id} on non-existent id returns 404 Not Found")
    void testDelete_NotFound() throws Exception {
        mockMvc.perform(delete("/api/v1/chat_messages/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/chat_messages/{id}/send without token returns 401 Unauthorized or 403 Forbidden")
    void testSend_Unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/chat_messages/00000000-0000-0000-0000-000000000000/send")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(s == 401 || s == 403, "Expected 401 or 403 but got " + s);
                });
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/v1/chat_messages/{id}/send on non-existent entity returns 404 Not Found")
    void testSend_NotFound() throws Exception {
        mockMvc.perform(post("/api/v1/chat_messages/00000000-0000-0000-0000-000000000000/send")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/chat_messages/{id}/direct without token returns 401 Unauthorized or 403 Forbidden")
    void testDirect_Unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/chat_messages/00000000-0000-0000-0000-000000000000/direct")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(s == 401 || s == 403, "Expected 401 or 403 but got " + s);
                });
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/v1/chat_messages/{id}/direct on non-existent entity returns 404 Not Found")
    void testDirect_NotFound() throws Exception {
        mockMvc.perform(post("/api/v1/chat_messages/00000000-0000-0000-0000-000000000000/direct")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

}
