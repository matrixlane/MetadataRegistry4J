package io.github.matrixlane.metadataregistry.web.seed;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.DocumentType;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Individual;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.ReferenceDocument;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.EmailAddress;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.PhoneNumber;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.PostalAddress;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Definition;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import io.github.matrixlane.metadataregistry.dataspecification.DataElementConcept;
import io.github.matrixlane.metadataregistry.dataspecification.DataType;
import io.github.matrixlane.metadataregistry.dataspecification.DataTypeScheme;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.ObjectClass;
import io.github.matrixlane.metadataregistry.dataspecification.Property;
import io.github.matrixlane.metadataregistry.dataspecification.ValueDomain;
import io.github.matrixlane.metadataregistry.web.auth.AppUser;
import io.github.matrixlane.metadataregistry.web.auth.AppUserRepository;
import io.github.matrixlane.metadataregistry.web.auth.AppUserRole;
import io.github.matrixlane.metadataregistry.web.repository.ConceptualDomainRepository;
import io.github.matrixlane.metadataregistry.web.repository.DataElementConceptRepository;
import io.github.matrixlane.metadataregistry.web.repository.DataElementRepository;
import io.github.matrixlane.metadataregistry.web.repository.DataTypeRepository;
import io.github.matrixlane.metadataregistry.web.repository.DataTypeSchemeRepository;
import io.github.matrixlane.metadataregistry.web.repository.DocumentTypeRepository;
import io.github.matrixlane.metadataregistry.web.repository.IndividualRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.ObjectClassRepository;
import io.github.matrixlane.metadataregistry.web.repository.OrganizationRepository;
import io.github.matrixlane.metadataregistry.web.repository.PropertyRepository;
import io.github.matrixlane.metadataregistry.web.repository.ReferenceDocumentRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.repository.ValueDomainRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 启动时初始化演示用的种子数据。仅当数据库中没有任何 AppUser 时才会执行，
 * 因此重复启动不会破坏已有数据。可通过 app.seed.enabled=false 关闭。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements ApplicationRunner {

    private final AppUserRepository appUserRepository;
    private final LanguageIdentificationRepository languageRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final ReferenceDocumentRepository referenceDocumentRepository;
    private final OrganizationRepository organizationRepository;
    private final IndividualRepository individualRepository;
    private final RegistrationAuthorityRepository registrationAuthorityRepository;
    private final NamespaceRepository namespaceRepository;
    private final DataTypeSchemeRepository dataTypeSchemeRepository;
    private final DataTypeRepository dataTypeRepository;
    private final ObjectClassRepository objectClassRepository;
    private final PropertyRepository propertyRepository;
    private final DataElementConceptRepository dataElementConceptRepository;
    private final ConceptualDomainRepository conceptualDomainRepository;
    private final ValueDomainRepository valueDomainRepository;
    private final DataElementRepository dataElementRepository;
    private final PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (appUserRepository.count() > 0) {
            log.info("AppUser 表已有数据，跳过种子数据初始化");
            return;
        }
        log.info("开始初始化种子数据");

        SeedContext ctx = new SeedContext();
        seedAppUsers();
        seedLanguages(ctx);
        seedDocumentTypeAndReference(ctx);
        seedOrganizationAndIndividual(ctx);
        seedRegistrationAuthorityAndNamespace(ctx);
        seedDataTypeAndScheme(ctx);
        seedObjectClassesAndProperties(ctx);
        seedDataElementConcepts(ctx);
        seedConceptualAndValueDomains(ctx);
        seedDataElements(ctx);

        log.info("种子数据初始化完成");
    }

    private void seedAppUsers() {
        appUserRepository.save(buildUser("admin", "Admin@123", "系统管理员",
                "admin@example.com", AppUserRole.ADMIN));
        appUserRepository.save(buildUser("registrar", "Registrar@123", "示例注册人员",
                "registrar@example.com", AppUserRole.REGISTRAR));
        appUserRepository.save(buildUser("viewer", "Viewer@123", "示例查看者",
                "viewer@example.com", AppUserRole.VIEWER));
        log.info("已创建演示账号：admin / Admin@123、registrar / Registrar@123、viewer / Viewer@123");
    }

    private AppUser buildUser(String username, String rawPassword, String displayName,
                              String email, AppUserRole role) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setRole(role);
        user.setEnabled(true);
        return user;
    }

    private void seedLanguages(SeedContext ctx) {
        ctx.langZh = upsertLanguage("zh-CN", "Hans", "CN");
        ctx.langEn = upsertLanguage("en-US", "Latn", "US");
        ctx.langJa = upsertLanguage("ja-JP", "Jpan", "JP");
    }

    private LanguageIdentification upsertLanguage(String identifier, String script, String territory) {
        return languageRepository.findByLanguageIdentifier(identifier).orElseGet(() -> {
            LanguageIdentification lang = new LanguageIdentification();
            lang.setLanguageIdentifier(identifier);
            lang.setScriptIdentifier(script);
            lang.setGeopoliticalTerritoryIdentifier(territory);
            return languageRepository.save(lang);
        });
    }

    private void seedDocumentTypeAndReference(SeedContext ctx) {
        DocumentType isoStandard = new DocumentType();
        isoStandard.setIdentifier("ISO_STANDARD");
        isoStandard.setDescription(Text.of("ISO/IEC 国际标准文件"));
        isoStandard.setSchemeReference(Sign.of("ISO/IEC"));
        ctx.documentTypeIsoStandard = documentTypeRepository.save(isoStandard);

        ReferenceDocument doc = new ReferenceDocument();
        doc.setIdentifier("ISO-IEC-11179-1-2023");
        doc.setTitle(Text.of("ISO/IEC 11179-1:2023 元数据登记 — 第 1 部分：框架"));
        doc.setUri("https://www.iso.org/standard/78914.html");
        doc.setTypeDescription(ctx.documentTypeIsoStandard);
        doc.getLanguage().add(ctx.langEn);
        doc.getLanguage().add(ctx.langZh);
        ctx.referenceDocIso11179 = referenceDocumentRepository.save(doc);
    }

    private void seedOrganizationAndIndividual(SeedContext ctx) {
        Organization org = new Organization();
        org.getName().add(Sign.of("示例集团有限公司"));
        org.getName().add(Sign.of("Example Group Co., Ltd."));
        org.setMailAddress(PostalAddress.of("北京市海淀区中关村大街 1 号"));
        org.getEmailAddress().add(EmailAddress.of("info@example.com"));
        org.getPhoneNumber().add(PhoneNumber.of("+86-10-12345678"));
        org.setWebAddress("https://www.example.com");
        ctx.exampleOrganization = organizationRepository.save(org);

        Individual ind = new Individual();
        ind.setName(Sign.of("张三"));
        ind.setTitle("元数据架构师");
        ind.setMailAddress(PostalAddress.of("北京市海淀区中关村大街 1 号"));
        ind.getEmailAddress().add(EmailAddress.of("zhangsan@example.com"));
        ind.getPhoneNumber().add(PhoneNumber.of("+86-138-0000-0000"));
        ctx.exampleIndividual = individualRepository.save(ind);

        // Contact 关联 Org+Individual，Cascade 不会触发，但 Contact 实体没有标准 repo，
        // 直接通过 EntityManager 持久化即可。
        Contact contact = new Contact();
        contact.setOrganization(ctx.exampleOrganization);
        contact.setIndividual(ctx.exampleIndividual);
        entityManager.persist(contact);
    }

    private void seedRegistrationAuthorityAndNamespace(SeedContext ctx) {
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.getName().add(Sign.of("示例注册机构"));
        ra.getName().add(Sign.of("Example Registration Authority"));
        ra.getEmailAddress().add(EmailAddress.of("registry@example.com"));
        ra.getDocumentationLanguageIdentifier().add(ctx.langZh);
        ra.getDocumentationLanguageIdentifier().add(ctx.langEn);
        ctx.registrationAuthority = registrationAuthorityRepository.save(ra);

        Namespace ns = new Namespace();
        addDesignation(ns, ctx.langZh, "示例命名空间");
        addDesignation(ns, ctx.langEn, "Demo Namespace");
        addDefinition(ns, ctx.langZh, "用于演示元数据登记流程的命名空间。");
        ns.setRegistrationAuthority(ctx.registrationAuthority);
        ctx.namespace = namespaceRepository.save(ns);

        ctx.registrationAuthority.setNamespace(ctx.namespace);
        ctx.registrationAuthority = registrationAuthorityRepository.save(ctx.registrationAuthority);
    }

    private void seedDataTypeAndScheme(SeedContext ctx) {
        DataTypeScheme scheme = new DataTypeScheme();
        scheme.setReference("ISO/IEC 11404 通用数据类型");
        addDesignation(scheme, ctx.langZh, "ISO/IEC 11404 基本数据类型方案");
        addDesignation(scheme, ctx.langEn, "ISO/IEC 11404 General Purpose Datatypes");
        addDefinition(scheme, ctx.langZh, "依据 ISO/IEC 11404 提供的一组通用数据类型。");
        ctx.dataTypeScheme = dataTypeSchemeRepository.save(scheme);
        attachAdministeredItem(ctx, ctx.dataTypeScheme, "DTS-COMMON", "1.0", RegistrationStatus.STANDARD);

        ctx.dtString = createDataType(ctx, "STRING", "字符串", "String", "可变长度的 Unicode 字符序列。");
        ctx.dtInteger = createDataType(ctx, "INTEGER", "整数", "Integer", "有符号整型数值。");
        ctx.dtDecimal = createDataType(ctx, "DECIMAL", "定点数", "Decimal", "任意精度的十进制数值。");
        ctx.dtBoolean = createDataType(ctx, "BOOLEAN", "布尔值", "Boolean", "true/false 二值。");
        ctx.dtDate = createDataType(ctx, "DATE", "日期", "Date", "公历日期，精确到日。");
    }

    private DataType createDataType(SeedContext ctx, String identifier,
                                    String zhLabel, String enLabel, String zhDef) {
        DataType dt = new DataType();
        dt.setDataTypeScheme(ctx.dataTypeScheme);
        addDesignation(dt, ctx.langZh, zhLabel);
        addDesignation(dt, ctx.langEn, enLabel);
        addDefinition(dt, ctx.langZh, zhDef);
        DataType saved = dataTypeRepository.save(dt);
        attachAdministeredItem(ctx, saved, identifier, "1.0", RegistrationStatus.STANDARD);
        return dataTypeRepository.save(saved);
    }

    private void seedObjectClassesAndProperties(SeedContext ctx) {
        ctx.ocCustomer = createObjectClass(ctx, "OC-CUSTOMER", "客户", "Customer",
                "购买产品或服务的法人或自然人。");
        ctx.ocProduct = createObjectClass(ctx, "OC-PRODUCT", "产品", "Product",
                "可销售给客户的商品或服务。");
        ctx.ocOrder = createObjectClass(ctx, "OC-ORDER", "订单", "Order",
                "客户下单购买产品的事务记录。");

        ctx.propIdentifier = createProperty(ctx, "PROP-IDENTIFIER", "标识", "Identifier",
                "用于在系统中唯一识别对象的字符串。");
        ctx.propName = createProperty(ctx, "PROP-NAME", "名称", "Name",
                "对象的可读名称。");
        ctx.propAmount = createProperty(ctx, "PROP-AMOUNT", "金额", "Amount",
                "以特定货币计的数值。");
        ctx.propCurrency = createProperty(ctx, "PROP-CURRENCY", "币种", "Currency",
                "金额所采用的货币代码。");
        ctx.propStatus = createProperty(ctx, "PROP-STATUS", "状态", "Status",
                "对象当前所处的业务状态。");
        ctx.propCreatedAt = createProperty(ctx, "PROP-CREATED-AT", "创建时间", "Created At",
                "对象被创建的时间戳。");
    }

    private ObjectClass createObjectClass(SeedContext ctx, String identifier, String zhLabel,
                                          String enLabel, String zhDef) {
        ObjectClass oc = new ObjectClass();
        addDesignation(oc, ctx.langZh, zhLabel);
        addDesignation(oc, ctx.langEn, enLabel);
        addDefinition(oc, ctx.langZh, zhDef);
        ObjectClass saved = objectClassRepository.save(oc);
        attachAdministeredItem(ctx, saved, identifier, "1.0", RegistrationStatus.STANDARD);
        return objectClassRepository.save(saved);
    }

    private Property createProperty(SeedContext ctx, String identifier, String zhLabel,
                                    String enLabel, String zhDef) {
        Property p = new Property();
        addDesignation(p, ctx.langZh, zhLabel);
        addDesignation(p, ctx.langEn, enLabel);
        addDefinition(p, ctx.langZh, zhDef);
        Property saved = propertyRepository.save(p);
        attachAdministeredItem(ctx, saved, identifier, "1.0", RegistrationStatus.STANDARD);
        return propertyRepository.save(saved);
    }

    private void seedDataElementConcepts(SeedContext ctx) {
        ctx.decCustomerName = createDec(ctx, "DEC-CUSTOMER-NAME", ctx.ocCustomer, ctx.propName,
                "客户名称", "Customer Name", "客户的可读名称。");
        ctx.decOrderAmount = createDec(ctx, "DEC-ORDER-AMOUNT", ctx.ocOrder, ctx.propAmount,
                "订单金额", "Order Amount", "订单的总金额。");
        ctx.decOrderStatus = createDec(ctx, "DEC-ORDER-STATUS", ctx.ocOrder, ctx.propStatus,
                "订单状态", "Order Status", "订单当前所处的业务状态。");
        ctx.decProductIdentifier = createDec(ctx, "DEC-PRODUCT-IDENTIFIER", ctx.ocProduct,
                ctx.propIdentifier, "产品编号", "Product Identifier", "产品在系统中的唯一标识。");
    }

    private DataElementConcept createDec(SeedContext ctx, String identifier, ObjectClass oc,
                                         Property prop, String zhLabel, String enLabel,
                                         String zhDef) {
        DataElementConcept dec = new DataElementConcept();
        dec.setObjectClass(oc);
        dec.setProperty(prop);
        addDesignation(dec, ctx.langZh, zhLabel);
        addDesignation(dec, ctx.langEn, enLabel);
        addDefinition(dec, ctx.langZh, zhDef);
        DataElementConcept saved = dataElementConceptRepository.save(dec);
        attachAdministeredItem(ctx, saved, identifier, "1.0", RegistrationStatus.STANDARD);
        return dataElementConceptRepository.save(saved);
    }

    private void seedConceptualAndValueDomains(SeedContext ctx) {
        DescribedConceptualDomain cdMoney = new DescribedConceptualDomain();
        addDesignation(cdMoney, ctx.langZh, "货币金额");
        addDesignation(cdMoney, ctx.langEn, "Monetary Amount");
        addDefinition(cdMoney, ctx.langZh, "以货币表示的非负实数。");
        DescribedConceptualDomain savedCdMoney = (DescribedConceptualDomain)
                conceptualDomainRepository.save(cdMoney);
        attachAdministeredItem(ctx, savedCdMoney, "CD-MONETARY", "1.0", RegistrationStatus.STANDARD);
        ctx.cdMonetary = (DescribedConceptualDomain) conceptualDomainRepository.save(savedCdMoney);

        DescribedConceptualDomain cdName = new DescribedConceptualDomain();
        addDesignation(cdName, ctx.langZh, "名称");
        addDesignation(cdName, ctx.langEn, "Name");
        addDefinition(cdName, ctx.langZh, "标识对象的可读字符序列。");
        DescribedConceptualDomain savedCdName = (DescribedConceptualDomain)
                conceptualDomainRepository.save(cdName);
        attachAdministeredItem(ctx, savedCdName, "CD-NAME", "1.0", RegistrationStatus.STANDARD);
        ctx.cdName = (DescribedConceptualDomain) conceptualDomainRepository.save(savedCdName);

        ctx.vdRmbAmount = createDescribedValueDomain(ctx, "VD-RMB-AMOUNT", ctx.cdMonetary,
                ctx.dtDecimal, "人民币金额", "RMB Amount", "以人民币元为单位的金额。",
                18, "###,##0.00");
        ctx.vdShortName = createDescribedValueDomain(ctx, "VD-SHORT-NAME", ctx.cdName,
                ctx.dtString, "短名称（≤64）", "Short Name (≤64)",
                "长度不超过 64 字符的名称。", 64, null);
    }

    private DescribedValueDomain createDescribedValueDomain(SeedContext ctx, String identifier,
                                                            DescribedConceptualDomain cd,
                                                            DataType dt,
                                                            String zhLabel, String enLabel,
                                                            String zhDef,
                                                            Integer maxChar, String format) {
        DescribedValueDomain vd = new DescribedValueDomain();
        vd.setDescribedConceptualDomain(cd);
        vd.setDataType(dt);
        vd.setMaximumCharacterQuantity(maxChar);
        vd.setFormat(format);
        addDesignation(vd, ctx.langZh, zhLabel);
        addDesignation(vd, ctx.langEn, enLabel);
        addDefinition(vd, ctx.langZh, zhDef);
        DescribedValueDomain saved = (DescribedValueDomain) valueDomainRepository.save(vd);
        attachAdministeredItem(ctx, saved, identifier, "1.0", RegistrationStatus.STANDARD);
        return (DescribedValueDomain) valueDomainRepository.save(saved);
    }

    private void seedDataElements(SeedContext ctx) {
        createDataElement(ctx, "DE-CUSTOMER-NAME", ctx.decCustomerName, ctx.vdShortName,
                "客户名称", "Customer Name", "客户的法定名称或常用称谓。");
        createDataElement(ctx, "DE-ORDER-AMOUNT", ctx.decOrderAmount, ctx.vdRmbAmount,
                "订单金额（人民币）", "Order Amount (RMB)",
                "订单中以人民币元为单位的总金额。");
        createDataElement(ctx, "DE-PRODUCT-IDENTIFIER", ctx.decProductIdentifier, ctx.vdShortName,
                "产品编号", "Product Identifier", "产品的内部唯一标识。");
        createDataElement(ctx, "DE-ORDER-STATUS", ctx.decOrderStatus, ctx.vdShortName,
                "订单状态", "Order Status", "订单当前的业务状态描述。");
    }

    private DataElement createDataElement(SeedContext ctx, String identifier,
                                          DataElementConcept dec, ValueDomain vd,
                                          String zhLabel, String enLabel, String zhDef) {
        DataElement de = new DataElement();
        de.setDataElementConcept(dec);
        de.setValueDomain(vd);
        addDesignation(de, ctx.langZh, zhLabel);
        addDesignation(de, ctx.langEn, enLabel);
        addDefinition(de, ctx.langZh, zhDef);
        DataElement saved = dataElementRepository.save(de);
        attachAdministeredItem(ctx, saved, identifier, "1.0", RegistrationStatus.STANDARD);
        return dataElementRepository.save(saved);
    }

    private void addDesignation(Item item, LanguageIdentification lang, String value) {
        Designation d = new Designation();
        d.setItem(item);
        d.setSign(Sign.of(value));
        d.setLanguage(lang);
        item.getDesignations().add(d);
    }

    private void addDefinition(Item item, LanguageIdentification lang, String value) {
        Definition def = new Definition();
        def.setItem(item);
        def.setText(Text.of(value));
        def.setLanguage(lang);
        item.getDefinitions().add(def);
    }

    private void attachAdministeredItem(SeedContext ctx, Item item, String identifier,
                                        String version, RegistrationStatus status) {
        AdministeredItem ai = new AdministeredItem(item);
        ScopedIdentifier si = new ScopedIdentifier();
        si.setIdentifier(identifier);
        si.setVersion(version);
        si.setNamespace(ctx.namespace);
        si.setIdentifiedItem(ai);
        ai.getScopedIdentifiers().add(si);
        ai.setRegistrationAuthority(ctx.registrationAuthority);

        RegistrationState state = new RegistrationState();
        state.setRegistrationStatus(status);
        state.setEffectiveDate(LocalDateTime.now());
        ai.setRegistrationState(state);
        ai.setCreationDateTime(LocalDateTime.now());
        ai.setLastChangeDateTime(LocalDateTime.now());

        item.setIdentifiedItem(ai);
    }

    /**
     * 在种子化过程中传递已经创建的关键引用，避免多次查询。
     */
    private static class SeedContext {
        LanguageIdentification langZh;
        LanguageIdentification langEn;
        LanguageIdentification langJa;
        DocumentType documentTypeIsoStandard;
        ReferenceDocument referenceDocIso11179;
        Organization exampleOrganization;
        Individual exampleIndividual;
        RegistrationAuthority registrationAuthority;
        Namespace namespace;
        DataTypeScheme dataTypeScheme;
        DataType dtString;
        DataType dtInteger;
        DataType dtDecimal;
        DataType dtBoolean;
        DataType dtDate;
        ObjectClass ocCustomer;
        ObjectClass ocProduct;
        ObjectClass ocOrder;
        Property propIdentifier;
        Property propName;
        Property propAmount;
        Property propCurrency;
        Property propStatus;
        Property propCreatedAt;
        DataElementConcept decCustomerName;
        DataElementConcept decOrderAmount;
        DataElementConcept decOrderStatus;
        DataElementConcept decProductIdentifier;
        DescribedConceptualDomain cdMonetary;
        DescribedConceptualDomain cdName;
        DescribedValueDomain vdRmbAmount;
        DescribedValueDomain vdShortName;
    }
}
