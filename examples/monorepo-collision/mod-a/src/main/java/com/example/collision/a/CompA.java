package com.example.collision.a;

import io.github.younesic.backstage.annotations.BackstageComponent;
import io.github.younesic.backstage.annotations.Lifecycle;

/** Deliberately collides with mod-b: both force {@code name="dup"}. */
@BackstageComponent(name = "dup", owner = "team-x", lifecycle = Lifecycle.EXPERIMENTAL)
public final class CompA {

    private CompA() {
    }
}
