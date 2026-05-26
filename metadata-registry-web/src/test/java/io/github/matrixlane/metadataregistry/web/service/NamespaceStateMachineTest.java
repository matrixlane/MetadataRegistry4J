package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.NamespaceStatus;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Namespace State Machine Tests")
class NamespaceStateMachineTest {

    @Mock
    private NamespaceRepository namespaceRepository;

    @Mock
    private ApprovalService approvalService;

    @InjectMocks
    private NamespaceStateMachine stateMachine;

    private UUID namespaceId;
    private Namespace namespace;

    @BeforeEach
    void setUp() {
        namespaceId = UUID.randomUUID();
        namespace = new Namespace();
        namespace.setId(namespaceId);
        namespace.setNamespaceStatus(NamespaceStatus.DRAFT);
    }

    @Nested
    @DisplayName("State Transition Validation")
    class StateTransitionTests {

        static Stream<Arguments> validTransitions() {
            return Stream.of(
                    Arguments.of(NamespaceStatus.DRAFT, NamespaceStatus.SUBMITTED),
                    Arguments.of(NamespaceStatus.SUBMITTED, NamespaceStatus.ACTIVE),
                    Arguments.of(NamespaceStatus.SUBMITTED, NamespaceStatus.DRAFT),
                    Arguments.of(NamespaceStatus.ACTIVE, NamespaceStatus.DEPRECATED),
                    Arguments.of(NamespaceStatus.DEPRECATED, NamespaceStatus.OBSOLETE),
                    Arguments.of(NamespaceStatus.DEPRECATED, NamespaceStatus.ACTIVE)
            );
        }

        @ParameterizedTest(name = "{0} → {1} should be valid")
        @MethodSource("validTransitions")
        @DisplayName("Valid state transitions should be allowed")
        void validTransitions(NamespaceStatus from, NamespaceStatus target) {
            assertThat(stateMachine.canTransition(from, target)).isTrue();
        }

        static Stream<Arguments> invalidTransitions() {
            return Stream.of(
                    Arguments.of(NamespaceStatus.DRAFT, NamespaceStatus.ACTIVE),
                    Arguments.of(NamespaceStatus.DRAFT, NamespaceStatus.DEPRECATED),
                    Arguments.of(NamespaceStatus.DRAFT, NamespaceStatus.OBSOLETE),
                    Arguments.of(NamespaceStatus.ACTIVE, NamespaceStatus.DRAFT),
                    Arguments.of(NamespaceStatus.ACTIVE, NamespaceStatus.SUBMITTED),
                    Arguments.of(NamespaceStatus.ACTIVE, NamespaceStatus.OBSOLETE),
                    Arguments.of(NamespaceStatus.SUBMITTED, NamespaceStatus.DEPRECATED),
                    Arguments.of(NamespaceStatus.SUBMITTED, NamespaceStatus.OBSOLETE),
                    Arguments.of(NamespaceStatus.DEPRECATED, NamespaceStatus.DRAFT),
                    Arguments.of(NamespaceStatus.DEPRECATED, NamespaceStatus.SUBMITTED),
                    Arguments.of(NamespaceStatus.OBSOLETE, NamespaceStatus.ACTIVE),
                    Arguments.of(NamespaceStatus.OBSOLETE, NamespaceStatus.DRAFT),
                    Arguments.of(NamespaceStatus.OBSOLETE, NamespaceStatus.SUBMITTED),
                    Arguments.of(NamespaceStatus.OBSOLETE, NamespaceStatus.DEPRECATED)
            );
        }

        @ParameterizedTest(name = "{0} → {1} should be invalid")
        @MethodSource("invalidTransitions")
        @DisplayName("Invalid state transitions should be rejected")
        void invalidTransitions(NamespaceStatus from, NamespaceStatus target) {
            assertThat(stateMachine.canTransition(from, target)).isFalse();
        }
    }

    @Nested
    @DisplayName("Submit for Approval")
    class SubmitTests {

        @Test
        @DisplayName("DRAFT namespace can be submitted")
        void submit_fromDraft_shouldSucceed() {
            namespace.setNamespaceStatus(NamespaceStatus.DRAFT);
            when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(namespace));
            when(namespaceRepository.save(namespace)).thenReturn(namespace);

            Namespace result = stateMachine.submit(namespaceId);

            assertThat(result.getNamespaceStatus()).isEqualTo(NamespaceStatus.SUBMITTED);
        }

        @Test
        @DisplayName("Non-DRAFT namespace cannot be submitted")
        void submit_fromNonDraft_shouldFail() {
            namespace.setNamespaceStatus(NamespaceStatus.ACTIVE);
            when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(namespace));

            assertThatThrownBy(() -> stateMachine.submit(namespaceId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Invalid state transition");
        }
    }

    @Nested
    @DisplayName("Approval Callback")
    class ApproveTests {

        @Test
        @DisplayName("SUBMITTED namespace can be approved to ACTIVE")
        void approve_fromSubmitted_shouldSucceed() {
            namespace.setNamespaceStatus(NamespaceStatus.SUBMITTED);
            when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(namespace));
            when(namespaceRepository.save(namespace)).thenReturn(namespace);

