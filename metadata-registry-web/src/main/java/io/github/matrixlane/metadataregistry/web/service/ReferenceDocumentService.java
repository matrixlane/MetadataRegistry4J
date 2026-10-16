package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.DocumentType;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.ReferenceDocument;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Notation;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.language.LanguageResponse;
import io.github.matrixlane.metadataregistry.web.dto.referencedocument.ReferenceDocumentRequest;
import io.github.matrixlane.metadataregistry.web.dto.referencedocument.ReferenceDocumentResponse;
import io.github.matrixlane.metadataregistry.web.repository.DocumentTypeRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.ReferenceDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ReferenceDocumentService {

    private final ReferenceDocumentRepository repository;
    private final DocumentTypeRepository documentTypeRepository;
    private final LanguageIdentificationRepository languageRepository;
    private final LanguageGovernanceService governanceService;
    private final LanguageExtensionService extensionService;

    /**
     * 参考文档列表，关键词同时匹配标准表 29 自有的 identifier 列与标题。
     *
     * <p>ReferenceDocument 是独立根实体，其 identifier 是标准直接定义的标识属性
     * （如 {@code WST 364.8}），不走受管链，故直接匹配该列即可，无需 Scoped_Identifier 子查询。</p>
     */
    @Transactional(readOnly = true)
    public Page<ReferenceDocumentResponse> list(String keyword, Pageable pageable) {
        Specification<ReferenceDocument> spec = Specification.where(null);
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("identifier")), like),
                    cb.like(cb.lower(root.get("title").get("value")), like)));
        }
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ReferenceDocumentResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public ReferenceDocumentResponse create(ReferenceDocumentRequest request) {
        if (repository.existsByIdentifier(request.identifier())) {
            throw new BusinessException("ReferenceDocument identifier 已存在：" + request.identifier());
        }
        ReferenceDocument entity = new ReferenceDocument();
        applyRequest(entity, request);
        return toResponse(repository.save(entity));
    }

    public ReferenceDocumentResponse update(UUID id, ReferenceDocumentRequest request) {
        ReferenceDocument entity = loadOrThrow(id);
        if (!entity.getIdentifier().equals(request.identifier())
                && repository.existsByIdentifier(request.identifier())) {
            throw new BusinessException("ReferenceDocument identifier 已存在：" + request.identifier());
        }
        applyRequest(entity, request);
        return toResponse(repository.save(entity));
    }

    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("ReferenceDocument", id);
        }
        repository.deleteById(id);
    }

    /**
     * 按 URI 读取本地参考文档内容并流式返回，供前端在新标签页打开。
     *
     * <p>URI 支持 Windows 盘符路径（{@code E:\...}）、UNC（{@code \\server\share}）与
     * {@code file://} URI；http(s) 等远程地址由前端直接打开，不经此端点。文件不存在时
     * 抛 {@link ResourceNotFoundException}（404），前端据此提示重新指定有效 URI。</p>
     */
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> content(UUID id) {
        ReferenceDocument entity = loadOrThrow(id);
        String uri = entity.getUri() == null ? "" : entity.getUri().trim();
        Path path = resolveLocalPath(uri);
        if (path == null) {
            throw new BusinessException("参考文档 URI 不是本地文件路径：" + uri);
        }
        File file = path.toFile();
        if (!file.isFile()) {
            throw new ResourceNotFoundException("ReferenceDocument content", id);
        }
        String filename = file.getName();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentTypeOf(filename)))
                .contentLength(file.length())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename*=UTF-8''" + UriUtils.encode(filename, StandardCharsets.UTF_8))
                .body(new FileSystemResource(file));
    }

    /**
     * 把 URI 解析为本地文件路径；非本地路径（含空串）返回 null。
     */
    private Path resolveLocalPath(String uri) {
        if (uri.isBlank()) {
            return null;
        }
        String p = uri.trim();
        if (p.regionMatches(true, 0, "file://", 0, 7)) {
            p = UriUtils.decode(p.substring(7), StandardCharsets.UTF_8);
            if (p.startsWith("//")) {
                // file:////server/share 形式的 UNC
                p = "\\\\" + p.substring(2).replace('/', '\\');
            } else if (p.startsWith("/")) {
                // file:///abs/path：Windows 盘符去掉前导斜杠，POSIX 绝对路径原样保留
                if (hasDriveLetter(p.substring(1))) {
                    p = p.substring(1);
                }
            } else if (!hasDriveLetter(p)) {
                // file://server/share 的 host 形式 → UNC
                p = "\\\\" + p.replace('/', '\\');
            }
        }
        if (hasDriveLetter(p) || p.startsWith("\\\\") || p.startsWith("/")) {
            return Path.of(p);
        }
        return null;
    }

    private boolean hasDriveLetter(String p) {
        return p.length() >= 3
                && Character.isLetter(p.charAt(0))
                && p.charAt(1) == ':'
                && (p.charAt(2) == '\\' || p.charAt(2) == '/');
    }

    /** 常见文档扩展名的 Content-Type；未知类型按 application/octet-stream 返回。 */
    private String contentTypeOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot >= 0 && dot < filename.length() - 1) {
            String type = CONTENT_TYPES.get(filename.substring(dot + 1).toLowerCase(Locale.ROOT));
            if (type != null) {
                return type;
            }
        }
        return "application/octet-stream";
    }

    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("txt", "text/plain;charset=UTF-8"),
            Map.entry("md", "text/markdown;charset=UTF-8"),
            Map.entry("html", "text/html;charset=UTF-8"),
            Map.entry("htm", "text/html;charset=UTF-8"),
            Map.entry("xml", "application/xml"),
            Map.entry("json", "application/json"),
            Map.entry("csv", "text/csv;charset=UTF-8"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("webp", "image/webp"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("zip", "application/zip"));

    private ReferenceDocument loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReferenceDocument", id));
    }

    private void applyRequest(ReferenceDocument entity, ReferenceDocumentRequest request) {
        entity.setIdentifier(request.identifier());
        entity.setTitle(request.title() == null ? null : Text.of(request.title()));
        entity.setNotation(request.notation() == null ? null : Notation.of(request.notation()));
        entity.setUri(request.uri());
        if (request.typeDescriptionId() != null) {
            DocumentType dt = documentTypeRepository.findById(request.typeDescriptionId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "DocumentType", request.typeDescriptionId()));
            entity.setTypeDescription(dt);
        } else {
            entity.setTypeDescription(null);
        }
        entity.getLanguage().clear();
        if (request.languageIds() != null) {
            for (UUID langId : request.languageIds()) {
                LanguageIdentification li = languageRepository.findById(langId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "LanguageIdentification", langId));
                entity.getLanguage().add(li);
            }
        }
    }

    private ReferenceDocumentResponse toResponse(ReferenceDocument entity) {
        DocumentType type = entity.getTypeDescription();
        List<LanguageResponse> langs = new ArrayList<>();
        if (entity.getLanguage() != null) {
            for (LanguageIdentification li : entity.getLanguage()) {
                // 语言名称/地区名称仅取扩展表持久化值(纯展示字段,缺失或为空时返回 null,不做运行时解析)
                io.github.matrixlane.metadataregistry.ext.basicandcore.LanguageIdentificationExtension extension =
                        extensionService.getOrDefault(li.getId());
                langs.add(new LanguageResponse(
                        li.getId(),
                        li.getLanguageIdentifier(),
                        li.getScriptIdentifier(),
                        li.getGeopoliticalTerritoryIdentifier(),
                        extension == null ? null : extension.getLanguageName(),
                        extension == null ? null : extension.getTerritoryName(),
                        new ArrayList<>(li.getVariantIdentifier()),
                        new ArrayList<>(li.getExtensionIdentifier()),
                        li.getPrivateUseQualifier(),
                        governanceService.getOrDefault(li.getId()).getEnabled()));
            }
        }
        return new ReferenceDocumentResponse(
                entity.getId(),
                entity.getIdentifier(),
                type == null ? null : type.getId(),
                type == null ? null : type.getIdentifier(),
                entity.getTitle() == null ? null : entity.getTitle().getValue(),
                entity.getNotation() == null ? null : entity.getNotation().getValue(),
                entity.getUri(),
                langs);
    }
}
