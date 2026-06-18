package io.github.younesic.backstage.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a Backstage {@code Resource} <strong>owned by this service</strong> (its own database,
 * queue or bucket provisioned together with it). Repeatable on the {@code @BackstageComponent} class.
 *
 * <p>When present, the generator emits a {@code kind: Resource} entity and wires
 * {@code spec.dependsOn: [resource:default/<name>]} on the Component. {@code owner} and {@code system}
 * are inherited from the Component unless overridden here.
 *
 * <p><strong>Dedicated resources only.</strong> Declare ONLY resources this service owns. Never use
 * this for shared infrastructure (a shared Kafka, a team database, an org bucket) — that would create
 * duplicate/conflicting entities across services. For shared things, reference them with
 * {@code @BackstageComponent(dependsOn = ...)} instead. There is deliberately no inference from the
 * datasource/Spring config: ownership must be declared explicitly.
 *
 * <pre>{@code
 * @BackstageComponent(owner = "team-payments", lifecycle = Lifecycle.PRODUCTION, system = "checkout")
 * @BackstageResource(name = "orders-db", type = "database")
 * public class OrdersApplication { ... }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
@Repeatable(BackstageResources.class)
public @interface BackstageResource {

    /** Sentinel meaning "inherit from the Component". */
    String INHERIT = "";

    /** Required. {@code metadata.name} of the Resource. Scope it to the service (e.g. {@code orders-db}). */
    String name();

    /** Required. Backstage Resource {@code spec.type} (e.g. {@code database}, {@code queue}, {@code bucket}). */
    String type();

    /** Optional. {@code spec.owner}; inherited from the Component when blank. */
    String owner() default INHERIT;

    /** Optional. {@code spec.system}; inherited from the Component when blank. */
    String system() default INHERIT;

    /** Optional. {@code metadata.description}. */
    String description() default INHERIT;
}
