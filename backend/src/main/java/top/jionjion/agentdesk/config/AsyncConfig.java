package top.jionjion.agentdesk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步任务配置
 *
 * @author Jion
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 知识库文档处理专用线程池
     */
    @Bean("knowledgeTaskExecutor")
    public Executor knowledgeTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("knowledge-");
        executor.setRejectedExecutionHandler((r, e) -> {
            throw new RuntimeException("知识库处理队列已满, 请稍后重试");
        });
        executor.initialize();
        return executor;
    }
}
