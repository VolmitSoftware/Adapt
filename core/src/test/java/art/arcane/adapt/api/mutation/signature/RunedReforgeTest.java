package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.item.ItemDurability;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.content.adaptation.crafting.CraftingMasterwork;
import art.arcane.adapt.content.skill.SkillCrafting;
import art.arcane.adapt.content.skill.SkillEnchanting;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeInventory;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Enchanting and Crafting, either leading, replace Convergence with Runed Reforge; a bonus never exists twice. */
class RunedReforgeTest {
  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private MutationKit kit;
  private CraftingMasterwork masterwork;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Runesmith", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillEnchanting.class, SkillCrafting.class));
    kit = new MutationKit(adapt, adapt.join(player));
    for (Adaptation<?> adaptation : kit.registry().getSkill("crafting").getAdaptations()) {
      if (adaptation instanceof CraftingMasterwork found) {
        masterwork = found;
      }
    }
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void theBonusMovesFromOnePieceThroughTheBookToAnotherWithEitherLead() {
    RunedReforge reforge = learn("enchanting", "crafting", 3);
    assertThat(kit.mutated("crafting", "enchanting").getMutationAdaptation(Recipe.CONVERGENCE).getSignature())
        .isInstanceOf(RunedReforge.class);
    ItemRef source = masterworked("iron_pickaxe", 0.2D, true);
    player.holding(blank(reforge));
    player.inventory().setOffHand(source);

    use(player);

    assertThat(CraftingMasterwork.bonus(player.inventory().offHand())).isNull();
    assertThat(ItemDurability.maxDamage(player.inventory().offHand())).isEqualTo(250);
    RunedReforge.RunedBook.Data held = reforge.book().read(player.inventory().mainHand());
    assertThat(held.share).isCloseTo(0.2D, within(1.0E-9D));
    assertThat(held.edge).isTrue();

    player.inventory().setOffHand(FakeItem.tool("diamond_pickaxe", 1561));
    use(player);

    CraftingMasterwork.Bonus moved = CraftingMasterwork.bonus(player.inventory().offHand());
    assertThat(moved.durabilityShare()).isCloseTo(0.2D, within(0.001D));
    assertThat(moved.edge()).isTrue();
    assertThat(player.inventory().mainHand().isEmpty()).isTrue();
  }

  @Test
  void durabilityRaisedByAnythingElseStaysOnThePiece() {
    RunedReforge reforge = learn("enchanting", "crafting", 3);
    ItemRef source = masterworked("iron_pickaxe", 0.2D, false);
    ItemDurability.setMaxDamage(source, ItemDurability.maxDamage(source) + 100);
    player.holding(blank(reforge));
    player.inventory().setOffHand(source);

    use(player);

    assertThat(reforge.book().read(player.inventory().mainHand()).share).isCloseTo(0.2D, within(1.0E-9D));
    assertThat(ItemDurability.maxDamage(player.inventory().offHand())).isEqualTo(350);
  }

  @Test
  void aPieceRolledBeforeTheBonusWasRecordedReadsItsExtraDurability() {
    ItemRef legacy = masterworked("iron_pickaxe", 0.2D, false);
    legacy.data().remove(Id.adapt("masterwork_durability"));

    assertThat(CraftingMasterwork.bonus(legacy).durabilityShare()).isCloseTo(0.2D, within(1.0E-9D));
  }

  @Test
  void theFirstLevelKeepsLessAndLeavesTheEdgeBehind() {
    RunedReforge reforge = learn("crafting", "enchanting", 1);
    player.holding(blank(reforge));
    player.inventory().setOffHand(masterworked("iron_pickaxe", 0.2D, true));

    use(player);

    RunedReforge.RunedBook.Data held = reforge.book().read(player.inventory().mainHand());
    assertThat(held.share).isCloseTo(0.14D, within(1.0E-9D));
    assertThat(held.edge).isFalse();
  }

  @Test
  void theBookIsInertWithoutTheMutation() {
    RunedReforge reforge = signature("enchanting", "crafting");
    ItemRef source = masterworked("iron_pickaxe", 0.2D, false);
    player.holding(blank(reforge));
    player.inventory().setOffHand(source);

    use(player);

    assertThat(CraftingMasterwork.bonus(player.inventory().offHand())).isNotNull();
    assertThat(reforge.book().read(player.inventory().mainHand()).share).isNull();
  }

