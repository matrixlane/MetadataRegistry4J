package io.github.matrixlane.metadataregistry.draftgeneration.service;

import io.github.matrixlane.metadataregistry.draftgeneration.generator.MetadataDraftGenerator;
import io.github.matrixlane.metadataregistry.draftgeneration.model.DraftGenerationResult;
import io.github.matrixlane.metadataregistry.draftgeneration.model.DraftStatus;
import io.github.matrixlane.metadataregistry.draftgeneration.model.MappingConfidence;
import io.github.matrixlane.metadataregistry.draftgeneration.persistence.ColumnDraftEntity;
import io.github.matrixlane.metadataregistry.draftgeneration.persistence.MetadataDraftEntity;
import io.github.matrixlane.metadataregistry.schemaextraction.model.ColumnInfo;
import io.github.matrixlane.metadataregistry.schemaextraction.model.DatabaseSchema;
import io.github.matrixlane.metadataregistry.schemaextraction.model.TableInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetadataDraftServiceTest {

    private MetadataDraftService service;

    @BeforeEach
    void setUp() {
        service = new MetadataDraftService(new MetadataDraftGenerator());
    }

    @Test
    void generateDrafts_shouldReturnResult() {
        DatabaseSchema schema = createTestSchema();

        DraftGenerationResult result = service.generateDrafts(schema);

        assertThat(result.getTotalTables()).isEqualTo(1);
        assertThat(result.getTotalColumns()).isEqualTo(2);
    }

    @Test
    void generateDrafts_shouldHandleNullSchema() {
        DraftGenerationResult result = service.generateDrafts(null);

        assertThat(result.getTotalTables()).isZero();
        assertThat(result.getTotalColumns()).isZero();
    }

    @Test
    void generateDraftEntities_shouldReturnEntities() {
        DatabaseSchema schema = createTestSchema();

        List<MetadataDraftEntity> entities = service.generateDraftEntities(schema);

        assertThat(entities).hasSize(1);
        assertThat(entities.get(0).getSourceTableName()).isEqualTo("tbl_user");
        assertThat(entities.get(0).getColumns()).hasSize(2);
    }

    @Test
    void generateDraftEntities_shouldHandleEmptySchema() {
        DatabaseSchema schema = DatabaseSchema.builder().build();

        List<MetadataDraftEntity> entities = service.generateDraftEntities(schema);

        assertThat(entities).isEmpty();
    }

    @Test
    void approve_shouldTransitionToApproved() {
        MetadataDraftEntity draft = new MetadataDraftEntity("db", "dbo", "user", "user");

        service.approve(draft);

        assertThat(draft.getStatus()).isEqualTo(DraftStatus.APPROVED);
    }

    @Test
    void approve_shouldThrowForNull() {
        assertThatThrownBy(() -> service.approve(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Draft cannot be null");
    }

    @Test
    void reject_shouldTransitionToRejected() {
        MetadataDraftEntity draft = new MetadataDraftEntity("db", "dbo", "user", "user");

        service.reject(draft);

        assertThat(draft.getStatus()).isEqualTo(DraftStatus.REJECTED);
    }

    @Test
    void reject_shouldThrowForNull() {
        assertThatThrownBy(() -> service.reject(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Draft cannot be null");
    }

    @Test
    void findLowConfidenceDrafts_shouldReturnMatchingDrafts() {
        MetadataDraftEntity draft1 = new MetadataDraftEntity("db", "dbo", "user", "user");
        draft1.addColumn(new ColumnDraftEntity("name", "name", "VARCHAR", "String", MappingConfidence.HIGH));

        MetadataDraftEntity draft2 = new MetadataDraftEntity("db", "dbo", "custom", "custom");
        draft2.addColumn(new ColumnDraftEntity("data", "data", "CUSTOM", "Unknown", MappingConfidence.LOW));

        List<MetadataDraftEntity> drafts = List.of(draft1, draft2);
        List<MetadataDraftEntity> lowConfidence = service.findLowConfidenceDrafts(drafts);

        assertThat(lowConfidence).hasSize(1);
        assertThat(lowConfidence.get(0).getSourceTableName()).isEqualTo("custom");
    }

    @Test
    void findLowConfidenceDrafts_shouldReturnEmptyForNoMatches() {
        MetadataDraftEntity draft = new MetadataDraftEntity("db", "dbo", "user", "user");
        draft.addColumn(new ColumnDraftEntity("name", "name", "VARCHAR", "String", MappingConfidence.HIGH));

        List<MetadataDraftEntity> lowConfidence = service.findLowConfidenceDrafts(List.of(draft));

        assertThat(lowConfidence).isEmpty();
    }

    @Test
    void findLowConfidenceDrafts_shouldHandleNullList() {
        List<MetadataDraftEntity> lowConfidence = service.findLowConfidenceDrafts(null);

        assertThat(lowConfidence).isEmpty();
    }

    @Test
    void findLowConfidenceDrafts_shouldHandleEmptyList() {
        List<MetadataDraftEntity> lowConfidence = service.findLowConfidenceDrafts(List.of());

        assertThat(lowConfidence).isEmpty();
    }

    private DatabaseSchema createTestSchema() {
        List<ColumnInfo> columns = new ArrayList<>();
        columns.add(ColumnInfo.builder()
                .columnName("user_name")
                .dataType("VARCHAR")
                .columnSize(100)
                .build());
        columns.add(ColumnInfo.builder()
                .columnName("created_at")
                .dataType("DATETIME")
                .build());

        return DatabaseSchema.builder()
                .databaseProductName("Microsoft SQL Server")
                .schema("dbo")
                .tables(List.of(
                        TableInfo.builder()
                                .tableName("tbl_user")
                                .columns(columns)
                                .build()
                ))
                .build();
    }
}
