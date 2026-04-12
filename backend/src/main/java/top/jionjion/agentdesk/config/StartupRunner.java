package top.jionjion.agentdesk.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

/**
 * 项目启动完成后, 打印启动信息
 *
 * @author Jion
 */
@Component
public class StartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupRunner.class);

    private final Environment environment;

    public StartupRunner(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String appName = environment.getProperty("spring.application.name", "application");
        String port = environment.getProperty("server.port", "8080");
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        String[] activeProfiles = environment.getActiveProfiles();
        String profiles = activeProfiles.length > 0 ? String.join(", ", activeProfiles) : "default";
        String host = InetAddress.getLocalHost().getHostAddress();
        String datasourceUrl = environment.getProperty("spring.datasource.url", "未配置");

        log.info("""

                ----------------------------------------------------------
                  应用 '{}' 启动成功!
                  环境:        {}
                  数据库:      {}
                  本地访问:    http://localhost:{}{}
                  外部访问:    http://{}:{}{}
                ----------------------------------------------------------""",
                appName, profiles, datasourceUrl, port, contextPath, host, port, contextPath);
    }
}
