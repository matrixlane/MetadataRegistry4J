package io.github.matrixlane.metadataregistry.web.standard;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Concept;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Entity;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.NamingConvention;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AttachedItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.ConstraintSet;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrySpecification;
import io.github.matrixlane.metadataregistry.dataspecification.ConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import io.github.matrixlane.metadataregistry.dataspecification.DataElementConcept;
import io.github.matrixlane.metadataregistry.dataspecification.DataType;
import io.github.matrixlane.metadataregistry.dataspecification.DataTypeScheme;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.Dimensionality;
import io.github.matrixlane.metadataregistry.dataspecification.LocalEnumeratedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.LocalEnumeratedValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.MeasureClass;
import io.github.matrixlane.metadataregistry.dataspecification.ObjectClass;
import io.github.matrixlane.metadataregistry.dataspecification.PermissibleValue;
import io.github.matrixlane.metadataregistry.dataspecification.PermissibleValueMeaning;
import io.github.matrixlane.metadataregistry.dataspecification.PermissibleValueSet;
import io.github.matrixlane.metadataregistry.dataspecification.Property;
import io.github.matrixlane.metadataregistry.dataspecification.ReferenceEnumeratedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.ReferenceEnumeratedValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.UnitOfMeasure;
import io.github.matrixlane.metadataregistry.dataspecification.ValueMeaning;
import io.github.matrixlane.metadataregistry.dataspecification.ValueMeaningSet;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 实体继承符合性守护测试（OpenSpec: registry-specification-model-correction）。
 *
 * <p>规则：实体之间的 {@code extends} 关系必须有 ISO/IEC 11179 对应部分
 * "Direct superclass" 条款依据。本测试把该规则机械化：</p>
 * <ol>
 *   <li>硬断言 {@link RegistrySpecification} 的直接超类是 {@link Entity}——ISO/IEC
 *       11179-3:2023 9.4.10 Registry_Specification class 无 Direct superclass 小节，
 *       不得实现为 {@link Item} 子类；</li>
 *   <li>白名单断言：扫描全部 {@code @Entity}，凡"直接超类也是 {@code @Entity}"的
 *       具体类（不限于 extends Item，如 Registrar extends Contact、AdministeredItem
 *       extends RegisteredItem）都必须登记标准条款引用，新增未登记的继承边即失败。</li>
 * </ol>
 *
 * <p>扫描不引入额外依赖：走 {@code ClassLoader.getResources} 枚举各模块 jar /
 * target-classes 下的包目录，读取 {@code .class} 文件。放在 web 模块是因为只有它
 * 的测试 classpath 覆盖全部业务模块。</p>
 */
class StandardInheritanceConformanceTest {

    private static final String PACKAGE_PATH = "io/github/matrixlane/metadataregistry";

    private static final String BACKLOG = "待全量审计（backlog：Item 子类继承符合性全量审计）";

    /**
     * 实体继承边白名单：类 → 标准 "Direct superclass" 条款引用。
     * 新增条目必须附标准依据（Milvus / 本地 PDF 原文核对），
     * 其余条目的占位引用指向全量审计 backlog。
     */
    private static final Map<Class<?>, String> INHERITANCE_WHITELIST = buildWhitelist();

    private static Map<Class<?>, String> buildWhitelist() {
        Map<Class<?>, String> whitelist = new LinkedHashMap<>();
        // —— 已按标准原文核实（2026-10-09 Milvus 检索）——
        whitelist.put(Context.class, "ISO/IEC 11179-3:2023 6.4.2.3");
        whitelist.put(Namespace.class, "ISO/IEC 11179-3:2023 7.3.3");
        whitelist.put(NamingConvention.class, "ISO/IEC 11179-3:2023 8.4.4");
        whitelist.put(ConstraintSet.class, "ISO/IEC 11179-3:2023 9.4.5");
        whitelist.put(Registrar.class, "ISO/IEC 11179-3:2023 9.4.7（§6.3.5.1 同述：Registrar is a subclass of Contact）");
        whitelist.put(RegistrationAuthority.class, "ISO/IEC 11179-3:2023 9.4.6（§9.2.2：Registration_Authority is a subclass of Organization）");
        whitelist.put(AdministeredItem.class, "ISO/IEC 11179-3:2023 9.4.2（Annex C：Administered_Item is a subclass of Registered_Item）");
        whitelist.put(AttachedItem.class, "ISO/IEC 11179-3:2023 9.4.3（Annex C：Attached_Item is a subclass of Registered_Item）");
        whitelist.put(ItemMapping.class, "ISO/IEC 11179-3:2023 11（Item_Mapping 为 Item 子类）");
        // —— 待全量审计（占位引用）——
        for (Class<?> itemSubclass : new Class<?>[]{
                Concept.class,
                DataElement.class,
                DataElementConcept.class,
                DataType.class,
                DataTypeScheme.class,
                DescribedConceptualDomain.class,
                DescribedValueDomain.class,
                Dimensionality.class,
                LocalEnumeratedConceptualDomain.class,
                LocalEnumeratedValueDomain.class,
                MeasureClass.class,
                ObjectClass.class,
                PermissibleValue.class,
                PermissibleValueMeaning.class,
                PermissibleValueSet.class,
                Property.class,
                ReferenceEnumeratedConceptualDomain.class,
                ReferenceEnumeratedValueDomain.class,
                UnitOfMeasure.class,
                ValueMeaning.class,
                ValueMeaningSet.class}) {
            whitelist.putIfAbsent(itemSubclass, BACKLOG);
        }
        return whitelist;
    }

