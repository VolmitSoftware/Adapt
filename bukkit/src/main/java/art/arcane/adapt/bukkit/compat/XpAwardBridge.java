package art.arcane.adapt.bukkit.compat;

import art.arcane.adapt.api.xp.XpAward;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import org.bukkit.Location;

public final class XpAwardBridge {
  private XpAwardBridge() {
  }

  @ApiBridge(value = XpAward.class, instance = false, name = "<init>")
  public static XpAward create(String source, double amount, Location location, boolean notifyPlayer,
                               XpAward.Kind kind) {
    return new XpAward(source, amount, BukkitRefs.loc(location), notifyPlayer, kind);
  }

  @ApiBridge(value = XpAward.class, name = "location")
  public static Location location(XpAward award) {
    return BukkitRefs.location(award.location());
  }
}
