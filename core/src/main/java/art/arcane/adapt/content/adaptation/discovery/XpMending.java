package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.ComponentPort;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.item.ItemRef;

/** Better Mending's sneak-left-click that spends vanilla XP on the Mending item in the main hand. */
public final class XpMending {
  private XpMending() {
  }

  /** The rates of one level of Better Mending. */
  public record Rates(double repairPerXp, int maxXpSpend, int cooldownTicks) {
  }

  public enum Outcome {
    NONE,
    FIZZLED,
    MENDED
  }

  /** What one press did: the damage before and after, when it mended. */
  public record Press(Outcome outcome, int damageBefore, int damageAfter) {
    static final Press NONE = new Press(Outcome.NONE, 0, 0);
    static final Press FIZZLED = new Press(Outcome.FIZZLED, 0, 0);
  }

  /** Whether the event is a main-hand left click, the gesture Better Mending listens for. */
  public static boolean isPress(PlayerInteractEvent e) {
    InteractAction action = e.action();
    return e.hand() == Hand.MAIN_HAND && (action == InteractAction.LEFT_CLICK_AIR || action == InteractAction.LEFT_CLICK_BLOCK);
  }

  /**
   * Mends the item in the main hand at {@code rates}, paying vanilla XP through {@code owner} above the player's XP
   * reserve and up to their repair limit, both read from {@code owner}'s preferences. On a mend the item goes on
   * cooldown and the event is cancelled.
   */
  public static Press mend(Adaptation<?> owner, PlayerRef p, Rates rates, PlayerInteractEvent e) {
    ItemRef hand = p.inventory().mainHand();
    if (!canMend(owner, hand) || p.hasCooldown(hand.type())) {
      return Press.NONE;
    }

    int availableXp = Math.max(0, experience().totalExperiencePoints(p) - owner.preference(p, DiscoveryBetterMending.XP_RESERVE).amount());
    if (availableXp <= 0) {
      return Press.FIZZLED;
    }

    double repairPerXp = rates.repairPerXp();
    int maxXpSpend = Math.min(rates.maxXpSpend(), availableXp);
    int currentDamage = hand.damage();
    int maximumDurability = hand.maxDamage();
    int minimumDamage = (int) Math.floor(maximumDurability * (1D - owner.preference(p, DiscoveryBetterMending.REPAIR_LIMIT).multiplier()));
    int repairable = Math.max(0, currentDamage - minimumDamage);
    int xpNeeded = (int) Math.ceil(repairable / repairPerXp);
    int xpSpent = Math.min(maxXpSpend, xpNeeded);
    if (xpSpent <= 0) {
      return Press.FIZZLED;
    }

    int repaired = Math.max(1, (int) Math.round(xpSpent * repairPerXp));
    int newDamage = Math.max(minimumDamage, currentDamage - repaired);

    if (!owner.payExperienceCost(p, "experience", xpSpent, () -> spendExperiencePoints(p, xpSpent))) {
      return Press.NONE;
    }

    hand.setDamage(newDamage);
    p.inventory().setMainHand(hand);
    p.setCooldown(hand.type(), rates.cooldownTicks());
    AbilityGlyphs.cooldown(p, owner, rates.cooldownTicks() * 50L);
    e.setCancelled(true);
    return new Press(Outcome.MENDED, currentDamage, newDamage);
  }

  private static boolean canMend(Adaptation<?> owner, ItemRef hand) {
    if (!owner.isItem(hand) || AdaptCore.platform().host().items().maxDurability(hand.type()) <= 0) {
      return false;
    }

    if (hand.enchantmentLevel(DiscoveryBetterMending.MENDING) <= 0) {
      return false;
    }

    return hand.damage() > 0;
  }

  static boolean spendExperiencePoints(PlayerRef player, int amount) {
    if (player == null || amount <= 0) {
      return false;
    }

    ComponentPort experience = experience();
    int available = experience.totalExperiencePoints(player);
    if (available < amount) {
      return false;
    }

    experience.setExperiencePoints(player, available - amount);
    return true;
  }

  private static ComponentPort experience() {
    return AdaptPorts.get(ComponentPort.class, ComponentPort.NONE);
  }
}
