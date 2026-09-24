package io.github.matrixlane.metadataregistry.commonfacilities.registration;

/**
 * 迁移规则的通过规则（quorum，设计文档 §4.1）。
 */
public enum QuorumType {

    /** 任一名合格审核 Registrar 批准即生效（默认）。 */
    ANY_ONE,

    /** 至少 quorum_count 名不同 Registrar 批准才生效。 */
    AT_LEAST
}
