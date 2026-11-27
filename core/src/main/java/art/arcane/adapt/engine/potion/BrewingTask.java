package art.arcane.adapt.engine.potion;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.BrewingRecipe;
import art.arcane.adapt.api.item.VanillaUse;
import art.arcane.adapt.engine.item.AdaptItems;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BrewingStandRef;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class BrewingTask implements Runnable {

  private static final int DEFAULT_BREW_TIME = 400;
  private static final Id BREWING_STAND = Id.minecraft("brewing_stand");

  private final BrewingRecipe recipe;
  private final BlockRef block;
  private final Loc location;
  private final AdaptPlayer brewerRuntime;
  private final PlayerRef brewerSession;
  private final UUID brewerId;
  private final Consumer<BrewingTask> completion;
  private final int totalBrewTime;
  private int brewTime;
  private boolean initialized;
  private volatile boolean cancelled;

  private BrewingTask(Context context) {
    this.recipe = Objects.requireNonNull(context.recipe());
    this.block = Objects.requireNonNull(context.block());
    this.location = block.location();
    this.brewerId = Objects.requireNonNull(context.brewerId());
    AdaptServer server = server();
    this.brewerRuntime = server == null ? null : server.getOnlineAdaptPlayer(brewerId);
    this.brewerSession = brewerRuntime == null ? null : brewerRuntime.getPlayer();
    this.completion = context.completion();
    this.totalBrewTime = Math.max(1, recipe.brewingTicks());
    this.brewTime = totalBrewTime;
  }

  public static BrewingTask create(BrewingRecipe recipe, BlockRef block, UUID brewerId, Consumer<BrewingTask> completion) {
    return new BrewingTask(new Context(recipe, block, brewerId, completion));
  }

  public void start() {
    if (!Tasks.runAt(location, this::run)) {
      cancel();
    }
  }

  public BrewingRecipe getRecipe() {
    return recipe;
  }

  public static ItemRef decrease(ItemRef source, int amount) {
    if (source == null || source.amount() <= amount) {
      return AdaptCore.platform().host().items().empty();
    }

    return source.copyWithAmount(source.amount() - Math.max(0, amount));
  }

  static int fuelItemsRequired(int storedFuel, int fuelCost) {
    int uncoveredCost = Math.max(0, fuelCost - Math.max(0, storedFuel));
    return (uncoveredCost + 19) / 20;
  }

  static int remainingFuel(int storedFuel, int fuelCost) {
    int fuelItems = fuelItemsRequired(storedFuel, fuelCost);
    return Math.max(0, storedFuel) + (fuelItems * 20) - Math.max(0, fuelCost);
  }

  static boolean isBrewerRuntimeAvailable(
      UUID brewerId,
      AdaptPlayer brewerRuntime,
      PlayerRef brewerSession,
      AdaptServer server
  ) {
    if (brewerId == null || brewerRuntime == null || brewerSession == null || server == null) {
      return false;
    }
    AdaptPlayer adaptPlayer = server.getOnlineAdaptPlayer(brewerId);
    return adaptPlayer == brewerRuntime
        && adaptPlayer.isRuntimeReady()
        && adaptPlayer.getPlayer() == brewerSession;
  }

  public static boolean isValid(BrewingRecipe recipe, BlockRef block) {
    if (recipe == null || block == null || !block.type().equals(BREWING_STAND)) {
      return false;
    }

    Optional<BrewingStandRef> found = block.brewingStand();
    if (found.isEmpty()) {
      return false;
    }
    BrewingStandRef stand = found.get();
    if (!hasRecipeInputs(recipe, stand)) {
      return false;
    }

    ItemRef fuel = stand.fuel();
    int totalFuel = (!fuel.isEmpty() ? fuel.amount() * 20 : 0) + stand.fuelLevel();
    if (totalFuel < recipe.fuelCost()) {
      return false;
    }

    return totalFuel >= Math.max(0, recipe.fuelCost());
  }

  @Override
  public void run() {
    if (cancelled) {
      return;
    }

    if (!isBrewerRuntimeAvailable(brewerId, brewerRuntime, brewerSession, server())) {
      cancel();
      return;
    }

    if (!block.type().equals(BREWING_STAND)) {
      cancel();
      return;
    }

    Optional<BrewingStandRef> found = block.brewingStand();
    if (found.isEmpty()) {
      cancel();
      return;
    }
    BrewingStandRef stand = found.get();
    if (!initialized) {
      if (!isValid(recipe, block)) {
        cancel();
        return;
      }
      consumeFuel(stand);
      initialized = true;
      stand.setBrewingTime(DEFAULT_BREW_TIME);
    } else if (!hasRecipeInputs(recipe, stand)) {
      cancel();
      return;
    }

    if (brewTime <= 0) {
      stand.setIngredient(decrease(stand.ingredient(), 1));

      int brewedPotions = 0;
      for (int i = 0; i < BrewingStandRef.BOTTLE_SLOTS; i++) {
        ItemRef input = stand.bottle(i);
        if (!input.isEmpty() && recipe.basePotion().isSimilar(input)) {
          stand.setBottle(i, recipe.result().copy());
          brewedPotions++;
        }
      }

      if (brewedPotions > 0) {
        AdaptPorts.get(BrewEventPort.class, BrewEventPort.BUS).brewed(block, recipe, brewerId, brewedPotions);
      }

      for (PlayerRef viewer : stand.viewers()) {
        SoundPlayer.of(viewer).play(location, Sounds.BLOCK_BREWING_STAND_BREW, 1, 1);
      }
      cancel();
      return;
    }
    brewTime--;
    stand.setBrewingTime(getRemainingTime());
    if (!Tasks.runAt(location, this::run, 1)) {
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

  private void consumeFuel(BrewingStandRef stand) {
    int storedFuel = stand.fuelLevel();
    int fuelCost = Math.max(0, recipe.fuelCost());
    int fuelItems = fuelItemsRequired(storedFuel, fuelCost);
    if (fuelItems > 0) {
      stand.setFuel(decrease(stand.fuel(), fuelItems));
    }
    stand.setFuelLevel(remainingFuel(storedFuel, fuelCost));
  }

  static boolean acceptsIngredient(ItemRef ingredient) {
    return ingredient != null && !ingredient.isEmpty() && !AdaptItems.refuses(ingredient, VanillaUse.WORKSTATION);
  }

  private static boolean hasRecipeInputs(BrewingRecipe recipe, BrewingStandRef stand) {
    ItemRef ingredient = stand.ingredient();
    if (!acceptsIngredient(ingredient) || !recipe.ingredient().equals(ingredient.type())) {
      return false;
    }

    for (int i = 0; i < BrewingStandRef.BOTTLE_SLOTS; i++) {
      ItemRef input = stand.bottle(i);
      if (!input.isEmpty() && recipe.basePotion().isSimilar(input)) {
        return true;
      }
    }

    return false;
  }

  private static AdaptServer server() {
    AdaptCore core = AdaptCore.current();
    return core == null ? null : core.getAdaptServer();
  }

  private record Context(BrewingRecipe recipe, BlockRef block, UUID brewerId, Consumer<BrewingTask> completion) {
  }
}
