package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Material;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class GolemancyPreferences {
  static final String KEY_PREFIX = "golemancy.preferences.";
  static final PlayerPreference<Labels> STATUS_LABELS = new PlayerPreference<>(Labels.class,
      new PlayerPreference.Definition<>("status-labels", key("status_labels", "Golem status labels"), Labels.SHOWN, List.of(
          choice(Labels.SHOWN, "shown", "Shown", Material.NAME_TAG),
          choice(Labels.HIDDEN, "hidden", "Hidden", Material.GRAY_DYE))));
  static final PlayerPreference<NodeReveal> NODE_REVEAL = new PlayerPreference<>(NodeReveal.class,
      new PlayerPreference.Definition<>("node-reveal", key("node_reveal", "Node reveal"), NodeReveal.ANY_ITEM, List.of(
          choice(NodeReveal.ANY_ITEM, "any_item", "Any Golemancy item", Material.CLAY_BALL),
          choice(NodeReveal.CROOK_ONLY, "crook_only", "Crook only", Material.IRON_HOE))));
  static final PlayerPreference<CommonPreferences.Toggle> WORK_AREA_OUTLINE =
      CommonPreferences.toggle("work-area-outline", key("work_area_outline", "Work-area outline"));
  static final PlayerPreference<CommonPreferences.Toggle> INTERCEPT = CommonPreferences.toggle("intercept", key("intercept", "Intercept"));
  static final PlayerPreference<CommonPreferences.Toggle> RETALIATE_PLAYERS =
      CommonPreferences.toggle("retaliate-players", key("retaliate_players", "Retaliate against players"));
  static final PlayerPreference<CommonPreferences.Toggle> MAGNET = CommonPreferences.toggle("magnet", key("magnet", "Magnet"));
  static final PlayerPreference<CommonPreferences.Toggle> ABSORB_ORBS = CommonPreferences.toggle("absorb-orbs", key("absorb_orbs", "Absorb XP orbs"));
  static final PlayerPreference<CommonPreferences.Toggle> TENDING = CommonPreferences.toggle("tending", key("tending", "Tending"));
  static final PlayerPreference<CommonPreferences.Toggle> TILLING = CommonPreferences.toggle("tilling", key("tilling", "Tilling"));
  static final PlayerPreference<CommonPreferences.Toggle> LEAF_SWEEP = CommonPreferences.toggle("leaf-sweep", key("leaf_sweep", "Leaf sweep"));
  static final PlayerPreference<CommonPreferences.Toggle> SWEEPING_FISTS =
      CommonPreferences.toggle("sweeping-fists", key("sweeping_fists", "Sweeping Fists"));
  static final PlayerPreference<CommonPreferences.Toggle> STATIC_OVERDRIVE =
      CommonPreferences.toggle("static-overdrive", key("static_overdrive", "Static Overdrive"));
  static final PlayerPreference<SignalFeedback> SIGNAL_FEEDBACK = new PlayerPreference<>(SignalFeedback.class,
      new PlayerPreference.Definition<>("signal-feedback", key("signal_feedback", "Signal feedback"), SignalFeedback.CHIME_AND_PARTICLES, List.of(
          choice(SignalFeedback.CHIME_AND_PARTICLES, "chime_and_particles", "Chime and particles", Material.BELL),
          choice(SignalFeedback.ACTION_BAR_ONLY, "action_bar_only", "Action bar only", Material.PAPER))));
  static final PlayerPreference<CommonPreferences.Toggle> AURA = CommonPreferences.toggle("aura", key("aura", "Aura"));
  static final PlayerPreference<CommonPreferences.Toggle> OBEY_OVERSEER =
      CommonPreferences.toggle("obey-overseer", key("obey_overseer", "Servants obey Overseer"));
  static final List<PlayerPreference<?>> ALL = List.of(STATUS_LABELS, NODE_REVEAL, WORK_AREA_OUTLINE, INTERCEPT, RETALIATE_PLAYERS,
      MAGNET, ABSORB_ORBS, TENDING, TILLING, LEAF_SWEEP, SWEEPING_FISTS, STATIC_OVERDRIVE, SIGNAL_FEEDBACK, AURA, OBEY_OVERSEER);

  private GolemancyPreferences() {
  }

  public static void addMessages(MessageCatalog.Builder builder) {
    Set<String> added = new HashSet<>();
    for (PlayerPreference<?> preference : ALL) {
      if (added.add(preference.label().id())) {
        builder.add(preference.label());
      }
      for (PlayerPreference.Choice<?> choice : preference.choices()) {
        if (choice.label().id().startsWith(KEY_PREFIX) && added.add(choice.label().id())) {
          builder.add(choice.label());
        }
      }
    }
  }

  private static TextKey key(String id, String text) {
    return TextKey.of(KEY_PREFIX + id, text);
  }

  private static <E extends Enum<E>> PlayerPreference.Choice<E> choice(E value, String id, String text, Material icon) {
    return new PlayerPreference.Choice<>(value, key(id, text), icon, 1);
  }

  enum Labels {
    SHOWN,
    HIDDEN
  }

  enum NodeReveal {
    ANY_ITEM,
    CROOK_ONLY
  }

  enum SignalFeedback {
    CHIME_AND_PARTICLES,
    ACTION_BAR_ONLY
  }
}
