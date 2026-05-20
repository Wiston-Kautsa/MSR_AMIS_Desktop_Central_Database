package com.mycompany.msr.amis.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mycompany.msr.amis.api.config.ReservedEmailConfig;
import com.mycompany.msr.amis.api.domain.UserAccount;
import com.mycompany.msr.amis.api.domain.UserRole;
import com.mycompany.msr.amis.api.domain.UserStatus;
import com.mycompany.msr.amis.api.dto.auth.InitialAdminSetupRequest;
import com.mycompany.msr.amis.api.repository.UserRepository;
import com.mycompany.msr.amis.api.security.JwtService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

class AuthFacadeServiceTest {

    @Test
    void setupInitialAdminCreatesPermanentSuperAdmin() {
        UserRepository userRepository = mock(UserRepository.class);
        ActionAuditService actionAuditService = mock(ActionAuditService.class);
        when(userRepository.countByRoleAndTemporaryFalseAndStatus(any(), eq(UserStatus.ACTIVE))).thenReturn(0L);
        when(userRepository.existsByEmailIgnoreCase("first.admin@example.com")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("firstadmin")).thenReturn(false);
        when(userRepository.findAll()).thenReturn(List.of());

        AuthFacadeService service = new AuthFacadeService(
                userRepository,
                mock(JwtService.class),
                new BCryptPasswordEncoder(),
                mock(PasswordResetEmailService.class),
                mock(JdbcTemplate.class),
                actionAuditService,
                new ReservedEmailConfig(new MockEnvironment()
                        .withProperty("MSR_AMIS_PRIMARY_SUPER_ADMIN_EMAIL", "primary@example.com")),
                mock(PlatformTransactionManager.class),
                false
        );

        service.setupInitialAdmin(new InitialAdminSetupRequest(
                "First Admin",
                "firstadmin",
                "first.admin@example.com",
                "ICT",
                "StrongPass123"
        ));

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userRepository).save(captor.capture());
        UserAccount saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo(UserRole.SUPER_ADMIN);
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.isTemporary()).isFalse();
        assertThat(saved.isMustChangePassword()).isFalse();
        assertThat(new BCryptPasswordEncoder().matches("StrongPass123", saved.getPasswordHash())).isTrue();
    }
}
