package top.jionjion.agentdesk.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 文本分块器 - 递归字符分割策略
 * <p>
 * 分割优先级: 段落(\n\n) → 换行(\n) → 中文句号(。) → 英文句号(. ) → 逗号 → 空格
 *
 * @author Jion
 */
public class TextChunker {

    private static final String[] DEFAULT_SEPARATORS = {"\n\n", "\n", "。", ". ", "，", ", ", " "};

    private final int chunkSize;
    private final int overlap;

    public TextChunker(int chunkSize, int overlap) {
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    /**
     * 静态工厂方法
     */
    public static List<String> split(String text, int chunkSize, int overlap) {
        return new TextChunker(chunkSize, overlap).splitText(text);
    }

    /**
     * 将文本分割为多个块
     */
    public List<String> splitText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // 预处理
        text = text.replaceAll("\\r\\n", "\n")
                .replaceAll("\\r", "\n")
                .replaceAll(" {3,}", "  ");

        List<String> chunks = recursiveSplit(text, 0);
        return mergeSmallChunks(chunks);
    }

    private List<String> recursiveSplit(String text, int separatorIndex) {
        if (text.length() <= chunkSize) {
            String trimmed = text.trim();
            return trimmed.isEmpty() ? List.of() : List.of(trimmed);
        }

        if (separatorIndex >= DEFAULT_SEPARATORS.length) {
            return forceSplit(text);
        }

        String separator = DEFAULT_SEPARATORS[separatorIndex];
        String[] parts = text.split(Pattern.quote(separator), -1);

        if (parts.length <= 1) {
            return recursiveSplit(text, separatorIndex + 1);
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();

        for (String part : parts) {
            String candidate = currentChunk.isEmpty()
                    ? part
                    : currentChunk + separator + part;

            if (candidate.length() <= chunkSize) {
                currentChunk = new StringBuilder(candidate);
            } else {
                if (!currentChunk.isEmpty()) {
                    String trimmed = currentChunk.toString().trim();
                    if (!trimmed.isEmpty()) chunks.add(trimmed);
                }

                if (part.length() > chunkSize) {
                    chunks.addAll(recursiveSplit(part, separatorIndex + 1));
                    currentChunk = new StringBuilder();
                } else {
                    currentChunk = new StringBuilder(part);
                }
            }
        }

        if (!currentChunk.isEmpty()) {
            String trimmed = currentChunk.toString().trim();
            if (!trimmed.isEmpty()) chunks.add(trimmed);
        }

        return applyOverlap(chunks);
    }

    private List<String> forceSplit(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) chunks.add(chunk);
            start = end - overlap;
            if (start >= end) break;
        }
        return chunks;
    }

    private List<String> applyOverlap(List<String> chunks) {
        if (overlap <= 0 || chunks.size() <= 1) {
            return chunks;
        }

        List<String> result = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            if (i == 0) {
                result.add(chunks.get(i));
            } else {
                String prev = chunks.get(i - 1);
                String overlapText = prev.length() > overlap
                        ? prev.substring(prev.length() - overlap)
                        : prev;
                result.add(overlapText + chunks.get(i));
            }
        }
        return result;
    }

    private List<String> mergeSmallChunks(List<String> chunks) {
        if (chunks.size() <= 1) return chunks;

        List<String> result = new ArrayList<>();
        int minSize = chunkSize / 4;

        for (String chunk : chunks) {
            if (chunk.isBlank()) continue;
            if (!result.isEmpty() && chunk.length() < minSize) {
                int lastIdx = result.size() - 1;
                result.set(lastIdx, result.get(lastIdx) + "\n" + chunk);
            } else {
                result.add(chunk);
            }
        }

        return result;
    }
}
