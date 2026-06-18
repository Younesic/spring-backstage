package io.github.younesic.backstage.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** A Backstage {@code metadata.links[]} entry. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonPropertyOrder({"url", "title", "icon", "type"})
public class Link {

    public String url;
    public String title;
    public String icon;
    public String type;

    public Link() {
    }

    public Link(String url, String title) {
        this.url = url;
        this.title = title;
    }
}
