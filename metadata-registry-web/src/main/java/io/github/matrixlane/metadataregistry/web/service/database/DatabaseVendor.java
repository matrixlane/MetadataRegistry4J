package io.github.matrixlane.metadataregistry.web.service.database;

/**
 * Supported database vendors. Resolution prefers the explicit DB_VENDOR
 * environment/config value and falls back to inspecting the JDBC URL.
 */
public enum DatabaseVendor {

    SQLSERVER,
    SQLITE,
    DM;

    public static DatabaseVendor resolve(String vendor, String jdbcUrl) {
        if (vendor != null && !vendor.isBlank()) {
            return switch (vendor.trim().toLowerCase()) {
                case "sqlserver", "mssql" -> SQLSERVER;
                case "sqlite" -> SQLITE;
                case "dm", "dameng" -> DM;
                default -> throw new IllegalArgumentException("不支持的数据库方言标识: " + vendor);
            };
        }
        String url = jdbcUrl == null ? "" : jdbcUrl.toLowerCase();
        if (url.startsWith("jdbc:sqlite")) {
            return SQLITE;
        }
        if (url.startsWith("jdbc:dm")) {
            return DM;
        }
        // Preserve historical behavior: default is SQL Server.
        return SQLSERVER;
    }
}
