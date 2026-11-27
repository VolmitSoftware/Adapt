package art.arcane.adapt.content.golem;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.value.Id;

public final class GolemScale {
  public static final Id MODIFIER_KEY = Id.of("adapt", "golem_scale");
  public static final Id RITUAL_KEY = Id.of("adapt", "golem_ritual_scale");
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

  public static double transitionStep(double from, double to, double fraction) {
    double progress = Double.isFinite(fraction) ? Math.max(0D, Math.min(1D, fraction)) : 0D;
    return (clampScale(from) - clampScale(to)) * (1D - progress);
  }

  public static double target(GolemBody body) {
    return GolemancyRuntime.settings().bodyScale(body);
  }

  public static void stepTo(LivingRef entity, double fraction) {
    GolemAttribute instance = scaleOf(entity);
    if (instance == null) {
      return;
    }
    GolemBody.of(entity).ifPresent(body -> setStepModifier(instance, stepAmount(target(body), fraction)));
  }

  public static void stepTo(LivingRef entity, double from, double to, double fraction) {
    GolemAttribute instance = scaleOf(entity);
    if (instance != null) {
      stepTo(instance, from, to, fraction);
    }
  }

  public static void clearStep(LivingRef entity) {
    GolemAttribute instance = scaleOf(entity);
    if (instance != null) {
      clearStepModifier(instance);
    }
  }

  static GolemAttribute scaleOf(LivingRef entity) {
    return GolemAttribute.of(entity, Attributes.SCALE);
  }

  static void stepTo(GolemAttribute instance, double from, double to, double fraction) {
    double settled = modifierAmount(to);
    if (!settledAt(instance, settled)) {
      setModifier(instance, settled);
    }
    setStepModifier(instance, transitionStep(from, to, fraction));
  }

  static void setModifier(GolemAttribute instance, double amount) {
    clearModifier(instance);
    if (Math.abs(amount) > EPSILON) {
      instance.add(new AttributeMod(MODIFIER_KEY, amount, AttributeOperation.ADD_VALUE));
    }
  }

  static void clearModifier(GolemAttribute instance) {
    instance.remove(MODIFIER_KEY);
  }

  static void setStepModifier(GolemAttribute instance, double amount) {
    clearStepModifier(instance);
    if (Math.abs(amount) > EPSILON) {
      instance.addTransient(new AttributeMod(RITUAL_KEY, amount, AttributeOperation.ADD_VALUE));
    }
  }

  static void clearStepModifier(GolemAttribute instance) {
    instance.remove(RITUAL_KEY);
  }

  private static boolean settledAt(GolemAttribute instance, double amount) {
    boolean present = false;
    for (AttributeMod modifier : instance.modifiers()) {
      if (!MODIFIER_KEY.equals(modifier.key())) {
        continue;
      }
      if (present || Math.abs(modifier.amount() - amount) > EPSILON) {
        return false;
      }
      present = true;
    }
    return present == Math.abs(amount) > EPSILON;
  }
}
