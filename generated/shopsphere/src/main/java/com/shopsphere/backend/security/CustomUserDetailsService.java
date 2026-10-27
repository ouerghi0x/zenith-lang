package com.shopsphere.backend.security;

import com.shopsphere.backend.entity.Customer;
import com.shopsphere.backend.repository.CustomerRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final CustomerRepository userRepository;

    public CustomUserDetailsService(CustomerRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Customer user = userRepository.findByEmail(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found with identity: " + username));

        String roleStr = user.getRole() != null ? user.getRole().toString() : "USER";
        List<SimpleGrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + roleStr));
        return new User(
            user.getEmail(),
            user.getPassword(),
            authorities
        );
    }
}
