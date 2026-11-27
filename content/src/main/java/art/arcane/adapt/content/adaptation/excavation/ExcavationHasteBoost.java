package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Timed block break speed while digging. Each dig refreshes the window; the first dig of a window and every raise of
 * the tier show their own effect. The maps belong to the owning adaptation.
 */
public final class ExcavationHasteBoost {
  public static final List<PlatformNeed> NEEDS = List.of(PlatformNeed.attribute(Attributes.BLOCK_BREAK_SPEED));
  private static final String HASTE_SLOT = "haste";

  private final Adaptation<?> owner;
  private final Map<UUID, Long> hasteUntil;
  private final Map<UUID, Integer> hasteLevel;

  public ExcavationHasteBoost(Adaptation<?> owner, Map<UUID, Long> hasteUntil, Map<UUID, Integer> hasteLevel) {
    this.owner = owner;
    this.hasteUntil = hasteUntil;
    this.hasteLevel = hasteLevel;
  }

  /** Applies haste of {@code level} for {@code durationTicks}; true when this dig started a new window. */
  public boolean apply(PlayerRef p, BlockRef block, int level, int durationTicks) {
    AdaptAttributeService.get().applyTimed(p, owner.getId().toString(), HASTE_SLOT, Attributes.BLOCK_BREAK_SPEED,
        hasteAmount(level), AttributeOperation.ADD_MULTIPLIED_BASE, durationTicks);

    UUID id = p.uuid();
    long now = System.currentTimeMillis();
    Long until = hasteUntil.get(id);
    boolean onset = until == null || now >= until;
    hasteUntil.put(id, now + (durationTicks * 50L));
    HudStatus.duration(id, owner, now, durationTicks * 50L);
    if (onset) {
      hasteLevel.put(id, level);
      ExcavationHaste.onsetFx(owner, p.eyeLocation());
      return true;
    }

    Integer seen = hasteLevel.get(id);
    if (seen == null || level > seen) {
      hasteLevel.put(id, level);
      ExcavationHaste.tierUpFx(owner, block.center());
    }
    return false;
  }

  public static double hasteAmount(int level) {
    return 0.20D * Math.max(1, level);
  }
}
