package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.engine.value.MaterialCount;
import art.arcane.adapt.engine.value.MaterialRecipe;
import art.arcane.adapt.engine.value.MaterialValuePort;
import art.arcane.adapt.content.adaptation.architect.upgraded.MasterBuilder;
import art.arcane.adapt.content.item.ChalkWandItem;
import art.arcane.adapt.content.item.ChalkWandItem.Plan;
import art.arcane.adapt.content.item.ChalkWandItem.Plane;
import art.arcane.adapt.content.item.ChalkWandItem.Point;
import art.arcane.adapt.content.item.ChalkWandItem.Tool;
import art.arcane.adapt.content.skill.SkillArchitect;
import art.arcane.adapt.content.skill.SkillCrafting;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static art.arcane.adapt.content.mutation.signature.SignatureBench.Y;
import static org.assertj.core.api.Assertions.assertThat;

/** Architect leading Crafting: a chalk fill short of stairs cuts them from carried stone bricks at the recipe's yield. */
class CuttingOnTheFlyTest {
  private static final Id STAIRS = Id.minecraft("stone_brick_stairs");
  private static final Id BRICKS = Id.minecraft("stone_bricks");
  private static final Id STONE = Id.minecraft("stone");
  private static final Id COBBLESTONE = Id.minecraft("cobblestone");

  @TempDir
  Path data;

  private SignatureBench bench;
  private final Recipes recipes = new Recipes(new HashMap<>(Map.of(
      STAIRS, List.of(recipe(BRICKS, 6, STAIRS, 4, MaterialRecipe.Kind.CRAFTING),
          recipe(BRICKS, 1, STAIRS, 1, MaterialRecipe.Kind.STONECUTTING)),
      STONE, List.of(recipe(COBBLESTONE, 1, STONE, 1, MaterialRecipe.Kind.OTHER)))));

  @BeforeEach
  void start() {
    AdaptPorts.install(MaterialValuePort.class, recipes);
    bench = new SignatureBench(data, List.of(SkillArchitect.class, SkillCrafting.class));
  }

  @AfterEach
  void stop() {
    bench.close();
    AdaptPorts.uninstall(MaterialValuePort.class);
  }

  @Test
  void aFillShortOfStairsCutsThemFromStoneBricksAtTheStonecuttersYield() {
    bench.signature("architect", "crafting", 1);
    bench.upgrade("architect", MasterBuilder.class, 1);
    holdGuide(2, 9);
    bench.player.inventory().setOffHand(new FakeItem(STAIRS, 1));
    bench.player.inventory().setItem(20, new FakeItem(BRICKS, 20));

    fill();
    bench.advance(20);

    int bricksUsed = 20 - bench.count(BRICKS);
    assertThat(bench.placed(STAIRS, row())).isEqualTo(8);
    assertThat(bench.count(STAIRS) + 8).isEqualTo(1 + bricksUsed);
    assertThat(bench.handler("architect", "crafting", CuttingOnTheFly.class))
        .isInstanceOf(CuttingOnTheFly.class);
  }

  @Test
  void theReverseOrderAndAPlayerWithoutTheSignatureCutNothing() {
    bench.signature("crafting", "architect", 3);
    bench.upgrade("architect", MasterBuilder.class, 1);
    holdGuide(2, 9);
    bench.player.inventory().setOffHand(new FakeItem(STAIRS, 1));
    bench.player.inventory().setItem(20, new FakeItem(BRICKS, 20));

    fill();
    bench.advance(20);

    assertThat(bench.placed(STAIRS, row())).isEqualTo(1);
    assertThat(bench.count(BRICKS)).isEqualTo(20);
  }

  @Test
  void nothingIsCutBeforeTheSignatureIsLearned() {
    bench.kit.mutate("architect", "crafting");
    bench.upgrade("architect", MasterBuilder.class, 1);
    holdGuide(2, 9);
    bench.player.inventory().setOffHand(new FakeItem(STAIRS, 1));
    bench.player.inventory().setItem(20, new FakeItem(BRICKS, 20));

    fill();
    bench.advance(20);

    assertThat(bench.placed(STAIRS, row())).isEqualTo(1);
    assertThat(bench.count(BRICKS)).isEqualTo(20);
  }

