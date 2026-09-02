package io.github.matrixlane.metadataregistry.web.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ItemSortPageableResolver.remap:虚拟排序键改写与真实字段透传。
 */
class ItemSortPageableResolverTest {

    @Test
    void remap_metaIdentifier_mapsToScopedIdentifierPath() {
        Pageable in = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "meta.identifier"));
        Pageable out = ItemSortPageableResolver.remap(in);
        Sort.Order order = out.getSort().iterator().next();
        assertThat(order.getProperty()).isEqualTo("identifiedItem.scopedIdentifiers.identifier");
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(out.getPageNumber()).isZero();
        assertThat(out.getPageSize()).isEqualTo(20);
    }

    @Test
    void remap_metaLabel_mapsToDesignationValuePath() {
        Pageable in = PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "meta.label"));
        Pageable out = ItemSortPageableResolver.remap(in);
        Sort.Order order = out.getSort().iterator().next();
        assertThat(order.getProperty()).isEqualTo("designations.sign.value");
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void remap_realProperty_passesThrough() {
        Pageable in = PageRequest.of(0, 20, Sort.by("identifier", "name", "createdAt"));
        Pageable out = ItemSortPageableResolver.remap(in);
        assertThat(out.getSort().stream().map(Sort.Order::getProperty))
                .containsExactly("identifier", "name", "createdAt");
    }

    @Test
    void remap_unsorted_returnsSameInstance() {
        Pageable in = PageRequest.of(2, 50);
        assertThat(ItemSortPageableResolver.remap(in)).isSameAs(in);
    }
}
