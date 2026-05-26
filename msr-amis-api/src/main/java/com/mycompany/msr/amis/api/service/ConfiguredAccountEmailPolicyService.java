package com.mycompany.msr.amis.api.service;

import com.mycompany.msr.amis.api.config.ReservedEmailConfig;
import com.mycompany.msr.amis.api.domain.UserAccount;
import com.mycompany.msr.amis.api.domain.UserRole;
import com.mycompany.msr.amis.api.domain.UserStatus;
import com.mycompany.msr.amis.api.repository.UserRepository;
import java.util.Set;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfiguredAccountEmailPolicyService {

    private static final Set<String> LEGACY_SEED_EMAILS = Set.of(
            "wkautsa@gmail.com",
            "admin@msr.local",
            "user@msr.local"
    );

    private final UserRepository userRepository;
    private final ReservedEmailConfig reservedEmailConfig;

    public ConfiguredAccountEmailPolicyService(UserRepository userRepository,
                                               ReservedEmailConfig reservedEmailConfig) {
        this.userRepository = userRepository;
        this.reservedEmailConfig = reservedEmailConfig;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void applyConfiguredAccountPolicy() {
        for (UserAccount user : userRepository.findAll()) {
            String email = normalize(user.getEmail());
            if (reservedEmailConfig.isPrimarySuperAdminEmail(email)) {
                user.setRole(UserRole.SUPER_ADMIN);
                user.setStatus(UserStatus.ACTIVE);
                user.setTemporary(false);
                continue;
            }
            if (!LEGACY_SEED_EMAILS.contains(email) || reservedEmailConfig.isConfiguredAccountEmail(email)) {
                continue;
            }
            if (user.getRole() == UserRole.SUPER_ADMIN) {
                user.setTemporary(false);
                continue;
            }
            user.setStatus(UserStatus.FROZEN);
            user.setTemporary(true);
            user.setMustChangePassword(false);
            user.setResetCode(null);
            user.setResetExpiry(null);
            user.setResetRequestedAt(null);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
