package io.github.matrixlane.metadataregistry.web.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link FlywayHistoryRelocationConfig} 的 BFPP 单测:经类型查找
 * {@link FlywayMigrationInitializer}(Boot 3.5 的 bean 名为 flywayInitializer,
 * 回归:曾按字面量 flywayMigrationInitializer 查找导致静默 no-op)并入 dependsOn。
 */
class FlywayHistoryRelocationConfigTest {

    @Test
    @DisplayName("存在 FlywayMigrationInitializer 时并入 dependsOn,保留既有依赖")
    void appendsRelocatorDependency() throws Exception {
        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        BeanDefinition initializer = mock(BeanDefinition.class);
        when(beanFactory.getBeanNamesForType(FlywayMigrationInitializer.class, false, false))
                .thenReturn(new String[]{"flywayInitializer"});
        when(beanFactory.getBeanDefinition("flywayInitializer")).thenReturn(initializer);
        when(initializer.getDependsOn()).thenReturn(new String[]{"flyway"});

        new FlywayHistoryRelocationConfig().flywayHistoryRelocationDependency()
                .postProcessBeanFactory(beanFactory);

        org.mockito.ArgumentCaptor<String[]> captor = org.mockito.ArgumentCaptor.forClass(String[].class);
        verify(initializer).setDependsOn(captor.capture());
        assertThat(captor.getValue())
                .containsExactlyInAnyOrder("flyway", "flywayHistoryRelocator");
    }

    @Test
    @DisplayName("幂等:已含搬运器依赖时不重复追加")
    void idempotentWhenAlreadyPresent() throws Exception {
        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        BeanDefinition initializer = mock(BeanDefinition.class);
        when(beanFactory.getBeanNamesForType(FlywayMigrationInitializer.class, false, false))
                .thenReturn(new String[]{"flywayInitializer"});
        when(beanFactory.getBeanDefinition("flywayInitializer")).thenReturn(initializer);
        when(initializer.getDependsOn()).thenReturn(new String[]{"flywayHistoryRelocator", "flyway"});

        new FlywayHistoryRelocationConfig().flywayHistoryRelocationDependency()
                .postProcessBeanFactory(beanFactory);

        org.mockito.ArgumentCaptor<String[]> captor = org.mockito.ArgumentCaptor.forClass(String[].class);
        verify(initializer).setDependsOn(captor.capture());
        assertThat(captor.getValue()).containsExactly("flywayHistoryRelocator", "flyway");
    }

    @Test
    @DisplayName("flyway 关闭(local profile)时无 FlywayMigrationInitializer → 无操作")
    void skipsWhenInitializerAbsent() {
        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        when(beanFactory.getBeanNamesForType(FlywayMigrationInitializer.class, false, false))
                .thenReturn(new String[0]);

        new FlywayHistoryRelocationConfig().flywayHistoryRelocationDependency()
                .postProcessBeanFactory(beanFactory);

        verify(beanFactory).getBeanNamesForType(FlywayMigrationInitializer.class, false, false);
        verifyNoMoreInteractions(beanFactory);
    }
}
