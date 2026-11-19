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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdvancementSpec;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.item.BackpackItem;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.AdvancementMessages;
import art.arcane.adapt.localization.catalog.CraftingMessages;
import art.arcane.adapt.localization.catalog.ItemsMessages;
import art.arcane.adapt.platform.EngineFeature;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryDragEvent;
import art.arcane.adapt.platform.event.inventory.PrepareItemCraftEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerDropItemEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerItemHeldEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.StorageView;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * Adapt Backpacks. The crafted item is a tagged bundle whose
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
  public static final PlayerPreference<CraftingPreferences.Storage> STORAGE = CraftingPreferences.storage("storage", CraftingMessages.PREFERENCE_CRAFTINGBACKPACKS_STORAGE);
  public static final PlayerPreference<CommonPreferences.Toggle> MODE_SWITCH = CommonPreferences.toggle("mode-switch", CraftingMessages.PREFERENCE_CRAFTINGBACKPACKS_MODE_SWITCH, CommonPreferences.Toggle.ON);

  static final String CRAFT_RECIPE_KEY = "crafting-backpacks";
  static final String CYCLE_RECIPE_KEY = "crafting-backpacks-mode";
  private static final Id CRAFT_RECIPE = Id.adapt(CRAFT_RECIPE_KEY);
  private static final Id CYCLE_RECIPE = Id.adapt(CYCLE_RECIPE_KEY);
  private static final Id BUNDLE = Id.minecraft("bundle");
  private static final Id GRAY_PANE = Id.minecraft("gray_stained_glass_pane");
  private static final Id ARROW = Id.minecraft("arrow");
  private static final int NAV_PREV = 0;
  private static final int NAV_INFO = 4;
  private static final int NAV_NEXT = 8;
  private static final long DECLINE_COOLDOWN_MILLIS = 1200L;
  private static final long OPEN_COOLDOWN_MILLIS = 200L;

  private final ConcurrentMap<UUID, BackpackView> views = new ConcurrentHashMap<>();
  private final ConcurrentMap<StorageView, BackpackView> windows = new ConcurrentHashMap<>();
  private final Cooldowns declineCd = cooldowns();
  private final Cooldowns openCd = cooldowns();

  public CraftingBackpacks() {
    super("crafting-backpacks");
    needs(PlatformNeed.feature(EngineFeature.BUNDLES));
    registerConfiguration(Config.class);
    setIcon(BUNDLE);
    setInterval(17779);
    registerRecipe(new RecipeSpec.Shaped(CRAFT_RECIPE, recipeShape(),
        Map.of('L', Ingredient.of(Id.minecraft("leather")), 'C', Ingredient.of(Id.minecraft("chest"))),
        template()));
    // A real registered recipe is what makes the mode cycle consume exactly one
    // backpack and hand back exactly one. A result injected onto a matrix that
    // matches no recipe is taken for free, which duplicates the input.
    registerRecipe(new RecipeSpec.Shapeless(CYCLE_RECIPE, List.of(Ingredient.of(BUNDLE)), template()));
    AdvancementSpec backpacksCrafted = AdvancementSpec.challenge(
        "challenge_crafting_backpack_25",
        BUNDLE,
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
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, STORAGE, MODE_SWITCH);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(CraftingMessages.BACKPACKS_LORE1));
    v.addLore(C.YELLOW + "- " + C.GRAY + AdaptLanguage.text(CraftingMessages.BACKPACKS_LORE2));
    v.addLore(C.YELLOW + "- " + C.GRAY + AdaptLanguage.text(CraftingMessages.BACKPACKS_LORE3));
    v.addLore(C.YELLOW + "- " + C.GRAY + AdaptLanguage.text(CraftingMessages.BACKPACKS_LORE4));
  }

  private int capacity() {
    return BackpackItem.snapCapacity(getConfig().slots);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    if (!hasActiveAdaptation(player.getPlayer())) {
      closeAndPersist(player.getPlayer());
    }
  }

  private BackpackItem.Mode defaultMode() {
    return BackpackItem.Mode.parse(getConfig().defaultStorageMode);
  }

  private ItemRef template() {
    return BackpackItem.io.withData(BackpackItem.Data.of(null, defaultMode(), capacity(), 0));
  }

  // -------------------------------------------------------------- interaction

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    InteractAction action = e.action();
    if (action != InteractAction.RIGHT_CLICK_AIR && action != InteractAction.RIGHT_CLICK_BLOCK) {
      return;
    }

    PlayerRef p = e.player();
    if (e.hand() == Hand.OFF_HAND) {
      if (BackpackItem.isBackpack(p.inventory().offHand())) {
        e.setUseItem(UseResult.DENY);
      }
      return;
    }

    ItemRef hand = p.inventory().mainHand();
    if (!BackpackItem.isBackpack(hand)) {
      return;
    }

    // Captured before our own deny. The event's cancelled flag is unusable
    // here: an air click reports cancelled by default (the block half is
    // DENY when there is no block), which would make air opens impossible.
    boolean itemUseAlreadyDenied = e.useItem() == UseResult.DENY;

    // Vanilla bundle use dies here whether or not the player learned the
    // adaptation, so an unlearned backpack is dormant instead of a bundle.
    e.setUseItem(UseResult.DENY);
    if (p.isSneaking() || !hasActiveAdaptation(p) || itemUseAlreadyDenied) {
      return;
    }

    e.setCancelled(true);
    if (!openCd.isReady(p.uuid(), OPEN_COOLDOWN_MILLIS)) {
      return;
    }
    openCd.mark(p.uuid());
    openBackpack(p, hand);
  }

  private void openBackpack(PlayerRef p, ItemRef hand) {
    if (views.containsKey(p.uuid())) {
      return;
    }

    BackpackItem.Data data = BackpackItem.dataOf(hand);
    if (data == null) {
      return;
    }

    ItemRef[] stored;
    try {
      stored = BackpackItem.readContents(hand);
    } catch (IllegalStateException unreadable) {
      AdaptLog.warn("Backpack contents for " + p.uuid() + " could not be read: " + unreadable.getMessage());
      decline(p, CraftingMessages.BACKPACKS_UNREADABLE);
      return;
    }

    // Residue from a window where this adaptation was disabled and vanilla
    // bundling worked. Hand it straight back rather than budgeting it.
    for (ItemRef stranded : BackpackItem.drainBundleComponent(hand)) {
      deliver(p, stranded);
    }

    BackpackItem.Mode mode = BackpackItem.Mode.parse(data.getMode());
    int capacity = capacity();
    String id = ensureIdentity(p, hand, data, mode, capacity, stored);
    BackpackView view = mode == BackpackItem.Mode.BUNDLE
        ? openBundleView(id, capacity, stored)
        : openSlotView(p, id, capacity, stored);

    view.storedBytes = BackpackItem.storedBytes(hand);
    views.put(p.uuid(), view);
    StorageView window = AdaptCore.platform().menus().openStorage(p, AdaptLanguage.text(ItemsMessages.BACKPACK_NAME),
        view.size, this::windowClosed);
    if (window == null) {
      views.remove(p.uuid(), view);
      return;
    }
    view.inventory = window.inventory();
    windows.put(window, view);
    if (view.mode == BackpackItem.Mode.BUNDLE) {
      render(view);
    } else {
      place(view.inventory, view.stored, view.kept);
    }
    view.stored = null;
    view.kept = null;

    fx(p.eyeLocation(), FxPriority.TRANSITION)
        .particle(Particles.CRIT, 4, 0, 0.2D, 0, 0.2D, 0.01D)
        .chord(Sounds.ITEM_BUNDLE_INSERT, 0.5f, 1.2f, Sounds.BLOCK_WOOL_PLACE, 0.4f, 1.1f);
  }

  private BackpackView openSlotView(PlayerRef p, String id, int capacity, ItemRef[] stored) {
    ItemRef[] kept = BackpackItem.compact(stored);
    int overflow = BackpackItem.slotOverflow(kept.length);
    if (overflow > 0) {
      for (int index = kept.length - overflow; index < kept.length; index++) {
        deliver(p, kept[index]);
      }
      ItemRef[] trimmed = new ItemRef[BackpackItem.MAX_CAPACITY];
      System.arraycopy(kept, 0, trimmed, 0, BackpackItem.MAX_CAPACITY);
      kept = trimmed;
    }

    BackpackView view = new BackpackView(id, BackpackItem.Mode.SLOTS, capacity);
    view.size = BackpackItem.slotViewSize(kept.length, capacity);
    view.stored = stored;
    view.kept = kept;
    return view;
  }

  private BackpackView openBundleView(String id, int capacity, ItemRef[] stored) {
    BackpackView view = new BackpackView(id, BackpackItem.Mode.BUNDLE, capacity);
    view.heap = BackpackItem.readHeap(stored);
    view.size = BackpackItem.bundleViewSize(view.heap.size());
    return view;
  }

  /**
   * Keeps the player's own arrangement when the stored array still fits the
   * view, and falls back to a compacted fill when the view shrank.
   */
  private void place(InventoryRef inventory, ItemRef[] stored, ItemRef[] compacted) {
    int size = inventory.size();
    if (stored != null && stored.length <= size && BackpackItem.countStored(stored) == compacted.length) {
      for (int index = 0; index < stored.length; index++) {
        setSlot(inventory, index, stored[index]);
      }
      return;
    }

    for (int index = 0; index < compacted.length && index < size; index++) {
      setSlot(inventory, index, compacted[index]);
    }
  }

  private static void setSlot(InventoryRef inventory, int slot, ItemRef item) {
    if (item == null || item.isEmpty()) {
      inventory.clear(slot);
    } else {
      inventory.setItem(slot, item);
    }
  }

  private String ensureIdentity(PlayerRef p, ItemRef hand, BackpackItem.Data data,
                                BackpackItem.Mode mode, int capacity, ItemRef[] stored) {
    String id = data.getId();
    if (id != null && !duplicateIdentity(contents(p.inventory()), hand, id)) {
      return id;
    }

    String fresh = UUID.randomUUID().toString();
    int used = mode == BackpackItem.Mode.BUNDLE
        ? BackpackItem.heapWeight(BackpackItem.readHeap(stored))
        : BackpackItem.countStored(stored);
    BackpackItem.write(hand, BackpackItem.Data.of(fresh, mode, capacity, used), stored);
    p.inventory().setMainHand(hand);
    return fresh;
  }

  /** A cloned backpack would otherwise let a write-back land on the wrong stack. */
  static boolean duplicateIdentity(ItemRef[] contents, ItemRef self, String id) {
    if (contents == null || id == null) {
      return false;
    }

    int matches = 0;
    for (ItemRef candidate : contents) {
      if (candidate == null || candidate == self) {
        continue;
      }
      if (id.equals(BackpackItem.identityOf(candidate))) {
        matches++;
      }
    }

    return matches > 0;
  }

  private static ItemRef[] contents(InventoryRef inventory) {
    return inventory.contents().toArray(new ItemRef[0]);
  }

  // -------------------------------------------------------------------- views

  private BackpackView viewOf(PlayerRef p) {
    StorageView window = AdaptCore.platform().menus().currentStorage(p);
    return window == null ? null : windows.get(window);
  }

  private static boolean clickedView(InventoryClickEvent e, BackpackView view) {
    return e.rawSlot() >= 0 && e.rawSlot() < view.size;
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(InventoryClickEvent e) {
    PlayerRef p = e.player();

    BackpackView view = viewOf(p);
    if (view == null) {
      if (suppressesVanillaBundling(
          BackpackItem.isBackpack(e.cursor()),
          BackpackItem.isBackpack(e.currentItem()),
          isEmpty(e.cursor()),
          isEmpty(e.currentItem()),
          isRightClick(e.click()))) {
        e.setCancelled(true);
      }
      return;
    }

    if (views.get(p.uuid()) != view) {
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

  private static boolean isRightClick(ClickKind click) {
    return click == ClickKind.RIGHT || click == ClickKind.SHIFT_RIGHT;
  }

  private boolean guardsBackingItem(PlayerRef p, BackpackView view, InventoryClickEvent e) {
    String id = view.backpackId;
    if (!clickedView(e, view) && isBacking(e.currentItem(), id)) {
      return true;
    }
    if (isBacking(e.cursor(), id)) {
      return true;
    }
    if (e.click() == ClickKind.NUMBER_KEY
        && e.hotbarButton() >= 0
        && isBacking(p.inventory().item(e.hotbarButton()), id)) {
      return true;
    }

    return e.click() == ClickKind.SWAP_OFFHAND
        && (isBacking(e.currentItem(), id) || isBacking(p.inventory().offHand(), id));
  }

  static boolean isBacking(ItemRef stack, String id) {
    return id != null && id.equals(BackpackItem.identityOf(stack));
  }

  private void handleSlotClick(PlayerRef p, BackpackView view, InventoryClickEvent e) {
    ItemRef incoming = incomingSlotDeposit(p, view, e);
    if (incoming != null && !acceptsDeposit(p, view, incoming)) {
      e.setCancelled(true);
      return;
    }

    markDirty(p, view);
  }

  private ItemRef incomingSlotDeposit(PlayerRef p, BackpackView view, InventoryClickEvent e) {
    if (clickedView(e, view)) {
      if (e.click() == ClickKind.NUMBER_KEY && e.hotbarButton() >= 0) {
        return p.inventory().item(e.hotbarButton());
      }
      if (e.click() == ClickKind.SWAP_OFFHAND) {
        return p.inventory().offHand();
      }
      return isEmpty(e.cursor()) ? null : e.cursor();
    }

    if (e.action() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
      return isEmpty(e.currentItem()) ? null : e.currentItem();
    }

    return null;
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(InventoryDragEvent e) {
    PlayerRef p = e.player();

    BackpackView view = viewOf(p);
    if (view == null) {
      return;
    }

    boolean touchesTop = false;
    for (int raw : e.rawSlots()) {
      if (raw < view.size) {
        touchesTop = true;
        break;
      }
    }
    if (!touchesTop) {
      return;
    }

    if (views.get(p.uuid()) != view || view.mode == BackpackItem.Mode.BUNDLE) {
      e.setCancelled(true);
      return;
    }

    if (isBacking(e.oldCursor(), view.backpackId) || !acceptsDeposit(p, view, e.oldCursor())) {
      e.setCancelled(true);
      return;
    }

    markDirty(p, view);
  }

  // ------------------------------------------------------------- bundle model

  private void handleBundleClick(PlayerRef p, BackpackView view, InventoryClickEvent e) {
    if (!clickedView(e, view)) {
      if (e.action() != InventoryAction.MOVE_TO_OTHER_INVENTORY) {
        return;
      }
      e.setCancelled(true);
      ItemRef source = e.currentItem();
      if (isEmpty(source) || !acceptsDeposit(p, view, source)) {
        return;
      }
      int accepted = acceptableAmount(view, source, source.amount());
      if (accepted <= 0) {
        decline(p, CraftingMessages.BACKPACKS_DENY_FULL);
        return;
      }
      view.heap = BackpackItem.deposit(view.heap, detach(source), accepted);
      ItemRef remainder = source.copy();
      remainder.setAmount(source.amount() - accepted);
      e.setCurrentItem(remainder.amount() <= 0 ? items().empty() : remainder);
      commitBundle(p, view);
      return;
    }

    e.setCancelled(true);
    int display = BackpackItem.pageCapacity(view.size);
    int slot = e.slot();
    if (slot >= display) {
      navigate(p, view, slot - display);
      return;
    }

    ItemRef cursor = e.cursor();
    if (!isEmpty(cursor)) {
      depositCursor(p, view, cursor, isRightClick(e.click()) ? 1 : cursor.amount());
      return;
    }

    withdraw(p, view, BackpackItem.pageStart(view.page, display) + slot, isRightClick(e.click()));
  }

  private void depositCursor(PlayerRef p, BackpackView view, ItemRef cursor, int amount) {
    if (!acceptsDeposit(p, view, cursor)) {
      return;
    }

    int accepted = acceptableAmount(view, cursor, amount);
    if (accepted <= 0) {
      decline(p, CraftingMessages.BACKPACKS_DENY_FULL);
      return;
    }

    view.heap = BackpackItem.deposit(view.heap, detach(cursor), accepted);
    ItemRef remainder = cursor.copy();
    remainder.setAmount(cursor.amount() - accepted);
    p.inventory().setCursor(remainder.amount() <= 0 ? items().empty() : remainder);
    commitBundle(p, view);
  }

  private void withdraw(PlayerRef p, BackpackView view, int heapIndex, boolean half) {
    if (heapIndex < 0 || heapIndex >= view.heap.size()) {
      return;
    }

    BackpackItem.HeapSlot slot = view.heap.get(heapIndex);
    int want = half ? (slot.amount() + 1) / 2 : slot.amount();
    BackpackItem.HeapWithdrawal taken = BackpackItem.withdraw(view.heap, heapIndex, want);
    if (taken.taken() <= 0) {
      return;
    }

    ItemRef out = slot.template().copy();
    out.setAmount(taken.taken());
    view.heap = taken.heap();
    // Anything the player cannot hold goes straight back into the heap. A
    // withdrawal never drops on the floor and never evaporates.
    for (ItemRef leftover : p.inventory().addItem(out)) {
      view.heap = BackpackItem.deposit(view.heap, leftover, leftover.amount());
    }
    commitBundle(p, view);
  }

  private void navigate(PlayerRef p, BackpackView view, int navSlot) {
    int pages = BackpackItem.pageCount(view.heap.size(), BackpackItem.pageCapacity(view.size));
    if (navSlot == NAV_PREV && view.page > 0) {
      view.page--;
    } else if (navSlot == NAV_NEXT && view.page < pages - 1) {
      view.page++;
    } else {
      return;
    }

    render(view);
    p.updateInventory();
    sfx(p.location(), Sounds.UI_BUTTON_CLICK, 0.3f, 1.4f);
  }

  private void commitBundle(PlayerRef p, BackpackView view) {
    render(view);
    persist(p, view);
    p.updateInventory();
  }

  private int acceptableAmount(BackpackView view, ItemRef stack, int amount) {
    return BackpackItem.acceptableAmount(
        BackpackItem.unitWeightOf(stack),
        amount,
        BackpackItem.freeWeight(BackpackItem.heapWeight(view.heap), BackpackItem.capacityWeight(view.capacity))
    );
  }

  private void render(BackpackView view) {
    InventoryRef inventory = view.inventory;
    int display = BackpackItem.pageCapacity(view.size);
    int pages = BackpackItem.pageCount(view.heap.size(), display);
    view.page = BackpackItem.clampPage(view.page, pages);
    int start = BackpackItem.pageStart(view.page, display);

    for (int index = 0; index < display; index++) {
      int heapIndex = start + index;
      if (heapIndex >= view.heap.size()) {
        inventory.clear(index);
        continue;
      }
      BackpackItem.HeapSlot slot = view.heap.get(heapIndex);
      ItemRef shown = slot.template().copy();
      shown.setAmount(slot.amount());
      inventory.setItem(index, shown);
    }

    // The nav row reads as dead space: grey filler everywhere, counts on the
    // centre pane, and arrows only once there is actually something to page.
    for (int index = 0; index < BackpackItem.NAV_ROW; index++) {
      inventory.setItem(display + index, navFiller());
    }
    inventory.setItem(display + NAV_INFO, navInfo(view, pages));
    if (pages > 1) {
      inventory.setItem(display + NAV_PREV, navArrow(CraftingMessages.BACKPACKS_PREV_PAGE));
      inventory.setItem(display + NAV_NEXT, navArrow(CraftingMessages.BACKPACKS_NEXT_PAGE));
    }
  }

  private ItemRef navFiller() {
    ItemRef filler = items().create(GRAY_PANE, 1);
    filler.setHideTooltip(true);
    return filler;
  }

  private ItemRef navInfo(BackpackView view, int pages) {
    ItemRef info = items().create(GRAY_PANE, 1);
    info.setDisplayName(C.GRAY + AdaptLanguage.text(
        ItemsMessages.BACKPACK_ITEMS_STORED,
        trusted("used", String.valueOf(BackpackItem.heapWeight(view.heap))),
        trusted("total", String.valueOf(BackpackItem.capacityWeight(view.capacity)))
    ));
    if (pages > 1) {
      info.setLore(List.of(C.DARK_GRAY + AdaptLanguage.text(
          CraftingMessages.BACKPACKS_PAGE,
          trusted("page", String.valueOf(view.page + 1)),
          trusted("pages", String.valueOf(pages))
      )));
    }
    return info;
  }

  private ItemRef navArrow(TextKey label) {
    ItemRef arrow = items().create(ARROW, 1);
    arrow.setDisplayName(C.YELLOW + AdaptLanguage.text(label));
    return arrow;
  }

  // ---------------------------------------------------------------- deposits

  private boolean acceptsDeposit(PlayerRef p, BackpackView view, ItemRef incoming) {
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

  private int measure(ItemRef stack) {
    try {
      return BackpackItem.serializedSize(new ItemRef[]{stack});
    } catch (RuntimeException error) {
      return 0;
    }
  }

  private void decline(PlayerRef p, TextKey message) {
    if (!declineCd.isReady(p.uuid(), DECLINE_COOLDOWN_MILLIS)) {
      return;
    }

    declineCd.mark(p.uuid());
    AdaptHud.actionBar(p, C.RED + AdaptLanguage.text(message));
    sfx(p.location(), Sounds.BLOCK_DISPENSER_FAIL, 0.4f, 0.9f);
  }

  // --------------------------------------------------------------- persistence

  private void markDirty(PlayerRef p, BackpackView view) {
    if (!view.dirty.compareAndSet(false, true)) {
      return;
    }

    if (!Tasks.runEntity(p, () -> {
      view.dirty.set(false);
      if (views.get(p.uuid()) == view) {
        persist(p, view);
      }
    }, 1)) {
      view.dirty.set(false);
    }
  }

  private void persist(PlayerRef p, BackpackView view) {
    ItemRef[] contents;
    int used;
    if (view.mode == BackpackItem.Mode.BUNDLE) {
      contents = BackpackItem.writeHeap(view.heap);
      used = BackpackItem.heapWeight(view.heap);
    } else {
      // Slot indexed with its holes intact; the stored item array format keeps
      // empty entries in order, so the player's own arrangement survives.
      contents = slotContents(view.inventory);
      used = BackpackItem.countStored(contents);
    }

    List<ItemRef> evicted = new ArrayList<>();
    contents = trimToCeiling(contents, getConfig().maxStoredBytes, evicted);
    if (view.mode != BackpackItem.Mode.BUNDLE) {
      used = BackpackItem.countStored(contents);
    }

    int slot = BackpackItem.identitySlot(contents(p.inventory()), view.backpackId);
    if (slot < 0) {
      AdaptLog.warn("Backpack " + view.backpackId + " left " + p.uuid()
          + " while open; returning its contents.");
      for (ItemRef stack : contents) {
        deliver(p, stack);
      }
      for (ItemRef stack : evicted) {
        deliver(p, stack);
      }
      // The view is now a phantom copy of what was just delivered. Empty it and
      // close it, or the displayed stacks could be taken out a second time.
      view.heap = new ArrayList<>();
      for (int index = 0; index < view.inventory.size(); index++) {
        view.inventory.clear(index);
      }
      if (views.remove(p.uuid(), view) && p.isOnline()) {
        Tasks.runEntity(p, p::closeInventory);
      }
      return;
    }

    ItemRef backing = p.inventory().item(slot);
    if (backing == null || backing.isEmpty()) {
      return;
    }

    // Mutated in place rather than swapped back through the inventory so a
    // death drop snapshot taken from this same stack still carries the write.
    BackpackItem.write(backing, BackpackItem.Data.of(view.backpackId, view.mode, view.capacity, used), contents);
    view.storedBytes = BackpackItem.storedBytes(backing);
    for (ItemRef stack : evicted) {
      deliver(p, stack);
    }
  }

  private static ItemRef[] slotContents(InventoryRef inventory) {
    ItemRef[] contents = new ItemRef[inventory.size()];
    for (int index = 0; index < contents.length; index++) {
      ItemRef item = inventory.item(index);
      contents[index] = isEmpty(item) ? null : item.copy();
    }
    return contents;
  }

  /** Nothing is deleted; anything past the ceiling comes back to the player. */
  private ItemRef[] trimToCeiling(ItemRef[] contents, int ceiling, List<ItemRef> evicted) {
    ItemRef[] kept = contents;
    while (BackpackItem.countStored(kept) > 0
        && BackpackItem.serializedSize(kept) > ceiling) {
      List<ItemRef> remaining = new ArrayList<>();
      for (ItemRef stack : kept) {
        remaining.add(stack);
      }
      for (int index = remaining.size() - 1; index >= 0; index--) {
        ItemRef stack = remaining.get(index);
        if (stack == null || stack.type().isAir()) {
          continue;
        }
        evicted.add(stack);
        remaining.set(index, null);
        break;
      }
      kept = remaining.toArray(new ItemRef[0]);
    }

    return kept;
  }

  private void deliver(PlayerRef p, ItemRef stack) {
    if (isEmpty(stack)) {
      return;
    }

    for (ItemRef leftover : p.inventory().addItem(stack)) {
      p.world().dropItemNaturally(p.location().position(), leftover);
    }
  }

  private void windowClosed(StorageView window) {
    BackpackView view = windows.remove(window);
    PlayerRef p = window.viewer();
    if (view == null || !views.remove(p.uuid(), view)) {
      return;
    }

    persist(p, view);
  }

  @Handle(priority = EventPriority.LOWEST)
  public void on(PlayerDeathEvent e) {
    closeAndPersist(e.player());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    closeAndPersist(e.player());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerItemHeldEvent e) {
    if (views.containsKey(e.player().uuid())) {
      e.player().closeInventory();
    }
  }

  @Handle
  public void on(PlayerDropItemEvent e) {
    BackpackView view = views.get(e.player().uuid());
    if (view != null && isBacking(e.itemDrop().item(), view.backpackId)) {
      e.setCancelled(true);
    }
  }

  @Handle
  public void on(PlayerSwapHandItemsEvent e) {
    BackpackView view = views.get(e.player().uuid());
    if (view == null) {
      return;
    }

    if (isBacking(e.mainHand(), view.backpackId) || isBacking(e.offHand(), view.backpackId)) {
      e.setCancelled(true);
    }
  }

  private void closeAndPersist(PlayerRef p) {
    BackpackView view = views.remove(p.uuid());
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
      PlayerRef player = AdaptCore.platform().host().player(playerId);
      if (player == null) {
        views.remove(playerId);
        continue;
      }
      if (!Tasks.runEntity(player, () -> closeAndPersist(player))) {
        views.remove(playerId);
      }
    }
    super.unregister();
  }

  // -------------------------------------------------------------- mode cycle

  @Handle
  public void on(PrepareItemCraftEvent e) {
    Id recipe = e.recipeKey();
    ItemRef[] matrix = e.matrix().toArray(new ItemRef[0]);
    PlayerRef player = e.player();
    if (recipe != null && CRAFT_RECIPE.equals(recipe)) {
      if (!hasActiveAdaptation(player)) {
        e.setResult(items().empty());
        return;
      }
      CraftingPreferences.Storage preferred = preference(player, STORAGE);
      BackpackItem.Mode mode = preferred == CraftingPreferences.Storage.SERVER ? defaultMode() : BackpackItem.Mode.valueOf(preferred.name());
      e.setResult(BackpackItem.io.withData(BackpackItem.Data.of(null, mode, capacity(), 0)));
    }
    if (recipe == null || !CYCLE_RECIPE.equals(recipe)) {
      // A backpack consumed as an ingredient anywhere else would delete its
      // stored contents along with the item.
      if (!CRAFT_RECIPE.equals(recipe) && matrixHasBackpack(matrix)) {
        e.setResult(items().empty());
      }
      return;
    }

    if (!getConfig().allowModeToggle || !isLoneBackpack(matrix)
        || !hasActiveAdaptation(player)
        || !preferenceEnabled(player, MODE_SWITCH)) {
      e.setResult(items().empty());
      return;
    }

    ItemRef backpack = firstBackpack(matrix);
    if (!isEmptyBackpack(backpack)) {
      e.setResult(items().empty());
      decline(player, CraftingMessages.BACKPACKS_CYCLE_NEEDS_EMPTY);
      return;
    }

    ItemRef cycled = cycled(backpack);
    e.setResult(cycled == null ? items().empty() : cycled);
  }

  static boolean matrixHasBackpack(ItemRef[] matrix) {
    if (matrix == null) {
      return false;
    }

    for (ItemRef stack : matrix) {
      if (BackpackItem.isBackpack(stack)) {
        return true;
      }
    }

    return false;
  }

  /** Exactly one backpack, a single one, and nothing else in the grid. */
  static boolean isLoneBackpack(ItemRef[] matrix) {
    if (matrix == null) {
      return false;
    }

    int backpacks = 0;
    for (ItemRef stack : matrix) {
      if (isEmpty(stack)) {
        continue;
      }
      if (!BackpackItem.isBackpack(stack)) {
        return false;
      }
      if (stack.amount() > 1) {
        return false;
      }
      backpacks++;
    }

    return backpacks == 1;
  }

  static ItemRef firstBackpack(ItemRef[] matrix) {
    if (matrix == null) {
      return null;
    }

    for (ItemRef stack : matrix) {
      if (BackpackItem.isBackpack(stack)) {
        return stack;
      }
    }

    return null;
  }

  static boolean isEmptyBackpack(ItemRef backpack) {
    if (!BackpackItem.isBackpack(backpack)) {
      return false;
    }

    try {
      return BackpackItem.countStored(BackpackItem.readContents(backpack)) == 0;
    } catch (IllegalStateException unreadable) {
      return false;
    }
  }

  private ItemRef cycled(ItemRef backpack) {
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

  @Handle
  public void on(CraftItemEvent e) {
    PlayerRef p = e.player();

    Id recipe = e.recipeKey();
    if (recipe == null || CYCLE_RECIPE.equals(recipe) || !CRAFT_RECIPE.equals(recipe)) {
      return;
    }
    if (!hasActiveAdaptation(p)) {
      return;
    }

    addStat(p, "crafting.backpacks.bundles-crafted", 1);
    cinchDrawstring(p.location().add(0, 1, 0));
  }

  private void cinchDrawstring(Loc center) {
    timeline(center)
        .duration(8)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          double radius = 0.8D - (0.7D * progress);
          fx.ring(Particles.CRIT_MAGIC, radius, 12, 0.0D);
          fx.particle(Particles.PORTAL, 2, 0, 0.2D, 0, radius, 0.02D);
          if (tick == 0) {
            fx.dustRing(new Rgb(160, 120, 60), 0.6D, 16, 1.0F);
            fx.chord(Sounds.ITEM_BUNDLE_INSERT, 0.8F, 0.8F, Sounds.BLOCK_WOOL_PLACE, 0.6F, 1.2F, Sounds.ITEM_ARMOR_EQUIP_LEATHER, 0.5F, 1.0F);
          }
        })
        .start();
  }

  private static boolean isEmpty(ItemRef stack) {
    return stack == null || stack.isEmpty() || stack.type().isAir() || stack.amount() <= 0;
  }

  /**
   * The heap template must not be a live inventory mirror; the same click that
   * deposits also rewrites the slot it came from.
   */
  private static ItemRef detach(ItemRef stack) {
    ItemRef copy = stack.copy();
    copy.setAmount(1);
    return copy;
  }

  private static ItemOps items() {
    return AdaptCore.platform().host().items();
  }

  private static final class BackpackView {
    private final String backpackId;
    private final BackpackItem.Mode mode;
    private final int capacity;
    private final AtomicBoolean dirty = new AtomicBoolean();
    private InventoryRef inventory;
    private int size;
    private ItemRef[] stored;
    private ItemRef[] kept;
    private List<BackpackItem.HeapSlot> heap = new ArrayList<>();
    private int page;
    private int storedBytes;

    private BackpackView(String backpackId, BackpackItem.Mode mode, int capacity) {
      this.backpackId = backpackId;
      this.mode = mode;
      this.capacity = capacity;
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
