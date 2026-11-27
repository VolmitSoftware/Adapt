package art.arcane.adapt.content.golem.item;

import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.PrepareItemCraftEvent;
import art.arcane.adapt.platform.event.player.PlayerRecipeBookClickEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeCraftCounterTest {
  private static final Id TRACKED = Id.adapt("tracked-recipe");
  private static final Id OTHER = Id.adapt("other-recipe");
  private static final Id REDSTONE = Id.minecraft("redstone");
  private static final Id AMETHYST_SHARD = Id.minecraft("amethyst_shard");

  @TempDir
  Path data;

  private final RecipeCraftCounter counter = new RecipeCraftCounter();
  private final Id[] types = new Id[9];
  private final int[] amounts = new int[9];
  private final List<Integer> heard = new ArrayList<>();
  private final List<ItemRef> heardResults = new ArrayList<>();
  private ItemRef shownResult;
  private TestAdapt adapt;
  private FakePlayer player;

  @BeforeEach
  void setUp() {
    TestPlatform platform = TestPlatform.builder(data).build();
    player = platform.player("Crafter", 0.5D, 64D, 0.5D);
    adapt = TestAdapt.start(platform, List.of());
    adapt.core().getRegistrar().register(counter);

    counter.track(TRACKED);
    counter.observe((who, recipe, result, crafts) -> {
      heard.add(crafts);
      heardResults.add(result);
    });
  }

  @AfterEach
  void tearDown() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void anObserverHearsEveryCraftOfATrackedRecipeIncludingShiftCrafts() {
    fill(4, REDSTONE, AMETHYST_SHARD);

    int crafts = craftAll(TRACKED, true);

    assertThat(crafts).isEqualTo(4);
    assertThat(heard.stream().mapToInt(Integer::intValue).sum()).isEqualTo(4);
  }

  @Test
  void aSingleCraftIsHeardOnce() {
    fill(5, REDSTONE, AMETHYST_SHARD);

    assertThat(craftAll(TRACKED, false)).isEqualTo(1);

    assertThat(heard).containsExactly(1);
  }

  @Test
  void theObserverHearsTheCraftedResult() {
    fill(5, REDSTONE, AMETHYST_SHARD);

    craftAll(TRACKED, false);

    assertThat(heardResults).hasSize(1);
    assertThat(heardResults.getFirst().type()).isEqualTo(Id.minecraft("heart_of_the_sea"));
    assertThat(heardResults.getFirst().amount()).isEqualTo(2);
  }

  @Test
  void craftsDoNotNeedAnyExperienceLevels() {
    player.setLevel(0);
    fill(3, REDSTONE, AMETHYST_SHARD);

    int crafts = craftAll(TRACKED, true);

    assertThat(crafts).isEqualTo(3);
    assertThat(player.level()).isZero();
    assertThat(heard.stream().mapToInt(Integer::intValue).sum()).isEqualTo(3);
  }

  @Test
  void prepareLeavesTheResultAndTheClickAloneForAPlayerWithNoLevels() {
    player.setLevel(0);
    fill(3, REDSTONE);

    PrepareItemCraftEvent prepare = adapt.post(prepareEvent(TRACKED));
    CraftItemEvent click = adapt.post(craftEvent(TRACKED));

    assertThat(prepare.result().isEmpty()).isFalse();
    assertThat(click.isCancelled()).isFalse();
  }

  @Test
  void creativeCraftsAreNotHeard() {
    player.setGameMode(GameMode.CREATIVE);
    fill(5, REDSTONE, AMETHYST_SHARD);

    craftAll(TRACKED, false);

    assertThat(heard).isEmpty();
  }

  @Test
  void untrackedRecipesAreNotHeard() {
    fill(5, REDSTONE, AMETHYST_SHARD);

    assertThat(counter.tracks(TRACKED)).isTrue();
    assertThat(counter.tracks(OTHER)).isFalse();
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
    fill(5, REDSTONE, AMETHYST_SHARD);

    adapt.post(craftEvent(TRACKED));
    adapt.post(craftEvent(OTHER));
    amounts[0] = 4;
    adapt.post(prepareEvent(TRACKED));

    assertThat(heard).isEmpty();
  }

  @Test
  void emptiedGridInALaterTickCountsNothingMore() {
    fill(5, REDSTONE, AMETHYST_SHARD);
    craftAll(TRACKED, false);

    player.setTicksLived(player.ticksLived() + 1);
    amounts[0] = 0;
    amounts[1] = 0;
    adapt.post(prepareEvent(null));

    assertThat(heard).containsExactly(1);
  }

  @Test
  void emptiedGridWithinTheClickTickCountsNothingMore() {
    fill(5, REDSTONE, AMETHYST_SHARD);
    craftAll(TRACKED, false);

    amounts[0] = 0;
    adapt.post(prepareEvent(null));
    amounts[1] = 0;
    adapt.post(prepareEvent(null));

    assertThat(heard).containsExactly(1);
  }

  @Test
  void unevenRefillCountsNothingMore() {
    fill(5, REDSTONE, AMETHYST_SHARD);
    amounts[1] = 3;
    craftAll(TRACKED, false);

    amounts[0] = 1;
    adapt.post(prepareEvent(TRACKED));
    amounts[0] = 2;
    amounts[1] = 1;
    adapt.post(prepareEvent(TRACKED));

    assertThat(heard).containsExactly(1);
  }

  @Test
  void recipeBookClickClosesTheCountWindow() {
    fill(5, REDSTONE, AMETHYST_SHARD);
    craftAll(TRACKED, false);

    adapt.post(new PlayerRecipeBookClickEvent(player, TRACKED, false));
    amounts[0] = 3;
    amounts[1] = 3;
    adapt.post(prepareEvent(TRACKED));

    assertThat(heard).containsExactly(1);
  }

  @Test
  void clickCancelledByALaterHandlerCountsNothing() {
    fill(5, REDSTONE, AMETHYST_SHARD);
    LateCanceller canceller = new LateCanceller();
    adapt.core().getRegistrar().register(canceller);

    CraftItemEvent click = adapt.post(craftEvent(TRACKED));
    adapt.core().getRegistrar().unregister(canceller);
    amounts[0] = 4;
    amounts[1] = 4;
    adapt.post(prepareEvent(TRACKED));

    assertThat(click.isCancelled()).isTrue();
    assertThat(heard).isEmpty();
  }

  private int craftAll(Id key, boolean shiftClick) {
    CraftItemEvent click = adapt.post(craftEvent(key));
    if (click.isCancelled()) {
      return 0;
    }

    boolean resultShown = true;
    int crafts = 0;
    while (resultShown && matches()) {
      crafts++;
      for (int slot = 0; slot < amounts.length; slot++) {
        if (amounts[slot] <= 0) {
          continue;
        }
        amounts[slot]--;
        resultShown = matches();
        adapt.post(prepareEvent(matches() ? key : null));
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

  private void fill(int amount, Id... materials) {
    for (int slot = 0; slot < materials.length; slot++) {
      types[slot] = materials[slot];
      amounts[slot] = amount;
    }
    shownResult = new FakeItem(Id.minecraft("heart_of_the_sea"), 2);
  }

  private List<ItemRef> matrix() {
    List<ItemRef> matrix = new ArrayList<>(types.length);
    for (int slot = 0; slot < types.length; slot++) {
      matrix.add(types[slot] == null || amounts[slot] <= 0 ? FakeItem.empty() : new FakeItem(types[slot], amounts[slot]));
    }
    return matrix;
  }

  private CraftItemEvent craftEvent(Id key) {
    return new CraftItemEvent(player, InventoryKind.WORKBENCH, 0, 0, ClickKind.LEFT, InventoryAction.PICKUP_ALL, -1,
        shownResult.copy(), FakeItem.empty(), key, CraftItemEvent.RecipeKind.SHAPED, matrix());
  }

  private PrepareItemCraftEvent prepareEvent(Id key) {
    ItemRef result = key == null ? FakeItem.empty() : shownResult.copy();
    return new PrepareItemCraftEvent(player, matrix(), key, CraftItemEvent.RecipeKind.SHAPED, false, result);
  }

  static final class LateCanceller {
    @Handle(priority = EventPriority.HIGHEST)
    public void on(CraftItemEvent e) {
      e.setCancelled(true);
    }
  }
}
