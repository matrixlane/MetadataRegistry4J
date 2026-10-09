import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { routeTable } from './NavigationRouteTable';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/login/Login.vue'),
      meta: { public: true },
    },
    {
      path: '/init-wizard',
      name: 'InitWizard',
      component: () => import('@/views/init-wizard/InitWizard.vue'),
      meta: { public: true },
    },
    {
      // 连接恢复页：初始化状态未知（后端瞬时不可达/响应非法）时由守卫送入，
      // 静默重试至状态已知再分流。个人版免登录：本页取代登录页作为等待态，
      // 个人版因此永远不会渲染登录页（personal-no-login）。
      path: '/connecting',
      name: 'Connecting',
      component: () => import('@/views/connecting/Connecting.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: () => import('@/layouts/AppLayout.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/dashboard/Dashboard.vue'),
          meta: { title: '概览', routeKey: 'dashboard' },
        },
        {
          path: 'workspace',
          name: 'Workspace',
          component: () => import('@/views/workspace/WorkspaceView.vue'),
          meta: { title: '工作空间', routeKey: 'workspace' },
        },
        {
          path: 'contexts',
          name: 'Contexts',
          component: () => import('@/views/context/ContextList.vue'),
          meta: { title: '上下文', routeKey: 'contexts' },
        },
        {
          path: 'registration-authorities',
          name: 'RegistrationAuthorities',
          component: () => import('@/views/registration-authority/RegistrationAuthorityList.vue'),
          meta: { title: '注册机构', routeKey: 'registration-authorities' },
        },
        {
          path: 'registrars',
          name: 'Registrars',
          component: () => import('@/views/registrar/RegistrarList.vue'),
          meta: { title: '注册人员', routeKey: 'registrars' },
        },
        {
          path: 'namespaces',
          name: 'Namespaces',
          component: () => import('@/views/namespace/NamespaceList.vue'),
          meta: { title: '命名空间', routeKey: 'namespaces' },
        },
        {
          path: 'naming-conventions',
          name: 'NamingConventions',
          component: () => import('@/views/naming-convention/NamingConventionList.vue'),
          meta: { title: '命名规范', routeKey: 'naming-conventions' },
        },
        {
          path: 'registration-process',
          name: 'RegistrationProcess',
          component: () => import('@/views/registration-process/RegistrationProcessView.vue'),
          meta: { title: '注册流程', routeKey: 'registration-process' },
        },
        {
          path: 'administered-items',
          name: 'AdministeredItems',
          component: () => import('@/views/administered-item/AdministeredItemList.vue'),
          meta: { title: '注册项', routeKey: 'administered-items' },
        },
        {
          path: 'organizations',
          name: 'Organizations',
          component: () => import('@/views/organization/OrganizationList.vue'),
          meta: { title: '组织', routeKey: 'organizations' },
        },
        {
          path: 'individuals',
          name: 'Individuals',
          component: () => import('@/views/individual/IndividualList.vue'),
          meta: { title: '个人', routeKey: 'individuals' },
        },
        {
          path: 'contacts',
          name: 'Contacts',
          component: () => import('@/views/contact/ContactList.vue'),
          meta: { title: '联系人', routeKey: 'contacts' },
        },
        {
          path: 'reference-documents',
          name: 'ReferenceDocuments',
          component: () => import('@/views/reference-document/ReferenceDocumentList.vue'),
          meta: { title: '参考文献', routeKey: 'reference-documents' },
        },
        {
          path: 'document-types',
          name: 'DocumentTypes',
          component: () => import('@/views/document-type/DocumentTypeList.vue'),
          meta: { title: '文档类型', routeKey: 'document-types' },
        },
        {
          path: 'object-classes',
          name: 'ObjectClasses',
          component: () => import('@/views/object-class/ObjectClassList.vue'),
          meta: { title: '对象类', routeKey: 'object-classes' },
        },
        {
          path: 'properties',
          name: 'Properties',
          component: () => import('@/views/property/PropertyList.vue'),
          meta: { title: '特性', routeKey: 'properties' },
        },
        {
          path: 'data-element-concepts',
          name: 'DataElementConcepts',
          component: () => import('@/views/data-element-concept/DataElementConceptList.vue'),
          meta: { title: '数据元概念', routeKey: 'data-element-concepts' },
        },
        {
          path: 'conceptual-domains',
          name: 'ConceptualDomains',
          component: () => import('@/views/conceptual-domain/ConceptualDomainList.vue'),
          meta: { title: '概念域', routeKey: 'conceptual-domains' },
        },
        {
          path: 'value-meanings',
          name: 'ValueMeanings',
          component: () => import('@/views/value-meaning/ValueMeaningList.vue'),
          meta: { title: '值含义', routeKey: 'value-meanings' },
        },
        {
          path: 'value-domains',
          name: 'ValueDomains',
          component: () => import('@/views/value-domain/ValueDomainList.vue'),
          meta: { title: '值域', routeKey: 'value-domains' },
        },
        {
          path: 'data-types',
          name: 'DataTypes',
          component: () => import('@/views/data-type/DataTypeList.vue'),
          meta: { title: '数据类型', routeKey: 'data-types' },
        },
        {
          path: 'data-type-schemes',
          name: 'DataTypeSchemes',
          component: () => import('@/views/data-type-scheme/DataTypeSchemeList.vue'),
          meta: { title: '数据类型方案', routeKey: 'data-type-schemes' },
        },
        {
          path: 'units-of-measure',
          name: 'UnitsOfMeasure',
          component: () => import('@/views/unit-of-measure/UnitOfMeasureList.vue'),
          meta: { title: '计量单位', routeKey: 'units-of-measure' },
        },
        {
          path: 'measure-classes',
          name: 'MeasureClasses',
          component: () => import('@/views/measure-class/MeasureClassList.vue'),
          meta: { title: '计量类', routeKey: 'measure-classes' },
        },
        {
          path: 'data-dictionary',
          name: 'DataDictionary',
          component: () => import('@/views/data-dictionary/BrowserView.vue'),
          meta: { title: '数据字典', routeKey: 'data-dictionary' },
        },
        {
          path: 'data-dictionary/:id',
          name: 'MetadataDetail',
          component: () => import('@/views/data-dictionary/MetadataDetailView.vue'),
          meta: { title: '元数据详情', routeKey: 'metadata-detail' },
        },
        {
          path: 'classification',
          name: 'Classification',
          component: () => import('@/views/classification/ClassificationTreeBrowser.vue'),
          meta: { title: '分类管理', routeKey: 'classification' },
        },
        {
          path: 'item-mappings',
          name: 'ItemMappings',
          component: () => import('@/views/mapping/ItemMappingList.vue'),
          meta: { title: '项目映射', routeKey: 'item-mappings' },
        },
        {
          path: 'mapping-network',
          name: 'MappingNetwork',
          component: () => import('@/views/mapping/MappingNetworkView.vue'),
          meta: { title: '映射网络', routeKey: 'mapping-network' },
        },
        {
          path: 'interop-mappings',
          name: 'InteropMappings',
          component: () => import('@/views/interop-mapping/InteropMappingList.vue'),
          meta: { title: '匹配结果管理', routeKey: 'interop-mappings' },
        },
        {
          path: 'data-elements',
          name: 'DataElements',
          component: () => import('@/views/data-element/DataElementList.vue'),
          meta: { title: '数据元', routeKey: 'data-elements' },
        },
        {
          path: 'app-users/:pathMatch(.*)*',
          redirect: '/system/users',
        },
        {
          path: 'system',
          name: 'System',
          component: () => import('@/views/system/SystemLayout.vue'),
          meta: { title: '系统', routeKey: 'system', minRole: 'ADMIN' },
          redirect: '/system/general',
          children: [
            {
              path: 'users',
              name: 'SystemUsers',
              component: () => import('@/views/app-user/AppUserList.vue'),
              meta: { title: '用户管理', routeKey: 'app-users', minRole: 'ADMIN' },
            },
            {
              path: 'general',
              name: 'SystemSettingsGeneral',
              component: () => import('@/views/system/settings/GeneralSettingsPage.vue'),
              meta: { title: '通用', routeKey: 'system-settings', minRole: 'ADMIN' },
            },
            {
              path: 'datasource',
              name: 'SystemSettingsDatasource',
              component: () => import('@/views/system/settings/DatasourceSettingsPage.vue'),
              meta: { title: '数据源', routeKey: 'system-settings', minRole: 'ADMIN' },
            },
            {
              path: 'danger',
              name: 'SystemSettingsDanger',
              component: () => import('@/views/system/settings/DangerSettingsPage.vue'),
              meta: { title: '危险区域', routeKey: 'system-settings', minRole: 'ADMIN' },
            },
            {
              path: 'settings/:pathMatch(.*)*',
              redirect: '/system/general',
            },
          ],
        },
        {
          path: 'system-settings/:pathMatch(.*)*',
          redirect: '/system/general',
        },
        {
          path: 'languages',
          name: 'Languages',
          component: () => import('@/views/language/LanguageList.vue'),
          meta: { title: '语言', routeKey: 'languages' },
        },
        {
          path: 'registry-specifications',
          name: 'RegistrySpecifications',
          component: () => import('@/views/registry-spec/RegistrySpecificationSettings.vue'),
          meta: { title: '注册规范', routeKey: 'registry-specifications' },
        },
        {
          path: 'app-settings',
          name: 'AppSettings',
          component: () => import('@/views/app-settings/AppSettingsView.vue'),
          meta: { title: '应用设置', routeKey: 'app-settings' },
        },
        {
          path: 'my-profile',
          alias: 'my',
          name: 'MyProfile',
          component: () => import('@/views/profile/MyProfile.vue'),
          meta: { title: '我的', routeKey: 'my-profile' },
        },
        {
          path: 'dimensionality',
          name: 'Dimensionality',
          component: () => import('@/views/dimensionality/DimensionalityList.vue'),
          meta: { title: '维度', routeKey: 'dimensionality' },
        },
        {
          path: 'roles',
          name: 'Roles',
          component: () => import('@/views/role/RoleList.vue'),
          meta: { title: '角色', routeKey: 'roles' },
        },
        {
          path: 'classification-items',
          name: 'ClassificationItems',
          component: () => import('@/views/placeholder/PlaceholderPage.vue'),
          meta: { title: '分类项', routeKey: 'classification-items' },
        },
        {
          path: 'federation/nodes',
          name: 'FederationNodes',
          component: () => import('@/views/federation/FederationNodesView.vue'),
          meta: { title: '联邦节点', routeKey: 'federation-nodes', minRole: 'ADMIN' },
        },
        {
          path: 'federation/merge-requests',
          name: 'FederationMergeRequests',
          component: () => import('@/views/federation/MergeRequestListView.vue'),
          meta: { title: '合并请求', routeKey: 'federation-merge-requests', minRole: 'ADMIN' },
        },
        {
          path: 'federation/merge-requests/:id',
          name: 'FederationMergeRequestReview',
          component: () => import('@/views/federation/MergeRequestReviewView.vue'),
          meta: { title: '合并评审', routeKey: 'federation-merge-request-review', minRole: 'ADMIN' },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'NotFound',
      component: () => import('@/views/error/NotFound.vue'),
    },
  ],
});

