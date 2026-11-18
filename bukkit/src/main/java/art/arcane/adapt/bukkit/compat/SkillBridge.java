package art.arcane.adapt.bukkit.compat;

import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class SkillBridge {
  private SkillBridge() {
  }

  @ApiBridge(value = Skill.class, name = "getIcon")
  public static Material icon(Skill<?> skill) {
    return BukkitRefs.material(skill.getIcon());
  }

  @ApiBridge(Skill.class)
  public static boolean hasUsePermission(Skill<?> self, Player player, Skill<?> skill) {
    return self.hasUsePermission(BukkitRefs.player(player), skill);
  }

  @ApiBridge(Skill.class)
  public static void xp(Skill<?> skill, Player player, double xp) {
    skill.xp(BukkitRefs.player(player), xp);
  }

  @ApiBridge(Skill.class)
  public static void xp(Skill<?> skill, Player player, double xp, String rewardKey) {
    skill.xp(BukkitRefs.player(player), xp, rewardKey);
  }

  @ApiBridge(Skill.class)
  public static void xp(Skill<?> skill, Player player, Location at, double xp) {
    skill.xp(BukkitRefs.player(player), BukkitRefs.loc(at), xp);
  }

  @ApiBridge(Skill.class)
  public static void xp(Skill<?> skill, Player player, Location at, double xp, String rewardKey) {
    skill.xp(BukkitRefs.player(player), BukkitRefs.loc(at), xp, rewardKey);
  }

  @ApiBridge(Skill.class)
  public static void xpS(Skill<?> skill, Player player, Location at, double xp) {
    skill.xpS(BukkitRefs.player(player), BukkitRefs.loc(at), xp);
  }

  @ApiBridge(Skill.class)
  public static void xpS(Skill<?> skill, Player player, Location at, double xp, String rewardKey) {
    skill.xpS(BukkitRefs.player(player), BukkitRefs.loc(at), xp, rewardKey);
  }

  @ApiBridge(Skill.class)
  public static void xpSilent(Skill<?> skill, Player player, double xp) {
    skill.xpSilent(BukkitRefs.player(player), xp);
  }

  @ApiBridge(Skill.class)
  public static void xpSilent(Skill<?> skill, Player player, double xp, String rewardKey) {
    skill.xpSilent(BukkitRefs.player(player), xp, rewardKey);
  }

  @ApiBridge(Skill.class)
  public static void xp(Skill<?> skill, Location at, double xp, int radius, long duration) {
    skill.xp(BukkitRefs.loc(at), xp, radius, duration);
  }

  @ApiBridge(Skill.class)
  public static void knowledge(Skill<?> skill, Player player, long knowledge) {
    skill.knowledge(BukkitRefs.player(player), knowledge);
  }

  @ApiBridge(Skill.class)
  public static boolean openGui(Skill<?> skill, Player player, boolean checkPermissions) {
    return skill.openGui(BukkitRefs.player(player), checkPermissions);
  }

  @ApiBridge(Skill.class)
  public static void openGui(Skill<?> skill, Player player) {
    skill.openGui(BukkitRefs.player(player));
  }

  @ApiBridge(Skill.class)
  public static void openGui(Skill<?> skill, Player player, int page) {
    skill.openGui(BukkitRefs.player(player), page);
  }
}
