package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.DocumentType;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.documenttype.DocumentTypeRequest;
import io.github.matrixlane.metadataregistry.web.dto.documenttype.DocumentTypeResponse;
import io.github.matrixlane.metadataregistry.web.repository.DocumentTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DocumentTypeService {

    private final DocumentTypeRepository repository;

    @Transactional(readOnly = true)
    public Page<DocumentTypeResponse> list(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public DocumentTypeResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public DocumentTypeResponse create(DocumentTypeRequest request) {
        if (repository.existsByIdentifier(request.identifier())) {
            throw new BusinessException("DocumentType identifier 已存在：" + request.identifier());
        }
        DocumentType entity = new DocumentType();
        applyRequest(entity, request);
        return toResponse(repository.save(entity));
    }

    public DocumentTypeResponse update(UUID id, DocumentTypeRequest request) {
        DocumentType entity = loadOrThrow(id);
        if (!entity.getIdentifier().equals(request.identifier())
                && repository.existsByIdentifier(request.identifier())) {
            throw new BusinessException("DocumentType identifier 已存在：" + request.identifier());
        }
        applyRequest(entity, request);
        return toResponse(repository.save(entity));
    }

    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("DocumentType", id);
        }
        repository.deleteById(id);
    }

    private DocumentType loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DocumentType", id));
    }

    private void applyRequest(DocumentType entity, DocumentTypeRequest request) {
        entity.setIdentifier(request.identifier());
        entity.setDescription(request.description() == null ? null : Text.of(request.description()));
        entity.setSchemeReference(
                request.schemeReference() == null ? null : Sign.of(request.schemeReference()));
    }

    private DocumentTypeResponse toResponse(DocumentType entity) {
        return new DocumentTypeResponse(
                entity.getId(),
                entity.getIdentifier(),
                entity.getDescription() == null ? null : entity.getDescription().getValue(),
                entity.getSchemeReference() == null ? null : entity.getSchemeReference().getValue());
    }
}