// ── Initialize NavigationRouteTable ─────────────────────────────
// Register every known route so the route table can resolve names,
// show placeholder pages for planned features, and filter by role.

routeTable.register(
  { key: 'dashboard', path: '/dashboard', title: '概览', status: 'implemented' },
  { key: 'workspace', path: '/workspace', title: '工作空间', status: 'implemented' },
  { key: 'data-dictionary', path: '/data-dictionary', title: '数据字典', status: 'implemented' },
  { key: 'metadata-detail', path: '/data-dictionary/:id', title: '元数据详情', status: 'implemented' },
  { key: 'contexts', path: '/contexts', title: '上下文', status: 'implemented' },
  { key: 'registration-authorities', path: '/registration-authorities', title: '注册机构', status: 'implemented' },
  { key: 'registrars', path: '/registrars', title: '注册人员', status: 'implemented' },
  { key: 'namespaces', path: '/namespaces', title: '命名空间', status: 'implemented' },
  { key: 'naming-conventions', path: '/naming-conventions', title: '命名规范', status: 'implemented' },
  { key: 'administered-items', path: '/administered-items', title: '注册项', status: 'implemented' },
  { key: 'registration-process', path: '/registration-process', title: '注册流程', status: 'implemented' },
  { key: 'organizations', path: '/organizations', title: '组织', status: 'implemented' },
  { key: 'individuals', path: '/individuals', title: '个人', status: 'implemented' },
  { key: 'contacts', path: '/contacts', title: '联系人', status: 'implemented' },
  { key: 'reference-documents', path: '/reference-documents', title: '参考文献', status: 'implemented' },
  { key: 'document-types', path: '/document-types', title: '文档类型', status: 'implemented' },
  { key: 'object-classes', path: '/object-classes', title: '对象类', status: 'implemented' },
  { key: 'properties', path: '/properties', title: '特性', status: 'implemented' },
  { key: 'data-element-concepts', path: '/data-element-concepts', title: '数据元概念', status: 'implemented' },
  { key: 'conceptual-domains', path: '/conceptual-domains', title: '概念域', status: 'implemented' },
  { key: 'value-meanings', path: '/value-meanings', title: '值含义', status: 'implemented' },
  { key: 'value-domains', path: '/value-domains', title: '值域', status: 'implemented' },
  { key: 'data-types', path: '/data-types', title: '数据类型', status: 'implemented' },
  { key: 'data-type-schemes', path: '/data-type-schemes', title: '数据类型方案', status: 'implemented' },
  { key: 'units-of-measure', path: '/units-of-measure', title: '计量单位', status: 'implemented' },
  { key: 'measure-classes', path: '/measure-classes', title: '计量类', status: 'implemented' },
  { key: 'classification', path: '/classification', title: '分类管理', status: 'implemented' },
  { key: 'item-mappings', path: '/item-mappings', title: '项目映射', status: 'implemented' },
  { key: 'mapping-network', path: '/mapping-network', title: '映射网络', status: 'implemented' },
  { key: 'interop-mappings', path: '/interop-mappings', title: '匹配结果管理', status: 'implemented' },
  { key: 'data-elements', path: '/data-elements', title: '数据元', status: 'implemented' },
  { key: 'app-users', path: '/system/users', title: '用户管理', status: 'implemented', minRole: 'ADMIN' },
  { key: 'languages', path: '/languages', title: '语言', status: 'implemented' },
  { key: 'registry-specifications', path: '/registry-specifications', title: '注册规范', status: 'implemented' },
  { key: 'system', path: '/system', title: '系统', status: 'implemented', minRole: 'ADMIN' },
  { key: 'system-settings', path: '/system/general', title: '系统设置', status: 'implemented', minRole: 'ADMIN' },
  { key: 'app-settings', path: '/app-settings', title: '应用设置', status: 'implemented' },
  { key: 'my-profile', path: '/my-profile', title: '我的', status: 'implemented' },
  { key: 'federation-nodes', path: '/federation/nodes', title: '联邦节点', status: 'implemented', minRole: 'ADMIN' },
  { key: 'federation-merge-requests', path: '/federation/merge-requests', title: '合并请求', status: 'implemented', minRole: 'ADMIN' },

  // ── Planned features (will show PlaceholderPage) ─────────────
  { key: 'dimensionality', path: '/dimensionality', title: '维度', status: 'implemented' },
  { key: 'roles', path: '/roles', title: '角色', status: 'implemented' },
  { key: 'classification-items', path: '/classification-items', title: '分类项', status: 'planned' },
  { key: 'concept-management', path: '/concept-management', title: '概念管理', status: 'planned' },
  { key: 'context-management', path: '/context-management', title: '上下文管理', status: 'planned' },
  { key: 'registration-agency-management', path: '/registration-agency-management', title: '注册机构管理', status: 'planned' },
  { key: 'personal-settings', path: '/personal-settings', title: '个人设置', status: 'planned' },
);

