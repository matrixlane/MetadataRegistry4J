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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassificationSchemeService {

    private final ClassificationSchemeRepository schemeRepository;
    private final ClassificationNodeRepository nodeRepository;

    // ==================== Scheme Operations ====================

    public Page<ClassificationSchemeResponse> listSchemes(String q, Pageable pageable) {
        Page<ClassificationScheme> page;
        if (q != null && !q.isBlank()) {
            page = schemeRepository.findBySchemeNameContainingIgnoreCase(q, pageable);
        } else {
            page = schemeRepository.findAll(pageable);
        }
        return page.map(this::toSchemeResponse);
    }

    public ClassificationSchemeResponse getScheme(UUID schemeId) {
        ClassificationScheme scheme = schemeRepository.findById(schemeId)
                .orElseThrow(() -> new BusinessException(ApiCodes.NOT_FOUND,
                        "分类方案不存在: " + schemeId));
        return toSchemeResponse(scheme);
    }

    @Transactional
    public ClassificationSchemeResponse createScheme(ClassificationSchemeRequest request) {
        ClassificationScheme scheme = new ClassificationScheme(request.getSchemeName());
        scheme.setDescription(request.getDescription());
        scheme.setVersion(request.getVersion());
        scheme.setStatus(request.getStatus() != null ? request.getStatus() : "DRAFT");
        scheme = schemeRepository.save(scheme);
        return toSchemeResponse(scheme);
    }

    @Transactional
    public ClassificationSchemeResponse updateScheme(UUID schemeId, ClassificationSchemeRequest request) {
        ClassificationScheme scheme = schemeRepository.findById(schemeId)
                .orElseThrow(() -> new BusinessException(ApiCodes.NOT_FOUND,
                        "分类方案不存在: " + schemeId));
        scheme.setSchemeName(request.getSchemeName());
        scheme.setDescription(request.getDescription());
        scheme.setVersion(request.getVersion());
        scheme.setStatus(request.getStatus());
        scheme = schemeRepository.save(scheme);
        return toSchemeResponse(scheme);
    }

    @Transactional
    public void deleteScheme(UUID schemeId) {
        ClassificationScheme scheme = schemeRepository.findById(schemeId)
                .orElseThrow(() -> new BusinessException(ApiCodes.NOT_FOUND,
                        "分类方案不存在: " + schemeId));
        long nodeCount = nodeRepository.countByClassificationSchemeId(schemeId);
        if (nodeCount > 0) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "分类方案下存在 " + nodeCount + " 个节点，无法删除。请先删除所有节点。");
        }
        schemeRepository.delete(scheme);
    }

    // ==================== Node Operations ====================

    public List<ClassificationNodeResponse> listNodesByScheme(UUID schemeId) {
        schemeRepository.findById(schemeId)
                .orElseThrow(() -> new BusinessException(ApiCodes.NOT_FOUND,
                        "分类方案不存在: " + schemeId));
        return nodeRepository.findByClassificationSchemeIdOrderBySortOrderAsc(schemeId)
                .stream()
                .map(this::toNodeResponse)
                .collect(Collectors.toList());
    }

    public ClassificationNodeResponse getNode(UUID nodeId) {
        ClassificationNode node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new BusinessException(ApiCodes.NOT_FOUND,
                        "分类节点不存在: " + nodeId));
        return toNodeResponse(node);
    }

    @Transactional
    public ClassificationNodeResponse createNode(ClassificationNodeRequest request) {
        ClassificationScheme scheme = schemeRepository.findById(request.getSchemeId())
                .orElseThrow(() -> new BusinessException(ApiCodes.NOT_FOUND,
                        "关联的分类方案不存在"));

        ClassificationNode node = new ClassificationNode(
                request.getNodeCode(), request.getNodeName(), scheme);
        node.setDescription(request.getDescription());
        node.setDefinition(request.getDefinition());
        node.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);

        resolveParent(node, request.getParentId());

        node = nodeRepository.save(node);
        return toNodeResponse(node);
    }

    @Transactional
    public ClassificationNodeResponse updateNode(UUID nodeId, ClassificationNodeRequest request) {
        ClassificationNode node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new BusinessException(ApiCodes.NOT_FOUND,
                        "分类节点不存在: " + nodeId));
        node.setNodeCode(request.getNodeCode());
        node.setNodeName(request.getNodeName());
        node.setDescription(request.getDescription());
        node.setDefinition(request.getDefinition());
        node.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);

        resolveParent(node, request.getParentId());

        node = nodeRepository.save(node);
        return toNodeResponse(node);
    }

    @Transactional
    public ClassificationNodeResponse moveNode(UUID nodeId, MoveNodeRequest request) {
        ClassificationNode node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new BusinessException(ApiCodes.NOT_FOUND,
                        "分类节点不存在: " + nodeId));

        UUID newParentId = request.getTargetParentId();

        // Cycle detection: ensure new parent is not a descendant of this node
        if (isDescendant(nodeId, newParentId)) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "不能将节点移动到其子节点下，会产生循环引用");
        }

        resolveParent(node, newParentId);
        node = nodeRepository.save(node);
        return toNodeResponse(node);
    }

    private void resolveParent(ClassificationNode node, UUID parentId) {
        if (parentId != null) {
            ClassificationNode parent = nodeRepository.findById(parentId)
                    .orElseThrow(() -> new BusinessException(ApiCodes.NOT_FOUND,
                            "父节点不存在: " + parentId));
            node.setParent(parent);
        } else {
            node.setParent(null);
        }
    }

    @Transactional
    public void deleteNode(UUID nodeId) {
        ClassificationNode node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new BusinessException(ApiCodes.NOT_FOUND,
                        "分类节点不存在: " + nodeId));
        if (nodeRepository.hasChildren(nodeId)) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "节点存在子节点，无法删除。请先删除子节点或使用级联删除。");
        }
        nodeRepository.delete(node);
    }

    @Transactional
    public void deleteNodeCascade(UUID nodeId) {
        ClassificationNode node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new BusinessException(ApiCodes.NOT_FOUND,
                        "分类节点不存在: " + nodeId));
        deleteRecursive(node);
    }

    private void deleteRecursive(ClassificationNode node) {
        List<ClassificationNode> children = nodeRepository.findByParentIdOrderBySortOrderAsc(node.getId());
        for (ClassificationNode child : children) {
            deleteRecursive(child);
        }
        nodeRepository.delete(node);
    }

    // ==================== Tree Operations ====================

    public List<ClassificationTreeNode> getTree(UUID schemeId) {
        schemeRepository.findById(schemeId)
                .orElseThrow(() -> new BusinessException(ApiCodes.NOT_FOUND,
                        "分类方案不存在: " + schemeId));

        List<ClassificationNode> rootNodes = nodeRepository
                .findByParentIdIsNullAndClassificationSchemeIdOrderBySortOrderAsc(schemeId);

        List<ClassificationTreeNode> tree = new ArrayList<>();
        for (ClassificationNode root : rootNodes) {
            tree.add(buildTreeNode(root));
        }
        return tree;
    }

    public List<ClassificationTreeNode> getTreeByRoot(UUID schemeId, UUID rootNodeId) {
        ClassificationNode root = nodeRepository.findById(rootNodeId)
                .orElseThrow(() -> new BusinessException(ApiCodes.NOT_FOUND,
                        "分类节点不存在: " + rootNodeId));
        return Collections.singletonList(buildTreeNode(root));
    }

    /**
     * 懒加载：根据 parentId 获取子节点列表。parentId 为 null 表示获取根节点。
     */
    public List<ClassificationTreeNode> getChildren(UUID schemeId, UUID parentId) {
        schemeRepository.findById(schemeId)
                .orElseThrow(() -> new BusinessException(ApiCodes.NOT_FOUND,
                        "分类方案不存在: " + schemeId));

        List<ClassificationNode> children;
        if (parentId == null) {
            children = nodeRepository
                    .findByParentIdIsNullAndClassificationSchemeIdOrderBySortOrderAsc(schemeId);
        } else {
            children = nodeRepository.findByParentIdOrderBySortOrderAsc(parentId);
        }

        List<ClassificationTreeNode> tree = new ArrayList<>();
        for (ClassificationNode child : children) {
            tree.add(buildTreeNode(child));
        }
        return tree;
    }

    /**
     * 搜索节点（按名称或编码模糊匹配）
     */
    public List<ClassificationNodeResponse> searchNodes(UUID schemeId, String keyword) {
        schemeRepository.findById(schemeId)
                .orElseThrow(() -> new BusinessException(ApiCodes.NOT_FOUND,
                        "分类方案不存在: " + schemeId));

        List<ClassificationNode> allNodes = nodeRepository
                .findByClassificationSchemeIdOrderBySortOrderAsc(schemeId);

        String lowerKeyword = keyword.toLowerCase();
        return allNodes.stream()
                .filter(n -> n.getNodeName().toLowerCase().contains(lowerKeyword)
                        || n.getNodeCode().toLowerCase().contains(lowerKeyword))
                .map(this::toNodeResponse)
                .collect(Collectors.toList());
    }

    // ==================== Helpers ====================

    private ClassificationTreeNode buildTreeNode(ClassificationNode node) {
        boolean hasChildren = nodeRepository.hasChildren(node.getId());
        return ClassificationTreeNode.builder()
                .id(node.getId())
                .label(node.getNodeName())
                .nodeCode(node.getNodeCode())
                .description(node.getDescription())
                .sortOrder(node.getSortOrder())
                .hasChildren(hasChildren)
                .children(hasChildren ? null : Collections.emptyList())
                .build();
    }

    private ClassificationSchemeResponse toSchemeResponse(ClassificationScheme scheme) {
        return ClassificationSchemeResponse.builder()
                .id(scheme.getId())
                .schemeName(scheme.getSchemeName())
                .description(scheme.getDescription())
                .version(scheme.getVersion())
                .status(scheme.getStatus())
                .createdAt(scheme.getCreatedAt())
                .updatedAt(scheme.getUpdatedAt())
                .build();
    }

    private ClassificationNodeResponse toNodeResponse(ClassificationNode node) {
        boolean hasChildren = nodeRepository.hasChildren(node.getId());
        return ClassificationNodeResponse.builder()
                .id(node.getId())
                .nodeCode(node.getNodeCode())
                .nodeName(node.getNodeName())
                .description(node.getDescription())
                .definition(node.getDefinition())
                .sortOrder(node.getSortOrder())
                .parentId(node.getParentId())
                .schemeId(node.getClassificationScheme().getId())
                .schemeName(node.getClassificationScheme().getSchemeName())
                .hasChildren(hasChildren)
                .createdAt(node.getCreatedAt())
                .updatedAt(node.getUpdatedAt())
                .build();
    }

    /**
     * Checks if candidateDescendantId is a descendant of ancestorId.
     * Used for cycle detection when moving nodes.
     */
    private boolean isDescendant(UUID ancestorId, UUID candidateDescendantId) {
        if (candidateDescendantId == null || ancestorId == null) {
            return false;
        }
        if (ancestorId.equals(candidateDescendantId)) {
            return true;
        }
        List<ClassificationNode> children = nodeRepository.findByParentIdOrderBySortOrderAsc(ancestorId);
        for (ClassificationNode child : children) {
            if (isDescendant(child.getId(), candidateDescendantId)) {
                return true;
            }
        }
        return false;
    }
}