    @Test
    @DisplayName("RegistrySpecification 必须是独立根实体（ISO/IEC 11179-3:2023 9.4.10 无 Direct superclass）")
    void registrySpecification_mustExtendEntity() {
        assertThat(RegistrySpecification.class.getSuperclass())
                .as("RegistrySpecification 的直接超类必须是 Entity，不得继承 Item")
                .isEqualTo(Entity.class);
    }

    @Test
    @DisplayName("所有实体继承边都必须登记标准 Direct superclass 条款依据")
    void entityInheritanceEdges_mustBeRegisteredInWhitelist() {
        List<Class<?>> edges = concreteEntitiesWithEntitySuperclass();

        assertThat(edges)
                .as("扫描未发现任何实体继承边，说明包扫描失效，测试将失去守护作用")
                .isNotEmpty();

        List<String> unregistered = edges.stream()
                .filter(clazz -> !INHERITANCE_WHITELIST.containsKey(clazz))
                .map(Class::getName)
                .toList();

        assertThat(unregistered)
                .as("以下 @Entity 直接继承了另一个 @Entity 却没有标准 Direct superclass 条款依据登记：%n%s%n"
                        + "请核对 ISO/IEC 11179 对应部分原文后追加到 INHERITANCE_WHITELIST，"
                        + "确无依据的实现应改为独立根实体",
                        String.join(System.lineSeparator(), unregistered))
                .isEmpty();
    }

    @Test
    @DisplayName("白名单条目不得失效（实体已删除或继承关系已变更时须同步移除登记）")
    void whitelistEntries_mustStillBeEntitySubclasses() {
        Set<Class<?>> discovered = Set.copyOf(concreteEntitiesWithEntitySuperclass());

        List<String> stale = INHERITANCE_WHITELIST.keySet().stream()
                .filter(clazz -> !discovered.contains(clazz))
                .map(Class::getName)
                .toList();

        assertThat(stale)
                .as("以下白名单条目已不是直接继承 @Entity 的具体类，请同步移除登记：%n%s",
                        String.join(System.lineSeparator(), stale))
                .isEmpty();
    }

    /** 直接超类也是 {@code @Entity} 的具体类——即全部实体继承边的子节点。 */
    private static List<Class<?>> concreteEntitiesWithEntitySuperclass() {
        List<Class<?>> result = new ArrayList<>();
        for (Class<?> candidate : scanEntityClasses()) {
            if (Modifier.isAbstract(candidate.getModifiers())) {
                continue;
            }
            Class<?> superclass = candidate.getSuperclass();
            if (superclass == null
                    || !superclass.isAnnotationPresent(jakarta.persistence.Entity.class)) {
                continue;
            }
            result.add(candidate);
        }
        return result;
    }

    private static List<Class<?>> scanEntityClasses() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = StandardInheritanceConformanceTest.class.getClassLoader();
        }
        Set<String> classNames = new TreeSet<>();
        try {
            Enumeration<URL> resources = classLoader.getResources(PACKAGE_PATH);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                if ("file".equals(resource.getProtocol())) {
                    collectFromDirectory(Paths.get(resource.toURI()), PACKAGE_PATH, classNames);
                } else if ("jar".equals(resource.getProtocol())) {
                    collectFromJar(resource, classNames);
                }
            }
        } catch (IOException | java.net.URISyntaxException e) {
            throw new IllegalStateException("扫描包 " + PACKAGE_PATH + " 失败", e);
        }

        List<Class<?>> entities = new ArrayList<>();
        for (String className : classNames) {
            Class<?> clazz;
            try {
                clazz = Class.forName(className, false, classLoader);
            } catch (ClassNotFoundException | LinkageError e) {
                continue;
            }
            if (isTestFixture(clazz)) {
                continue;
            }
            if (clazz.isAnnotationPresent(jakarta.persistence.Entity.class)) {
                entities.add(clazz);
            }
        }
        return entities;
    }

    /** 测试源码树里的 @Entity 夹具（如 {@code XxxServiceTest$TestItem}）不属于注册元模型，不纳入白名单。 */
    private static boolean isTestFixture(Class<?> clazz) {
        ProtectionDomain protectionDomain = clazz.getProtectionDomain();
        CodeSource codeSource = protectionDomain == null ? null : protectionDomain.getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            return false;
        }
        return codeSource.getLocation().toString().replace('\\', '/').contains("/test-classes");
    }

    private static void collectFromDirectory(Path root, String packagePath, Set<String> classNames)
            throws IOException {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".class")).forEach(path -> {
                String relative = root.relativize(path).toString().replace('\\', '/');
                classNames.add(toClassName(packagePath + "/" + relative));
            });
        }
    }

    private static void collectFromJar(URL resource, Set<String> classNames) throws IOException {
        JarURLConnection connection = (JarURLConnection) resource.openConnection();
        try (JarFile jarFile = connection.getJarFile()) {
            Enumeration<JarEntry> entries = jarFile.entries();
            String prefix = PACKAGE_PATH + "/";
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith(prefix) && name.endsWith(".class")) {
                    classNames.add(toClassName(name));
                }
            }
        }
    }

    private static String toClassName(String classFile) {
        return classFile.substring(0, classFile.length() - ".class".length()).replace('/', '.');
    }
}
