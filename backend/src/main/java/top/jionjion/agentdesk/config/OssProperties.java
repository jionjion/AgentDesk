package top.jionjion.agentdesk.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云 OSS 配置属性
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "agentdesk.oss")
public class OssProperties {

    /**
     * OSS 服务 Endpoint, 如 https://oss-cn-shanghai.aliyuncs.com
     */
    private String endpoint;

    /**
     * 存储桶名称
     */
    private String bucket;

    /**
     * 访问凭据 Access Key ID
     */
    private String accessKeyId;

    /**
     * 访问凭据 Access Key Secret
     */
    private String accessKeySecret;
}
