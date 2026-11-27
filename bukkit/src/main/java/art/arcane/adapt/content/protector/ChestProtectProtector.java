package art.arcane.adapt.content.protector;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import me.angeschossen.chestprotect.api.addons.ChestProtectAddon;
import me.angeschossen.chestprotect.api.protection.block.BlockProtection;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class ChestProtectProtector extends BukkitProtector {
  private final ChestProtectAddon chestProtect;

  public ChestProtectProtector() {
    this.chestProtect = new ChestProtectAddon(Adapt.instance);
  }

  @Override
  protected boolean canAccessChest(Player player, Location chestlocation) {
    if (!chestProtect.isProtectable(chestlocation.getBlock())) return true;
    BlockProtection blockProtection = chestProtect.getProtection(chestlocation);
    if (blockProtection == null) return true;
    return blockProtection.isTrusted(player.getUniqueId());
  }

  @Override
  public String name() {
    return "ChestProtect";
  }

  @Override
  public boolean enabledByDefault() {
    return AdaptConfig.get().getProtectorSupport().isChestProtect();
  }
}