  @Test
  void aBonusIsNeverInTwoPlacesThroughAnyExit() {
    RunedReforge reforge = learn("enchanting", "crafting", 3);
    player.holding(blank(reforge));
    player.inventory().setOffHand(masterworked("iron_pickaxe", 0.2D, false));

    player.openMenu(new FakeInventory(), player.location());
    use(player);
    assertThat(bonuses(reforge, player)).as("menu open").isEqualTo(1);
    assertThat(CraftingMasterwork.bonus(player.inventory().offHand())).as("menu open").isNotNull();
    player.openMenu(null, null);

    for (int slot = 1; slot < 36; slot++) {
      player.inventory().setItem(slot, new FakeItem(Id.minecraft("stone"), 64));
    }
    use(player);
    assertThat(bonuses(reforge, player)).as("full inventory").isEqualTo(1);
    assertThat(reforge.book().read(player.inventory().mainHand()).share).isNotNull();

    player.inventory().setOffHand(masterworked("diamond_pickaxe", 0.1D, false));
    use(player);
    assertThat(bonuses(reforge, player)).as("one bonus per item").isEqualTo(2);
    assertThat(reforge.book().read(player.inventory().mainHand()).share).isNotNull();

    adapt.post(new PlayerQuitEvent(player));
    use(player);
    assertThat(bonuses(reforge, player)).as("after leaving").isEqualTo(2);

    FakePlayer other = platform.player("Other", 2.5D, 61D, 0.5D);
    adapt.join(other);
    other.holding(player.inventory().mainHand().copy());
    player.inventory().setMainHand(FakeItem.empty());
    other.inventory().setOffHand(FakeItem.tool("iron_pickaxe", 250));
    use(other);
    assertThat(CraftingMasterwork.bonus(other.inventory().offHand())).as("other player").isNull();
    assertThat(reforge.book().read(other.inventory().mainHand()).share).as("other player").isNotNull();
  }

  @Test
  void onlyAPlayerWhoLearnedItCraftsTheRunedBook() {
    RunedReforge reforge = signature("enchanting", "crafting");
    Id recipe = reforge.getRecipes().getFirst().key();
    platform.scheduler().advance(200);

    assertThat(kit.registry().getRequiredAdaptation(recipe)).as(recipe.toString()).isSameAs(reforge);
    assertThat(craft(recipe).isCancelled()).isTrue();
    learn("enchanting", "crafting", 1);
    assertThat(craft(recipe).isCancelled()).isFalse();
  }

  private CraftItemEvent craft(Id recipe) {
    return adapt.post(new CraftItemEvent(player, InventoryKind.WORKBENCH, 0, 0, ClickKind.LEFT,
        InventoryAction.PICKUP_ALL, -1, FakeItem.of("book"), FakeItem.empty(), recipe,
        CraftItemEvent.RecipeKind.SHAPELESS, List.of()));
  }

  private RunedReforge learn(String lead, String partner, int level) {
    kit.mutate(lead, partner);
    kit.learn(lead, partner, Recipe.CONVERGENCE, level);
    return (RunedReforge) kit.adaptation(lead, partner, Recipe.CONVERGENCE).getSignature();
  }

  private RunedReforge signature(String lead, String partner) {
    for (Signature<?> signature : kit.registry().getSkill(lead).getSignatures()) {
      if (signature instanceof RunedReforge reforge && signature.getRow().partnerId().equals(partner)) {
        return reforge;
      }
    }
    throw new AssertionError("No Runed Reforge for " + lead + " and " + partner);
  }

  private ItemRef masterworked(String type, double share, boolean edge) {
    int base = type.equals("diamond_pickaxe") ? 1561 : 250;
    ItemRef gear = masterwork.apply(FakeItem.tool(type, base), new CraftingMasterwork.Bonus(share, edge));
    assertThat(gear).isNotNull();
    return gear;
  }

  private static ItemRef blank(RunedReforge reforge) {
    return reforge.book().create(new RunedReforge.RunedBook.Data());
  }

  private int bonuses(RunedReforge reforge, FakePlayer who) {
    int count = 0;
    for (ItemRef item : who.inventory().contents()) {
      RunedReforge.RunedBook.Data book = reforge.book().read(item);
      if (CraftingMasterwork.bonus(item) != null || (book != null && book.share != null)) {
        count++;
      }
    }
    return count;
  }

  private void use(FakePlayer who) {
    adapt.post(new PlayerInteractEvent(who, InteractAction.RIGHT_CLICK_AIR, Hand.MAIN_HAND, who.inventory().mainHand(),
        null, BlockFace.UP, null, UseResult.DEFAULT, UseResult.DEFAULT));
  }
}
