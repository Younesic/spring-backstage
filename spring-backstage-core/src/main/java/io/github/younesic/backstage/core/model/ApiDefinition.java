package io.github.younesic.backstage.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A Backstage {@code $text} placeholder used for {@code spec.definition}, e.g.
 * <pre>
 * definition:
 *   $text: ./openapi.yaml
 * </pre>
 * Backstage resolves the relative path against the descriptor's location when served via a git
 * integration (UrlReader).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ApiDefinition {

    @JsonProperty("$text")
    public String text;

    public ApiDefinition() {
    }

    private ApiDefinition(String text) {
        this.text = text;
    }

    /** Build a {@code $text} reference (e.g. {@code ./openapi.yaml}). */
    public static ApiDefinition textRef(String ref) {
        return new ApiDefinition(ref);
    }
}
