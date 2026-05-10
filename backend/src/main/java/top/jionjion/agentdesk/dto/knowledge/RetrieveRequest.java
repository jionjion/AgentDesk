package top.jionjion.agentdesk.dto.knowledge;

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
