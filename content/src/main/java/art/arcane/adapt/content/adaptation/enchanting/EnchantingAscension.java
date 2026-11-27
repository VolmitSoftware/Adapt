package art.arcane.adapt.content.adaptation.enchanting;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Soulforged's Ascension: an enchantment grown to its vanilla maximum rises one level past it. The item records which
 * enchantments ascended; the extra level is written on the stack only while a holder carries it ("awake") and taken
 * back off otherwise ("dormant"). An entry whose level no longer matches either state is dropped, so a ground or
 * re-enchanted item never wakes again.
 */
public final class EnchantingAscension {
  private static final DataKey<String> ASCENDED_KEY = DataKey.adapt("ascended", DataCodecs.STRING);
  private static final DataKey<String> GROWN_KEY = DataKey.adapt("soulforged-grown", DataCodecs.STRING);

  private EnchantingAscension() {
  }

  public static boolean hasAscension(@Nullable ItemRef item) {
    return item != null && !item.isEmpty() && item.data().has(ASCENDED_KEY);
  }

  public static Set<Id> ascended(@Nullable ItemRef item) {
    return read(item, ASCENDED_KEY);
  }

  /** Records that Soulforged grew {@code enchant} on {@code item} to its vanilla maximum. */
  public static void markGrown(ItemRef item, Id enchant) {
    if (EnchantingBooks.readEnchants(item).getOrDefault(enchant, 0) >= maxLevel(enchant)) {
      Set<Id> grown = new LinkedHashSet<>(read(item, GROWN_KEY));
      grown.add(enchant);
      write(item, GROWN_KEY, grown);
    }
  }

  /** The first enchantment that grew to its maximum, may ascend and has not, or null. */
  public static @Nullable Id candidate(ItemRef item, Collection<String> ascendable) {
    Set<Id> grown = read(item, GROWN_KEY);
    Set<Id> done = ascended(item);
    for (Map.Entry<Id, Integer> entry : item.enchantments().entrySet()) {
      Id enchant = entry.getKey();
      int max = maxLevel(enchant);
      if (max > 1 && entry.getValue() == max && grown.contains(enchant) && !done.contains(enchant)
          && ascendable.contains(enchant.toString())) {
        return enchant;
      }
    }
    return null;
  }

  /** Raises {@code enchant} one past its maximum on {@code item} and records it, awake. */
  public static void ascend(ItemRef item, Id enchant) {
    Set<Id> done = new LinkedHashSet<>(ascended(item));
    done.add(enchant);
    write(item, ASCENDED_KEY, done);
    item.setEnchantment(enchant, maxLevel(enchant) + 1);
    relore(item, done);
  }

  /**
   * Writes every ascended enchantment awake (one past maximum) or dormant (at maximum), and drops entries whose level
   * matches neither. Returns whether the stack changed.
   */
  public static boolean settle(ItemRef item, boolean awake) {
    Set<Id> done = ascended(item);
    if (done.isEmpty()) {
      return false;
    }
    boolean changed = false;
    Set<Id> kept = new LinkedHashSet<>();
    for (Id enchant : done) {
      int max = maxLevel(enchant);
      int current = item.enchantmentLevel(enchant);
      if (current != max && current != max + 1) {
        changed = true;
        continue;
      }
      kept.add(enchant);
      int wanted = awake ? max + 1 : max;
      if (current != wanted) {
        item.setEnchantment(enchant, wanted);
        changed = true;
      }
    }
    if (kept.size() != done.size()) {
      write(item, ASCENDED_KEY, kept);
      relore(item, kept);
    }
    return changed;
  }

  /** Clears the record and lore of every ascension on {@code item}, as a grindstone does. Returns whether it had any. */
  public static boolean strip(ItemRef item) {
    if (!hasAscension(item)) {
      return false;
    }
    for (Id enchant : ascended(item)) {
      int max = maxLevel(enchant);
      if (item.enchantmentLevel(enchant) > max) {
        item.setEnchantment(enchant, max);
      }
    }
    item.data().remove(ASCENDED_KEY);
    item.data().remove(GROWN_KEY);
    relore(item, Set.of());
    return true;
  }

  /**
   * Lowers to its maximum any enchantment on {@code result} above its maximum that the item itself has not ascended,
   * so an anvil cannot copy an ascended level onto other gear. Returns whether it lowered any.
   */
  public static boolean clampForeign(ItemRef result) {
    Set<Id> own = ascended(result);
    boolean changed = false;
    for (Map.Entry<Id, Integer> entry : new ArrayList<>(result.enchantments().entrySet())) {
      int max = maxLevel(entry.getKey());
      if (entry.getValue() > max && !own.contains(entry.getKey())) {
        result.setEnchantment(entry.getKey(), max);
        changed = true;
      }
    }
    return changed;
  }

  private static void relore(ItemRef item, Set<Id> ascended) {
    String prefix = TextFormat.LIGHT_PURPLE + Text.key("adapt.upgrade.enchanting.soulforged.ascended").resolve() + " ";
    List<String> lore = new ArrayList<>();
    for (String line : item.lore()) {
      if (!line.startsWith(prefix)) {
        lore.add(line);
      }
    }
    for (Id enchant : ascended) {
      lore.add(prefix + enchant.path().replace('_', ' '));
    }
    item.setLore(lore);
  }

  private static Set<Id> read(@Nullable ItemRef item, DataKey<String> key) {
    String raw = item == null || item.isEmpty() ? null : item.data().get(key);
    if (raw == null || raw.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(raw.split(",")).map(Id::parse).collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static void write(ItemRef item, DataKey<String> key, Set<Id> ids) {
    if (ids.isEmpty()) {
      item.data().remove(key);
      return;
    }
    item.data().set(key, ids.stream().map(Id::toString).collect(Collectors.joining(",")));
  }

  private static int maxLevel(Id enchant) {
    return AdaptApi.platform().host().registries().enchantmentMaxLevel(enchant);
  }
}
