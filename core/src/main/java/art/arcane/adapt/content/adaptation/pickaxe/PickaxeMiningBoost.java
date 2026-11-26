package art.arcane.adapt.content.adaptation.pickaxe;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.volmlib.util.math.M;

import java.util.Map;
import java.util.UUID;

/** A timed mining speed bonus on {@code attribute} that its owner renews while the player keeps mining. */
public final class PickaxeMiningBoost {
  private static final long MILLIS_PER_TICK = 50L;

  private final Adaptation<?> owner;
  private final String slot;
  private final Id attribute;
  private final Map<UUID, Long> expiresAt;

  public PickaxeMiningBoost(Adaptation<?> owner, String slot, Id attribute, Map<UUID, Long> expiresAt) {
    this.owner = owner;
    this.slot = slot;
    this.attribute = attribute;
    this.expiresAt = expiresAt;
  }

  /** Applies or renews the bonus; true when it was not already running. */
  public boolean surge(PlayerRef player, double bonus, int durationTicks) {
    long now = M.ms();
    long durationMillis = durationTicks * MILLIS_PER_TICK;
    Long previous = expiresAt.put(player.uuid(), now + durationMillis);
    AdaptAttributeService.get().applyTimed(player, owner.getName(), slot, attribute,
        bonus, AttributeOperation.ADD_MULTIPLIED_TOTAL, durationTicks);
    AbilityGlyphs.duration(player.uuid(), owner, now, durationMillis);
    return previous == null || previous <= now;
  }
}
