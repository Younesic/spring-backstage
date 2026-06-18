package io.github.younesic.backstage.core.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Backstage Resource {@code spec}. {@code type} and {@code owner} are required; {@code system} is
 * optional. Backstage Resources have no {@code lifecycle} field, so none is emitted.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonPropertyOrder({"type", "owner", "system", "dependsOn"})
public class ResourceSpec {

    public String type;
    public String owner;
    public String system;
    public List<String> dependsOn;
}