            Namespace result = stateMachine.approve(namespaceId);

            assertThat(result.getNamespaceStatus()).isEqualTo(NamespaceStatus.ACTIVE);
        }

        @Test
        @DisplayName("Non-SUBMITTED namespace cannot be approved")
        void approve_fromNonSubmitted_shouldFail() {
            namespace.setNamespaceStatus(NamespaceStatus.DRAFT);
            when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(namespace));

            assertThatThrownBy(() -> stateMachine.approve(namespaceId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("not in SUBMITTED state");
        }
    }

    @Nested
    @DisplayName("Deprecate")
    class DeprecateTests {

        @Test
        @DisplayName("ACTIVE namespace can be deprecated")
        void deprecate_fromActive_shouldSucceed() {
            namespace.setNamespaceStatus(NamespaceStatus.ACTIVE);
            when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(namespace));
            when(namespaceRepository.save(namespace)).thenReturn(namespace);

            Namespace result = stateMachine.deprecate(namespaceId);

            assertThat(result.getNamespaceStatus()).isEqualTo(NamespaceStatus.DEPRECATED);
        }
    }

    @Nested
    @DisplayName("Obsolete")
    class ObsoleteTests {

        @Test
        @DisplayName("DEPRECATED namespace can be obsoleted")
        void obsolete_fromDeprecated_shouldSucceed() {
            namespace.setNamespaceStatus(NamespaceStatus.DEPRECATED);
            when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(namespace));
            when(namespaceRepository.save(namespace)).thenReturn(namespace);

            Namespace result = stateMachine.obsolete(namespaceId);

            assertThat(result.getNamespaceStatus()).isEqualTo(NamespaceStatus.OBSOLETE);
        }

        @Test
        @DisplayName("OBSOLETE namespace has no allowed transitions")
        void obsolete_hasNoTransitions() {
            // getAllowedTransitions is a pure method - no repository call needed
            Set<NamespaceStatus> allowed = stateMachine.getAllowedTransitions(NamespaceStatus.OBSOLETE);
            assertThat(allowed).isEmpty();
        }
    }

    @Nested
    @DisplayName("Return to Draft")
    class ReturnToDraftTests {

        @Test
        @DisplayName("SUBMITTED namespace can return to DRAFT")
        void returnToDraft_fromSubmitted_shouldSucceed() {
            namespace.setNamespaceStatus(NamespaceStatus.SUBMITTED);
            when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(namespace));
            when(namespaceRepository.save(namespace)).thenReturn(namespace);

            Namespace result = stateMachine.returnToDraft(namespaceId);

            assertThat(result.getNamespaceStatus()).isEqualTo(NamespaceStatus.DRAFT);
        }

        @Test
        @DisplayName("ACTIVE namespace cannot return to DRAFT")
        void returnToDraft_fromActive_shouldFail() {
            namespace.setNamespaceStatus(NamespaceStatus.ACTIVE);
            when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(namespace));

            assertThatThrownBy(() -> stateMachine.returnToDraft(namespaceId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cannot return namespace to DRAFT");
        }
    }

    @Nested
    @DisplayName("Reactivate")
    class ReactivateTests {

        @Test
        @DisplayName("DEPRECATED namespace can be reactivated to ACTIVE")
        void reactivate_fromDeprecated_shouldSucceed() {
            namespace.setNamespaceStatus(NamespaceStatus.DEPRECATED);
            when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(namespace));
            when(namespaceRepository.save(namespace)).thenReturn(namespace);

            Namespace result = stateMachine.reactivate(namespaceId);

            assertThat(result.getNamespaceStatus()).isEqualTo(NamespaceStatus.ACTIVE);
        }

        @Test
        @DisplayName("OBSOLETE namespace cannot be reactivated")
        void reactivate_fromObsolete_shouldFail() {
            namespace.setNamespaceStatus(NamespaceStatus.OBSOLETE);
            when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(namespace));

            assertThatThrownBy(() -> stateMachine.reactivate(namespaceId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Invalid state transition");
        }
    }

    @Nested
    @DisplayName("Allowed Transitions")
    class AllowedTransitionsTests {

        @Test
        @DisplayName("DRAFT has only SUBMITTED as allowed transition")
        void draft_allowedTransitions() {
            Set<NamespaceStatus> allowed = stateMachine.getAllowedTransitions(NamespaceStatus.DRAFT);
            assertThat(allowed).containsExactly(NamespaceStatus.SUBMITTED);
        }

        @Test
        @DisplayName("SUBMITTED has ACTIVE and DRAFT as allowed transitions")
        void submitted_allowedTransitions() {
            Set<NamespaceStatus> allowed = stateMachine.getAllowedTransitions(NamespaceStatus.SUBMITTED);
            assertThat(allowed).containsExactlyInAnyOrder(NamespaceStatus.ACTIVE, NamespaceStatus.DRAFT);
        }

        @Test
        @DisplayName("ACTIVE has only DEPRECATED as allowed transition")
        void active_allowedTransitions() {
            Set<NamespaceStatus> allowed = stateMachine.getAllowedTransitions(NamespaceStatus.ACTIVE);
            assertThat(allowed).containsExactly(NamespaceStatus.DEPRECATED);
        }
    }
}