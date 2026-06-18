package io.github.younesic.backstage.core.derive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class NamesTest {

    @Test
    void normalizesToKebabLowercase() {
        assertEquals("orders-service", Names.normalizeName("Orders Service"));
        assertEquals("orders-service", Names.normalizeName("orders_service"));
        assertEquals("orders-service", Names.normalizeName("  orders.service  "));
    }

    @Test
    void stripsLeadingTrailingAndCollapsesSeparators() {
        assertEquals("a-b", Names.normalizeName("--a   b--"));
        assertEquals("abc", Names.normalizeName("@@@abc@@@"));
    }

    @Test
    void truncatesToSixtyThree() {
        assertEquals(63, Names.normalizeName("x".repeat(100)).length());
    }

    @Test
    void throwsWhenNothingValidRemains() {
        assertThrows(IllegalArgumentException.class, () -> Names.normalizeName("***"));
        assertThrows(IllegalArgumentException.class, () -> Names.normalizeName(null));
    }

    @Test
    void derivePrefersSpringNameWhenLiteral() {
        assertEquals("orders", Names.deriveName(Optional.of("orders"), "orders-service-artifact"));
    }

    @Test
    void deriveFallsBackOnUnresolvedPlaceholder() {
        assertEquals("orders-artifact", Names.deriveName(Optional.of("@project.artifactId@"), "orders-artifact"));
    }

    @Test
    void deriveFallsBackOnBlankOrAbsent() {
        assertEquals("artie", Names.deriveName(Optional.of("   "), "artie"));
        assertEquals("artie", Names.deriveName(Optional.empty(), "artie"));
    }

    @Test
    void qualifiesOwnerReferences() {
        assertEquals("group:default/team-payments", Names.qualifyOwner("team-payments"));
        assertEquals("user:default/jdoe", Names.qualifyOwner("user:default/jdoe"));
        assertEquals("group:custom/team-x", Names.qualifyOwner("custom/team-x"));
    }

    @Test
    void normalizesKindOnlyOwnerToDefaultNamespace() {
        assertEquals("group:default/team-x", Names.qualifyOwner("group:team-x"));
    }

    @Test
    void rejectsMalformedOwnerRefsByFormatOnly() {
        assertThrows(IllegalArgumentException.class, () -> Names.qualifyOwner("team payments"));
        assertThrows(IllegalArgumentException.class, () -> Names.qualifyOwner(""));
        assertThrows(IllegalArgumentException.class, () -> Names.qualifyOwner("group:"));
        assertThrows(IllegalArgumentException.class, () -> Names.qualifyOwner("@bad/x"));
    }

    @Test
    void buildsComponentRef() {
        assertEquals("component:default/orders-api", Names.componentRef("orders-api"));
    }

    @Test
    void qualifiesRefsWithInjectableDefaultKind() {
        assertEquals("api:default/payments", Names.qualifyRef("payments", "api"));
        assertEquals("api:default/payments", Names.qualifyRef("api:default/payments", "api"));
        assertEquals("component:default/inventory", Names.qualifyRef("inventory", "component"));
        assertEquals("resource:custom/db", Names.qualifyRef("resource:custom/db", "resource"));
    }

    @Test
    void qualifyRefRejectsMalformed() {
        assertThrows(IllegalArgumentException.class, () -> Names.qualifyRef("bad ref", "api"));
        assertThrows(IllegalArgumentException.class, () -> Names.qualifyRef("", "api"));
    }

    @Test
    void normalizesTagsToBackstageCharset() {
        assertEquals(List.of("java", "spring-boot", "c#"),
                Names.normalizeTags(new String[]{"Java", "Spring Boot", "C#"}));
        assertEquals(List.of("java"), Names.normalizeTags(new String[]{"java", "JAVA", "  "}));
    }
}
