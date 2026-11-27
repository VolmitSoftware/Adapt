package art.arcane.adapt.client.server;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationGroup;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.player.MutationResult;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.engine.adaptation.AdaptationLevelActions;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.mutation.MutationOffers;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.mutation.upgrade.SkillUpgrades;
import art.arcane.adapt.engine.mutation.upgrade.Lending;
import art.arcane.adapt.engine.preference.PlayerPreferences;
import art.arcane.adapt.engine.player.PlayerApi;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.client.view.GroupLabelView;
import art.arcane.adapt.client.view.KeptView;
import art.arcane.adapt.client.view.MutationSpreadView;
import art.arcane.adapt.client.view.MutationSpreadViews;
import art.arcane.adapt.client.view.PreferenceOwner;
import art.arcane.adapt.engine.gui.MutationGui;
import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The server-side operations behind the skill screens, shared by the native client's frames and the resource pack
 * screens so both run the same permission and rule checks the chest GUI runs.
 */
public final class ScreenActions {
  private ScreenActions() {
  }

  /** The adaptation when the chest GUI would let the player change it, else null. */
  public static Adaptation<?> available(SkillRegistry registry, PlayerRef player, String adaptationId) {
    Adaptation<?> adaptation = registry == null ? null : ClientUiService.adaptation(registry, adaptationId);
    return adaptation != null && AdaptationLevelActions.available(adaptation, player) ? adaptation : null;
  }

  /** Sets a skill's or adaptation's preference to {@code value}; false when the preference refuses it. */
  public static boolean setPreference(SkillRegistry registry, AdaptPlayer player, PreferenceOwner owner, String ownerId,
                                      String preferenceId, String value) {
    if (registry == null || player == null) {
      return false;
    }
    return switch (owner) {
      case SKILL -> setSkillPreference(registry.getSkill(ownerId), player, preferenceId, value);
      case ADAPTATION -> setAdaptationPreference(ClientUiService.adaptation(registry, ownerId), player, preferenceId, value);
    };
  }

  /** Puts every personal preference of the skill or adaptation back to the server default. */
  public static boolean resetPreference(SkillRegistry registry, AdaptPlayer player, PreferenceOwner owner, String ownerId) {
    if (registry == null || player == null) {
      return false;
    }
    return switch (owner) {
      case SKILL -> {
        Skill<?> skill = registry.getSkill(ownerId);
        yield skill != null && PlayerPreferences.resetSkill(skill, player);
      }
      case ADAPTATION -> {
        Adaptation<?> adaptation = ClientUiService.adaptation(registry, ownerId);
        yield adaptation != null && PlayerPreferences.reset(adaptation, player);
      }
    };
  }

  /** Skills whose page offers Mutate to the player, in registry order. */
  public static List<String> mutateOffers(SkillRegistry registry, AdaptPlayer player) {
    List<String> offers = new ArrayList<>();
    for (Skill<?> skill : MutationOffers.leads(player.getData(), registry.getSkills(), AdaptConfig.get().getMutations())) {
      offers.add(SkillHost.lineKey(skill));
    }
    return offers;
  }

  /** Whether the page of {@code skill} offers Mutate to the player. */
  public static boolean mutateOffered(SkillRegistry registry, AdaptPlayer player, Skill<?> skill) {
    return registry != null && player != null && skill != null
        && MutationOffers.offered(player.getData(), skill, registry.getSkills(), AdaptConfig.get().getMutations());
  }

  /** The spread for {@code lead} and an optional partner, or null when {@code lead} cannot lead a mutation. */
  public static MutationSpreadView spread(SkillRegistry registry, AdaptPlayer player, Skill<?> lead, Skill<?> partner) {
    if (registry == null || player == null || lead == null || lead instanceof MutatedSkill) {
      return null;
    }
    return MutationSpreadViews.build(registry, player.getData(), lead, partner, AdaptConfig.get().getMutations(), "");
  }

