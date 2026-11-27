package art.arcane.adapt.engine.potion;

import art.arcane.adapt.api.item.Potions;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.item.BrewingRecipe;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillPickaxes;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.adapt.AdaptBrewCompleteEvent;
import art.arcane.adapt.platform.event.inventory.BrewEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.SlotKind;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBrewingStand;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class BrewingRuntimeTest {

  @TempDir
  Path data;

  private TestAdapt adapt;
  private FakeWorld world;
  private FakePlayer player;
  private AdaptPlayer profile;
  private BrewingRecipe recipe;
  private Adaptation<?> required;
  private BlockRef standBlock;
  private FakeBrewingStand stand;
  private final AtomicReference<AdaptBrewCompleteEvent> completed = new AtomicReference<>();

  @BeforeEach
  void start() {
    TestPlatform platform = TestPlatform.builder(data).build();
    world = platform.world();
    player = platform.player("Brewer", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillPickaxes.class));
    profile = adapt.join(player);
    recipe = new BrewingRecipe(Id.adapt("brewing_test_1"), Id.minecraft("iron_ingot"),
        Potions.vanilla(Potions.Type.REGULAR, Id.minecraft("awkward")),
        Potions.of(Potions.Type.REGULAR)
            .setName("Potion of Testing")
            .setColor(Rgb.of(0xFFFFFF))
            .addEffect(Id.minecraft("resistance"), 1200, 0, true, true, true)
            .build(),
        3, 16);
    required = adapt.core().getAdaptServer().getSkillRegistry().getSkill("pickaxe").getAdaptations().getFirst();
    BrewingManager.registerRecipe(required.getId().toString(), recipe);
    standBlock = world.set(0, 60, 0, "brewing_stand").blockAt(0, 60, 0);
    stand = world.brewingStand(standBlock.pos());
    stand.view(player);
    player.openContainer(standBlock);
    adapt.bus().register(this, AdaptBrewCompleteEvent.class, EventPriority.MONITOR, completed::set);
  }

  @AfterEach
  void stop() {
    BrewingManager.unregisterRecipe(required.getId().toString(), recipe);
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  private void learn() {
    profile.getData().getSkillLine("pickaxe").setAdaptation(required, 1);
  }

  private void loadStand(ItemRef ingredient) {
    stand.setBottle(0, recipe.basePotion().copy());
    stand.setIngredient(ingredient);
    FakeItem fuel = FakeItem.of("blaze_powder");
    fuel.setAmount(2);
    stand.setFuel(fuel);
  }

  private InventoryClickEvent click(int rawSlot, ItemRef cursor) {
    return adapt.post(new InventoryClickEvent(player, InventoryKind.BREWING, InventoryKind.BREWING, rawSlot, rawSlot,
        SlotKind.CONTAINER, ClickKind.LEFT, InventoryAction.PLACE_ALL, -1, FakeItem.empty(), cursor));
  }

  @Test
  void learnedRecipeBrewsTheResultConsumesFuelAndPostsTheCoreEvent() {
    learn();
    loadStand(FakeItem.of("iron_ingot"));

    click(0, FakeItem.empty());
    adapt.awaitTicks(() -> completed.get() != null, 5_000L);

    assertThat(stand.bottle(0).isSimilar(recipe.result())).isTrue();
    assertThat(stand.ingredient().isEmpty()).isTrue();
    assertThat(stand.fuel().amount()).isEqualTo(1);
    assertThat(stand.fuelLevel()).isEqualTo(BrewingTask.remainingFuel(0, 16));
    assertThat(completed.get().block()).isEqualTo(standBlock);
    assertThat(completed.get().recipeId()).isEqualTo("adapt:brewing_test_1");
    assertThat(completed.get().brewerId()).isEqualTo(player.uuid());
    assertThat(completed.get().brewedPotions()).isEqualTo(1);
  }

  @Test
  void unlearnedRecipeLeavesTheStandAlone() {
    loadStand(FakeItem.of("iron_ingot"));

    click(0, FakeItem.empty());
    adapt.platform().scheduler().advance(10);

    assertThat(completed.get()).isNull();
    assertThat(stand.bottle(0).isSimilar(recipe.basePotion())).isTrue();
    assertThat(stand.fuel().amount()).isEqualTo(2);
    assertThat(stand.brewingTime()).isZero();
  }

  @Test
  void customIngredientClickIsPlacedByAdaptAndStartsTheBrew() {
    learn();
    loadStand(FakeItem.empty());
    FakeItem ingot = FakeItem.of("iron_ingot");
    player.inventory().setCursor(ingot.copy());

    InventoryClickEvent event = click(3, ingot);
    assertThat(event.isCancelled()).isTrue();
    adapt.awaitTicks(() -> completed.get() != null, 5_000L);

    assertThat(player.inventory().cursor().isEmpty()).isTrue();
    assertThat(stand.bottle(0).isSimilar(recipe.result())).isTrue();
  }

  @Test
  void gunpowderTurnsCustomPotionsIntoSplashPotionsAndLeavesVanillaOnes() {
    ItemRef custom = recipe.result().copy();
    ItemRef vanilla = Potions.vanilla(Potions.Type.REGULAR, Id.minecraft("swiftness"));
    List<ItemRef> potions = List.of(custom, vanilla, FakeItem.empty());
    List<ItemRef> results = new ArrayList<>(List.of(vanilla.copy(), vanilla.copy(), FakeItem.empty()));

    BrewEvent event = adapt.post(new BrewEvent(standBlock, FakeItem.of("gunpowder"), potions, results, 0, 0));

    assertThat(event.results().get(0).type()).isEqualTo(Id.minecraft("splash_potion"));
    assertThat(event.results().get(0).potionContents()).isEqualTo(custom.potionContents());
    assertThat(event.results().get(1).potionContents().map(PotionContents::potion)).contains(Id.minecraft("swiftness"));
    assertThat(event.results().get(1).type()).isEqualTo(Id.minecraft("potion"));
  }

  @Test
  void potionBuilderWritesTheContentsOnThePlatformItem() {
    PotionContents contents = recipe.result().potionContents().orElseThrow();

    assertThat(recipe.result().type()).isEqualTo(Id.minecraft("potion"));
    assertThat(contents.potion()).isNull();
    assertThat(contents.name()).isEqualTo("Potion of Testing");
    assertThat(contents.color()).isEqualTo(Rgb.of(0xFFFFFF));
    assertThat(contents.customEffects())
        .containsExactly(EffectInstance.of(Id.minecraft("resistance"), 1200, 0).withAmbient(true));
  }
}
