package io.github.matrixlane.metadataregistry.web.service.fhir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirImportError;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirImportResult;
import io.github.matrixlane.metadataregistry.web.entity.fhir.FhirCodeSystem;
import io.github.matrixlane.metadataregistry.web.entity.fhir.FhirElement;
import io.github.matrixlane.metadataregistry.web.entity.fhir.FhirResource;
import io.github.matrixlane.metadataregistry.web.entity.fhir.FhirValueSet;
import io.github.matrixlane.metadataregistry.web.repository.fhir.FhirCodeSystemRepository;
import io.github.matrixlane.metadataregistry.web.repository.fhir.FhirElementRepository;
import io.github.matrixlane.metadataregistry.web.repository.fhir.FhirResourceRepository;
import io.github.matrixlane.metadataregistry.web.repository.fhir.FhirValueSetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class FhirImportService {

    private static final String SUPPORTED_FHIR_VERSION = "4.0";
    private static final String DEFAULT_FHIR_VERSION = "R4";

    private final FhirResourceRepository resourceRepository;
    private final FhirElementRepository elementRepository;
    private final FhirValueSetRepository valueSetRepository;
    private final FhirCodeSystemRepository codeSystemRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public FhirImportResult importFromDirectory(String packageDir) {
        FhirImportResult result = new FhirImportResult();
        Path dir = Path.of(packageDir);

        if (!Files.isDirectory(dir)) {
            result.addError(new FhirImportError(packageDir, "INVALID_PATH",
                    "Directory does not exist: " + packageDir));
            return result;
        }

        try (Stream<Path> files = Files.list(dir)) {
            List<Path> jsonFiles = files
                    .filter(p -> p.toString().endsWith(".json"))
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .toList();

            if (jsonFiles.isEmpty()) {
                result.addError(new FhirImportError(packageDir, "NO_FILES",
                        "No JSON files found in: " + packageDir));
                return result;
            }

            for (Path file : jsonFiles) {
                result.incrementProcessed();
                try {
                    processJsonFile(file, result);
                } catch (Exception e) {
                    log.warn("Error processing file: {}", file, e);
                    result.addError(new FhirImportError(
                            file.getFileName().toString(), "UNEXPECTED_ERROR", e.getMessage()));
                }
            }
        } catch (IOException e) {
            result.addError(new FhirImportError(
                    packageDir, "IO_ERROR", "Failed to list directory: " + e.getMessage()));
        }

        log.info("FHIR import completed: {} success, {} errors out of {} files",
                result.getSuccessCount(), result.getErrorCount(), result.getTotalProcessed());
        return result;
    }

    @Transactional
    public FhirImportResult importSingleFile(String jsonFilePath) {
        FhirImportResult result = new FhirImportResult();
        Path file = Path.of(jsonFilePath);

        if (!Files.isRegularFile(file)) {
            result.addError(new FhirImportError(
                    jsonFilePath, "INVALID_PATH", "File does not exist: " + jsonFilePath));
            return result;
        }

        result.incrementProcessed();
        try {
            processJsonFile(file, result);
        } catch (Exception e) {
            log.warn("Error processing file: {}", file, e);
            result.addError(new FhirImportError(
                    file.getFileName().toString(), "UNEXPECTED_ERROR", e.getMessage()));
        }

        return result;
    }

    private void processJsonFile(Path file, FhirImportResult result) throws IOException {
        JsonNode root;
        try {
            root = objectMapper.readTree(file.toFile());
        } catch (Exception e) {
            result.addError(new FhirImportError(
                    file.getFileName().toString(), "JSON_SYNTAX",
                    "Invalid JSON syntax: " + e.getMessage()));
            return;
        }

        JsonNode rt = root.get("resourceType");
        if (rt == null || rt.isNull()) {
            result.addError(new FhirImportError(
                    file.getFileName().toString(), "MISSING_FIELD",
                    "Missing required field: resourceType"));
            return;
        }

        String resourceType = rt.asText();
        switch (resourceType) {
            case "StructureDefinition" -> processStructureDefinition(file, root, result);
            case "ValueSet" -> processValueSet(file, root, result);
            case "CodeSystem" -> processCodeSystem(file, root, result);
            default -> log.debug("Skipping unsupported resource type: {}", resourceType);
        }
    }

    private void processStructureDefinition(Path file, JsonNode root, FhirImportResult result) {
        String fileName = file.getFileName().toString();
        if (!isVersionSupported(root)) {
            result.addError(new FhirImportError(fileName, "UNSUPPORTED_VERSION",
                    "FHIR version not supported: " + getString(root, "fhirVersion")));
            return;
        }

        String fhirId = getString(root, "id");
        String url = getString(root, "url");
        String name = getString(root, "name");

        if (name == null && fhirId == null) {
            result.addError(new FhirImportError(
                    fileName, "MISSING_FIELD", "StructureDefinition missing both 'name' and 'id'"));
            return;
        }

        if (url != null && resourceRepository.existsByUrl(url)) {
            log.info("Skipping already-imported resource: {}", url);
            return;
        }

        FhirResource resource = FhirResource.builder()
                .fhirId(fhirId != null ? fhirId : name)
                .url(url)
                .name(name)
                .title(getString(root, "title"))
                .status(getString(root, "status"))
                .version(getString(root, "version"))
                .fhirVersion(DEFAULT_FHIR_VERSION)
                .kind(getString(root, "kind"))
                .abstractResource(getBoolean(root, "abstract"))
                .type(getString(root, "type"))
                .description(getString(root, "description"))
                .publisher(getString(root, "publisher"))
                .date(getString(root, "date"))
                .importedAt(LocalDateTime.now())
                .build();

        resource = resourceRepository.save(resource);
        List<FhirElement> elements = parseElements(root, resource);
        if (!elements.isEmpty()) {
            elementRepository.saveAll(elements);
        }

        result.addImportedResource(name != null ? name : fhirId);
        log.info("Imported StructureDefinition: {} ({} elements)", name, elements.size());
    }

    private List<FhirElement> parseElements(JsonNode root, FhirResource resource) {
        List<FhirElement> elements = new ArrayList<>();
        JsonNode snapshot = root.get("snapshot");
        if (snapshot == null || snapshot.isNull()) {
            JsonNode diff = root.get("differential");
            if (diff == null || diff.isNull()) return elements;
            snapshot = diff;
        }

        JsonNode elementArray = snapshot.get("element");
        if (elementArray == null || !elementArray.isArray()) return elements;

        Map<String, FhirElement> pathToElement = new HashMap<>();
        for (JsonNode elemNode : elementArray) {
            FhirElement element = parseSingleElement(elemNode, resource, pathToElement);
            if (element != null) {
                elements.add(element);
                pathToElement.put(element.getPath(), element);
            }
        }
        return elements;
    }

    private FhirElement parseSingleElement(JsonNode elemNode, FhirResource resource,
                                           Map<String, FhirElement> pathToElement) {
        String path = getString(elemNode, "path");
        if (path == null) return null;

        FhirElement parent = null;
        int lastDot = path.lastIndexOf('.');
        if (lastDot > 0) {
            String parentPath = path.substring(0, lastDot);
            parent = pathToElement.get(parentPath);
        }

        String elementPath = lastDot > 0 ? path.substring(lastDot + 1) : path;

        String elementType = null;
        JsonNode typeNode = elemNode.get("type");
        if (typeNode != null && typeNode.isArray() && typeNode.size() > 0) {
            elementType = getString(typeNode.get(0), "code");
        }

        Integer min = elemNode.has("min") && !elemNode.get("min").isNull()
                ? elemNode.get("min").asInt() : null;
        String max = getString(elemNode, "max");

        String bindingValueSet = null;
        String bindingStrength = null;
        String bindingDescription = null;
        JsonNode bindingNode = elemNode.get("binding");
        if (bindingNode != null && !bindingNode.isNull()) {
            bindingStrength = getString(bindingNode, "strength");
            bindingDescription = getString(bindingNode, "description");
            JsonNode vsRef = bindingNode.get("valueSet");
            if (vsRef != null) bindingValueSet = vsRef.isTextual() ? vsRef.asText() : null;
        }

        return FhirElement.builder()
                .resource(resource)
                .parent(parent)
                .path(path)
                .elementPath(elementPath)
                .elementType(elementType)
                .min(min)
                .max(max)
                .shortDescription(getString(elemNode, "short"))
                .definition(getString(elemNode, "definition"))
                .bindingValueSet(bindingValueSet)
                .bindingStrength(bindingStrength)
                .bindingDescription(bindingDescription)
                .isModifier(getBoolean(elemNode, "isModifier"))
                .isSummary(getBoolean(elemNode, "isSummary"))
                .fhirVersion(DEFAULT_FHIR_VERSION)
                .importedAt(LocalDateTime.now())
                .build();
    }

    private void processValueSet(Path file, JsonNode root, FhirImportResult result) {
        String fileName = file.getFileName().toString();
        String url = getString(root, "url");
        if (url == null) {
            result.addError(new FhirImportError(fileName, "MISSING_FIELD",
                    "ValueSet missing required field: url"));
            return;
        }
        if (valueSetRepository.existsByUrl(url)) return;

        String name = getString(root, "name");
        FhirValueSet vs = FhirValueSet.builder()
                .fhirId(getString(root, "id"))
                .url(url).name(name).title(getString(root, "title"))
                .status(getString(root, "status")).version(getString(root, "version"))
                .fhirVersion(DEFAULT_FHIR_VERSION)
                .description(getString(root, "description"))
                .purpose(getString(root, "purpose"))
                .experimental(getBoolean(root, "experimental"))
                .publisher(getString(root, "publisher"))
                .importedAt(LocalDateTime.now())
                .build();
        valueSetRepository.save(vs);
        result.addImportedValueSet(name != null ? name : url);
    }

    private void processCodeSystem(Path file, JsonNode root, FhirImportResult result) {
        String fileName = file.getFileName().toString();
        String url = getString(root, "url");
        if (url == null) {
            result.addError(new FhirImportError(fileName, "MISSING_FIELD",
                    "CodeSystem missing required field: url"));
            return;
        }
        if (codeSystemRepository.existsByUrl(url)) return;

        String name = getString(root, "name");
        FhirCodeSystem cs = FhirCodeSystem.builder()
                .fhirId(getString(root, "id"))
                .url(url).name(name).title(getString(root, "title"))
                .status(getString(root, "status")).version(getString(root, "version"))
                .fhirVersion(DEFAULT_FHIR_VERSION)
                .description(getString(root, "description"))
                .content(getString(root, "content"))
                .publisher(getString(root, "publisher"))
                .importedAt(LocalDateTime.now())
                .build();
        codeSystemRepository.save(cs);
        result.addImportedCodeSystem(name != null ? name : url);
    }

    private boolean isVersionSupported(JsonNode root) {
        String fv = getString(root, "fhirVersion");
        return fv == null || fv.startsWith(SUPPORTED_FHIR_VERSION);
    }

    private static String getString(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return child != null && !child.isNull() ? child.asText() : null;
    }

    private static boolean getBoolean(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return child != null && !child.isNull() && child.asBoolean();
    }
}
