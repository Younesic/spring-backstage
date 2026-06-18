package com.example.orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.github.younesic.backstage.annotations.BackstageComponent;
import io.github.younesic.backstage.annotations.Lifecycle;

/**
 * Example service. {@code name} is omitted on purpose so it is derived from
 * {@code spring.application.name} ({@code orders-service}); {@code owner} and {@code lifecycle} are
 * the required governance fields. springdoc on the classpath adds the API entity automatically.
 */
@SpringBootApplication
@BackstageComponent(
        owner = "team-payments",
        lifecycle = Lifecycle.PRODUCTION,
        system = "checkout",
        description = "Handles customer orders for the checkout flow.",
        tags = {"java", "spring-boot"},
        // Tooling annotations (sonarqube/argocd/harbor/dependency-track) are derived from
        // env/convention. This pins one explicitly to show the override precedence.
        annotations = {"argocd/app-name=orders-checkout"})
public class OrdersServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrdersServiceApplication.class, args);
    }
}
