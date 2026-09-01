package io.github.matrixlane.metadataregistry.dataspecification;

import jakarta.persistence.Column;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public abstract class EnumeratedConceptualDomain extends ConceptualDomain {

    /**
     * 值含义集合展开为该枚举概念域取值集的时间。
     *
     * <p>ISO/IEC 11179-31:2023 表 3(7.2.2.4.4 引用):expanded_on_datetime 0..1,
     * 数据类型 Datetime(ISO/IEC 11179-3:2023, 6.2.3,含时区)。
     */
    @Column(name = "expanded_on_datetime")
    private OffsetDateTime expandedOnDatetime;

    /**
     * 枚举概念域取值集被锁定(不可再增删)的时间。
     *
     * <p>ISO/IEC 11179-31:2023 表 3:locked_datetime 0..1,
     * 数据类型 Datetime(ISO/IEC 11179-3:2023, 6.2.3,含时区)。
     */
    @Column(name = "locked_datetime")
    private OffsetDateTime lockedDatetime;
}
