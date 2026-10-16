package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/**
 * ISO/IEC 11179-3:2023, 6.2.10 — Sign
 *
 * <p>A perceptible representation, such as a symbol, graphic, or sound fragment,
 * used to convey meaning.
 */
@StandardReference("ISO/IEC 11179-3:2023, 6.2.10")
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Sign implements IValueType<String> {

    /**
     * ISO/IEC 11179-3:2023 表 11(Designation):name 1,必备。
     * 指称(名称)文本:实测 WS 363 全量 2200 条最长 55 字符,255 上限充足。
     * 显式声明 length 锁定 DDL,避免方言默认值漂移(曾致 ddl-auto 收缩列失败)。
     * 跨方言统一 varchar(255):SQL Server 存中文(Collation 双字节)够用。
     */
    @jakarta.persistence.Column(name = "value", length = 255)
    private String value;

    public static Sign of(String value) {
        return new Sign(value);
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Sign other)) return false;
        return Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
