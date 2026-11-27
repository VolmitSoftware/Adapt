package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.player.SkillProgress;
import art.arcane.adapt.api.xp.XpAward;

/** Unity's vanilla-XP sharing and Polymath's per-skill global XP bonus. */
public final class DiscoveryXpShare {
  private DiscoveryXpShare() {
  }

  public static double orbXp(double multiplier, double levelPercent, int orbs) {
    return orbs * multiplier * levelPercent;
  }

  /** Gives {@code xp} to the skill behind {@code line}. */
  public static void feed(SkillProgress line, double xp, String rewardKey) {
    if (line.isAvailable() && Double.isFinite(xp) && xp > 0D) {
      line.award(XpAward.gameplay(xp, rewardKey));
    }
  }

  /** Skill lines at or above {@code threshold}. */
  public static int levelledSkills(PlayerProfile data, int threshold) {
    int qualifying = 0;
    for (SkillProgress line : data.skills()) {
      if (line.level() >= threshold) {
        qualifying++;
      }
    }
    return qualifying;
  }

  /** Raises every XP gain by {@code bonus} for {@code durationMillis}, replacing the previous bonus of {@code owner}. */
  public static void boost(PlayerProfile data, String owner, double bonus, long durationMillis) {
    data.boost(owner, bonus, durationMillis);
  }
}
