package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.mutation.tool.MutationTools;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.architect.upgraded.MasterBuilder;
import art.arcane.adapt.content.adaptation.excavation.upgraded.Landshaper;
import art.arcane.adapt.content.item.ChalkWandItem;
import art.arcane.adapt.content.item.ChalkWandItem.Plan;
import art.arcane.adapt.content.item.ChalkWandItem.Plane;
import art.arcane.adapt.content.item.ChalkWandItem.Point;
import art.arcane.adapt.content.item.ChalkWandItem.Tool;
import art.arcane.adapt.content.skill.SkillArchitect;
import art.arcane.adapt.content.skill.SkillExcavation;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/** Excavation and Architect, either leading: Grading Stakes dig a chalk guide and pour it full of the off-hand block. */
class FoundationPourTest {
  private static final Id DIRT = Id.minecraft("dirt");
  private static final Id COBBLE = Id.minecraft("cobblestone");

  @TempDir
  Path data;

  private SignatureBench bench;

  @BeforeEach
  void start() {
    bench = new SignatureBench(data, List.of(SkillExcavation.class, SkillArchitect.class));
    for (int x = 0; x <= 11; x++) {
      for (int y = 57; y <= 60; y++) {
        bench.world.set(x, y, 3, "dirt");
      }
    }
  }

  @AfterEach
  void stop() {
    bench.close();
  }

  @Test
  void theGuideIsDugAndPouredFullOfTheOffHandBlock() {
    bench.signature("excavation", "architect", 1);
    tools();

    pour();

    assertThat(bench.placed(COBBLE, row(60))).isEqualTo(8);
    assertThat(bench.placed(COBBLE, row(59))).isZero();
    assertThat(bench.count(COBBLE)).isEqualTo(56);
    assertThat(bench.count(DIRT)).isEqualTo(8);
    assertThat(bench.player.inventory().heldSlot()).isZero();
  }

  @Test
  void theLeftClickThatPoursIsTakenAndOneThatDoesNotIsLeft() {
    bench.signature("excavation", "architect", 1);
    tools();
    bench.handler("excavation", "architect", FoundationPour.class).getConfig().cooldownMillis = 60_000L;

    PlayerInteractEvent poured = pourOnBlock();
    PlayerInteractEvent cooling = pourOnBlock();

    assertThat(poured.isCancelled()).isTrue();
    assertThat(cooling.isCancelled()).isFalse();
  }

  @Test
  void eitherSkillMayLeadAndEachLevelPoursOneBlockDeeper() {
    bench.signature("architect", "excavation", 2);
    tools();

    pour();

    assertThat(bench.placed(COBBLE, row(60))).isEqualTo(8);
    assertThat(bench.placed(COBBLE, row(59))).isEqualTo(8);
    assertThat(bench.count(COBBLE)).isEqualTo(48);
  }

  @Test
  void nothingIsPouredWithoutTheLearnedSignature() {
    bench.kit.mutate("excavation", "architect");
    tools();

    pour();

    assertThat(bench.placed(COBBLE, row(60))).isZero();
    assertThat(bench.count(COBBLE)).isEqualTo(64);
  }

  @Test
  void aPourStopsAtTheLevelsBlocksAndTheCooldownHolds() {
    bench.signature("excavation", "architect", 1);
    tools();
    FoundationPour pour = bench.handler("excavation", "architect", FoundationPour.class);
    pour.getConfig().blocksAtFirstLevel = 5;
    pour.getConfig().cooldownMillis = 60_000L;

    pour();
    for (int x = 2; x <= 9; x++) {
      bench.world.set(x, 60, 3, "dirt");
    }
    pour();

    assertThat(bench.placed(COBBLE, row(60))).isZero();
    assertThat(bench.count(COBBLE)).isEqualTo(59);
    assertThat(pour.blocks(3)).isEqualTo(320);
  }

  @Test
  void placedGroundIsNeverDugSoNothingIsFarmed() {
    bench.signature("excavation", "architect", 3);
    tools();

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(any(BlockRef.class))).thenReturn(true);
      pour();
    }

    assertThat(bench.placed(DIRT, row(60))).isEqualTo(8);
    assertThat(bench.count(COBBLE)).isEqualTo(64);
    assertThat(bench.count(DIRT)).isZero();
  }

  private void tools() {
    bench.upgrade("excavation", Landshaper.class, 1);
    bench.upgrade("architect", MasterBuilder.class, 1);
    RecipeSpec recipe = MutationTools.find("tool-excavation-grading-stakes").recipes().getFirst();
    bench.player.inventory().setItem(0, ((RecipeSpec.Shapeless) recipe).result().copyWithAmount(1));
    bench.player.inventory().setItem(1, FakeItem.tool("iron_shovel", 250));
    ItemRef wand = ChalkWandItem.create(Tool.STRAIGHTEDGE);
    ChalkWandItem.writePlan(wand, new Plan(bench.world.uid(), List.of(new Point(2, 60, 3), new Point(9, 60, 3)), Plane.XZ));
    bench.player.inventory().setItem(2, wand);
    bench.player.inventory().setOffHand(new FakeItem(COBBLE, 64));
  }

  private void pour() {
    bench.adapt.post(new PlayerInteractEvent(bench.player, InteractAction.LEFT_CLICK_AIR, Hand.MAIN_HAND,
        bench.player.inventory().mainHand(), null, BlockFace.UP, null, UseResult.DENY, UseResult.DEFAULT));
    bench.advance(20);
  }

  private PlayerInteractEvent pourOnBlock() {
    PlayerInteractEvent click = bench.adapt.post(new PlayerInteractEvent(bench.player, InteractAction.LEFT_CLICK_BLOCK,
        Hand.MAIN_HAND, bench.player.inventory().mainHand(), bench.world.blockAt(0, 60, 3), BlockFace.UP, null,
        UseResult.DEFAULT, UseResult.DEFAULT));
    bench.advance(20);
    return click;
  }

  private List<BlockRef> row(int y) {
    List<BlockRef> blocks = new ArrayList<>();
    for (int x = 2; x <= 9; x++) {
      blocks.add(bench.world.blockAt(x, y, 3));
    }
    return blocks;
  }
}
