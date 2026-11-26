package art.arcane.adapt.localization.catalog;

import art.arcane.adapt.api.mutation.upgrade.SkillUpgrades;
import art.arcane.adapt.api.mutation.upgrade.UpgradeSpec;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

/**
 * Name and description keys of all 72 upgraded adaptations, with the English text taken from the skill block catalog.
 * Lore lines belong in each skill's own message class under the same {@code upgrade.<skill>.<slug>} prefix.
 */
public final class UpgradeMessages {
  public static final TextKey GRANTED_LEVEL = TextKey.of("upgrade.granted_level", "{adaptation} works at level {level}");
  public static final TextKey OVERCHARGE = TextKey.of("upgrade.overcharge", "Overcharge");

  private UpgradeMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    builder.add(GRANTED_LEVEL);
    builder.add(OVERCHARGE);
    for (UpgradeSpec spec : SkillUpgrades.all()) {
      builder.add(TextKey.of(spec.nameKey(), spec.upgrade().name()));
      builder.add(TextKey.of(spec.descriptionKey(), spec.upgrade().description()));
    }
  }
}
