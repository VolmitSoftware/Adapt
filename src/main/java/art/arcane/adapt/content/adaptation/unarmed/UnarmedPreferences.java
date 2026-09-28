package art.arcane.adapt.content.adaptation.unarmed;

import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.adaptation.AdaptationDamageTargets;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class UnarmedPreferences {
  static final PlayerPreference<CommonPreferences.Toggle> PLAYERS = CommonPreferences.toggle("player-targets", key("player_targets", "Disarm players"));
  static final PlayerPreference<CommonPreferences.Toggle> ARMOR = CommonPreferences.toggle("mob-armor", key("mob_armor", "Disarm mob armor"));
  static final PlayerPreference<CommonPreferences.Toggle> COMBAT = CommonPreferences.toggle("combat", key("combat", "Unarmed damage bonus"));
  static final PlayerPreference<CommonPreferences.Toggle> BREAKING = CommonPreferences.toggle("soft-blocks", key("soft_blocks", "Faster soft block breaking"));
  static final PlayerPreference<CommonPreferences.Toggle> AIRBORNE = CommonPreferences.toggle("airborne-only", key("airborne_only", "Require airborne clap"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> ARM = CommonPreferences.toggle("require-arming", key("require_arming", "Require sneak-right-click arming"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> MANUAL = CommonPreferences.toggle("manual-meditation", key("manual_meditation", "Require explicit meditation arming"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> ARMED = CommonPreferences.toggle("meditation-armed", key("meditation_armed", "Meditation armed"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<Targets> TARGETS = new PlayerPreference<>(Targets.class,
      new PlayerPreference.Definition<>("targets", key("targets", "Eligible targets"), Targets.ALL, List.of(
          choice(Targets.ALL, "all", "All permitted targets", Material.DIAMOND_SWORD),
          choice(Targets.HOSTILE, "hostile", "Hostile mobs", Material.ZOMBIE_HEAD),
          choice(Targets.NON_PLAYERS, "non_players", "Non-player targets", Material.BONE))));
  static final PlayerPreference<Loadout> LOADOUT = new PlayerPreference<>(Loadout.class,
      new PlayerPreference.Definition<>("loadout", key("loadout", "Charge loadout"), Loadout.BOTH, List.of(
          choice(Loadout.BOTH, "both", "Fists or shield", Material.SHIELD),
          choice(Loadout.FISTS, "fists", "Empty hands only", Material.LEATHER),
          choice(Loadout.SHIELD, "shield", "Shield only", Material.SHIELD))));
  static final PlayerPreference<Release> RELEASE = new PlayerPreference<>(Release.class,
      new PlayerPreference.Definition<>("release", key("release", "Release grabbed target"), Release.SNEAK_OR_HIT, List.of(
          choice(Release.SNEAK_OR_HIT, "sneak_or_hit", "Release sneak or punch again", Material.LEATHER_BOOTS),
          choice(Release.NEXT_HIT, "next_hit", "Punch again only", Material.LEATHER))));
  static final PlayerPreference<Reserve> HUNGER = new PlayerPreference<>(Reserve.class,
      new PlayerPreference.Definition<>("hunger-reserve", key("hunger", "Hunger remaining after clap"), Reserve.NONE, List.of(
          choice(Reserve.NONE, "none", "No extra reserve", Material.BOWL),
          choice(Reserve.FIVE, "five", "Keep 5 hunger", Material.APPLE),
          choice(Reserve.TEN, "ten", "Keep 10 hunger", Material.BREAD))));
  private UnarmedPreferences() {}
  public static void addMessages(MessageCatalog.Builder builder) {
    Set<String> added = new HashSet<>();
    for (PlayerPreference<?> preference : List.of(PLAYERS, ARMOR, COMBAT, BREAKING, AIRBORNE, ARM, MANUAL, ARMED, TARGETS, LOADOUT, RELEASE, HUNGER)) {
      if (added.add(preference.label().id())) { builder.add(preference.label()); }
      for (PlayerPreference.Choice<?> choice : preference.choices()) {
        if (choice.label().id().startsWith("unarmed.preferences.") && added.add(choice.label().id())) { builder.add(choice.label()); }
      }
    }
  }
  private static TextKey key(String id, String text) { return TextKey.of("unarmed.preferences." + id, text); }
  private static <E extends Enum<E>> PlayerPreference.Choice<E> choice(E value, String id, String text, Material icon) {
    return new PlayerPreference.Choice<>(value, key(id, text), icon, 1);
  }
  enum Targets {
    ALL, HOSTILE, NON_PLAYERS;
    boolean accepts(LivingEntity target) {
      return this == ALL || (!(target instanceof Player) && (this == NON_PLAYERS || AdaptationDamageTargets.allows(target, true)));
    }
  }
  enum Loadout {
    BOTH, FISTS, SHIELD;
    boolean accepts(boolean fists, boolean shield) { return (this != SHIELD && fists) || (this != FISTS && shield); }
  }
  enum Release { SNEAK_OR_HIT, NEXT_HIT }
  enum Reserve {
    NONE(0), FIVE(5), TEN(10);
    private final int remaining;
    Reserve(int remaining) { this.remaining = remaining; }
    boolean permits(int food, int cost) { return food - cost >= remaining; }
  }
}
