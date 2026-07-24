 package io.github.matrixlane.metadataregistry.web.service;
 
 import io.github.matrixlane.metadataregistry.commonfacilities.registration.ConstraintSet;
 import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
 import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
 import io.github.matrixlane.metadataregistry.web.dto.constraintset.ConstraintSetRequest;
 import io.github.matrixlane.metadataregistry.web.dto.constraintset.ConstraintSetResponse;
 import io.github.matrixlane.metadataregistry.web.repository.ConstraintSetRepository;
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
 class ConstraintSetServiceTest {
 
     @Mock
     private ConstraintSetRepository repository;
 
     @Mock
     private ItemAdministrationService itemAdministrationService;
 
     @InjectMocks
     private ConstraintSetService service;
 
     private final UUID langId = UUID.randomUUID();
 
     private ConstraintSetRequest request(Boolean designated, Boolean defined, Boolean classified) {
         return new ConstraintSetRequest(
                 List.of(new TextLine(langId, "约束集", null, null)),
                 null,
                 null,
                 designated,
                 defined,
                 classified);
     }
 
     @Test
     @DisplayName("创建 ConstraintSet 时应用三个布尔指标")
     void create_shouldApplyIndicatorsAndSave() {
         when(repository.save(any(ConstraintSet.class))).thenAnswer(inv -> {
             ConstraintSet cs = inv.getArgument(0);
             cs.setId(UUID.randomUUID());
             return cs;
         });
 
         ConstraintSetResponse response = service.create(request(false, false, false));
 
         assertThat(response.itemShallBeDesignatedIndicator()).isFalse();
         assertThat(response.itemShallBeDefinedIndicator()).isFalse();
         assertThat(response.itemShallBeClassifiedIndicator()).isFalse();
         verify(repository).save(any(ConstraintSet.class));
     }
 
     @Test
     @DisplayName("未指定指标时默认值为 true")
     void create_noIndicators_shouldDefaultToTrue() {
         when(repository.save(any(ConstraintSet.class))).thenAnswer(inv -> {
             ConstraintSet cs = inv.getArgument(0);
             cs.setId(UUID.randomUUID());
             return cs;
         });
 
         ConstraintSetResponse response = service.create(request(null, null, null));
 
         assertThat(response.itemShallBeDesignatedIndicator()).isTrue();
         assertThat(response.itemShallBeDefinedIndicator()).isTrue();
         assertThat(response.itemShallBeClassifiedIndicator()).isTrue();
     }
 
     @Test
     @DisplayName("更新 ConstraintSet 时修改指标")
     void update_shouldChangeIndicators() {
         UUID id = UUID.randomUUID();
         ConstraintSet existing = new ConstraintSet();
         existing.setId(id);
         existing.setItemShallBeDesignatedIndicator(true);
         existing.setItemShallBeDefinedIndicator(true);
         existing.setItemShallBeClassifiedIndicator(true);
         when(repository.findById(id)).thenReturn(Optional.of(existing));
         when(repository.save(any(ConstraintSet.class))).thenAnswer(inv -> inv.getArgument(0));
 
         ConstraintSetResponse response = service.update(id, request(false, true, false));
 
         assertThat(response.itemShallBeDesignatedIndicator()).isFalse();
         assertThat(response.itemShallBeDefinedIndicator()).isTrue();
         assertThat(response.itemShallBeClassifiedIndicator()).isFalse();
     }
 
     @Test
     @DisplayName("删除不存在的 ConstraintSet 应抛出 ResourceNotFoundException")
     void delete_notExists_shouldThrow() {
         UUID id = UUID.randomUUID();
         when(repository.existsById(id)).thenReturn(false);
 
         assertThatThrownBy(() -> service.delete(id))
                 .isInstanceOf(ResourceNotFoundException.class)
                 .hasMessageContaining("ConstraintSet");
     }
 
     @Test
     @DisplayName("删除存在的 ConstraintSet 应调用 repository.deleteById")
     void delete_exists_shouldDelete() {
         UUID id = UUID.randomUUID();
         when(repository.existsById(id)).thenReturn(true);
 
         service.delete(id);
 
         verify(repository).deleteById(id);
     }
 }
