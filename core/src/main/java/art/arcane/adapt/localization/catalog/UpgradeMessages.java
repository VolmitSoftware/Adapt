package art.arcane.adapt.localization.catalog;

import art.arcane.adapt.localization.AdaptKeys;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

/**
 * The shared upgrade labels. Each upgrade's name and description live with its skill's kit text, its lore lines in the
 * skill's own message class, all under the {@code upgrade.<skill>.<slug>} prefix.
 */
public final class UpgradeMessages {
  public static final TextKey GRANTED_LEVEL = AdaptKeys.text("upgrade.granted_level", "{adaptation} works at level {level}");
  public static final TextKey OVERCHARGE = AdaptKeys.text("upgrade.overcharge", "Overcharge");

  private UpgradeMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    builder.add(GRANTED_LEVEL);
    builder.add(OVERCHARGE);
  }
}
