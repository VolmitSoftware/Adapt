package art.arcane.adapt.api.recipe;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.SkillConfig;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.SkillMessages;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
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

class RecipeUnlockRuntimeTest {
  private static final Id BASIC = Id.adapt("recipe-test-basic");
  private static final Id ADVANCED = Id.adapt("recipe-test-advanced");
  private static final Id SMELTED = Id.adapt("recipe-test-smelted");

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private AdaptPlayer profile;
  private SkillRegistry registry;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Crafter", 0.5D, 64D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(RecipeSkill.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    adapt.awaitTicks(registry::isRecipeRegistrationReady, 10_000L);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void everyRecipeReachesThePlatformWithItsRequiredLevel() {
    assertThat(platform.recipes().registered()).contains(BASIC, ADVANCED, SMELTED);
    assertThat(platform.recipes().spec(BASIC).requiredLevel()).isEqualTo(1);
    assertThat(platform.recipes().spec(ADVANCED).requiredLevel()).isEqualTo(2);
    assertThat(platform.recipes().spec(SMELTED)).isInstanceOf(RecipeSpec.Cooking.class);
    assertThat(registry.getRequiredRecipeLevel(BASIC)).isEqualTo(1);
    assertThat(registry.getRequiredRecipeLevel(ADVANCED)).isEqualTo(2);
    assertThat(registry.getRequiredAdaptation(ADVANCED)).isInstanceOf(RecipeAdaptation.class);
  }

  @Test
  void craftingARecipeAboveTheLearnedLevelIsCancelled() {
    learn(1);

    assertThat(adapt.post(craft(BASIC)).isCancelled()).isFalse();
    assertThat(adapt.post(craft(ADVANCED)).isCancelled()).isTrue();

    learn(2);

    assertThat(adapt.post(craft(ADVANCED)).isCancelled()).isFalse();
  }

  @Test
  void craftingWithoutTheAdaptationIsCancelled() {
    assertThat(adapt.post(craft(BASIC)).isCancelled()).isTrue();
    assertThat(adapt.post(craft(Id.minecraft("crafting_table"))).isCancelled()).isFalse();
  }

  @Test
  void theRecipeBookHoldsOnlyRecipesTheLearnedLevelUnlocks() {
    learn(1);
    synchronize();

    assertThat(platform.recipes().discovered(player)).contains(BASIC, SMELTED).doesNotContain(ADVANCED);

    learn(2);
    synchronize();

    assertThat(platform.recipes().discovered(player)).contains(BASIC, SMELTED, ADVANCED);

    learn(0);
    synchronize();

    assertThat(platform.recipes().discovered(player)).doesNotContain(BASIC, SMELTED, ADVANCED);
  }

  private void learn(int level) {
    RecipeAdaptation adaptation = (RecipeAdaptation) registry.getRequiredAdaptation(BASIC);
    profile.getData().getSkillLine("recipe-test").setAdaptation(adaptation, level);
  }

  private void synchronize() {
    adapt.core().getAdaptServer().synchronizeRecipeBook(player.uuid(), profile);
    platform.scheduler().advance(2);
  }

  private CraftItemEvent craft(Id key) {
    return new CraftItemEvent(player, InventoryKind.WORKBENCH, 0, 0, ClickKind.LEFT, InventoryAction.PICKUP_ALL, -1,
        FakeItem.of("stick"), FakeItem.empty(), key, CraftItemEvent.RecipeKind.SHAPED, List.of());
  }

  public static final class RecipeSkill extends SimpleSkill<RecipeSkill.Config> {
    public RecipeSkill() {
      super("recipe-test", SkillPresentation.of(SkillMessages.HUNTER_NAME, SkillMessages.HUNTER_ICON, SkillMessages.HUNTER_DESCRIPTION,
          SkillMessages.HUNTER_QUIP, SkillMessages.HUNTER_UNLOCK_QUIP_1));
      registerConfiguration(Config.class);
      setIcon(Id.minecraft("crafting_table"));
      registerAdaptation(new RecipeAdaptation());
    }

    @Override
    public boolean isEnabled() {
      return getConfig().enabled;
    }

    public static class Config extends SkillConfig {
      boolean enabled = true;
    }
  }

  public static final class RecipeAdaptation extends SimpleAdaptation<AdaptationConfig> {
    public RecipeAdaptation() {
      super("recipe-test-crafter");
      registerConfiguration(AdaptationConfig.class);
      setLocalizationKey("pickaxe.chisel");
      setIcon(Id.minecraft("crafting_table"));
      registerRecipe(new RecipeSpec.Shaped(BASIC, List.of("S"), Map.of('S', Ingredient.of(Id.minecraft("stick"))), FakeItem.of("stone")));
      registerRecipe(new RecipeSpec.Shapeless(ADVANCED, List.of(Ingredient.of(Id.minecraft("stone"))), FakeItem.of("stick"), 2));
      registerRecipe(new RecipeSpec.Cooking(SMELTED, RecipeSpec.CookingStation.SMOKER, Ingredient.of(Id.minecraft("cobblestone")),
          FakeItem.of("stone"), 0.1F, 100));
    }

    @Override
    public void addStats(int level, StatLines lines) {
    }
  }
}
