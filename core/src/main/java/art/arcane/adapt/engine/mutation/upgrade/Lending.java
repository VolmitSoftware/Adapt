package art.arcane.adapt.engine.mutation.upgrade;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.mutation.UpgradeDeclaration;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.mutation.kit.Kits;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * How an upgrade stands in for its sources, behind {@code Mutations}. A player who mutated a skill reads 0 on every
 * original of it, so a held upgrade lends each of its sources a level. Outside gates (recipes, brewing, usage
 * conflicts) read that level through {@link #level}, so what an original unlocked stays unlocked; the originals an
 * upgrade fuses also read it as their own level through {@link #fusedLevel}, so their code keeps running.
 */
public final class Lending {
  private Lending() {
  }

  /** The originals the upgrade's kit declares it stands in for; empty for an upgrade no kit declares. */
  public static List<Id> sources(SkillUpgrade<?> upgrade) {
    return UpgradeCaches.SOURCES.computeIfAbsent(upgrade,
        (SkillUpgrade<?> key) -> declaration(key).map(UpgradeDeclaration::sources).orElse(List.of()));
  }

  /** The sources the upgrade's kit declares it fuses; empty for an upgrade no kit declares. */
  public static Set<Id> fuses(SkillUpgrade<?> upgrade) {
    return UpgradeCaches.FUSES.computeIfAbsent(upgrade,
        (SkillUpgrade<?> key) -> declaration(key).map(UpgradeDeclaration::fuses).orElse(Set.of()));
  }

  private static Optional<UpgradeDeclaration> declaration(SkillUpgrade<?> upgrade) {
    Skill<?> skill = upgrade.getSkill();
    if (skill == null) {
      return Optional.empty();
    }
    for (UpgradeDeclaration declaration : Kits.of(skill).upgrades()) {
      if (declaration.id().equals(upgrade.getId())) {
        return Optional.of(declaration);
      }
    }
    return Optional.empty();
  }

  /** The highest level an upgrade lends its source {@code original}, reading each upgrade's level with {@code upgradeLevel}. */
  public static int lentLevel(Adaptation<?> original, ToIntFunction<Adaptation<?>> upgradeLevel) {
    return lent(original, sourced(original), upgradeLevel);
  }

  /**
   * {@code own} when it is above 0, otherwise the highest level an upgrade of {@code skill}, the skill the original is
   * placed on, that fuses it lends it; {@code own} for an original placed on no skill.
   */
  public static int fusedLevel(Adaptation<?> original, @Nullable SkillHost skill, int own,
                               ToIntFunction<Adaptation<?>> upgradeLevel) {
    if (own > 0 || skill == null || original instanceof SkillUpgrade<?>) {
      return own;
    }
    List<SkillUpgrade<?>> fusing = UpgradeCaches.FUSING.computeIfAbsent(original,
        (Adaptation<?> key) -> upgrades(skill, (SkillUpgrade<?> upgrade) -> upgrade.fuses(key)));
    int lent = 0;
    for (SkillUpgrade<?> upgrade : fusing) {
      lent = Math.max(lent, upgrade.lend(original, upgradeLevel.applyAsInt(upgrade)));
    }
    return lent;
  }

  private static int lent(Adaptation<?> original, List<SkillUpgrade<?>> upgrades,
                          ToIntFunction<Adaptation<?>> upgradeLevel) {
    if (original instanceof SkillUpgrade<?> || original.getSkill() == null) {
      return 0;
    }
    int lent = 0;
    for (SkillUpgrade<?> upgrade : upgrades) {
      lent = Math.max(lent, upgrade.lend(original, upgradeLevel.applyAsInt(upgrade)));
    }
    return lent;
  }

  /** {@link #level(Adaptation, int, ToIntFunction)} with the adaptation's own level read by {@code levelOf}. */
  public static int level(Adaptation<?> adaptation, ToIntFunction<Adaptation<?>> levelOf) {
    return adaptation == null ? 0 : level(adaptation, levelOf.applyAsInt(adaptation), levelOf);
  }

  /** {@code own} when it is above 0, otherwise the level a fusing upgrade lends. */
  public static int level(Adaptation<?> original, int own, ToIntFunction<Adaptation<?>> upgradeLevel) {
    return own > 0 ? own : lentLevel(original, upgradeLevel);
  }

  /** A block context built from a fusing upgrade's context with the level it lends, or null. */
  public static Adaptation.BlockActionContext lentContext(Adaptation<?> original,
                                                           Function<Adaptation<?>, Adaptation.BlockActionContext> upgradeContext) {
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
    for (SkillUpgrade<?> upgrade : fusing(original)) {
      if (upgrade.getLevel(player) > 0) {
        upgrade.landed(player);
      }
    }
  }

  /** The multiplier a held fusing upgrade puts on what {@code original} grants {@code player}; 1 when none is held. */
  public static double strength(Adaptation<?> original, PlayerRef player) {
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
   * The originals of the pair's parents that a held upgrade lends a level while their own code runs for the player,
   * so their settings still matter; each with the upgrade and the level it works at.
   */
  public static List<Running> running(PlayerRef player, AdaptPlayer adaptPlayer, SkillRegistry registry, SkillPair pair) {
    List<Running> running = new ArrayList<>();
    for (Id parent : List.of(pair.lead(), pair.partner())) {
      Skill<?> skill = registry.getSkill(parent);
      if (skill == null) {
        continue;
      }
      for (Adaptation<?> upgrade : SkillHost.of(skill).getUpgrades()) {
        if (!(upgrade instanceof SkillUpgrade<?> held) || AdaptationHost.of(held).level(adaptPlayer) <= 0) {
          continue;
        }
        for (Adaptation<?> original : skill.getAdaptations()) {
          if (held.fuses(original) && AdaptationHost.of(original).level(adaptPlayer) <= 0) {
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

  /** Whether any upgrade of the original's skill lists it among its sources. */
  public static boolean isSource(Adaptation<?> original) {
    return !sourced(original).isEmpty();
  }

  /** Whether any upgrade of the original's skill fuses it. */
  public static boolean fused(Adaptation<?> original) {
    return !fusing(original).isEmpty();
  }

  private static List<SkillUpgrade<?>> sourced(Adaptation<?> original) {
    if (original == null || original.getSkill() == null) {
      return List.of();
    }
    return UpgradeCaches.SOURCED.computeIfAbsent(original, (Adaptation<?> key) ->
        upgrades(SkillHost.of(key.getSkill()), (SkillUpgrade<?> upgrade) -> upgrade.isSource(key)));
  }

  private static List<SkillUpgrade<?>> fusing(Adaptation<?> original) {
    if (original == null || original.getSkill() == null) {
      return List.of();
    }
    return UpgradeCaches.FUSING.computeIfAbsent(original, (Adaptation<?> key) ->
        upgrades(SkillHost.of(key.getSkill()), (SkillUpgrade<?> upgrade) -> upgrade.fuses(key)));
  }

  private static List<SkillUpgrade<?>> upgrades(SkillHost skill, Predicate<SkillUpgrade<?>> matches) {
    List<SkillUpgrade<?>> found = new ArrayList<>(3);
    for (Adaptation<?> upgrade : skill.getUpgrades()) {
      if (upgrade instanceof SkillUpgrade<?> skillUpgrade && matches.test(skillUpgrade)) {
        found.add(skillUpgrade);
      }
    }
    return List.copyOf(found);
  }
}
