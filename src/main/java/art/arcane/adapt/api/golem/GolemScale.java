package art.arcane.adapt.api.golem;

import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.reflect.registries.Attributes;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.util.ArrayList;
import java.util.List;

public final class GolemScale {
  public static final NamespacedKey MODIFIER_KEY = new NamespacedKey("adapt", "golem_scale");
  public static final NamespacedKey RITUAL_KEY = new NamespacedKey("adapt", "golem_ritual_scale");
  public static final double MIN_SCALE = 0.0625D;
  public static final double MAX_SCALE = 16D;
  private static final double EPSILON = 1.0e-9D;

  private GolemScale() {
  }

  public static double clampScale(double scale) {
    return Double.isFinite(scale) ? Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale)) : 1D;
  }

  public static double modifierAmount(double target) {
    return clampScale(target) - 1D;
  }

  public static double stepAmount(double target, double fraction) {
    double progress = Double.isFinite(fraction) ? Math.max(0D, Math.min(1D, fraction)) : 0D;
    return modifierAmount(target) * progress;
  }

  public static double target(GolemBody body) {
    return GolemancyRuntime.settings().bodyScale(body);
  }

  public static void stepTo(LivingEntity entity, double fraction) {
    AttributeInstance instance = scaleOf(entity);
    if (instance == null) {
      return;
    }
    GolemBody.of(entity).ifPresent(body -> setStepModifier(instance, stepAmount(target(body), fraction)));
  }

  public static void clearStep(LivingEntity entity) {
    AttributeInstance instance = scaleOf(entity);
    if (instance != null) {
      clearStepModifier(instance);
    }
  }

  static AttributeInstance scaleOf(LivingEntity entity) {
    Attribute scale = Attributes.SCALE;
    return scale == null ? null : entity.getAttribute(scale);
  }

  static void setModifier(AttributeInstance instance, double amount) {
    clearModifier(instance);
    if (Math.abs(amount) > EPSILON) {
      instance.addModifier(new AttributeModifier(MODIFIER_KEY, amount, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
    }
  }

  static void clearModifier(AttributeInstance instance) {
    removeKeyed(instance, MODIFIER_KEY);
  }

  static void setStepModifier(AttributeInstance instance, double amount) {
    clearStepModifier(instance);
    if (Math.abs(amount) > EPSILON) {
      PaperCompat.addTransientModifier(instance, new AttributeModifier(RITUAL_KEY, amount, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
    }
  }

  static void clearStepModifier(AttributeInstance instance) {
    removeKeyed(instance, RITUAL_KEY);
  }

  private static void removeKeyed(AttributeInstance instance, NamespacedKey key) {
    List<AttributeModifier> ours = new ArrayList<>(1);
    for (AttributeModifier modifier : instance.getModifiers()) {
      if (key.equals(modifier.getKey())) {
        ours.add(modifier);
      }
    }
    for (AttributeModifier modifier : ours) {
      instance.removeModifier(modifier);
    }
  }
}
