package art.arcane.adapt.content.adaptation.enchanting;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Item work shared by Quick-Click Enchant, Infusion Transfer, Tome Rebinding and Arcane Hand. */
public final class EnchantingBooks {
  public static final Id BOOK = Id.minecraft("book");
  public static final Id ENCHANTED_BOOK = Id.minecraft("enchanted_book");

  private EnchantingBooks() {
  }

  /** {@code merged} is the item's enchantments after the book; {@code leftover} is what stays on the book. */
  public record Merge(Map<Id, Integer> original, Map<Id, Integer> merged, Map<Id, Integer> added,
                      Map<Id, Integer> leftover, int power) {
    public boolean changed() {
      return !original.equals(merged);
    }
  }

  public static boolean isBookApplication(ItemRef current, ItemRef cursor) {
    return cursor != null
        && current != null
        && cursor.type().equals(ENCHANTED_BOOK)
        && !current.type().equals(BOOK)
        && !current.type().equals(ENCHANTED_BOOK)
        && !current.isEmpty()
        && current.amount() == 1
        && cursor.amount() == 1;
  }

  public static Merge merge(ItemRef item, Map<Id, Integer> bookEnchants) {
    ItemOps items = AdaptApi.platform().host().items();
    Map<Id, Integer> itemEnchants = new LinkedHashMap<>(item.enchantments());
    Map<Id, Integer> leftover = new LinkedHashMap<>(bookEnchants);
    Map<Id, Integer> merged = new LinkedHashMap<>(itemEnchants);
    Map<Id, Integer> added = new LinkedHashMap<>();
    int power = itemEnchants.values().stream().mapToInt((Integer i) -> i).sum();
    for (Id enchant : new ArrayList<>(leftover.keySet())) {
      if (itemEnchants.containsKey(enchant) || !items.canEnchant(enchant, item) || conflictsWithAny(merged.keySet(), enchant)) {
        continue;
      }
      int level = leftover.remove(enchant);
      power += level;
      merged.put(enchant, level);
      added.put(enchant, level);
    }
    return new Merge(itemEnchants, merged, added, leftover, power);
  }

  public static void writeEnchantments(ItemRef item, Map<Id, Integer> enchants) {
    if (item.type().equals(ENCHANTED_BOOK)) {
      writeStored(item, enchants);
      return;
    }
    new ArrayList<>(item.enchantments().keySet()).forEach(item::removeEnchantment);
    enchants.forEach(item::setEnchantment);
  }

  /** Empties the cursor when nothing is left on the book and returns true; otherwise leaves the trimmed book there. */
  public static boolean settleCursor(InventoryClickEvent e, ItemRef book, Map<Id, Integer> leftover) {
    if (leftover.isEmpty()) {
      e.setCursor(AdaptApi.platform().host().items().empty());
      return true;
    }
    if (!book.storedEnchantments().equals(leftover)) {
      writeStored(book, leftover);
      e.setCursor(book);
    }
    return false;
  }

  public static void showAdded(PlayerRef player, Map<Id, Integer> added) {
    if (added.isEmpty()) {
      return;
    }

    StringBuilder summary = new StringBuilder();
    added.forEach((Id enchant, Integer level) -> {
      if (summary.length() > 0) {
        summary.append(TextFormat.GRAY).append(", ");
      }
      summary.append(TextFormat.LIGHT_PURPLE).append("+ ").append(enchant.path()).append(' ').append(level);
    });
    HudStatus.show(player, summary.toString());
  }

  public static void writeStored(ItemRef book, Map<Id, Integer> stored) {
    new ArrayList<>(book.storedEnchantments().keySet()).forEach(book::removeStoredEnchantment);
    stored.forEach(book::setStoredEnchantment);
  }

  public static boolean isTransferBase(ItemRef item) {
    return item != null
        && !item.type().isAir()
        && !item.type().equals(ENCHANTED_BOOK)
        && !item.type().equals(BOOK);
  }

  public static Map<Id, Integer> readEnchants(ItemRef item) {
    if (item.type().equals(ENCHANTED_BOOK)) {
      return item.storedEnchantments();
    }
    return item.enchantments();
  }

