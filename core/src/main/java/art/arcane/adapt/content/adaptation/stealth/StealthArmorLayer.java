package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.value.Id;

/** Ghost's Armor's layer: armor points that build up over time and all break on the next hit. */
public final class StealthArmorLayer {
  private static final String SLOT_ARMOR = "armor";
  private static final Id BYPASSES_ARMOR = Id.minecraft("bypasses_armor");

  private StealthArmorLayer() {
  }

  public static double nextArmorAmount(double current, double maximum, double increment) {
    double safeMaximum = clampArmor(maximum);
    double safeCurrent = Math.min(clampArmor(current), safeMaximum);
    double safeIncrement = Double.isFinite(increment) ? Math.max(0D, increment) : 0D;
    return Math.min(safeMaximum, safeCurrent + safeIncrement);
  }

  public static boolean isConsumableCharge(double storedArmor) {
    return storedArmor > 0.1D;
  }

  public static double clampArmor(double value) {
    return Double.isFinite(value) ? Math.max(0D, Math.min(20D, value)) : 0D;
  }

  /** Adds {@code increment} armor up to {@code maximum}, applies it and returns the new amount. */
  public static double charge(Adaptation<?> owner, PlayerRef player, double current, double maximum, double increment) {
    double amount = nextArmorAmount(current, maximum, increment);
    if (amount > 0D) {
      AdaptAttributeService.get().apply(player, owner.getName(), SLOT_ARMOR, Attributes.ARMOR, amount,
          AttributeOperation.ADD_VALUE);
    }
    return amount;
  }

  public static boolean bypassesArmor(EntityDamageEvent event) {
    return AdaptCore.platform().host().registries().isTagged(RegistryKind.DAMAGE_TYPE, BYPASSES_ARMOR, event.damageType());
  }

  public static void clear(Adaptation<?> owner, PlayerRef player) {
    AdaptAttributeService.get().remove(player, owner.getName(), SLOT_ARMOR, Attributes.ARMOR);
  }
}
