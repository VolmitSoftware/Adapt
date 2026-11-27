package art.arcane.adapt.engine.mutation.upgrade;

import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.api.mutation.ToolUse;
import art.arcane.adapt.content.item.ToolItems;
import art.arcane.adapt.content.skill.SkillArchitect;
import art.arcane.adapt.content.skill.SkillCrafting;
import art.arcane.adapt.engine.mutation.tool.MutationTools;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.block.CrafterCraftEvent;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.PrepareItemCraftEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerItemConsumeEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A tool works only for a holder at its required level, and is inert as a vanilla item for everyone. */
class MutationToolTest {
  private static final Id ID = Id.adapt("architect/tool/test_lantern");

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private AdaptPlayer profile;
  private SkillUpgrade<?> owner;
  private Lantern lantern;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Mason", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillArchitect.class, SkillCrafting.class));
    profile = adapt.join(player);
    owner = (SkillUpgrade<?>) adapt.core().getAdaptServer().getSkillRegistry()
        .getAdaptation(Id.adapt("architect/upgrade/master_builder"));
    lantern = new Lantern(owner);
    register(owner, lantern);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void aToolIsAPlainMarkedItemThatRoundTripsItsData() {
    Lantern.Data stored = new Lantern.Data();
    stored.charges = 4;
    ItemRef item = lantern.create(stored);

    assertThat(item.type()).isEqualTo(Id.minecraft("lantern"));
    assertThat(lantern.is(item)).isTrue();
    assertThat(lantern.read(item).orElseThrow().charges).isEqualTo(4);
    assertThat(lantern.read(FakeItem.of("lantern"))).isEmpty();
    assertThat(MutationTools.of(item)).isSameAs(lantern);
  }

  @Test
  void itsRecipesBecomeTheOwnersRecipesAtTheRequiredLevel() {
    assertThat(owner.getTools()).contains(lantern);
    assertThat(owner.getRecipes()).anySatisfy((RecipeSpec recipe) -> {
      assertThat(recipe.key()).isEqualTo(ID);
      assertThat(recipe.requiredLevel()).isEqualTo(2);
      assertThat(lantern.is(recipe.result())).isTrue();
    });
  }

  @Test
  void anyoneWithoutTheOwnerAtTheRequiredLevelGetsNothingFromIt() {
    player.holding(lantern.create(new Lantern.Data()));

    PlayerInteractEvent unmutated = use(Hand.MAIN_HAND);
    new MutationKit(adapt, profile).mutate("architect", "crafting");
    profile.getData().getSkillLine("architect").setAdaptation(owner, 1);
    PlayerInteractEvent tooLow = use(Hand.MAIN_HAND);

    assertThat(unmutated.useItem()).isEqualTo(UseResult.DENY);
    assertThat(tooLow.useItem()).isEqualTo(UseResult.DENY);
    assertThat(lantern.uses).isEmpty();
    assertThat(player.actionBars()).isNotEmpty();
  }

  @Test
  void aHolderAtTheRequiredLevelUsesItInTheMainHandOnly() {
    player.holding(lantern.create(new Lantern.Data()));
    new MutationKit(adapt, profile).mutate("architect", "crafting");
    profile.getData().getSkillLine("architect").setAdaptation(owner, 2);

    PlayerInteractEvent main = use(Hand.MAIN_HAND);
    PlayerInteractEvent off = use(Hand.OFF_HAND);

    assertThat(main.useItem()).isEqualTo(UseResult.DENY);
    assertThat(off.useItem()).isEqualTo(UseResult.DENY);
    assertThat(lantern.uses).hasSize(1);
    assertThat(lantern.uses.getFirst().level()).isEqualTo(2);
  }

  @Test
  void itCannotBePlacedEatenCraftedIntoAnythingElseOrMadeByACrafter() {
    ItemRef item = lantern.create(new Lantern.Data());
    BlockRef block = platform.world().blockAt(1, 61, 0);

    BlockPlaceEvent place = adapt.post(new BlockPlaceEvent(player, block, block.state(), block, item, Hand.MAIN_HAND));
    PlayerItemConsumeEvent eat = adapt.post(new PlayerItemConsumeEvent(player, item, Hand.MAIN_HAND, null));
    PrepareItemCraftEvent other = adapt.post(new PrepareItemCraftEvent(player, List.of(item), Id.minecraft("chain"),
        CraftItemEvent.RecipeKind.SHAPELESS, false, FakeItem.of("chain")));
    PrepareItemCraftEvent own = adapt.post(new PrepareItemCraftEvent(player, List.of(item), ID.withPath(ID.path() + "/refill"),
        CraftItemEvent.RecipeKind.SHAPELESS, false, lantern.create(new Lantern.Data())));
    platform.world().blockInventory(block.pos(), new art.arcane.adapt.testkit.FakeInventory());
    CrafterCraftEvent crafter = adapt.post(new CrafterCraftEvent(block, ID, lantern.create(new Lantern.Data())));

    assertThat(place.isCancelled()).isTrue();
    assertThat(eat.isCancelled()).isTrue();
    assertThat(other.result().isEmpty()).isTrue();
    assertThat(lantern.is(own.result())).isTrue();
    assertThat(crafter.isCancelled()).isTrue();
  }

  /** Registers the tool on the upgrade the way an upgrade's constructor does, then drops the tool index. */
  private static void register(SkillUpgrade<?> upgrade, MutationTool<?> tool) {
    try {
      Method registerTool = SkillUpgrade.class.getDeclaredMethod("registerTool", MutationTool.class);
      registerTool.setAccessible(true);
      registerTool.invoke(upgrade, tool);
    } catch (ReflectiveOperationException failed) {
      throw new AssertionError(failed);
    }
    MutationTools.forget();
  }

  private PlayerInteractEvent use(Hand hand) {
    ItemRef item = player.inventory().mainHand();
    return adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_AIR, hand, item, null, BlockFace.UP,
        null, UseResult.DEFAULT, UseResult.DEFAULT));
  }

  private static final class Lantern extends MutationTool<Lantern.Data> {
    private final List<ToolUse> uses = new ArrayList<>();

    Lantern(Adaptation<?> owner) {
      super(ToolItems.builder(ID, Id.minecraft("lantern"), Data.class, new Data()).build(), owner, 2);
    }

    @Override
    public List<RecipeSpec> recipes() {
      return List.of(RecipeSpec.shapeless(ID, List.of(Ingredient.of(Id.minecraft("torch"))), create(new Data())).withRequiredLevel(1));
    }

    @Override
    public void use(ToolUse use) {
      uses.add(use);
    }

    static final class Data {
      int charges;
    }
  }
}
