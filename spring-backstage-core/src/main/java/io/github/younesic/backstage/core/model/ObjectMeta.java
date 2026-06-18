package io.github.younesic.backstage.core.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Backstage {@code metadata}. {@code annotations}/{@code labels} are {@code Map<String,String>} so
 * values are always strings (Backstage rejects non-string annotation values); use a
 * {@link java.util.LinkedHashMap} to keep a stable, human-friendly key order.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonPropertyOrder({"name", "namespace", "title", "description", "annotations", "labels", "tags", "links"})
public class ObjectMeta {

    public String name;
    public String namespace;
    public String title;
    public String description;
    public Map<String, String> annotations;
    public Map<String, String> labels;
    public List<String> tags;
    public List<Link> links;
}
