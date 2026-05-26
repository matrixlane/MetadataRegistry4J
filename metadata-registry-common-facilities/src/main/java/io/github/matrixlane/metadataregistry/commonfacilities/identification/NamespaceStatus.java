package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;

/**
 * ISO/IEC 11179-3:2023, 7.3.3 — Namespace Status Lifecycle.
 *
 * <p>Extended from ISO registration states to support Namespace governance workflow:</p>
 * <pre>
 * DRAFT → SUBMITTED → ACTIVE → DEPRECATED → OBSOLETE
 * </pre>
 *
 * <p>State descriptions:</p>
 * <ul>
 *   <li>DRAFT: Initial state, Namespace is being prepared</li>
 *   <li>SUBMITTED: Submitted for REGISTRAR approval (triggers WI-009 approval workflow)</li>
 *   <li>ACTIVE: Approved and operational, metadata objects can be created</li>
 *   <li>DEPRECATED: No longer recommended for new objects, still queryable</li>
 *   <li>OBSOLETE: Read-only, no operations allowed</li>
 * </ul>
 *
 * <p>WI-028-A extends WI-009 approval framework with namespace-specific states.</p>
 */
@StandardReference("ISO/IEC 11179-3:2023, 7.3.3")
public enum NamespaceStatus {
    DRAFT,
    SUBMITTED,
    ACTIVE,
    DEPRECATED,
    OBSOLETE
}