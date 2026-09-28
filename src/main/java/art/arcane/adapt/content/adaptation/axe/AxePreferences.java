package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Material;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AxePreferences {
  static final PlayerPreference<CommonPreferences.Toggle> LATCH = CommonPreferences.toggle("latched-run", key("latch", "Latch tree felling; sneak again to cancel"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("require-sneak", key("sneak", "Require sneaking"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> IGNORE_PASSIVE = CommonPreferences.toggle("ignore-passive", key("ignore_passive", "Ignore passive mobs"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> PLAYERS = CommonPreferences.toggle("player-targets", key("players", "Affect players"));
  static final PlayerPreference<CommonPreferences.Scale> WORK = CommonPreferences.scale("work-limit", key("work", "Work limit"));
  static final PlayerPreference<Reserve> RESERVE = new PlayerPreference<>(Reserve.class,
      new PlayerPreference.Definition<>("hunger-reserve", key("reserve", "Hunger remaining after use"), Reserve.NONE, List.of(
          choice(Reserve.NONE, "none", "No extra reserve", Material.BOWL),
          choice(Reserve.FIVE, "five", "Keep 5 hunger", Material.APPLE),
          choice(Reserve.TEN, "ten", "Keep 10 hunger", Material.BREAD),
          choice(Reserve.FIFTEEN, "fifteen", "Keep 15 hunger", Material.COOKED_BEEF))));
  static final PlayerPreference<Items> ITEMS = new PlayerPreference<>(Items.class,
      new PlayerPreference.Definition<>("items", key("items", "Collected items"), Items.ALL, List.of(
          choice(Items.ALL, "all", "All permitted", Material.CHEST),
          choice(Items.LOGS, "logs", "Logs and stems", Material.OAK_LOG),
          choice(Items.SAPLINGS, "saplings", "Saplings and propagules", Material.OAK_SAPLING),
          choice(Items.APPLES, "apples", "Apples only", Material.APPLE))));
  static final PlayerPreference<Trigger> TRIGGER = new PlayerPreference<>(Trigger.class,
      new PlayerPreference.Definition<>("trigger", key("trigger", "Activation gesture"), Trigger.SNEAK, List.of(
          choice(Trigger.SNEAK, "while_sneaking", "While sneaking", Material.LEATHER_BOOTS),
          choice(Trigger.NOT_SNEAKING, "not_sneaking", "While not sneaking", Material.IRON_BOOTS),
          choice(Trigger.ALWAYS, "always", "Always", Material.DIAMOND_BOOTS))));

  private AxePreferences() {}

  public static void addMessages(MessageCatalog.Builder builder) {
    Set<String> added = new HashSet<>();
    for (PlayerPreference<?> preference : List.of(LATCH, SNEAK, IGNORE_PASSIVE, PLAYERS, WORK, RESERVE, ITEMS, TRIGGER)) {
      if (added.add(preference.label().id())) { builder.add(preference.label()); }
      for (PlayerPreference.Choice<?> choice : preference.choices()) {
        if (choice.label().id().startsWith("axe.preferences.") && added.add(choice.label().id())) { builder.add(choice.label()); }
      }
    }
  }
  private static TextKey key(String id, String text) { return TextKey.of("axe.preferences." + id, text); }
  private static <E extends Enum<E>> PlayerPreference.Choice<E> choice(E value, String id, String text, Material icon) {
    return new PlayerPreference.Choice<>(value, key(id, text), icon, 1);
  }
  enum Reserve {
    NONE(0), FIVE(5), TEN(10), FIFTEEN(15);
    private final int minimum;
    Reserve(int minimum) { this.minimum = minimum; }
    boolean permits(int food) { return food >= minimum; }
  }
  enum Items {
    ALL, LOGS, SAPLINGS, APPLES;
    boolean accepts(Material material) {
      return switch (this) {
        case ALL -> true;
        case LOGS -> material.name().endsWith("_LOG") || material.name().endsWith("_STEM");
        case SAPLINGS -> material.name().endsWith("_SAPLING") || material == Material.MANGROVE_PROPAGULE;
        case APPLES -> material == Material.APPLE;
      };
    }
  }
  enum Trigger {
    SNEAK, NOT_SNEAKING, ALWAYS;
    boolean accepts(boolean sneaking) { return this == ALWAYS || (this == SNEAK) == sneaking; }
  }
}
