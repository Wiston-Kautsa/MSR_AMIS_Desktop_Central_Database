package com.mycompany.msr.amis;

import java.time.LocalDateTime;

public final class LocalAuthService implements AuthService {

    private final RemoteMirrorCoordinator remoteMirrorCoordinator = ServiceRegistry.getRemoteMirrorCoordinator();

    @Override
    public User authenticate(String identifier, String password) {
        if (remoteMirrorCoordinator.canAttemptRemoteAuthentication()) {
            try {
                User user = remoteMirrorCoordinator.getRemoteAuthService().authenticate(identifier, password);
                remoteMirrorCoordinator.handleRemoteLogin(user, password);
                Session.setPasswordChangeRequired(DatabaseHandler.requiresInitialPasswordChange(user));
                return user;
            } catch (SecurityException securityException) {
                throw securityException;
            } catch (Exception ignored) {
                // If the central API drops mid-authentication, fall back to the local database.
            }
        }
        User user = DatabaseHandler.authenticateUser(identifier, password);
        Session.setPasswordChangeRequired(DatabaseHandler.requiresInitialPasswordChange(user));
        return user;
    }

    @Override
    public boolean isTemporarySetupAccount(User user) {
        return DatabaseHandler.isTemporarySetupAccount(user);
    }

    @Override
    public String issuePasswordResetCode(String identifier, String resetCode, LocalDateTime expiresAt) throws Exception {
        if (remoteMirrorCoordinator.canAttemptRemoteAuthentication()) {
            return remoteMirrorCoordinator.getRemoteAuthService().issuePasswordResetCode(identifier, resetCode, expiresAt);
        }
        return DatabaseHandler.issuePasswordResetCode(identifier, resetCode, expiresAt);
    }

    @Override
    public void clearPasswordResetCode(String identifier) {
        if (remoteMirrorCoordinator.canAttemptRemoteAuthentication()) {
            return;
        }
        DatabaseHandler.clearPasswordResetCode(identifier);
    }

    @Override
    public void resetPasswordWithCode(String identifier, String resetCode, String hashedPassword) throws Exception {
        if (remoteMirrorCoordinator.canAttemptRemoteAuthentication()) {
            remoteMirrorCoordinator.getRemoteAuthService().resetPasswordWithCode(identifier, resetCode, hashedPassword);
            remoteMirrorCoordinator.updateMirroredPassword(identifier, hashedPassword);
            return;
        }
        DatabaseHandler.resetPasswordWithCode(identifier, resetCode, PasswordUtils.hash(hashedPassword));
    }

    @Override
    public void completeInitialPasswordChange(String newPassword) throws Exception {
        User currentUser = Session.getCurrentUser();
        if (currentUser == null) {
            throw new IllegalStateException("No signed-in user was found.");
        }
        if (remoteMirrorCoordinator.hasRemoteSession()) {
            remoteMirrorCoordinator.getRemoteAuthService().completeInitialPasswordChange(newPassword);
            remoteMirrorCoordinator.updateMirroredPassword(currentUser.getEmail(), newPassword);
            Session.setPasswordChangeRequired(false);
            return;
        }
        DatabaseHandler.completeInitialPasswordChange(currentUser.getEmail(), PasswordUtils.hash(newPassword));
        Session.setPasswordChangeRequired(false);
    }
}
