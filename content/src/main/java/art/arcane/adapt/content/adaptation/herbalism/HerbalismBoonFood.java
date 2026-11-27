package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.PlayerRef;

/**
 * Hunger a mutation boon restored, kept on the player so it survives a restart. Barter can buy that hunger with health
 * (TragOul's fuel), so it must never be turned back into health; the boon food counts as the last food eaten and
 * shrinks as the food bar drops below it.
 */
public final class HerbalismBoonFood {
  private static final DataKey<Integer> BOON_FOOD = DataKey.adapt("herbalism_boon_food", DataCodecs.INT);

  private HerbalismBoonFood() {
  }

  public static void restored(PlayerRef player, int food) {
    if (food > 0) {
      set(player, Math.min(player.foodLevel(), held(player) + food));
    }
  }

  /** Food points that came from eating rather than from a boon. */
  public static int eaten(PlayerRef player) {
    return Math.max(0, player.foodLevel() - Math.min(player.foodLevel(), held(player)));
  }

  /** Shrinks the boon food to what a food bar of {@code foodLevel} can still hold. */
  public static void settle(PlayerRef player, int foodLevel) {
    if (held(player) > foodLevel) {
      set(player, foodLevel);
    }
  }

  private static int held(PlayerRef player) {
    return Math.max(0, player.data().getOrDefault(BOON_FOOD, 0));
  }

  private static void set(PlayerRef player, int food) {
    if (food <= 0) {
      player.data().remove(BOON_FOOD);
    } else {
      player.data().set(BOON_FOOD, food);
    }
  }
}
