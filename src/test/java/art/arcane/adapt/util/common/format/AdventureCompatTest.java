package art.arcane.adapt.util.common.format;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdventureCompatTest {

  @Test
  void environmentIncompatibilityLatchesGlobalFallback() {
    assertTrue(AdventureCompat.shouldDisableMiniMessage(new NoClassDefFoundError("adventure missing")));
    assertTrue(AdventureCompat.shouldDisableMiniMessage(new NoSuchMethodError("wrong adventure version")));
    assertTrue(AdventureCompat.shouldDisableMiniMessage(new NoSuchFieldError("wrong adventure version")));
    assertTrue(AdventureCompat.shouldDisableMiniMessage(new IncompatibleClassChangeError()));
  }

  @Test
  void malformedMessageDoesNotLatchGlobalFallback() {
    assertFalse(AdventureCompat.shouldDisableMiniMessage(new RuntimeException("simulated parsing exception")));
    assertFalse(AdventureCompat.shouldDisableMiniMessage(new IllegalArgumentException("bad tag")));
    assertFalse(AdventureCompat.shouldDisableMiniMessage(new IllegalStateException()));
    assertFalse(AdventureCompat.shouldDisableMiniMessage(null));
  }

  @Test
  void fallbackProducesCleanTextWhenMiniMessageIsUnavailable() {
    assertEquals("bold", AdventureCompat.stripTags("<bold>bold</bold>"));
    assertEquals("hi", AdventureCompat.toLegacySection("<red>hi</red>"));
    assertEquals("plain", AdventureCompat.stripTags("plain"));
  }
}
