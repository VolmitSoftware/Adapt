package art.arcane.adapt.engine.protection;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.protection.ProtectionCheck;
import art.arcane.adapt.platform.protection.ProtectionHost;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

/**
 * The protection host core hands to everyone: the platform's host answers whether the actor and world are there and
 * breaks blocks; every rule comes from the registered protectors, the same way on every platform. A known adaptation
 * answers through its own protector set, anything else through the protectors enabled by default.
 */
public final class ProtectionDispatch implements ProtectionHost {
  private final ProtectionHost platform;
  private final Function<Id, Adaptation<?>> adaptations;

  public ProtectionDispatch(ProtectionHost platform, Function<Id, Adaptation<?>> adaptations) {
    this.platform = Objects.requireNonNull(platform, "platform");
    this.adaptations = Objects.requireNonNull(adaptations, "adaptations");
  }

  @Override
  public boolean allows(PlayerRef actor, ProtectionCheck check, Loc at, @Nullable Id adaptation) {
    if (!platform.allows(actor, check, at, adaptation)) {
      return false;
    }
    Adaptation<?> owner = adaptation == null ? null : adaptations.apply(adaptation);
    if (owner != null) {
      return owner.allows(check, actor, at, null);
    }
    return Protectors.defaultsAllow(check, actor, at, null);
  }

  @Override
  public boolean allowsDamage(PlayerRef actor, EntityRef target, @Nullable Id adaptation) {
    if (!platform.allowsDamage(actor, target, adaptation)) {
      return false;
    }
    Adaptation<?> owner = adaptation == null ? null : adaptations.apply(adaptation);
    if (owner != null) {
      return owner.canDamageTarget(actor, target);
    }
    ProtectionCheck check = target instanceof PlayerRef ? ProtectionCheck.PVP : ProtectionCheck.PVE;
    return Protectors.defaultsAllow(check, actor, target.location(), target);
  }

  @Override
  public boolean attemptBreak(PlayerRef actor, BlockRef block) {
    return platform.attemptBreak(actor, block);
  }
}
