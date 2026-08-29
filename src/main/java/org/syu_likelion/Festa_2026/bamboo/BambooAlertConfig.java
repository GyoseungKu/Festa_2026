package org.syu_likelion.Festa_2026.bamboo;

import java.util.concurrent.Executor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(BambooAlertProperties.class)
public class BambooAlertConfig {

    /** 신고 알림이 늦어지더라도 신고 자체는 즉시 성공해야 하므로 발송은 별도 스레드로 넘긴다. */
    @Bean(name = "bambooAlertExecutor")
    Executor bambooAlertExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("bamboo-alert-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(200);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
