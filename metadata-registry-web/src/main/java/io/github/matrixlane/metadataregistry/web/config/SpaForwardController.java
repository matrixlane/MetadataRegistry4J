package io.github.matrixlane.metadataregistry.web.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA 前端路由回退：直接访问或刷新 /login、/namespaces 等客户端路由时，
 * 转发到 index.html，由前端 history 路由接管。
 *
 * 采用显式路由清单（与 metadata-registry-ui/src/router/index.ts 对齐），
 * 避免通配模式吞掉 /assets/** 等静态资源；新增前端路由时需在此补充。
 */
@Controller
public class SpaForwardController {

    @GetMapping({
            "/login", "/init-wizard", "/dashboard", "/contexts",
            "/registration-authorities", "/registrars", "/namespaces", "/naming-conventions",
            "/administered-items", "/organizations", "/individuals", "/contacts",
            "/reference-documents", "/object-classes", "/properties", "/data-element-concepts",
            "/conceptual-domains", "/value-meanings", "/value-domains", "/data-types",
            "/data-type-schemes", "/units-of-measure", "/measure-classes", "/classification",
            "/mapping-network", "/interop-mappings", "/data-elements", "/app-users",
            "/languages", "/registry-specifications", "/system-settings", "/my-profile",
            "/dimensionality",
            // 含参数段的嵌套路由（/** 同时覆盖其前缀本身）
            "/data-dictionary/**", "/mapping-schemes/**", "/federation/**"
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
