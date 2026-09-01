package io.github.matrixlane.metadataregistry.ext.identification;

/**
 * 版本串比较与递增（registry-merge-strategy）。
 *
 * <p>Scoped_Identifier.version 为字符串，实践中可能是整数（"10"）、SemVer
 * （"1.0.0"）或日期（"2026-08-04"）。本工具提供统一的比较与按命名空间版本
 * 策略（versioningPolicy：SEQUENTIAL / SEMVER / DATE）递增的能力，供本地编辑
 * 版本递增与联邦合并的合并基/冲突判定使用。
 */
public final class Versions {

    private Versions() {
    }

    /**
     * 比较两个版本串。
     *
     * <p>相等返回 0；同为整数或同为 SemVer 时按数值比较；无法解析且不相等时
     * 保守地视为 {@code incoming} 已更新（返回 1），避免静默漏判来源变更。
     *
     * @return 负数 {@code incoming < base}，0 相等，正数 {@code incoming > base}
     */
    public static int compare(String incoming, String base) {
        if (incoming == null && base == null) {
            return 0;
        }
        if (incoming == null) {
            return -1;
        }
        if (base == null) {
            return 1;
        }
        if (incoming.equals(base)) {
            return 0;
        }
        Long a = parseLong(incoming);
        Long b = parseLong(base);
        if (a != null && b != null) {
            return Long.compare(a, b);
        }
        if (SemVerValidator.isValid(incoming) && SemVerValidator.isValid(base)) {
            return compareSemVer(incoming, base);
        }
        return 1;
    }

    /**
     * 按命名空间版本策略递增版本串。
     *
     * <p>DATE 策略返回当日日期（同日重复递增追加 -N 后缀）；其余策略下整数
     * 递增（"1"→"2"）、SemVer 递增补丁位（"1.0.0"→"1.0.1"）；空版本按初始
     * 版本处理；不可解析的版本保持不变以避免版本回退。
     */
    public static String bump(String current, String versioningPolicy) {
        String policy = versioningPolicy == null || versioningPolicy.isBlank()
                ? "SEMVER" : versioningPolicy;
        if ("DATE".equalsIgnoreCase(policy)) {
            String today = java.time.LocalDate.now().toString();
            if (today.equals(current)) {
                return today + "-2";
            }
            if (current != null && current.startsWith(today + "-")) {
                Long suffix = parseLong(current.substring(today.length() + 1));
                return today + "-" + (suffix == null ? 2 : suffix + 1);
            }
            return today;
        }
        Long asLong = parseLong(current);
        if (asLong != null) {
            return String.valueOf(asLong + 1);
        }
        if (SemVerValidator.isValid(current)) {
            int[] parts = splitSemVer(current);
            return parts[0] + "." + parts[1] + "." + (parts[2] + 1);
        }
        if (current == null || current.isBlank()) {
            return initial(policy);
        }
        return current;
    }

    /**
     * 迁移条目在目的方命名空间下的初始版本（"1"；DATE 策略为当日日期）。
     */
    public static String initial(String versioningPolicy) {
        if ("DATE".equalsIgnoreCase(versioningPolicy)) {
            return java.time.LocalDate.now().toString();
        }
        return "1";
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank() || !value.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int compareSemVer(String a, String b) {
        int[] pa = splitSemVer(a);
        int[] pb = splitSemVer(b);
        for (int i = 0; i < 3; i++) {
            if (pa[i] != pb[i]) {
                return Integer.compare(pa[i], pb[i]);
            }
        }
        return 0;
    }

    private static int[] splitSemVer(String version) {
        String[] parts = version.split("\\.");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }
}
