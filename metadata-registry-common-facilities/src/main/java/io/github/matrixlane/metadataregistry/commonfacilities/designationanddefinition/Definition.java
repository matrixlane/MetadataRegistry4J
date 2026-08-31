package io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.ReferenceDocument;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Entity;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import jakarta.persistence.Embedded;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "definition", indexes = {
        @jakarta.persistence.Index(name = "idx_definition_item_id", columnList = "item_id"),
        @jakarta.persistence.Index(name = "idx_definition_language_id", columnList = "language_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Definition extends Entity {

    @Embedded
    private Text text;

    @ManyToOne
    @JoinColumn(name = "language_id")
    private LanguageIdentification language;

    @ManyToOne
    @JoinColumn(name = "source_id")
    private ReferenceDocument source;

    // 与 Designation.item 同理：LAZY 避免 JOINED 继承层级根的全子类表多态连接。
    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private Item item;

    @ManyToMany
    @JoinTable(
            name = "definition_namespace",
            joinColumns = @JoinColumn(name = "definition_id"),
            inverseJoinColumns = @JoinColumn(name = "namespace_id")
    )
    private List<Namespace> namespaces = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "definition_context",
            joinColumns = @JoinColumn(name = "definition_id"),
            inverseJoinColumns = @JoinColumn(name = "context_id")
    )
    private List<Context> contexts = new ArrayList<>();

    @OneToMany(mappedBy = "definition")
    private List<DefinitionContext> definitionContexts = new ArrayList<>();

    @jakarta.persistence.Column(name = "acceptability")
    private Acceptability acceptability;

    @OneToMany(mappedBy = "definition")
    private List<DesignationDefinitionPairing> designationDefinitionPairings = new ArrayList<>();

    @Override
    public String toString() {
        return text == null ? null : text.toString();
    }
}
