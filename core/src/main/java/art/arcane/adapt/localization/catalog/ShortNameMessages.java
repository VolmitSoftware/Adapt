package art.arcane.adapt.localization.catalog;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The names adaptations show on their own skill's pages, where the skill is already named, for the adaptations whose
 * full name repeats that skill. Everywhere else the full name stays, so it still says which skill it belongs to.
 */
public final class ShortNameMessages {
  private static final Map<String, TextKey> BY_ADAPTATION = Map.ofEntries(
      entry("axe-chop", "axe.chop", "Chop"),
      entry("axe-ground-smash", "axe.ground_smash", "Ground Smash"),
      entry("axe-drop-to-inventory", "axe.drop_to_inventory", "Drop to Inventory"),
      entry("pickaxe-drop-to-inventory", "pickaxe.drop_to_inventory", "Drop to Inventory"),
      entry("pickaxe-silk-spawner", "pickaxe.silk_spawner", "Silk-Spawner"),
      entry("excavation-drop-to-inventory", "excavation.drop_to_inventory", "Drop to Inventory"),
      entry("herbalism-drop-to-inventory", "herbalism.drop_to_inventory", "Drop to Inventory"),
      entry("herbalism-hippo", "herbalism.hippo", "Hippo"),
      entry("herbalism-luck", "herbalism.luck", "Luck"),
      entry("herbalism-myconid", "herbalism.myconid", "Myconid"),
      entry("herbalism-terralid", "herbalism.terralid", "Terralid"),
      entry("hunter-drop-to-inventory", "hunter.drop_to_inventory", "Drop to Inventory"),
      entry("hunter-regen", "hunter.regen", "Regen"),
      entry("hunter-jumpboost", "hunter.jump_boost", "Heights"),
      entry("hunter-luck", "hunter.luck", "Luck"),
      entry("hunter-speed", "hunter.speed", "Speed"),
      entry("hunter-strength", "hunter.strength", "Strength"),
      entry("hunter-resistance", "hunter.resistance", "Resistance"),
      entry("rift-blink", "rift.blink", "Blink"),
      entry("rift-resist", "rift.resist", "Resistance"),
      entry("golem-masterwork", "golemancy.masterwork", "Masterwork"));

  private ShortNameMessages() {
  }

  /** The short name key of the adaptation {@code adaptationId}, or null when its full name is shown everywhere. */
  public static @Nullable TextKey of(String adaptationId) {
    return BY_ADAPTATION.get(adaptationId);
  }

  public static Map<String, TextKey> all() {
    return BY_ADAPTATION;
  }

  public static void addTo(MessageCatalog.Builder builder) {
    for (TextKey key : BY_ADAPTATION.values()) {
      builder.add(key);
    }
  }

  private static Map.Entry<String, TextKey> entry(String adaptationId, String localizationKey, String english) {
    return Map.entry(adaptationId, TextKey.of(localizationKey + ".short_name", english));
  }
}
