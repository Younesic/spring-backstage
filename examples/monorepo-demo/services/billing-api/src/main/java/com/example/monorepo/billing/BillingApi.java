package com.example.monorepo.billing;

import io.github.younesic.backstage.annotations.BackstageComponent;
import io.github.younesic.backstage.annotations.Lifecycle;

/** Billing service Component. springdoc on the classpath → also gets an API entity. */
@BackstageComponent(
        owner = "team-payments",
        lifecycle = Lifecycle.EXPERIMENTAL,
        system = "checkout",
        description = "Handles billing.")
public final class BillingApi {

    private BillingApi() {
    }
}
