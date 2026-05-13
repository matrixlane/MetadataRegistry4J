package io.github.matrixlane.metadataregistry.draftgeneration.persistence;

import io.github.matrixlane.metadataregistry.draftgeneration.model.DraftStatus;
import io.github.matrixlane.metadataregistry.draftgeneration.model.MappingConfidence;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetadataDraftEntityTest {

    @Test
    void constructor_shouldInitializeDefaults() {
        MetadataDraftEntity entity = new MetadataDraftEntity("TestDB", "dbo", "user", "user");

        assertThat(entity.getSourceDatabaseName()).isEqualTo("TestDB");
        assertThat(entity.getSourceSchemaName()).isEqualTo("dbo");
        assertThat(entity.getSourceTableName()).isEqualTo("user");
        assertThat(entity.getSuggestedTableName()).isEqualTo("user");
        assertThat(entity.getStatus()).isEqualTo(DraftStatus.DRAFT);
        assertThat(entity.getExtractedAt()).isNotNull();
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getColumns()).isEmpty();
    }

    @Test
    void addColumn_shouldSetBidirectionalRelationship() {
        MetadataDraftEntity draft = new MetadataDraftEntity("db", "dbo", "user", "user");
        ColumnDraftEntity column = new ColumnDraftEntity("name", "name", "VARCHAR", "String", MappingConfidence.HIGH);

        draft.addColumn(column);

        assertThat(draft.getColumns()).containsExactly(column);
        assertThat(column.getMetadataDraft()).isEqualTo(draft);
    }

    @Test
    void removeColumn_shouldClearRelationship() {
        MetadataDraftEntity draft = new MetadataDraftEntity("db", "dbo", "user", "user");
        ColumnDraftEntity column = new ColumnDraftEntity("name", "name", "VARCHAR", "String", MappingConfidence.HIGH);
        draft.addColumn(column);

        draft.removeColumn(column);

        assertThat(draft.getColumns()).isEmpty();
        assertThat(column.getMetadataDraft()).isNull();
    }

    @Test
    void canTransitionTo_shouldAllowDraftToApproved() {
        MetadataDraftEntity draft = new MetadataDraftEntity("db", "dbo", "user", "user");

        assertThat(draft.canTransitionTo(DraftStatus.APPROVED)).isTrue();
    }

    @Test
    void canTransitionTo_shouldAllowDraftToRejected() {
        MetadataDraftEntity draft = new MetadataDraftEntity("db", "dbo", "user", "user");

        assertThat(draft.canTransitionTo(DraftStatus.REJECTED)).isTrue();
    }

    @Test
    void canTransitionTo_shouldNotAllowSameStatus() {
        MetadataDraftEntity draft = new MetadataDraftEntity("db", "dbo", "user", "user");

        assertThat(draft.canTransitionTo(DraftStatus.DRAFT)).isFalse();
    }

    @Test
    void canTransitionTo_shouldNotAllowApprovedToRejected() {
        MetadataDraftEntity draft = new MetadataDraftEntity("db", "dbo", "user", "user");
        draft.transitionTo(DraftStatus.APPROVED);

        assertThat(draft.canTransitionTo(DraftStatus.REJECTED)).isFalse();
    }

    @Test
    void canTransitionTo_shouldNotAllowRejectedToApproved() {
        MetadataDraftEntity draft = new MetadataDraftEntity("db", "dbo", "user", "user");
        draft.transitionTo(DraftStatus.REJECTED);

        assertThat(draft.canTransitionTo(DraftStatus.APPROVED)).isFalse();
    }

    @Test
    void transitionTo_shouldUpdateStatus() {
        MetadataDraftEntity draft = new MetadataDraftEntity("db", "dbo", "user", "user");

        draft.transitionTo(DraftStatus.APPROVED);

        assertThat(draft.getStatus()).isEqualTo(DraftStatus.APPROVED);
        assertThat(draft.getUpdatedAt()).isNotNull();
    }

    @Test
    void transitionTo_shouldThrowForInvalidTransition() {
        MetadataDraftEntity draft = new MetadataDraftEntity("db", "dbo", "user", "user");
        draft.transitionTo(DraftStatus.APPROVED);

        assertThatThrownBy(() -> draft.transitionTo(DraftStatus.REJECTED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition from APPROVED to REJECTED");
    }

    @Test
    void transitionTo_shouldThrowForSameStatus() {
        MetadataDraftEntity draft = new MetadataDraftEntity("db", "dbo", "user", "user");

        assertThatThrownBy(() -> draft.transitionTo(DraftStatus.DRAFT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition from DRAFT to DRAFT");
    }
}
