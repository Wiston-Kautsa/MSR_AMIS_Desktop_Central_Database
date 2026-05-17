package com.mycompany.msr.amis.api.repository;

import com.mycompany.msr.amis.api.domain.UserAccount;
import com.mycompany.msr.amis.api.domain.UserRole;
import com.mycompany.msr.amis.api.domain.UserStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmailIgnoreCaseOrUsernameIgnoreCase(String email, String username);

    List<UserAccount> findByRoleInOrderByFullNameAsc(Collection<UserRole> roles);

    List<UserAccount> findByRoleAndStatusOrderByFullNameAsc(UserRole role, UserStatus status);

    List<UserAccount> findByTemporaryFalseAndRoleInOrderByFullNameAsc(Collection<UserRole> roles);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByUsernameIgnoreCaseAndIdNot(String username, Long id);

    long countByRole(UserRole role);

    long countByRoleAndTemporaryFalseAndStatus(UserRole role, UserStatus status);
}
