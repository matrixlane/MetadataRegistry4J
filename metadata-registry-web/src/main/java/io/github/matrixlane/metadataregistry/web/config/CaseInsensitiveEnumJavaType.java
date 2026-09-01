package io.github.matrixlane.metadataregistry.web.config;

import org.hibernate.dialect.Dialect;
import org.hibernate.type.descriptor.converter.spi.BasicValueConverter;
import org.hibernate.type.descriptor.java.EnumJavaType;
import org.hibernate.type.descriptor.jdbc.JdbcType;

import java.util.Locale;

/**
 * 大小写不敏感的字符串枚举描述符。
 *
 * <p>ISO/IEC 11179 各部分对枚举取值的大小写约定不一致：如 11179-3:2023 表 20（8.7.1.2）
 * 规定可接受性用小写（preferred/…），而 11179-6:2023 的注册状态用首字母大写
 * （Incomplete/…/Standard/…）。按标准格式导入的历史数据因此与 Java 枚举常量（大写，
 * Java 命名习惯）大小写不匹配，Hibernate 默认的 {@code Enum.valueOf} 区分大小写，
 * 读取时抛出 {@code No enum constant}。
 *
 * <p>本描述符在默认解析失败时按"去空白后转大写"兜底匹配，使数据库中的
 * {@code Standard}/{@code preferred}/{@code PREFERRED} 等写法均能读回枚举。
 * 写入路径不改动，仍落库 Java 枚举常量名（大写）。
 */
public class CaseInsensitiveEnumJavaType<T extends Enum<T>> extends EnumJavaType<T> {


    public CaseInsensitiveEnumJavaType(Class<T> type) {
        super(type);
    }

    @Override
    public T fromName(String name) {
        if (name == null) {
            return null;
        }
        try {
            return super.fromName(name);
        }
        catch (IllegalArgumentException e) {
            return relaxedValueOf(name);
        }
    }

    @Override
    public T fromString(CharSequence string) {
        if (string == null) {
            return null;
        }
        try {
            return Enum.valueOf(getJavaTypeClass(), string.toString());
        }
        catch (IllegalArgumentException e) {
            return relaxedValueOf(string.toString());
        }
    }

    @Override
    public String getCheckCondition(String columnName, JdbcType jdbcType,
            BasicValueConverter<?, ?> converter, Dialect dialect) {
        // 不生成枚举 CHECK 约束。默认实现会把取值限定为大写 Java 枚举名，
        // 与按标准格式（小写/首字母大写）导入的数据冲突——本项目必须支持
        // 直接 SQL 导入标准格式数据，库级约束会阻塞该路径。
        return null;
    }

    private T relaxedValueOf(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return Enum.valueOf(getJavaTypeClass(), normalized);
        }
        catch (IllegalArgumentException e) {
            // 标准书写格式含空格/连字符的取值(如 11179-3 Table 30 的 "Preferred Standard")
            // 归一为下划线后再匹配,兼容按标准格式导入的历史数据。
            String underscored = normalizeToUnderscored(normalized);
            return Enum.valueOf(getJavaTypeClass(), underscored);
        }
    }

    private static String normalizeToUnderscored(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean prevUnderscore = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || c == '-') {
                if (!prevUnderscore) {
                    sb.append('_');
                    prevUnderscore = true;
                }
            }
            else {
                sb.append(c);
                prevUnderscore = false;
            }
        }
        return sb.toString();
    }
}