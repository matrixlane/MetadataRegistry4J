 package io.github.matrixlane.metadataregistry.web.service;
 
 import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
 import io.github.matrixlane.metadataregistry.commonfacilities.registration.DegreeOfConformance;
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
         existing.setName("Old");
         existing.setCharacterRepertoire("ASCII");
         when(repository.findById(id)).thenReturn(Optional.of(existing));
         when(repository.save(any(RegistrySpecification.class))).thenAnswer(inv -> inv.getArgument(0));
 
         RegistrySpecificationResponse response = service.update(
                 id, request("New", "Unicode UTF-8"));
 
         assertThat(response.name()).isEqualTo("New");
         assertThat(response.characterRepertoire()).isEqualTo("Unicode UTF-8");
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
