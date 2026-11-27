package art.arcane.adapt.bukkit.feature.protection;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.protection.ProtectionCheck;
import art.arcane.adapt.platform.protection.ProtectionHost;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Bukkit's part of the protection checks: whether the player and the world are there, and real breaks. The rules are
 * the protectors Adapt registers for the protection plugins it integrates with, which core asks for every platform.
 * Breaks call {@link Player#breakBlock(Block)} directly, which fires the real break event; core
 * {@code BonusBlockBreaks} wraps this call, so it must not call back into it.
 */
public final class BukkitProtectionHost implements ProtectionHost {
  @Override
  public boolean allows(PlayerRef actor, ProtectionCheck check, Loc at, @Nullable Id adaptation) {
    Player player = BukkitRefs.player(actor);
    Location location = BukkitRefs.location(at);
    return player != null && location != null && location.getWorld() != null;
  }

  @Override
  public boolean allowsDamage(PlayerRef actor, EntityRef target, @Nullable Id adaptation) {
    return BukkitRefs.player(actor) != null && BukkitRefs.entity(target) != null;
  }

  @Override
  public boolean attemptBreak(PlayerRef actor, BlockRef block) {
    Player player = BukkitRefs.player(actor);
    Block target = BukkitRefs.block(block);
    if (player == null || target == null || target.getType().isAir()) {
      return false;
    }
    return player.breakBlock(target);
  }
}
