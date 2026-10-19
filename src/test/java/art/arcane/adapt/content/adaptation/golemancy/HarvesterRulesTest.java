package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemTestItems;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeItemTags;
import art.arcane.adapt.api.golem.node.NodeRecord;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class HarvesterRulesTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000022b1");
  private static final NodeRecord OPEN = NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).build();
  private static final Predicate<Block> OWNED = block -> true;

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
    assertThat(flags(HarvesterRules.plan(config, crops, 1, OPEN, NodeItemTags.shared()))).containsExactly(false, false, false, false);
    assertThat(flags(HarvesterRules.plan(config, crops, 2, OPEN, NodeItemTags.shared()))).containsExactly(true, false, false, false);
    assertThat(flags(HarvesterRules.plan(config, crops, 3, OPEN, NodeItemTags.shared()))).containsExactly(true, true, false, false);
    assertThat(flags(HarvesterRules.plan(config, crops, 4, OPEN, NodeItemTags.shared()))).containsExactly(true, true, true, false);
    assertThat(flags(HarvesterRules.plan(config, crops, 5, OPEN, NodeItemTags.shared()))).containsExactly(true, true, true, true);
  }

  @Test
  void ageableCropsAreRipeAtTheirMaximumAgeAndYoungOnesNeedTending() {
    field.set(0, 64, 0, Material.WHEAT, CropField.ageable(7, 7));
    field.set(1, 64, 0, Material.CARROTS, CropField.ageable(3, 7));
    field.set(2, 64, 0, Material.NETHER_WART, CropField.ageable(1, 3));
    field.set(3, 64, 0, Material.COCOA, CropField.ageable(2, 2));

    assertThat(survey(0, 64, 0, 1)).isEqualTo(HarvesterRules.Spot.RIPE);
    assertThat(survey(3, 64, 0, 1)).isEqualTo(HarvesterRules.Spot.RIPE);
    assertThat(survey(1, 64, 0, 1)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(1, 64, 0, 3)).isEqualTo(HarvesterRules.Spot.TEND);
    assertThat(survey(2, 64, 0, 3)).isEqualTo(HarvesterRules.Spot.NONE);
  }

  @Test
  void pitcherCropsAreHarvestedFromTheirLowerHalfOnly() {
    field.set(0, 64, 0, Material.PITCHER_CROP, CropField.pitcher(4, Bisected.Half.BOTTOM));
    field.set(0, 65, 0, Material.PITCHER_CROP, CropField.pitcher(4, Bisected.Half.TOP));

    assertThat(survey(0, 64, 0, 1)).isEqualTo(HarvesterRules.Spot.RIPE);
    assertThat(survey(0, 65, 0, 5)).isEqualTo(HarvesterRules.Spot.NONE);
  }

  @Test
  void torchflowersAreRipeOnceGrownOnFarmlandAndTheirCropsNeedTending() {
    field.set(0, 63, 0, Material.FARMLAND).set(0, 64, 0, Material.TORCHFLOWER);
    field.set(1, 63, 0, Material.GRASS_BLOCK).set(1, 64, 0, Material.TORCHFLOWER);
    field.set(2, 64, 0, Material.TORCHFLOWER_CROP, CropField.ageable(1, 1));

    assertThat(survey(0, 64, 0, 1)).isEqualTo(HarvesterRules.Spot.RIPE);
    assertThat(survey(1, 64, 0, 5)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(2, 64, 0, 1)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(2, 64, 0, 3)).isEqualTo(HarvesterRules.Spot.TEND);
  }

  @Test
  void berriesArePickedFromRipeBushesAndBerryVines() {
    field.set(0, 64, 0, Material.SWEET_BERRY_BUSH, CropField.ageable(3, 3));
    field.set(1, 64, 0, Material.SWEET_BERRY_BUSH, CropField.ageable(1, 3));
    field.set(2, 64, 0, Material.CAVE_VINES, CropField.vines(true));
    field.set(3, 64, 0, Material.CAVE_VINES_PLANT, CropField.vines(true));
    field.set(4, 64, 0, Material.CAVE_VINES_PLANT, CropField.vines(false));

    assertThat(survey(0, 64, 0, 1)).isEqualTo(HarvesterRules.Spot.RIPE);
    assertThat(survey(1, 64, 0, 3)).isEqualTo(HarvesterRules.Spot.TEND);
    assertThat(survey(2, 64, 0, 1)).isEqualTo(HarvesterRules.Spot.RIPE);
    assertThat(survey(3, 64, 0, 1)).isEqualTo(HarvesterRules.Spot.RIPE);
    assertThat(survey(4, 64, 0, 5)).isEqualTo(HarvesterRules.Spot.NONE);
  }

  @Test
  void melonsAndPumpkinsNeedLevelTwoAndAStemAttachedToThem() {
    field.set(0, 64, 0, Material.MELON).set(1, 64, 0, Material.ATTACHED_MELON_STEM, CropField.facing(BlockFace.WEST));
    field.set(0, 64, 5, Material.PUMPKIN).set(1, 64, 5, Material.ATTACHED_PUMPKIN_STEM, CropField.facing(BlockFace.NORTH));
    field.set(0, 64, 9, Material.PUMPKIN).set(0, 64, 8, Material.PUMPKIN_STEM, CropField.ageable(7, 7));
    field.set(0, 64, 12, Material.MELON).set(0, 64, 11, Material.ATTACHED_PUMPKIN_STEM, CropField.facing(BlockFace.SOUTH));

    assertThat(survey(0, 64, 0, 1)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(0, 64, 0, 2)).isEqualTo(HarvesterRules.Spot.RIPE);
    assertThat(survey(0, 64, 5, 5)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(0, 64, 9, 5)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(0, 64, 12, 5)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(1, 64, 0, 5)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(0, 64, 8, 5)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(HarvesterRules.survey(field.block(0, 64, 0), plan(5), block -> block.getX() == 0)).isEqualTo(HarvesterRules.Spot.NONE);
  }

  @Test
  void columnCropsAreTakenFromTheBlockAboveTheirBaseAtLevelTwo() {
    for (int y = 64; y <= 66; y++) {
      field.set(0, y, 0, Material.SUGAR_CANE);
      field.set(1, y, 0, Material.CACTUS);
    }
    for (int y = 64; y <= 70; y++) {
      field.set(2, y, 0, Material.BAMBOO);
    }

    assertThat(survey(0, 64, 0, 5)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(0, 65, 0, 1)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(0, 65, 0, 2)).isEqualTo(HarvesterRules.Spot.RIPE);
    assertThat(survey(0, 66, 0, 5)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(1, 65, 0, 2)).isEqualTo(HarvesterRules.Spot.RIPE);
    assertThat(survey(2, 65, 0, 2)).isEqualTo(HarvesterRules.Spot.RIPE);
    assertThat(HarvesterRules.column(field.block(2, 65, 0))).extracting(Block::getY).containsExactly(65, 66, 67, 68, 69, 70);
    assertThat(HarvesterRules.column(field.block(0, 65, 0))).extracting(Block::getY).containsExactly(65, 66);
  }

  @Test
  void cropsMissingFromTheConfiguredSetAreIgnored() {
    HarvestCrops wheatOnly = HarvestCrops.parse(List.of("WHEAT"));
    field.set(0, 64, 0, Material.CARROTS, CropField.ageable(7, 7));
    field.set(1, 64, 0, Material.WHEAT, CropField.ageable(7, 7));

    HarvesterRules.Plan plan = HarvesterRules.plan(config, wheatOnly, 5, OPEN, NodeItemTags.shared());

    assertThat(HarvesterRules.survey(field.block(0, 64, 0), plan, OWNED)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(HarvesterRules.survey(field.block(1, 64, 0), plan, OWNED)).isEqualTo(HarvesterRules.Spot.RIPE);
  }

  @Test
  void theNodeFilterPicksCropsByTheirBlockOrTheirSeed() {
    field.set(0, 64, 0, Material.WHEAT, CropField.ageable(7, 7));
    field.set(1, 64, 0, Material.CARROTS, CropField.ageable(7, 7));
    field.set(2, 64, 0, Material.MELON).set(3, 64, 0, Material.ATTACHED_MELON_STEM, CropField.facing(BlockFace.WEST));
    field.set(4, 64, 0, Material.SWEET_BERRY_BUSH, CropField.ageable(3, 3));
    field.set(5, 64, 0, Material.CAVE_VINES, CropField.vines(true));
    NodeRecord seeds = filtered(true, Material.WHEAT_SEEDS, Material.MELON, Material.SWEET_BERRIES);
    NodeRecord noCarrots = filtered(false, Material.CARROT, Material.GLOW_BERRIES);

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
    field.set(0, 63, 0, Material.DIRT);
    field.set(1, 63, 0, Material.DIRT).set(1, 64, 0, Material.STONE);
    field.set(2, 63, 0, Material.GRASS_BLOCK);

    assertThat(survey(0, 63, 0, 3)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(0, 63, 0, 4)).isEqualTo(HarvesterRules.Spot.TILL);
    assertThat(survey(1, 63, 0, 5)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(2, 63, 0, 5)).isEqualTo(HarvesterRules.Spot.NONE);
  }

  @Test
  void dirtBesideAStemIsLeftForTheFruit() {
    field.set(0, 63, 0, Material.DIRT).set(1, 64, 0, Material.MELON_STEM, CropField.ageable(7, 7));
    field.set(4, 63, 4, Material.DIRT).set(4, 64, 5, Material.ATTACHED_PUMPKIN_STEM, CropField.facing(BlockFace.NORTH));
    field.set(8, 63, 0, Material.DIRT);

    assertThat(survey(0, 63, 0, 5)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(4, 63, 4, 5)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(8, 63, 0, 5)).isEqualTo(HarvesterRules.Spot.TILL);
    assertThat(HarvesterRules.survey(field.block(8, 63, 0), plan(5), block -> block.getX() == 8)).isEqualTo(HarvesterRules.Spot.NONE);
  }

  @Test
  void emptyFarmlandIsPlantedAtLevelFourUnlessAStemNeedsItForFruit() {
    field.set(0, 63, 0, Material.FARMLAND);
    field.set(1, 63, 0, Material.FARMLAND).set(1, 64, 0, Material.WHEAT, CropField.ageable(0, 7));
    field.set(4, 63, 0, Material.FARMLAND).set(5, 64, 0, Material.MELON_STEM, CropField.ageable(7, 7));
    field.set(4, 63, 4, Material.FARMLAND).set(4, 64, 5, Material.ATTACHED_PUMPKIN_STEM, CropField.facing(BlockFace.NORTH));

    assertThat(survey(0, 63, 0, 3)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(0, 63, 0, 4)).isEqualTo(HarvesterRules.Spot.PLANT);
    assertThat(survey(1, 63, 0, 5)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(4, 63, 0, 5)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(survey(4, 63, 4, 5)).isEqualTo(HarvesterRules.Spot.NONE);
    assertThat(HarvesterRules.survey(field.block(0, 63, 0), plan(5), block -> block.getX() == 0)).isEqualTo(HarvesterRules.Spot.NONE);
  }

  @Test
  void plantingPrefersTheSeedOfANeighboringCropThenAnyCarriedSeedTheNodeWants() {
    field.set(0, 64, 1, Material.CARROTS, CropField.ageable(2, 7));
    List<ItemStack> carried = List.of(GolemTestItems.stack(Material.MELON_SEEDS, 4), GolemTestItems.stack(Material.WHEAT_SEEDS, 3),
        GolemTestItems.stack(Material.CARROT, 2));

    assertThat(HarvesterRules.seedToPlant(plan(4), carried, field.block(0, 64, 0), OWNED)).isEqualTo(Material.CARROT);
    assertThat(HarvesterRules.seedToPlant(plan(4), carried, field.block(5, 64, 5), OWNED)).isEqualTo(Material.WHEAT_SEEDS);
    assertThat(HarvesterRules.seedToPlant(HarvesterRules.plan(config, crops, 4, filtered(false, Material.WHEAT_SEEDS), NodeItemTags.shared()),
        carried, field.block(5, 64, 5), OWNED)).isEqualTo(Material.CARROT);
    assertThat(HarvesterRules.seedToPlant(plan(4), List.of(GolemTestItems.stack(Material.MELON_SEEDS, 4)), field.block(5, 64, 5), OWNED)).isNull();
  }

  @Test
  void replantingUsesEachCropsOwnSeed() {
    assertThat(HarvestCrops.seed(Material.WHEAT)).isEqualTo(Material.WHEAT_SEEDS);
    assertThat(HarvestCrops.seed(Material.CARROTS)).isEqualTo(Material.CARROT);
    assertThat(HarvestCrops.seed(Material.POTATOES)).isEqualTo(Material.POTATO);
    assertThat(HarvestCrops.seed(Material.BEETROOTS)).isEqualTo(Material.BEETROOT_SEEDS);
    assertThat(HarvestCrops.seed(Material.NETHER_WART)).isEqualTo(Material.NETHER_WART);
    assertThat(HarvestCrops.seed(Material.COCOA)).isEqualTo(Material.COCOA_BEANS);
    assertThat(HarvestCrops.seed(Material.PITCHER_CROP)).isEqualTo(Material.PITCHER_POD);
    assertThat(HarvestCrops.seed(Material.TORCHFLOWER)).isEqualTo(Material.TORCHFLOWER_SEEDS);
    assertThat(HarvestCrops.seed(Material.MELON)).isNull();
  }

  @Test
  void theCropListMapsPlantFamiliesAndSkipsUnknownNames() {
    HarvestCrops parsed = HarvestCrops.parse(List.of("torchflower_crop", "CAVE_VINES", "MELON", "BAMBOO", "SWEET_BERRY_BUSH", "WHEAT", "NOT_A_BLOCK"));

    assertThat(parsed.kind(Material.TORCHFLOWER)).isEqualTo(HarvestCrops.Kind.TORCHFLOWER);
    assertThat(parsed.kind(Material.TORCHFLOWER_CROP)).isEqualTo(HarvestCrops.Kind.TORCHFLOWER);
    assertThat(parsed.kind(Material.CAVE_VINES_PLANT)).isEqualTo(HarvestCrops.Kind.CAVE_VINES);
    assertThat(parsed.kind(Material.MELON)).isEqualTo(HarvestCrops.Kind.GOURD);
    assertThat(parsed.kind(Material.BAMBOO)).isEqualTo(HarvestCrops.Kind.COLUMN);
    assertThat(parsed.kind(Material.SWEET_BERRY_BUSH)).isEqualTo(HarvestCrops.Kind.BERRY_BUSH);
    assertThat(parsed.kind(Material.WHEAT)).isEqualTo(HarvestCrops.Kind.CROP);
    assertThat(parsed.kind(Material.CARROTS)).isEqualTo(HarvestCrops.Kind.NONE);
    assertThat(parsed.farmlandCrop(Material.WHEAT_SEEDS)).isEqualTo(Material.WHEAT);
    assertThat(parsed.farmlandCrop(Material.TORCHFLOWER_SEEDS)).isEqualTo(Material.TORCHFLOWER_CROP);
    assertThat(parsed.farmlandCrop(Material.CARROT)).isNull();
    assertThat(HarvestCrops.parse(config.crops).kind(Material.NETHER_WART)).isEqualTo(HarvestCrops.Kind.CROP);
  }

  @Test
  void stemsInTheCropListAreIgnored() {
    HarvestCrops parsed = HarvestCrops.parse(List.of("MELON_STEM", "PUMPKIN_STEM", "ATTACHED_MELON_STEM", "ATTACHED_PUMPKIN_STEM", "MELON"));

    assertThat(parsed.kind(Material.MELON_STEM)).isEqualTo(HarvestCrops.Kind.NONE);
    assertThat(parsed.kind(Material.PUMPKIN_STEM)).isEqualTo(HarvestCrops.Kind.NONE);
    assertThat(parsed.kind(Material.ATTACHED_MELON_STEM)).isEqualTo(HarvestCrops.Kind.NONE);
    assertThat(parsed.kind(Material.ATTACHED_PUMPKIN_STEM)).isEqualTo(HarvestCrops.Kind.NONE);
    assertThat(parsed.kind(Material.MELON)).isEqualTo(HarvestCrops.Kind.GOURD);
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
    return HarvesterRules.survey(field.block(x, y, z), HarvesterRules.plan(config, crops, 5, node, NodeItemTags.shared()), OWNED);
  }

  private HarvesterRules.Plan plan(int level) {
    return HarvesterRules.plan(config, crops, level, OPEN, NodeItemTags.shared());
  }

  private static List<Boolean> flags(HarvesterRules.Plan plan) {
    return List.of(plan.extended(), plan.tending(), plan.tilling(), plan.bountiful());
  }

  private static NodeRecord filtered(boolean whitelist, Material... items) {
    List<ItemStack> entries = new ArrayList<>();
    for (Material item : items) {
      entries.add(GolemTestItems.stack(item, 1));
    }
    return NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).filterItems(entries).whitelist(whitelist).build();
  }
}
