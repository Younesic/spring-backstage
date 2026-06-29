package io.github.younesic.backstage.core.derive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

/**
 * Infers the <em>type</em> of a service's own backing resources (database, kafka, redis, …) from two
 * stable, build-time signals: the resolved Maven dependency coordinates, and the structural keys of
 * {@code application.yaml}/{@code .properties} (key prefixes + the {@code jdbc:<scheme>:} sub-protocol).
 *
 * <p>It deliberately reads only the <strong>type</strong> — never the connection value (host, port,
 * topic, …), which is runtime config, frequently variabilized ({@code ${...}}) and secret. The caller
 * names each inferred resource by a stable convention ({@code <component>-<type>}); the variabilized
 * value is irrelevant to the catalog graph.
 *
 * <p>Ordered output (registry order) keeps generation deterministic.
 */
public final class ResourceInference {

    private static final YAMLMapper YAML = new YAMLMapper();

    /** {@code jdbc:postgresql://...} → captures {@code postgresql}. */
    private static final Pattern JDBC = Pattern.compile("jdbc:([a-z0-9]+):");

    private ResourceInference() {
    }

    /** Full inference for a module: Maven coordinates + {@code application.*} in {@code resourcesDir}. */
    public static List<String> infer(Set<String> coordinates, Path resourcesDir) {
        LinkedHashSet<String> types = new LinkedHashSet<>(fromCoordinates(coordinates));
        types.addAll(fromConfig(resourcesDir));
        return new ArrayList<>(types);
    }

    // --- coordinates -------------------------------------------------------------------------------

    /** Map a single {@code groupId:artifactId} to a resource type, or {@code null}. Package-visible for tests. */
    static String typeForCoordinate(String coordinate) {
        if (coordinate == null) {
            return null;
        }
        String c = coordinate.trim();
        if (c.startsWith("org.postgresql:")) return "postgresql";
        if (c.startsWith("com.mysql:") || c.equals("mysql:mysql-connector-java")) return "mysql";
        if (c.startsWith("org.mariadb")) return "mariadb";
        if (c.equals("com.microsoft.sqlserver:mssql-jdbc")) return "sqlserver";
        if (c.startsWith("com.oracle.database")) return "oracle";
        if (c.equals("org.springframework.kafka:spring-kafka")
                || c.equals("org.apache.kafka:kafka-clients")) return "kafka";
        if (c.equals("org.springframework.boot:spring-boot-starter-data-redis")
                || c.equals("org.springframework.boot:spring-boot-starter-data-redis-reactive")
                || c.startsWith("io.lettuce:") || c.equals("redis.clients:jedis")) return "redis";
        if (c.equals("org.springframework.boot:spring-boot-starter-amqp")
                || c.equals("com.rabbitmq:amqp-client")) return "rabbitmq";
        if (c.startsWith("org.mongodb:")
                || c.equals("org.springframework.boot:spring-boot-starter-data-mongodb")
                || c.equals("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")) return "mongodb";
        if (c.equals("co.elastic.clients:elasticsearch-java")
                || c.equals("org.springframework.boot:spring-boot-starter-data-elasticsearch")) return "elasticsearch";
        if (c.equals("software.amazon.awssdk:s3")
                || c.equals("io.awspring.cloud:spring-cloud-aws-starter-s3")
                || c.equals("com.amazonaws:aws-java-sdk-s3")) return "s3";
        return null;
    }

    static List<String> fromCoordinates(Set<String> coordinates) {
        LinkedHashSet<String> types = new LinkedHashSet<>();
        if (coordinates != null) {
            for (String c : coordinates) {
                String t = typeForCoordinate(c);
                if (t != null) {
                    types.add(t);
                }
            }
        }
        return new ArrayList<>(types);
    }

    // --- application config ------------------------------------------------------------------------

    /** Read {@code application.{yml,yaml,properties}} and infer types from keys + jdbc sub-protocols. */
    static List<String> fromConfig(Path resourcesDir) {
        if (resourcesDir == null) {
            return List.of();
        }
        Map<String, String> flat = new LinkedHashMap<>();
        readYaml(resourcesDir.resolve("application.yml"), flat);
        readYaml(resourcesDir.resolve("application.yaml"), flat);
        readProperties(resourcesDir.resolve("application.properties"), flat);
        return typesFromConfig(flat.keySet(), flat.values());
    }

    /** Pure: derive types from flattened config keys and their (possibly variabilized) values. */
    static List<String> typesFromConfig(Set<String> keys, Iterable<String> values) {
        LinkedHashSet<String> types = new LinkedHashSet<>();
        // jdbc sub-protocol from any value, e.g. jdbc:postgresql://${DB_HOST} -> postgresql
        for (String v : values) {
            if (v == null) {
                continue;
            }
            Matcher m = JDBC.matcher(v);
            if (m.find()) {
                String scheme = m.group(1);
                String t = jdbcSchemeToType(scheme);
                if (t != null) {
                    types.add(t);
                }
            }
        }
        // structural key prefixes (value-agnostic)
        for (String key : keys) {
            String k = key.toLowerCase();
            if (k.startsWith("spring.kafka")) types.add("kafka");
            else if (k.startsWith("spring.data.redis") || k.startsWith("spring.redis")) types.add("redis");
            else if (k.startsWith("spring.rabbitmq")) types.add("rabbitmq");
            else if (k.startsWith("spring.data.mongodb")) types.add("mongodb");
            else if (k.startsWith("spring.elasticsearch") || k.startsWith("spring.data.elasticsearch")) types.add("elasticsearch");
            else if (k.startsWith("spring.cloud.aws.s3") || k.startsWith("cloud.aws.s3")) types.add("s3");
        }
        return new ArrayList<>(types);
    }

    private static String jdbcSchemeToType(String scheme) {
        switch (scheme) {
            case "postgresql":
                return "postgresql";
            case "mysql":
                return "mysql";
            case "mariadb":
                return "mariadb";
            case "sqlserver":
                return "sqlserver";
            case "oracle":
                return "oracle";
            default:
                return null; // h2/hsqldb/derby = in-memory/embedded → not a catalog resource
        }
    }

    private static void readYaml(Path file, Map<String, String> out) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            JsonNode root = YAML.readTree(file.toFile());
            if (root != null && !root.isMissingNode() && !root.isNull()) {
                flatten("", root, out);
            }
        } catch (IOException ignored) {
            // best-effort
        }
    }

    private static void readProperties(Path file, Map<String, String> out) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (InputStream in = Files.newInputStream(file)) {
            Properties p = new Properties();
            p.load(in);
            for (String name : p.stringPropertyNames()) {
                out.put(name, p.getProperty(name));
            }
        } catch (IOException ignored) {
            // best-effort
        }
    }

    /** Flatten a YAML/JSON tree to dotted keys (nested {@code spring: kafka:} → {@code spring.kafka}). */
    private static void flatten(String prefix, JsonNode node, Map<String, String> out) {
        if (node.isObject()) {
            node.fields().forEachRemaining(e ->
                    flatten(prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey(), e.getValue(), out));
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                flatten(prefix + "[" + i + "]", node.get(i), out);
            }
        } else {
            out.put(prefix, node.asText());
        }
    }
}
