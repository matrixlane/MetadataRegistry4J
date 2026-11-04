package io.github.matrixlane.metadataregistry.web.service;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class ApplicationRestartExecutor {

    private static final long EXIT_DELAY_MILLIS = 1000;

    private final ConfigurableApplicationContext applicationContext;

    public ApplicationRestartExecutor(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public void scheduleExit() {
        Thread exitThread = new Thread(() -> {
            try {
                Thread.sleep(EXIT_DELAY_MILLIS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            int exitCode = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(exitCode);
        }, "datasource-auto-restart-exit");
        exitThread.setDaemon(false);
        exitThread.start();
    }
}
