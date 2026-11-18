package art.arcane.adapt.bukkit.compat;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationLearningTransaction;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class AdaptationBridge {
  private AdaptationBridge() {
  }

  @ApiBridge(value = Adaptation.class, name = "getIcon")
  public static Material icon(Adaptation<?> adaptation) {
    return BukkitRefs.material(adaptation.getIcon());
  }

  @ApiBridge(Adaptation.class)
  public static void xp(Adaptation<?> adaptation, Player player, double xp) {
    adaptation.xp(BukkitRefs.player(player), xp);
  }

  @ApiBridge(Adaptation.class)
  public static void xp(Adaptation<?> adaptation, Player player, double xp, String rewardKey) {
    adaptation.xp(BukkitRefs.player(player), xp, rewardKey);
  }

  @ApiBridge(Adaptation.class)
  public static void xp(Adaptation<?> adaptation, Player player, Location at, double xp) {
    adaptation.xp(BukkitRefs.player(player), BukkitRefs.loc(at), xp);
  }

  @ApiBridge(Adaptation.class)
  public static void xp(Adaptation<?> adaptation, Player player, Location at, double xp, String rewardKey) {
    adaptation.xp(BukkitRefs.player(player), BukkitRefs.loc(at), xp, rewardKey);
  }

  @ApiBridge(Adaptation.class)
  public static void xpSilent(Adaptation<?> adaptation, Player player, double xp, String rewardKey) {
    adaptation.xpSilent(BukkitRefs.player(player), xp, rewardKey);
  }

  @ApiBridge(Adaptation.class)
  public static void xpSilent(Adaptation<?> adaptation, Player player, double xp) {
    adaptation.xpSilent(BukkitRefs.player(player), xp);
  }

  @ApiBridge(Adaptation.class)
  public static int getLevel(Adaptation<?> adaptation, Player player) {
    return adaptation.getLevel(BukkitRefs.player(player));
  }

  @ApiBridge(Adaptation.class)
  public static double getLevelPercent(Adaptation<?> adaptation, Player player) {
    return adaptation.getLevelPercent(BukkitRefs.player(player));
  }

  @ApiBridge(Adaptation.class)
  public static boolean hasAdaptation(Adaptation<?> adaptation, Player player) {
    return adaptation.hasAdaptation(BukkitRefs.player(player));
  }

  @ApiBridge(Adaptation.class)
  public static boolean hasActiveAdaptation(Adaptation<?> adaptation, Player player) {
    return adaptation.hasActiveAdaptation(BukkitRefs.player(player));
  }

  @ApiBridge(Adaptation.class)
  public static int getActiveLevel(Adaptation<?> adaptation, Player player) {
    return adaptation.getActiveLevel(BukkitRefs.player(player));
  }

  @ApiBridge(Adaptation.class)
  public static boolean isPlayerEnabled(Adaptation<?> adaptation, Player player) {
    return adaptation.isPlayerEnabled(BukkitRefs.player(player));
  }

  @ApiBridge(Adaptation.class)
  public static boolean canUse(Adaptation<?> adaptation, Player player) {
    return adaptation.canUse(BukkitRefs.player(player));
  }

  @ApiBridge(Adaptation.class)
  public static boolean hasUsePermission(Adaptation<?> self, Player player, Adaptation<?> adaptation) {
    return self.hasUsePermission(BukkitRefs.player(player), adaptation);
  }

  @ApiBridge(Adaptation.class)
  public static boolean openGui(Adaptation<?> adaptation, Player player, boolean checkPermissions) {
    return adaptation.openGui(BukkitRefs.player(player), checkPermissions);
  }

  @ApiBridge(Adaptation.class)
  public static void openGui(Adaptation<?> adaptation, Player player) {
    adaptation.openGui(BukkitRefs.player(player));
  }

  @ApiBridge(Adaptation.class)
  public static void openGui(Adaptation<?> adaptation, Player player, int page) {
    adaptation.openGui(BukkitRefs.player(player), page);
  }

  @ApiBridge(Adaptation.class)
  public static AdaptationLearningTransaction.Result learn(Adaptation<?> adaptation, Player player, int level,
                                                           boolean force) {
    return adaptation.learn(BukkitRefs.player(player), level, force);
  }

  @ApiBridge(Adaptation.class)
  public static AdaptationLearningTransaction.Result unlearn(Adaptation<?> adaptation, Player player, int level,
                                                             boolean force) {
    return adaptation.unlearn(BukkitRefs.player(player), level, force);
  }
}
