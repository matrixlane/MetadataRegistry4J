package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.dataspecification.ClassificationNode;
import io.github.matrixlane.metadataregistry.dataspecification.ClassificationScheme;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.classification.ClassificationNodeRequest;
import io.github.matrixlane.metadataregistry.web.dto.classification.ClassificationNodeResponse;
import io.github.matrixlane.metadataregistry.web.dto.classification.ClassificationSchemeRequest;
import io.github.matrixlane.metadataregistry.web.dto.classification.ClassificationSchemeResponse;
import io.github.matrixlane.metadataregistry.web.dto.classification.ClassificationTreeNode;
import io.github.matrixlane.metadataregistry.web.dto.classification.MoveNodeRequest;
import io.github.matrixlane.metadataregistry.web.repository.ClassificationNodeRepository;
import io.github.matrixlane.metadataregistry.web.repository.ClassificationSchemeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassificationSchemeServiceTest {

    @Mock
    private ClassificationSchemeRepository schemeRepository;

    @Mock
    private ClassificationNodeRepository nodeRepository;

    @InjectMocks
    private ClassificationSchemeService service;

    private UUID schemeId;
    private UUID nodeId;
    private UUID parentId;
    private ClassificationScheme scheme;
    private ClassificationNode node;
    private ClassificationNode parentNode;

    @BeforeEach
    void setUp() {
        schemeId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        nodeId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        parentId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

        scheme = new ClassificationScheme("Test Scheme");
        scheme.setId(schemeId);
        scheme.setDescription("Test Desc");
        scheme.setVersion("1.0");
        scheme.setStatus("DRAFT");

        node = new ClassificationNode("N01", "Node1", scheme);
        node.setId(nodeId);
        node.setDescription("Desc1");
        node.setDefinition("Def1");
        node.setSortOrder(1);

        parentNode = new ClassificationNode("P01", "Parent1", scheme);
        parentNode.setId(parentId);
        parentNode.setSortOrder(0);
    }

    // Helper: save mock that properly syncs parentId from parent
    private ClassificationNode mockSaveReturnsWithParentIdSync() {
        return mockSaveReturns(null);
    }

    private ClassificationNode mockSaveReturns(UUID explicitParentId) {
        when(nodeRepository.save(any(ClassificationNode.class))).thenAnswer(i -> {
            ClassificationNode n = i.getArgument(0);
            n.setId(nodeId);
            // Sync parentId to reflect what JPA would do after save:
            // - If parent is set via setParent(), derive parentId from it
            // - If parent is set to null, clear parentId
            if (explicitParentId != null) {
                n.setParentId(explicitParentId);
            } else if (n.getParent() != null) {
                n.setParentId(n.getParent().getId());
            } else {
                n.setParentId(null);
            }
            return n;
        });
        return node;
    }

    // ==================== Scheme CRUD Tests ====================

    @Test
    void listSchemes_shouldReturnPagedResults() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<ClassificationScheme> page = new PageImpl<>(List.of(scheme), pageable, 1);
        when(schemeRepository.findAll(pageable)).thenReturn(page);

        Page<ClassificationSchemeResponse> result = service.listSchemes(null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getSchemeName()).isEqualTo("Test Scheme");
    }

    @Test
    void listSchemes_shouldFilterByKeyword() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<ClassificationScheme> page = new PageImpl<>(List.of(scheme), pageable, 1);
        when(schemeRepository.findBySchemeNameContainingIgnoreCase("test", pageable)).thenReturn(page);

        Page<ClassificationSchemeResponse> result = service.listSchemes("test", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getSchemeName()).isEqualTo("Test Scheme");
    }

    @Test
    void getScheme_shouldReturnScheme() {
        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));

        ClassificationSchemeResponse result = service.getScheme(schemeId);

        assertThat(result.getId()).isEqualTo(schemeId);
        assertThat(result.getSchemeName()).isEqualTo("Test Scheme");
    }

    @Test
    void getScheme_shouldThrowWhenNotFound() {
        when(schemeRepository.findById(schemeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getScheme(schemeId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ApiCodes.NOT_FOUND));
    }

    @Test
    void createScheme_shouldCreateWithDefaultStatus() {
        ClassificationSchemeRequest request = new ClassificationSchemeRequest();
        request.setSchemeName("New Scheme");
        request.setDescription("New Desc");
        request.setVersion("1.0");

        when(schemeRepository.save(any(ClassificationScheme.class))).thenAnswer(i -> {
            ClassificationScheme s = i.getArgument(0);
            s.setId(schemeId);
            return s;
        });

        ClassificationSchemeResponse result = service.createScheme(request);

        assertThat(result.getSchemeName()).isEqualTo("New Scheme");
        assertThat(result.getStatus()).isEqualTo("DRAFT");
        verify(schemeRepository).save(any(ClassificationScheme.class));
    }

    @Test
    void createScheme_shouldRespectProvidedStatus() {
        ClassificationSchemeRequest request = new ClassificationSchemeRequest();
        request.setSchemeName("New Scheme");
        request.setStatus("PUBLISHED");

        when(schemeRepository.save(any(ClassificationScheme.class))).thenAnswer(i -> {
            ClassificationScheme s = i.getArgument(0);
            s.setId(schemeId);
            return s;
        });

        ClassificationSchemeResponse result = service.createScheme(request);

        assertThat(result.getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    void updateScheme_shouldUpdateFields() {
        ClassificationSchemeRequest request = new ClassificationSchemeRequest();
        request.setSchemeName("Updated");
        request.setDescription("Updated Desc");
        request.setVersion("2.0");
        request.setStatus("PUBLISHED");

        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));
        when(schemeRepository.save(any(ClassificationScheme.class))).thenReturn(scheme);

        ClassificationSchemeResponse result = service.updateScheme(schemeId, request);

        assertThat(result.getSchemeName()).isEqualTo("Updated");
        assertThat(result.getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    void updateScheme_shouldThrowWhenNotFound() {
        when(schemeRepository.findById(schemeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateScheme(schemeId, new ClassificationSchemeRequest()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ApiCodes.NOT_FOUND));
    }

    @Test
    void deleteScheme_shouldDeleteWhenNoNodes() {
        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));
        when(nodeRepository.countByClassificationSchemeId(schemeId)).thenReturn(0L);
        doNothing().when(schemeRepository).delete(scheme);

        service.deleteScheme(schemeId);

        verify(schemeRepository).delete(scheme);
    }

    @Test
    void deleteScheme_shouldRejectWhenNodesExist() {
        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));
        when(nodeRepository.countByClassificationSchemeId(schemeId)).thenReturn(5L);

        assertThatThrownBy(() -> service.deleteScheme(schemeId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ApiCodes.VALIDATION_FAILED))
                .hasMessageContaining("5");
    }

    // ==================== Node CRUD Tests ====================

    @Test
    void getNode_shouldReturnNode() {
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));

        ClassificationNodeResponse result = service.getNode(nodeId);

        assertThat(result.getId()).isEqualTo(nodeId);
        assertThat(result.getNodeCode()).isEqualTo("N01");
        assertThat(result.getSchemeId()).isEqualTo(schemeId);
    }

    @Test
    void getNode_shouldThrowWhenNotFound() {
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getNode(nodeId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ApiCodes.NOT_FOUND));
    }

    @Test
    void createNode_shouldCreateRootNode() {
        ClassificationNodeRequest request = new ClassificationNodeRequest();
        request.setSchemeId(schemeId);
        request.setNodeCode("N02");
        request.setNodeName("Node2");
        request.setSortOrder(2);

        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));
        when(nodeRepository.save(any(ClassificationNode.class))).thenAnswer(i -> {
            ClassificationNode n = i.getArgument(0);
            n.setId(nodeId);
            return n;
        });

        ClassificationNodeResponse result = service.createNode(request);

        assertThat(result.getNodeCode()).isEqualTo("N02");
        assertThat(result.getParentId()).isNull();
        assertThat(result.getSortOrder()).isEqualTo(2);
    }

    @Test
    void createNode_shouldCreateChildNode() {
        ClassificationNodeRequest request = new ClassificationNodeRequest();
        request.setSchemeId(schemeId);
        request.setNodeCode("N02");
        request.setNodeName("Node2");
        request.setParentId(parentId);

        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));
        when(nodeRepository.findById(parentId)).thenReturn(Optional.of(parentNode));
        mockSaveReturnsWithParentIdSync();

        ClassificationNodeResponse result = service.createNode(request);

        assertThat(result.getParentId()).isEqualTo(parentId);
    }

    @Test
    void createNode_shouldThrowWhenSchemeNotFound() {
        ClassificationNodeRequest request = new ClassificationNodeRequest();
        request.setSchemeId(schemeId);
        request.setNodeCode("N02");
        request.setNodeName("Node2");

        when(schemeRepository.findById(schemeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createNode(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ApiCodes.NOT_FOUND));
    }

    @Test
    void createNode_shouldThrowWhenParentNotFound() {
        ClassificationNodeRequest request = new ClassificationNodeRequest();
        request.setSchemeId(schemeId);
        request.setNodeCode("N02");
        request.setNodeName("Node2");
        request.setParentId(parentId);

        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));
        when(nodeRepository.findById(parentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createNode(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ApiCodes.NOT_FOUND))
                .hasMessageContaining("父节点不存在");
    }

    @Test
    void updateNode_shouldUpdateFields() {
        ClassificationNodeRequest request = new ClassificationNodeRequest();
        request.setNodeCode("N01-U");
        request.setNodeName("Node1-U");
        request.setSortOrder(10);

        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(nodeRepository.save(any(ClassificationNode.class))).thenReturn(node);

        ClassificationNodeResponse result = service.updateNode(nodeId, request);

        assertThat(result.getNodeCode()).isEqualTo("N01-U");
        assertThat(result.getSortOrder()).isEqualTo(10);
    }

    @Test
    void updateNode_shouldChangeParent() {
        ClassificationNodeRequest request = new ClassificationNodeRequest();
        request.setNodeCode("N01");
        request.setNodeName("Node1");
        request.setParentId(parentId);

        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(nodeRepository.findById(parentId)).thenReturn(Optional.of(parentNode));
        mockSaveReturnsWithParentIdSync();

        ClassificationNodeResponse result = service.updateNode(nodeId, request);

        assertThat(result.getParentId()).isEqualTo(parentId);
    }

    @Test
    void updateNode_shouldRemoveParent() {
        node.setParent(parentNode);
        node.setParentId(parentId);
        ClassificationNodeRequest request = new ClassificationNodeRequest();
        request.setNodeCode("N01");
        request.setNodeName("Node1");
        request.setParentId(null);

        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(nodeRepository.save(any(ClassificationNode.class))).thenAnswer(i -> {
            ClassificationNode n = i.getArgument(0);
            if (n.getParent() == null) {
                n.setParentId(null);
            }
            return n;
        });

        ClassificationNodeResponse result = service.updateNode(nodeId, request);

        assertThat(result.getParentId()).isNull();
    }

    // ==================== Move Node Tests ====================

    @Test
    void moveNode_shouldMoveToNewParent() {
        MoveNodeRequest request = new MoveNodeRequest();
        request.setTargetParentId(parentId);

        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(nodeRepository.findById(parentId)).thenReturn(Optional.of(parentNode));
        mockSaveReturnsWithParentIdSync();

        ClassificationNodeResponse result = service.moveNode(nodeId, request);

        assertThat(result.getParentId()).isEqualTo(parentId);
    }

    @Test
    void moveNode_shouldRejectCircularReference() {
        // node is parent of parentNode, trying to move node under parentNode
        // Set parentId field directly (simulates DB state) so isDescendant query works
        parentNode.setParentId(nodeId);

        MoveNodeRequest request = new MoveNodeRequest();
        request.setTargetParentId(parentId);

        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(nodeRepository.findByParentIdOrderBySortOrderAsc(nodeId)).thenReturn(List.of(parentNode));

        assertThatThrownBy(() -> service.moveNode(nodeId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ApiCodes.VALIDATION_FAILED))
                .hasMessageContaining("循环引用");
    }

    @Test
    void moveNode_shouldRejectSelfReference() {
        MoveNodeRequest request = new MoveNodeRequest();
        request.setTargetParentId(nodeId);

        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));

        assertThatThrownBy(() -> service.moveNode(nodeId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ApiCodes.VALIDATION_FAILED))
                .hasMessageContaining("循环引用");
    }

    // ==================== Delete Node Tests ====================

    @Test
    void deleteNode_shouldDeleteLeafNode() {
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(nodeRepository.hasChildren(nodeId)).thenReturn(false);
        doNothing().when(nodeRepository).delete(node);

        service.deleteNode(nodeId);

        verify(nodeRepository).delete(node);
    }

    @Test
    void deleteNode_shouldRejectWhenHasChildren() {
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(nodeRepository.hasChildren(nodeId)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteNode(nodeId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ApiCodes.VALIDATION_FAILED))
                .hasMessageContaining("子节点");
    }

    @Test
    void deleteNodeCascade_shouldDeleteNodeAndDescendants() {
        ClassificationNode child = new ClassificationNode("C01", "Child", scheme);
        child.setId(UUID.randomUUID());
        child.setParent(node); // only affects entity graph for delete, not used by the query

        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(nodeRepository.findByParentIdOrderBySortOrderAsc(nodeId)).thenReturn(List.of(child));
        when(nodeRepository.findByParentIdOrderBySortOrderAsc(child.getId())).thenReturn(Collections.emptyList());

        service.deleteNodeCascade(nodeId);

        verify(nodeRepository, times(2)).delete(any(ClassificationNode.class));
    }

    // ==================== Tree Tests ====================

    @Test
    void getTree_shouldReturnRootNodes() {
        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));
        when(nodeRepository.findByParentIdIsNullAndClassificationSchemeIdOrderBySortOrderAsc(schemeId))
                .thenReturn(List.of(node));
        when(nodeRepository.hasChildren(nodeId)).thenReturn(false);

        List<ClassificationTreeNode> result = service.getTree(schemeId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLabel()).isEqualTo("Node1");
        assertThat(result.get(0).isHasChildren()).isFalse();
    }

    @Test
    void getTree_shouldThrowWhenSchemeNotFound() {
        when(schemeRepository.findById(schemeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTree(schemeId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ApiCodes.NOT_FOUND));
    }

    @Test
    void getChildren_shouldReturnRootNodesWhenParentIdNull() {
        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));
        when(nodeRepository.findByParentIdIsNullAndClassificationSchemeIdOrderBySortOrderAsc(schemeId))
                .thenReturn(List.of(node));
        when(nodeRepository.hasChildren(nodeId)).thenReturn(false);

        List<ClassificationTreeNode> result = service.getChildren(schemeId, null);

        assertThat(result).hasSize(1);
    }

    @Test
    void getChildren_shouldReturnChildrenWhenParentIdProvided() {
        ClassificationNode child = new ClassificationNode("C01", "Child", scheme);
        child.setId(UUID.randomUUID());

        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));
        when(nodeRepository.findByParentIdOrderBySortOrderAsc(parentId)).thenReturn(List.of(child));
        when(nodeRepository.hasChildren(child.getId())).thenReturn(false);

        List<ClassificationTreeNode> result = service.getChildren(schemeId, parentId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNodeCode()).isEqualTo("C01");
    }

    @Test
    void getTreeByRoot_shouldReturnSubTree() {
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(nodeRepository.hasChildren(nodeId)).thenReturn(false);

        List<ClassificationTreeNode> result = service.getTreeByRoot(schemeId, nodeId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLabel()).isEqualTo("Node1");
    }

    // ==================== Search Tests ====================

    @Test
    void searchNodes_shouldFindByName() {
        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));
        when(nodeRepository.findByClassificationSchemeIdOrderBySortOrderAsc(schemeId))
                .thenReturn(List.of(node));

        List<ClassificationNodeResponse> result = service.searchNodes(schemeId, "Node1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNodeName()).isEqualTo("Node1");
    }

    @Test
    void searchNodes_shouldFindByCode() {
        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));
        when(nodeRepository.findByClassificationSchemeIdOrderBySortOrderAsc(schemeId))
                .thenReturn(List.of(node));

        List<ClassificationNodeResponse> result = service.searchNodes(schemeId, "N01");

        assertThat(result).hasSize(1);
    }

    @Test
    void searchNodes_shouldReturnEmptyWhenNoMatch() {
        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));
        when(nodeRepository.findByClassificationSchemeIdOrderBySortOrderAsc(schemeId))
                .thenReturn(List.of(node));

        List<ClassificationNodeResponse> result = service.searchNodes(schemeId, "NONEXISTENT");

        assertThat(result).isEmpty();
    }

    // ==================== Boundary / Edge Cases ====================

    @Test
    void listNodesByScheme_shouldReturnEmptyWhenNoNodes() {
        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));
        when(nodeRepository.findByClassificationSchemeIdOrderBySortOrderAsc(schemeId))
                .thenReturn(Collections.emptyList());

        List<ClassificationNodeResponse> result = service.listNodesByScheme(schemeId);

        assertThat(result).isEmpty();
    }

    @Test
    void createNode_shouldUseDefaultSortOrder() {
        ClassificationNodeRequest request = new ClassificationNodeRequest();
        request.setSchemeId(schemeId);
        request.setNodeCode("N02");
        request.setNodeName("Node2");
        // sortOrder not set

        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));
        when(nodeRepository.save(any(ClassificationNode.class))).thenAnswer(i -> {
            ClassificationNode n = i.getArgument(0);
            n.setId(nodeId);
            return n;
        });

        ClassificationNodeResponse result = service.createNode(request);

        assertThat(result.getSortOrder()).isEqualTo(0);
    }

    @Test
    void moveNode_shouldHandleNullParentId_isDescendant() {
        // Test the NPE fix: candidateDescendantId == null should return false
        // Move node to a valid parent (not self, not descendant) - happy path
        MoveNodeRequest request = new MoveNodeRequest();
        request.setTargetParentId(parentId);

        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(nodeRepository.findById(parentId)).thenReturn(Optional.of(parentNode));
        mockSaveReturnsWithParentIdSync();

        ClassificationNodeResponse result = service.moveNode(nodeId, request);
        assertThat(result).isNotNull();
        assertThat(result.getParentId()).isEqualTo(parentId);
    }

    @Test
    void deleteNode_shouldThrowWhenNodeNotFound() {
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteNode(nodeId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ApiCodes.NOT_FOUND));
    }

    @Test
    void deleteNodeCascade_shouldThrowWhenNodeNotFound() {
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteNodeCascade(nodeId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ApiCodes.NOT_FOUND));
    }

    @Test
    void getSubTree_shouldThrowWhenRootNotFound() {
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTreeByRoot(schemeId, nodeId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ApiCodes.NOT_FOUND));
    }

    @Test
    void buildTreeNode_shouldSetHasChildrenCorrectly() {
        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));
        when(nodeRepository.findByParentIdIsNullAndClassificationSchemeIdOrderBySortOrderAsc(schemeId))
                .thenReturn(List.of(node));
        when(nodeRepository.hasChildren(nodeId)).thenReturn(true);

        List<ClassificationTreeNode> result = service.getTree(schemeId);

        assertThat(result.get(0).isHasChildren()).isTrue();
        assertThat(result.get(0).getChildren()).isNull(); // lazy: null when has children
    }

    @Test
    void buildTreeNode_shouldSetEmptyChildrenWhenLeaf() {
        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));
        when(nodeRepository.findByParentIdIsNullAndClassificationSchemeIdOrderBySortOrderAsc(schemeId))
                .thenReturn(List.of(node));
        when(nodeRepository.hasChildren(nodeId)).thenReturn(false);

        List<ClassificationTreeNode> result = service.getTree(schemeId);

        assertThat(result.get(0).isHasChildren()).isFalse();
        assertThat(result.get(0).getChildren()).isEqualTo(Collections.emptyList());
    }
}
