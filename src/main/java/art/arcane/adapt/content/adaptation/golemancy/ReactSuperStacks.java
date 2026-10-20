package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.golem.GolemContext;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

final class ReactSuperStacks {
  static final String PLUGIN = "React";
  static final String MARKER = "REACT SUPER STACK";

  private ReactSuperStacks() {
  }

  static boolean isSuperStack(ItemStack stack) {
    return marked(stack) != null;
  }

  static boolean hollow(ItemStack stack) {
    BundleMeta meta = marked(stack);
    return meta != null && (stack.getAmount() != 1 || validItems(meta).isEmpty());
  }

  static List<ItemStack> contents(ItemStack stack) {
    BundleMeta meta = marked(stack);
    return meta == null || stack.getAmount() != 1 ? List.of() : validItems(meta);
  }

  static boolean accepts(ItemStack stack, Predicate<Material> types) {
    BundleMeta meta = marked(stack);
    if (meta == null) {
      return types.test(stack.getType());
    }
    if (stack.getAmount() != 1) {
      return false;
    }
    for (ItemStack content : validItems(meta)) {
      if (types.test(content.getType())) {
        return true;
      }
    }
    return false;
  }

  static Unpacked unpack(GolemContext golem, ItemStack bundle, Predicate<Material> accepts) {
    List<ItemStack> contents = contents(bundle);
    List<ItemStack> leftovers = new ArrayList<>(contents.size());
    int carried = 0;
    for (ItemStack content : contents) {
      if (golem.room() <= 0 || !accepts.test(content.getType())) {
        leftovers.add(content);
        continue;
      }
      int amount = content.getAmount();
      Optional<ItemStack> refused = golem.carry(content);
      carried += amount - refused.map(ItemStack::getAmount).orElse(0);
      refused.ifPresent(leftovers::add);
    }
    return new Unpacked(carried, leftovers);
  }

  static Optional<ItemStack> rebuild(ItemStack bundle, List<ItemStack> leftovers) {
    if (leftovers.isEmpty()) {
      return Optional.empty();
    }
    ItemStack rebuilt = bundle.clone();
    if (!(rebuilt.getItemMeta() instanceof BundleMeta meta)) {
      return Optional.empty();
    }
    boolean applied;
    try {
      meta.setItems(leftovers);
      applied = rebuilt.setItemMeta(meta);
    } catch (RuntimeException failure) {
      Adapt.warn("A React super stack could not be rebuilt around its " + leftovers.size() + " remaining stacks.", failure);
      return Optional.empty();
    }
    return applied && isSuperStack(rebuilt) ? Optional.of(rebuilt) : Optional.empty();
  }

  private static BundleMeta marked(ItemStack stack) {
    if (stack == null || stack.getType() != Material.BUNDLE || !Bukkit.getPluginManager().isPluginEnabled(PLUGIN)) {
      return null;
    }
    if (!(stack.getItemMeta() instanceof BundleMeta meta)) {
      return null;
    }
    List<String> lore = meta.getLore();
    return lore != null && lore.size() == 1 && MARKER.equals(lore.getFirst()) ? meta : null;
  }

  private static List<ItemStack> validItems(BundleMeta meta) {
    List<ItemStack> items = meta.getItems();
    List<ItemStack> kept = new ArrayList<>(items.size());
    for (ItemStack item : items) {
      if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
        kept.add(item.clone());
      }
    }
    return kept;
  }

  record Unpacked(int carried, List<ItemStack> leftovers) {
  }
}
