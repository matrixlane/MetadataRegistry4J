package io.github.matrixlane.metadataregistry.datasetregistration.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a single import error with row number and detailed reason.
 */
@Getter
@Setter
public class ImportError {

    /** The row/line number where the error occurred (1-based, including header). */
    private final int row;

    /** The column index where the error was detected, or -1 if row-level. */
    private final int column;

    /** Human-readable error description. */
    private final String message;

    /** The raw value that caused the error, if applicable. */
    private final String rawValue;

    public ImportError(int row, int column, String message, String rawValue) {
        this.row = row;
        this.column = column;
        this.message = message;
        this.rawValue = rawValue;
    }

    public ImportError(int row, String message, String rawValue) {
        this(row, -1, message, rawValue);
    }

    public ImportError(int row, String message) {
        this(row, -1, message, null);
    }
}
