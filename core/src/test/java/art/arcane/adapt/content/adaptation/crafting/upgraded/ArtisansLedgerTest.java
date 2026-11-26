package art.arcane.adapt.content.adaptation.crafting.upgraded;

import art.arcane.adapt.api.item.DataBoundItem;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.api.mutation.tool.MutationTools;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.content.item.BackpackItem;
import art.arcane.adapt.content.skill.SkillArchitect;
import art.arcane.adapt.content.skill.SkillCrafting;
import art.arcane.adapt.platform.entity.CraftingStation;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHost;
import art.arcane.adapt.platform.menu.MenuSession;
import art.arcane.adapt.platform.menu.StorageView;
import art.arcane.adapt.platform.recipe.RecipeChoice;
import art.arcane.adapt.platform.recipe.RecipeView;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.testkit.XpPaid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

/** The Artisan's Ledger keeps recipes and stations on its pages and crafts from what the holder carries. */
class ArtisansLedgerTest {
  private static final Id PLANKS = Id.minecraft("oak_planks");
  private static final Id STICK = Id.minecraft("stick");
  private static final Id PICKAXE = Id.minecraft("wooden_pickaxe");
  private static final Id TABLE = Id.minecraft("crafting_table");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private SkillRegistry registry;
  private Menus menus;

