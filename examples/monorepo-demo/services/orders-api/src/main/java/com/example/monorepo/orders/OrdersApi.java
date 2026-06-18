package com.example.monorepo.orders;

import io.github.younesic.backstage.annotations.BackstageComponent;
import io.github.younesic.backstage.annotations.BackstageResource;
import io.github.younesic.backstage.annotations.Lifecycle;

/**
 * Orders service Component. Demonstrates relations: it <em>consumes</em> the payments API (referenced,
 * never created) and <em>owns</em> a dedicated {@code orders-db} Resource (emitted + wired via
 * dependsOn, owner/system inherited).
 */
@BackstageComponent(
        owner = "team-platform",
        lifecycle = Lifecycle.PRODUCTION,
        system = "checkout",
        description = "Handles orders.",
        tags = {"java"},
        consumesApis = {"api:default/payments"})
@BackstageResource(name = "orders-db", type = "database")
public final class OrdersApi {

    private OrdersApi() {
    }
}
