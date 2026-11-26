package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.UUID;
import java.util.function.Consumer;

/** Anti-Levitation's release: strips levitation, then guards the fall for as long as the cooldown lasts. */
public final class RiftLevitation {

  private final Adaptation<?> owner;
  private final Cooldowns cooldown;
  private final Consumer<PlayerRef> onCancelled;

  public RiftLevitation(Adaptation<?> owner, Cooldowns cooldown, Consumer<PlayerRef> onCancelled) {
    this.owner = owner;
    this.cooldown = cooldown;
    this.onCancelled = onCancelled;
  }

  /** Cancels the player's levitation when the owner is active for them and off cooldown. */
  public void cancel(PlayerRef p, double cooldownSeconds) {
    if (p.effect(RiftDescent.LEVITATION) == null) {
      return;
    }
    if (!owner.hasActiveAdaptation(p)) {
      return;
    }
    UUID playerId = p.uuid();
    long cooldownMs = Math.max(1L, (long) (cooldownSeconds * 1000D));
    if (!cooldown.isReady(playerId, cooldownMs)) {
      return;
    }

    p.removeEffect(RiftDescent.LEVITATION);
    onCancelled.accept(p);
    cooldown.mark(playerId, cooldownMs);
    RiftDescent.release(owner, p, cooldownSeconds);
  }
}
