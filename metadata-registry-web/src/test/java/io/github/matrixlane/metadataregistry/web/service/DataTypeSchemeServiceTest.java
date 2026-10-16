package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.dataspecification.DataType;
import io.github.matrixlane.metadataregistry.dataspecification.DataTypeScheme;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.repository.DataTypeSchemeRepository;
import io.github.matrixlane.metadataregistry.web.repository.ReferenceDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataTypeSchemeServiceTest {

    @Mock
    private DataTypeSchemeRepository repository;

    @Mock
    private ReferenceDocumentRepository referenceDocumentRepository;

    @Mock
    private ItemAdministrationService itemAdministrationService;

    @InjectMocks
    private DataTypeSchemeService service;

    @Test
    void delete_dissociatesDataTypes_thenDeletesScheme() {
        UUID schemeId = UUID.randomUUID();
        DataTypeScheme scheme = new DataTypeScheme();
        scheme.setId(schemeId);
        DataType linked = new DataType();
        linked.setDataTypeScheme(scheme);
        List<DataType> linkedTypes = new ArrayList<>(List.of(linked));
        scheme.setDataTypes(linkedTypes);
        when(repository.findById(schemeId)).thenReturn(Optional.of(scheme));

        service.delete(schemeId);

        assertThat(linked.getDataTypeScheme()).isNull();
        var ordered = Mockito.inOrder(itemAdministrationService, repository);
        ordered.verify(itemAdministrationService).assertDeletable(scheme);
        ordered.verify(itemAdministrationService).removeFromSubmissionRecords(scheme);
        ordered.verify(repository).deleteById(schemeId);
    }

    @Test
    void delete_missingScheme_throwsNotFound() {
        UUID schemeId = UUID.randomUUID();
        when(repository.findById(schemeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(schemeId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
