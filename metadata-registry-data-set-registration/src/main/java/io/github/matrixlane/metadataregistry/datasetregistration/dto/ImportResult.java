package io.github.matrixlane.metadataregistry.datasetregistration.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Result of an import operation, including success/failure statistics
 * and per-row error details for error row localization.
 */
@Getter
@Setter
public class ImportResult {

    private boolean success;

    /** UUID of the created/updated dataset. */
    private UUID dataSetId;

    /** Name of the imported dataset. */
    private String dataSetName;

    /** Total number of rows processed (excluding header). */
    private int totalRows;

    /** Number of rows successfully imported. */
    private int importedRows;

    /** Number of rows that had errors and were skipped. */
    private int errorRows;

    /** List of per-row import errors for error localization. */
    private final List<ImportError> errors = new ArrayList<>();

    /** Duration of the import operation in milliseconds. */
    private long durationMs;

    public ImportResult() {
    }

    /**
     * Returns true if all rows were imported without errors.
     */
    public boolean isFullySuccessful() {
        return errorRows == 0 && importedRows > 0;
    }

    /**
     * Add a single import error.
     */
    public void addError(ImportError error) {
        errors.add(error);
        errorRows++;
    }

    /**
     * Mark the overall result as successful (partial success is still success).
     */
    public void markSuccess(UUID dataSetId, String dataSetName) {
        this.success = true;
        this.dataSetId = dataSetId;
        this.dataSetName = dataSetName;
    }
}
