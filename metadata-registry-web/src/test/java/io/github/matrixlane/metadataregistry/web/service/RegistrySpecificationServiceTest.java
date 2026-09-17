 package io.github.matrixlane.metadataregistry.web.service;
 
 import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
 import io.github.matrixlane.metadataregistry.commonfacilities.registration.DegreeOfConformance;
 import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrySpecification;
 import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
 import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
 import io.github.matrixlane.metadataregistry.web.dto.registries.RegistrySpecificationRequest;
 import io.github.matrixlane.metadataregistry.web.dto.registries.RegistrySpecificationResponse;
 import io.github.matrixlane.metadataregistry.web.repository.ContextRepository;
 import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
 import io.github.matrixlane.metadataregistry.web.repository.RegistrySpecificationRepository;
 import org.junit.jupiter.api.DisplayName;
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
 import static org.mockito.Mockito.verify;
 import static org.mockito.Mockito.when;
 
 @ExtendWith(MockitoExtension.class)
 class RegistrySpecificationServiceTest {
 
     @Mock
     private RegistrySpecificationRepository repository;
 
     @Mock
     private LanguageIdentificationRepository languageRepository;
 
     @Mock
     private ContextRepository contextRepository;
 
     @Mock
     private ItemAdministrationService itemAdministrationService;
 
     @InjectMocks
     private RegistrySpecificationService service;
 
     private final UUID langId = UUID.randomUUID();
 
     private RegistrySpecificationRequest request(String name, String characterRepertoire) {
         return new RegistrySpecificationRequest(
                 name,
                 null,
                 null,
                 null,
                 null,
                 characterRepertoire,
                 null,
                 null,
                 null,
                 "ISO/IEC 11179-3:2023",
                 DegreeOfConformance.CONFORMING,
                 null,
                 null,
                 null,
                 null,
                 null,
                 List.of(new TextLine(langId, "注册规范", null, null)),
                 null,
                 null);
     }
 
     @Test
     @DisplayName("创建 RegistrySpecification 时保存所有属性")
     void create_shouldApplyAttributesAndSave() {
         when(repository.save(any(RegistrySpecification.class))).thenAnswer(inv -> {
             RegistrySpecification rs = inv.getArgument(0);
             rs.setId(UUID.randomUUID());
             return rs;
         });
 
         RegistrySpecificationResponse response = service.create(
                 request("MetadataRegistry", "Unicode UTF-8"));
 
         assertThat(response.name()).isEqualTo("MetadataRegistry");
         assertThat(response.characterRepertoire()).isEqualTo("Unicode UTF-8");
         assertThat(response.standard()).isEqualTo("ISO/IEC 11179-3:2023");
         assertThat(response.degreeOfConformance()).isEqualTo(DegreeOfConformance.CONFORMING);
         verify(repository).save(any(RegistrySpecification.class));
     }
 
     @Test
     @DisplayName("更新 RegistrySpecification 时修改名称与字符集")
     void update_shouldChangeAttributes() {
         UUID id = UUID.randomUUID();
         RegistrySpecification existing = new RegistrySpecification();
         existing.setId(id);
         existing.setName(Sign.of("Old"));
         existing.setCharacterRepertoire("ASCII");
         when(repository.findById(id)).thenReturn(Optional.of(existing));
         when(repository.save(any(RegistrySpecification.class))).thenAnswer(inv -> inv.getArgument(0));
 
         RegistrySpecificationResponse response = service.update(
                 id, request("New", "Unicode UTF-8"));
 
         assertThat(response.name()).isEqualTo("New");
         assertThat(response.characterRepertoire()).isEqualTo("Unicode UTF-8");
     }
 
     private RegistrySpecificationRequest requestWithTimeZone(String timeZone) {
        return new RegistrySpecificationRequest(
                "MetadataRegistry",
                null,
                null,
                null,
                null,
                "Unicode UTF-8",
                null,
                null,
                null,
                "ISO/IEC 11179-3:2023",
                DegreeOfConformance.CONFORMING,
                null,
                null,
                null,
                null,
                timeZone,
                List.of(new TextLine(langId, "注册规范", null, null)),
                null,
                null);
    }

    @Test
    @DisplayName("创建时非法 default_time_zone 格式应抛出业务校验异常（400）")
    void create_invalidTimeZone_shouldThrowBusinessValidation() {
        assertThatThrownBy(() -> service.create(requestWithTimeZone("800")))
                .isInstanceOf(io.github.matrixlane.metadataregistry.web.common.BusinessException.class)
                .hasMessageContaining("default_time_zone");
        assertThatThrownBy(() -> service.create(requestWithTimeZone("+08:00")))
                .isInstanceOf(io.github.matrixlane.metadataregistry.web.common.BusinessException.class);
    }

    @Test
    @DisplayName("创建时合法 default_time_zone 应保存并回读")
    void create_validTimeZone_shouldSave() {
        when(repository.save(any(RegistrySpecification.class))).thenAnswer(inv -> {
            RegistrySpecification rs = inv.getArgument(0);
            rs.setId(UUID.randomUUID());
            return rs;
        });

        RegistrySpecificationResponse response = service.create(requestWithTimeZone("-0230"));

        assertThat(response.defaultTimeZone()).isEqualTo("-0230");
    }

    private LanguageIdentification lang(UUID id, String identifier) {
        LanguageIdentification l = new LanguageIdentification();
        l.setId(id);
        l.setLanguageIdentifier(identifier);
        return l;
    }

    @Test
    @DisplayName("createDefault 携带全量属性时按所填值创建，缺省时按系统默认兜底")
    void createDefault_fullAttributes_andDefaults() {
        UUID zhLang = UUID.randomUUID();
        UUID enLang = UUID.randomUUID();
        when(languageRepository.findByLanguageIdentifier("zh-CN")).thenReturn(Optional.of(lang(zhLang, "zh-CN")));
        when(languageRepository.findByLanguageIdentifier("en-US")).thenReturn(Optional.of(lang(enLang, "en-US")));
        when(languageRepository.findById(zhLang)).thenReturn(Optional.of(lang(zhLang, "zh-CN")));
        when(languageRepository.findById(enLang)).thenReturn(Optional.of(lang(enLang, "en-US")));
        when(repository.save(any(RegistrySpecification.class))).thenAnswer(inv -> {
            RegistrySpecification rs = inv.getArgument(0);
            rs.setId(UUID.randomUUID());
            return rs;
        });

        RegistrySpecificationResponse full = service.createDefault(
                "全属性规范", "zh-CN", "描述",
                "https://example.org/mdr",
                "ISO/IEC 10646", "ISO/IEC 11179-3:2023", DegreeOfConformance.STRICTLY_CONFORMING,
                "level-1", "label-1", "inc", "exc", "-0230",
                List.of("en-US"), List.of("URI"), "rcs-1");

        assertThat(full.name()).isEqualTo("全属性规范");
        assertThat(full.webAddress()).isEqualTo("https://example.org/mdr");
        assertThat(full.characterRepertoire()).isEqualTo("ISO/IEC 10646");
        assertThat(full.standard()).isEqualTo("ISO/IEC 11179-3:2023");
        assertThat(full.degreeOfConformance()).isEqualTo(DegreeOfConformance.STRICTLY_CONFORMING);
        assertThat(full.conformanceLevel()).isEqualTo("level-1");
        assertThat(full.conformanceLabel()).isEqualTo("label-1");
        assertThat(full.defaultTimeZone()).isEqualTo("-0230");
        assertThat(full.alternativeLanguageIds()).containsExactly(enLang);
        assertThat(full.referenceDocumentIdentifierKinds()).containsExactly("URI");
        assertThat(full.representationClassSchemeId()).isEqualTo("rcs-1");

        RegistrySpecificationResponse defaults = service.createDefault(null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null);
        assertThat(defaults.name()).isEqualTo("默认元数据注册表");
        assertThat(defaults.characterRepertoire()).isEqualTo("UTF-8");
        assertThat(defaults.standard()).isEqualTo("ISO/IEC 11179-3:2023");
        assertThat(defaults.defaultTimeZone()).isEqualTo("+0800");
    }

    @DisplayName("找不到 RegistrySpecification 时应抛出 ResourceNotFoundException")
     @Test
     void get_notFound_shouldThrow() {
         UUID id = UUID.randomUUID();
         when(repository.findById(id)).thenReturn(Optional.empty());
 
         assertThatThrownBy(() -> service.get(id))
                 .isInstanceOf(ResourceNotFoundException.class)
                 .hasMessageContaining("RegistrySpecification");
     }
 
     @Test
     @DisplayName("删除不存在的 RegistrySpecification 应抛出 ResourceNotFoundException")
     void delete_notExists_shouldThrow() {
         UUID id = UUID.randomUUID();
         when(repository.existsById(id)).thenReturn(false);
 
         assertThatThrownBy(() -> service.delete(id))
                 .isInstanceOf(ResourceNotFoundException.class)
                 .hasMessageContaining("RegistrySpecification");
     }
 
     @Test
     @DisplayName("删除存在的 RegistrySpecification 应调用 repository.deleteById")
     void delete_exists_shouldDelete() {
         UUID id = UUID.randomUUID();
         when(repository.existsById(id)).thenReturn(true);
 
         service.delete(id);
 
         verify(repository).deleteById(id);
     }
 }
