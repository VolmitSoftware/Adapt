package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

final class SorterVariants {
  private static final String NONE = "";
  private static final String SEPARATOR = ",";
  private static final Id ENCHANTED_BOOK = Id.minecraft("enchanted_book");

  private SorterVariants() {
  }

  static String of(ItemRef item) {
    if (!SorterRules.precise(item.type())) {
      return NONE;
    }
    if (item.type().equals(ENCHANTED_BOOK)) {
      return keys(item.storedEnchantments().keySet());
    }
    Optional<PotionContents> potion = item.potionContents();
    Id type = potion.map(PotionContents::potion).orElse(null);
    return type == null ? NONE : type.toString();
  }

  static String keys(Collection<Id> entries) {
    List<String> keys = new ArrayList<>(entries.size());
    for (Id entry : entries) {
      keys.add(entry.toString());
    }
    keys.sort(null);
    return String.join(SEPARATOR, keys);
  }
}
