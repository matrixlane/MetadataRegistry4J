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
    void shouldAddDefinitionContext() {
        Context ctx = new Context();
        DefinitionContext dc = new DefinitionContext();
        ctx.getDefinitionContexts().add(dc);
        assertThat(ctx.getDefinitionContexts()).hasSize(1);
    }
}
