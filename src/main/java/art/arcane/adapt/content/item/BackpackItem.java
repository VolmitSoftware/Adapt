/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.content.item;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.item.DataItem;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ItemsMessages;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.format.C;
import lombok.AllArgsConstructor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * The Adapt Backpack. A bundle-skinned item whose storage lives in its own
 * persistent data, never in the vanilla {@code bundle_contents} component, so
 * every vanilla bundle mechanic can be suppressed without ever touching what a
 * player has stored.
 *
 * <p>Two per-item storage models share one stack array:
 * <ul>
 *   <li>{@link Mode#SLOTS} - the array is slot indexed and may hold nulls. Every
 *   slot is one ordinary stack, so the container view always shows everything.
 *   <li>{@link Mode#BUNDLE} - the array is a compacted heap of distinct stacks
 *   with a vanilla-style weight budget. The view is a projection of the heap.
 * </ul>
 */
public class BackpackItem implements DataItem<BackpackItem.Data> {
  public static final BackpackItem io = new BackpackItem();

  public static final int MIN_CAPACITY = 9;
  public static final int MAX_CAPACITY = 54;
  public static final int CAPACITY_STEP = 9;
  public static final int NAV_ROW = 9;
  static final int LORE_WRAP_WIDTH = 38;

  /** Weight units per stack. Mirrors vanilla's one-stack bundle budget. */
  public static final int STACK_WEIGHT = 64;
  public static final int DEFAULT_MAX_STORED_BYTES = 262144;

  static final int NEST_SCAN_DEPTH = 4;
  private static final String CONTENTS_KEY_NAME = "backpack_contents";

  public static NamespacedKey contentsKey() {
    return new NamespacedKey(Adapt.instance, CONTENTS_KEY_NAME);
  }

  @Override
  public Material getMaterial() {
    return Material.BUNDLE;
  }

  @Override
  public Class<Data> getType() {
    return Data.class;
  }

  @Override
  public void applyLore(Data data, List<String> lore) {
    Mode mode = Mode.parse(data.getMode());
    int capacity = snapCapacity(data.getCapacity());
    if (mode == Mode.BUNDLE) {
      addWrapped(lore, C.GRAY, AdaptLanguage.text(
          ItemsMessages.BACKPACK_MODE_BUNDLE,
          trusted("stacks", String.valueOf(capacity))
      ));
      addWrapped(lore, C.DARK_GRAY, AdaptLanguage.text(
          ItemsMessages.BACKPACK_ITEMS_STORED,
          trusted("used", String.valueOf(Math.max(0, data.getUsed()))),
          trusted("total", String.valueOf(capacityWeight(capacity)))
      ));
    } else {
      addWrapped(lore, C.GRAY, AdaptLanguage.text(
          ItemsMessages.BACKPACK_MODE_SLOTS,
          trusted("slots", String.valueOf(capacity))
      ));
      addWrapped(lore, C.DARK_GRAY, AdaptLanguage.text(
          ItemsMessages.BACKPACK_SLOTS_USED,
          trusted("used", String.valueOf(Math.max(0, data.getUsed()))),
          trusted("total", String.valueOf(capacity))
      ));
    }
    addWrapped(lore, C.GRAY, AdaptLanguage.text(ItemsMessages.BACKPACK_USAGE1));
    addWrapped(lore, C.DARK_GRAY, AdaptLanguage.text(ItemsMessages.BACKPACK_USAGE2));
  }

  private static void addWrapped(List<String> lore, C color, String text) {
    for (String line : wrap(text, LORE_WRAP_WIDTH)) {
      lore.add(color + line);
    }
  }

  /**
   * Keeps every hover line narrow regardless of how long a locale's translation
   * runs. Splits on spaces only; a single over-long word stays on its own line.
   */
  static List<String> wrap(String text, int width) {
    List<String> lines = new ArrayList<>();
    if (text == null || text.isBlank()) {
      return lines;
    }

    StringBuilder line = new StringBuilder();
    for (String word : text.trim().split("\\s+")) {
      if (line.length() > 0 && line.length() + 1 + word.length() > width) {
        lines.add(line.toString());
        line.setLength(0);
      }
      if (line.length() > 0) {
        line.append(' ');
      }
      line.append(word);
    }
    if (line.length() > 0) {
      lines.add(line.toString());
    }

    return lines;
  }

  @Override
  public void applyMeta(Data data, ItemMeta meta) {
    meta.setDisplayName(C.GOLD + AdaptLanguage.text(ItemsMessages.BACKPACK_NAME));
    meta.setMaxStackSize(1);
    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    normalizeBundleComponent(meta);
  }

  /**
   * Empties the vanilla bundle payload. Storage lives in Adapt's own key, so a
   * populated {@code bundle_contents} is only ever residue from a window where
   * this adaptation was disabled - and it also drives the fullness bar this item
   * must never show.
   */
  public static void normalizeBundleComponent(ItemMeta meta) {
    if (meta instanceof BundleMeta bundle && bundle.hasItems()) {
      bundle.setItems(List.of());
    }
  }

  public static List<ItemStack> drainBundleComponent(ItemStack stack) {
    if (stack == null || !(stack.getItemMeta() instanceof BundleMeta bundle) || !bundle.hasItems()) {
      return List.of();
    }

    List<ItemStack> stranded = new ArrayList<>(bundle.getItems());
    bundle.setItems(List.of());
    stack.setItemMeta(bundle);
    return stranded;
  }

  public static boolean isBackpack(ItemStack stack) {
    return io.hasData(stack);
  }

  public static Data dataOf(ItemStack stack) {
    return io.getData(stack);
  }

  public static Mode modeOf(ItemStack stack) {
    Data data = io.getData(stack);
    return data == null ? Mode.SLOTS : Mode.parse(data.getMode());
  }

  public static String identityOf(ItemStack stack) {
    Data data = io.getData(stack);
    return data == null ? null : data.getId();
  }

  /**
   * Locates the backing stack by its stamped identity rather than by slot, so an
   * inventory reshuffle mid-view can never write one backpack's contents onto
   * another stack.
   */
  public static int identitySlot(ItemStack[] contents, String id) {
    if (contents == null || id == null) {
      return -1;
    }

    for (int index = 0; index < contents.length; index++) {
      ItemStack candidate = contents[index];
      if (candidate == null || candidate.getType() != Material.BUNDLE) {
        continue;
      }
      if (id.equals(identityOf(candidate))) {
        return index;
      }
    }

    return -1;
  }

  // ---------------------------------------------------------------- capacity

  /** Snaps any configured capacity onto a container-representable size. */
  public static int snapCapacity(int requested) {
    if (requested <= MIN_CAPACITY) {
      return MIN_CAPACITY;
    }
    if (requested >= MAX_CAPACITY) {
      return MAX_CAPACITY;
    }

    int steps = Math.round(requested / (float) CAPACITY_STEP);
    return Math.max(MIN_CAPACITY, Math.min(MAX_CAPACITY, steps * CAPACITY_STEP));
  }

  public static int snapUp(int needed) {
    if (needed <= MIN_CAPACITY) {
      return MIN_CAPACITY;
    }
    if (needed >= MAX_CAPACITY) {
      return MAX_CAPACITY;
    }

    return ((needed + CAPACITY_STEP - 1) / CAPACITY_STEP) * CAPACITY_STEP;
  }

  /**
   * Slot-mode view size. A backpack holding more stacks than the configured
   * capacity - an admin lowered it - still opens large enough to show all of
   * them, so stored items are never hidden or truncated.
   */
  public static int slotViewSize(int storedStacks, int capacity) {
    return snapUp(Math.max(snapCapacity(capacity), Math.max(MIN_CAPACITY, storedStacks)));
  }

  /** Stacks that cannot be shown even by a double chest, delivered to the player. */
  public static int slotOverflow(int storedStacks) {
    return Math.max(0, storedStacks - MAX_CAPACITY);
  }

  // ------------------------------------------------------------------ weight

  /**
   * Vanilla weight rule: one stack costs a full stack of budget, so an item that
   * stacks to 16 costs 4 and an unstackable costs 64. Integer division floors,
   * which keeps a full stack at or under one stack of weight - the invariant
   * that makes a slot-mode backpack always fit in bundle mode.
   */
  public static int unitWeight(int maxStackSize) {
    int max = Math.max(1, Math.min(STACK_WEIGHT, maxStackSize));
    return STACK_WEIGHT / max;
  }

  public static int unitWeightOf(ItemStack stack) {
    return stack == null ? STACK_WEIGHT : unitWeight(stack.getMaxStackSize());
  }

  public static int capacityWeight(int capacityStacks) {
    return snapCapacity(capacityStacks) * STACK_WEIGHT;
  }

  public static int weightOf(int unitWeight, int amount) {
    return unitWeight * Math.max(0, amount);
  }

  public static int freeWeight(int usedWeight, int capacityWeight) {
    return Math.max(0, capacityWeight - Math.max(0, usedWeight));
  }

  /** How much of an incoming stack the remaining budget can take. */
  public static int acceptableAmount(int unitWeight, int amount, int freeWeight) {
    if (unitWeight <= 0 || amount <= 0 || freeWeight <= 0) {
      return 0;
    }

    return Math.min(amount, freeWeight / unitWeight);
  }

  // -------------------------------------------------------------------- heap

  public record HeapSlot(ItemStack template, int amount) {
  }

  public record HeapWithdrawal(List<HeapSlot> heap, int taken) {
  }

  public static List<HeapSlot> readHeap(ItemStack[] contents) {
    List<HeapSlot> heap = new ArrayList<>();
    if (contents == null) {
      return heap;
    }

    for (ItemStack stack : contents) {
      if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
        continue;
      }
      heap.add(new HeapSlot(stack, stack.getAmount()));
    }

    return heap;
  }

  public static ItemStack[] writeHeap(List<HeapSlot> heap) {
    List<ItemStack> stacks = new ArrayList<>(heap.size());
    for (HeapSlot slot : heap) {
      if (slot.template() == null || slot.amount() <= 0) {
        continue;
      }
      ItemStack stack = slot.template().clone();
      stack.setAmount(slot.amount());
      stacks.add(stack);
    }

    return stacks.toArray(new ItemStack[0]);
  }

  /** Fills existing matching stacks before opening a new one, like a real bundle. */
  public static List<HeapSlot> deposit(List<HeapSlot> heap, ItemStack incoming, int amount) {
    List<HeapSlot> next = new ArrayList<>(heap);
    if (incoming == null || amount <= 0) {
      return next;
    }

    int max = Math.max(1, incoming.getMaxStackSize());
    int remaining = amount;
    for (int index = 0; index < next.size() && remaining > 0; index++) {
      HeapSlot slot = next.get(index);
      if (slot.template() == null || !slot.template().isSimilar(incoming)) {
        continue;
      }
      int room = max - slot.amount();
      if (room <= 0) {
        continue;
      }
      int moved = Math.min(room, remaining);
      next.set(index, new HeapSlot(slot.template(), slot.amount() + moved));
      remaining -= moved;
    }

    while (remaining > 0) {
      int moved = Math.min(max, remaining);
      next.add(new HeapSlot(incoming, moved));
      remaining -= moved;
    }

    return next;
  }

  public static HeapWithdrawal withdraw(List<HeapSlot> heap, int index, int amount) {
    if (index < 0 || index >= heap.size() || amount <= 0) {
      return new HeapWithdrawal(new ArrayList<>(heap), 0);
    }

    List<HeapSlot> next = new ArrayList<>(heap);
    HeapSlot slot = next.get(index);
    int taken = Math.min(amount, slot.amount());
    if (taken >= slot.amount()) {
      next.remove(index);
    } else {
      next.set(index, new HeapSlot(slot.template(), slot.amount() - taken));
    }

    return new HeapWithdrawal(next, taken);
  }

  public static int heapWeight(List<HeapSlot> heap, ToIntFunction<ItemStack> unit) {
    int total = 0;
    for (HeapSlot slot : heap) {
      if (slot.template() == null || slot.amount() <= 0) {
        continue;
      }
      total += unit.applyAsInt(slot.template()) * slot.amount();
    }

    return total;
  }

  public static int heapWeight(List<HeapSlot> heap) {
    return heapWeight(heap, BackpackItem::unitWeightOf);
  }

  // -------------------------------------------------------------- projection

  /**
   * Bundle-mode views always reserve the bottom row for navigation, so the view
   * never has to be resized mid-session when a deposit adds a distinct stack.
   */
  public static int bundleViewSize(int distinctStacks) {
    int display = Math.max(MIN_CAPACITY, Math.min(MAX_CAPACITY - NAV_ROW, distinctStacks));
    return snapUp(display + NAV_ROW);
  }

  public static int pageCapacity(int viewSize) {
    return Math.max(1, viewSize - NAV_ROW);
  }

  public static int pageCount(int distinctStacks, int pageCapacity) {
    if (pageCapacity <= 0) {
      return 1;
    }

    return Math.max(1, (Math.max(0, distinctStacks) + pageCapacity - 1) / pageCapacity);
  }

  public static int clampPage(int page, int pages) {
    return Math.max(0, Math.min(page, Math.max(1, pages) - 1));
  }

  public static int pageStart(int page, int pageCapacity) {
    return Math.max(0, page) * Math.max(1, pageCapacity);
  }

  // ------------------------------------------------------------------ denial

  public static boolean isShulkerBox(Material type) {
    return type != null && type.name().endsWith("SHULKER_BOX");
  }

  /**
   * Bounded scan for a backpack hidden inside a shulker box or a vanilla bundle.
   * Refusing these keeps a nested blob from growing exponentially; the byte
   * ceiling is the backstop for anything this scan does not model.
   */
  public static boolean containsBackpack(ItemStack stack, int depth) {
    if (stack == null || depth <= 0) {
      return false;
    }
    if (isBackpack(stack)) {
      return true;
    }

    ItemMeta meta = stack.getItemMeta();
    if (meta instanceof BundleMeta bundle && bundle.hasItems()) {
      for (ItemStack inner : bundle.getItems()) {
        if (containsBackpack(inner, depth - 1)) {
          return true;
        }
      }
    }
    if (meta instanceof BlockStateMeta blockState
        && blockState.hasBlockState()
        && blockState.getBlockState() instanceof Container container) {
      for (ItemStack inner : container.getInventory().getContents()) {
        if (containsBackpack(inner, depth - 1)) {
          return true;
        }
      }
    }

    return false;
  }

  public static boolean deniesDeposit(ItemStack stack, boolean denyNestedContainers) {
    if (stack == null || stack.getType() == Material.AIR) {
      return false;
    }
    if (isBackpack(stack)) {
      return true;
    }

    return denyNestedContainers && containsBackpack(stack, NEST_SCAN_DEPTH);
  }

  public static boolean exceedsCeiling(int currentBytes, int incomingBytes, int ceilingBytes) {
    if (ceilingBytes <= 0) {
      return false;
    }

    return Math.max(0, currentBytes) + Math.max(0, incomingBytes) > ceilingBytes;
  }

  // ---------------------------------------------------------------- contents

  public static ItemStack[] readContents(ItemStack backpack) {
    if (!isBackpack(backpack)) {
      return null;
    }

    byte[] blob = backpack.getItemMeta().getPersistentDataContainer()
        .get(contentsKey(), PersistentDataType.BYTE_ARRAY);
    if (blob == null || blob.length == 0) {
      return new ItemStack[0];
    }

    return normalize(PaperCompat.deserializeItems(blob));
  }

  public static int storedBytes(ItemStack backpack) {
    if (!isBackpack(backpack)) {
      return 0;
    }

    byte[] blob = backpack.getItemMeta().getPersistentDataContainer()
        .get(contentsKey(), PersistentDataType.BYTE_ARRAY);
    return blob == null ? 0 : blob.length;
  }

  public static int countStored(ItemStack[] contents) {
    if (contents == null) {
      return 0;
    }

    int count = 0;
    for (ItemStack stack : contents) {
      if (stack != null && stack.getType() != Material.AIR && stack.getAmount() > 0) {
        count++;
      }
    }

    return count;
  }

  public static ItemStack[] compact(ItemStack[] contents) {
    List<ItemStack> kept = new ArrayList<>();
    if (contents != null) {
      for (ItemStack stack : contents) {
        if (stack != null && stack.getType() != Material.AIR && stack.getAmount() > 0) {
          kept.add(stack);
        }
      }
    }

    return kept.toArray(new ItemStack[0]);
  }

  private static ItemStack[] normalize(ItemStack[] contents) {
    if (contents == null) {
      return new ItemStack[0];
    }

    ItemStack[] normalized = new ItemStack[contents.length];
    for (int index = 0; index < contents.length; index++) {
      ItemStack stack = contents[index];
      normalized[index] = stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0
          ? null
          : stack;
    }

    return normalized;
  }

  /**
   * Rewrites presentation and storage on the live stack instance. The meta is
   * mutated in place rather than swapped through the inventory so a death-drop
   * snapshot taken from the same stack still sees the persisted contents.
   */
  public static void write(ItemStack backpack, Data data, ItemStack[] contents) {
    io.setData(backpack, data);
    writeContents(backpack, contents);
  }

  public static void writeContents(ItemStack backpack, ItemStack[] contents) {
    ItemMeta meta = backpack.getItemMeta();
    if (meta == null) {
      return;
    }

    ItemStack[] kept = normalize(contents);
    if (countStored(kept) == 0) {
      meta.getPersistentDataContainer().remove(contentsKey());
    } else {
      meta.getPersistentDataContainer().set(
          contentsKey(),
          PersistentDataType.BYTE_ARRAY,
          PaperCompat.serializeItems(kept)
      );
    }
    normalizeBundleComponent(meta);
    backpack.setItemMeta(meta);
  }

  // -------------------------------------------------------------- extraction

  public record BackpackExtraction(ItemStack pulled, ItemStack[] remaining) {
  }

  /**
   * Mode agnostic on purpose: both models store an ordinary stack array, so a
   * refill pull never has to know which model the backpack is in.
   */
  public static BackpackExtraction extractMatching(ItemStack[] contents, ItemStack template) {
    if (contents == null || template == null) {
      return null;
    }

    for (int index = 0; index < contents.length; index++) {
      ItemStack candidate = contents[index];
      if (candidate == null || candidate.getType() == Material.AIR || candidate.getAmount() <= 0) {
        continue;
      }
      if (!candidate.isSimilar(template)) {
        continue;
      }

      ItemStack[] remaining = contents.clone();
      remaining[index] = null;
      return new BackpackExtraction(candidate.clone(), remaining);
    }

    return null;
  }

  public enum Mode {
    SLOTS,
    BUNDLE;

    public Mode cycle() {
      return this == SLOTS ? BUNDLE : SLOTS;
    }

    public static Mode parse(String raw) {
      if (raw == null) {
        return SLOTS;
      }

      for (Mode mode : values()) {
        if (mode.name().equalsIgnoreCase(raw.trim())) {
          return mode;
        }
      }

      return SLOTS;
    }
  }

  @AllArgsConstructor
  @lombok.Data
  public static class Data {
    private String id;
    private String mode;
    private int capacity;
    private int used;

    public static Data of(String id, Mode mode, int capacity, int used) {
      return new Data(id, mode.name(), capacity, used);
    }
  }
}
