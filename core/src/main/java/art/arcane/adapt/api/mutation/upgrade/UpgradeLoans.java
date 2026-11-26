package art.arcane.adapt.api.mutation.upgrade;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.SkillMutation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * How an upgrade stands in for the originals it fuses. A player who mutated a skill reads 0 on every original of it, so
 * a held upgrade lends each original it fuses a level. Outside gates (recipes, brewing, usage conflicts) read that
 * level through {@link #level(Adaptation, ToIntFunction)}, so what an original unlocked stays unlocked. An original
 * whose own code should run for a mutated player reads its level through {@link #level(Adaptation, int, ToIntFunction)}
 * and reports each effect it lands through {@link #landed}.
 */
public final class UpgradeLoans {
  private UpgradeLoans() {
  }

  /**
   * The level an upgrade at {@code upgradeLevel} lends {@code original} unless it says otherwise in
   * {@link SkillUpgrade#lend}: the original's top level at the upgrade's top level, one less for each level the upgrade
   * is short of its top, and never below 1.
   */
  public static int lent(Adaptation<?> original, Adaptation<?> upgrade, int upgradeLevel) {
    if (upgradeLevel <= 0) {
      return 0;
    }
    return Math.max(1, original.getMaxLevel() - Math.max(0, upgrade.getMaxLevel() - upgradeLevel));
  }

  /** {@link #level(Adaptation, int, ToIntFunction)} with the adaptation's own level read by {@code levelOf}. */
  public static int level(Adaptation<?> adaptation, ToIntFunction<Adaptation<?>> levelOf) {
    return adaptation == null ? 0 : level(adaptation, levelOf.applyAsInt(adaptation), levelOf);
  }

  /**
   * {@code own} when the player holds the original, otherwise the highest level a fusing upgrade lends it, reading each
   * upgrade's level with {@code upgradeLevel}.
   */
  public static int level(Adaptation<?> original, int own, ToIntFunction<Adaptation<?>> upgradeLevel) {
    if (own > 0 || original instanceof SkillUpgrade<?> || original.getSkill() == null) {
      return Math.max(0, own);
    }
    int lent = 0;
    for (SkillUpgrade<?> upgrade : fusing(original)) {
      lent = Math.max(lent, upgrade.lend(original, upgradeLevel.applyAsInt(upgrade)));
    }
    return lent;
  }

  /** The block context of the original, or one built from a fusing upgrade's context with the lent level. */
  public static Adaptation.BlockActionContext context(Adaptation<?> original, Adaptation.BlockActionContext own,
                                                      Function<Adaptation<?>, Adaptation.BlockActionContext> upgradeContext) {
    if (own != null) {
      return own;
    }
    for (SkillUpgrade<?> upgrade : fusing(original)) {
      Adaptation.BlockActionContext lent = upgradeContext.apply(upgrade);
      if (lent != null && lent.level() > 0) {
        return new Adaptation.BlockActionContext(lent.player(), lent.location(), upgrade.lend(original, lent.level()));
      }
    }
    return null;
  }

  /** Tells each held upgrade that fuses {@code original} that the original landed an effect for {@code player}. */
  public static void landed(Adaptation<?> original, PlayerRef player) {
    if (player == null) {
      return;
    }
    for (SkillUpgrade<?> upgrade : fusing(original)) {
      if (upgrade.getLevel(player) > 0) {
        upgrade.landed(player);
      }
    }
  }

  /** The multiplier a held fusing upgrade puts on what {@code original} grants {@code player}; 1 when none is held. */
  public static double strength(Adaptation<?> original, PlayerRef player) {
    if (player == null) {
      return 1D;
    }
    double strength = 1D;
    for (SkillUpgrade<?> upgrade : fusing(original)) {
      int level = upgrade.getLevel(player);
      if (level > 0) {
        strength = Math.max(strength, upgrade.strength(original, level));
      }
    }
    return strength;
  }

  /**
   * The originals of {@code mutation}'s parents that a held upgrade lends a level while their own code runs for the
   * player, so their settings still matter; each with the upgrade and the level it works at.
   */
  public static List<Running> running(PlayerRef player, AdaptPlayer adaptPlayer, SkillRegistry registry,
                                      SkillMutation mutation) {
    List<Running> running = new ArrayList<>();
    for (String parent : List.of(mutation.lead(), mutation.partner())) {
      Skill<?> skill = registry.getSkill(parent);
      if (skill == null) {
        continue;
      }
      for (Adaptation<?> upgrade : skill.getUpgrades()) {
        if (!(upgrade instanceof SkillUpgrade<?> held) || held.getLevel(adaptPlayer) <= 0) {
          continue;
        }
        for (Adaptation<?> original : skill.getAdaptations()) {
          if (held.fuses(original) && original.getLevel(adaptPlayer) <= 0) {
            int level = original.getLevel(player);
            if (level > 0) {
              running.add(new Running(held, original, level));
            }
          }
        }
      }
    }
    return running;
  }

  /** An original running at {@code level} on the level {@code upgrade} lends it. */
  public record Running(SkillUpgrade<?> upgrade, Adaptation<?> original, int level) {
  }

  /** Whether any upgrade of the original's skill fuses it. */
  public static boolean fused(Adaptation<?> original) {
    return !fusing(original).isEmpty();
  }

  private static List<SkillUpgrade<?>> fusing(Adaptation<?> original) {
    if (original == null || original.getSkill() == null) {
      return List.of();
    }
    return UpgradeCaches.FUSING.computeIfAbsent(original, UpgradeLoans::scanFusing);
  }

  private static List<SkillUpgrade<?>> scanFusing(Adaptation<?> original) {
    List<SkillUpgrade<?>> fusing = new ArrayList<>(3);
    for (Adaptation<?> upgrade : original.getSkill().getUpgrades()) {
      if (upgrade instanceof SkillUpgrade<?> skillUpgrade && skillUpgrade.fuses(original)) {
        fusing.add(skillUpgrade);
      }
    }
    return List.copyOf(fusing);
  }
}
