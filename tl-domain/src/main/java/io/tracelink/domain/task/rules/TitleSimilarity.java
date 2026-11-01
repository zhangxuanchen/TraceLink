package io.tracelink.domain.task.rules;

/**
 * 标题相似度，用于复发识别（R7）。取二元组 Dice 系数，对中文短标题比编辑距离更稳，
 * 阈值由 sys_config 的 similarity.threshold 控制，不硬编码在代码里。
 */
public final class TitleSimilarity {

    private TitleSimilarity() {
    }

    public static double score(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        if (a.equals(b)) {
            return 1.0;
        }
        int hits = 0;
        int[] used = new int[b.length() - 1];
        for (int i = 0; i + 1 < a.length(); i++) {
            String gram = a.substring(i, i + 2);
            for (int j = 0; j + 1 < b.length(); j++) {
                if (used[j] == 0 && gram.equals(b.substring(j, j + 2))) {
                    used[j] = 1;
                    hits++;
                    break;
                }
            }
        }
        int total = (a.length() - 1) + (b.length() - 1);
        return total == 0 ? 0.0 : (2.0 * hits) / total;
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase()
                .replaceAll("[\\s\\p{Punct}，。、；：！？“”‘’（）【】《》—…·]", "");
    }
}
