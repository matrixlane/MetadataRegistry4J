package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Individual;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.PhoneNumber;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.PostalAddress;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.individual.IndividualRequest;
import io.github.matrixlane.metadataregistry.web.dto.individual.IndividualResponse;
import io.github.matrixlane.metadataregistry.web.repository.IndividualRepository;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class IndividualService {

    private final IndividualRepository repository;

    /**
     * 个人列表（排除已绑定停用账号的 Individual），关键词按姓名与称谓检索。
     *
     * <p>维持不加标识符匹配：Individual 是独立根实体（{@code Individual extends Entity}），
     * 不在受管链上，不存在 Scoped_Identifier 行。</p>
     */
    @Transactional(readOnly = true)
    public Page<IndividualResponse> list(String keyword, Pageable pageable) {
        Specification<Individual> spec = Specification.where((root, query, cb) -> {
            jakarta.persistence.criteria.Subquery<Long> subquery = query.subquery(Long.class);
            jakarta.persistence.criteria.Root<Account> userRoot = subquery.from(Account.class);
            subquery.select(cb.literal(1L))
                    .where(
                        cb.equal(userRoot.get("individual"), root),
                        cb.equal(userRoot.get("enabled"), false)
                    );
            return cb.not(cb.exists(subquery));
        });
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name").get("value")), like),
                    cb.like(cb.lower(root.get("title")), like)));
        }
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public IndividualResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public IndividualResponse create(IndividualRequest request) {
        Individual entity = new Individual();
        applyRequest(entity, request);
        return toResponse(repository.save(entity));
    }

    public IndividualResponse update(UUID id, IndividualRequest request) {
        Individual entity = loadOrThrow(id);
        applyRequest(entity, request);
        return toResponse(repository.save(entity));
    }

    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Individual", id);
        }
        repository.deleteById(id);
    }

    private Individual loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Individual", id));
    }

    private void applyRequest(Individual entity, IndividualRequest request) {
        entity.setName(Sign.of(request.name()));
        entity.setTitle(request.title());
        entity.setMailAddress(request.mailAddress() == null
                ? new PostalAddress()
                : PostalAddress.of(request.mailAddress()));

        entity.getEmailAddress().clear();
        if (request.emailAddress() != null) {
            for (String e : request.emailAddress()) {
                entity.getEmailAddress().add(e);
            }
        }
        entity.getPhoneNumber().clear();
        if (request.phoneNumber() != null) {
            for (String p : request.phoneNumber()) {
                entity.getPhoneNumber().add(PhoneNumber.of(p));
            }
        }
    }

    private IndividualResponse toResponse(Individual entity) {
        return new IndividualResponse(
                entity.getId(),
                entity.getName() == null ? null : entity.getName().getValue(),
                entity.getTitle(),
                entity.getMailAddress() == null ? null : entity.getMailAddress().getValue(),
                entity.getEmailAddress().stream().toList(),
                entity.getPhoneNumber().stream().map(PhoneNumber::getValue).toList());
    }
}
