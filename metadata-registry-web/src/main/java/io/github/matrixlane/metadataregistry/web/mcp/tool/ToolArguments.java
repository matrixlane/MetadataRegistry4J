package io.github.matrixlane.metadataregistry.web.mcp.tool;

import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MCP 工具参数解析助手（add-mcp-server-support）：类型安全取值 + 统一 422 语义。
 */
public final class ToolArguments {

    private ToolArguments() {
    }

    public static String str(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    public static String requiredStr(Map<String, Object> args, String key) {
        String value = str(args, key);
        if (value == null) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "缺少必填参数：" + key);
        }
        return value;
    }

    public static UUID uuid(Map<String, Object> args, String key) {
        String value = str(args, key);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, key + " 不是合法 UUID：" + value);
        }
    }

    public static UUID requiredUuid(Map<String, Object> args, String key) {
        UUID value = uuid(args, key);
        if (value == null) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "缺少必填参数：" + key);
        }
        return value;
    }

    public static boolean bool(Map<String, Object> args, String key, boolean defaultValue) {
        Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    /** 分页：page 从 0 起（默认 0），size 默认 20、上限 100；排序固定 creationDateTime DESC（与业务面一致）。 */
    public static Pageable pageable(Map<String, Object> args) {
        int page = intInRange(args, "page", 0, 10_000, 0);
        int size = intInRange(args, "size", 1, 100, 20);
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "creationDateTime"));
    }

    public static int intInRange(Map<String, Object> args, String key, int min, int max, int defaultValue) {
        String value = str(args, key);
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) {
                throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                        key + " 须在 [" + min + ", " + max + "] 内：" + parsed);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, key + " 不是合法整数：" + value);
        }
    }

    /** 可空整数参数：缺省返回 null，存在时校验 [min, max] 区间，非法 → 422。 */
    public static Integer intArg(Map<String, Object> args, String key, int min, int max) {
        String value = str(args, key);
        if (value == null) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) {
                throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                        key + " 须在 [" + min + ", " + max + "] 内：" + parsed);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, key + " 不是合法整数：" + value);
        }
    }

    /** 单值枚举（大小写不敏感）；非法值 → 422。 */
    public static <E extends Enum<E>> E enumValue(Class<E> type, Map<String, Object> args, String key) {
        String value = str(args, key);
        if (value == null) {
            return null;
        }
        return Arrays.stream(type.getEnumConstants())
                .filter(constant -> constant.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ApiCodes.VALIDATION_FAILED,
                        key + " 非法取值：" + value + "（允许 " + allowedValues(type) + "）"));
    }

    /** 逗号分隔枚举列表（大小写不敏感）；空串 → null；任一非法 → 422。 */
    public static <E extends Enum<E>> List<E> enumList(Class<E> type, Map<String, Object> args, String key) {
        String value = str(args, key);
        if (value == null) {
            return null;
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .map(part -> Arrays.stream(type.getEnumConstants())
                        .filter(constant -> constant.name().equalsIgnoreCase(part))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ApiCodes.VALIDATION_FAILED,
                                key + " 非法取值：" + part + "（允许 " + allowedValues(type) + "）")))
                .toList();
    }

    /** 字符串列表（逗号分隔）。 */
    public static List<String> stringList(Map<String, Object> args, String key) {
        String value = str(args, key);
        if (value == null) {
            return null;
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }

    private static <E extends Enum<E>> String allowedValues(Class<E> type) {
        return Arrays.toString(Arrays.stream(type.getEnumConstants()).map(Enum::name).toArray());
    }
}
