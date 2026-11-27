package art.arcane.adapt.platform.protection;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Region and claim checks, answered the same way on every platform through the registered {@code Protector}s. The
 * platform's own rules are protectors too: on Bukkit one per protection plugin Adapt integrates with, on modded
 * servers {@code claims} (spawn protection, world border, build height, adventure mode, the PVP rule, team friendly
 * fire, and the loader's cancellable break event so claim mods can veto a break). Owning-thread only.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface ProtectionHost {
  /**
   * True when {@code actor} may do {@code check} at {@code at}; false when the actor is offline or the world is not
   * loaded. With an installed {@code adaptation} the answer is that adaptation's own, from the protectors its config
   * enables; without one (null or unknown) every protector enabled by default must allow it.
   */
  boolean allows(PlayerRef actor, ProtectionCheck check, Loc at, @Nullable Id adaptation);

  /**
   * True when {@code actor} may hurt {@code target}: a {@link ProtectionCheck#PVP} check for a player target, a
   * {@link ProtectionCheck#PVE} check otherwise, with {@code adaptation} working as in {@link #allows}. With an
   * adaptation, protected friendlies (displays, invulnerable entities, the actor's own pets) are refused as well.
   */
  boolean allowsDamage(PlayerRef actor, EntityRef target, @Nullable Id adaptation);

  /**
   * Breaks the block as the player would, firing the platform break event so protection and logging
   * plugins see it. Returns true when the block was broken; false for air, or when an event cancelled the break.
   */
  boolean attemptBreak(PlayerRef actor, BlockRef block);
}
