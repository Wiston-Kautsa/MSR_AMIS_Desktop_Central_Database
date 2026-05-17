package com.mycompany.msr.amis;

import java.time.LocalDateTime;

public final class RemoteApiNotConfiguredAuthService implements AuthService {

    private UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException(
                "REMOTE_API mode is not implemented yet. Build the backend API and desktop HTTP client first."
        );
    }

    @Override
    public User authenticate(String identifier, String password) {
        throw unsupported();
    }

    @Override
    public boolean isTemporarySetupAccount(User user) {
        throw unsupported();
    }

    @Override
    public String issuePasswordResetCode(String identifier, String resetCode, LocalDateTime expiresAt) {
        throw unsupported();
    }

    @Override
    public void clearPasswordResetCode(String identifier) {
        throw unsupported();
    }

    @Override
    public void resetPasswordWithCode(String identifier, String resetCode, String plainPassword) {
        throw unsupported();
    }

    @Override
    public void completeInitialPasswordChange(String newPassword) {
        throw unsupported();
    }
}
