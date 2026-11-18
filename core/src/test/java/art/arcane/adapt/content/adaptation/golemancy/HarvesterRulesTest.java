package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemTestItems;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeItemTags;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HarvesterRulesTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000022b1");
  private static final NodeRecord OPEN = NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).build();
  private static final Predicate<BlockRef> OWNED = block -> true;

  private final GolemHarvesterCore.Config config = new GolemHarvesterCore.Config();
  private final HarvestCrops crops = HarvestCrops.parse(config.crops);
  private final CropField field = new CropField();

  @Test
  void levelValuesReadTheirArraysAndNeverDropBelowOne() {
    assertThat(List.of(1, 2, 3, 4, 5)).extracting(level -> HarvesterRules.actionIntervalTicks(config.actionIntervalTicksByLevel, level))
        .containsExactly(40, 30, 24, 18, 12);
    assertThat(List.of(1, 2, 3, 4, 5)).extracting(level -> HarvesterRules.cropsPerAction(config.cropsPerActionByLevel, level))
        .containsExactly(1, 1, 2, 3, 4);
    assertThat(HarvesterRules.actionIntervalTicks(config.actionIntervalTicksByLevel, 0)).isEqualTo(40);
    assertThat(HarvesterRules.actionIntervalTicks(config.actionIntervalTicksByLevel, 9)).isEqualTo(12);
    assertThat(HarvesterRules.actionIntervalTicks(List.of(-5), 1)).isEqualTo(1);
    assertThat(HarvesterRules.cropsPerAction(List.of(0), 1)).isEqualTo(1);
    assertThat(HarvesterRules.cropsPerAction(List.of(), 3)).isEqualTo(1);
  }

  @Test
  void eachLevelUnlocksItsPartOfTheField() {
    assertThat(flags(HarvesterRules.plan(config, crops, 1, OPEN, NodeItemTags.shared(), Set.of()))).containsExactly(false, false, false, false);
    assertThat(flags(HarvesterRules.plan(config, crops, 2, OPEN, NodeItemTags.shared(), Set.of()))).containsExactly(true, false, false, false);
    assertThat(flags(HarvesterRules.plan(config, crops, 3, OPEN, NodeItemTags.shared(), Set.of()))).containsExactly(true, true, false, false);
    assertThat(flags(HarvesterRules.plan(config, crops, 4, OPEN, NodeItemTags.shared(), Set.of()))).containsExactly(true, true, true, false);
    assertThat(flags(HarvesterRules.plan(config, crops, 5, OPEN, NodeItemTags.shared(), Set.of()))).containsExactly(true, true, true, true);
  }

  @Test
  void ageableCropsAreRipeAtTheirMaximumAgeAndYoungOnesNeedTending() {
    field.set(0, 64, 0, Id.minecraft("wheat"), CropField.ageable(7, 7));
    field.set(1, 64, 0, Id.minecraft("carrots"), CropField.ageable(3, 7));
    field.set(2, 64, 0, Id.minecraft("nether_wart"), CropField.ageable(1, 3));
    field.set(3, 64, 0, Id.minecraft("cocoa"), CropField.ageable(2, 2));

    assertThat(survey(0, 64, 0, 1)).isEqualTo(HarvesterRules.Spot.RIPE);
    assertThat(survey(3, 64, 0, 1)).isEqualTo(HarvesterRules.Spot.RIPE);
    assertThat(survey(1, 64, 0, 1)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(1, 64, 0, 3)).isEqualTo(HarvesterRules.Spot.TEND);
    assertThat(survey(2, 64, 0, 3)).isEqualTo(HarvesterRules.Spot.NONE);
  }

  @Test
  void growthBlacklistedCropsAreNeverTendedButStillHarvested() {
    field.set(0, 64, 0, Id.minecraft("wheat"), CropField.ageable(3, 7));
    field.set(1, 64, 0, Id.minecraft("wheat"), CropField.ageable(7, 7));
    field.set(2, 64, 0, Id.minecraft("torchflower_crop"), CropField.ageable(1, 1));
    field.set(3, 64, 0, Id.minecraft("carrots"), CropField.ageable(3, 7));
    HarvesterRules.Plan plan = HarvesterRules.plan(config, crops, 5, OPEN, NodeItemTags.shared(),
        Set.of(Id.minecraft("wheat"), Id.minecraft("torchflower_crop")));

    assertThat(HarvesterRules.survey(field.block(0, 64, 0), plan, OWNED)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(HarvesterRules.survey(field.block(1, 64, 0), plan, OWNED)).isEqualTo(HarvesterRules.Spot.RIPE);
    assertThat(HarvesterRules.survey(field.block(2, 64, 0), plan, OWNED)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(HarvesterRules.survey(field.block(3, 64, 0), plan, OWNED)).isEqualTo(HarvesterRules.Spot.TEND);
    assertThat(plan.criteria(5)).isNotEqualTo(plan(5).criteria(5));
    assertThat(plan(5).criteria(5)).isEqualTo(plan(5).criteria(5));
  }

  @Test
  void pitcherCropsAreHarvestedFromTheirLowerHalfOnly() {
    field.set(0, 64, 0, Id.minecraft("pitcher_crop"), CropField.pitcher(4, "lower"));
    field.set(0, 65, 0, Id.minecraft("pitcher_crop"), CropField.pitcher(4, "upper"));

    assertThat(survey(0, 64, 0, 1)).isEqualTo(HarvesterRules.Spot.RIPE);
    assertThat(survey(0, 65, 0, 5)).isEqualTo(HarvesterRules.Spot.NONE);
  }

  @Test
  void torchflowersAreRipeOnceGrownOnFarmlandAndTheirCropsNeedTending() {
    field.set(0, 63, 0, Id.minecraft("farmland")).set(0, 64, 0, Id.minecraft("torchflower"));
    field.set(1, 63, 0, Id.minecraft("grass_block")).set(1, 64, 0, Id.minecraft("torchflower"));
    field.set(2, 64, 0, Id.minecraft("torchflower_crop"), CropField.ageable(1, 1));

    assertThat(survey(0, 64, 0, 1)).isEqualTo(HarvesterRules.Spot.RIPE);
    assertThat(survey(1, 64, 0, 5)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(2, 64, 0, 1)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(2, 64, 0, 3)).isEqualTo(HarvesterRules.Spot.TEND);
  }

  @Test
  void berriesArePickedFromRipeBushesAndBerryVines() {
    field.set(0, 64, 0, Id.minecraft("sweet_berry_bush"), CropField.ageable(3, 3));
    field.set(1, 64, 0, Id.minecraft("sweet_berry_bush"), CropField.ageable(1, 3));
    field.set(2, 64, 0, Id.minecraft("cave_vines"), CropField.vines(true));
    field.set(3, 64, 0, Id.minecraft("cave_vines_plant"), CropField.vines(true));
    field.set(4, 64, 0, Id.minecraft("cave_vines_plant"), CropField.vines(false));

    assertThat(survey(0, 64, 0, 1)).isEqualTo(HarvesterRules.Spot.RIPE);
    assertThat(survey(1, 64, 0, 3)).isEqualTo(HarvesterRules.Spot.TEND);
    assertThat(survey(2, 64, 0, 1)).isEqualTo(HarvesterRules.Spot.RIPE);
    assertThat(survey(3, 64, 0, 1)).isEqualTo(HarvesterRules.Spot.RIPE);
    assertThat(survey(4, 64, 0, 5)).isEqualTo(HarvesterRules.Spot.NONE);
  }

  @Test
  void melonsAndPumpkinsNeedLevelTwoAndAStemAttachedToThem() {
    field.set(0, 64, 0, Id.minecraft("melon")).set(1, 64, 0, Id.minecraft("attached_melon_stem"), CropField.facing(BlockFace.WEST));
    field.set(0, 64, 5, Id.minecraft("pumpkin")).set(1, 64, 5, Id.minecraft("attached_pumpkin_stem"), CropField.facing(BlockFace.NORTH));
    field.set(0, 64, 9, Id.minecraft("pumpkin")).set(0, 64, 8, Id.minecraft("pumpkin_stem"), CropField.ageable(7, 7));
    field.set(0, 64, 12, Id.minecraft("melon")).set(0, 64, 11, Id.minecraft("attached_pumpkin_stem"), CropField.facing(BlockFace.SOUTH));

    assertThat(survey(0, 64, 0, 1)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(0, 64, 0, 2)).isEqualTo(HarvesterRules.Spot.RIPE);
    assertThat(survey(0, 64, 5, 5)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(0, 64, 9, 5)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(0, 64, 12, 5)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(1, 64, 0, 5)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(0, 64, 8, 5)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(HarvesterRules.survey(field.block(0, 64, 0), plan(5), block -> block.x() == 0)).isEqualTo(HarvesterRules.Spot.UNAVAILABLE);
  }

  @Test
  void columnCropsAreTakenFromTheBlockAboveTheirBaseAtLevelTwo() {
    for (int y = 64; y <= 66; y++) {
      field.set(0, y, 0, Id.minecraft("sugar_cane"));
      field.set(1, y, 0, Id.minecraft("cactus"));
    }
    for (int y = 64; y <= 70; y++) {
      field.set(2, y, 0, Id.minecraft("bamboo"));
    }

    assertThat(survey(0, 64, 0, 5)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(0, 65, 0, 1)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(0, 65, 0, 2)).isEqualTo(HarvesterRules.Spot.RIPE);
    assertThat(survey(0, 66, 0, 5)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(1, 65, 0, 2)).isEqualTo(HarvesterRules.Spot.RIPE);
    assertThat(survey(2, 65, 0, 2)).isEqualTo(HarvesterRules.Spot.RIPE);
    assertThat(HarvesterRules.column(field.block(2, 65, 0))).extracting(BlockRef::y).containsExactly(65, 66, 67, 68, 69, 70);
    assertThat(HarvesterRules.column(field.block(0, 65, 0))).extracting(BlockRef::y).containsExactly(65, 66);
  }

  @Test
  void cropsMissingFromTheConfiguredSetAreIgnored() {
    HarvestCrops wheatOnly = HarvestCrops.parse(List.of("WHEAT"));
    field.set(0, 64, 0, Id.minecraft("carrots"), CropField.ageable(7, 7));
    field.set(1, 64, 0, Id.minecraft("wheat"), CropField.ageable(7, 7));

    HarvesterRules.Plan plan = HarvesterRules.plan(config, wheatOnly, 5, OPEN, NodeItemTags.shared(), Set.of());

    assertThat(HarvesterRules.survey(field.block(0, 64, 0), plan, OWNED)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(HarvesterRules.survey(field.block(1, 64, 0), plan, OWNED)).isEqualTo(HarvesterRules.Spot.RIPE);
  }

  @Test
  void theNodeFilterPicksCropsByTheirBlockOrTheirSeed() {
    field.set(0, 64, 0, Id.minecraft("wheat"), CropField.ageable(7, 7));
    field.set(1, 64, 0, Id.minecraft("carrots"), CropField.ageable(7, 7));
    field.set(2, 64, 0, Id.minecraft("melon")).set(3, 64, 0, Id.minecraft("attached_melon_stem"), CropField.facing(BlockFace.WEST));
    field.set(4, 64, 0, Id.minecraft("sweet_berry_bush"), CropField.ageable(3, 3));
    field.set(5, 64, 0, Id.minecraft("cave_vines"), CropField.vines(true));
    NodeRecord seeds = filtered(true, Id.minecraft("wheat_seeds"), Id.minecraft("melon"), Id.minecraft("sweet_berries"));
    NodeRecord noCarrots = filtered(false, Id.minecraft("carrot"), Id.minecraft("glow_berries"));

    assertThat(survey(seeds, 0, 64, 0)).isEqualTo(HarvesterRules.Spot.RIPE);
    assertThat(survey(seeds, 1, 64, 0)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(seeds, 2, 64, 0)).isEqualTo(HarvesterRules.Spot.RIPE);
    assertThat(survey(noCarrots, 0, 64, 0)).isEqualTo(HarvesterRules.Spot.RIPE);
    assertThat(survey(noCarrots, 1, 64, 0)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(seeds, 4, 64, 0)).isEqualTo(HarvesterRules.Spot.RIPE);
    assertThat(survey(seeds, 5, 64, 0)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(noCarrots, 4, 64, 0)).isEqualTo(HarvesterRules.Spot.RIPE);
    assertThat(survey(noCarrots, 5, 64, 0)).isEqualTo(HarvesterRules.Spot.NONE);
  }

  @Test
  void bareDirtIsTilledAtLevelFour() {
    field.set(0, 63, 0, Id.minecraft("dirt"));
    field.set(1, 63, 0, Id.minecraft("dirt")).set(1, 64, 0, Id.minecraft("stone"));
    field.set(2, 63, 0, Id.minecraft("grass_block"));

    assertThat(survey(0, 63, 0, 3)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(0, 63, 0, 4)).isEqualTo(HarvesterRules.Spot.TILL);
    assertThat(survey(1, 63, 0, 5)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(2, 63, 0, 5)).isEqualTo(HarvesterRules.Spot.NONE);
  }

  @Test
  void dirtBesideAStemIsLeftForTheFruit() {
    field.set(0, 63, 0, Id.minecraft("dirt")).set(1, 64, 0, Id.minecraft("melon_stem"), CropField.ageable(7, 7));
    field.set(4, 63, 4, Id.minecraft("dirt")).set(4, 64, 5, Id.minecraft("attached_pumpkin_stem"), CropField.facing(BlockFace.NORTH));
    field.set(8, 63, 0, Id.minecraft("dirt"));

    assertThat(survey(0, 63, 0, 5)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(4, 63, 4, 5)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(8, 63, 0, 5)).isEqualTo(HarvesterRules.Spot.TILL);
    assertThat(HarvesterRules.survey(field.block(8, 63, 0), plan(5), block -> block.x() == 8)).isEqualTo(HarvesterRules.Spot.UNAVAILABLE);
  }

  @Test
  void emptyFarmlandIsPlantedAtLevelFourUnlessAStemNeedsItForFruit() {
    field.set(0, 63, 0, Id.minecraft("farmland"));
    field.set(1, 63, 0, Id.minecraft("farmland")).set(1, 64, 0, Id.minecraft("wheat"), CropField.ageable(0, 7));
    field.set(4, 63, 0, Id.minecraft("farmland")).set(5, 64, 0, Id.minecraft("melon_stem"), CropField.ageable(7, 7));
    field.set(4, 63, 4, Id.minecraft("farmland")).set(4, 64, 5, Id.minecraft("attached_pumpkin_stem"), CropField.facing(BlockFace.NORTH));

    assertThat(survey(0, 63, 0, 3)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(0, 63, 0, 4)).isEqualTo(HarvesterRules.Spot.PLANT);
    assertThat(survey(1, 63, 0, 5)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(4, 63, 0, 5)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(4, 63, 4, 5)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(HarvesterRules.survey(field.block(0, 63, 0), plan(5), block -> block.x() == 0)).isEqualTo(HarvesterRules.Spot.UNAVAILABLE);
  }

  @Test
  void plantingPrefersTheSeedOfANeighboringCropThenAnyCarriedSeedTheNodeWants() {
    field.set(0, 64, 1, Id.minecraft("carrots"), CropField.ageable(2, 7));
    List<ItemRef> carried = List.of(GolemTestItems.stack(Id.minecraft("melon_seeds"), 4), GolemTestItems.stack(Id.minecraft("wheat_seeds"), 3),
        GolemTestItems.stack(Id.minecraft("carrot"), 2));

    assertThat(HarvesterRules.seedToPlant(plan(4), carried, field.block(0, 64, 0), OWNED)).isEqualTo(Id.minecraft("carrot"));
    assertThat(HarvesterRules.seedToPlant(plan(4), carried, field.block(5, 64, 5), OWNED)).isEqualTo(Id.minecraft("wheat_seeds"));
    assertThat(HarvesterRules.seedToPlant(HarvesterRules.plan(config, crops, 4, filtered(false, Id.minecraft("wheat_seeds")), NodeItemTags.shared(), Set.of()),
        carried, field.block(5, 64, 5), OWNED)).isEqualTo(Id.minecraft("carrot"));
    assertThat(HarvesterRules.seedToPlant(plan(4), List.of(GolemTestItems.stack(Id.minecraft("melon_seeds"), 4)), field.block(5, 64, 5), OWNED)).isNull();
  }

  @Test
  void aVisibleAttachedStemProvesARipeGourdEvenWithOtherUnavailableNeighbours() {
    field.set(15, 64, 0, Id.minecraft("melon")).set(14, 64, 0, Id.minecraft("attached_melon_stem"), CropField.facing(BlockFace.EAST));
    when(field.world.isChunkLoaded(1, 0)).thenReturn(false);

    assertThat(survey(15, 64, 0, 5)).isEqualTo(HarvesterRules.Spot.RIPE);
  }

  @Test
  void neighboursInUnloadedChunksAreNeverReadAndLeaveTheSpotUnavailable() {
    field.set(15, 64, 0, Id.minecraft("melon")).set(16, 64, 0, Id.minecraft("attached_melon_stem"), CropField.facing(BlockFace.WEST));
    field.set(15, 63, 4, Id.minecraft("dirt"));
    field.set(15, 64, 8, Id.minecraft("air")).set(16, 64, 8, Id.minecraft("carrots"), CropField.ageable(2, 7));
    when(field.world.isChunkLoaded(1, 0)).thenReturn(false);
    List<ItemRef> carried = List.of(GolemTestItems.stack(Id.minecraft("wheat_seeds"), 3), GolemTestItems.stack(Id.minecraft("carrot"), 2));

    assertThat(survey(15, 64, 0, 5)).isEqualTo(HarvesterRules.Spot.UNAVAILABLE);
    assertThat(survey(15, 63, 4, 5)).isEqualTo(HarvesterRules.Spot.UNAVAILABLE);
    assertThat(HarvesterRules.seedToPlant(plan(4), carried, field.block(15, 64, 8), OWNED)).isEqualTo(Id.minecraft("wheat_seeds"));
    verify(field.block(16, 64, 0), never()).type();
    verify(field.block(16, 64, 4), never()).type();
    verify(field.block(16, 64, 8), never()).type();
  }

  @Test
  void replantingUsesEachCropsOwnSeed() {
    assertThat(HarvestCrops.seed(Id.minecraft("wheat"))).isEqualTo(Id.minecraft("wheat_seeds"));
    assertThat(HarvestCrops.seed(Id.minecraft("carrots"))).isEqualTo(Id.minecraft("carrot"));
    assertThat(HarvestCrops.seed(Id.minecraft("potatoes"))).isEqualTo(Id.minecraft("potato"));
    assertThat(HarvestCrops.seed(Id.minecraft("beetroots"))).isEqualTo(Id.minecraft("beetroot_seeds"));
    assertThat(HarvestCrops.seed(Id.minecraft("nether_wart"))).isEqualTo(Id.minecraft("nether_wart"));
    assertThat(HarvestCrops.seed(Id.minecraft("cocoa"))).isEqualTo(Id.minecraft("cocoa_beans"));
    assertThat(HarvestCrops.seed(Id.minecraft("pitcher_crop"))).isEqualTo(Id.minecraft("pitcher_pod"));
    assertThat(HarvestCrops.seed(Id.minecraft("torchflower"))).isEqualTo(Id.minecraft("torchflower_seeds"));
    assertThat(HarvestCrops.seed(Id.minecraft("melon"))).isNull();
  }

  @Test
  void theCropListMapsPlantFamiliesAndSkipsUnknownNames() {
    HarvestCrops parsed = HarvestCrops.parse(List.of("torchflower_crop", "CAVE_VINES", "MELON", "BAMBOO", "SWEET_BERRY_BUSH", "WHEAT", "NOT_A_BLOCK"));

    assertThat(parsed.kind(Id.minecraft("torchflower"))).isEqualTo(HarvestCrops.Kind.TORCHFLOWER);
    assertThat(parsed.kind(Id.minecraft("torchflower_crop"))).isEqualTo(HarvestCrops.Kind.TORCHFLOWER);
    assertThat(parsed.kind(Id.minecraft("cave_vines_plant"))).isEqualTo(HarvestCrops.Kind.CAVE_VINES);
    assertThat(parsed.kind(Id.minecraft("melon"))).isEqualTo(HarvestCrops.Kind.GOURD);
    assertThat(parsed.kind(Id.minecraft("bamboo"))).isEqualTo(HarvestCrops.Kind.COLUMN);
    assertThat(parsed.kind(Id.minecraft("sweet_berry_bush"))).isEqualTo(HarvestCrops.Kind.BERRY_BUSH);
    assertThat(parsed.kind(Id.minecraft("wheat"))).isEqualTo(HarvestCrops.Kind.CROP);
    assertThat(parsed.kind(Id.minecraft("carrots"))).isEqualTo(HarvestCrops.Kind.NONE);
    assertThat(parsed.farmlandCrop(Id.minecraft("wheat_seeds"))).isEqualTo(Id.minecraft("wheat"));
    assertThat(parsed.farmlandCrop(Id.minecraft("torchflower_seeds"))).isEqualTo(Id.minecraft("torchflower_crop"));
    assertThat(parsed.farmlandCrop(Id.minecraft("carrot"))).isNull();
    assertThat(HarvestCrops.parse(config.crops).kind(Id.minecraft("nether_wart"))).isEqualTo(HarvestCrops.Kind.CROP);
  }

  @Test
  void stemsInTheCropListAreIgnored() {
    HarvestCrops parsed = HarvestCrops.parse(List.of("MELON_STEM", "PUMPKIN_STEM", "ATTACHED_MELON_STEM", "ATTACHED_PUMPKIN_STEM", "MELON"));

    assertThat(parsed.kind(Id.minecraft("melon_stem"))).isEqualTo(HarvestCrops.Kind.NONE);
    assertThat(parsed.kind(Id.minecraft("pumpkin_stem"))).isEqualTo(HarvestCrops.Kind.NONE);
    assertThat(parsed.kind(Id.minecraft("attached_melon_stem"))).isEqualTo(HarvestCrops.Kind.NONE);
    assertThat(parsed.kind(Id.minecraft("attached_pumpkin_stem"))).isEqualTo(HarvestCrops.Kind.NONE);
    assertThat(parsed.kind(Id.minecraft("melon"))).isEqualTo(HarvestCrops.Kind.GOURD);
  }

  @Test
  void loadClampsKeepChancesFractionsXpPositiveAndTendingAtLeastOneSecond() {
    assertThat(GolemancyRules.fraction(1.5D)).isEqualTo(1D);
    assertThat(GolemancyRules.fraction(-0.5D)).isZero();
    assertThat(GolemancyRules.fraction(Double.NaN)).isZero();
    assertThat(GolemancyRules.nonNegative(-2D)).isZero();
    assertThat(GolemancyRules.nonNegative(Double.POSITIVE_INFINITY)).isZero();
    assertThat(GolemancyRules.nonNegative(0.4D)).isEqualTo(0.4D);
    assertThat(HarvesterRules.tendingInterval(10L)).isEqualTo(1_000L);
    assertThat(HarvesterRules.tendingInterval(10_000L)).isEqualTo(10_000L);
  }

  private HarvesterRules.Spot survey(int x, int y, int z, int level) {
    return HarvesterRules.survey(field.block(x, y, z), plan(level), OWNED);
  }

  private HarvesterRules.Spot survey(NodeRecord node, int x, int y, int z) {
    return HarvesterRules.survey(field.block(x, y, z), HarvesterRules.plan(config, crops, 5, node, NodeItemTags.shared(), Set.of()), OWNED);
  }

  private HarvesterRules.Plan plan(int level) {
    return HarvesterRules.plan(config, crops, level, OPEN, NodeItemTags.shared(), Set.of());
  }

  private static List<Boolean> flags(HarvesterRules.Plan plan) {
    return List.of(plan.extended(), plan.tending(), plan.tilling(), plan.bountiful());
  }

  private static NodeRecord filtered(boolean whitelist, Id... items) {
    List<ItemRef> entries = new ArrayList<>();
    for (Id item : items) {
      entries.add(GolemTestItems.stack(item, 1));
    }
    return NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).filterItems(entries).whitelist(whitelist).build();
  }
}
