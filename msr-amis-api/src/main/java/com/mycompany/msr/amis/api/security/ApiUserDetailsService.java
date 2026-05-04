package com.mycompany.msr.amis.api.security;

import com.mycompany.msr.amis.api.domain.UserAccount;
import com.mycompany.msr.amis.api.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class ApiUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public ApiUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        UserAccount account = userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase(identifier, identifier)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return User.withUsername(account.getEmail())
                .password(account.getPasswordHash())
                .authorities(new SimpleGrantedAuthority("ROLE_" + account.getRole().name()))
                .accountLocked(account.isFrozen())
                .disabled(!account.isActive())
                .build();
    }
}
