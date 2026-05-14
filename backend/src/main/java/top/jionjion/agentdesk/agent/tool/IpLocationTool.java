package top.jionjion.agentdesk.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * IP 地理位置查询工具, 基于 ip9.com.cn 接口。
 * 支持查询指定 IP 或当前服务器公网 IP 的地理位置。
 *
 * @author Jion
 */
public class IpLocationTool {

    private static final Logger log = LoggerFactory.getLogger(IpLocationTool.class);

    private static final String API_URL = "https://ip9.com.cn/get";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public IpLocationTool() {
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    @Tool(name = ToolDefinitions.IP_LOCATION, description = ToolDefinitions.IP_LOCATION_DESC)
    public String ipLocation(
            @ToolParam(name = "ip", description = "要查询的 IP 地址, 不传则查询当前公网 IP") String ip) {

        String url = API_URL;
        if (ip != null && !ip.isBlank()) {
            url = API_URL + "?ip=" + ip.trim();
        }

        try {
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            log.info("ip_location: 查询 {}", ip == null || ip.isBlank() ? "当前IP" : ip);

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return "查询失败: HTTP " + response.code();
                }
                return formatResult(response.body().string());
            }
        } catch (IOException e) {
            log.error("IP 位置查询失败: {}", e.getMessage());
            return "查询失败: " + e.getMessage();
        }
    }

    private String formatResult(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);

        int ret = root.has("ret") ? root.get("ret").asInt() : 0;
        if (ret != 200) {
            return "查询失败: 接口返回错误 ret=" + ret;
        }

        JsonNode data = root.get("data");
        if (data == null) {
            return "查询失败: 无数据返回";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("IP: ").append(getText(data, "ip")).append("\n");
        sb.append("国家: ").append(getText(data, "country")).append("\n");
        sb.append("省份: ").append(getText(data, "prov")).append("\n");
        sb.append("城市: ").append(getText(data, "city")).append("\n");

        String area = getText(data, "area");
        if (!area.isEmpty()) {
            sb.append("区域: ").append(area).append("\n");
        }

        sb.append("邮编: ").append(getText(data, "post_code")).append("\n");
        sb.append("区号: ").append(getText(data, "area_code")).append("\n");

        String isp = getText(data, "isp");
        if (!isp.isEmpty()) {
            sb.append("运营商: ").append(isp).append("\n");
        }

        String lat = getText(data, "lat");
        String lng = getText(data, "lng");
        if (!lat.isEmpty() && !lng.isEmpty()) {
            sb.append("坐标: ").append(lat).append(", ").append(lng).append("\n");
        }

        return sb.toString().trim();
    }

    private String getText(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText("");
        }
        return "";
    }
}
