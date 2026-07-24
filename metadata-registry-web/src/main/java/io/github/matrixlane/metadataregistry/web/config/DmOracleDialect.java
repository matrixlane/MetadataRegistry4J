package io.github.matrixlane.metadataregistry.web.config;

import org.hibernate.dialect.OracleDialect;
import org.hibernate.dialect.identity.IdentityColumnSupport;
import org.hibernate.dialect.identity.IdentityColumnSupportImpl;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.boot.model.TypeContributions;
import org.hibernate.type.descriptor.sql.internal.DdlTypeImpl;
import org.hibernate.type.SqlTypes;

/**
 * 达梦 DM8 方言（Oracle 兼容模式 + DM 专有 IDENTITY 语法）。
 *
 * <p>DM8 与 Oracle 高度兼容，但不接受 ANSI 标准的
 * {@code generated as identity} 列定义（语法分析出错）。
 * 本方�在 OracleDialect 基础上，将自增列语法替换为 DM 的
 * {@code identity(1,1)} 内联写法，并使用 {@code @@identity} 取回生成键。
 */
public class DmOracleDialect extends OracleDialect {

    @Override
    public void contributeTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
        super.contributeTypes(typeContributions, serviceRegistry);
        // DM 的 IDENTITY 列要求类型为 BIGINT/INT，不接受 number(19,0)；
        // 普通列使用 BIGINT 关键字 DM 同样兼容。
        typeContributions.getTypeConfiguration().getDdlTypeRegistry().addDescriptor(
                SqlTypes.BIGINT, new DdlTypeImpl(SqlTypes.BIGINT, "bigint", this));
    }

    @Override
    public IdentityColumnSupport getIdentityColumnSupport() {
        return new DmIdentityColumnSupport();
    }

    static class DmIdentityColumnSupport extends IdentityColumnSupportImpl {

        @Override
        public String getIdentityColumnString(int type) {
            // DM 内联自增语法：<type> identity(1,1)
            return " identity(1,1)";
        }

        @Override
        public String getIdentitySelectString(String table, String column, int type) {
            return "select @@identity";
        }

        @Override
        public boolean supportsIdentityColumns() {
            return true;
        }
    }
}
