package com.zenith.chat.repository;

import com.zenith.chat.entity.ChatUser;
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
public interface ChatUserRepository extends JpaRepository<ChatUser, UUID>, JpaSpecificationExecutor<ChatUser> {

    Optional<ChatUser> findByUsername(String username);
    boolean existsByUsername(String username);

    Optional<ChatUser> findByEmail(String email);
    boolean existsByEmail(String email);

}
