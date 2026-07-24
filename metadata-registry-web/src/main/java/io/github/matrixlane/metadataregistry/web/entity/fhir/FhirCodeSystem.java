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
    name = "fhir_code_system",
    indexes = {
        @Index(name = "idx_fhir_cs_name", columnList = "name"),
        @Index(name = "idx_fhir_cs_status", columnList = "status")
    }
)
public class FhirCodeSystem {

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
    @Column
    private String description;

    @Column(length = 50)
    private String content;

    @Column(length = 200)
    private String publisher;

    @Column(name = "imported_at", nullable = false, updatable = false)
    private LocalDateTime importedAt;

    public FhirCodeSystem() {}

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
    public String getContent() { return content; }
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
    public void setContent(String content) { this.content = content; }
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
        private String content;
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
        public Builder content(String content) { this.content = content; return this; }
        public Builder publisher(String publisher) { this.publisher = publisher; return this; }
        public Builder importedAt(LocalDateTime importedAt) { this.importedAt = importedAt; return this; }

        public FhirCodeSystem build() {
            FhirCodeSystem c = new FhirCodeSystem();
            c.id = this.id;
            c.fhirId = this.fhirId;
            c.url = this.url;
            c.name = this.name;
            c.title = this.title;
            c.status = this.status;
            c.version = this.version;
            c.fhirVersion = this.fhirVersion;
            c.description = this.description;
            c.content = this.content;
            c.publisher = this.publisher;
            c.importedAt = this.importedAt;
            return c;
        }
    }
}
