package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class RegistrationLifecycleService {

    private static final Map<RegistrationStatus, Set<RegistrationStatus>> ALLOWED_TRANSITIONS;

    static {
        EnumMap<RegistrationStatus, Set<RegistrationStatus>> map = new EnumMap<>(RegistrationStatus.class);
        map.put(RegistrationStatus.INCOMPLETE,
                EnumSet.of(RegistrationStatus.CANDIDATE, RegistrationStatus.RETIRED));
        map.put(RegistrationStatus.CANDIDATE,
                EnumSet.of(RegistrationStatus.RECORDED, RegistrationStatus.RETIRED));
        map.put(RegistrationStatus.RECORDED,
                EnumSet.of(RegistrationStatus.QUALIFIED, RegistrationStatus.RETIRED));
        map.put(RegistrationStatus.QUALIFIED,
                EnumSet.of(RegistrationStatus.STANDARD, RegistrationStatus.RETIRED));
        map.put(RegistrationStatus.STANDARD,
                EnumSet.of(RegistrationStatus.PREFERRED_STANDARD, RegistrationStatus.SUPERSEDED,
                        RegistrationStatus.RETIRED));
        map.put(RegistrationStatus.PREFERRED_STANDARD,
                EnumSet.of(RegistrationStatus.STANDARD, RegistrationStatus.SUPERSEDED,
                        RegistrationStatus.RETIRED));
        map.put(RegistrationStatus.SUPERSEDED,
                EnumSet.of(RegistrationStatus.RETIRED, RegistrationStatus.HISTORICAL));
        map.put(RegistrationStatus.RETIRED,
                EnumSet.of(RegistrationStatus.HISTORICAL));
        map.put(RegistrationStatus.HISTORICAL, EnumSet.noneOf(RegistrationStatus.class));
        map.put(RegistrationStatus.APPLICATION,
                EnumSet.of(RegistrationStatus.STANDARD, RegistrationStatus.RETIRED));
        ALLOWED_TRANSITIONS = Map.copyOf(map);
    }

    private final AdministeredItemRepository administeredItemRepository;

    public AdministeredItem transition(UUID administeredItemId,
                                       RegistrationStatus to,
                                       String administrativeNote) {
        AdministeredItem ai = administeredItemRepository.findById(administeredItemId)
                .orElseThrow(() -> new ResourceNotFoundException("AdministeredItem", administeredItemId));

        RegistrationState current = ai.getRegistrationState();
        RegistrationStatus from = current == null ? RegistrationStatus.INCOMPLETE
                : current.getRegistrationStatus();

        if (from == to) {
            throw new BusinessException("当前状态已经是 " + to + "，无需迁移");
        }
        Set<RegistrationStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(RegistrationStatus.class));
        if (!allowed.contains(to)) {
            throw new BusinessException("非法的状态迁移：%s → %s".formatted(from, to));
        }

        LocalDateTime now = LocalDateTime.now();
        if (current != null) {
            current.setUntilDate(now);
        }
        RegistrationState next = new RegistrationState();
        next.setRegistrationStatus(to);
        next.setEffectiveDate(now);
        next.setPreviousState(current);
        next.setAdministrativeNote(administrativeNote);
        ai.setRegistrationState(next);
        ai.setLastChangeDateTime(now);
        ai.setChangeDescription("状态迁移：%s → %s".formatted(from, to));

        return administeredItemRepository.save(ai);
    }
}
