package top.jionjion.agentdesk.dto.knowledge;

/**
 * 知识库检索请求
 *
 * @param query          查询文本
 * @param topK           返回的最大结果数
 * @param scoreThreshold 最小相似度阈值
 * @author Jion
 */
public record RetrieveRequest(
        String query,
        Integer topK,
        Double scoreThreshold
) {
    public int effectiveTopK() {
        return topK != null && topK > 0 ? topK : 5;
    }

    public double effectiveThreshold() {
        return scoreThreshold != null && scoreThreshold > 0 ? scoreThreshold : 0.5;
    }
}
