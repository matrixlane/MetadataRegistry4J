package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.ext.basicandcore.ContactAccountBinding;
import io.github.matrixlane.metadataregistry.web.repository.ContactAccountBindingRepository;
import io.github.matrixlane.metadataregistry.web.repository.ContactRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrarRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 绑定服务：首个身份自动默认（add-submitter-contact-identity）。 */
@ExtendWith(MockitoExtension.class)
class ContactAccountBindingServiceDefaultTest {

    @Mock private ContactAccountBindingRepository bindingRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private RegistrarRepository registrarRepository;

    @InjectMocks
    private ContactAccountBindingService service;

    private final UUID accountId = UUID.randomUUID();
    private final UUID contactId = UUID.randomUUID();

    @Test
    void bind_firstIdentityBecomesDefault() {
        when(bindingRepository.findByContactId(contactId)).thenReturn(Optional.empty());
        when(bindingRepository.findByAccountId(accountId)).thenReturn(List.of());

        service.bind(contactId, accountId);

        org.mockito.ArgumentCaptor<ContactAccountBinding> captor =
                org.mockito.ArgumentCaptor.forClass(ContactAccountBinding.class);
        verify(bindingRepository).save(captor.capture());
        assertThat(captor.getValue().isDefault()).isTrue();
    }

    @Test
    void bind_secondIdentityNotDefault() {
        ContactAccountBinding existing = new ContactAccountBinding(UUID.randomUUID(), accountId, true);
        when(bindingRepository.findByContactId(contactId)).thenReturn(Optional.empty());
        when(bindingRepository.findByAccountId(accountId)).thenReturn(List.of(existing));

        service.bind(contactId, accountId);

        org.mockito.ArgumentCaptor<ContactAccountBinding> captor =
                org.mockito.ArgumentCaptor.forClass(ContactAccountBinding.class);
        verify(bindingRepository).save(captor.capture());
        assertThat(captor.getValue().isDefault()).isFalse();
    }

    @Test
    void bind_existingBindingKeepsDefaultFlag() {
        ContactAccountBinding existing = new ContactAccountBinding(contactId, accountId, true);
        when(bindingRepository.findByContactId(contactId)).thenReturn(Optional.of(existing));

        service.bind(contactId, accountId);

        verify(bindingRepository).save(existing);
        assertThat(existing.isDefault()).isTrue();
        verify(bindingRepository, never()).findByAccountId(any());
    }
}
