package art.arcane.adapt.content.adaptation.enchanting;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.EnchantingMessages;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;

import java.util.Map;

/** Collected XP charges an item; a full charge raises one of its enchantments a level, within vanilla caps. */
public final class EnchantingEchoes {
  private static final DataKey<Integer> CHARGE_KEY = DataKey.adapt("echo-charge", DataCodecs.INT);

  private EnchantingEchoes() {
  }

  /** {@code upgraded} is null when the charge only grew; nothing changes when the item has no enchantment to raise. */
  public record Charge(boolean changed, Id upgraded) {
  }

  /** Charges {@code item} in place; books raise stored enchantments, other items their own. */
  public static Charge charge(ItemRef item, int gain, int threshold) {
    Map<Id, Integer> enchants = EnchantingBooks.readEnchants(item);
    Id upgradeable = findUpgradeable(enchants);
    if (upgradeable == null) {
      return new Charge(false, null);
    }

    int charge = item.data().getOrDefault(CHARGE_KEY, 0) + gain;
    if (charge < threshold) {
      item.data().set(CHARGE_KEY, Math.min(charge, threshold));
      return new Charge(true, null);
    }

    int raised = Math.min(maxLevel(upgradeable), enchants.get(upgradeable) + 1);
    if (item.type().equals(EnchantingBooks.ENCHANTED_BOOK)) {
      item.removeStoredEnchantment(upgradeable);
      item.setStoredEnchantment(upgradeable, raised);
    } else {
      item.removeEnchantment(upgradeable);
      item.setEnchantment(upgradeable, raised);
    }
    item.data().set(CHARGE_KEY, charge - threshold);
    return new Charge(true, upgradeable);
  }

  public static void announce(PlayerRef p, Id upgraded) {
    AdaptHud.actionBar(p, C.LIGHT_PURPLE + "+1 " + upgraded.path().replace('_', ' ')
        + C.GRAY + " " + AdaptLanguage.text(EnchantingMessages.ECHO_OF_KNOWLEDGE_UPGRADED));
  }

  private static Id findUpgradeable(Map<Id, Integer> enchants) {
    for (Map.Entry<Id, Integer> entry : enchants.entrySet()) {
      if (entry.getValue() < maxLevel(entry.getKey())) {
        return entry.getKey();
      }
    }

    return null;
  }

  private static int maxLevel(Id enchantment) {
    return AdaptCore.platform().host().registries().enchantmentMaxLevel(enchantment);
  }
}
