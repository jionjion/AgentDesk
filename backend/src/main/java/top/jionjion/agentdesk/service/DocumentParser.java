package top.jionjion.agentdesk.service;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * 文档解析服务 - 基于 Apache Tika
 * <p>
 * 支持格式: PDF, DOCX, TXT, MD, CSV, HTML, RTF 等
 *
 * @author Jion
 */
@Service
public class DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(DocumentParser.class);

    private final Tika tika = new Tika();

    /**
     * 解析文档为纯文本
     *
     * @param inputStream 文件输入流
     * @param fileName    原始文件名
     * @return 提取的纯文本内容
     */
    public String parse(InputStream inputStream, String fileName) {
        try {
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
            String text = tika.parseToString(inputStream, metadata);
            log.info("文档解析完成: {}, 提取字符数: {}", fileName, text.length());
            return text;
        } catch (Exception e) {
            log.error("文档解析失败: {}", fileName, e);
            throw new RuntimeException("文档解析失败: " + e.getMessage(), e);
        }
    }
}
