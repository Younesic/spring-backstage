package com.example.collision.b;

import io.github.younesic.backstage.annotations.BackstageComponent;
import io.github.younesic.backstage.annotations.Lifecycle;

/** Deliberately collides with mod-a: both force {@code name="dup"}. */
@BackstageComponent(name = "dup", owner = "team-x", lifecycle = Lifecycle.EXPERIMENTAL)
public final class CompB {

    private CompB() {
    }
}
