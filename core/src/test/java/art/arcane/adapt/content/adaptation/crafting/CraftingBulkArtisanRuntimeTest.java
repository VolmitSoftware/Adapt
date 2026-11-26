package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillCrafting;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.ItemCraftedEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.recipe.RecipeChoice;
import art.arcane.adapt.platform.recipe.RecipeView;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CraftingBulkArtisanRuntimeTest {
  private static final Id MILK = Id.minecraft("milk_bucket");
  private static final Id BUCKET = Id.minecraft("bucket");
  private static final Id SUGAR = Id.minecraft("sugar");
  private static final Id CAKE = Id.minecraft("cake");
  private static final Id RECIPE = Id.minecraft("cake");

  @TempDir
  Path data;

  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private SkillRegistry registry;

  @BeforeEach
  void start() {
    TestPlatform platform = TestPlatform.builder(data).build();
    platform.recipes().serverRecipe(new RecipeView(RECIPE, RecipeView.Kind.SHAPELESS, List.of(), Map.of(),
        List.of(RecipeChoice.ofTypes(List.of(MILK)), RecipeChoice.ofTypes(List.of(SUGAR))), new FakeItem(CAKE, 1)));
    player = platform.player("Baker", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillCrafting.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void aBatchReturnsTheBucketOfEveryMilkItUses() {
    CraftingBulkArtisan bulk = registry.getSkill("crafting").getAdaptations().stream()
        .filter(CraftingBulkArtisan.class::isInstance).map(CraftingBulkArtisan.class::cast).findFirst().orElseThrow();
    profile.getData().getSkillLine("crafting").setAdaptation(bulk, 1);
    player.inventory().setItem(9, new FakeItem(MILK, 1));
    player.inventory().setItem(10, new FakeItem(MILK, 1));
    player.inventory().setItem(11, new FakeItem(MILK, 1));
    player.inventory().setItem(12, new FakeItem(SUGAR, 3));
    List<ItemRef> matrix = List.of(new FakeItem(MILK, 1), new FakeItem(SUGAR, 1));

    adapt.post(new CraftItemEvent(player, InventoryKind.WORKBENCH, 0, 0, ClickKind.SHIFT_LEFT,
        InventoryAction.MOVE_TO_OTHER_INVENTORY, -1, new FakeItem(CAKE, 1), FakeItem.empty(), RECIPE,
        CraftItemEvent.RecipeKind.SHAPELESS, matrix));
    adapt.post(new ItemCraftedEvent(player, new FakeItem(CAKE, 1), RECIPE));
    adapt.awaitTicks(() -> count(CAKE) > 0, 5_000L);

    assertThat(count(CAKE)).isEqualTo(3);
    assertThat(count(MILK)).isZero();
    assertThat(count(SUGAR)).isZero();
    assertThat(count(BUCKET)).isEqualTo(3);
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
}
