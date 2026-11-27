package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.platform.entity.PlayerRef;

/**
 * The blood reservoir an upgrade keeps for a player: healing past full health stored for later. Other parts of the
 * skill spend blood before the player's own health through {@link #draw} and {@link #payHealth}.
 */
public final class TragoulBlood {
  private static final double LOWEST_HEALTH = 1D;

  private TragoulBlood() {
  }

  /** An upgrade that keeps a blood reservoir. */
  public interface Reservoir {
    /** Takes up to {@code amount} blood from the player's reservoir and returns what it took. */
    double draw(PlayerRef player, double amount);

    /** Blood in the player's reservoir now. */
    double blood(PlayerRef player);

    /** Stores up to {@code amount} blood, within the reservoir's size, and returns what it stored. */
    double fill(PlayerRef player, double amount);
  }

  /** Stores up to {@code amount} blood in the reservoirs of {@code skill}, and returns what they stored. */
  public static double fill(Skill<?> skill, PlayerRef player, double amount) {
    if (skill == null || !(amount > 0D)) {
      return 0D;
    }
    double stored = 0D;
    for (Adaptation<?> upgrade : skill.getUpgrades()) {
      if (upgrade instanceof Reservoir reservoir && stored < amount) {
        stored += Math.max(0D, reservoir.fill(player, amount - stored));
      }
    }
    return stored;
  }

  /** Takes up to {@code amount} blood from every reservoir of {@code owner}'s skill, and returns what it took. */
  public static double draw(Adaptation<?> owner, PlayerRef player, double amount) {
    if (owner.getSkill() == null || !(amount > 0D)) {
      return 0D;
    }
    double taken = 0D;
    for (Adaptation<?> upgrade : owner.getSkill().getUpgrades()) {
      if (upgrade instanceof Reservoir reservoir && taken < amount) {
        taken += Math.max(0D, reservoir.draw(player, amount - taken));
      }
    }
    return taken;
  }

  /** Blood the player holds across every reservoir of {@code owner}'s skill. */
  public static double blood(Adaptation<?> owner, PlayerRef player) {
    if (owner.getSkill() == null) {
      return 0D;
    }
    double blood = 0D;
    for (Adaptation<?> upgrade : owner.getSkill().getUpgrades()) {
      if (upgrade instanceof Reservoir reservoir) {
        blood += Math.max(0D, reservoir.blood(player));
      }
    }
    return blood;
  }

  /**
   * Pays {@code amount} health, blood first and then the player's own health, never leaving them below 1 HP. False, and
   * nothing paid, when blood and health together cannot cover it.
   */
  public static boolean payHealth(Adaptation<?> owner, PlayerRef player, double amount) {
    if (!(amount > 0D)) {
      return true;
    }
    double spareHealth = Math.max(0D, player.health() - LOWEST_HEALTH);
    if (blood(owner, player) + spareHealth < amount) {
      return false;
    }
    double rest = amount - draw(owner, player, amount);
    if (rest > 0D) {
      player.setHealth(Math.max(LOWEST_HEALTH, player.health() - rest));
    }
    return true;
  }
}