  /** True when {@code enchant} conflicts with any of {@code present}, in either direction. */
  public static boolean conflictsWithAny(Collection<Id> present, Id enchant) {
    ItemOps items = AdaptApi.platform().host().items();
    for (Id existing : present) {
      if (items.enchantmentsConflict(existing, enchant) || items.enchantmentsConflict(enchant, existing)) {
        return true;
      }
    }
    return false;
  }

  /** The source's highest enchantment the base lacks, can take and holds nothing conflicting with, or null. */
  public static Id pickTransferEnchant(ItemRef base, ItemRef source) {
    Id best = null;
    int bestLevel = 0;
    Map<Id, Integer> present = base.enchantments();
    for (Map.Entry<Id, Integer> entry : readEnchants(source).entrySet()) {
      Id enchant = entry.getKey();
      if (present.containsKey(enchant) || !AdaptApi.platform().host().items().canEnchant(enchant, base)
          || conflictsWithAny(present.keySet(), enchant)) {
        continue;
      }
      if (entry.getValue() > bestLevel) {
        best = enchant;
        bestLevel = entry.getValue();
      }
    }
    return best;
  }

  public static int transferLevel(ItemRef source, Id enchant) {
    return Math.max(1, Math.min(readEnchants(source).getOrDefault(enchant, 1),
        AdaptApi.platform().host().registries().enchantmentMaxLevel(enchant)));
  }

  public static ItemRef reduceSource(ItemRef source, Id transfer) {
    ItemRef reduced = source.copy();
    if (reduced.type().equals(ENCHANTED_BOOK)) {
      reduced.removeStoredEnchantment(transfer);
      if (reduced.storedEnchantments().isEmpty()) {
        return AdaptApi.platform().host().items().create(BOOK, reduced.amount());
      }
      return reduced;
    }
    reduced.removeEnchantment(transfer);
    return reduced;
  }

  public static boolean isMultiEnchantBook(ItemRef item) {
    if (item == null || !item.type().equals(ENCHANTED_BOOK) || item.amount() != 1) {
      return false;
    }
    return isSplittableBook(item.type(), item.amount(), item.storedEnchantments().size());
  }

  public static boolean isSplittableBook(Id material, int amount, int storedEnchantCount) {
    return material.equals(ENCHANTED_BOOK) && amount == 1 && storedEnchantCount >= 2;
  }

  public static int normalizedStoredLevel(int storedLevel) {
    return Math.max(1, storedLevel);
  }

  /** One single-enchantment book per stored enchantment; with {@code lossChance} one of them may be lost. */
  public static List<ItemRef> splitBook(ItemRef book, double lossChance) {
    List<Map.Entry<Id, Integer>> enchants = new ArrayList<>(book.storedEnchantments().entrySet());
    if (ThreadLocalRandom.current().nextDouble() < lossChance && enchants.size() > 1) {
      enchants.remove(ThreadLocalRandom.current().nextInt(enchants.size()));
    }
    List<ItemRef> books = new ArrayList<>(enchants.size());
    for (Map.Entry<Id, Integer> entry : enchants) {
      ItemRef single = AdaptApi.platform().host().items().create(ENCHANTED_BOOK);
      single.setStoredEnchantment(entry.getKey(), normalizedStoredLevel(entry.getValue()));
      books.add(single);
    }
    return books;
  }

  /** Turns the dropped book into the first split book and throws the rest beside it. */
  public static void dropSplit(PlayerRef player, ItemEntityRef dropped, List<ItemRef> books) {
    dropped.setItem(books.getFirst());
    for (int index = 1; index < books.size(); index++) {
      ItemEntityRef splitItem = player.world().dropItem(dropped.location().position(), books.get(index));
      splitItem.setVelocity(dropped.velocity());
      splitItem.setPickupDelay(dropped.pickupDelay());
      splitItem.setThrower(player.uuid());
    }
  }

  public static boolean spendLevels(PlayerRef player, int amount) {
    if (player == null || amount <= 0 || player.level() < amount) {
      return false;
    }
    player.giveExpLevels(-amount);
    return true;
  }
}
