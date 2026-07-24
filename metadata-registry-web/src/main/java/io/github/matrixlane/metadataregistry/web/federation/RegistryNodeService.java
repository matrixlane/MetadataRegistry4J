package io.github.matrixlane.metadataregistry.web.federation;

import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 联邦节点管理服务（登记/列表/更新/停用）。
 */
@Service
@RequiredArgsConstructor
public class RegistryNodeService {

    private final RegistryNodeRepository repository;
    private final TokenCipher tokenCipher;

    @Transactional(readOnly = true)
    public List<RegistryNodeView> list() {
        return repository.findAll().stream().map(RegistryNodeView::of).toList();
    }

    @Transactional
    public RegistryNodeView create(RegistryNodeRequest request) {
        RegistryNode node = new RegistryNode();
        node.setName(request.name());
        node.setBaseUrl(request.baseUrl());
        node.setRai(request.rai());
        if (request.authToken() != null && !request.authToken().isBlank()) {
            node.setAuthTokenEncrypted(tokenCipher.encrypt(request.authToken()));
        }
        return RegistryNodeView.of(repository.save(node));
    }

    @Transactional
    public RegistryNodeView update(UUID id, RegistryNodeRequest request) {
        RegistryNode node = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RegistryNode", id));
        node.setName(request.name());
        node.setBaseUrl(request.baseUrl());
        node.setRai(request.rai());
        if (request.authToken() != null && !request.authToken().isBlank()) {
            node.setAuthTokenEncrypted(tokenCipher.encrypt(request.authToken()));
        }
        return RegistryNodeView.of(repository.save(node));
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("RegistryNode", id);
        }
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public RegistryNode getEnabled(UUID id) {
        RegistryNode node = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RegistryNode", id));
        if (!node.isEnabled()) {
            throw new ResourceNotFoundException("RegistryNode", id);
        }
        return node;
    }

    /**
     * 校验对端凭据（接收端点使用）。
     */
    @Transactional(readOnly = true)
    public boolean verifyToken(UUID nodeId, String plaintext) {
        RegistryNode node = repository.findById(nodeId).orElse(null);
        if (node == null || !node.isEnabled() || node.getAuthTokenEncrypted() == null || plaintext == null) {
            return false;
        }
        return tokenCipher.decrypt(node.getAuthTokenEncrypted()).equals(plaintext);
    }
}
