package com.zenith.chat.security;

import com.zenith.chat.entity.ChatUser;
import com.zenith.chat.repository.ChatUserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final ChatUserRepository userRepository;

    public CustomUserDetailsService(ChatUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        ChatUser user = userRepository.findByEmail(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found with identity: " + username));

        String roleStr = user.getRole() != null ? user.getRole().toString() : "USER";
        List<SimpleGrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + roleStr));
        return new org.springframework.security.core.userdetails.User(
            user.getEmail(),
            user.getPassword(),
            authorities
        );
    }
}
