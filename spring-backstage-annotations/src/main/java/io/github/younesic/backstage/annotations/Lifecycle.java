package io.github.younesic.backstage.annotations;

/**
 * Backstage {@code spec.lifecycle} value. Maps to the lowercase well-known values expected by the
 * Backstage software catalog ({@code experimental}, {@code production}, {@code deprecated}).
 *
 * <p>Lifecycle is a governance concept absent from the code itself, so it must be declared
 * explicitly on {@link BackstageComponent#lifecycle()} (the build fails fast if it is missing).
 */
public enum Lifecycle {
    EXPERIMENTAL,
    PRODUCTION,
    DEPRECATED;

    /** The Backstage on-wire value, e.g. {@code PRODUCTION -> "production"}. */
    public String toBackstageValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
