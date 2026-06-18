package io.github.younesic.backstage.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** A Backstage {@code kind: Component} descriptor. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonPropertyOrder({"apiVersion", "kind", "metadata", "spec"})
public class ComponentEntity implements CatalogEntity {

    public String apiVersion = Backstage.API_VERSION;
    public String kind = Backstage.KIND_COMPONENT;
    public ObjectMeta metadata;
    public ComponentSpec spec;

    @Override
    public String kindName() {
        return kind;
    }

    @Override
    public String entityName() {
        return metadata != null ? metadata.name : null;
    }
}
