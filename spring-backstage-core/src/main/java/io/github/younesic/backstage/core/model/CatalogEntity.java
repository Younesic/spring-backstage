package io.github.younesic.backstage.core.model;

/**
 * Marker for a serializable Backstage entity. Implementations are plain POJOs (no polymorphic
 * Jackson typing) so the YAML serializer emits clean documents without {@code !<...>} type tags.
 *
 * <p>The accessors are intentionally <em>not</em> JavaBean getters ({@code getX}/{@code isX}) so
 * Jackson does not serialize them; they exist only for logging and validation.
 */
public interface CatalogEntity {

    /** e.g. {@code "Component"} or {@code "API"} — for logging, never serialized as a duplicate. */
    String kindName();

    /** {@code metadata.name} — for logging/validation, never serialized as a duplicate. */
    String entityName();
}