router.beforeEach(async (to) => {
  const auth = useAuthStore();

  // ── Initialization guard (highest priority) ────────────────────────────
  // Check whether the system has been set up. If not, force ALL routes —
  // including /login — to the init wizard. This catches the case where a
  // factory reset was performed while the user still has a valid token, and
  // also the case where a freshly-reset system has no token at all and the
  // user lands on the root URL.
  // Skip the check for the wizard itself to avoid an infinite redirect.
  // /connecting 同样跳过：它就是状态未知时的重试等待页，由它自己重查后分流。
  if (to.path !== '/init-wizard' && to.path !== '/connecting') {
    const isInitialized = await auth.checkInitialized();
    // 状态未知（重试后仍不可达/响应非法）：不猜测、不踢向登录页——个人版
    // 没有可去的登录页，进连接恢复页静默重试，恢复后回跳目标。
    if (auth.initialized === null) {
      return { path: '/connecting', query: { redirect: to.fullPath } };
    }
    if (!isInitialized) {
      return '/init-wizard';
    }
  }

  // Allow public routes (login, init-wizard) without auth
  if (to.meta.public) {
    // Already logged-in users should not see the login page,
    // but only redirect them to dashboard if the system is initialized.
    if (auth.isLoggedIn && to.path === '/login') {
      return '/dashboard';
    }
    return true;
  }

  // Redirect to login if not authenticated
  if (!auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }

  // Fetch user profile if needed (silent auth check).
  // 个人版免登录：fetchMe 失败（如后端暂不可达）时不踢向登录页——
  // 个人版没有登录页可去，保持匿名主体继续渲染即可。
  if (!auth.user) {
    try {
      await auth.fetchMe();
    } catch (e) {
      const status = (e as { response?: { status?: number } })?.response?.status;
      if (status === 404 && auth.bypassLogin && auth.token) {
        // 个人版免登录：localStorage 可能残留旧部署的令牌（库已重建、账号 UUID 已变），
        // Bearer 头会绕过隐式管理员认证导致 /auth/me 404。丢弃残留令牌后，后续请求
        // 改走匿名通道，由 PersonalEditionAuthenticationFilter 装载隐式管理员。
        auth.token = '';
        localStorage.removeItem('token');
      }
      if (!auth.bypassLogin) {
        auth.logout();
        return '/login';
      }
    }
  }

  // Role-based access control
  const minRole = to.meta.minRole as string | undefined;
  if (minRole && !auth.hasRole(minRole)) {
    return '/dashboard';
  }
});

export default router;
