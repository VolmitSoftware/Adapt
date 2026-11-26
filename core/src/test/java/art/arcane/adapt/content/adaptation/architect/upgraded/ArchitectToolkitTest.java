package art.arcane.adapt.content.adaptation.architect.upgraded;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.architect.ArchitectChalkFill;
import art.arcane.adapt.content.adaptation.architect.ArchitectPlacement;
import art.arcane.adapt.content.item.ChalkWandItem;
import art.arcane.adapt.content.item.ChalkWandItem.Plan;
import art.arcane.adapt.content.item.ChalkWandItem.Plane;
import art.arcane.adapt.content.item.ChalkWandItem.Point;
import art.arcane.adapt.content.item.ChalkWandItem.Tool;
import art.arcane.adapt.content.item.MasonsTemplate;
import art.arcane.adapt.content.skill.SkillArchitect;
import art.arcane.adapt.content.skill.SkillCrafting;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/** Chalk fills, the Mason's Template, undo and replace: real paid placements, inert without the upgrade, no way to farm them. */
class ArchitectToolkitTest {
  private static final Id STONE = Id.minecraft("stone");
  private static final Id OAK = Id.minecraft("oak_planks");
  private static final Id BRICKS = Id.minecraft("bricks");
  private static final int Y = 61;

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakeWorld world;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private SkillRegistry registry;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    world = platform.world();
    player = platform.player("Mason", 0.5D, Y, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillArchitect.class, SkillCrafting.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void aChalkFillLaysTheGuideAFewBlocksATickPaidFromTheOffHandThenTheInventory() {
    mutate();
    learn(MasterBuilder.class, 1);
    holdGuide(line(2, 9));
    player.inventory().setOffHand(new FakeItem(STONE, 5));
    player.inventory().setItem(20, new FakeItem(STONE, 10));

    PlayerInteractEvent click = fill();
    platform.scheduler().advance(1);
    int afterOneTick = placed(row(2, 9));
    platform.scheduler().advance(10);

    assertThat(click.useItem()).isEqualTo(UseResult.DENY);
    assertThat(afterOneTick).isBetween(1, 4);
    assertThat(placed(row(2, 9))).isEqualTo(8);
    assertThat(count(STONE)).isEqualTo(7);
    assertThat(player.inventory().offHand().isEmpty()).isTrue();
  }

  @Test
  void aChalkFillDoesNothingForAPlayerWithoutMasterBuilder() {
    learn(MasterBuilder.class, 3);
    holdGuide(line(2, 9));
    player.inventory().setOffHand(new FakeItem(STONE, 16));

    fill();
    platform.scheduler().advance(10);

    assertThat(placed(row(2, 9))).isZero();
    assertThat(count(STONE)).isEqualTo(16);
  }

  @Test
  void aChalkFillStopsAtAProtectedBlockAndKeepsTheItems() {
    mutate();
    learn(MasterBuilder.class, 1);
    holdGuide(line(2, 9));
    player.inventory().setOffHand(new FakeItem(STONE, 16));
    platform.protection().deny(true);

    fill();
    platform.scheduler().advance(10);

    assertThat(placed(row(2, 9))).isZero();
    assertThat(count(STONE)).isEqualTo(16);
  }

  @Test
  void aCreativePlayerGetsNoFreeFill() {
    mutate();
    learn(MasterBuilder.class, 1);
    player.setGameMode(GameMode.CREATIVE);
    holdGuide(line(2, 9));
    player.inventory().setOffHand(new FakeItem(STONE, 2));

    fill();
    platform.scheduler().advance(10);

    assertThat(placed(row(2, 9))).isZero();
    assertThat(count(STONE)).isEqualTo(2);
  }

  @Test
  void fillsPayNoSkillXpSoFillingAndUndoingFarmsNothing() {
    mutate();
    learn(MasterBuilder.class, 1);
    learn(WreckingHand.class, 1);
    double xp = profile.getData().getSkillLine("architect").getXp();
    for (int round = 0; round < 3; round++) {
      holdGuide(line(2, 9));
      player.inventory().setOffHand(new FakeItem(STONE, 8));
      fill();
      platform.scheduler().advance(10);
      player.inventory().setOffHand(FakeItem.empty());
      undo();
      platform.scheduler().advance(10);
    }

    assertThat(profile.getData().getSkillLine("architect").getXp()).isEqualTo(xp);
    assertThat(count(STONE)).isEqualTo(24);
    assertThat(placed(row(2, 9))).isZero();
  }

  @Test
  void aFourthFillInsideTheWindowIsRefused() {
    mutate();
    learn(MasterBuilder.class, 1);
    player.inventory().setOffHand(new FakeItem(STONE, 64));
    for (int z = 3; z <= 6; z++) {
      holdGuide(new Plan(world.uid(), List.of(new Point(2, Y, z), new Point(3, Y, z)), Plane.XZ));
      fill();
      platform.scheduler().advance(4);
    }

    assertThat(placed(List.of(world.blockAt(2, Y, 6), world.blockAt(3, Y, 6)))).isZero();
    assertThat(count(STONE)).isEqualTo(58);
  }

  @Test
  void aSolidDiscNeedsLevelTwoAndFillsTheCircle() {
    mutate();
    learn(MasterBuilder.class, 1);
    profile.getData().getPreferences().set(MasterBuilder.ID, "shape", ArchitectChalkFill.Shape.DISC.name());
    Plan circle = new Plan(world.uid(), List.of(new Point(10, Y, 10), new Point(12, Y, 10)), Plane.XZ);
    holdGuide(Tool.COMPASS, circle);
    player.inventory().setOffHand(new FakeItem(STONE, 64));

    fill();
    platform.scheduler().advance(20);
    boolean centerAtLevelOne = !world.blockAt(10, Y, 10).isAir();
    learn(MasterBuilder.class, 2);
    fill();
    platform.scheduler().advance(20);

    assertThat(centerAtLevelOne).isFalse();
    assertThat(world.blockAt(10, Y, 10).type()).isEqualTo(STONE);
    assertThat(world.blockAt(11, Y, 11).type()).isEqualTo(STONE);
  }

  @Test
  void undoReturnsWhatTheFillCostOnceAndNothingForABlockBrokenFirst() {
    mutate();
    learn(MasterBuilder.class, 1);
    learn(WreckingHand.class, 1);
    holdGuide(line(2, 9));
    player.inventory().setOffHand(new FakeItem(STONE, 8));
    fill();
    platform.scheduler().advance(10);
    world.set(2, Y, 3, "air");
    player.inventory().setOffHand(FakeItem.empty());

    undo();
    platform.scheduler().advance(10);
    int afterUndo = count(STONE);
    undo();
    platform.scheduler().advance(10);

    assertThat(afterUndo).isEqualTo(7);
    assertThat(count(STONE)).isEqualTo(7);
    assertThat(placed(row(2, 9))).isZero();
  }

  @Test
  void undoNeedsWreckingHand() {
    mutate();
    learn(MasterBuilder.class, 1);
    holdGuide(line(2, 9));
    player.inventory().setOffHand(new FakeItem(STONE, 8));
    fill();
    platform.scheduler().advance(10);
    player.inventory().setOffHand(FakeItem.empty());

    undo();
    platform.scheduler().advance(10);

    assertThat(placed(row(2, 9))).isEqualTo(8);
  }

  @Test
  void theTemplateCopiesTwoCornersThenPreviewsAndBuildsAPaidCopy() {
    mutate();
    learn(Skyhook.class, 1);
    world.set(20, Y, 0, "stone").set(21, Y, 0, "stone").set(20, Y + 1, 0, "oak_planks");
    ItemRef template = template().create(new MasonsTemplate.Data());
    player.holding(template);
    player.inventory().setItem(20, new FakeItem(STONE, 10));
    player.inventory().setItem(21, new FakeItem(OAK, 10));

    click(InteractAction.LEFT_CLICK_BLOCK, world.blockAt(20, Y, 0), BlockFace.UP);
    click(InteractAction.RIGHT_CLICK_BLOCK, world.blockAt(21, Y + 1, 0), BlockFace.UP);
    BlockRef ground = world.set(30, Y - 1, 0, "stone").blockAt(30, Y - 1, 0);
    click(InteractAction.RIGHT_CLICK_BLOCK, ground, BlockFace.UP);
    platform.scheduler().advance(5);
    boolean builtOnPreview = !world.blockAt(30, Y, 0).isAir() || !world.blockAt(29, Y, 0).isAir();
    click(InteractAction.RIGHT_CLICK_BLOCK, ground, BlockFace.UP);
    platform.scheduler().advance(5);

    assertThat(template().read(player.inventory().mainHand()).pattern.count()).isEqualTo(3);
    assertThat(builtOnPreview).isFalse();
    assertThat(world.blockAt(29, Y, 0).type()).isEqualTo(STONE);
    assertThat(world.blockAt(30, Y, 0).type()).isEqualTo(STONE);
    assertThat(world.blockAt(29, Y + 1, 0).type()).isEqualTo(OAK);
    assertThat(count(STONE)).isEqualTo(8);
    assertThat(count(OAK)).isEqualTo(9);
  }

  @Test
  void theTemplateIsInertWithoutSkyhook() {
    world.set(20, Y, 0, "stone");
    player.holding(template().create(new MasonsTemplate.Data()));

    click(InteractAction.LEFT_CLICK_BLOCK, world.blockAt(20, Y, 0), BlockFace.UP);
    click(InteractAction.RIGHT_CLICK_BLOCK, world.blockAt(20, Y, 0), BlockFace.UP);

    assertThat(template().read(player.inventory().mainHand()).hasPattern()).isFalse();
    assertThat(player.actionBars()).isNotEmpty();
  }

  @Test
  void aCopyNeverHoldsContentsOrADoubleSlabsSecondItem() {
    mutate();
    learn(Skyhook.class, 1);
    world.set(new BlockPos(20, Y, 0), new FakeBlockState(Id.minecraft("oak_slab"), Map.of("type", "double", "waterlogged", "true")));
    world.set(21, Y, 0, "spawner");
    player.holding(template().create(new MasonsTemplate.Data()));
    player.inventory().setItem(20, new FakeItem(Id.minecraft("oak_slab"), 4));

    click(InteractAction.LEFT_CLICK_BLOCK, world.blockAt(20, Y, 0), BlockFace.UP);
    click(InteractAction.RIGHT_CLICK_BLOCK, world.blockAt(21, Y, 0), BlockFace.UP);
    BlockRef ground = world.set(30, Y - 1, 5, "stone").blockAt(30, Y - 1, 5);
    click(InteractAction.RIGHT_CLICK_BLOCK, ground, BlockFace.UP);
    click(InteractAction.RIGHT_CLICK_BLOCK, ground, BlockFace.UP);
    platform.scheduler().advance(5);

    assertThat(template().read(player.inventory().mainHand()).pattern.count()).isEqualTo(1);
    BlockRef slab = world.blockAt(29, Y, 5);
    assertThat(slab.type()).isEqualTo(Id.minecraft("oak_slab"));
    assertThat(slab.state().property("type")).isEqualTo("bottom");
    assertThat(slab.state().property("waterlogged")).isEqualTo("false");
    assertThat(world.blockAt(30, Y, 5).isAir()).isTrue();
    assertThat(count(Id.minecraft("oak_slab"))).isEqualTo(3);
  }

  @Test
  void theTemplateNeverCopiesAContainer() {
    mutate();
    learn(Skyhook.class, 1);
    world.set(20, Y, 0, "chest").set(21, Y, 0, "stone");
    world.blockAt(20, Y, 0).container().addItem(new FakeItem(Id.minecraft("diamond"), 8));
    player.holding(template().create(new MasonsTemplate.Data()));

    click(InteractAction.LEFT_CLICK_BLOCK, world.blockAt(20, Y, 0), BlockFace.UP);
    click(InteractAction.RIGHT_CLICK_BLOCK, world.blockAt(21, Y, 0), BlockFace.UP);

    assertThat(template().read(player.inventory().mainHand()).pattern.count()).isEqualTo(1);
  }

  @Test
  void theReplaceBrushReachesOnlyTheBlocksAFillPlaced() {
    mutate();
    learn(MasterBuilder.class, 1);
    learn(Skyhook.class, 1);
    learn(WreckingHand.class, 1);
    holdGuide(line(2, 9));
    player.inventory().setOffHand(new FakeItem(STONE, 4));
    fill();
    platform.scheduler().advance(10);
    world.set(8, Y, 3, "stone");
    armReplaceBrush();

    click(InteractAction.LEFT_CLICK_BLOCK, world.blockAt(2, Y, 3), BlockFace.UP);
    platform.scheduler().advance(10);

    assertThat(platform.protection().broken()).hasSize(4);
    assertThat(world.blockAt(8, Y, 3).type()).isEqualTo(STONE);
  }

  @Test
  void theReplaceBrushNeverReachesWhatStandsWhereAnUndoneFillWas() {
    mutate();
    learn(MasterBuilder.class, 1);
    learn(Skyhook.class, 1);
    learn(WreckingHand.class, 1);
    holdGuide(line(2, 9));
    player.inventory().setOffHand(new FakeItem(STONE, 8));
    fill();
    platform.scheduler().advance(10);
    player.inventory().setOffHand(FakeItem.empty());
    undo();
    platform.scheduler().advance(10);
    for (BlockRef block : row(2, 9)) {
      world.set(block.x(), Y, 3, "stone");
    }
    armReplaceBrush();

    click(InteractAction.LEFT_CLICK_BLOCK, world.blockAt(2, Y, 3), BlockFace.UP);
    platform.scheduler().advance(10);

    assertThat(platform.protection().broken()).isEmpty();
    assertThat(count(BRICKS)).isEqualTo(8);
  }

  @Test
  void theReplaceBrushNeedsSkyhookForItsTemplate() {
    mutate();
    learn(MasterBuilder.class, 1);
    learn(WreckingHand.class, 1);
    holdGuide(line(2, 9));
    player.inventory().setOffHand(new FakeItem(STONE, 8));
    fill();
    platform.scheduler().advance(10);
    MasonsTemplate.Data brush = new MasonsTemplate.Data();
    brush.replace = true;
    player.inventory().setItem(3, template().create(brush));
    player.holding(FakeItem.tool("iron_pickaxe", 250));
    player.inventory().setOffHand(new FakeItem(BRICKS, 8));
    player.setSneaking(true);

    click(InteractAction.LEFT_CLICK_BLOCK, world.blockAt(4, Y, 3), BlockFace.UP);
    platform.scheduler().advance(10);

    assertThat(platform.protection().broken()).isEmpty();
    assertThat(count(BRICKS)).isEqualTo(8);
  }

  @Test
  void aLevelOneWallIsTallerThanTheOutline() {
    mutate();
    learn(MasterBuilder.class, 1);
    profile.getData().getPreferences().set(MasterBuilder.ID, "shape", ArchitectChalkFill.Shape.WALL.name());
    holdGuide(line(2, 5));
    player.inventory().setOffHand(new FakeItem(STONE, 64));

    fill();
    platform.scheduler().advance(10);

    assertThat(placed(row(2, 5))).isEqualTo(4);
    assertThat(placed(row(2, 5).stream().map((BlockRef block) -> block.relative(BlockFace.UP)).toList())).isEqualTo(4);
  }

  @Test
  void rotatingTheTemplateTurnsPositionsAndFacings() {
    mutate();
    learn(Skyhook.class, 1);
    world.set(new BlockPos(20, Y, 0), new FakeBlockState(Id.minecraft("oak_stairs"), Map.of("facing", "north")));
    world.set(21, Y, 0, "stone");
    player.holding(template().create(new MasonsTemplate.Data()));
    player.inventory().setItem(20, new FakeItem(Id.minecraft("oak_stairs"), 1));
    player.inventory().setItem(21, new FakeItem(STONE, 1));

    click(InteractAction.LEFT_CLICK_BLOCK, world.blockAt(20, Y, 0), BlockFace.UP);
    click(InteractAction.RIGHT_CLICK_BLOCK, world.blockAt(21, Y, 0), BlockFace.UP);
    click(InteractAction.LEFT_CLICK_AIR, null, null);
    BlockRef ground = world.set(40, Y - 1, 0, "stone").blockAt(40, Y - 1, 0);
    click(InteractAction.RIGHT_CLICK_BLOCK, ground, BlockFace.UP);
    click(InteractAction.RIGHT_CLICK_BLOCK, ground, BlockFace.UP);
    platform.scheduler().advance(5);

    BlockRef stairs = world.blockAt(40, Y, -1);
    assertThat(stairs.type()).isEqualTo(Id.minecraft("oak_stairs"));
    assertThat(stairs.state().property("facing")).isEqualTo("east");
    assertThat(world.blockAt(40, Y, 0).type()).isEqualTo(STONE);
  }

  @Test
  void theTemplateRepeatsAlongAChalkGuideAtItsSpacing() {
    mutate();
    learn(Skyhook.class, 1);
    world.set(20, Y, 0, "stone");
    ItemRef template = template().create(new MasonsTemplate.Data());
    player.holding(template);
    player.inventory().setItem(20, new FakeItem(STONE, 64));
    click(InteractAction.LEFT_CLICK_BLOCK, world.blockAt(20, Y, 0), BlockFace.UP);
    click(InteractAction.RIGHT_CLICK_BLOCK, world.blockAt(20, Y, 0), BlockFace.UP);
    ItemRef wand = ChalkWandItem.create(Tool.STRAIGHTEDGE);
    ChalkWandItem.writePlan(wand, new Plan(world.uid(), List.of(new Point(0, Y, 8), new Point(20, Y, 8)), Plane.XZ));
    player.inventory().setItem(1, wand);
    player.setSneaking(true);

    click(InteractAction.RIGHT_CLICK_BLOCK, world.blockAt(0, Y - 1, 8), BlockFace.UP);
    platform.scheduler().advance(10);

    assertThat(placed(row(0, 20, 8))).isEqualTo(4);
    assertThat(world.blockAt(0, Y, 8).type()).isEqualTo(STONE);
    assertThat(world.blockAt(2, Y, 8).type()).isEqualTo(STONE);
    assertThat(world.blockAt(6, Y, 8).type()).isEqualTo(STONE);
    assertThat(count(STONE)).isEqualTo(60);
  }

  @Test
  void anOverchargedRepeatSpendsTheCraftingFuelForHalfAgainTheStamps() {
    mutate();
    learn(Skyhook.class, 1);
    world.set(20, Y, 0, "stone");
    player.holding(template().create(new MasonsTemplate.Data()));
    player.inventory().setItem(20, new FakeItem(STONE, 64));
    player.inventory().setItem(22, new FakeItem(Id.minecraft("iron_nugget"), 9));
    click(InteractAction.LEFT_CLICK_BLOCK, world.blockAt(20, Y, 0), BlockFace.UP);
    click(InteractAction.RIGHT_CLICK_BLOCK, world.blockAt(20, Y, 0), BlockFace.UP);
    ItemRef wand = ChalkWandItem.create(Tool.STRAIGHTEDGE);
    ChalkWandItem.writePlan(wand, new Plan(world.uid(), List.of(new Point(0, Y, 8), new Point(20, Y, 8)), Plane.XZ));
    player.inventory().setItem(1, wand);
    player.setSneaking(true);

    click(InteractAction.RIGHT_CLICK_BLOCK, world.blockAt(0, Y - 1, 8), BlockFace.UP);
    platform.scheduler().advance(10);

    assertThat(placed(row(0, 20, 8))).isEqualTo(6);
    assertThat(count(Id.minecraft("iron_nugget"))).isLessThan(9);
  }

  @Test
  void theReplaceBrushSwapsAFillForTheOffHandBlockWithRealBreaksAndPaidPlacements() {
    mutate();
    learn(MasterBuilder.class, 1);
    learn(Skyhook.class, 1);
    learn(WreckingHand.class, 1);
    holdGuide(line(2, 9));
    player.inventory().setOffHand(new FakeItem(STONE, 8));
    fill();
    platform.scheduler().advance(10);
    player.setSneaking(false);
    MasonsTemplate.Data brush = new MasonsTemplate.Data();
    ItemRef template = template().create(brush);
    player.holding(template);
    adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().offHand(), template));
    boolean replacing = template().read(player.inventory().mainHand()).replace;
    player.inventory().setItem(3, player.inventory().mainHand());
    player.holding(FakeItem.tool("iron_pickaxe", 250));
    player.inventory().setOffHand(new FakeItem(BRICKS, 8));
    player.setSneaking(true);

