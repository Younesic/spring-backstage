package io.github.younesic.backstage.core.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** Backstage Component {@code spec}. {@code type}, {@code lifecycle}, {@code owner} are required. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonPropertyOrder({"type", "lifecycle", "owner", "system", "subcomponentOf",
        "providesApis", "consumesApis", "dependsOn"})
public class ComponentSpec {

    public String type;
    public String lifecycle;
    public String owner;
    public String system;
    public String subcomponentOf;
    public List<String> providesApis;
    public List<String> consumesApis;
    public List<String> dependsOn;
}
