package io.github.matrixlane.metadataregistry.web.seed;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (accountRepository.count() > 0) {
            log.info("账户表已有数据，跳过系统初始化数据生成");
            return;
        }
        log.info("开始初始化系统必要数据");

        Account admin = new Account();
        admin.setUsername("admin");
        admin.setPasswordHash("NO_PASSWORD");
        admin.setSysRole(SystemRole.SUPER_ADMIN);
        admin.setEnabled(true);
        accountRepository.save(admin);

        log.info("已成功初始化超级管理员账号：admin / (首次登录免密)");
    }
}
