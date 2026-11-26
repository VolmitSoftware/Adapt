package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.api.xp.XPMultiplier;

/** Unity's vanilla-XP sharing and Polymath's per-skill global XP bonus. */
public final class DiscoveryXpShare {
  private DiscoveryXpShare() {
  }

  public static double orbXp(double multiplier, double levelPercent, int orbs) {
    return orbs * multiplier * levelPercent;
  }

  /** Gives {@code xp} to the skill behind {@code line}. */
  public static void feed(AdaptPlayer player, SkillRegistry registry, PlayerSkillLine line, double xp, String rewardKey) {
    XP.xp(player, registry.getSkill(line.getLine()), xp, rewardKey);
  }

  /** Skill lines at or above {@code threshold}. */
  public static int levelledSkills(PlayerData data, int threshold) {
    int qualifying = 0;
    for (PlayerSkillLine line : data.getSkillLines().values()) {
      if (line != null && line.getLevel() >= threshold) {
        qualifying++;
      }
    }
    return qualifying;
  }

  /** Raises every XP gain by {@code bonus} for {@code durationMillis}, replacing the previous bonus of {@code owner}. */
  public static void boost(PlayerData data, String owner, double bonus, long durationMillis) {
    data.globalXPMultiplier(XPMultiplier.owned(owner, bonus, durationMillis));
  }
}
