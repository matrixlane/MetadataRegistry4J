package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core;

import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.DefinitionContext;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.DesignationContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContextTest {

    @Test
    void shouldInitializeCollections() {
        Context ctx = new Context();
        assertThat(ctx.getDesignationContexts()).isEmpty();
        assertThat(ctx.getDefinitionContexts()).isEmpty();
    }

    @Test
    void shouldAddDesignationContext() {
        Context ctx = new Context();
        DesignationContext dc = new DesignationContext();
        ctx.getDesignationContexts().add(dc);
        assertThat(ctx.getDesignationContexts()).hasSize(1);
    }

    @Test
    void shouldRemoveDesignationContext() {
        Context ctx = new Context();
        DesignationContext dc = new DesignationContext();
        ctx.getDesignationContexts().add(dc);
        ctx.getDesignationContexts().remove(dc);
        assertThat(ctx.getDesignationContexts()).isEmpty();
    }

    @Test
    void shouldAddMultipleDesignationContexts() {
        Context ctx = new Context();
        ctx.getDesignationContexts().add(new DesignationContext());
        ctx.getDesignationContexts().add(new DesignationContext());
        assertThat(ctx.getDesignationContexts()).hasSize(2);
    }

    @Test
    void shouldAddDefinitionContext() {
        Context ctx = new Context();
        DefinitionContext dc = new DefinitionContext();
        ctx.getDefinitionContexts().add(dc);
        assertThat(ctx.getDefinitionContexts()).hasSize(1);
    }

    @Test
    void shouldRemoveDefinitionContext() {
        Context ctx = new Context();
        DefinitionContext dc = new DefinitionContext();
        ctx.getDefinitionContexts().add(dc);
        ctx.getDefinitionContexts().remove(dc);
        assertThat(ctx.getDefinitionContexts()).isEmpty();
    }

    @Test
    void shouldAddMultipleDefinitionContexts() {
        Context ctx = new Context();
        ctx.getDefinitionContexts().add(new DefinitionContext());
        ctx.getDefinitionContexts().add(new DefinitionContext());
        assertThat(ctx.getDefinitionContexts()).hasSize(2);
    }

    @Test
    void shouldInheritItemBehaviour() {
        Context ctx = new Context();
        assertThat(ctx.getSlots()).isEmpty();
        assertThat(ctx.getDesignations()).isEmpty();
        assertThat(ctx.getDefinitions()).isEmpty();
    }

    @Test
    void shouldAddSlotViaInheritedCollection() {
        Context ctx = new Context();
        Slot slot = new Slot("key", "value", "xsd:string", ctx);
        ctx.getSlots().add(slot);
        assertThat(ctx.getSlots()).hasSize(1);
        assertThat(ctx.getSlots().get(0).getItem()).isEqualTo(ctx);
    }

    @Test
    void shouldSetAndGetId() {
        Context ctx = new Context();
        java.util.UUID id = java.util.UUID.randomUUID();
        ctx.setId(id);
        assertThat(ctx.getId()).isEqualTo(id);
    }

    @Test
    void shouldBeEqualWhenSameId() {
        Context ctx1 = new Context();
        Context ctx2 = new Context();
        java.util.UUID sharedId = java.util.UUID.randomUUID();
        ctx1.setId(sharedId);
        ctx2.setId(sharedId);
        assertThat(ctx1).isEqualTo(ctx2);
        assertThat(ctx1.hashCode()).isEqualTo(ctx2.hashCode());
    }

    @Test
    void shouldNotBeEqualWhenDifferentIds() {
        Context ctx1 = new Context();
        Context ctx2 = new Context();
        ctx1.setId(java.util.UUID.randomUUID());
        ctx2.setId(java.util.UUID.randomUUID());
        assertThat(ctx1).isNotEqualTo(ctx2);
    }

    @Test
    void shouldNotBeEqualToNullId() {
        Context ctx1 = new Context();
        Context ctx2 = new Context();
        ctx1.setId(java.util.UUID.randomUUID());
        // ctx2 has null id
        assertThat(ctx1).isNotEqualTo(ctx2);
    }

    @Test
    void shouldSupportBothContextTypesSimultaneously() {
        Context ctx = new Context();
        ctx.getDesignationContexts().add(new DesignationContext());
        ctx.getDefinitionContexts().add(new DefinitionContext());
        assertThat(ctx.getDesignationContexts()).hasSize(1);
        assertThat(ctx.getDefinitionContexts()).hasSize(1);
    }
}
