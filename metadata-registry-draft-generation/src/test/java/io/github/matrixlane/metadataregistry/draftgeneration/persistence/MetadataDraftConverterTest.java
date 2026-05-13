package io.github.matrixlane.metadataregistry.draftgeneration.persistence;

import io.github.matrixlane.metadataregistry.draftgeneration.model.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataDraftConverterTest {

    @Test
    void toEntity_shouldConvertTableDraft() {
        TableDraft tableDraft = TableDraft.builder()
                .sourceTableName("tbl_user")
                .suggestedTableName("user")
                .remarks("User table")
                .extractedAt(LocalDateTime.now())
                .columnDrafts(List.of(
                        ColumnDraft.builder()
                                .sourceColumnName("user_name")
                                .suggestedColumnName("userName")
                                .sourceDataType("VARCHAR")
                                .mappedDataType("String")
                                .mappingConfidence(MappingConfidence.HIGH)
                                .columnSize(100)
                                .nullable(true)
                                .build()
                ))
                .build();

        MetadataDraftEntity entity = MetadataDraftConverter.toEntity(tableDraft, "TestDB", "dbo");

        assertThat(entity).isNotNull();
        assertThat(entity.getSourceDatabaseName()).isEqualTo("TestDB");
        assertThat(entity.getSourceSchemaName()).isEqualTo("dbo");
        assertThat(entity.getSourceTableName()).isEqualTo("tbl_user");
        assertThat(entity.getSuggestedTableName()).isEqualTo("user");
        assertThat(entity.getTableRemarks()).isEqualTo("User table");
        assertThat(entity.getStatus()).isEqualTo(DraftStatus.DRAFT);
        assertThat(entity.getColumns()).hasSize(1);

        ColumnDraftEntity col = entity.getColumns().get(0);
        assertThat(col.getSourceColumnName()).isEqualTo("user_name");
        assertThat(col.getMappedDataType()).isEqualTo("String");
        assertThat(col.getMappingConfidence()).isEqualTo(MappingConfidence.HIGH);
        assertThat(col.getColumnSize()).isEqualTo(100);
        assertThat(col.getNullable()).isTrue();
        assertThat(col.getMetadataDraft()).isEqualTo(entity);
    }

    @Test
    void toEntity_shouldHandleNullTableDraft() {
        assertThat(MetadataDraftConverter.toEntity(null, "db", "dbo")).isNull();
    }

    @Test
    void toEntity_shouldHandleNullColumns() {
        TableDraft tableDraft = TableDraft.builder()
                .sourceTableName("empty_table")
                .build();

        MetadataDraftEntity entity = MetadataDraftConverter.toEntity(tableDraft, "db", "dbo");

        assertThat(entity.getColumns()).isEmpty();
    }

    @Test
    void toEntity_shouldHandleNullDatabaseAndSchema() {
        TableDraft tableDraft = TableDraft.builder()
                .sourceTableName("test")
                .build();

        MetadataDraftEntity entity = MetadataDraftConverter.toEntity(tableDraft, null, null);

        assertThat(entity.getSourceDatabaseName()).isEqualTo("");
        assertThat(entity.getSourceSchemaName()).isEqualTo("");
    }

    @Test
    void toEntity_shouldConvertNamingSuggestion() {
        TableDraft tableDraft = TableDraft.builder()
                .sourceTableName("tbl_user")
                .suggestedTableName("user")
                .columnDrafts(List.of(
                        ColumnDraft.builder()
                                .sourceColumnName("user_name")
                                .suggestedColumnName("userName")
                                .sourceDataType("VARCHAR")
                                .mappedDataType("String")
                                .mappingConfidence(MappingConfidence.HIGH)
                                .namingSuggestion(NamingSuggestion.builder()
                                        .originalName("user_name")
                                        .suggestedName("userName")
                                        .ruleName("SNAKE_TO_CAMEL")
                                        .build())
                                .build()
                ))
                .build();

        MetadataDraftEntity entity = MetadataDraftConverter.toEntity(tableDraft, "db", "dbo");

        ColumnDraftEntity col = entity.getColumns().get(0);
        assertThat(col.getNamingRuleApplied()).isEqualTo("SNAKE_TO_CAMEL");
    }

    @Test
    void toEntities_shouldConvertResult() {
        DraftGenerationResult result = DraftGenerationResult.builder()
                .tableDrafts(List.of(
                        TableDraft.builder()
                                .sourceTableName("tbl_user")
                                .suggestedTableName("user")
                                .build(),
                        TableDraft.builder()
                                .sourceTableName("tbl_order")
                                .suggestedTableName("order")
                                .build()
                ))
                .sourceDatabaseName("TestDB")
                .sourceSchemaName("dbo")
                .build();

        List<MetadataDraftEntity> entities = MetadataDraftConverter.toEntities(result);

        assertThat(entities).hasSize(2);
        assertThat(entities.get(0).getSourceTableName()).isEqualTo("tbl_user");
        assertThat(entities.get(1).getSourceTableName()).isEqualTo("tbl_order");
    }

    @Test
    void toEntities_shouldReturnEmptyListForNullResult() {
        assertThat(MetadataDraftConverter.toEntities(null)).isEmpty();
    }

    @Test
    void toEntities_shouldReturnEmptyListForNullTableDrafts() {
        DraftGenerationResult result = DraftGenerationResult.builder().build();
        assertThat(MetadataDraftConverter.toEntities(result)).isEmpty();
    }
}
