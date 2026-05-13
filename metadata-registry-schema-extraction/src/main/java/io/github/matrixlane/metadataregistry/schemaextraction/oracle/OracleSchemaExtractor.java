package io.github.matrixlane.metadataregistry.schemaextraction.oracle;

import io.github.matrixlane.metadataregistry.schemaextraction.AbstractSchemaExtractor;
import io.github.matrixlane.metadataregistry.schemaextraction.strategy.DatabaseType;

/**
 * Oracle schema extraction strategy (placeholder for future implementation).
 */
public class OracleSchemaExtractor extends AbstractSchemaExtractor {

    @Override
    public boolean supports(DatabaseType databaseType) {
        return databaseType == DatabaseType.ORACLE;
    }
}