  @BeforeEach
  void start() {
    menus = new Menus();
    platform = TestPlatform.builder(data).menus(menus).build();
    platform.recipes()
        .serverRecipe(new RecipeView(Id.minecraft("stick"), RecipeView.Kind.SHAPED, List.of("P", "P"),
            Map.of('P', RecipeChoice.ofTypes(List.of(PLANKS))), List.of(), new FakeItem(STICK, 4)))
        .serverRecipe(new RecipeView(Id.minecraft("wooden_pickaxe"), RecipeView.Kind.SHAPED, List.of("PPP", " S ", " S "),
            Map.of('P', RecipeChoice.ofTypes(List.of(PLANKS)), 'S', RecipeChoice.ofTypes(List.of(STICK))), List.of(),
            FakeItem.tool("wooden_pickaxe", 59)));
    player = platform.player("Artisan", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillCrafting.class, SkillArchitect.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void theLedgerIsCraftedFromABookLeatherAndATableAtLevelOne() {
    MutationTool<?> ledger = ledger();

    assertThat(ledger.id()).isEqualTo("tool-crafting-artisans-ledger");
    assertThat(ledger.material()).isEqualTo(Id.minecraft("written_book"));
    assertThat(ledger.requiredLevel()).isEqualTo(1);
    assertThat(ledger.recipes()).hasSize(1);
    adapt.awaitTicks(registry::isRecipeRegistrationReady, 10_000L);
    assertThat(registry.getRequiredAdaptation(Id.adapt("tool-crafting-artisans-ledger"))).isSameAs(workshop());
    assertThat(registry.getRequiredRecipeLevel(Id.adapt("tool-crafting-artisans-ledger"))).isEqualTo(1);
  }

  @Test
  void aHolderOpensAsManyPagesAsTheLevelAllows() {
    mutate(2);

    open();

    assertThat(menus.menu.size()).isEqualTo(9);
    assertThat(menus.menu.buttons()).hasSize(8);
  }

  @Test
  void dropAnItemOnAPageToKeepItsRecipeAndClickToCraftFromTheInventory() {
    mutate(1);
    player.inventory().setItem(5, new FakeItem(PLANKS, 3));
    open();

    player.inventory().setCursor(new FakeItem(STICK, 1));
    click(0, ClickKind.LEFT);
    player.inventory().setCursor(FakeItem.empty());
    click(0, ClickKind.LEFT);

    assertThat(count(PLANKS)).isEqualTo(1);
    assertThat(count(STICK)).isEqualTo(4);
    assertThat(XpPaid.by(profile, workshop())).isZero();
  }

  @Test
  void aSneakClickCraftsOnlyWhatTheMaterialsAllow() {
    mutate(1);
    player.inventory().setItem(5, new FakeItem(PLANKS, 5));
    store(0, STICK);

    click(0, ClickKind.SHIFT_LEFT);
    click(0, ClickKind.SHIFT_LEFT);

    assertThat(count(PLANKS)).isEqualTo(1);
    assertThat(count(STICK)).isEqualTo(8);
  }

  @Test
  void storedPartsAreCraftedOnTheWay() {
    mutate(1);
    player.inventory().setItem(5, new FakeItem(PLANKS, 5));
    store(0, STICK);
    store(1, PICKAXE);

    click(1, ClickKind.LEFT);

    assertThat(count(PICKAXE)).isEqualTo(1);
    assertThat(count(PLANKS)).isZero();
    assertThat(count(STICK)).isEqualTo(2);
  }

  @Test
  void missingIngredientsAreListedAndNothingIsTaken() {
    mutate(1);
    player.inventory().setItem(5, new FakeItem(PLANKS, 2));
    store(1, PICKAXE);

    click(1, ClickKind.LEFT);

    assertThat(count(PLANKS)).isEqualTo(2);
    assertThat(count(PICKAXE)).isZero();
    assertThat(player.actionBars()).anySatisfy((String bar) -> assertThat(bar).contains("Missing").contains("Stick"));
  }

  @Test
  void ingredientsComeOutOfBackpacksToo() {
    mutate(1);
    ItemRef backpack = backpack(new FakeItem(PLANKS, 2));
    player.inventory().setItem(7, backpack);
    store(0, STICK);

    click(0, ClickKind.LEFT);

    assertThat(count(STICK)).isEqualTo(4);
    assertThat(BackpackItem.countStored(BackpackItem.readContents(player.inventory().item(7)))).isZero();
  }

  @Test
  void oneMenuReadsEachBackpackOnceForAllItsPages() {
    mutate(1);
    store(0, STICK);
    store(1, PICKAXE);
    player.inventory().setItem(7, backpack(new FakeItem(PLANKS, 2)));

    try (MockedStatic<BackpackItem> backpacks = mockStatic(BackpackItem.class, CALLS_REAL_METHODS)) {
      open();
      backpacks.verify(() -> BackpackItem.readContents(any(ItemRef.class)), times(1));
    }
  }

  @Test
  void namedItemsAndToolsAreNeverEatenAsIngredients() {
    mutate(1);
    FakeItem named = new FakeItem(PLANKS, 2);
    named.setDisplayName("Heirloom");
    player.inventory().setItem(5, named);
    store(0, STICK);

    click(0, ClickKind.LEFT);

    assertThat(player.inventory().item(5).amount()).isEqualTo(2);
    assertThat(count(STICK)).isZero();
  }

  @Test
  void toolRecipesCannotBeStored() {
    mutate(1);
    open();

    player.inventory().setCursor(ledger().create(new ArtisansLedger.Data()));
    click(0, ClickKind.LEFT);

    assertThat(page(0)).isNull();
    assertThat(player.actionBars()).isNotEmpty();
  }

  @Test
  void aStationPageOpensTheStationOnlyWhileOneIsCarried() {
    mutate(1);
    player.setFoodLevel(20);
    store(0, TABLE);

    click(0, ClickKind.LEFT);
    assertThat(player.openStation()).isNull();

    player.inventory().setItem(9, new FakeItem(TABLE, 1));
    open();
    click(0, ClickKind.LEFT);

    assertThat(player.openStation()).isEqualTo(CraftingStation.WORKBENCH);
    assertThat(player.foodLevel()).isEqualTo(19);
    assertThat(count(TABLE)).isEqualTo(1);
  }

  @Test
  void rightClickClearsAPage() {
    mutate(1);
    store(0, STICK);

    click(0, ClickKind.RIGHT);

    assertThat(page(0)).isNull();
  }

  @Test
  void itDoesNothingForAPlayerWhoHasNotMutated() {
    player.holding(ledger().create(new ArtisansLedger.Data()));

    use();

    assertThat(menus.menu).isNull();
  }

  private void store(int page, Id type) {
    open();
    player.inventory().setCursor(new FakeItem(type, 1));
    click(page, ClickKind.LEFT);
    player.inventory().setCursor(FakeItem.empty());
    assertThat(page(page)).isNotNull();
  }

  private ArtisansLedger.Page page(int index) {
    ArtisansLedger.Data stored = ((ArtisansLedger) ledger()).read(player.inventory().mainHand());
    return stored == null || index >= stored.pages.size() ? null : stored.pages.get(index);
  }

  private void open() {
    if (!ledger().is(player.inventory().mainHand())) {
      player.holding(((ArtisansLedger) ledger()).create(new ArtisansLedger.Data()));
    }
    use();
    assertThat(menus.menu).isNotNull();
  }

  private void use() {
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_AIR, Hand.MAIN_HAND, player.inventory().mainHand(),
        null, null, null, UseResult.DEFAULT, UseResult.DEFAULT));
  }

