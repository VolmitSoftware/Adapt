package art.arcane.adapt.api.recipe;

import art.arcane.adapt.ListenerDispatch;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerRecipeBookClickEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecipeCraftCounterTest {
  private static final NamespacedKey TRACKED = new NamespacedKey("adapt", "tracked-recipe");
  private static final NamespacedKey OTHER = new NamespacedKey("adapt", "other-recipe");

  private final RecipeCraftCounter counter = new RecipeCraftCounter();
  private final Plugin plugin = mock(Plugin.class);
  private final AtomicInteger level = new AtomicInteger();
  private final AtomicInteger ticksLived = new AtomicInteger(100);
  private final AtomicBoolean resultShown = new AtomicBoolean();
  private final Material[] types = new Material[9];
  private final int[] amounts = new int[9];
  private final ItemStack[] slots = new ItemStack[9];
  private final List<Integer> heard = new ArrayList<>();
  private Player player;
  private GameMode gameMode;
  private CraftingInventory inventory;

  @BeforeEach
  void setUp() {
    gameMode = GameMode.SURVIVAL;
    player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    when(player.getLevel()).thenAnswer(invocation -> level.get());
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

    counter.track(TRACKED);
    counter.observe((who, recipe, crafts) -> heard.add(crafts));
  }

  @Test
  void anObserverHearsEveryCraftOfATrackedRecipeIncludingShiftCrafts() {
    fill(4, Material.REDSTONE, Material.AMETHYST_SHARD);

    int crafts = craftAll(TRACKED, true);

    assertThat(crafts).isEqualTo(4);
    assertThat(heard.stream().mapToInt(Integer::intValue).sum()).isEqualTo(4);
  }

  @Test
  void aSingleCraftIsHeardOnce() {
    fill(5, Material.REDSTONE, Material.AMETHYST_SHARD);

    assertThat(craftAll(TRACKED, false)).isEqualTo(1);

    assertThat(heard).containsExactly(1);
  }

  @Test
  void craftsDoNotNeedAnyExperienceLevels() {
    level.set(0);
    fill(3, Material.REDSTONE, Material.AMETHYST_SHARD);

    int crafts = craftAll(TRACKED, true);

    assertThat(crafts).isEqualTo(3);
    assertThat(level.get()).isZero();
    assertThat(heard.stream().mapToInt(Integer::intValue).sum()).isEqualTo(3);
  }

  @Test
  void prepareLeavesTheResultAndTheClickAloneForAPlayerWithNoLevels() {
    level.set(0);
    fill(3, Material.REDSTONE);
    resultShown.set(true);

    dispatch(prepareEvent(TRACKED));
    CraftItemEvent click = craftEvent(TRACKED);
    dispatch(click);

    assertThat(resultShown.get()).isTrue();
    assertThat(click.isCancelled()).isFalse();
  }

  @Test
  void creativeCraftsAreNotHeard() {
    gameMode = GameMode.CREATIVE;
    fill(5, Material.REDSTONE, Material.AMETHYST_SHARD);

    craftAll(TRACKED, false);

    assertThat(heard).isEmpty();
  }

  @Test
  void untrackedRecipesAreNotHeard() {
    fill(5, Material.REDSTONE, Material.AMETHYST_SHARD);

    assertThat(counter.tracks(recipe(TRACKED))).isTrue();
    assertThat(counter.tracks(recipe(OTHER))).isFalse();
    craftAll(OTHER, false);

    assertThat(heard).isEmpty();
  }

  @Test
  void craftsStartedFollowsTheMostConsumedSlot() {
    assertThat(RecipeCraftCounter.craftsStarted(new int[]{4, 4, 0}, new int[]{4, 4, 0})).isZero();
    assertThat(RecipeCraftCounter.craftsStarted(new int[]{4, 4, 0}, new int[]{3, 4, 0})).isEqualTo(1);
    assertThat(RecipeCraftCounter.craftsStarted(new int[]{4, 4, 0}, new int[]{3, 3, 0})).isEqualTo(1);
    assertThat(RecipeCraftCounter.craftsStarted(new int[]{4, 4, 0}, new int[]{2, 3, 0})).isEqualTo(2);
    assertThat(RecipeCraftCounter.craftsStarted(new int[]{1, 4, 0}, new int[]{0, 4, 0})).isEqualTo(1);
    assertThat(RecipeCraftCounter.craftsStarted(new int[]{1, 4, 0}, new int[]{2, 4, 0})).isZero();
  }

  @Test
  void onlySingleCraftStepsAreCounted() {
    assertThat(RecipeCraftCounter.isCraftStep(0, 1)).isTrue();
    assertThat(RecipeCraftCounter.isCraftStep(2, 2)).isTrue();
    assertThat(RecipeCraftCounter.isCraftStep(2, 0)).isTrue();
    assertThat(RecipeCraftCounter.isCraftStep(1, 3)).isFalse();
    assertThat(RecipeCraftCounter.isCraftStep(1, -1)).isFalse();
  }

  @Test
  void aLaterClickClosesTheCountWindow() {
    fill(5, Material.REDSTONE, Material.AMETHYST_SHARD);

    dispatch(craftEvent(TRACKED));
    dispatch(craftEvent(OTHER));
    amounts[0] = 4;
    dispatch(prepareEvent(TRACKED));

    assertThat(heard).isEmpty();
  }

  @Test
  void emptiedGridInALaterTickCountsNothingMore() {
    fill(5, Material.REDSTONE, Material.AMETHYST_SHARD);
    craftAll(TRACKED, false);

    ticksLived.incrementAndGet();
    amounts[0] = 0;
    amounts[1] = 0;
    dispatch(prepareEvent(null));

    assertThat(heard).containsExactly(1);
  }

  @Test
  void emptiedGridWithinTheClickTickCountsNothingMore() {
    fill(5, Material.REDSTONE, Material.AMETHYST_SHARD);
    craftAll(TRACKED, false);

    amounts[0] = 0;
    dispatch(prepareEvent(null));
    amounts[1] = 0;
    dispatch(prepareEvent(null));

    assertThat(heard).containsExactly(1);
  }

  @Test
  void unevenRefillCountsNothingMore() {
    fill(5, Material.REDSTONE, Material.AMETHYST_SHARD);
    amounts[1] = 3;
    craftAll(TRACKED, false);

    amounts[0] = 1;
    dispatch(prepareEvent(TRACKED));
    amounts[0] = 2;
    amounts[1] = 1;
    dispatch(prepareEvent(TRACKED));

    assertThat(heard).containsExactly(1);
  }

  @Test
  void recipeBookClickClosesTheCountWindow() {
    fill(5, Material.REDSTONE, Material.AMETHYST_SHARD);
    craftAll(TRACKED, false);

    dispatch(new PlayerRecipeBookClickEvent(player, recipe(TRACKED), false));
    amounts[0] = 3;
    amounts[1] = 3;
    dispatch(prepareEvent(TRACKED));

    assertThat(heard).containsExactly(1);
  }

  @Test
  void clickCancelledByALaterHandlerCountsNothing() {
    fill(5, Material.REDSTONE, Material.AMETHYST_SHARD);

    CraftItemEvent click = craftEvent(TRACKED);
    dispatch(click, new LateCanceller());
    amounts[0] = 4;
    amounts[1] = 4;
    dispatch(prepareEvent(TRACKED));

    assertThat(click.isCancelled()).isTrue();
    assertThat(heard).isEmpty();
  }

  private int craftAll(NamespacedKey key, boolean shiftClick) {
    CraftItemEvent click = craftEvent(key);
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
        dispatch(prepareEvent(matches() ? key : null));
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
    List<Listener> listeners = new ArrayList<>(List.of(others));
    listeners.add(counter);
    ListenerDispatch.dispatch(plugin, event, listeners.toArray(new Listener[0]));
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
}
