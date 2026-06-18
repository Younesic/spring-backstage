package com.example.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.github.younesic.backstage.annotations.BackstageComponent;
import io.github.younesic.backstage.annotations.Lifecycle;

/**
 * Generated from the Backstage golden-path template. The owner/system/lifecycle below were filled
 * from the scaffolder form; everything else (name, project-slug, …) is derived at build time.
 */
@SpringBootApplication
@BackstageComponent(
        owner = "${{ values.owner }}",
        lifecycle = Lifecycle.${{ values.lifecycle | upper }},
        system = "${{ values.system }}",
        description = "${{ values.description }}")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
