package art.arcane.adapt.api.recipe;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerRecipeBookClickEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecipeTitheTest {
  private static final NamespacedKey TITHED = new NamespacedKey("adapt", "tithed-recipe");
  private static final NamespacedKey FREE = new NamespacedKey("adapt", "free-recipe");

  private final RecipeTithe tithe = new RecipeTithe();
  private final Plugin plugin = mock(Plugin.class);
  private final AtomicInteger level = new AtomicInteger();
  private final AtomicInteger ticksLived = new AtomicInteger(100);
  private final AtomicBoolean resultShown = new AtomicBoolean();
  private final Material[] types = new Material[9];
  private final int[] amounts = new int[9];
  private final ItemStack[] slots = new ItemStack[9];
  private Player player;
  private GameMode gameMode;
  private CraftingInventory inventory;

  @BeforeEach
  void setUp() {
    gameMode = GameMode.SURVIVAL;
    player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    when(player.getLevel()).thenAnswer(invocation -> level.get());
    doAnswer(invocation -> {
      level.set(invocation.getArgument(0));
      return null;
    }).when(player).setLevel(anyInt());
    when(player.getGameMode()).thenAnswer(invocation -> gameMode);
    when(player.getTicksLived()).thenAnswer(invocation -> ticksLived.get());

    for (int slot = 0; slot < slots.length; slot++) {
      int index = slot;
      ItemStack stack = mock(ItemStack.class);
      when(stack.getType()).thenAnswer(invocation -> types[index]);
      when(stack.getAmount()).thenAnswer(invocation -> amounts[index]);
      slots[slot] = stack;
    }
    inventory = mock(CraftingInventory.class);
    when(inventory.getMatrix()).thenAnswer(invocation -> matrix());
    doAnswer(invocation -> {
      resultShown.set(invocation.getArgument(0) != null);
      return null;
    }).when(inventory).setResult(any());

    tithe.set(TITHED, 3);
  }

  @Test
  void anObserverHearsEveryCraftOfATrackedRecipeIncludingShiftCrafts() {
    List<Integer> heard = new ArrayList<>();
    tithe.observe((who, recipe, crafts) -> heard.add(crafts));
    level.set(30);
    fill(4, Material.REDSTONE, Material.AMETHYST_SHARD);

    int crafts = craftAll(true);

    assertThat(crafts).isEqualTo(4);
    assertThat(heard.stream().mapToInt(Integer::intValue).sum()).isEqualTo(4);
  }

  @Test
  void anObserverHearsCraftsOfRecipesWithoutATitheButNotCreativeOnes() {
    List<Integer> heard = new ArrayList<>();
    tithe.observe((who, recipe, crafts) -> heard.add(crafts));
    tithe.set(TITHED, 0);
    level.set(0);
    fill(5, Material.REDSTONE, Material.AMETHYST_SHARD);

    assertThat(craftAll(false)).isEqualTo(1);
    assertThat(heard).containsExactly(1);

    gameMode = GameMode.CREATIVE;
    fill(5, Material.REDSTONE, Material.AMETHYST_SHARD);
    craftAll(false);
    assertThat(heard).containsExactly(1);
  }

  @Test
  void affordableCraftsDividesLevelsByTheTithe() {
    assertThat(RecipeTithe.affordableCrafts(7, 3)).isEqualTo(2);
    assertThat(RecipeTithe.affordableCrafts(6, 3)).isEqualTo(2);
    assertThat(RecipeTithe.affordableCrafts(2, 3)).isZero();
    assertThat(RecipeTithe.affordableCrafts(-4, 3)).isZero();
    assertThat(RecipeTithe.affordableCrafts(0, 0)).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void craftsStartedFollowsTheMostConsumedSlot() {
    assertThat(RecipeTithe.craftsStarted(new int[]{4, 4, 0}, new int[]{4, 4, 0})).isZero();
    assertThat(RecipeTithe.craftsStarted(new int[]{4, 4, 0}, new int[]{3, 4, 0})).isEqualTo(1);
    assertThat(RecipeTithe.craftsStarted(new int[]{4, 4, 0}, new int[]{3, 3, 0})).isEqualTo(1);
    assertThat(RecipeTithe.craftsStarted(new int[]{4, 4, 0}, new int[]{2, 3, 0})).isEqualTo(2);
    assertThat(RecipeTithe.craftsStarted(new int[]{1, 4, 0}, new int[]{0, 4, 0})).isEqualTo(1);
    assertThat(RecipeTithe.craftsStarted(new int[]{1, 4, 0}, new int[]{2, 4, 0})).isZero();
  }

  @Test
  void onlySingleCraftStepsAreCharged() {
    assertThat(RecipeTithe.isCraftStep(0, 1)).isTrue();
    assertThat(RecipeTithe.isCraftStep(2, 2)).isTrue();
    assertThat(RecipeTithe.isCraftStep(2, 0)).isTrue();
    assertThat(RecipeTithe.isCraftStep(1, 3)).isFalse();
    assertThat(RecipeTithe.isCraftStep(1, -1)).isFalse();
  }

  @Test
  void shiftCraftingStopsAtTheCraftsThePlayerCanAfford() {
    level.set(7);
    fill(5, Material.REDSTONE, Material.AMETHYST_SHARD);

    int crafts = craftAll(true);

    assertThat(crafts).isEqualTo(2);
    assertThat(level.get()).isEqualTo(1);
    assertThat(amounts[0]).isEqualTo(3);
    assertThat(resultShown.get()).isFalse();
  }

  @Test
  void shiftCraftingChargesOnlyTheCraftsTheIngredientsAllow() {
    level.set(30);
    fill(4, Material.REDSTONE, Material.AMETHYST_SHARD);

    int crafts = craftAll(true);

    assertThat(crafts).isEqualTo(4);
    assertThat(level.get()).isEqualTo(18);
    assertThat(amounts[0]).isZero();
  }

  @Test
  void singleCraftChargesOneTithe() {
    level.set(10);
    fill(5, Material.REDSTONE, Material.AMETHYST_SHARD);

    int crafts = craftAll(false);

    assertThat(crafts).isEqualTo(1);
    assertThat(level.get()).isEqualTo(7);
    assertThat(resultShown.get()).isTrue();
  }

  @Test
  void clickThatTakesNothingChargesNothing() {
    level.set(10);
    fill(5, Material.REDSTONE, Material.AMETHYST_SHARD);

    dispatch(craftEvent(TITHED));
    dispatch(craftEvent(FREE));
    amounts[0] = 4;
    dispatch(prepareEvent(TITHED));

    assertThat(level.get()).isEqualTo(10);
  }

  @Test
  void emptiedGridInALaterTickChargesNothing() {
    level.set(10);
    fill(5, Material.REDSTONE, Material.AMETHYST_SHARD);
    craftAll(false);

    ticksLived.incrementAndGet();
    amounts[0] = 0;
    amounts[1] = 0;
    dispatch(prepareEvent(null));

    assertThat(level.get()).isEqualTo(7);
  }

  @Test
  void emptiedGridWithinTheClickTickChargesNothing() {
    level.set(10);
    fill(5, Material.REDSTONE, Material.AMETHYST_SHARD);
    craftAll(false);

    amounts[0] = 0;
    dispatch(prepareEvent(null));
    amounts[1] = 0;
    dispatch(prepareEvent(null));

    assertThat(level.get()).isEqualTo(7);
  }

  @Test
  void unevenRefillChargesNothing() {
    level.set(20);
    fill(5, Material.REDSTONE, Material.AMETHYST_SHARD);
    amounts[1] = 3;
    craftAll(false);

    amounts[0] = 1;
    dispatch(prepareEvent(TITHED));
    amounts[0] = 2;
    amounts[1] = 1;
    dispatch(prepareEvent(TITHED));

    assertThat(level.get()).isEqualTo(17);
  }

  @Test
  void recipeBookClickClosesTheChargeWindow() {
    level.set(10);
    fill(5, Material.REDSTONE, Material.AMETHYST_SHARD);
    craftAll(false);

    dispatch(new PlayerRecipeBookClickEvent(player, recipe(TITHED), false));
    amounts[0] = 3;
    amounts[1] = 3;
    dispatch(prepareEvent(TITHED));

    assertThat(level.get()).isEqualTo(7);
  }

  @Test
  void clickCancelledByALaterHandlerChargesNothing() {
    level.set(10);
    fill(5, Material.REDSTONE, Material.AMETHYST_SHARD);

    CraftItemEvent click = craftEvent(TITHED);
    dispatch(click, new LateCanceller());
    amounts[0] = 4;
    amounts[1] = 4;
    dispatch(prepareEvent(TITHED));

    assertThat(click.isCancelled()).isTrue();
    assertThat(level.get()).isEqualTo(10);
  }

  @Test
  void unaffordableCraftStaysCancelledWhenAnEarlierHandlerUncancels() {
    level.set(2);
    fill(5, Material.REDSTONE);

    CraftItemEvent click = craftEvent(TITHED);
    dispatch(click, new EarlyUncanceller());

    assertThat(click.isCancelled()).isTrue();
    assertThat(level.get()).isEqualTo(2);
  }

  @Test
  void prepareHidesTheResultWhenThePlayerCannotAfford() {
    level.set(2);
    fill(5, Material.REDSTONE);
    resultShown.set(true);

    dispatch(prepareEvent(TITHED));

    assertThat(resultShown.get()).isFalse();
  }

  @Test
  void craftIsCancelledWhenLevelsDroppedBelowTheTithe() {
    level.set(2);
    fill(5, Material.REDSTONE);

    CraftItemEvent click = craftEvent(TITHED);
    dispatch(click);

    assertThat(click.isCancelled()).isTrue();
    assertThat(level.get()).isEqualTo(2);
  }

  @Test
  void creativePlayersCraftWithoutPaying() {
    gameMode = GameMode.CREATIVE;
    level.set(0);
    fill(3, Material.REDSTONE);
    resultShown.set(true);

    dispatch(prepareEvent(TITHED));
    int crafts = craftAll(true);

    assertThat(crafts).isEqualTo(3);
    assertThat(level.get()).isZero();
  }

  @Test
  void recipesWithoutATitheAreUntouched() {
    level.set(0);
    fill(3, Material.REDSTONE);
    resultShown.set(true);

    dispatch(prepareEvent(FREE));
    CraftItemEvent click = craftEvent(FREE);
    dispatch(click);

    assertThat(resultShown.get()).isTrue();
    assertThat(click.isCancelled()).isFalse();
  }

  @Test
  void settingZeroLevelsRemovesTheTithe() {
    tithe.set(TITHED, 0);

    assertThat(tithe.levels(recipe(TITHED))).isZero();
  }

  @Test
  void craftersCannotCraftTithedRecipes() {
    CrafterCraftEvent tithed = new CrafterCraftEvent(mock(Block.class), recipe(TITHED), mock(ItemStack.class));
    CrafterCraftEvent free = new CrafterCraftEvent(mock(Block.class), recipe(FREE), mock(ItemStack.class));

    dispatch(tithed);
    dispatch(free);

    assertThat(tithed.isCancelled()).isTrue();
    assertThat(free.isCancelled()).isFalse();
  }

  private int craftAll(boolean shiftClick) {
    CraftItemEvent click = craftEvent(TITHED);
    dispatch(click);
    if (click.isCancelled()) {
      return 0;
    }

    resultShown.set(true);
    int crafts = 0;
    while (resultShown.get() && matches()) {
      crafts++;
      for (int slot = 0; slot < amounts.length; slot++) {
        if (amounts[slot] <= 0) {
          continue;
        }
        amounts[slot]--;
        resultShown.set(matches());
        dispatch(prepareEvent(matches() ? TITHED : null));
      }
      if (!shiftClick) {
        break;
      }
    }
    return crafts;
  }

  private boolean matches() {
    for (int slot = 0; slot < amounts.length; slot++) {
      if (types[slot] != null && amounts[slot] <= 0) {
        return false;
      }
    }
    return true;
  }

  private void fill(int amount, Material... materials) {
    for (int slot = 0; slot < materials.length; slot++) {
      types[slot] = materials[slot];
      amounts[slot] = amount;
    }
  }

  private ItemStack[] matrix() {
    ItemStack[] matrix = new ItemStack[slots.length];
    for (int slot = 0; slot < slots.length; slot++) {
      matrix[slot] = types[slot] == null || amounts[slot] <= 0 ? null : slots[slot];
    }
    return matrix;
  }

  private void dispatch(Event event, Listener... others) {
    List<RegisteredListener> handlers = new ArrayList<>();
    collectHandlers(tithe, event, handlers);
    for (Listener other : others) {
      collectHandlers(other, event, handlers);
    }
    handlers.sort(Comparator.comparing(RegisteredListener::getPriority));
    try {
      for (RegisteredListener handler : handlers) {
        handler.callEvent(event);
      }
    } catch (EventException e) {
      throw new IllegalStateException(e.getCause());
    }
  }

  private void collectHandlers(Listener listener, Event event, List<RegisteredListener> handlers) {
    for (Method method : listener.getClass().getDeclaredMethods()) {
      EventHandler annotation = method.getAnnotation(EventHandler.class);
      if (annotation == null || method.getParameterCount() != 1 || !method.getParameterTypes()[0].isInstance(event)) {
        continue;
      }
      method.setAccessible(true);
      handlers.add(new RegisteredListener(listener, (target, fired) -> invoke(method, target, fired), annotation.priority(), plugin, annotation.ignoreCancelled()));
    }
  }

  private static void invoke(Method method, Listener target, Event event) throws EventException {
    try {
      method.invoke(target, event);
    } catch (InvocationTargetException e) {
      throw new EventException(e.getCause());
    } catch (IllegalAccessException e) {
      throw new EventException(e);
    }
  }

  private CraftItemEvent craftEvent(NamespacedKey key) {
    CraftItemEvent event = mock(CraftItemEvent.class);
    ShapedRecipe recipe = recipe(key);
    AtomicBoolean cancelled = new AtomicBoolean();
    when(event.getWhoClicked()).thenReturn(player);
    when(event.getRecipe()).thenReturn(recipe);
    when(event.getInventory()).thenReturn(inventory);
    when(event.isCancelled()).thenAnswer(invocation -> cancelled.get());
    doAnswer(invocation -> {
      cancelled.set(invocation.getArgument(0));
      return null;
    }).when(event).setCancelled(anyBoolean());
    return event;
  }

  private PrepareItemCraftEvent prepareEvent(NamespacedKey key) {
    PrepareItemCraftEvent event = mock(PrepareItemCraftEvent.class);
    InventoryView view = mock(InventoryView.class);
    ShapedRecipe recipe = key == null ? null : recipe(key);
    when(view.getPlayer()).thenReturn(player);
    when(event.getView()).thenReturn(view);
    when(event.getInventory()).thenReturn(inventory);
    when(event.getRecipe()).thenReturn(recipe);
    return event;
  }

  private static ShapedRecipe recipe(NamespacedKey key) {
    ShapedRecipe recipe = mock(ShapedRecipe.class);
    when(recipe.getKey()).thenReturn(key);
    return recipe;
  }

  static final class LateCanceller implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(CraftItemEvent e) {
      e.setCancelled(true);
    }
  }

  static final class EarlyUncanceller implements Listener {
    @EventHandler(priority = EventPriority.HIGH)
    public void on(CraftItemEvent e) {
      e.setCancelled(false);
    }
  }
}
