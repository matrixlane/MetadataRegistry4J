import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

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
      path: '/',
      component: () => import('@/layouts/AppLayout.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/dashboard/Dashboard.vue'),
          meta: { title: '概览' },
        },
        {
          path: 'contexts',
          name: 'Contexts',
          component: () => import('@/views/context/ContextList.vue'),
          meta: { title: '上下文' },
        },
        {
          path: 'registration-authorities',
          name: 'RegistrationAuthorities',
          component: () => import('@/views/registration-authority/RegistrationAuthorityList.vue'),
          meta: { title: '注册机构' },
        },
        {
          path: 'registrars',
          name: 'Registrars',
          component: () => import('@/views/registrar/RegistrarList.vue'),
          meta: { title: '注册人员' },
        },
        {
          path: 'namespaces',
          name: 'Namespaces',
          component: () => import('@/views/namespace/NamespaceList.vue'),
          meta: { title: '命名空间' },
        },
        {
          path: 'organizations',
          name: 'Organizations',
          component: () => import('@/views/organization/OrganizationList.vue'),
          meta: { title: '组织' },
        },
        {
          path: 'individuals',
          name: 'Individuals',
          component: () => import('@/views/individual/IndividualList.vue'),
          meta: { title: '个人' },
        },
        {
          path: 'contacts',
          name: 'Contacts',
          component: () => import('@/views/contact/ContactList.vue'),
          meta: { title: '联系人' },
        },
        {
          path: 'reference-documents',
          name: 'ReferenceDocuments',
          component: () => import('@/views/reference-document/ReferenceDocumentList.vue'),
          meta: { title: '参考文献' },
        },
        {
          path: 'object-classes',
          name: 'ObjectClasses',
          component: () => import('@/views/object-class/ObjectClassList.vue'),
          meta: { title: '对象类' },
        },
        {
          path: 'properties',
          name: 'Properties',
          component: () => import('@/views/property/PropertyList.vue'),
          meta: { title: '特性' },
        },
        {
          path: 'data-element-concepts',
          name: 'DataElementConcepts',
          component: () => import('@/views/data-element-concept/DataElementConceptList.vue'),
          meta: { title: '数据元概念' },
        },
        {
          path: 'conceptual-domains',
          name: 'ConceptualDomains',
          component: () => import('@/views/conceptual-domain/ConceptualDomainList.vue'),
          meta: { title: '概念域' },
        },
        {
          path: 'value-domains',
          name: 'ValueDomains',
          component: () => import('@/views/value-domain/ValueDomainList.vue'),
          meta: { title: '值域' },
        },
        {
          path: 'data-types',
          name: 'DataTypes',
          component: () => import('@/views/data-type/DataTypeList.vue'),
          meta: { title: '数据类型' },
        },
        {
          path: 'data-type-schemes',
          name: 'DataTypeSchemes',
          component: () => import('@/views/data-type-scheme/DataTypeSchemeList.vue'),
          meta: { title: '数据类型方案' },
        },
        {
          path: 'units-of-measure',
          name: 'UnitsOfMeasure',
          component: () => import('@/views/unit-of-measure/UnitOfMeasureList.vue'),
          meta: { title: '计量单位' },
        },
        {
          path: 'measure-classes',
          name: 'MeasureClasses',
          component: () => import('@/views/measure-class/MeasureClassList.vue'),
          meta: { title: '计量类' },
        },
        {
          path: 'data-dictionary',
          name: 'DataDictionary',
          component: () => import('@/views/data-dictionary/BrowserView.vue'),
          meta: { title: '数据字典' },
        },
        {
          path: 'data-dictionary/:id',
          name: 'MetadataDetail',
          component: () => import('@/views/data-dictionary/MetadataDetailView.vue'),
          meta: { title: '元数据详情' },
        },
        {
          path: 'classification',
          name: 'Classification',
          component: () => import('@/views/classification/ClassificationTreeBrowser.vue'),
          meta: { title: '分类管理' },
        },
        {
          path: 'mapping-network',
          name: 'MappingNetwork',
          component: () => import('@/views/mapping/MappingNetworkView.vue'),
          meta: { title: '映射网络' },
        },
        {
          path: 'data-elements',
          name: 'DataElements',
          component: () => import('@/views/data-element/DataElementList.vue'),
          meta: { title: '数据元' },
        },
        {
          path: 'app-users',
          name: 'AppUsers',
          component: () => import('@/views/app-user/AppUserList.vue'),
          meta: { title: '用户管理', adminOnly: true },
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

router.beforeEach(async (to) => {
  const auth = useAuthStore();

  if (!auth.isLoggedIn && !to.meta.public) {
    return '/login';
  }

  if (auth.isLoggedIn && !auth.user) {
    try {
      await auth.fetchMe();
    } catch {
      auth.logout();
      return '/login';
    }
  }

  if (auth.isLoggedIn && to.path === '/login') {
    return '/';
  }

  if (to.meta.adminOnly && !auth.isAdmin) {
    return '/dashboard';
  }
});

export default router;
