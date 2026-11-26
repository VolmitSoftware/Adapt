package art.arcane.adapt.content.adaptation.herbalism.upgraded;

import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockBurnEvent;
import art.arcane.adapt.platform.event.block.BlockDispenseEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.block.BlockPistonEvent;
import art.arcane.adapt.platform.event.entity.EntityChangeBlockEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeInventory;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeItemEntity;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.testkit.XpPaid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/** The Scarecrow tends a square of naturally ripened crops into a chest and never pays for forced growth. */
class HarvestMoonScarecrowTest {
  private static final Id WHEAT = Id.minecraft("wheat");
  private static final Id HAY = Id.minecraft("hay_block");
  private static final BlockPos POST = new BlockPos(4, 61, 4);
  private static final BlockPos CHEST = new BlockPos(5, 61, 4);

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private SkillRegistry registry;
  private FakeInventory chest;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Warden", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillHerbalism.class, SkillStealth.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    platform.world().set(CHEST.x(), CHEST.y(), CHEST.z(), "chest");
    chest = new FakeInventory();
    platform.world().blockInventory(CHEST, chest);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void theScarecrowIsAToolOfHarvestMoonMadeFromHayAPumpkinSticksAndATunic() {
    HarvestMoon moon = moon();

    assertThat(moon.getTools()).extracting(MutationTool::id).containsExactly("tool-herbalism-scarecrow");
    assertThat(moon.getRecipes()).anySatisfy((RecipeSpec recipe) ->
        assertThat(recipe.key()).isEqualTo(Id.adapt("tool-herbalism-scarecrow")));
  }

  @Test
  void aPlacedScarecrowHarvestsRipeNaturalCropsIntoTheChestAndPaysNoXp() {
    HarvestMoon moon = holder(1);
    place();
    crop(4, 6, 7);
    crop(2, 3, 7);
    crop(3, 5, 3);
    crop(12, 12, 7);

    tend(moon);

    assertThat(block(4, 6).type().isAir()).isTrue();
    assertThat(block(2, 3).type().isAir()).isTrue();
    assertThat(block(3, 5).state().age()).isEqualTo(3);
    assertThat(block(12, 12).type()).isEqualTo(WHEAT);
    assertThat(chest.contains(WHEAT, 2)).isTrue();
    assertThat(platform.world().items()).isEmpty();
    assertThat(XpPaid.by(profile, moon)).isZero();
  }

  @Test
  void aWideSquareIsWalkedOverSeveralPassesUntilItsLastCornerIsReached() {
    HarvestMoon moon = holder(3);
    place();
    crop(12, 12, 7);

    int passes = 0;
    while (block(12, 12).type().equals(WHEAT) && passes < 10) {
      tend(moon);
      passes++;
    }

    assertThat(passes).isEqualTo(4);
    assertThat(chest.contains(WHEAT, 1)).isTrue();
  }

