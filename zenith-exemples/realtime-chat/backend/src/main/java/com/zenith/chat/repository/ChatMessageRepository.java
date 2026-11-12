package com.zenith.chat.repository;

import com.zenith.chat.entity.ChatMessage;
import com.zenith.chat.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;
import java.time.LocalDate;
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID>, JpaSpecificationExecutor<ChatMessage> {

    Page<ChatMessage> findBySenderId(UUID senderId, Pageable pageable);

    List<ChatMessage> findBySenderId(UUID senderId);

    List<ChatMessage> findBySenderEmail(String senderEmail);

    Page<ChatMessage> findByRoomId(UUID roomId, Pageable pageable);

    List<ChatMessage> findByRoomId(UUID roomId);

}
