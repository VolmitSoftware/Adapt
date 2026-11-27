package art.arcane.adapt.content.protector;

import art.arcane.adapt.AdaptConfig;
import me.crafter.mc.lockettepro.LocketteProAPI;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class LocketteProProtector extends BukkitProtector {
  @Override
  protected boolean canAccessChest(Player player, Location chestlocation) {
    return LocketteProAPI.isOwner(chestlocation.getBlock(), player);
  }

  @Override
  public String name() {
    return "LockettePro";
  }

  @Override
  public boolean enabledByDefault() {
    return AdaptConfig.get().getProtectorSupport().isLockettePro();
  }
}
