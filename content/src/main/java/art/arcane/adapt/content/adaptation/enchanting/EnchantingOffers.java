package art.arcane.adapt.content.adaptation.enchanting;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.event.inventory.EnchantOffer;

import java.util.List;

/** Enchanting-table offer work shared by Bookshelf Attunement, Rune Sight and Fate Weaver. */
public final class EnchantingOffers {
  private EnchantingOffers() {
  }

  public record Reveal(String message, int revealed) {
  }

  /** Raises every offer as if {@code power} more bookshelves stood around the table; true when an offer changed. */
  public static boolean boost(List<EnchantOffer> offers, int power) {
    if (offers == null) {
      return false;
    }
    boolean boosted = false;
    for (EnchantOffer offer : offers) {
      if (offer == null) {
        continue;
      }
      int newCost = Math.min(30, offer.cost() + power);
      int newLevel = Math.min(AdaptApi.platform().host().registries().enchantmentMaxLevel(offer.enchantment()),
          offer.level() + Math.max(0, power / 3));
      offer.setCost(newCost);
      offer.setLevel(Math.max(1, newLevel));
      boosted = true;
    }
    return boosted;
  }

  /** The first {@code depth} offers as one action-bar line. */
  public static Reveal reveal(List<EnchantOffer> offers, int depth) {
    StringBuilder message = new StringBuilder();
    int revealed = 0;
    for (int i = 0; i < offers.size() && revealed < depth; i++) {
      EnchantOffer offer = offers.get(i);
      if (offer == null || offer.enchantment() == null) {
        continue;
      }
      if (message.length() > 0) {
        message.append(TextFormat.GRAY).append("  |  ");
      }
      message.append(TextFormat.LIGHT_PURPLE)
          .append(offer.enchantment().path().replace('_', ' '))
          .append(' ')
          .append(offer.level())
          .append(TextFormat.DARK_GRAY)
          .append(" (")
          .append(offer.cost())
          .append(')');
      revealed++;
    }
    return new Reveal(message.toString(), revealed);
  }
}
