package io.github.younesic.backstage.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that the annotated application is a Backstage {@code Component} (and, when springdoc is
 * present, an {@code API}). Placed on the {@code @SpringBootApplication} class (or a dedicated
 * config class).
 *
 * <p>Values set here win over values derived from the project/environment. The build-time Maven
 * plugin reads this annotation by scanning compiled bytecode, so it is retained at
 * {@link RetentionPolicy#CLASS}: visible to the bytecode scanner, but kept out of the application's
 * runtime model (no reflective lookup, no Spring side effects).
 *
 * <p>{@link #owner()} and {@link #lifecycle()} are <strong>required</strong> governance fields
 * absent from the code; the build fails fast with an actionable message if either is missing.
 *
 * <pre>{@code
 * @SpringBootApplication
 * @BackstageComponent(
 *     owner = "team-payments",
 *     lifecycle = Lifecycle.PRODUCTION,
 *     system = "checkout",
 *     tags = {"java", "spring-boot"})
 * public class OrdersServiceApplication { ... }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface BackstageComponent {

    /** Sentinel meaning "derive from {@code spring.application.name}, falling back to the Maven artifactId". */
    String DERIVED = "";

    /** Optional. Overrides the derived {@code metadata.name}. When blank, the name is derived and normalized. */
    String name() default DERIVED;

    /** Backstage {@code spec.type}. Defaults to {@code service}. */
    String type() default "service";

    /**
     * Required. Backstage {@code spec.owner}. A bare value such as {@code team-payments} is normalized to
     * the fully-qualified reference {@code group:default/team-payments}; a value already containing a
     * kind (e.g. {@code user:default/jdoe}) is kept verbatim.
     */
    String owner();

    /** Required. Backstage {@code spec.lifecycle}. */
    Lifecycle lifecycle();

    /** Optional. Backstage {@code spec.system} (entity reference to a System declared elsewhere). */
    String system() default DERIVED;

    /** Optional. {@code metadata.description}. */
    String description() default DERIVED;

    /** Optional. {@code metadata.tags}; each tag is normalized to the Backstage tag charset. */
    String[] tags() default {};

    /**
     * Optional. Backstage {@code spec.dependsOn}, as entity references
     * (e.g. {@code resource:default/orders-db}, {@code component:default/inventory}). Manual override
     * only — no automatic inference. References existing entities; never creates them.
     */
    String[] dependsOn() default {};

    /**
     * Optional. Backstage {@code spec.consumesApis} — APIs this service calls, as references
     * (e.g. {@code api:default/payments}). The consumed APIs are owned by the services that expose
     * them: this is a <strong>reference only</strong>, never a generated {@code API} entity. A bare
     * value such as {@code payments} is normalized to {@code api:default/payments}.
     */
    String[] consumesApis() default {};

    /**
     * Optional. Extra Backstage {@code spec.providesApis} entries for services that expose an API
     * without springdoc, or expose several. Merged (de-duplicated) with the API derived from springdoc.
     */
    String[] providesApis() default {};

    /**
     * When {@code true} (default) and springdoc-openapi is detected on the build classpath, an
     * {@code API} entity is generated and linked from the Component via {@code spec.providesApis}.
     * Set to {@code false} to suppress the API entity regardless of springdoc.
     */
    boolean emitApi() default true;

    /**
     * Optional. Static overrides for tooling/integration annotations, as {@code "key=value"} pairs
     * (e.g. {@code {"argocd/app-name=orders-prod", "sonarqube.org/project-key=acme_orders"}}).
     *
     * <p>These have the <strong>highest precedence</strong> in the tooling registry: an entry here wins
     * over any value derived from the CI environment or from an org convention. Use it for the rare
     * service that deviates from the conventions, or as the opt-in escape hatch to pin a value the
     * environment cannot supply at build time. Entries with a malformed key or no {@code =} are ignored
     * with a build warning; the value (right of the first {@code =}) is emitted verbatim, quoted as a
     * string. This never affects {@code owner}/{@code lifecycle}, which remain required governance fields.
     */
    String[] annotations() default {};
}
