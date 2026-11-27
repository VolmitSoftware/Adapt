package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.value.Id;

import java.util.List;

public final class GolemancyPreferences {
  static final PlayerPreference<Labels> STATUS_LABELS = PlayerPreference.of("status-labels", Labels.class, Labels.SHOWN, List.of(
          choice(Labels.SHOWN, "shown", Id.minecraft("name_tag")),
          choice(Labels.HIDDEN, "hidden", Id.minecraft("gray_dye")))).labelled(key("status_labels"));
  static final PlayerPreference<NodeReveal> NODE_REVEAL = PlayerPreference.of("node-reveal", NodeReveal.class, NodeReveal.ANY_ITEM, List.of(
          choice(NodeReveal.ANY_ITEM, "any_item", Id.minecraft("clay_ball")),
          choice(NodeReveal.CROOK_ONLY, "crook_only", Id.minecraft("iron_hoe")))).labelled(key("node_reveal"));
  static final PlayerPreference<CommonPreferences.Toggle> WORK_AREA_OUTLINE =
      CommonPreferences.toggle("work-area-outline", key("work_area_outline"));
  static final PlayerPreference<CommonPreferences.Toggle> INTERCEPT = CommonPreferences.toggle("intercept", key("intercept"));
  static final PlayerPreference<CommonPreferences.Toggle> RETALIATE_PLAYERS =
      CommonPreferences.toggle("retaliate-players", key("retaliate_players"));
  static final PlayerPreference<CommonPreferences.Toggle> MAGNET = CommonPreferences.toggle("magnet", key("magnet"));
  static final PlayerPreference<CommonPreferences.Toggle> ABSORB_ORBS = CommonPreferences.toggle("absorb-orbs", key("absorb_orbs"));
  static final PlayerPreference<CommonPreferences.Toggle> TENDING = CommonPreferences.toggle("tending", key("tending"));
  static final PlayerPreference<CommonPreferences.Toggle> TILLING = CommonPreferences.toggle("tilling", key("tilling"));
  static final PlayerPreference<CommonPreferences.Toggle> LEAF_SWEEP = CommonPreferences.toggle("leaf-sweep", key("leaf_sweep"));
  static final PlayerPreference<CommonPreferences.Toggle> SWEEPING_FISTS =
      CommonPreferences.toggle("sweeping-fists", key("sweeping_fists"));
  static final PlayerPreference<CommonPreferences.Toggle> STATIC_OVERDRIVE =
      CommonPreferences.toggle("static-overdrive", key("static_overdrive"));
  static final PlayerPreference<SignalFeedback> SIGNAL_FEEDBACK = PlayerPreference.of("signal-feedback", SignalFeedback.class, SignalFeedback.CHIME_AND_PARTICLES, List.of(
          choice(SignalFeedback.CHIME_AND_PARTICLES, "chime_and_particles", Id.minecraft("bell")),
          choice(SignalFeedback.ACTION_BAR_ONLY, "action_bar_only", Id.minecraft("paper")))).labelled(key("signal_feedback"));
  static final PlayerPreference<CommonPreferences.Toggle> AURA = CommonPreferences.toggle("aura", key("aura"));
  static final PlayerPreference<CommonPreferences.Toggle> OBEY_OVERSEER =
      CommonPreferences.toggle("obey-overseer", key("obey_overseer"));
  static final List<PlayerPreference<?>> ALL = List.of(STATUS_LABELS, NODE_REVEAL, WORK_AREA_OUTLINE, INTERCEPT, RETALIATE_PLAYERS,
      MAGNET, ABSORB_ORBS, TENDING, TILLING, LEAF_SWEEP, SWEEPING_FISTS, STATIC_OVERDRIVE, SIGNAL_FEEDBACK, AURA, OBEY_OVERSEER);

  private GolemancyPreferences() {
  }

  private static Text key(String id) {
    return Text.key("adapt.golemancy.preferences." + id);
  }

  private static <E extends Enum<E>> PlayerPreference.Choice<E> choice(E value, String id, Id icon) {
    return new PlayerPreference.Choice<>(value, key(id), icon, 1);
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
