package art.arcane.adapt.content.adaptation.enchanting;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/** Pays lapis and levels, then reseeds the player's enchanting offers; rolls the payment back when the reseed fails. */
public final class EnchantingRerolls {
  public static final Id ENCHANTING_TABLE = Id.minecraft("enchanting_table");
  private static final Id LAPIS_LAZULI = Id.minecraft("lapis_lazuli");

  private EnchantingRerolls() {
  }

  public enum Outcome {
    UNPAID,
    FAILED,
    REROLLED
  }

  public static Outcome reroll(Adaptation<?> owner, PlayerRef p, int lapisCost, int xpLevelCost) {
    Reservation reservation = reserve(owner, p, lapisCost);
    if (reservation == null) {
      return Outcome.UNPAID;
    }
    p.setLevel(Math.max(0, p.level() - xpLevelCost));
    if (!setSeed(p, ThreadLocalRandom.current().nextInt())) {
      refundFailedReroll(owner, p, reservation, AbilityRefundReason.ACTIVATION_FAILED);
      return Outcome.FAILED;
    }
    if (!acceptsDeferredSettlement(reservation.defaultLapisConsumed(), owner.settleCost(reservation.charge().activationId()))) {
      if (!setSeed(p, reservation.previousSeed())) {
        AdaptLog.warn("Offer Reroll could not restore the previous enchantment seed for " + p.name() + ".");
      }
      refundFailedReroll(owner, p, reservation, AbilityRefundReason.CHARGE_ROLLBACK);
      return Outcome.FAILED;
    }
    return Outcome.REROLLED;
  }

  public static boolean hasLapis(PlayerRef p, int amount) {
    int found = 0;
    PlayerInventoryRef inventory = p.inventory();
    for (int slot = 0; slot < inventory.size(); slot++) {
      ItemRef stack = inventory.item(slot);
      if (stack == null || !stack.type().equals(LAPIS_LAZULI)) {
        continue;
      }

      found += stack.amount();
      if (found >= amount) {
        return true;
      }
    }

    return false;
  }

  public static boolean acceptsDeferredSettlement(boolean defaultLapisConsumed, boolean settled) {
    return defaultLapisConsumed || settled;
  }

  private static Reservation reserve(Adaptation<?> owner, PlayerRef p, int amount) {
    int previousXpLevel = p.level();
    int previousSeed = p.enchantmentSeed();
    AtomicBoolean defaultLapisConsumed = new AtomicBoolean();
    AbilityCharge charge = owner.payItemCostDeferred(
        p,
        "lapis",
        AdaptCore.platform().host().items().create(LAPIS_LAZULI),
        amount,
        () -> {
          boolean consumed = takeLapis(p, amount);
          defaultLapisConsumed.set(consumed);
          return consumed;
        }
    );
    if (!charge.allowed()) {
      return null;
    }

    return new Reservation(charge, amount, defaultLapisConsumed.get(), previousXpLevel, previousSeed);
  }

  private static boolean takeLapis(PlayerRef p, int amount) {
    if (!hasLapis(p, amount)) {
      return false;
    }

    int need = amount;
    PlayerInventoryRef inventory = p.inventory();
    for (int slot = 0; slot < inventory.size(); slot++) {
      ItemRef stack = inventory.item(slot);
      if (stack == null || !stack.type().equals(LAPIS_LAZULI) || need <= 0) {
        continue;
      }

      int used = Math.min(stack.amount(), need);
      ItemRef remainder = stack.copy();
      remainder.setAmount(stack.amount() - used);
      inventory.setItem(slot, remainder.amount() <= 0 ? null : remainder);
      need -= used;
    }

    return need == 0;
  }

  private static void refundFailedReroll(Adaptation<?> owner, PlayerRef p, Reservation reservation, AbilityRefundReason reason) {
    boolean refunded = owner.refundCost(reservation.charge().activationId(), reason);
    if (reservation.charge().defaultCostSuppressed() && !refunded) {
      AdaptLog.warn("Offer Reroll could not resolve the deferred provider cost for " + p.name() + ".");
    }
    if (reservation.defaultLapisConsumed()) {
      refundDefaultLapis(p, reservation.lapisAmount());
    }
    p.setLevel(reservation.previousXpLevel());
  }

  private static void refundDefaultLapis(PlayerRef p, int amount) {
    try {
      List<ItemRef> leftovers = p.inventory().addItem(AdaptCore.platform().host().items().create(LAPIS_LAZULI, amount));
      leftovers.forEach((ItemRef item) -> p.world().dropItemNaturally(p.location().position(), item));
    } catch (Throwable t) {
      AdaptLog.warn("Offer Reroll could not return reserved lapis to " + p.name() + ".");
      AdaptLog.error(t);
    }
  }

  private static boolean setSeed(PlayerRef p, int seed) {
    try {
      p.setEnchantmentSeed(seed);
      return true;
    } catch (Throwable t) {
      AdaptLog.warn("Offer Reroll could not update the enchantment seed for " + p.name() + ".");
      AdaptLog.error(t);
      return false;
    }
  }

  private record Reservation(AbilityCharge charge, int lapisAmount, boolean defaultLapisConsumed, int previousXpLevel,
                             int previousSeed) {
  }
}
