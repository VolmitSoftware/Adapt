package art.arcane.adapt.api.potion;

import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public final class BrewingTask implements Runnable {

  private static final int DEFAULT_BREW_TIME = 400;

  private final BrewingRecipe recipe;
  private final Location location;
  private final UUID brewerId;
  private final Consumer<BrewingTask> completion;
  private final int totalBrewTime;
  private int brewTime;
  private boolean initialized;
  private volatile boolean cancelled;

  private BrewingTask(Context context) {
    this.recipe = Objects.requireNonNull(context.recipe());
    this.location = Objects.requireNonNull(context.location()).clone();
    this.brewerId = Objects.requireNonNull(context.brewerId());
    this.completion = context.completion();
    this.totalBrewTime = Math.max(1, recipe.getBrewingTime());
    this.brewTime = totalBrewTime;
  }

  public static BrewingTask create(BrewingRecipe recipe, Location location, UUID brewerId, Consumer<BrewingTask> completion) {
    return new BrewingTask(new Context(recipe, location, brewerId, completion));
  }

  public void start() {
    if (!J.runAt(location, this::run)) {
      cancel();
    }
  }

  public BrewingRecipe getRecipe() {
    return recipe;
  }

  public static ItemStack decrease(ItemStack source, int amount) {
    if (source == null || source.getAmount() <= amount) {
      return new ItemStack(Material.AIR);
    }

    ItemStack result = source.clone();
    result.setAmount(result.getAmount() - Math.max(0, amount));
    return result;
  }

  static int fuelItemsRequired(int storedFuel, int fuelCost) {
    int uncoveredCost = Math.max(0, fuelCost - Math.max(0, storedFuel));
    return (uncoveredCost + 19) / 20;
  }

  static int remainingFuel(int storedFuel, int fuelCost) {
    int fuelItems = fuelItemsRequired(storedFuel, fuelCost);
    return Math.max(0, storedFuel) + (fuelItems * 20) - Math.max(0, fuelCost);
  }

  public static boolean isValid(BrewingRecipe recipe, Location loc) {
    if (recipe == null || loc == null || loc.getBlock().getType() != Material.BREWING_STAND) {
      return false;
    }

    BrewingStand block = (BrewingStand) loc.getBlock().getState();
    BrewerInventory inv = block.getInventory();
    if (!hasRecipeInputs(recipe, inv)) {
      return false;
    }

    int totalFuel = (inv.getFuel() != null && inv.getFuel().getType() != Material.AIR ? inv.getFuel().getAmount() * 20 : 0) + block.getFuelLevel();
    if (totalFuel < recipe.getFuelCost()) {
      return false;
    }

    return totalFuel >= Math.max(0, recipe.getFuelCost());
  }

  @Override
  public void run() {
    if (cancelled) {
      return;
    }

    if (location.getBlock().getType() != Material.BREWING_STAND) {
      cancel();
      return;
    }

    BrewingStand block = (BrewingStand) this.location.getBlock().getState();
    BrewerInventory inventory = block.getInventory();
    if (!initialized) {
      if (!isValid(recipe, location)) {
        cancel();
        return;
      }
      consumeFuel(block);
      initialized = true;
      block.setBrewingTime(DEFAULT_BREW_TIME);
      block.update(true);
    } else if (!hasRecipeInputs(recipe, inventory)) {
      cancel();
      return;
    }

    if (brewTime <= 0) {
      inventory.setIngredient(decrease(inventory.getIngredient(), 1));

      int brewedPotions = 0;
      for (int i = 0; i < 3; i++) {
        ItemStack input = inventory.getItem(i);
        if (input != null && recipe.getBasePotion().isSimilar(input)) {
          inventory.setItem(i, recipe.getResult().clone());
          brewedPotions++;
        }
      }

      if (brewedPotions > 0) {
        Bukkit.getPluginManager().callEvent(new AdaptBrewCompleteEvent(block.getBlock(), recipe, brewerId, brewedPotions));
      }

      inventory.getViewers().forEach(e -> {
        if (e instanceof Player p) {
          SoundPlayer sp = SoundPlayer.of(p);
          sp.play(block.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1, 1);
        }
      });
      cancel();
      return;
    }
    brewTime--;
    block.setBrewingTime(getRemainingTime());
    block.update(true);
    if (!J.runAt(location, this::run, 1)) {
      cancel();
    }
  }

  public void cancel() {
    if (cancelled) {
      return;
    }
    cancelled = true;
    if (completion != null) {
      completion.accept(this);
    }
  }

  private int getRemainingTime() {
    return (int) (DEFAULT_BREW_TIME * (brewTime / (float) totalBrewTime));
  }

  private void consumeFuel(BrewingStand block) {
    int storedFuel = block.getFuelLevel();
    int fuelCost = Math.max(0, recipe.getFuelCost());
    int fuelItems = fuelItemsRequired(storedFuel, fuelCost);
    if (fuelItems > 0) {
      BrewerInventory inventory = block.getInventory();
      inventory.setFuel(decrease(inventory.getFuel(), fuelItems));
    }
    block.setFuelLevel(remainingFuel(storedFuel, fuelCost));
  }

  private static boolean hasRecipeInputs(BrewingRecipe recipe, BrewerInventory inventory) {
    ItemStack ingredient = inventory.getIngredient();
    if (ingredient == null || recipe.getIngredient() != ingredient.getType()) {
      return false;
    }

    for (int i = 0; i < 3; i++) {
      ItemStack input = inventory.getItem(i);
      if (input != null && recipe.getBasePotion().isSimilar(input)) {
        return true;
      }
    }

    return false;
  }

  private record Context(BrewingRecipe recipe, Location location, UUID brewerId, Consumer<BrewingTask> completion) {
  }
}
