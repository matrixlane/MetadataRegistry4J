package io.github.matrixlane.metadataregistry.web.seed;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.service.RegistrationProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 注册流程标准规则启动播种（registration-process-designer）。
 *
 * <p>应用启动时为全部存量注册机构调和播种默认迁移规则（§2.5 迁移集 +
 * 三处默认审核点）；联邦/初始化向导等路径新增 RA 时由
 * {@link RegistrationProcessService#ensureSeed} 在 RA 创建处与服务方法内
 * 惰性补播兜底。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegistrationProcessSeedInitializer implements ApplicationRunner {

    private final RegistrationAuthorityRepository registrationAuthorityRepository;
    private final RegistrationProcessService registrationProcessService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<RegistrationAuthority> authorities = registrationAuthorityRepository.findAll();
        for (RegistrationAuthority authority : authorities) {
            registrationProcessService.ensureSeed(authority.getId());
        }
        if (!authorities.isEmpty()) {
            log.info("已为 {} 个注册机构播种标准注册流程规则", authorities.size());
        }
    }
}
