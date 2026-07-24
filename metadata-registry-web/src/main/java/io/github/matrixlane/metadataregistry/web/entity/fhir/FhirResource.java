package io.github.matrixlane.metadataregistry.web.entity.fhir;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * FHIR StructureDefinition 持久化实体
 */
@Entity
@Table(
    name = "fhir_resource",
    indexes = {
        @Index(name = "idx_fhir_res_name", columnList = "name"),
        @Index(name = "idx_fhir_res_url", columnList = "url"),
        @Index(name = "idx_fhir_res_kind", columnList = "kind"),
        @Index(name = "idx_fhir_res_fhir_version", columnList = "fhir_version")
    }
)
public class FhirResource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "fhir_id", nullable = false, length = 200)
    private String fhirId;

    @Column(length = 500)
    private String url;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String title;

    @Column(length = 50)
    private String status;

    @Column(length = 50)
    private String version;

    @Column(name = "fhir_version", nullable = false, length = 20)
    private String fhirVersion;

    @Column(length = 50)
    private String kind;

    @Column(name = "is_abstract")
    private Boolean abstractResource;

    @Column(length = 200)
    private String type;

    @Lob
    @Column
    private String description;

    @Column(length = 500)
    private String publisher;

    @Column(length = 50)
    private String date;

    @Column(name = "imported_at", nullable = false, updatable = false)
    private LocalDateTime importedAt;

    /** 预留：与 Administered_Item 的关联字段 */
    @Column(name = "administered_item_id")
    private UUID administeredItemId;

    public FhirResource() {}

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public UUID getId() { return id; }
    public String getFhirId() { return fhirId; }
    public String getUrl() { return url; }
    public String getName() { return name; }
    public String getTitle() { return title; }
    public String getStatus() { return status; }
    public String getVersion() { return version; }
    public String getFhirVersion() { return fhirVersion; }
    public String getKind() { return kind; }
    public Boolean getAbstractResource() { return abstractResource; }
    public String getType() { return type; }
    public String getDescription() { return description; }
    public String getPublisher() { return publisher; }
    public String getDate() { return date; }
    public LocalDateTime getImportedAt() { return importedAt; }
    public UUID getAdministeredItemId() { return administeredItemId; }

    // Setters
    public void setId(UUID id) { this.id = id; }
    public void setFhirId(String fhirId) { this.fhirId = fhirId; }
    public void setUrl(String url) { this.url = url; }
    public void setName(String name) { this.name = name; }
    public void setTitle(String title) { this.title = title; }
    public void setStatus(String status) { this.status = status; }
    public void setVersion(String version) { this.version = version; }
    public void setFhirVersion(String fhirVersion) { this.fhirVersion = fhirVersion; }
    public void setKind(String kind) { this.kind = kind; }
    public void setAbstractResource(Boolean abstractResource) { this.abstractResource = abstractResource; }
    public void setType(String type) { this.type = type; }
    public void setDescription(String description) { this.description = description; }
    public void setPublisher(String publisher) { this.publisher = publisher; }
    public void setDate(String date) { this.date = date; }
    public void setImportedAt(LocalDateTime importedAt) { this.importedAt = importedAt; }
    public void setAdministeredItemId(UUID administeredItemId) { this.administeredItemId = administeredItemId; }

    public static class Builder {
        private UUID id;
        private String fhirId;
        private String url;
        private String name;
        private String title;
        private String status;
        private String version;
        private String fhirVersion;
        private String kind;
        private Boolean abstractResource;
        private String type;
        private String description;
        private String publisher;
        private String date;
        private LocalDateTime importedAt;
        private UUID administeredItemId;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder fhirId(String fhirId) { this.fhirId = fhirId; return this; }
        public Builder url(String url) { this.url = url; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder fhirVersion(String fhirVersion) { this.fhirVersion = fhirVersion; return this; }
        public Builder kind(String kind) { this.kind = kind; return this; }
        public Builder abstractResource(Boolean abstractResource) { this.abstractResource = abstractResource; return this; }
        public Builder type(String type) { this.type = type; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder publisher(String publisher) { this.publisher = publisher; return this; }
        public Builder date(String date) { this.date = date; return this; }
        public Builder importedAt(LocalDateTime importedAt) { this.importedAt = importedAt; return this; }
        public Builder administeredItemId(UUID administeredItemId) { this.administeredItemId = administeredItemId; return this; }

        public FhirResource build() {
            FhirResource r = new FhirResource();
            r.id = this.id;
            r.fhirId = this.fhirId;
            r.url = this.url;
            r.name = this.name;
            r.title = this.title;
            r.status = this.status;
            r.version = this.version;
            r.fhirVersion = this.fhirVersion;
            r.kind = this.kind;
            r.abstractResource = this.abstractResource;
            r.type = this.type;
            r.description = this.description;
            r.publisher = this.publisher;
            r.date = this.date;
            r.importedAt = this.importedAt;
            r.administeredItemId = this.administeredItemId;
            return r;
        }
    }
}
