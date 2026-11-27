package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.engine.integration.ReactSuperStackPort;
import art.arcane.adapt.platform.item.ItemRef;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads and rebuilds React super stacks: bundles whose only lore line is React's marker, while React is enabled. */
public final class BukkitReactSuperStackPort implements ReactSuperStackPort {
  static final String PLUGIN = "React";
  static final String MARKER = "REACT SUPER STACK";

  @Override
  public boolean isSuperStack(ItemRef stack) {
    return stack != null && !stack.isEmpty() && marked(BukkitRefs.item(stack)) != null;
  }

  @Override
  public List<ItemRef> items(ItemRef stack) {
    BundleMeta meta = stack == null || stack.isEmpty() ? null : marked(BukkitRefs.item(stack));
    if (meta == null) {
      return List.of();
    }
    List<ItemRef> kept = new ArrayList<>();
    for (ItemStack item : meta.getItems()) {
      if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
        kept.add(BukkitRefs.item(item.clone()));
      }
    }
    return kept;
  }

  @Override
  public Optional<ItemRef> withItems(ItemRef stack, List<ItemRef> items) {
    ItemStack rebuilt = BukkitRefs.item(stack).clone();
    if (!(rebuilt.getItemMeta() instanceof BundleMeta meta)) {
      return Optional.empty();
    }
    List<ItemStack> contents = new ArrayList<>(items.size());
    for (ItemRef item : items) {
      contents.add(BukkitRefs.item(item));
    }
    boolean applied;
    try {
      meta.setItems(contents);
      applied = rebuilt.setItemMeta(meta);
    } catch (RuntimeException failure) {
      AdaptLog.warn("A React super stack could not be rebuilt around its " + items.size() + " remaining stacks.", failure);
      return Optional.empty();
    }
    return applied && marked(rebuilt) != null ? Optional.of(BukkitRefs.item(rebuilt)) : Optional.empty();
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
}
