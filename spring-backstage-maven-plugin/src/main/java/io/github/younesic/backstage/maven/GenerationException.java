package io.github.younesic.backstage.maven;

/** Signals an actionable generation failure (bad annotation values, multiple components, …). */
class GenerationException extends Exception {

    GenerationException(String message) {
        super(message);
    }

    GenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
