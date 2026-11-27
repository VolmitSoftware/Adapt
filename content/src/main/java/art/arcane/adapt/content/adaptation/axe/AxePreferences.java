package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.value.Id;
import java.util.List;

public final class AxePreferences {
  static final PlayerPreference<CommonPreferences.Toggle> LATCH = CommonPreferences.toggle("latched-run", key("latch"), CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("require-sneak", key("sneak"), CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<CommonPreferences.Toggle> IGNORE_PASSIVE = CommonPreferences.toggle("ignore-passive", key("ignore_passive"), CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<CommonPreferences.Toggle> PLAYERS = CommonPreferences.toggle("player-targets", key("players"));
  public static final PlayerPreference<CommonPreferences.Scale> WORK = CommonPreferences.scale("work-limit", key("work"));
  static final PlayerPreference<Reserve> RESERVE = PlayerPreference.of("hunger-reserve", Reserve.class, Reserve.NONE, List.of(
          choice(Reserve.NONE, "none", Id.minecraft("bowl")),
          choice(Reserve.FIVE, "five", Id.minecraft("apple")),
          choice(Reserve.TEN, "ten", Id.minecraft("bread")),
          choice(Reserve.FIFTEEN, "fifteen", Id.minecraft("cooked_beef")))).labelled(key("reserve"));
  public static final PlayerPreference<Items> ITEMS = PlayerPreference.of("items", Items.class, Items.ALL, List.of(
          choice(Items.ALL, "all", Id.minecraft("chest")),
          choice(Items.LOGS, "logs", Id.minecraft("oak_log")),
          choice(Items.SAPLINGS, "saplings", Id.minecraft("oak_sapling")),
          choice(Items.APPLES, "apples", Id.minecraft("apple")))).labelled(key("items"));
  public static final PlayerPreference<Trigger> TRIGGER = PlayerPreference.of("trigger", Trigger.class, Trigger.SNEAK, List.of(
          choice(Trigger.SNEAK, "while_sneaking", Id.minecraft("leather_boots")),
          choice(Trigger.NOT_SNEAKING, "not_sneaking", Id.minecraft("iron_boots")),
          choice(Trigger.ALWAYS, "always", Id.minecraft("diamond_boots")))).labelled(key("trigger"));
  public static final PlayerPreference<FelledLogs> FELLED_LOGS = PlayerPreference.of("felled-logs", FelledLogs.class, FelledLogs.STRIPPED, List.of(
          choice(FelledLogs.STRIPPED, "stripped", Id.minecraft("stripped_oak_log")),
          choice(FelledLogs.PLANKS, "planks", Id.minecraft("oak_planks")))).labelled(key("felled_logs"));

  private AxePreferences() {}

  private static Text key(String id) {
    return Text.key("adapt.axe.preferences." + id);
  }
  private static <E extends Enum<E>> PlayerPreference.Choice<E> choice(E value, String id, Id icon) {
    return new PlayerPreference.Choice<>(value, key(id), icon, 1);
  }
  enum Reserve {
    NONE(0), FIVE(5), TEN(10), FIFTEEN(15);
    private final int minimum;
    Reserve(int minimum) { this.minimum = minimum; }
    boolean permits(int food) { return food >= minimum; }
  }
  public enum Items {
    ALL, LOGS, SAPLINGS, APPLES;
    public boolean accepts(Id material) {
      String name = material.path();
      return switch (this) {
        case ALL -> true;
        case LOGS -> name.endsWith("_log") || name.endsWith("_stem");
        case SAPLINGS -> name.endsWith("_sapling") || material.equals(Id.minecraft("mangrove_propagule"));
        case APPLES -> material.equals(Id.minecraft("apple"));
      };
    }
  }
  public enum FelledLogs {
    STRIPPED, PLANKS
  }
  public enum Trigger {
    SNEAK, NOT_SNEAKING, ALWAYS;
    public boolean accepts(boolean sneaking) { return this == ALWAYS || (this == SNEAK) == sneaking; }
  }
}
