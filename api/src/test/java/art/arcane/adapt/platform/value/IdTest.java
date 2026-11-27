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
  void parseAdaptDefaultsBarePathsToAdapt() {
    assertEquals(Id.adapt("herbalism/luck"), Id.parseAdapt("herbalism/luck"));
    assertEquals(Id.adapt("swords"), Id.parseAdapt(":swords"));
    assertEquals(Id.of("alchemist", "alchemy"), Id.parseAdapt("alchemist:alchemy"));
    assertEquals("adapt:herbalism/luck", Id.parseAdapt("herbalism/luck").toString());
    assertThrows(IllegalArgumentException.class, () -> Id.parseAdapt("Swords"));
    assertThrows(IllegalArgumentException.class, () -> Id.parseAdapt("a:b:c"));
  }

  @Test
  void tryParseAdaptReturnsNullForBadInput() {
    assertNull(Id.tryParseAdapt(null));
    assertNull(Id.tryParseAdapt(""));
    assertNull(Id.tryParseAdapt("a+b"));
    assertEquals(Id.adapt("swords"), Id.tryParseAdapt("swords"));
  }

  @Test
  void constructionRejectsCharactersOutsideResourceLocations() {
    assertThrows(IllegalArgumentException.class, () -> Id.of("adapt", "a+b"));
    assertThrows(IllegalArgumentException.class, () -> Id.of("adapt", "a:b"));
    assertThrows(IllegalArgumentException.class, () -> Id.of("ad/apt", "a"));
    assertThrows(IllegalArgumentException.class, () -> Id.of("", "a"));
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
