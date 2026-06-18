package io.github.younesic.backstage.maven;

import java.util.List;

import io.github.younesic.backstage.core.build.ResourceRequest;

/** Plain holder for the {@code @BackstageComponent}/{@code @BackstageResource} values, read inside the scan scope. */
final class AnnotatedComponent {
    String className;
    String name;
    String type;
    String owner;
    String lifecycle;
    String system;
    String description;
    String[] tags;
    String[] dependsOn;
    String[] consumesApis;
    String[] providesApis;
    /** Generic {@code "key=value"} tooling-annotation overrides (highest precedence in the registry). */
    String[] annotations;
    List<ResourceRequest> resources;
    boolean emitApi;
}
