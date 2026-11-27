package art.arcane.adapt.engine.mutation.upgrade;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.engine.mutation.engine.MutationAdaptation;
import art.arcane.adapt.engine.mutation.sound.MutationSoundPlayer;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.world.PlayerAdaptation;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.UpgradeMessages;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/** The rule that decides which adaptations of a skill a player may hold, and what a mutated skill keeps. */
public final class SkillUpgrades {
  /** Plays every upgrade's sound cue, so the per-adaptation cooldown and pitch rotation hold across the server. */
  public static final MutationSoundPlayer SOUNDS = new MutationSoundPlayer(System::currentTimeMillis);

  private SkillUpgrades() {
  }

  /**
   * Whether the player may hold this adaptation: an upgrade only while they hold a mutation containing its skill, a
   * mutated adaptation only while they hold its mutation, a mutation block never, and any other adaptation only while
   * they have not mutated its skill, since the upgrades replace a mutated skill's originals. A signature is never held;
   * it reads the level of the mutated adaptation it replaces.
   */
  public static boolean holdable(Adaptation<?> adaptation, PlayerData data) {
    if (adaptation == null || data == null || adaptation.getSkill() == null || adaptation instanceof MutationBlock<?>
        || adaptation instanceof Signature<?>) {
      return false;
    }
    if (adaptation instanceof MutationAdaptation mutated) {
      return data.getMutations().contains(mutated.getPair());
    }
    boolean mutated = data.mutationOf(SkillHost.lineKey(adaptation.getSkill())) != null;
    return mutated == (adaptation instanceof SkillUpgrade<?>);
  }

  /**
   * The level a player who mutated the adaptation's skill works with it at without learning it: for an original that
   * no upgrade of its skill fuses, its free {@link #keptFloor} or the level the player raised it to with the mutated
   * line's knowledge, whichever is higher; 0 for anything else.
   */
  public static int heritageLevel(Adaptation<?> adaptation, PlayerData data) {
    int floor = keptFloor(adaptation, data);
    if (floor <= 0) {
      return 0;
    }
    return Math.max(floor, Math.min(storedLevel(adaptation, data), adaptation.getMaxLevel()));
  }

  /**
   * The free level a player who mutated the adaptation's skill keeps it at: {@link #keptLevel} for an original that no
   * upgrade of its skill fuses, so what it does and the items and recipes it made keep working; 0 for anything else.
   * Levels above it are bought and refunded like any learned level.
   */
  public static int keptFloor(Adaptation<?> adaptation, PlayerData data) {
    if (data == null || !kept(adaptation) || data.mutationOf(SkillHost.lineKey(adaptation.getSkill())) == null) {
      return 0;
    }
    return keptLevel(adaptation);
  }

  /**
   * The level the player paid to raise a kept original to, stored on the mutated line; 0 while it sits at its free
   * floor. A raised original takes a power slot like any learned adaptation.
   */
  public static int raisedLevel(Adaptation<?> adaptation, PlayerData data) {
    int floor = keptFloor(adaptation, data);
    if (floor <= 0) {
      return 0;
    }
    int stored = Math.min(storedLevel(adaptation, data), adaptation.getMaxLevel());
    return stored > floor ? stored : 0;
  }

  private static int storedLevel(Adaptation<?> adaptation, PlayerData data) {
    PlayerSkillLine line = data.getSkillLineNullable(SkillHost.lineKey(adaptation.getSkill()));
    PlayerAdaptation stored = line == null ? null : line.getAdaptation(adaptation.getId().toString());
    return stored == null || stored.isRegionGranted() ? 0 : Math.max(0, stored.getLevel());
  }

  /**
   * The level a mutated skill keeps the original at: its kept level, raised to the level its recipes need so every
   * item it unlocked stays craftable, and never above its top level.
   */
  public static int keptLevel(Adaptation<?> adaptation) {
    return UpgradeCaches.KEPT_LEVELS.computeIfAbsent(adaptation, SkillUpgrades::resolveKeptLevel);
  }

  private static int resolveKeptLevel(Adaptation<?> adaptation) {
    int level = adaptation.getKeptLevel();
    for (RecipeSpec recipe : adaptation.getRecipes()) {
      level = Math.max(level, recipe.requiredLevel());
    }
    return Math.max(1, Math.min(level, adaptation.getMaxLevel()));
  }

  /** Whether the adaptation is an original that no upgrade of its skill stands in for, so its skill's mutations keep it. */
  public static boolean kept(Adaptation<?> adaptation) {
    return adaptation != null && adaptation.getSkill() != null && !(adaptation instanceof SkillUpgrade<?>)
        && !(adaptation instanceof MutationAdaptation) && !(adaptation instanceof MutationBlock<?>)
        && !(adaptation instanceof Signature<?>) && !Lending.isSource(adaptation);
  }

  /** The originals of {@code skill} that its mutations keep, in registration order. */
  public static List<Adaptation<?>> kept(Skill<?> skill) {
    if (skill == null) {
      return List.of();
    }
    List<Adaptation<?>> kept = new ArrayList<>();
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (kept(adaptation)) {
        kept.add(adaptation);
      }
    }
    return List.copyOf(kept);
  }

  /**
   * Names of the originals the player runs without having learned them: every kept original of a mutated skill, and
   * every original that an upgrade the player learned stands in for.
   */
  public static Set<String> granted(PlayerData data, Function<Id, Skill<?>> skills) {
    List<SkillPair> mutations = data == null ? List.of() : data.getMutations();
    if (mutations.isEmpty()) {
      return Set.of();
    }
    Set<String> granted = new HashSet<>();
    for (SkillPair mutation : mutations) {
      PlayerSkillLine line = data.getSkillLineNullable(mutation.key());
      for (Id parent : List.of(mutation.lead(), mutation.partner())) {
        Skill<?> skill = skills.apply(parent);
        if (skill == null) {
          continue;
        }
        for (Adaptation<?> adaptation : skill.getAdaptations()) {
          if (kept(adaptation) || (line != null && sourceOfLearned(adaptation, skill, line))) {
            granted.add(adaptation.getId().toString());
          }
        }
      }
    }
    return Set.copyOf(granted);
  }

  private static boolean sourceOfLearned(Adaptation<?> original, Skill<?> skill, PlayerSkillLine line) {
    for (Adaptation<?> upgrade : SkillHost.of(skill).getUpgrades()) {
      if (upgrade instanceof SkillUpgrade<?> skillUpgrade && skillUpgrade.isSource(original)
          && line.getAdaptationLevel(upgrade.getId().toString()) > 0) {
        return true;
      }
    }
    return false;
  }

  /** A stat line saying {@code original} works at {@code level}. */
  public static String grantedLine(Adaptation<?> original, int level) {
    return C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(UpgradeMessages.GRANTED_LEVEL,
        trusted("adaptation", C.stripColor(original.getDisplayName())), trusted("level", level));
  }
}
