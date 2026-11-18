package art.arcane.adapt.api.potion;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.BrewEvent;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BrewingStandRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BrewingManager {

  private static final Id GUNPOWDER = Id.minecraft("gunpowder");
  private static final Id DRAGON_BREATH = Id.minecraft("dragon_breath");
  private static final Map<BrewingRecipe, Set<String>> recipes = new ConcurrentHashMap<>();
  private static final Map<BlockRef, BrewingTask> activeTasks = new ConcurrentHashMap<>();

  private final Map<UUID, PendingBrewingClick> pendingClicks = new ConcurrentHashMap<>();

  public static void registerRecipe(String adaptation, BrewingRecipe recipe) {
    if (adaptation == null || adaptation.isBlank() || recipe == null) {
      return;
    }

    recipes.computeIfAbsent(recipe, unused -> ConcurrentHashMap.newKeySet()).add(adaptation);
  }

  public static void unregisterRecipe(String adaptation, BrewingRecipe recipe) {
    if (adaptation == null || recipe == null) {
      return;
    }

    recipes.computeIfPresent(recipe, (registeredRecipe, adaptations) -> {
      adaptations.remove(adaptation);
      return adaptations.isEmpty() ? null : adaptations;
    });
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onInventoryClick(InventoryClickEvent e) {
    if (e.isCancelled()) {
      return;
    }

    if (e.viewKind() != InventoryKind.BREWING) {
      return;
    }
    PlayerRef clicker = e.player();
    Optional<BlockRef> standBlock = clicker.openContainerBlock();
    if (standBlock.isEmpty() || standBlock.get().brewingStand().isEmpty()) {
      return;
    }
    if (resolveReadyPlayer(clicker) == null) {
      return;
    }
    pendingClicks.put(clicker.uuid(), new PendingBrewingClick(standBlock.get(), clicker));
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void afterInventoryClick(InventoryClickEvent e) {
    PendingBrewingClick pending = pendingClicks.remove(e.player().uuid());
    if (pending == null || e.isCancelled()) {
      return;
    }
    if (resolveReadyPlayer(pending.player()) == null) {
      return;
    }

    ItemRef cursor = null;
    if (isCustomIngredientClick(e, pending.standBlock())) {
      cursor = e.cursor().copy();
      e.setCancelled(true);
    }

    ItemRef expectedCursor = cursor;
    Tasks.runEntity(pending.player(), () -> processClick(pending, expectedCursor), 1);
  }

  private void processClick(PendingBrewingClick pending, ItemRef expectedCursor) {
    PlayerRef clicker = pending.player();
    AdaptPlayer adaptPlayer = resolveReadyPlayer(clicker);
    if (adaptPlayer == null) {
      return;
    }
    BlockRef standBlock = pending.standBlock();
    if (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(standBlock.location())) {
      return;
    }
    if (!isSameOpenStand(clicker, standBlock)) {
      return;
    }
    Optional<BrewingStandRef> found = standBlock.brewingStand();
    if (found.isEmpty()) {
      return;
    }
    BrewingStandRef stand = found.get();

    if (expectedCursor != null) {
      PlayerInventoryRef inventory = clicker.inventory();
      ItemRef currentCursor = inventory.cursor();
      if (!stand.ingredient().isEmpty()
          || currentCursor.isEmpty()
          || !currentCursor.isSimilar(expectedCursor)
          || currentCursor.amount() != expectedCursor.amount()) {
        return;
      }
      stand.setIngredient(expectedCursor);
      inventory.setCursor(AdaptCore.platform().host().items().empty());
    }

    BrewingRecipe recipe = findMatchingRecipe(standBlock);
    if (recipe == null) {
      BrewingTask removed = activeTasks.remove(standBlock);
      if (removed != null) {
        removed.cancel();
      }
      return;
    }

    Set<String> requiredAdaptations = recipes.get(recipe);
    BrewingTask active = activeTasks.get(standBlock);
    if (!playerHasRequiredAdaptation(adaptPlayer, requiredAdaptations)) {
      if (active != null && !active.getRecipe().equals(recipe)) {
        BrewingTask removed = activeTasks.remove(standBlock);
        if (removed != null) {
          removed.cancel();
        }
      }
      return;
    }

    if (active != null && active.getRecipe().equals(recipe)) {
      return;
    }

    if (active != null) {
      BrewingTask removed = activeTasks.remove(standBlock);
      if (removed != null) {
        removed.cancel();
      }
    }

    BrewingTask task = BrewingTask.create(
        recipe,
        standBlock,
        clicker.uuid(),
        finished -> activeTasks.remove(standBlock, finished)
    );
    activeTasks.put(standBlock, task);
    task.start();
  }

  private boolean isCustomIngredientClick(InventoryClickEvent event, BlockRef standBlock) {
    Optional<BrewingStandRef> stand = standBlock.brewingStand();
    return stand.isPresent()
        && stand.get().ingredient().isEmpty()
        && BrewingTask.acceptsIngredient(event.cursor())
        && event.rawSlot() == 3
        && event.clickedKind() == InventoryKind.BREWING
        && event.click() == ClickKind.LEFT;
  }

  static boolean isSameOpenStand(PlayerRef player, BlockRef expectedBlock) {
    Optional<BlockRef> current = player.openContainerBlock();
    return current.isPresent() && current.get().equals(expectedBlock) && current.get().brewingStand().isPresent();
  }

  @Handle(ignoreCancelled = false)
  public void onBrew(BrewEvent e) {
    ItemRef ingredient = e.ingredient();
    if (ingredient.isEmpty()) {
      return;
    }

    Id m = ingredient.type();
    if (!m.equals(GUNPOWDER) && !m.equals(DRAGON_BREATH)) {
      return;
    }
    List<ItemRef> potions = e.potions();
    for (int i = 0; i < BrewingStandRef.BOTTLE_SLOTS && i < potions.size(); i++) {
      ItemRef s = potions.get(i);
      if (s.isEmpty()) continue;
      Optional<PotionContents> contents = s.potionContents();
      if (contents.isEmpty() || contents.get().potion() != null)
        continue;
      ItemRef newStack = s.copyAs(m.equals(GUNPOWDER) ? PotionBuilder.Type.SPLASH.getMaterial() : PotionBuilder.Type.LINGERING.getMaterial());
      e.results().set(i, newStack);
    }
  }

  private BrewingRecipe findMatchingRecipe(BlockRef standBlock) {
    if (standBlock == null) {
      return null;
    }

    for (BrewingRecipe recipe : recipes.keySet()) {
      if (BrewingTask.isValid(recipe, standBlock)) {
        return recipe;
      }
    }

    return null;
  }

  private boolean playerHasRequiredAdaptation(AdaptPlayer player, Set<String> requiredAdaptations) {
    if (player == null || requiredAdaptations == null || requiredAdaptations.isEmpty()) {
      return false;
    }

    for (String adaptation : requiredAdaptations) {
      if (player.hasAdaptation(adaptation)) {
        return true;
      }
    }

    return false;
  }

  private AdaptPlayer resolveReadyPlayer(PlayerRef player) {
    AdaptCore core = AdaptCore.current();
    AdaptServer adaptServer = core == null ? null : core.getAdaptServer();
    if (adaptServer == null) {
      return null;
    }
    AdaptPlayer adaptPlayer = adaptServer.getOnlineAdaptPlayer(player.uuid());
    return adaptPlayer != null && adaptPlayer.isRuntimeReady()
        && adaptPlayer.getPlayer() == player ? adaptPlayer : null;
  }

  private record PendingBrewingClick(BlockRef standBlock, PlayerRef player) {
  }
}
