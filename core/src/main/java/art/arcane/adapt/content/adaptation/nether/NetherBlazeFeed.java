package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

/** Blaze Leech's feeding: hunger, saturation and regeneration drawn from fire. */
public final class NetherBlazeFeed {
  private static final Id REGENERATION = Id.minecraft("regeneration");

  private NetherBlazeFeed() {
  }

  /** {@code defensive} is true when the player took the fire, false when they struck a burning target. */
  public static void feed(Adaptation<?> owner, PlayerRef p, double foodRestore, double saturationRestore, int regenTicks,
                          int regenAmplifier, boolean defensive) {
    p.setFoodLevel(Math.min(20, p.foodLevel() + (int) Math.round(foodRestore)));
    p.setSaturation(Math.min(20f, p.saturation() + (float) saturationRestore));
    p.addEffect(new EffectInstance(REGENERATION, regenTicks, regenAmplifier, true, false, true));
    NetherBlazeLeech.feedFx(owner, p, defensive);
  }
}
