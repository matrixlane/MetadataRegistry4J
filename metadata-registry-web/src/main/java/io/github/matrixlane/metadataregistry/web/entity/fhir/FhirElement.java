package io.github.matrixlane.metadataregistry.web.entity.fhir;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "fhir_element",
    indexes = {
        @Index(name = "idx_fhir_element_resource", columnList = "resource_id"),
        @Index(name = "idx_fhir_element_parent", columnList = "parent_id"),
        @Index(name = "idx_fhir_element_path", columnList = "path"),
        @Index(name = "idx_fhir_element_type", columnList = "element_type")
    }
)
public class FhirElement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private FhirResource resource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private FhirElement parent;

    @Column(nullable = false, length = 500)
    private String path;

    @Column(name = "element_path", nullable = false, length = 500)
    private String elementPath;

    @Column(name = "element_type", length = 200)
    private String elementType;

    @Column
    private Integer min;

    @Column(length = 20)
    private String max;

    @Column(name = "short_description", length = 1000)
    private String shortDescription;

    @Lob
    @Column
    private String definition;

    @Column(name = "binding_value_set", length = 500)
    private String bindingValueSet;

    @Column(name = "binding_strength", length = 50)
    private String bindingStrength;

    @Column(name = "binding_description", length = 1000)
    private String bindingDescription;

    @Column(name = "is_modifier", nullable = false)
    private boolean isModifier;

    @Column(name = "is_summary", nullable = false)
    private boolean isSummary;

    @Column(name = "fhir_version", nullable = false, length = 20)
    private String fhirVersion;

    @Column(name = "imported_at", nullable = false, updatable = false)
    private LocalDateTime importedAt;

    /** 预留：与本地 AdministeredItem 的关联字段（WI-012-2 映射数据） */
    @Column(name = "administered_item_id")
    private UUID administeredItemId;

    public FhirElement() {}

    public static Builder builder() { return new Builder(); }

    public UUID getId() { return id; }
    public FhirResource getResource() { return resource; }
    public FhirElement getParent() { return parent; }
    public String getPath() { return path; }
    public String getElementPath() { return elementPath; }
    public String getElementType() { return elementType; }
    public Integer getMin() { return min; }
    public String getMax() { return max; }
    public String getShortDescription() { return shortDescription; }
    public String getDefinition() { return definition; }
    public String getBindingValueSet() { return bindingValueSet; }
    public String getBindingStrength() { return bindingStrength; }
    public String getBindingDescription() { return bindingDescription; }
    public boolean isModifier() { return isModifier; }
    public boolean isSummary() { return isSummary; }
    public String getFhirVersion() { return fhirVersion; }
    public LocalDateTime getImportedAt() { return importedAt; }
    public UUID getAdministeredItemId() { return administeredItemId; }

    public void setId(UUID id) { this.id = id; }
    public void setResource(FhirResource resource) { this.resource = resource; }
    public void setParent(FhirElement parent) { this.parent = parent; }
    public void setPath(String path) { this.path = path; }
    public void setElementPath(String elementPath) { this.elementPath = elementPath; }
    public void setElementType(String elementType) { this.elementType = elementType; }
    public void setMin(Integer min) { this.min = min; }
    public void setMax(String max) { this.max = max; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }
    public void setDefinition(String definition) { this.definition = definition; }
    public void setBindingValueSet(String bindingValueSet) { this.bindingValueSet = bindingValueSet; }
    public void setBindingStrength(String bindingStrength) { this.bindingStrength = bindingStrength; }
    public void setBindingDescription(String bindingDescription) { this.bindingDescription = bindingDescription; }
    public void setModifier(boolean modifier) { isModifier = modifier; }
    public void setSummary(boolean summary) { isSummary = summary; }
    public void setFhirVersion(String fhirVersion) { this.fhirVersion = fhirVersion; }
    public void setImportedAt(LocalDateTime importedAt) { this.importedAt = importedAt; }
    public void setAdministeredItemId(UUID administeredItemId) { this.administeredItemId = administeredItemId; }

    public static class Builder {
        private UUID id;
        private FhirResource resource;
        private FhirElement parent;
        private String path;
        private String elementPath;
        private String elementType;
        private Integer min;
        private String max;
        private String shortDescription;
        private String definition;
        private String bindingValueSet;
        private String bindingStrength;
        private String bindingDescription;
        private boolean isModifier;
        private boolean isSummary;
        private String fhirVersion;
        private LocalDateTime importedAt;
        private UUID administeredItemId;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder resource(FhirResource resource) { this.resource = resource; return this; }
        public Builder parent(FhirElement parent) { this.parent = parent; return this; }
        public Builder path(String path) { this.path = path; return this; }
        public Builder elementPath(String elementPath) { this.elementPath = elementPath; return this; }
        public Builder elementType(String elementType) { this.elementType = elementType; return this; }
        public Builder min(Integer min) { this.min = min; return this; }
        public Builder max(String max) { this.max = max; return this; }
        public Builder shortDescription(String shortDescription) { this.shortDescription = shortDescription; return this; }
        public Builder definition(String definition) { this.definition = definition; return this; }
        public Builder bindingValueSet(String bindingValueSet) { this.bindingValueSet = bindingValueSet; return this; }
        public Builder bindingStrength(String bindingStrength) { this.bindingStrength = bindingStrength; return this; }
        public Builder bindingDescription(String bindingDescription) { this.bindingDescription = bindingDescription; return this; }
        public Builder isModifier(boolean isModifier) { this.isModifier = isModifier; return this; }
        public Builder isSummary(boolean isSummary) { this.isSummary = isSummary; return this; }
        public Builder fhirVersion(String fhirVersion) { this.fhirVersion = fhirVersion; return this; }
        public Builder importedAt(LocalDateTime importedAt) { this.importedAt = importedAt; return this; }
        public Builder administeredItemId(UUID administeredItemId) { this.administeredItemId = administeredItemId; return this; }

        public FhirElement build() {
            FhirElement e = new FhirElement();
            e.id = this.id;
            e.resource = this.resource;
            e.parent = this.parent;
            e.path = this.path;
            e.elementPath = this.elementPath;
            e.elementType = this.elementType;
            e.min = this.min;
            e.max = this.max;
            e.shortDescription = this.shortDescription;
            e.definition = this.definition;
            e.bindingValueSet = this.bindingValueSet;
            e.bindingStrength = this.bindingStrength;
            e.bindingDescription = this.bindingDescription;
            e.isModifier = this.isModifier;
            e.isSummary = this.isSummary;
            e.fhirVersion = this.fhirVersion;
            e.importedAt = this.importedAt;
            e.administeredItemId = this.administeredItemId;
            return e;
        }
    }
}