  private void click(int slot, ClickKind kind) {
    menus.menu.button(slot).action().click(new MenuClick(player, menus.session, slot, kind));
  }

  private int count(Id type) {
    int total = 0;
    for (int slot = 0; slot < 36; slot++) {
      ItemRef item = player.inventory().item(slot);
      if (item != null && !item.isEmpty() && item.type().equals(type)) {
        total += item.amount();
      }
    }
    return total;
  }

  @SuppressWarnings("unchecked")
  private MutationTool<ArtisansLedger.Data> ledger() {
    for (MutationTool<?> tool : MutationTools.all(registry)) {
      if (tool instanceof ArtisansLedger) {
        return (MutationTool<ArtisansLedger.Data>) tool;
      }
    }
    throw new AssertionError("No ledger");
  }

  private FieldWorkshop workshop() {
    return (FieldWorkshop) ledger().owner();
  }

  private void mutate(int level) {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("crafting", "architect")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("crafting"), registry.getSkill("architect"), "Artisan"))
        .isEqualTo(SkillMutations.Result.MUTATED);
    profile.getData().getSkillLine("crafting").setAdaptation(workshop(), level);
  }

  private static ItemRef backpack(ItemRef stored) {
    ItemRef stack = new FakeItem(Id.minecraft("bundle"), 1);
    stack.data().set(BackpackItem.io.dataKey(), DataBoundItem.GSON.toJson(BackpackItem.Data.of("ledger", BackpackItem.Mode.SLOTS, 9, 1)));
    BackpackItem.writeContents(stack, new ItemRef[]{stored});
    return stack;
  }

  private static final class Menus implements MenuHost {
    private ChestMenu menu;
    private Session session;

    @Override
    public MenuSession open(PlayerRef viewer, ChestMenu opened) {
      menu = opened;
      session = new Session(viewer);
      return session;
    }

    @Override
    public MenuSession current(PlayerRef viewer) {
      return session;
    }

    @Override
    public void closeAll() {
      session = null;
    }

    @Override
    public StorageView openStorage(PlayerRef viewer, String title, int size, Consumer<StorageView> onClose) {
      return null;
    }

    @Override
    public StorageView currentStorage(PlayerRef viewer) {
      return null;
    }

    private final class Session implements MenuSession {
      private final PlayerRef viewer;
      private boolean open = true;

      private Session(PlayerRef viewer) {
        this.viewer = viewer;
      }

      @Override
      public PlayerRef viewer() {
        return viewer;
      }

      @Override
      public ChestMenu menu() {
        return menu;
      }

      @Override
      public boolean isOpen() {
        return open && session == this;
      }

      @Override
      public void update(ChestMenu updated) {
        menu = updated;
      }

      @Override
      public void close() {
        open = false;
      }
    }
  }
}
