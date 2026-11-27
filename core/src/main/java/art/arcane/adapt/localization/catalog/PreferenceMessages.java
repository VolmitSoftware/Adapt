package art.arcane.adapt.localization.catalog;

import art.arcane.adapt.localization.AdaptKeys;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

public final class PreferenceMessages {
  public static final TextKey ENABLED = AdaptKeys.text("preferences.enabled", "Adaptation enabled");
  public static final TextKey SKILL_ENABLED = AdaptKeys.text("preferences.skill_enabled", "Skill adaptations enabled");
  public static final TextKey DEATH_SAVE_PRIORITY = AdaptKeys.text("preferences.death_save_priority", "Death-save priority");
  public static final TextKey FIRST = AdaptKeys.text("preferences.first", "First");
  public static final TextKey NORMAL = AdaptKeys.text("preferences.normal", "Normal");
  public static final TextKey LAST = AdaptKeys.text("preferences.last", "Last");
  public static final TextKey ON = AdaptKeys.text("preferences.on", "On");
  public static final TextKey OFF = AdaptKeys.text("preferences.off", "Off");
  public static final TextKey FULL = AdaptKeys.text("preferences.full", "Full earned limit");
  public static final TextKey HALF = AdaptKeys.text("preferences.half", "Half earned limit");
  public static final TextKey QUARTER = AdaptKeys.text("preferences.quarter", "Quarter earned limit");
  public static final TextKey CONFIRM = AdaptKeys.text("preferences.confirm", "Repeat the same action within 5 seconds to confirm.");

  private PreferenceMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    builder.addAll(List.of(ENABLED, SKILL_ENABLED, DEATH_SAVE_PRIORITY, FIRST, NORMAL, LAST, ON, OFF, FULL, HALF, QUARTER, CONFIRM));
  }
}
