package io.github.matrixlane.metadataregistry.itemmapping.enums;

/**
 * Independent status enum for mapping scheme version lifecycle.
 *
 * <p>Defines the three states a mapping scheme version can be in:
 * <ul>
 *   <li>{@link #DRAFT} — editable, deletable. Initial state.</li>
 *   <li>{@link #PUBLISHED} — locked, read-only. Can create new draft versions from this.</li>
 *   <li>{@link #ARCHIVED} — read-only, historical.</li>
 * </ul>
 *
 * <p>This is NOT the same as WI-007's 4-state registration lifecycle.
 * Mapping schemes use their own independent 3-state lifecycle.
 *
 * <p>Valid transitions:
 * <pre>
 * Draft ──[publish]──&gt; Published ──[archive]──&gt; Archived
 *   │
 *   └──[new-version]──&gt; New Draft
 * </pre>
 *
 * @see io.github.matrixlane.metadataregistry.itemmapping.entity.MappingScheme
 */
public enum MappingSchemeStatus {

    DRAFT,
    PUBLISHED,
    ARCHIVED
}