  /**
   * Merges {@code partner} into {@code lead} with every rule checked again. The answer carries the spread to show next,
   * naming the new skill when the merge went through, and the result text.
   */
  public static MutationOutcome mutate(SkillRegistry registry, PlayerRef player, AdaptPlayer adaptPlayer, Skill<?> lead,
                                       Skill<?> partner) {
    Objects.requireNonNull(lead, "lead");
    PlayerData data = adaptPlayer.getData();
    MutationResult.Status result = MutationGui.merge(player, adaptPlayer, lead, partner);
    String text = MutationGui.resultText(result, lead, partner);
    boolean mutated = result == MutationResult.Status.MUTATED;
    String mutatedId = mutated ? data.mutationOf(SkillHost.lineKey(lead)).key() : "";
    MutationSpreadView spread = MutationSpreadViews.build(registry, data, lead, mutated ? null : partner,
        AdaptConfig.get().getMutations(), mutatedId);
    return new MutationOutcome(mutated, text, spread);
  }

  /**
   * Undoes the player's mutation named {@code skillId} when they are in debug mode or hold the admin permission.
   * Returns the result text, or null when nothing was undone.
   */
  public static String unmutate(PlayerRef player, AdaptPlayer adaptPlayer, String skillId) {
    SkillPair mutation = adaptPlayer == null ? null : SkillMutations.mutationNamed(adaptPlayer.getData(), skillId);
    if (mutation == null || !MutationGui.mayUnmutate(player) || !MutationGui.unmutate(player, adaptPlayer, mutation)) {
      return null;
    }
    return MutationGui.unmutatedText(mutation, player.name());
  }

  /**
   * The originals each of the player's mutated skills keeps, and the originals each held upgrade lends a level while
   * their own code runs.
   */
  public static List<KeptView> kept(SkillRegistry registry, PlayerRef player, AdaptPlayer adaptPlayer) {
    List<KeptView> kept = new ArrayList<>();
    for (SkillPair mutation : adaptPlayer.getData().getMutations()) {
      MutatedSkill mutated = registry.getMutatedSkill(mutation);
      if (mutated == null) {
        continue;
      }
      for (Adaptation<?> adaptation : mutated.getKept()) {
        kept.add(new KeptView(SkillHost.lineKey(mutated), adaptation.getId().toString(), SkillUpgrades.keptLevel(adaptation)));
      }
      for (Lending.Running running : Lending.running(player, adaptPlayer, registry, mutation)) {
        kept.add(new KeptView(running.upgrade().getId().toString(), running.original().getId().toString(), running.level()));
      }
    }
    return kept;
  }

  /** The names of every grouped skill's adaptation groups in the current language, grouped skills only. */
  public static List<GroupLabelView> groupLabels(SkillRegistry registry) {
    List<GroupLabelView> named = new ArrayList<>();
    for (Skill<?> skill : registry.getSkills()) {
      List<String> labels = new ArrayList<>();
      for (AdaptationGroup group : skill.getAdaptationGroups()) {
        labels.add(group.label().resolve());
      }
      if (!labels.isEmpty()) {
        named.add(new GroupLabelView(SkillHost.lineKey(skill), labels));
      }
    }
    return named;
  }

  private static boolean setSkillPreference(Skill<?> skill, AdaptPlayer player, String preferenceId, String value) {
    if (skill == null || !CommonPreferences.SKILL_ENABLED.id().equals(preferenceId)) {
      return false;
    }
    CommonPreferences.Toggle toggle = CommonPreferences.SKILL_ENABLED.parse(value).orElse(null);
    return toggle != null && PlayerPreferences.setSkillEnabled(skill, player, toggle);
  }

  private static boolean setAdaptationPreference(Adaptation<?> adaptation, AdaptPlayer player, String preferenceId,
                                                 String value) {
    if (adaptation == null) {
      return false;
    }
    for (PlayerPreference<?> preference : adaptation.getPlayerPreferences()) {
      if (preference.id().equals(preferenceId)) {
        return adaptation.isPlayerPreferenceVisible(PlayerApi.profile(player), preference) && setChoice(adaptation, player, preference, value);
      }
    }
    return false;
  }

  private static <E extends Enum<E>> boolean setChoice(Adaptation<?> adaptation, AdaptPlayer player,
                                                       PlayerPreference<E> preference, String value) {
    E choice = preference.parse(value).orElse(null);
    return choice != null && PlayerPreferences.set(adaptation, player, preference, choice);
  }

  /** What a merge did: whether it went through, its text, and the spread to show next. */
  public record MutationOutcome(boolean mutated, String text, MutationSpreadView spread) {
    public MutationOutcome {
      Objects.requireNonNull(text, "text");
      Objects.requireNonNull(spread, "spread");
    }
  }
}
