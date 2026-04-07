package top.jionjion.agentdesk.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import org.springframework.stereotype.Service;
import top.jionjion.agentdesk.config.OssProperties;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 阿里云 OSS 操作服务
 */
@Service
public class OssService {

    private final OSS ossClient;
    private final OssProperties ossProperties;

    public OssService(OSS ossClient, OssProperties ossProperties) {
        this.ossClient = ossClient;
        this.ossProperties = ossProperties;
    }

    /**
     * 上传文件到 OSS
     */
    public void upload(String key, InputStream inputStream, long size, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(size);
        metadata.setContentType(contentType);
        ossClient.putObject(ossProperties.getBucket(), key, inputStream, metadata);
    }

    /**
     * 生成预签名下载 URL
     */
    public String generatePresignedUrl(String key, int expirationMinutes) {
        Date expiration = new Date(System.currentTimeMillis() + expirationMinutes * 60_000L);
        URL url = ossClient.generatePresignedUrl(ossProperties.getBucket(), key, expiration);
        return url.toString();
    }

    /**
     * 删除 OSS 对象
     */
    public void delete(String key) {
        ossClient.deleteObject(ossProperties.getBucket(), key);
    }

    /**
     * 从 OSS 读取文件内容为文本字符串
     */
    public String readAsText(String key) {
        OSSObject object = ossClient.getObject(ossProperties.getBucket(), key);
        try (InputStream is = object.getObjectContent()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("读取 OSS 文件失败: " + key, e);
        }
    }
}
