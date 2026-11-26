package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

/** The swim boost and Dolphin's Grace of Dolphin's Grace, owned by the calling adaptation. */
public final class SeaborneSwim {
  public static final PlatformNeed NEED = PlatformNeed.attribute(Attributes.WATER_MOVEMENT_EFFICIENCY);
  private static final String ATTRIBUTE_SLOT = "swim";
  private static final int GRACE_AMPLIFIER = 0;
  private static final int GRACE_BASE_DURATION_TICKS = 20;
  private static final int GRACE_MAX_LINGER_TICKS = 60;
  private static final Id DOLPHINS_GRACE = Id.minecraft("dolphins_grace");
  private static final Id DEPTH_STRIDER = Id.minecraft("depth_strider");

  private SeaborneSwim() {
  }

  public static boolean wearsDepthStrider(PlayerRef player) {
    return player.inventory().armor(EquipmentSlot.FEET).enchantmentLevel(DEPTH_STRIDER) > 0;
  }

  public static void applyBoost(Adaptation<?> owner, PlayerRef player, double amount) {
    AdaptAttributeService.get().apply(player, owner.getName(), ATTRIBUTE_SLOT, Attributes.WATER_MOVEMENT_EFFICIENCY,
        amount, AttributeOperation.ADD_VALUE);
  }

  public static void removeBoost(Adaptation<?> owner, PlayerRef player) {
    AdaptAttributeService.get().remove(player, owner.getName(), ATTRIBUTE_SLOT, Attributes.WATER_MOVEMENT_EFFICIENCY);
  }

  public static void applyGrace(PlayerRef player, int durationTicks) {
    if (player.isSwimming()) {
      grantGrace(player, durationTicks);
    }
  }

  /** Dolphin's Grace for {@code durationTicks}, unless a stronger or longer grace is already running. */
  public static boolean grantGrace(PlayerRef player, int durationTicks) {
    if (durationTicks <= 0) {
      return false;
    }
    EffectInstance current = player.effect(DOLPHINS_GRACE);
    boolean hasCurrent = current != null;
    int currentAmplifier = hasCurrent ? current.amplifier() : 0;
    int currentDurationTicks = hasCurrent ? current.durationTicks() : 0;
    if (!shouldApplyGrace(hasCurrent, currentAmplifier, currentDurationTicks, durationTicks)) {
      return false;
    }
    player.addEffect(new EffectInstance(DOLPHINS_GRACE, durationTicks, GRACE_AMPLIFIER, true, false, true));
    return true;
  }

  public static double waterEfficiencyAmount(int level, int maxLevel) {
    if (level <= 0) {
      return 0D;
    }
    return Math.min(1D, (double) level / Math.max(1, maxLevel));
  }

  public static int graceDurationTicks(int level, int maxLevel) {
    if (level <= 0) {
      return 0;
    }
    double levelPercent = Math.min(1D, (double) level / Math.max(1, maxLevel));
    return GRACE_BASE_DURATION_TICKS + (int) Math.round(levelPercent * GRACE_MAX_LINGER_TICKS);
  }

  public static boolean shouldApplyGrace(boolean hasCurrent, int currentAmplifier, int currentDurationTicks, int durationTicks) {
    if (!hasCurrent) {
      return true;
    }
    if (currentDurationTicks < 0) {
      return false;
    }
    return currentAmplifier < GRACE_AMPLIFIER || currentDurationTicks <= durationTicks;
  }
}
