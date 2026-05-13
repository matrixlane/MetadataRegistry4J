package io.github.matrixlane.metadataregistry.draftgeneration.mapping;

import io.github.matrixlane.metadataregistry.draftgeneration.model.DataTypeMappingResult;
import io.github.matrixlane.metadataregistry.draftgeneration.model.MappingConfidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SqlServerDataTypeMapperTest {

    private SqlServerDataTypeMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new SqlServerDataTypeMapper();
    }

    @Test
    void getDatabaseType_shouldReturnSqlServer() {
        assertThat(mapper.getDatabaseType()).isEqualTo("SQL_SERVER");
    }

    @ParameterizedTest
    @CsvSource({
            "VARCHAR, String, HIGH",
            "NVARCHAR, String, HIGH",
            "CHAR, String, HIGH",
            "NCHAR, String, HIGH",
            "INT, Integer, HIGH",
            "BIGINT, Long, HIGH",
            "SMALLINT, Integer, HIGH",
            "TINYINT, Integer, HIGH",
            "BIT, Boolean, HIGH",
            "DATETIME, DateTime, HIGH",
            "DATETIME2, DateTime, HIGH",
            "SMALLDATETIME, DateTime, HIGH",
            "DATE, Date, HIGH",
            "TIME, Time, HIGH",
            "TEXT, Text, HIGH",
            "NTEXT, Text, HIGH",
            "BINARY, Binary, HIGH",
            "VARBINARY, Binary, HIGH",
            "IMAGE, Binary, HIGH",
            "UNIQUEIDENTIFIER, UUID, HIGH",
            "XML, XML, HIGH"
    })
    void map_shouldReturnHighConfidenceForCommonTypes(String sourceType, String expectedMapped, MappingConfidence expectedConfidence) {
        DataTypeMappingResult result = mapper.map(sourceType, 100, 0);

        assertThat(result.getSourceType()).isEqualTo(sourceType);
        assertThat(result.getMappedType()).isEqualTo(expectedMapped);
        assertThat(result.getConfidence()).isEqualTo(expectedConfidence);
    }

    @ParameterizedTest
    @CsvSource({
            "DECIMAL, Decimal, MEDIUM",
            "NUMERIC, Decimal, MEDIUM",
            "MONEY, Decimal, MEDIUM",
            "SMALLMONEY, Decimal, MEDIUM"
    })
    void map_shouldReturnMediumConfidenceForDecimalTypes(String sourceType, String expectedMapped, MappingConfidence expectedConfidence) {
        DataTypeMappingResult result = mapper.map(sourceType, 18, 2);

        assertThat(result.getMappedType()).isEqualTo(expectedMapped);
        assertThat(result.getConfidence()).isEqualTo(expectedConfidence);
        assertThat(result.getReason()).contains("人工确认");
    }

    @ParameterizedTest
    @CsvSource({
            "DATETIMEOFFSET, DateTime, MEDIUM",
            "TIMESTAMP, Binary, MEDIUM",
            "ROWVERSION, Binary, MEDIUM",
            "SQL_VARIANT, String, MEDIUM",
            "GEOMETRY, String, MEDIUM",
            "GEOGRAPHY, String, MEDIUM",
            "HIERARCHYID, String, MEDIUM"
    })
    void map_shouldReturnMediumConfidenceForSpecialTypes(String sourceType, String expectedMapped, MappingConfidence expectedConfidence) {
        DataTypeMappingResult result = mapper.map(sourceType, null, null);

        assertThat(result.getMappedType()).isEqualTo(expectedMapped);
        assertThat(result.getConfidence()).isEqualTo(expectedConfidence);
        assertThat(result.getReason()).isNotNull();
    }

    @Test
    void map_shouldReturnLowConfidenceForUnknownType() {
        DataTypeMappingResult result = mapper.map("UNKNOWN_TYPE", 100, 0);

        assertThat(result.getMappedType()).isEqualTo("Unknown");
        assertThat(result.getConfidence()).isEqualTo(MappingConfidence.LOW);
        assertThat(result.getReason()).contains("未识别");
    }

    @Test
    void map_shouldReturnLowConfidenceForNullType() {
        DataTypeMappingResult result = mapper.map(null, 100, 0);

        assertThat(result.getMappedType()).isEqualTo("Unknown");
        assertThat(result.getConfidence()).isEqualTo(MappingConfidence.LOW);
    }

    @Test
    void map_shouldReturnLowConfidenceForBlankType() {
        DataTypeMappingResult result = mapper.map("   ", 100, 0);

        assertThat(result.getMappedType()).isEqualTo("Unknown");
        assertThat(result.getConfidence()).isEqualTo(MappingConfidence.LOW);
    }

    @Test
    void map_shouldBeCaseInsensitive() {
        DataTypeMappingResult result1 = mapper.map("varchar", 100, 0);
        DataTypeMappingResult result2 = mapper.map("VARCHAR", 100, 0);
        DataTypeMappingResult result3 = mapper.map("Varchar", 100, 0);

        assertThat(result1.getMappedType()).isEqualTo("String");
        assertThat(result2.getMappedType()).isEqualTo("String");
        assertThat(result3.getMappedType()).isEqualTo("String");
    }

    @Test
    void map_shouldHandleFloatTypes() {
        assertThat(mapper.map("FLOAT", null, null).getMappedType()).isEqualTo("Decimal");
        assertThat(mapper.map("REAL", null, null).getMappedType()).isEqualTo("Decimal");
    }
}
