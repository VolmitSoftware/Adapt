package art.arcane.adapt.api.potion;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.reflect.Reflect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BrewingManager implements Listener {

  private static final Map<BrewingRecipe, Set<String>> recipes = new ConcurrentHashMap<>();
  private static final Map<Location, BrewingTask> activeTasks = new ConcurrentHashMap<>();

  private final Map<InventoryClickEvent, PendingBrewingClick> pendingClicks = new ConcurrentHashMap<>();

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

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onInventoryClick(InventoryClickEvent e) {
    if (e.isCancelled()) {
      return;
    }

    Inventory topInventory = e.getView().getTopInventory();
    if (!(topInventory instanceof BrewerInventory inv)) {
      return;
    }
    BrewingStand stand = inv.getHolder();
    if (stand == null) {
      return;
    }
    Player clicker = (Player) e.getWhoClicked();
    if (resolveReadyPlayer(clicker) == null) {
      return;
    }
    pendingClicks.put(e, new PendingBrewingClick(inv, stand.getBlock(), clicker));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void afterInventoryClick(InventoryClickEvent e) {
    PendingBrewingClick pending = pendingClicks.remove(e);
    if (pending == null || e.isCancelled()) {
      return;
    }
    if (resolveReadyPlayer(pending.player()) == null) {
      return;
    }

    ItemStack cursor = null;
    if (isCustomIngredientClick(e, pending.inventory())) {
      cursor = e.getCursor().clone();
      e.setCancelled(true);
    }

    ItemStack expectedCursor = cursor;
    J.runEntity(pending.player(), () -> processClick(pending, expectedCursor), 1);
  }

  private void processClick(PendingBrewingClick pending, ItemStack expectedCursor) {
    Player clicker = pending.player();
    AdaptPlayer adaptPlayer = resolveReadyPlayer(clicker);
    if (adaptPlayer == null) {
      return;
    }
    BrewerInventory inventory = pending.inventory();
    Block standBlock = pending.standBlock();
    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(standBlock.getLocation())) {
      return;
    }
    if (!isSameOpenStand(clicker, inventory, standBlock)) {
      return;
    }

    if (expectedCursor != null) {
      ItemStack currentCursor = clicker.getItemOnCursor();
      if (inventory.getIngredient() != null
          || currentCursor == null
          || !currentCursor.isSimilar(expectedCursor)
          || currentCursor.getAmount() != expectedCursor.getAmount()) {
        return;
      }
      inventory.setIngredient(expectedCursor);
      clicker.setItemOnCursor(null);
    }

    Location standLocation = standBlock.getLocation();
    BrewingRecipe recipe = findMatchingRecipe(standLocation);
    if (recipe == null) {
      BrewingTask removed = activeTasks.remove(standLocation);
      if (removed != null) {
        removed.cancel();
      }
      return;
    }

    Set<String> requiredAdaptations = recipes.get(recipe);
    BrewingTask active = activeTasks.get(standLocation);
    if (!playerHasRequiredAdaptation(adaptPlayer, requiredAdaptations)) {
      if (active != null && !active.getRecipe().equals(recipe)) {
        BrewingTask removed = activeTasks.remove(standLocation);
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
      BrewingTask removed = activeTasks.remove(standLocation);
      if (removed != null) {
        removed.cancel();
      }
    }

    BrewingTask task = BrewingTask.create(
        recipe,
        standLocation,
        clicker.getUniqueId(),
        finished -> activeTasks.remove(standLocation, finished)
    );
    activeTasks.put(standLocation, task);
    task.start();
  }

  private boolean isCustomIngredientClick(InventoryClickEvent event, BrewerInventory inventory) {
    ItemStack cursor = event.getCursor();
    return inventory.getIngredient() == null
        && cursor != null
        && !cursor.getType().isAir()
        && event.getRawSlot() == 3
        && event.getClickedInventory() != null
        && event.getClickedInventory().getType() == InventoryType.BREWING
        && event.getClick() == ClickType.LEFT;
  }

  static boolean isSameOpenStand(Player player, BrewerInventory expectedInventory, Block expectedBlock) {
    Inventory currentTop = player.getOpenInventory().getTopInventory();
    if (currentTop != expectedInventory || !(currentTop.getHolder() instanceof BrewingStand currentStand)) {
      return false;
    }
    return currentStand.getBlock().equals(expectedBlock);
  }

  @EventHandler
  public void onBrew(BrewEvent e) {
    ItemStack ingredient = e.getContents().getIngredient();
    if (ingredient == null) {
      return;
    }

    Material m = ingredient.getType();
    if (m != Material.GUNPOWDER && m != Material.DRAGON_BREATH) {
      return;
    }
    for (int i = 0; i < 3; i++) {
      ItemStack s = e.getContents().getItem(i);
      if (s == null) continue;
      PotionMeta meta = (PotionMeta) s.getItemMeta();
      java.util.Optional<org.bukkit.potion.PotionType> opt = Reflect.getEnum(PotionType.class, "UNCRAFTABLE");
      if (opt.isEmpty() && meta.getBasePotionData() != null)
        continue;
      if (opt.isPresent() && meta.getBasePotionData().getType() == opt.get())
        continue;
      ItemStack newStack = s.clone();
      if (m == Material.GUNPOWDER) {
        newStack.setType(Material.SPLASH_POTION);
      } else {
        newStack.setType(Material.LINGERING_POTION);
                /*PotionMeta meta = (PotionMeta)newStack.getItemMeta();
                List<PotionEffect> newEffects = Lists.newArrayList();
                meta.getCustomEffects().forEach(effect -> newEffects.add(new PotionEffect(effect.getType(), effect.getDuration() / 4, effect.getAmplifier())));
                meta.clearCustomEffects();
                newEffects.forEach(effect -> meta.addCustomEffect(effect, true));
                newStack.setItemMeta(meta);*/
      }
      e.getResults().set(i, newStack);
    }
  }

  private BrewingRecipe findMatchingRecipe(Location standLocation) {
    if (standLocation == null) {
      return null;
    }

    for (BrewingRecipe recipe : recipes.keySet()) {
      if (BrewingTask.isValid(recipe, standLocation)) {
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

  private AdaptPlayer resolveReadyPlayer(Player player) {
    AdaptServer adaptServer = Adapt.instance == null ? null : Adapt.instance.getAdaptServer();
    if (adaptServer == null) {
      return null;
    }
    AdaptPlayer adaptPlayer = adaptServer.getOnlineAdaptPlayer(player.getUniqueId());
    return adaptPlayer != null && adaptPlayer.isRuntimeReady()
        && adaptPlayer.getPlayer() == player ? adaptPlayer : null;
  }

  private record PendingBrewingClick(BrewerInventory inventory, Block standBlock, Player player) {
  }
}