  @Test
  void cropsGrownWithBoneMealOrByAdaptAreLeftStanding() {
    HarvestMoon moon = holder(1);
    place();
    crop(4, 6, 7);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isBonemealed(any(BlockRef.class))).thenReturn(true);
      tend(moon);
    }

    assertThat(block(4, 6).type()).isEqualTo(WHEAT);
    assertThat(chest.contents()).allMatch(ItemRef::isEmpty);
  }

  @Test
  void withoutAChestBesideItTheScarecrowHarvestsNothing() {
    HarvestMoon moon = holder(1);
    platform.world().set(CHEST.x(), CHEST.y(), CHEST.z(), "air");
    place();
    crop(4, 6, 7);

    tend(moon);

    assertThat(block(4, 6).type()).isEqualTo(WHEAT);
  }

  @Test
  void anOwnerFartherThanItsRangeLeavesTheFieldAlone() {
    HarvestMoon moon = holder(1);
    place();
    crop(4, 6, 7);
    player.moveTo(new Loc(platform.world(), 200D, 61D, 200D));

    tend(moon);

    assertThat(block(4, 6).type()).isEqualTo(WHEAT);
  }

  @Test
  void placingInProtectedLandIsRefusedAndKeepsTheTool() {
    holder(1);
    platform.protection().deny(true);
    player.holding(tool());

    click(InteractAction.RIGHT_CLICK_BLOCK, platform.world().blockAt(POST.x(), 60, POST.z()));
    platform.scheduler().advance(2);

    assertThat(block(POST.x(), POST.z()).type()).isNotEqualTo(HAY);
    assertThat(isScarecrow(player.inventory().mainHand())).isTrue();
    assertThat(moon().scarecrows().of(player.uuid())).isEmpty();
  }

  @Test
  void aSecondScarecrowPastTheLevelCapIsRefused() {
    holder(1);
    place();
    player.holding(tool());

    click(InteractAction.RIGHT_CLICK_BLOCK, platform.world().blockAt(0, 60, 8));
    platform.scheduler().advance(2);

    assertThat(block(0, 8).type()).isNotEqualTo(HAY);
    assertThat(isScarecrow(player.inventory().mainHand())).isTrue();
    assertThat(moon().scarecrows().of(player.uuid())).hasSize(1);
  }

  @Test
  void aPlayerWhoHasNotMutatedGetsNothingFromTheScarecrow() {
    HarvestMoon moon = moon();
    profile.getData().getSkillLine("herbalism").setAdaptation(moon, 3);
    player.holding(tool());

    click(InteractAction.RIGHT_CLICK_BLOCK, platform.world().blockAt(POST.x(), 60, POST.z()));
    platform.scheduler().advance(2);

    assertThat(block(POST.x(), POST.z()).type()).isNotEqualTo(HAY);
    assertThat(isScarecrow(player.inventory().mainHand())).isTrue();
  }

  @Test
  void breakingAScarecrowGivesBackOnlyTheWornToolAndNeverTwice() {
    HarvestMoon moon = holder(1);
    place();
    crop(4, 6, 7);
    tend(moon);
    BlockRef post = block(POST.x(), POST.z());

    BlockBreakEvent first = adapt.post(new BlockBreakEvent(player, post, true, 0));
    BlockBreakEvent again = adapt.post(new BlockBreakEvent(player, post, true, 0));

    assertThat(first.dropItems()).isFalse();
    assertThat(again.dropItems()).isTrue();
    List<FakeItemEntity> drops = platform.world().items();
    assertThat(drops).hasSize(1);
    ItemRef returned = drops.getFirst().item();
    assertThat(isScarecrow(returned)).isTrue();
    assertThat(returned.lore()).anyMatch((String line) -> line.contains("1999/2000"));
    assertThat(moon.scarecrows().of(player.uuid())).isEmpty();
  }

  @Test
  void aPlainHayBaleOnAFormerScarecrowSpotStaysAPlainHayBale() {
    HarvestMoon moon = holder(1);
    place();
    BlockRef post = block(POST.x(), POST.z());
    adapt.post(new BlockBreakEvent(player, post, true, 0));
    platform.world().items().clear();
    platform.world().set(POST.x(), POST.y(), POST.z(), "hay_block");

    BlockBreakEvent plain = adapt.post(new BlockBreakEvent(player, post, true, 0));

    assertThat(plain.dropItems()).isTrue();
    assertThat(moon.scarecrows().guards(post)).isFalse();
  }

  @Test
  void aWornScarecrowStopsUntilItIsRepairedWithHay() {
    HarvestMoon moon = holder(1);
    moon.getConfig().scarecrowWearLimit = 1;
    moon.getConfig().scarecrowRepairPerHay = 1;
    place();
    crop(4, 6, 7);
    crop(4, 7, 7);

    tend(moon);
    tend(moon);
    int standing = (block(4, 6).type().equals(WHEAT) ? 1 : 0) + (block(4, 7).type().equals(WHEAT) ? 1 : 0);
    player.holding(new FakeItem(HAY, 2));
    click(InteractAction.RIGHT_CLICK_BLOCK, block(POST.x(), POST.z()));
    tend(moon);

    assertThat(standing).isEqualTo(1);
    assertThat(player.inventory().mainHand().amount()).isEqualTo(1);
    assertThat(block(4, 6).type().isAir() && block(4, 7).type().isAir()).isTrue();
  }

  @Test
  void mobsCannotTrampleFarmlandInTheSquareButCanOutsideIt() {
    holder(1);
    place();
    platform.world().set(3, 60, 3, "farmland");
    platform.world().set(20, 60, 20, "farmland");
    FakeLiving zombie = MutationKit.zombie(platform.world(), 3.5D, 61D, 3.5D);

    EntityChangeBlockEvent inside = adapt.post(new EntityChangeBlockEvent(zombie, platform.world().blockAt(3, 60, 3),
        FakeBlockState.of("dirt")));
    EntityChangeBlockEvent outside = adapt.post(new EntityChangeBlockEvent(zombie,
        platform.world().blockAt(20, 60, 20), FakeBlockState.of("dirt")));

    assertThat(inside.isCancelled()).isTrue();
    assertThat(outside.isCancelled()).isFalse();
  }

  @Test
  void fireDoesNotBurnAScarecrow() {
    holder(1);
    place();

    BlockBurnEvent burn = adapt.post(new BlockBurnEvent(block(POST.x(), POST.z()), block(POST.x(), POST.z())));

    assertThat(burn.isCancelled()).isTrue();
  }

  @Test
  void aPistonCannotPushAScarecrow() {
    holder(1);
    place();
    BlockRef post = block(POST.x(), POST.z());

    BlockPistonEvent push = adapt.post(new BlockPistonEvent(post.relative(BlockFace.WEST), BlockFace.EAST, true, List.of(post)));

    assertThat(push.isCancelled()).isTrue();
  }

  @Test
  void anOfflineOwnersScarecrowIsReadFromDiskWithoutStayingInMemory() throws IOException {
    HarvestMoon moon = holder(1);
    place();
    BlockRef post = block(POST.x(), POST.z());
    moon.scarecrows().forget(player.uuid());

    assertThat(moon.scarecrows().guards(post)).isTrue();
    Files.delete(AdaptEnvironment.dataFile("data", "scarecrows", player.uuid() + ".json").toPath());

    assertThat(moon.scarecrows().guards(post)).isFalse();
  }

  @Test
  void scarecrowsAreKeptOnDiskAcrossALeave() {
    HarvestMoon moon = holder(1);
    place();

    moon.scarecrows().forget(player.uuid());

    assertThat(moon.scarecrows().of(player.uuid())).hasSize(1);
  }

  @Test
  void breakingAScarecrowWhoseBlockDataTheServerAlreadyDroppedStillGivesBackTheToolOnce() {
    HarvestMoon moon = holder(1);
    place();
    BlockRef post = block(POST.x(), POST.z());
    post.data().remove(Id.adapt("scarecrow_owner"));

    BlockBreakEvent first = adapt.post(new BlockBreakEvent(player, post, true, 0));
    BlockBreakEvent again = adapt.post(new BlockBreakEvent(player, post, true, 0));

    assertThat(first.dropItems()).isFalse();
    assertThat(again.dropItems()).isTrue();
    assertThat(platform.world().items()).hasSize(1);
    assertThat(isScarecrow(platform.world().items().getFirst().item())).isTrue();
    assertThat(moon.scarecrows().of(player.uuid())).isEmpty();
  }

  @Test
  void aProtectionProbeOfTheBreakNeitherDropsTheToolNorForgetsTheScarecrow() {
    HarvestMoon moon = holder(1);
    place();
    BlockRef post = block(POST.x(), POST.z());
    BlockBreakEvent probe = new BlockBreakEvent(player, post, true, 0);
    probe.markProtectionProbe();

    adapt.post(probe);
    BlockBreakEvent real = adapt.post(new BlockBreakEvent(player, post, true, 0));

    assertThat(real.dropItems()).isFalse();
    assertThat(platform.world().items()).hasSize(1);
    assertThat(isScarecrow(platform.world().items().getFirst().item())).isTrue();
    assertThat(moon.scarecrows().of(player.uuid())).isEmpty();
  }

  @Test
  void clickingFiveTimesWithOneScarecrowStandsOneAndLeavesNoCopy() {
    holder(3);
    player.holding(tool());

    for (int x = 0; x < 5; x++) {
      click(InteractAction.RIGHT_CLICK_BLOCK, platform.world().blockAt(x * 2, 60, POST.z()));
    }
    platform.scheduler().advance(4);

    int standing = 0;
    for (int x = 0; x < 5; x++) {
      standing += block(x * 2, POST.z()).type().equals(HAY) ? 1 : 0;
    }
    int held = isScarecrow(player.inventory().mainHand()) ? player.inventory().mainHand().amount() : 0;
    assertThat(standing + held).isEqualTo(1);
    assertThat(standing).isEqualTo(1);
    assertThat(moon().scarecrows().of(player.uuid())).hasSize(1);
  }

  @Test
  void theOffHandAVanillaPlacementAndADispenserNeverStandOrSpendAScarecrow() {
    holder(1);
    ItemRef held = tool();
    player.inventory().setOffHand(held);
    BlockRef ground = platform.world().blockAt(POST.x(), 60, POST.z());

    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.OFF_HAND, held, ground, BlockFace.UP,
        ground.center().position(), UseResult.DEFAULT, UseResult.DEFAULT));
    platform.scheduler().advance(2);
    BlockPlaceEvent vanilla = adapt.post(new BlockPlaceEvent(player, ground.relative(BlockFace.UP),
        ground.relative(BlockFace.UP).state(), ground, held, Hand.MAIN_HAND));
    BlockPlaceEvent offHand = adapt.post(new BlockPlaceEvent(player, ground.relative(BlockFace.UP),
        ground.relative(BlockFace.UP).state(), ground, held, Hand.OFF_HAND));
    platform.world().set(0, 61, 0, "dispenser");
    BlockDispenseEvent dispensed = adapt.post(new BlockDispenseEvent(platform.world().blockAt(0, 61, 0), held, Vec3.ZERO));

    assertThat(block(POST.x(), POST.z()).type()).isNotEqualTo(HAY);
    assertThat(isScarecrow(player.inventory().offHand())).isTrue();
    assertThat(vanilla.isCancelled()).isTrue();
    assertThat(offHand.isCancelled()).isTrue();
    assertThat(dispensed.isCancelled()).isTrue();
    assertThat(moon().scarecrows().of(player.uuid())).isEmpty();
  }

  private HarvestMoon holder(int level) {
    mutate();
    HarvestMoon moon = moon();
    profile.getData().getSkillLine("herbalism").setAdaptation(moon, level);
    return moon;
  }

  private void place() {
    player.holding(tool());
    click(InteractAction.RIGHT_CLICK_BLOCK, platform.world().blockAt(POST.x(), 60, POST.z()));
    platform.scheduler().advance(2);
    assertThat(block(POST.x(), POST.z()).type()).isEqualTo(HAY);
    assertThat(player.inventory().mainHand().isEmpty()).isTrue();
  }

  private void tend(HarvestMoon moon) {
    moon.onTick();
    platform.scheduler().advance(1);
  }

  private ItemRef tool() {
    return scarecrow().worn(0);
  }

  private Scarecrow scarecrow() {
    return (Scarecrow) moon().getTools().getFirst();
  }

  private boolean isScarecrow(ItemRef item) {
    return scarecrow().is(item);
  }

  private HarvestMoon moon() {
    return registry.getSkill("herbalism").getUpgrades().stream().filter(HarvestMoon.class::isInstance)
        .map(HarvestMoon.class::cast).findFirst().orElseThrow();
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("herbalism", "stealth")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("herbalism"), registry.getSkill("stealth"),
        "Warden")).isEqualTo(SkillMutations.Result.MUTATED);
  }

  private BlockRef block(int x, int z) {
    return platform.world().blockAt(x, 61, z);
  }

  private void crop(int x, int z, int age) {
    platform.world().set(new BlockPos(x, 61, z), FakeBlockState.of("wheat").with("age", Integer.toString(age)));
  }

  private void click(InteractAction action, BlockRef block) {
    adapt.post(new PlayerInteractEvent(player, action, Hand.MAIN_HAND, player.inventory().mainHand(), block,
        BlockFace.UP, block.center().position(), UseResult.DEFAULT, UseResult.DEFAULT));
  }
}
