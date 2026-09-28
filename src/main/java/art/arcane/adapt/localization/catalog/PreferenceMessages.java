package art.arcane.adapt.localization.catalog;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

public final class PreferenceMessages {
  public static final TextKey ENABLED = TextKey.of("preferences.enabled", "Adaptation enabled");
  public static final TextKey SKILL_ENABLED = TextKey.of("preferences.skill_enabled", "Skill adaptations enabled");
  public static final TextKey ON = TextKey.of("preferences.on", "On");
  public static final TextKey OFF = TextKey.of("preferences.off", "Off");
  public static final TextKey FULL = TextKey.of("preferences.full", "Full earned limit");
  public static final TextKey HALF = TextKey.of("preferences.half", "Half earned limit");
  public static final TextKey QUARTER = TextKey.of("preferences.quarter", "Quarter earned limit");
  public static final TextKey CONFIRM = TextKey.of("preferences.confirm", "Repeat the same action within 5 seconds to confirm.");

  private PreferenceMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    builder.addAll(List.of(ENABLED, SKILL_ENABLED, ON, OFF, FULL, HALF, QUARTER, CONFIRM));
  }
}
