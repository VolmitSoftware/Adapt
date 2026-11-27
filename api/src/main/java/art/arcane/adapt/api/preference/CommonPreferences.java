package art.arcane.adapt.api.preference;

import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/** The preferences most content shares, and factories for the common shapes. Their labels are Adapt's own. */
@ApiStatus.AvailableSince("1.0.0")
public final class CommonPreferences {
  private static final Id LIME = Id.minecraft("lime_stained_glass_pane");
  private static final Id YELLOW = Id.minecraft("yellow_stained_glass_pane");
  private static final Id ORANGE = Id.minecraft("orange_stained_glass_pane");
  private static final Id RED = Id.minecraft("red_stained_glass_pane");

  /** Every adaptation has this unless it overrides {@code getPlayerPreferences()} without it. */
  public static final PlayerPreference<Toggle> ENABLED = toggle("enabled", Text.key("adapt.preferences.enabled"));
  /** A skill's own on/off switch, which turns every adaptation of the skill on or off for the player. */
  public static final PlayerPreference<Toggle> SKILL_ENABLED =
      toggle("enabled", Text.key("adapt.preferences.skill_enabled"));
  /** For adaptations that save the player from death: which of several goes first. */
  public static final PlayerPreference<DeathSavePriority> DEATH_SAVE_PRIORITY = PlayerPreference.of(
      "death-save-priority", DeathSavePriority.class, DeathSavePriority.NORMAL, List.of(
          new PlayerPreference.Choice<>(DeathSavePriority.FIRST, Text.key("adapt.preferences.first"), LIME, 1),
          new PlayerPreference.Choice<>(DeathSavePriority.NORMAL, Text.key("adapt.preferences.normal"), YELLOW, 1),
          new PlayerPreference.Choice<>(DeathSavePriority.LAST, Text.key("adapt.preferences.last"), ORANGE, 1)))
      .labelled(Text.key("adapt.preferences.death_save_priority"));

  private CommonPreferences() {
  }

  /** An on/off preference that starts on, labelled by convention. */
  public static PlayerPreference<Toggle> toggle(String id) {
    return toggle(id, Toggle.ON);
  }

  /** An on/off preference that starts at {@code defaultValue}, labelled by convention. */
  public static PlayerPreference<Toggle> toggle(String id, Toggle defaultValue) {
    return PlayerPreference.of(id, Toggle.class, defaultValue, List.of(
        new PlayerPreference.Choice<>(Toggle.ON, Text.key("adapt.preferences.on"), LIME, 1),
        new PlayerPreference.Choice<>(Toggle.OFF, Text.key("adapt.preferences.off"), RED, 1)));
  }

  /** An on/off preference that starts on, with an explicit label. */
  public static PlayerPreference<Toggle> toggle(String id, Text label) {
    return toggle(id, Toggle.ON).labelled(label);
  }

  /** An on/off preference that starts at {@code defaultValue}, with an explicit label. */
  public static PlayerPreference<Toggle> toggle(String id, Text label, Toggle defaultValue) {
    return toggle(id, defaultValue).labelled(label);
  }

  /** A full, half or quarter strength preference. */
  public static PlayerPreference<Scale> scale(String id) {
    return PlayerPreference.of(id, Scale.class, Scale.FULL, List.of(
        new PlayerPreference.Choice<>(Scale.FULL, Text.key("adapt.preferences.full"), LIME, 1),
        new PlayerPreference.Choice<>(Scale.HALF, Text.key("adapt.preferences.half"), YELLOW, 1),
        new PlayerPreference.Choice<>(Scale.QUARTER, Text.key("adapt.preferences.quarter"), ORANGE, 1)));
  }

  /** A strength preference that starts at full, with an explicit label. */
  public static PlayerPreference<Scale> scale(String id, Text label) {
    return scale(id).labelled(label);
  }

  /** The values of an on/off preference. */
  public enum Toggle {
    ON, OFF
  }

  /** The values of {@link #DEATH_SAVE_PRIORITY}. */
  public enum DeathSavePriority {
    FIRST, NORMAL, LAST
  }

  /** The values of a strength preference, each with the multiplier it stands for. */
  public enum Scale {
    FULL(1D), HALF(0.5D), QUARTER(0.25D);

    private final double multiplier;

    Scale(double multiplier) {
      this.multiplier = multiplier;
    }

    /** 1 for full, 0.5 for half, 0.25 for quarter. */
    public double multiplier() {
      return multiplier;
    }
  }
}
