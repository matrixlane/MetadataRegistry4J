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

@Entity
@Table(
    name = "fhir_value_set",
    indexes = {
        @Index(name = "idx_fhir_vs_name", columnList = "name"),
        @Index(name = "idx_fhir_vs_status", columnList = "status")
    }
)
public class FhirValueSet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "fhir_id", nullable = false, length = 200)
    private String fhirId;

    @Column(length = 500)
    private String url;

    @Column(length = 200)
    private String name;

    @Column(length = 500)
    private String title;

    @Column(length = 50)
    private String status;

    @Column(length = 50)
    private String version;

    @Column(name = "fhir_version", nullable = false, length = 20)
    private String fhirVersion;

    @Lob
    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Lob
    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String purpose;

    @Column(nullable = false)
    private boolean experimental;

    @Column(length = 200)
    private String publisher;

    @Column(name = "imported_at", nullable = false, updatable = false)
    private LocalDateTime importedAt;

    public FhirValueSet() {}

    public static Builder builder() { return new Builder(); }

    public UUID getId() { return id; }
    public String getFhirId() { return fhirId; }
    public String getUrl() { return url; }
    public String getName() { return name; }
    public String getTitle() { return title; }
    public String getStatus() { return status; }
    public String getVersion() { return version; }
    public String getFhirVersion() { return fhirVersion; }
    public String getDescription() { return description; }
    public String getPurpose() { return purpose; }
    public boolean isExperimental() { return experimental; }
    public String getPublisher() { return publisher; }
    public LocalDateTime getImportedAt() { return importedAt; }

    public void setId(UUID id) { this.id = id; }
    public void setFhirId(String fhirId) { this.fhirId = fhirId; }
    public void setUrl(String url) { this.url = url; }
    public void setName(String name) { this.name = name; }
    public void setTitle(String title) { this.title = title; }
    public void setStatus(String status) { this.status = status; }
    public void setVersion(String version) { this.version = version; }
    public void setFhirVersion(String fhirVersion) { this.fhirVersion = fhirVersion; }
    public void setDescription(String description) { this.description = description; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public void setExperimental(boolean experimental) { this.experimental = experimental; }
    public void setPublisher(String publisher) { this.publisher = publisher; }
    public void setImportedAt(LocalDateTime importedAt) { this.importedAt = importedAt; }

    public static class Builder {
        private UUID id;
        private String fhirId;
        private String url;
        private String name;
        private String title;
        private String status;
        private String version;
        private String fhirVersion;
        private String description;
        private String purpose;
        private boolean experimental;
        private String publisher;
        private LocalDateTime importedAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder fhirId(String fhirId) { this.fhirId = fhirId; return this; }
        public Builder url(String url) { this.url = url; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder fhirVersion(String fhirVersion) { this.fhirVersion = fhirVersion; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder purpose(String purpose) { this.purpose = purpose; return this; }
        public Builder experimental(boolean experimental) { this.experimental = experimental; return this; }
        public Builder publisher(String publisher) { this.publisher = publisher; return this; }
        public Builder importedAt(LocalDateTime importedAt) { this.importedAt = importedAt; return this; }

        public FhirValueSet build() {
            FhirValueSet v = new FhirValueSet();
            v.id = this.id;
            v.fhirId = this.fhirId;
            v.url = this.url;
            v.name = this.name;
            v.title = this.title;
            v.status = this.status;
            v.version = this.version;
            v.fhirVersion = this.fhirVersion;
            v.description = this.description;
            v.purpose = this.purpose;
            v.experimental = this.experimental;
            v.publisher = this.publisher;
            v.importedAt = this.importedAt;
            return v;
        }
    }
}