    click(InteractAction.LEFT_CLICK_BLOCK, world.blockAt(4, Y, 3), BlockFace.UP);
    platform.scheduler().advance(10);

    assertThat(replacing).isTrue();
    assertThat(platform.protection().broken()).hasSize(8);
    for (BlockRef block : row(2, 9)) {
      assertThat(block.type()).isEqualTo(BRICKS);
    }
    assertThat(count(BRICKS)).isZero();
  }

  @Test
  void theReplaceBrushNeedsWreckingHandAndATemplateSetToReplace() {
    mutate();
    learn(MasterBuilder.class, 1);
    learn(Skyhook.class, 1);
    holdGuide(line(2, 9));
    player.inventory().setOffHand(new FakeItem(STONE, 8));
    fill();
    platform.scheduler().advance(10);
    ItemRef template = template().create(new MasonsTemplate.Data());
    player.setSneaking(false);
    player.holding(template);
    adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().offHand(), template));
    MasonsTemplate.Data held = template().read(player.inventory().mainHand());
    held.replace = true;
    ItemRef forced = template().create(held);
    player.inventory().setItem(3, forced);
    player.holding(FakeItem.tool("iron_pickaxe", 250));
    player.inventory().setOffHand(new FakeItem(BRICKS, 8));
    player.setSneaking(true);

    click(InteractAction.LEFT_CLICK_BLOCK, world.blockAt(4, Y, 3), BlockFace.UP);
    platform.scheduler().advance(10);

    assertThat(template().read(template).replace).isFalse();
    assertThat(platform.protection().broken()).isEmpty();
    assertThat(count(BRICKS)).isEqualTo(8);
  }

  @Test
  void aLentWandFaceIsOneUndoableBatchOfRealPlacements() {
    mutate();
    learn(MasterBuilder.class, 1);
    learn(WreckingHand.class, 1);
    player.setSneaking(true);
    for (int x = -1; x <= 1; x++) {
      for (int z = -1; z <= 1; z++) {
        world.set(x, 44, z, "stone");
      }
    }
    BlockRef source = world.blockAt(0, 44, 0);
    ((ArchitectPlacement) registry.getAdaptation(ArchitectPlacement.ID)).runPlayerViewport(BlockFace.UP, source, STONE, player);
    BlockRef placed = world.set(0, 45, 0, "stone").blockAt(0, 45, 0);
    player.holding(new FakeItem(STONE, 9));

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class)) {
      adapt.post(new BlockPlaceEvent(player, placed, FakeBlockState.of("air"), source, player.inventory().mainHand(),
          Hand.MAIN_HAND));
      platform.scheduler().advance(3);
      provenance.verify(() -> XpProvenance.recordPlacement(any(BlockRef.class)), org.mockito.Mockito.times(9));
    }
    int face = placed(layer(45));
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_AIR, Hand.MAIN_HAND, player.inventory().mainHand(),
        null, BlockFace.UP, null, UseResult.DEFAULT, UseResult.DEFAULT));
    platform.scheduler().advance(3);

    assertThat(face).isEqualTo(9);
    assertThat(placed(layer(45))).isEqualTo(1);
    assertThat(count(STONE)).isEqualTo(9);
  }

  private void armReplaceBrush() {
    MasonsTemplate.Data brush = new MasonsTemplate.Data();
    brush.replace = true;
    player.inventory().setItem(3, template().create(brush));
    player.holding(FakeItem.tool("iron_pickaxe", 250));
    player.inventory().setOffHand(new FakeItem(BRICKS, 8));
    player.setSneaking(true);
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("architect", "crafting")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("architect"), registry.getSkill("crafting"), "Mason"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = registry.getSkill("architect").getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
    profile.getData().getSkillLine("architect").setAdaptation(upgrade, level);
    return upgrade;
  }

  private MasonsTemplate template() {
    return MasonsTemplate.find();
  }

  private Plan line(int fromX, int toX) {
    return new Plan(world.uid(), List.of(new Point(fromX, Y, 3), new Point(toX, Y, 3)), Plane.XZ);
  }

  private void holdGuide(Plan plan) {
    holdGuide(Tool.STRAIGHTEDGE, plan);
  }

  private void holdGuide(Tool tool, Plan plan) {
    ItemRef wand = ChalkWandItem.create(tool);
    ChalkWandItem.writePlan(wand, plan);
    player.holding(wand);
    player.setSneaking(true);
  }

  private PlayerInteractEvent fill() {
    return adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_AIR, Hand.MAIN_HAND,
        player.inventory().mainHand(), null, BlockFace.UP, null, UseResult.DENY, UseResult.DEFAULT));
  }

  private void undo() {
    player.setSneaking(true);
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_AIR, Hand.MAIN_HAND,
        player.inventory().mainHand(), null, BlockFace.UP, null, UseResult.DENY, UseResult.DEFAULT));
  }

  private void click(InteractAction action, BlockRef block, BlockFace face) {
    UseResult blockUse = block == null ? UseResult.DENY : UseResult.DEFAULT;
    adapt.post(new PlayerInteractEvent(player, action, Hand.MAIN_HAND, player.inventory().mainHand(), block, face, null,
        blockUse, UseResult.DEFAULT));
  }

  private List<BlockRef> row(int fromX, int toX) {
    return row(fromX, toX, 3);
  }

  private List<BlockRef> row(int fromX, int toX, int z) {
    List<BlockRef> blocks = new java.util.ArrayList<>();
    for (int x = fromX; x <= toX; x++) {
      blocks.add(world.blockAt(x, Y, z));
    }
    return blocks;
  }

  private List<BlockRef> layer(int y) {
    List<BlockRef> blocks = new java.util.ArrayList<>();
    for (int x = -1; x <= 1; x++) {
      for (int z = -1; z <= 1; z++) {
        blocks.add(world.blockAt(x, y, z));
      }
    }
    return blocks;
  }

  private static int placed(List<BlockRef> blocks) {
    int placed = 0;
    for (BlockRef block : blocks) {
      placed += block.isAir() ? 0 : 1;
    }
    return placed;
  }

  private int count(Id type) {
    int count = 0;
    for (ItemRef item : player.inventory().contents()) {
      if (item != null && !item.isEmpty() && item.type().equals(type)) {
        count += item.amount();
      }
    }
    ItemRef off = player.inventory().offHand();
    if (off != null && !off.isEmpty() && off.type().equals(type) && !player.inventory().contents().contains(off)) {
      count += off.amount();
    }
    return count;
  }
}
