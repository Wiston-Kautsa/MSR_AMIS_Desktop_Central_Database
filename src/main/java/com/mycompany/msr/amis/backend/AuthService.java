package com.mycompany.msr.amis;

import java.time.LocalDateTime;

public interface AuthService {

    User authenticate(String identifier, String password);

    boolean isTemporarySetupAccount(User user);

    String issuePasswordResetCode(String identifier, String resetCode, LocalDateTime expiresAt) throws Exception;

    void clearPasswordResetCode(String identifier);

    void resetPasswordWithCode(String identifier, String resetCode, String hashedPassword) throws Exception;

    void completeInitialPasswordChange(String newPassword) throws Exception;
}
