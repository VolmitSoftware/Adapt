package art.arcane.adapt.bukkit.feature.protection;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.protection.Protector;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.protection.ProtectedAction;
import art.arcane.adapt.platform.protection.ProtectionHost;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Protection checks through the plugin's protector registry. With a known adaptation the check runs through
 * that adaptation's own protector set (config overrides, WorldGuard flags, latency telemetry); without one it
 * asks the default protectors. Breaks call {@link Player#breakBlock(Block)} directly, which fires the real break
 * event; core {@code BonusBlockBreaks} wraps this call, so it must not call back into it.
 */
public final class BukkitProtectionHost implements ProtectionHost {
  private final Function<String, Adaptation<?>> adaptations;
  private final Supplier<List<Protector>> defaults;

  public BukkitProtectionHost(Function<String, Adaptation<?>> adaptations, Supplier<List<Protector>> defaults) {
    this.adaptations = adaptations;
    this.defaults = defaults;
  }

  @Override
  public boolean allows(PlayerRef actor, ProtectedAction action, Loc at, String adaptationId) {
    Player player = BukkitRefs.player(actor);
    Location location = BukkitRefs.location(at);
    if (player == null || location == null || location.getWorld() == null) {
      return false;
    }
    Adaptation<?> adaptation = adaptation(adaptationId);
    if (adaptation != null) {
      return switch (action) {
        case BREAK -> adaptation.canBlockBreak(actor, at);
        case PLACE -> adaptation.canBlockPlace(actor, at);
        case INTERACT -> adaptation.canInteract(actor, at);
        case ACCESS_CONTAINER -> adaptation.canAccessChest(actor, at);
        case PVP -> adaptation.canPVP(actor, at);
        case PVE -> adaptation.canPVE(actor, at);
      };
    }
    return switch (action) {
      case BREAK -> every((Protector protector) -> protector.canBlockBreak(player, location, null));
      case PLACE -> every((Protector protector) -> protector.canBlockPlace(player, location, null));
      case INTERACT -> every((Protector protector) -> protector.canInteract(player, location, null));
      case ACCESS_CONTAINER -> every((Protector protector) -> protector.canAccessChest(player, location, null));
      case PVP -> every((Protector protector) -> protector.canPVP(player, location, null));
      case PVE -> every((Protector protector) -> protector.canPVE(player, location, null));
    };
  }

  @Override
  public boolean allowsDamage(PlayerRef actor, EntityRef target, String adaptationId) {
    Player player = BukkitRefs.player(actor);
    Entity victim = BukkitRefs.entity(target);
    if (player == null || victim == null) {
      return false;
    }
    Adaptation<?> adaptation = adaptation(adaptationId);
    if (adaptation != null) {
      return adaptation.canDamageTarget(actor, target);
    }
    if (victim instanceof Player other) {
      Location location = other.getLocation();
      return every((Protector protector) -> protector.canPVP(player, location, null));
    }
    return every((Protector protector) -> protector.canPVE(player, victim, null));
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

  private Adaptation<?> adaptation(String adaptationId) {
    return adaptationId == null || adaptationId.isBlank() ? null : adaptations.apply(adaptationId);
  }

  private boolean every(Predicate<Protector> check) {
    for (Protector protector : defaults.get()) {
      if (!check.test(protector)) {
        return false;
      }
    }
    return true;
  }
}
