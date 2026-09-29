package io.github.matrixlane.metadataregistry.web.auth;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByUsername(String username);
    boolean existsByUsername(String username);

    /** 激活注册员指向清理：注册人员身份删除时解除各账号的 activeRegistrarId 悬挂。 */
    List<Account> findByActiveRegistrarId(UUID activeRegistrarId);
}
