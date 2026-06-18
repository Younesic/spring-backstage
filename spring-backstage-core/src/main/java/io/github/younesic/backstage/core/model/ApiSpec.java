package io.github.younesic.backstage.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Backstage API {@code spec}. {@code type}, {@code lifecycle}, {@code owner} and {@code definition}
 * are required by the catalog. {@code definition} is an {@link ApiDefinition} ({@code $text} ref)
 * or, for the inline case, a raw {@link String}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonPropertyOrder({"type", "lifecycle", "owner", "system", "definition"})
public class ApiSpec {

    public String type;
    public String lifecycle;
    public String owner;
    public String system;
    public Object definition;
}
