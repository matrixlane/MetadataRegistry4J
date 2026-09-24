package io.github.matrixlane.metadataregistry.web.inference;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Slot;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import io.github.matrixlane.metadataregistry.dataspecification.DataElementConcept;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedValueDomain;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemMetaResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineContextResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineResponse;
import io.github.matrixlane.metadataregistry.web.repository.ContextRepository;
import io.github.matrixlane.metadataregistry.web.repository.ItemRepository;
import io.github.matrixlane.metadataregistry.web.service.ItemAdministrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InferencePromptBuilderTest {

    @Mock private ItemRepository itemRepository;
    @Mock private ItemAdministrationService itemAdministrationService;
    @Mock private ContextRepository contextRepository;

    @InjectMocks
    private InferencePromptBuilder promptBuilder;

    private final UUID itemId = UUID.randomUUID();

    private DataElement dataElement;

    @BeforeEach
    void setUp() {
        dataElement = new DataElement();
        dataElement.setId(itemId);
    }

    @Test
    void build_knownMetadataIncludedInPrompt() {
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(dataElement));
        when(itemAdministrationService.buildItemMeta(any())).thenReturn(meta("患者标识", "MDR:DE0001@1.0"));
        when(itemAdministrationService.mapDesignations(any())).thenReturn(List.of(designationLine()));
        when(itemAdministrationService.mapDefinitions(any())).thenReturn(List.of());
        dataElement.setDataElementConcept(decWithName("患者"));
        dataElement.setValueDomain(vdWithName("标识符类型"));
        dataElement.getSlots().add(new Slot("业务域", "临床", null, dataElement));

        String prompt = promptBuilder.build(itemId, List.of("definition"), "zh-CN", null, 3);

        assertThat(prompt).contains("条目类型：DataElement");
        assertThat(prompt).contains("首选指称：患者标识");
        assertThat(prompt).contains("标识：MDR:DE0001@1.0");
        assertThat(prompt).contains("患者标识符（语种: zh-CN；来源文档: 数据元字典；首选；上下文: 临床文档（首选））");
        assertThat(prompt).contains("数据元概念（DEC）首选指称：患者");
        assertThat(prompt).contains("值域（VD）首选指称：标识符类型");
        assertThat(prompt).contains("扩展槽（slot）：");
        assertThat(prompt).contains("业务域");
    }

    @Test
    void build_emptySectionsAreOmitted() {
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(dataElement));
        when(itemAdministrationService.buildItemMeta(any())).thenReturn(meta(null, null));
        when(itemAdministrationService.mapDesignations(any())).thenReturn(List.of());
        when(itemAdministrationService.mapDefinitions(any())).thenReturn(List.of());

        String prompt = promptBuilder.build(itemId, List.of("designation"), "en-US", null, 2);

        // 无指称/定义/slot/DEC/VD/注册信息时不应出现对应段落标题
        assertThat(prompt).doesNotContain("指称（designation）：");
        assertThat(prompt).doesNotContain("定义（definition）：");
        assertThat(prompt).doesNotContain("扩展槽（slot）：");
        assertThat(prompt).doesNotContain("数据元概念（DEC）");
        assertThat(prompt).doesNotContain("值域（VD）");
        assertThat(prompt).doesNotContain("注册机构");
        // 任务与契约段落仍在
        assertThat(prompt).contains("推断「指称」");
        assertThat(prompt).contains("语种：en-US");
        assertThat(prompt).contains("至多给出 2 个候选");
    }

    @Test
    void build_definitionsIncludedWithSourceAndLanguage() {
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(dataElement));
        when(itemAdministrationService.buildItemMeta(any())).thenReturn(meta(null, null));
        when(itemAdministrationService.mapDesignations(any())).thenReturn(List.of());
        when(itemAdministrationService.mapDefinitions(any())).thenReturn(List.of(
                new TextLineResponse(UUID.randomUUID(), null, "zh-CN", "用于唯一标识患者的代码。",
                        UUID.randomUUID(), "术语表", List.of(), Acceptability.PREFERRED)));

        String prompt = promptBuilder.build(itemId, List.of("definition"), "zh-CN", null, 3);

        assertThat(prompt).contains("定义（definition）：");
        assertThat(prompt).contains("用于唯一标识患者的代码。（语种: zh-CN；来源文档: 术语表；首选）");
    }

    @Test
    void build_longTextIsTruncatedTo2000Chars() {
        String longDefinition = "长".repeat(3000);
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(dataElement));
        when(itemAdministrationService.buildItemMeta(any())).thenReturn(meta(null, null));
        when(itemAdministrationService.mapDesignations(any())).thenReturn(List.of(
                new TextLineResponse(UUID.randomUUID(), null, "zh-CN", longDefinition,
                        null, null, List.of(), null)));
        when(itemAdministrationService.mapDefinitions(any())).thenReturn(List.of());

        String prompt = promptBuilder.build(itemId, List.of("definition"), "zh-CN", null, 3);

        int marker = prompt.indexOf("（语种:");
        int lineStart = prompt.lastIndexOf("- ", marker);
        String textPart = prompt.substring(lineStart + 2, marker);
        assertThat(textPart).endsWith("…（已截断）");
        assertThat(textPart.length()).isEqualTo(2000 + "…（已截断）".length());
    }

    @Test
    void build_embedsIso11179RulesAndOutputContract() {
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(dataElement));
        when(itemAdministrationService.buildItemMeta(any())).thenReturn(meta(null, null));
        when(itemAdministrationService.mapDesignations(any())).thenReturn(List.of());
        when(itemAdministrationService.mapDefinitions(any())).thenReturn(List.of());

        String prompt = promptBuilder.build(itemId, List.of("definition", "designation"), "zh-CN", null, 3);

        assertThat(prompt).contains("ISO/IEC 11179-4");
        assertThat(prompt).contains("属 + 种差");
        assertThat(prompt).contains("不得循环定义");
        assertThat(prompt).contains("ISO/IEC 11179-5");
        assertThat(prompt).contains("只输出一个 JSON 对象");
        assertThat(prompt).contains("\"candidates\"");
        assertThat(prompt).contains("\"field\":\"definition\"");
        assertThat(prompt).contains("推断「定义」");
        assertThat(prompt).contains("推断「指称」");
    }

    @Test
    void build_withContext_appendsContextToDesignationTask() {
        UUID contextId = UUID.randomUUID();
        Context context = new Context();
        context.setId(contextId);
        Designation designation = new Designation();
        designation.setSign(Sign.of("临床文档"));
        context.getDesignations().add(designation);
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(dataElement));
        when(itemAdministrationService.buildItemMeta(any())).thenReturn(meta(null, null));
        when(itemAdministrationService.mapDesignations(any())).thenReturn(List.of());
        when(itemAdministrationService.mapDefinitions(any())).thenReturn(List.of());
        when(contextRepository.findById(contextId)).thenReturn(Optional.of(context));

        String prompt = promptBuilder.build(itemId, List.of("designation"), "zh-CN", contextId, 3);

        assertThat(prompt).contains("适用上下文：临床文档");
    }

    @Test
    void build_missingItem_throwsNotFound() {
        when(itemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> promptBuilder.build(itemId, List.of("definition"), "zh-CN", null, 3))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private ItemMetaResponse meta(String preferredDesignation, String scopedIdentifier) {
        return new ItemMetaResponse(
                itemId,
                preferredDesignation,
                null,
                scopedIdentifier,
                null,
                null,
                null,
                null,
                RegistrationStatus.RECORDED,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private TextLineResponse designationLine() {
        return new TextLineResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "zh-CN",
                "患者标识符",
                UUID.randomUUID(),
                "数据元字典",
                List.of(new TextLineContextResponse(UUID.randomUUID(), "临床文档", "PREFERRED")),
                Acceptability.PREFERRED);
    }

    private DataElementConcept decWithName(String name) {
        DataElementConcept dec = new DataElementConcept();
        Designation d = new Designation();
        d.setSign(Sign.of(name));
        d.setLanguage(language("zh-CN"));
        dec.getDesignations().add(d);
        return dec;
    }

    private DescribedValueDomain vdWithName(String name) {
        DescribedValueDomain vd = new DescribedValueDomain();
        Designation d = new Designation();
        d.setSign(Sign.of(name));
        d.setLanguage(language("zh-CN"));
        vd.getDesignations().add(d);
        return vd;
    }

    private LanguageIdentification language(String code) {
        LanguageIdentification lang = new LanguageIdentification();
        lang.setLanguageIdentifier(code);
        return lang;
    }
}
