package art.arcane.adapt.platform.value;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdTest {
  @Test
  void parseWithNamespace() {
    Id id = Id.parse("adapt:elevator");
    assertEquals("adapt", id.namespace());
    assertEquals("elevator", id.path());
    assertEquals("adapt:elevator", id.toString());
  }

  @Test
  void parseBarePathDefaultsToMinecraft() {
    assertEquals(Id.minecraft("diamond_sword"), Id.parse("diamond_sword"));
    assertEquals(Id.minecraft("stone"), Id.parse(":stone"));
  }

  @Test
  void parseAcceptsNestedPaths() {
    assertEquals("entity/player/hurt", Id.parse("minecraft:entity/player/hurt").path());
  }

  @Test
  void parseRejectsInvalidInput() {
    assertThrows(IllegalArgumentException.class, () -> Id.parse("Adapt:elevator"));
    assertThrows(IllegalArgumentException.class, () -> Id.parse("adapt:Elevator"));
    assertThrows(IllegalArgumentException.class, () -> Id.parse("a:b:c"));
    assertThrows(IllegalArgumentException.class, () -> Id.parse("adapt:"));
    assertThrows(IllegalArgumentException.class, () -> Id.parse("bad/ns:path"));
  }

  @Test
  void tryParseReturnsNullForBadInput() {
    assertNull(Id.tryParse(null));
    assertNull(Id.tryParse(""));
    assertNull(Id.tryParse("UPPER"));
    assertEquals(Id.adapt("x"), Id.tryParse("adapt:x"));
  }

  @Test
  void legacyNamesRoundTrip() {
    Id id = Id.fromLegacyName("DIAMOND_SWORD");
    assertEquals(Id.minecraft("diamond_sword"), id);
    assertEquals("DIAMOND_SWORD", id.legacyName());
    assertEquals(Id.minecraft("cave_air"), Id.fromLegacyName(" Cave_Air "));
  }

  @Test
  void airIds() {
    assertTrue(Id.minecraft("air").isAir());
    assertTrue(Id.minecraft("cave_air").isAir());
    assertTrue(Id.minecraft("void_air").isAir());
    assertFalse(Id.minecraft("stone").isAir());
    assertFalse(Id.adapt("air").isAir());
  }

  @Test
  void ordersByNamespaceThenPath() {
    assertTrue(Id.adapt("z").compareTo(Id.minecraft("a")) < 0);
    assertTrue(Id.minecraft("a").compareTo(Id.minecraft("b")) < 0);
  }
}
