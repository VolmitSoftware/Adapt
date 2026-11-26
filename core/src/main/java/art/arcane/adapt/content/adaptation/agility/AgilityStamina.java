package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.platform.entity.PlayerRef;

/** Hunger costs paid by agility moves: saturation first, then whole food points for the rest. */
public final class AgilityStamina {
  private AgilityStamina() {
  }

  public static void spend(PlayerRef p, double cost) {
    double saturation = p.saturation();
    if (saturation >= cost) {
      p.setSaturation((float) (saturation - cost));
      return;
    }

    p.setSaturation(0F);
    int foodCost = (int) Math.ceil(cost - saturation);
    p.setFoodLevel(Math.max(0, p.foodLevel() - foodCost));
  }
}
