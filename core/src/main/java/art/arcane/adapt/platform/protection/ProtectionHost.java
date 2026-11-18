package art.arcane.adapt.platform.protection;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.value.Loc;

/**
 * Region and claim checks. Bukkit asks the protection plugins Adapt integrates with; modded posts a
 * fake break or interact event so claim mods can veto. Owning-thread only.
 */
public interface ProtectionHost {
  /**
   * True when {@code actor} may perform the action at {@code at}. {@code adaptationId} lets protection
   * plugins scope flags per adaptation; it may be null.
   */
  boolean allows(PlayerRef actor, ProtectedAction action, Loc at, String adaptationId);

  boolean allowsDamage(PlayerRef actor, EntityRef target, String adaptationId);

  /**
   * Breaks the block as the player would, firing the platform break event so protection and logging
   * plugins see it. Returns true when the block was broken.
   */
  boolean attemptBreak(PlayerRef actor, BlockRef block);
}
