package io.github.matrixlane.metadataregistry.web.dto.fhir;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FhirImportResult {
    @Builder.Default private List<String> importedResources = new ArrayList<>();
    @Builder.Default private List<String> importedValueSets = new ArrayList<>();
    @Builder.Default private List<String> importedCodeSystems = new ArrayList<>();
    @Builder.Default private List<FhirImportError> errors = new ArrayList<>();
    private int totalProcessed;
    private int successCount;
    private int errorCount;

    public void addImportedResource(String name) { importedResources.add(name); successCount++; }
    public void addImportedValueSet(String name) { importedValueSets.add(name); }
    public void addImportedCodeSystem(String name) { importedCodeSystems.add(name); }
    public void addError(FhirImportError error) { errors.add(error); errorCount++; }
    public void incrementProcessed() { totalProcessed++; }
}
