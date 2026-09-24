package io.github.matrixlane.metadataregistry.commonfacilities.registration;

/**
 * 迁移规则的审核人模式（registration-process-designer，设计文档 §4.1）。
 *
 * <p>推进到 Qualified 及以上须注册机构批准，
 * 由该机构的 Registrar 代行；默认全部 Registrar 均可批准。</p>
 */
public enum ApproverMode {

    /** 该项所属注册机构的全部 Registrar 均可批准（默认）。 */
    ALL_REGISTRARS,

    /** 仅规则指定集合内的 Registrar 可批准。 */
    SELECTED_REGISTRARS
}
