package com.example.monorepo.shared;

import io.github.younesic.backstage.annotations.BackstageComponent;
import io.github.younesic.backstage.annotations.Lifecycle;

/** A shared library catalogued as a {@code Component} of {@code spec.type: library}. */
@BackstageComponent(
        type = "library",
        owner = "team-platform",
        lifecycle = Lifecycle.PRODUCTION,
        description = "Shared domain model used by the services.")
public final class SharedModel {

    private SharedModel() {
    }
}
