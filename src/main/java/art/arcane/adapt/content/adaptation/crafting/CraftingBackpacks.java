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

package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdvancementSpec;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.recipe.MaterialChar;
import art.arcane.adapt.content.item.BackpackItem;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.AdvancementMessages;
import art.arcane.adapt.localization.catalog.CraftingMessages;
import art.arcane.adapt.localization.catalog.ItemsMessages;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * Adapt Backpacks. The crafted item is a tagged {@link Material#BUNDLE} whose
 * storage lives in Adapt's own persistent data, never in the vanilla
 * {@code bundle_contents} component, so every vanilla bundle mechanic is
 * suppressed on it and the only way in or out is the Adapt container view.
 *
 * <p>The backpack carries its own storage mode. Slot mode is a plain container
 * of whole stacks; bundle mode is a weight budget whose view is a paged
 * projection of the heap. Crafting an empty backpack alone in a crafting grid
 * cycles the mode.
 */
public class CraftingBackpacks extends SimpleAdaptation<CraftingBackpacks.Config> {
  static final String CRAFT_RECIPE_KEY = "crafting-backpacks";
  static final String CYCLE_RECIPE_KEY = "crafting-backpacks-mode";
  private static final int NAV_PREV = 0;
  private static final int NAV_INFO = 4;
  private static final int NAV_NEXT = 8;
  private static final long DECLINE_COOLDOWN_MILLIS = 1200L;
  private static final long OPEN_COOLDOWN_MILLIS = 200L;

  private final ConcurrentMap<UUID, BackpackView> views = new ConcurrentHashMap<>();
  private final Cooldowns declineCd = cooldowns();
  private final Cooldowns openCd = cooldowns();
  private final AdaptRecipe craftRecipe;
  private final AdaptRecipe cycleRecipe;

  public CraftingBackpacks() {
    super("crafting-backpacks");
    registerConfiguration(Config.class);
    setIcon(Material.BUNDLE);
    setInterval(17779);
    craftRecipe = AdaptRecipe.shaped()
        .key(CRAFT_RECIPE_KEY)
        .ingredient(new MaterialChar('L', Material.LEATHER))
        .ingredient(new MaterialChar('C', Material.CHEST))
        .shapes(recipeShape())
        .result(template())
        .build();
    registerRecipe(craftRecipe);
    // A real registered recipe is what makes the mode cycle consume exactly one
    // backpack and hand back exactly one. A result injected onto a matrix that
    // matches no recipe is taken for free, which duplicates the input.
    cycleRecipe = AdaptRecipe.shapeless()
        .key(CYCLE_RECIPE_KEY)
        .ingredient(Material.BUNDLE)
        .result(template())
        .build();
    registerRecipe(cycleRecipe);
    AdvancementSpec backpacksCrafted = AdvancementSpec.challenge(
        "challenge_crafting_backpack_25",
        Material.BUNDLE,
        AdaptLanguage.text(AdvancementMessages.CHALLENGE_CRAFTING_BACKPACK_25_TITLE),
        AdaptLanguage.text(AdvancementMessages.CHALLENGE_CRAFTING_BACKPACK_25_DESCRIPTION)
    );
    registerMilestone(backpacksCrafted, "crafting.backpacks.bundles-crafted", 25, 300);
  }

  static List<String> recipeShape() {
    return List.of(
        "LLL",
        "LCL",
        "LLL"
    );
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.slots = BackpackItem.snapCapacity(loadedConfig.slots);
    loadedConfig.defaultStorageMode = BackpackItem.Mode.parse(loadedConfig.defaultStorageMode).name();
    loadedConfig.maxStoredBytes = Math.max(4096, loadedConfig.maxStoredBytes);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(CraftingMessages.BACKPACKS_LORE1));
    v.addLore(C.YELLOW + "- " + C.GRAY + AdaptLanguage.text(CraftingMessages.BACKPACKS_LORE2));
    v.addLore(C.YELLOW + "- " + C.GRAY + AdaptLanguage.text(CraftingMessages.BACKPACKS_LORE3));
    v.addLore(C.YELLOW + "- " + C.GRAY + AdaptLanguage.text(CraftingMessages.BACKPACKS_LORE4));
  }

  private int capacity() {
    return BackpackItem.snapCapacity(getConfig().slots);
  }

  private BackpackItem.Mode defaultMode() {
    return BackpackItem.Mode.parse(getConfig().defaultStorageMode);
  }

  private ItemStack template() {
    return BackpackItem.io.withData(BackpackItem.Data.of(null, defaultMode(), capacity(), 0));
  }

  // -------------------------------------------------------------- interaction

  @ReceiveCancelledEvents
  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
      return;
    }

    Player p = e.getPlayer();
    if (e.getHand() == EquipmentSlot.OFF_HAND) {
      if (BackpackItem.isBackpack(p.getInventory().getItemInOffHand())) {
        e.setUseItemInHand(Event.Result.DENY);
      }
      return;
    }

    ItemStack hand = p.getInventory().getItemInMainHand();
    if (!BackpackItem.isBackpack(hand)) {
      return;
    }

    // Captured before our own deny. The event's cancelled flag is unusable
    // here: Bukkit reports RIGHT_CLICK_AIR as cancelled by default (the block
    // half is DENY when there is no block), which would make air opens
    // impossible.
    boolean itemUseAlreadyDenied = e.useItemInHand() == Event.Result.DENY;

    // Vanilla bundle use dies here whether or not the player learned the
    // adaptation, so an unlearned backpack is dormant instead of a bundle.
    e.setUseItemInHand(Event.Result.DENY);
    if (p.isSneaking() || !hasActiveAdaptation(p) || itemUseAlreadyDenied) {
      return;
    }

    e.setCancelled(true);
    if (!openCd.isReady(p.getUniqueId(), OPEN_COOLDOWN_MILLIS)) {
      return;
    }
    openCd.mark(p.getUniqueId());
    openBackpack(p, hand);
  }

  private void openBackpack(Player p, ItemStack hand) {
    if (views.containsKey(p.getUniqueId())) {
      return;
    }

    BackpackItem.Data data = BackpackItem.dataOf(hand);
    if (data == null) {
      return;
    }

    ItemStack[] stored;
    try {
      stored = BackpackItem.readContents(hand);
    } catch (IllegalStateException unreadable) {
      Adapt.warn("Backpack contents for " + p.getUniqueId() + " could not be read: " + unreadable.getMessage());
      decline(p, CraftingMessages.BACKPACKS_UNREADABLE);
      return;
    }

    // Residue from a window where this adaptation was disabled and vanilla
    // bundling worked. Hand it straight back rather than budgeting it.
    for (ItemStack stranded : BackpackItem.drainBundleComponent(hand)) {
      deliver(p, stranded);
    }

    BackpackItem.Mode mode = BackpackItem.Mode.parse(data.getMode());
    int capacity = capacity();
    String id = ensureIdentity(p, hand, data, mode, capacity, stored);
    BackpackView view = mode == BackpackItem.Mode.BUNDLE
        ? openBundleView(p, id, capacity, stored)
        : openSlotView(p, id, capacity, stored);
    if (view == null) {
      return;
    }

    view.storedBytes = BackpackItem.storedBytes(hand);
    views.put(p.getUniqueId(), view);
    if (p.openInventory(view.inventory) == null) {
      views.remove(p.getUniqueId(), view);
      return;
    }

    fx(p.getEyeLocation(), FxPriority.TRANSITION)
        .particle(Particle.CRIT, 4, 0, 0.2D, 0, 0.2D, 0.01D)
        .chord(Sound.ITEM_BUNDLE_INSERT, 0.5f, 1.2f, Sound.BLOCK_WOOL_PLACE, 0.4f, 1.1f);
  }

  private BackpackView openSlotView(Player p, String id, int capacity, ItemStack[] stored) {
    ItemStack[] kept = BackpackItem.compact(stored);
    int overflow = BackpackItem.slotOverflow(kept.length);
    if (overflow > 0) {
      for (int index = kept.length - overflow; index < kept.length; index++) {
        deliver(p, kept[index]);
      }
      ItemStack[] trimmed = new ItemStack[BackpackItem.MAX_CAPACITY];
      System.arraycopy(kept, 0, trimmed, 0, BackpackItem.MAX_CAPACITY);
      kept = trimmed;
    }

    int size = BackpackItem.slotViewSize(kept.length, capacity);
    BackpackView view = new BackpackView(id, BackpackItem.Mode.SLOTS, capacity);
    view.inventory = createInventory(view, size);
    place(view.inventory, stored, kept);
    return view;
  }

  private BackpackView openBundleView(Player p, String id, int capacity, ItemStack[] stored) {
    BackpackView view = new BackpackView(id, BackpackItem.Mode.BUNDLE, capacity);
    view.heap = BackpackItem.readHeap(stored);
    view.inventory = createInventory(view, BackpackItem.bundleViewSize(view.heap.size()));
    render(view);
    return view;
  }

  /**
   * Keeps the player's own arrangement when the stored array still fits the
   * view, and falls back to a compacted fill when the view shrank.
   */
  private void place(Inventory inventory, ItemStack[] stored, ItemStack[] compacted) {
    int size = inventory.getSize();
    if (stored != null && stored.length <= size && BackpackItem.countStored(stored) == compacted.length) {
      for (int index = 0; index < stored.length; index++) {
        inventory.setItem(index, stored[index]);
      }
      return;
    }

    for (int index = 0; index < compacted.length && index < size; index++) {
      inventory.setItem(index, compacted[index]);
    }
  }

  private Inventory createInventory(BackpackView view, int size) {
    String title = AdaptLanguage.text(ItemsMessages.BACKPACK_NAME);
    if (size == BackpackItem.MIN_CAPACITY) {
      return Bukkit.createInventory(view, InventoryType.DISPENSER, title);
    }

    return Bukkit.createInventory(view, size, title);
  }

  private String ensureIdentity(Player p, ItemStack hand, BackpackItem.Data data,
                                BackpackItem.Mode mode, int capacity, ItemStack[] stored) {
    String id = data.getId();
    if (id != null && !duplicateIdentity(p.getInventory().getContents(), hand, id)) {
      return id;
    }

    String fresh = UUID.randomUUID().toString();
    int used = mode == BackpackItem.Mode.BUNDLE
        ? BackpackItem.heapWeight(BackpackItem.readHeap(stored))
        : BackpackItem.countStored(stored);
    BackpackItem.write(hand, BackpackItem.Data.of(fresh, mode, capacity, used), stored);
    p.getInventory().setItemInMainHand(hand);
    return fresh;
  }

  /** A cloned backpack would otherwise let a write-back land on the wrong stack. */
  static boolean duplicateIdentity(ItemStack[] contents, ItemStack self, String id) {
    if (contents == null || id == null) {
      return false;
    }

    int matches = 0;
    for (ItemStack candidate : contents) {
      if (candidate == null || candidate == self) {
        continue;
      }
      if (id.equals(BackpackItem.identityOf(candidate))) {
        matches++;
      }
    }

    return matches > 0;
  }

  // -------------------------------------------------------------------- views

  private BackpackView viewOf(Inventory inventory) {
    if (inventory == null) {
      return null;
    }

    InventoryHolder holder = inventory.getHolder();
    return holder instanceof BackpackView view ? view : null;
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(InventoryClickEvent e) {
    if (!(e.getWhoClicked() instanceof Player p)) {
      return;
    }

    BackpackView view = viewOf(e.getView().getTopInventory());
    if (view == null) {
      if (suppressesVanillaBundling(
          BackpackItem.isBackpack(e.getCursor()),
          BackpackItem.isBackpack(e.getCurrentItem()),
          isEmpty(e.getCursor()),
          isEmpty(e.getCurrentItem()),
          e.isRightClick())) {
        e.setCancelled(true);
      }
      return;
    }

    if (views.get(p.getUniqueId()) != view) {
      e.setCancelled(true);
      return;
    }

    if (guardsBackingItem(p, view, e)) {
      e.setCancelled(true);
      return;
    }

    if (view.mode == BackpackItem.Mode.BUNDLE) {
      handleBundleClick(p, view, e);
      return;
    }

    handleSlotClick(p, view, e);
  }

  /**
   * Vanilla bundling only ever fires when a stack meets the bundle, or when the
   * bundle is right-clicked with an empty cursor. Ordinary picking up and
   * putting down of the backpack itself stays untouched.
   */
  static boolean suppressesVanillaBundling(boolean cursorBackpack, boolean currentBackpack,
                                           boolean cursorEmpty, boolean currentEmpty, boolean rightClick) {
    if (!cursorBackpack && !currentBackpack) {
      return false;
    }
    if (!cursorEmpty && !currentEmpty) {
      return true;
    }

    return rightClick && cursorEmpty && currentBackpack;
  }

  private boolean guardsBackingItem(Player p, BackpackView view, InventoryClickEvent e) {
    String id = view.backpackId;
    if (e.getClickedInventory() != view.inventory && isBacking(e.getCurrentItem(), id)) {
      return true;
    }
    if (isBacking(e.getCursor(), id)) {
      return true;
    }
    if (e.getClick() == ClickType.NUMBER_KEY
        && e.getHotbarButton() >= 0
        && isBacking(p.getInventory().getItem(e.getHotbarButton()), id)) {
      return true;
    }

    return e.getClick() == ClickType.SWAP_OFFHAND
        && (isBacking(e.getCurrentItem(), id) || isBacking(p.getInventory().getItemInOffHand(), id));
  }

  static boolean isBacking(ItemStack stack, String id) {
    return id != null && id.equals(BackpackItem.identityOf(stack));
  }

  private void handleSlotClick(Player p, BackpackView view, InventoryClickEvent e) {
    ItemStack incoming = incomingSlotDeposit(p, view, e);
    if (incoming != null && !acceptsDeposit(p, view, incoming)) {
      e.setCancelled(true);
      return;
    }

    markDirty(p, view);
  }

  private ItemStack incomingSlotDeposit(Player p, BackpackView view, InventoryClickEvent e) {
    if (e.getClickedInventory() == view.inventory) {
      if (e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() >= 0) {
        return p.getInventory().getItem(e.getHotbarButton());
      }
      if (e.getClick() == ClickType.SWAP_OFFHAND) {
        return p.getInventory().getItemInOffHand();
      }
      return isEmpty(e.getCursor()) ? null : e.getCursor();
    }

    if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
      return isEmpty(e.getCurrentItem()) ? null : e.getCurrentItem();
    }

    return null;
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(InventoryDragEvent e) {
    if (!(e.getWhoClicked() instanceof Player p)) {
      return;
    }

    BackpackView view = viewOf(e.getView().getTopInventory());
    if (view == null) {
      return;
    }

    boolean touchesTop = false;
    for (int raw : e.getRawSlots()) {
      if (raw < view.inventory.getSize()) {
        touchesTop = true;
        break;
      }
    }
    if (!touchesTop) {
      return;
    }

    if (views.get(p.getUniqueId()) != view || view.mode == BackpackItem.Mode.BUNDLE) {
      e.setCancelled(true);
      return;
    }

    if (isBacking(e.getOldCursor(), view.backpackId) || !acceptsDeposit(p, view, e.getOldCursor())) {
      e.setCancelled(true);
      return;
    }

    markDirty(p, view);
  }

  // ------------------------------------------------------------- bundle model

  private void handleBundleClick(Player p, BackpackView view, InventoryClickEvent e) {
    if (e.getClickedInventory() != view.inventory) {
      if (e.getAction() != InventoryAction.MOVE_TO_OTHER_INVENTORY) {
        return;
      }
      e.setCancelled(true);
      ItemStack source = e.getCurrentItem();
      if (isEmpty(source) || !acceptsDeposit(p, view, source)) {
        return;
      }
      int accepted = acceptableAmount(view, source, source.getAmount());
      if (accepted <= 0) {
        decline(p, CraftingMessages.BACKPACKS_DENY_FULL);
        return;
      }
      view.heap = BackpackItem.deposit(view.heap, detach(source), accepted);
      ItemStack remainder = source.clone();
      remainder.setAmount(source.getAmount() - accepted);
      e.setCurrentItem(remainder.getAmount() <= 0 ? null : remainder);
      commitBundle(p, view);
      return;
    }

    e.setCancelled(true);
    int display = BackpackItem.pageCapacity(view.inventory.getSize());
    int slot = e.getSlot();
    if (slot >= display) {
      navigate(p, view, slot - display);
      return;
    }

    ItemStack cursor = e.getCursor();
    if (!isEmpty(cursor)) {
      depositCursor(p, view, cursor, e.isRightClick() ? 1 : cursor.getAmount());
      return;
    }

    withdraw(p, view, BackpackItem.pageStart(view.page, display) + slot, e.isRightClick());
  }

  private void depositCursor(Player p, BackpackView view, ItemStack cursor, int amount) {
    if (!acceptsDeposit(p, view, cursor)) {
      return;
    }

    int accepted = acceptableAmount(view, cursor, amount);
    if (accepted <= 0) {
      decline(p, CraftingMessages.BACKPACKS_DENY_FULL);
      return;
    }

    view.heap = BackpackItem.deposit(view.heap, detach(cursor), accepted);
    ItemStack remainder = cursor.clone();
    remainder.setAmount(cursor.getAmount() - accepted);
    p.setItemOnCursor(remainder.getAmount() <= 0 ? null : remainder);
    commitBundle(p, view);
  }

  private void withdraw(Player p, BackpackView view, int heapIndex, boolean half) {
    if (heapIndex < 0 || heapIndex >= view.heap.size()) {
      return;
    }

    BackpackItem.HeapSlot slot = view.heap.get(heapIndex);
    int want = half ? (slot.amount() + 1) / 2 : slot.amount();
    BackpackItem.HeapWithdrawal taken = BackpackItem.withdraw(view.heap, heapIndex, want);
    if (taken.taken() <= 0) {
      return;
    }

    ItemStack out = slot.template().clone();
    out.setAmount(taken.taken());
    view.heap = taken.heap();
    // Anything the player cannot hold goes straight back into the heap. A
    // withdrawal never drops on the floor and never evaporates.
    for (ItemStack leftover : p.getInventory().addItem(out).values()) {
      view.heap = BackpackItem.deposit(view.heap, leftover, leftover.getAmount());
    }
    commitBundle(p, view);
  }

  private void navigate(Player p, BackpackView view, int navSlot) {
    int pages = BackpackItem.pageCount(view.heap.size(), BackpackItem.pageCapacity(view.inventory.getSize()));
    if (navSlot == NAV_PREV && view.page > 0) {
      view.page--;
    } else if (navSlot == NAV_NEXT && view.page < pages - 1) {
      view.page++;
    } else {
      return;
    }

    render(view);
    p.updateInventory();
    sfx(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 1.4f);
  }

  private void commitBundle(Player p, BackpackView view) {
    render(view);
    persist(p, view);
    p.updateInventory();
  }

  private int acceptableAmount(BackpackView view, ItemStack stack, int amount) {
    return BackpackItem.acceptableAmount(
        BackpackItem.unitWeightOf(stack),
        amount,
        BackpackItem.freeWeight(BackpackItem.heapWeight(view.heap), BackpackItem.capacityWeight(view.capacity))
    );
  }

  private void render(BackpackView view) {
    Inventory inventory = view.inventory;
    int display = BackpackItem.pageCapacity(inventory.getSize());
    int pages = BackpackItem.pageCount(view.heap.size(), display);
    view.page = BackpackItem.clampPage(view.page, pages);
    int start = BackpackItem.pageStart(view.page, display);

    for (int index = 0; index < display; index++) {
      int heapIndex = start + index;
      if (heapIndex >= view.heap.size()) {
        inventory.setItem(index, null);
        continue;
      }
      BackpackItem.HeapSlot slot = view.heap.get(heapIndex);
      ItemStack shown = slot.template().clone();
      shown.setAmount(slot.amount());
      inventory.setItem(index, shown);
    }

    // The nav row reads as dead space: grey filler everywhere, counts on the
    // centre pane, and arrows only once there is actually something to page.
    ItemStack filler = navFiller();
    for (int index = 0; index < BackpackItem.NAV_ROW; index++) {
      inventory.setItem(display + index, filler);
    }
    inventory.setItem(display + NAV_INFO, navInfo(view, pages));
    if (pages > 1) {
      inventory.setItem(display + NAV_PREV, navArrow(CraftingMessages.BACKPACKS_PREV_PAGE));
      inventory.setItem(display + NAV_NEXT, navArrow(CraftingMessages.BACKPACKS_NEXT_PAGE));
    }
  }

  private ItemStack navFiller() {
    ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
    ItemMeta meta = filler.getItemMeta();
    if (meta == null) {
      return filler;
    }

    meta.setHideTooltip(true);
    filler.setItemMeta(meta);
    return filler;
  }

  private ItemStack navInfo(BackpackView view, int pages) {
    ItemStack info = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
    ItemMeta meta = info.getItemMeta();
    if (meta == null) {
      return info;
    }

    meta.setDisplayName(C.GRAY + AdaptLanguage.text(
        ItemsMessages.BACKPACK_ITEMS_STORED,
        trusted("used", String.valueOf(BackpackItem.heapWeight(view.heap))),
        trusted("total", String.valueOf(BackpackItem.capacityWeight(view.capacity)))
    ));
    if (pages > 1) {
      meta.setLore(List.of(C.DARK_GRAY + AdaptLanguage.text(
          CraftingMessages.BACKPACKS_PAGE,
          trusted("page", String.valueOf(view.page + 1)),
          trusted("pages", String.valueOf(pages))
      )));
    }
    info.setItemMeta(meta);
    return info;
  }

  private ItemStack navArrow(TextKey label) {
    ItemStack arrow = new ItemStack(Material.ARROW);
    ItemMeta meta = arrow.getItemMeta();
    if (meta == null) {
      return arrow;
    }

    meta.setDisplayName(C.YELLOW + AdaptLanguage.text(label));
    arrow.setItemMeta(meta);
    return arrow;
  }

  // ---------------------------------------------------------------- deposits

  private boolean acceptsDeposit(Player p, BackpackView view, ItemStack incoming) {
    if (isEmpty(incoming)) {
      return true;
    }

    if (BackpackItem.deniesDeposit(incoming, getConfig().denyNestedContainers)) {
      decline(p, CraftingMessages.BACKPACKS_DENY_NESTED);
      return false;
    }

    if (BackpackItem.exceedsCeiling(view.storedBytes, measure(incoming), getConfig().maxStoredBytes)) {
      decline(p, CraftingMessages.BACKPACKS_DENY_FULL);
      return false;
    }

    return true;
  }

  private int measure(ItemStack stack) {
    try {
      return PaperCompat.serializeItems(new ItemStack[]{stack}).length;
    } catch (RuntimeException error) {
      return 0;
    }
  }

  private void decline(Player p, TextKey message) {
    if (!declineCd.isReady(p.getUniqueId(), DECLINE_COOLDOWN_MILLIS)) {
      return;
    }

    declineCd.mark(p.getUniqueId());
    Adapt.actionbar(p, C.RED + AdaptLanguage.text(message));
    sfx(p.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.4f, 0.9f);
  }

  // --------------------------------------------------------------- persistence

  private void markDirty(Player p, BackpackView view) {
    if (!view.dirty.compareAndSet(false, true)) {
      return;
    }

    if (!J.runEntity(p, () -> {
      view.dirty.set(false);
      if (views.get(p.getUniqueId()) == view) {
        persist(p, view);
      }
    }, 1)) {
      view.dirty.set(false);
    }
  }

  private void persist(Player p, BackpackView view) {
    ItemStack[] contents;
    int used;
    if (view.mode == BackpackItem.Mode.BUNDLE) {
      contents = BackpackItem.writeHeap(view.heap);
      used = BackpackItem.heapWeight(view.heap);
    } else {
      // Slot indexed with its holes intact; Paper's item array format keeps
      // empty entries in order, so the player's own arrangement survives.
      contents = view.inventory.getContents();
      used = BackpackItem.countStored(contents);
    }

    List<ItemStack> evicted = new ArrayList<>();
    contents = trimToCeiling(contents, getConfig().maxStoredBytes, evicted);
    if (view.mode != BackpackItem.Mode.BUNDLE) {
      used = BackpackItem.countStored(contents);
    }

    int slot = BackpackItem.identitySlot(p.getInventory().getContents(), view.backpackId);
    if (slot < 0) {
      Adapt.warn("Backpack " + view.backpackId + " left " + p.getUniqueId()
          + " while open; returning its contents.");
      for (ItemStack stack : contents) {
        deliver(p, stack);
      }
      for (ItemStack stack : evicted) {
        deliver(p, stack);
      }
      // The view is now a phantom copy of what was just delivered. Empty it and
      // close it, or the displayed stacks could be taken out a second time.
      view.heap = new ArrayList<>();
      view.inventory.clear();
      if (views.remove(p.getUniqueId(), view) && p.isOnline()) {
        J.runEntity(p, p::closeInventory);
      }
      return;
    }

    ItemStack backing = p.getInventory().getItem(slot);
    if (backing == null) {
      return;
    }

    // Mutated in place rather than swapped back through the inventory so a
    // death drop snapshot taken from this same stack still carries the write.
    BackpackItem.write(backing, BackpackItem.Data.of(view.backpackId, view.mode, view.capacity, used), contents);
    view.storedBytes = BackpackItem.storedBytes(backing);
    for (ItemStack stack : evicted) {
      deliver(p, stack);
    }
  }

  /** Nothing is deleted; anything past the ceiling comes back to the player. */
  private ItemStack[] trimToCeiling(ItemStack[] contents, int ceiling, List<ItemStack> evicted) {
    ItemStack[] kept = contents;
    while (BackpackItem.countStored(kept) > 0
        && PaperCompat.serializeItems(kept).length > ceiling) {
      List<ItemStack> remaining = new ArrayList<>();
      for (ItemStack stack : kept) {
        remaining.add(stack);
      }
      for (int index = remaining.size() - 1; index >= 0; index--) {
        ItemStack stack = remaining.get(index);
        if (stack == null || stack.getType() == Material.AIR) {
          continue;
        }
        evicted.add(stack);
        remaining.set(index, null);
        break;
      }
      kept = remaining.toArray(new ItemStack[0]);
    }

    return kept;
  }

  private void deliver(Player p, ItemStack stack) {
    if (isEmpty(stack)) {
      return;
    }

    for (ItemStack leftover : p.getInventory().addItem(stack).values()) {
      p.getWorld().dropItemNaturally(p.getLocation(), leftover);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(InventoryCloseEvent e) {
    if (!(e.getPlayer() instanceof Player p)) {
      return;
    }

    BackpackView view = viewOf(e.getInventory());
    if (view == null || !views.remove(p.getUniqueId(), view)) {
      return;
    }

    persist(p, view);
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(PlayerDeathEvent e) {
    closeAndPersist(e.getEntity());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    closeAndPersist(e.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerItemHeldEvent e) {
    if (views.containsKey(e.getPlayer().getUniqueId())) {
      e.getPlayer().closeInventory();
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void on(PlayerDropItemEvent e) {
    BackpackView view = views.get(e.getPlayer().getUniqueId());
    if (view != null && isBacking(e.getItemDrop().getItemStack(), view.backpackId)) {
      e.setCancelled(true);
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void on(PlayerSwapHandItemsEvent e) {
    BackpackView view = views.get(e.getPlayer().getUniqueId());
    if (view == null) {
      return;
    }

    if (isBacking(e.getMainHandItem(), view.backpackId) || isBacking(e.getOffHandItem(), view.backpackId)) {
      e.setCancelled(true);
    }
  }

  private void closeAndPersist(Player p) {
    BackpackView view = views.remove(p.getUniqueId());
    if (view == null) {
      return;
    }

    persist(p, view);
    if (p.isOnline()) {
      p.closeInventory();
    }
  }

  @Override
  public void unregister() {
    List<UUID> open = new ArrayList<>(views.keySet());
    for (UUID playerId : open) {
      Player player = Bukkit.getPlayer(playerId);
      if (player == null) {
        views.remove(playerId);
        continue;
      }
      if (!J.runEntity(player, () -> closeAndPersist(player))) {
        views.remove(playerId);
      }
    }
    super.unregister();
  }

  // -------------------------------------------------------------- mode cycle

  @EventHandler
  public void on(PrepareItemCraftEvent e) {
    CraftingInventory inventory = e.getInventory();
    Recipe recipe = e.getRecipe();
    ItemStack[] matrix = inventory.getMatrix();
    if (recipe == null || !cycleRecipe.is(recipe)) {
      // A backpack consumed as an ingredient anywhere else would delete its
      // stored contents along with the item.
      if (!craftRecipe.is(recipe) && matrixHasBackpack(matrix)) {
        inventory.setResult(null);
      }
      return;
    }

    if (!getConfig().allowModeToggle || !isLoneBackpack(matrix)) {
      inventory.setResult(null);
      return;
    }

    ItemStack backpack = firstBackpack(matrix);
    if (!isEmptyBackpack(backpack)) {
      inventory.setResult(null);
      declineCycle(e.getView().getPlayer());
      return;
    }

    inventory.setResult(cycled(backpack));
  }

  private void declineCycle(HumanEntity viewer) {
    if (viewer instanceof Player p) {
      decline(p, CraftingMessages.BACKPACKS_CYCLE_NEEDS_EMPTY);
    }
  }

  static boolean matrixHasBackpack(ItemStack[] matrix) {
    if (matrix == null) {
      return false;
    }

    for (ItemStack stack : matrix) {
      if (BackpackItem.isBackpack(stack)) {
        return true;
      }
    }

    return false;
  }

  /** Exactly one backpack, a single one, and nothing else in the grid. */
  static boolean isLoneBackpack(ItemStack[] matrix) {
    if (matrix == null) {
      return false;
    }

    int backpacks = 0;
    for (ItemStack stack : matrix) {
      if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
        continue;
      }
      if (!BackpackItem.isBackpack(stack)) {
        return false;
      }
      if (stack.getAmount() > 1) {
        return false;
      }
      backpacks++;
    }

    return backpacks == 1;
  }

  static ItemStack firstBackpack(ItemStack[] matrix) {
    if (matrix == null) {
      return null;
    }

    for (ItemStack stack : matrix) {
      if (BackpackItem.isBackpack(stack)) {
        return stack;
      }
    }

    return null;
  }

  static boolean isEmptyBackpack(ItemStack backpack) {
    if (!BackpackItem.isBackpack(backpack)) {
      return false;
    }

    try {
      return BackpackItem.countStored(BackpackItem.readContents(backpack)) == 0;
    } catch (IllegalStateException unreadable) {
      return false;
    }
  }

  private ItemStack cycled(ItemStack backpack) {
    BackpackItem.Data cycled = cycledData(BackpackItem.dataOf(backpack), capacity());
    return cycled == null ? null : BackpackItem.io.withData(cycled);
  }

  /**
   * Same backpack, other mode. The identity is carried across so the write-back
   * lookup keeps working on the re-moded item, and the cycle only ever runs on
   * an empty backpack so there is nothing to convert.
   */
  static BackpackItem.Data cycledData(BackpackItem.Data data, int capacity) {
    if (data == null) {
      return null;
    }

    return BackpackItem.Data.of(
        data.getId(),
        BackpackItem.Mode.parse(data.getMode()).cycle(),
        capacity,
        0
    );
  }

  @EventHandler
  public void on(CraftItemEvent e) {
    if (!(e.getWhoClicked() instanceof Player p)) {
      return;
    }

    Recipe recipe = e.getRecipe();
    if (recipe == null || cycleRecipe.is(recipe) || !craftRecipe.is(recipe)) {
      return;
    }
    if (!hasActiveAdaptation(p)) {
      return;
    }

    addStat(p, "crafting.backpacks.bundles-crafted", 1);
    cinchDrawstring(p.getLocation().add(0, 1, 0));
  }

  private void cinchDrawstring(Location center) {
    timeline(center)
        .duration(8)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          double radius = 0.8D - (0.7D * progress);
          fx.ring(Particles.CRIT_MAGIC, radius, 12, 0.0D);
          fx.particle(Particle.PORTAL, 2, 0, 0.2D, 0, radius, 0.02D);
          if (tick == 0) {
            fx.dustRing(Color.fromRGB(160, 120, 60), 0.6D, 16, 1.0F);
            fx.chord(Sound.ITEM_BUNDLE_INSERT, 0.8F, 0.8F, Sound.BLOCK_WOOL_PLACE, 0.6F, 1.2F, Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.5F, 1.0F);
          }
        })
        .start();
  }

  private static boolean isEmpty(ItemStack stack) {
    return stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0;
  }

  /**
   * The heap template must not be a live inventory mirror; the same click that
   * deposits also rewrites the slot it came from.
   */
  private static ItemStack detach(ItemStack stack) {
    ItemStack copy = stack.clone();
    copy.setAmount(1);
    return copy;
  }

  private static final class BackpackView implements InventoryHolder {
    private final String backpackId;
    private final BackpackItem.Mode mode;
    private final int capacity;
    private final AtomicBoolean dirty = new AtomicBoolean();
    private Inventory inventory;
    private List<BackpackItem.HeapSlot> heap = new ArrayList<>();
    private int page;
    private int storedBytes;

    private BackpackView(String backpackId, BackpackItem.Mode mode, int capacity) {
      this.backpackId = backpackId;
      this.mode = mode;
      this.capacity = capacity;
    }

    @Override
    public Inventory getInventory() {
      return inventory;
    }
  }

  @ConfigDescription("Craft a Backpack that stores whole stacks and opens as its own container, in slot mode or bundle mode.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Backpack capacity. Only container representable sizes are valid: 9, 18, 27, 36, 45 and 54. Any other value snaps to the nearest valid size, and anything under 9 or over 54 clamps to 9 or 54. In slot mode this is the number of slots and every slot holds one ordinary stack, so 9 slots is 9 stacks. In bundle mode this is how many stacks worth of weight the backpack holds, using vanilla bundle weights where an item that stacks to 64 costs 1, an item that stacks to 16 costs 4, and an unstackable item costs 64.", impact = "Higher values let one Backpack carry more; 9 opens as a 3x3 view and 54 opens as a double chest view.")
    int slots = 9;
    @ConfigDoc(value = "Storage mode a newly crafted Backpack starts in. SLOTS gives one ordinary stack per slot and always shows every stored stack in one view. BUNDLE gives vanilla bundle weight semantics with a paged projection of the stored stacks. Any other value falls back to SLOTS.", impact = "Only affects Backpacks crafted after the change; existing Backpacks keep the mode they were crafted or cycled into.")
    String defaultStorageMode = "SLOTS";
    @ConfigDoc(value = "Allows a player to switch a Backpack between slot mode and bundle mode by crafting it alone in a crafting grid. The Backpack has to be empty before it will switch.", impact = "False locks every Backpack to the mode it was crafted in, so a server can force a single storage model.")
    boolean allowModeToggle = true;
    @ConfigDoc(value = "Maximum serialized size in bytes of the contents stored on one Backpack. Deposits that would push the stored data past this limit are refused, and anything already past it is handed back to the player instead of being dropped.", impact = "Higher values allow deeper nested containers inside a Backpack at the cost of larger item data on disk.")
    int maxStoredBytes = 262144;
    @ConfigDoc(value = "Denies depositing a shulker box or a vanilla bundle that itself contains an Adapt Backpack. A Backpack can never be put directly inside another Backpack regardless of this setting.", impact = "True blocks indirectly nested Backpacks entirely; false relies only on the stored size limit.")
    boolean denyNestedContainers = true;

    public Config() {
      permanent = true;
      baseCost = 5;
      costFactor = 1;
      maxLevel = 1;
    }
  }
}