  @Test
  void aBuildCutsNoMoreThanTheLevelAllowsAndTheCooldownHolds() {
    bench.signature("architect", "crafting", 1);
    bench.upgrade("architect", MasterBuilder.class, 1);
    CuttingOnTheFly cutting = bench.handler("architect", "crafting", CuttingOnTheFly.class);
    cutting.getConfig().piecesAtFirstLevel = 3;
    cutting.getConfig().cooldownMillis = 60_000L;
    holdGuide(2, 9);
    bench.player.inventory().setOffHand(new FakeItem(STAIRS, 1));
    bench.player.inventory().setItem(20, new FakeItem(BRICKS, 20));

    fill();
    bench.advance(20);
    int firstFill = bench.placed(STAIRS, row());
    holdGuide(2, 9);
    bench.player.inventory().setOffHand(new FakeItem(STAIRS, 1));
    for (BlockRef block : row()) {
      block.setType(Id.minecraft("air"), false);
    }
    fill();
    bench.advance(20);

    assertThat(firstFill).isEqualTo(4);
    assertThat(bench.placed(STAIRS, row())).isEqualTo(1);
    assertThat(bench.count(BRICKS)).isEqualTo(17);
    assertThat(cutting.pieces(1)).isEqualTo(3);
    assertThat(cutting.pieces(3)).isEqualTo(512);
  }

  @Test
  void aFurnaceRecipeIsNeverUsedToCutSoCobblestoneNeverBecomesStone() {
    bench.signature("architect", "crafting", 3);
    bench.upgrade("architect", MasterBuilder.class, 1);
    holdGuide(2, 9);
    bench.player.inventory().setOffHand(new FakeItem(STONE, 1));
    bench.player.inventory().setItem(20, new FakeItem(COBBLESTONE, 20));

    fill();
    bench.advance(20);

    assertThat(bench.placed(STONE, row())).isEqualTo(1);
    assertThat(bench.count(COBBLESTONE)).isEqualTo(20);
  }

  @Test
  void nothingIsCutWhileNoBuildRuns() {
    bench.signature("architect", "crafting", 3);
    holdGuide(2, 9);
    bench.player.inventory().setOffHand(new FakeItem(STAIRS, 1));
    bench.player.inventory().setItem(20, new FakeItem(BRICKS, 20));

    fill();
    bench.advance(20);

    assertThat(bench.count(BRICKS)).isEqualTo(20);
    assertThat(bench.count(STAIRS)).isEqualTo(1);
  }

  @Test
  void cuttingStopsWhenTheInventoryHasNoRoomForThePieces() {
    bench.signature("architect", "crafting", 3);
    bench.upgrade("architect", MasterBuilder.class, 1);
    holdGuide(2, 9);
    bench.player.inventory().setOffHand(new FakeItem(STAIRS, 1));
    bench.player.inventory().setItem(20, new FakeItem(BRICKS, 20));
    for (int slot = 0; slot < 36; slot++) {
      if (bench.player.inventory().item(slot).isEmpty()) {
        bench.player.inventory().setItem(slot, new FakeItem(Id.minecraft("dirt"), 64));
      }
    }

    fill();
    bench.advance(20);

    assertThat(20 - bench.count(BRICKS)).isLessThanOrEqualTo(6);
  }

  @Test
  void aReloadedRecipeListIsReadAgain() {
    bench.signature("architect", "crafting", 1);
    CuttingOnTheFly cutting = bench.handler("architect", "crafting", CuttingOnTheFly.class);
    assertThat(cutting.recipes(STAIRS)).hasSize(2);

    recipes.byOutput().put(STAIRS, List.of(recipe(BRICKS, 1, STAIRS, 1, MaterialRecipe.Kind.STONECUTTING)));

    assertThat(cutting.recipes(STAIRS)).hasSize(1);
  }

  private void holdGuide(int fromX, int toX) {
    ItemRef wand = ChalkWandItem.create(Tool.STRAIGHTEDGE);
    ChalkWandItem.writePlan(wand, new Plan(bench.world.uid(), List.of(new Point(fromX, Y, 3), new Point(toX, Y, 3)), Plane.XZ));
    bench.player.holding(wand);
    bench.player.setSneaking(true);
  }

  private void fill() {
    bench.adapt.post(new PlayerInteractEvent(bench.player, InteractAction.RIGHT_CLICK_AIR, Hand.MAIN_HAND,
        bench.player.inventory().mainHand(), null, BlockFace.UP, null, UseResult.DENY, UseResult.DEFAULT));
  }

  private List<BlockRef> row() {
    List<BlockRef> blocks = new ArrayList<>();
    for (int x = 2; x <= 9; x++) {
      blocks.add(bench.world.blockAt(x, Y, 3));
    }
    return blocks;
  }

  private static MaterialRecipe recipe(Id input, int inputs, Id output, int outputs, MaterialRecipe.Kind kind) {
    return new MaterialRecipe(new ArrayList<>(List.of(new MaterialCount(input, inputs))), new MaterialCount(output, outputs),
        kind);
  }

  private record Recipes(Map<Id, List<MaterialRecipe>> byOutput) implements MaterialValuePort {
    @Override
    public List<MaterialRecipe> recipesFor(Id material) {
      return byOutput.getOrDefault(material, List.of());
    }

    @Override
    public boolean breaksInstantly(Id material) {
      return false;
    }
  }
}
