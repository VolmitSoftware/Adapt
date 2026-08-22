package art.arcane.adapt.content.protector;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.protection.Protector;
import me.crafter.mc.lockettepro.LocketteProAPI;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class LocketteProProtector implements Protector {
  @Override
  public boolean canAccessChest(Player player, Location chestlocation, Adaptation<?> adaptation) {
    return LocketteProAPI.isOwner(chestlocation.getBlock(), player);
  }

  @Override
  public String getName() {
    return "LockettePro";
  }

  @Override
  public boolean isEnabledByDefault() {
    return AdaptConfig.get().getProtectorSupport().isLockettePro();
  }
}
