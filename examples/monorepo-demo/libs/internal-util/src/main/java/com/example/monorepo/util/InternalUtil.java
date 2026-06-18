package com.example.monorepo.util;

/** Plain library with NO {@code @BackstageComponent} — intentionally not catalogued. */
public final class InternalUtil {

    private InternalUtil() {
    }

    public static String noop() {
        return "noop";
    }
}
