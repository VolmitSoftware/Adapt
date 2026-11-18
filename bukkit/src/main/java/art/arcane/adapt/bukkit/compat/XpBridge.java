package art.arcane.adapt.bukkit.compat;

import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.api.xp.XpAward;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class XpBridge {
  private XpBridge() {
  }

  @ApiBridge(value = XP.class, instance = false)
  public static void xp(Player player, Skill<?> skill, double xp) {
    XP.xp(BukkitRefs.player(player), skill, xp);
  }

  @ApiBridge(value = XP.class, instance = false)
  public static void xp(Player player, Skill<?> skill, double xp, String rewardKey) {
    XP.xp(BukkitRefs.player(player), skill, xp, rewardKey);
  }

  @ApiBridge(value = XP.class, instance = false)
  public static void xpSilent(Player player, Skill<?> skill, double xp) {
    XP.xpSilent(BukkitRefs.player(player), skill, xp);
  }

  @ApiBridge(value = XP.class, instance = false)
  public static void xpSilent(Player player, Skill<?> skill, double xp, String rewardKey) {
    XP.xpSilent(BukkitRefs.player(player), skill, xp, rewardKey);
  }

  @ApiBridge(value = XP.class, instance = false)
  public static void award(Player player, Skill<?> skill, XpAward award) {
    XP.award(BukkitRefs.player(player), skill, award);
  }

  @ApiBridge(value = XP.class, instance = false)
  public static void spatialXP(Location location, Skill<?> skill, double xp, int radius, long duration) {
    XP.spatialXP(BukkitRefs.loc(location), skill, xp, radius, duration);
  }

  @ApiBridge(value = XP.class, instance = false)
  public static void wisdom(Player player, long wisdom) {
    XP.wisdom(BukkitRefs.player(player), wisdom);
  }

  @ApiBridge(value = XP.class, instance = false)
  public static void knowledge(Player player, Skill<?> skill, long knowledge) {
    XP.knowledge(BukkitRefs.player(player), skill, knowledge);
  }

  @ApiBridge(value = XP.class, instance = false)
  public static void boostXP(Player player, Skill<?> skill, double percentage, long duration) {
    XP.boostXP(BukkitRefs.player(player), skill, percentage, duration);
  }
}
