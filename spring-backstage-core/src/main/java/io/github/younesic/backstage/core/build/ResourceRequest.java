package io.github.younesic.backstage.core.build;

/**
 * A service-owned Resource declared via {@code @BackstageResource}. {@code owner}/{@code system} are
 * {@code null} when they should be inherited from the Component.
 */
public final class ResourceRequest {

    public final String name;
    public final String type;
    public final String owner;
    public final String system;
    public final String description;

    public ResourceRequest(String name, String type, String owner, String system, String description) {
        this.name = name;
        this.type = type;
        this.owner = owner;
        this.system = system;
        this.description = description;
    }
}
