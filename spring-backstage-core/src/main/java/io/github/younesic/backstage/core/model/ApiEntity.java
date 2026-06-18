package io.github.younesic.backstage.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** A Backstage {@code kind: API} descriptor. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonPropertyOrder({"apiVersion", "kind", "metadata", "spec"})
public class ApiEntity implements CatalogEntity {

    public String apiVersion = Backstage.API_VERSION;
    public String kind = Backstage.KIND_API;
    public ObjectMeta metadata;
    public ApiSpec spec;

    @Override
    public String kindName() {
        return kind;
    }

    @Override
    public String entityName() {
        return metadata != null ? metadata.name : null;
    }
}
