package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.DamageCause;

import java.util.UUID;

/** The fizzle Fire Resistance shows when it puts out a burn, throttled per player. */
public final class NetherFireFizzle {
  private final Adaptation<?> owner;
  private final Cooldowns cooldowns;

  public NetherFireFizzle(Adaptation<?> owner, Cooldowns cooldowns) {
    this.owner = owner;
    this.cooldowns = cooldowns;
  }

  public static boolean isBurn(DamageCause cause) {
    return cause == DamageCause.FIRE || cause == DamageCause.FIRE_TICK;
  }

  /** Plays the fizzle unless one played in the last 0.9 s; returns whether it played. */
  public boolean play(PlayerRef p) {
    UUID id = p.uuid();
    if (cooldowns.remaining(id, 900L) > 0) {
      return false;
    }
    boolean warded = cooldowns.isReady(id, 3000L);
    cooldowns.mark(id);
    NetherFireResist.fizzleFx(owner, p, warded);
    return true;
  }
}
