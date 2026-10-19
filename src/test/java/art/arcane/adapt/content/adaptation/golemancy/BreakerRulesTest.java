package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemTestItems;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeTagLookup;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class BreakerRulesTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000024b1");
  private static final NodeTagLookup TAGS = new NodeTagLookup() {
    @Override
    public boolean exists(String tag) {
      return tag.equals("#minecraft:ice");
    }

    @Override
    public boolean tagged(String tag, Material material) {
      return tag.equals("#minecraft:ice") && (material == Material.ICE || material == Material.PACKED_ICE);
    }
  };
  private static final Set<Material> PICKAXES = Set.of(Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE,
      Material.GOLDEN_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE);
  private static final Function<String, Collection<Material>> BLOCK_TAGS = tag -> switch (tag) {
    case "#beds" -> List.of(Material.RED_BED, Material.WHITE_BED);
    case "#doors" -> List.of(Material.OAK_DOOR, Material.IRON_DOOR);
    default -> null;
  };

  private final GolemBreakerCore.Config config = new GolemBreakerCore.Config();
  private final CropField field = new CropField();

  @Test
  void levelValuesReadTheirArraysAndNeverDropBelowTheirFloor() {
    assertThat(List.of(1, 2, 3, 4, 5)).extracting(level -> BreakerRules.intervalTicks(config.intervalTicksByLevel, level))
        .containsExactly(40, 30, 20, 15, 10);
    assertThat(List.of(1, 2, 3, 4, 5)).extracting(level -> BreakerRules.maxHardness(config.maxHardnessByLevel, level))
        .containsExactly(2.0D, 3.0D, 5.0D, 10.0D, 50.0D);
    assertThat(BreakerRules.intervalTicks(config.intervalTicksByLevel, 0)).isEqualTo(40);
    assertThat(BreakerRules.intervalTicks(config.intervalTicksByLevel, 9)).isEqualTo(10);
    assertThat(BreakerRules.intervalTicks(List.of(-3), 1)).isEqualTo(1);
    assertThat(BreakerRules.intervalTicks(List.of(), 2)).isEqualTo(1);
    assertThat(BreakerRules.maxHardness(List.of(), 2)).isZero();
    assertThat(BreakerRules.maxHardness(List.of(Double.NaN), 1)).isZero();
  }

  @Test
  void eachLevelMinesWithItsPickaxeAndNamesThatAreNotPickaxesFallBackToTheLevelDefault() {
    List<String> rejected = new ArrayList<>();
    List<Material> tools = BreakerRules.tools(config.toolByLevel, PICKAXES::contains, rejected::add);
    List<String> custom = new ArrayList<>(List.of("golden_pickaxe", "no_such_tool", "DIAMOND_SHOVEL", "STONE"));
    custom.add(null);

    assertThat(List.of(1, 2, 3, 4, 5)).extracting(level -> BreakerRules.toolAt(tools, level))
        .containsExactly(Material.STONE_PICKAXE, Material.IRON_PICKAXE, Material.IRON_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE);
    assertThat(rejected).isEmpty();
    assertThat(BreakerRules.tools(custom, PICKAXES::contains, rejected::add))
        .containsExactly(Material.GOLDEN_PICKAXE, Material.IRON_PICKAXE, Material.IRON_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE);
    assertThat(rejected).containsExactly("no_such_tool", "DIAMOND_SHOVEL", "STONE", "null");
    assertThat(BreakerRules.toolAt(List.of(), 5)).isEqualTo(Material.NETHERITE_PICKAXE);
    assertThat(BreakerRules.toolAt(tools, 0)).isEqualTo(Material.STONE_PICKAXE);
    assertThat(BreakerRules.toolAt(tools, 9)).isEqualTo(Material.NETHERITE_PICKAXE);
    assertThat(BreakerRules.tools(null, PICKAXES::contains, rejected::add)).isEmpty();
  }

  @Test
  void theBlacklistReadsBlockNamesAndBlockTagsAndIgnoresUnknownEntries() {
    List<String> names = new ArrayList<>(List.of("bedrock", " SPAWNER ", "#beds", "#doors", "#no_such_tag", "not_a_block", ""));
    names.add(null);

    Set<Material> blacklist = BreakerRules.blacklist(names, BLOCK_TAGS);

    assertThat(blacklist).containsExactlyInAnyOrder(Material.BEDROCK, Material.SPAWNER, Material.RED_BED, Material.WHITE_BED,
        Material.OAK_DOOR, Material.IRON_DOOR);
    assertThat(BreakerRules.blacklist(null, BLOCK_TAGS)).isEmpty();
  }

  @Test
  void theDefaultBlacklistCoversThePlanList() {
    Set<Material> blacklist = BreakerRules.blacklist(config.blacklist, BLOCK_TAGS);

    assertThat(blacklist).contains(Material.BEDROCK, Material.BARRIER, Material.COMMAND_BLOCK, Material.CHAIN_COMMAND_BLOCK,
        Material.REPEATING_COMMAND_BLOCK, Material.STRUCTURE_BLOCK, Material.STRUCTURE_VOID, Material.JIGSAW, Material.SPAWNER,
        Material.TRIAL_SPAWNER, Material.VAULT, Material.END_PORTAL_FRAME, Material.REINFORCED_DEEPSLATE, Material.BUDDING_AMETHYST,
        Material.RED_BED, Material.OAK_DOOR);
  }

  @Test
  void onlyAWhitelistFilterWithEntriesChoosesBlocks() {
    field.set(0, 64, 0, Material.COBBLESTONE, placed(Material.COBBLESTONE));

    assertThat(BreakerRules.wanted(node(List.of(), true), block(0, 64, 0), TAGS)).isFalse();
    assertThat(BreakerRules.wanted(node(List.of(Material.COBBLESTONE), false), block(0, 64, 0), TAGS)).isFalse();
    assertThat(BreakerRules.wanted(node(List.of(Material.STONE), false), block(0, 64, 0), TAGS)).isFalse();
    assertThat(BreakerRules.wanted(node(List.of(Material.STONE), true), block(0, 64, 0), TAGS)).isFalse();
    assertThat(BreakerRules.wanted(node(List.of(Material.COBBLESTONE), true), block(0, 64, 0), TAGS)).isTrue();
    assertThat(BreakerRules.filtered(node(List.of(Material.COBBLESTONE), true))).isTrue();
    assertThat(BreakerRules.filtered(node(List.of(Material.COBBLESTONE), false))).isFalse();
    assertThat(BreakerRules.filtered(node(List.of(), true))).isFalse();
  }

  @Test
  void aFilterEntryMatchesABlockByItsTypeItsPlacingItemOrAnItemTag() {
    field.set(0, 64, 0, Material.REDSTONE_WIRE, placed(Material.REDSTONE));
    field.set(1, 64, 0, Material.PACKED_ICE, placed(Material.PACKED_ICE));
    NodeRecord redstone = node(List.of(Material.REDSTONE), true);
    NodeRecord ice = NodeRecord.builder(OWNER, NodeType.BREAKER, BlockFace.UP).filterTags(List.of("#minecraft:ice")).build();

    assertThat(BreakerRules.wanted(redstone, block(0, 64, 0), TAGS)).isTrue();
    assertThat(BreakerRules.wanted(ice, block(1, 64, 0), TAGS)).isTrue();
    assertThat(BreakerRules.wanted(ice, block(0, 64, 0), TAGS)).isFalse();
  }

  @Test
  void aBlockIsMinableWhenItIsSolidUnlistedAndNoHarderThanTheLevelAllows() {
    BreakerRules.Plan plan = BreakerRules.plan(config, BreakerRules.blacklist(config.blacklist, BLOCK_TAGS), 1, node(List.of(Material.STONE), true),
        TAGS, GolemTestItems.stack(Material.STONE_PICKAXE, 1));

    assertThat(plan.maxHardness()).isEqualTo(2.0D);
    assertThat(BreakerRules.minable(Material.STONE, 1.5F, plan)).isTrue();
    assertThat(BreakerRules.minable(Material.COBBLESTONE, 2.0F, plan)).isTrue();
    assertThat(BreakerRules.minable(Material.DEEPSLATE, 3.0F, plan)).isFalse();
    assertThat(BreakerRules.minable(Material.BEDROCK, 1.0F, plan)).isFalse();
    assertThat(BreakerRules.minable(Material.BARRIER, -1.0F, plan)).isFalse();
    assertThat(BreakerRules.minable(Material.NETHER_PORTAL, -1.0F, plan)).isFalse();
    assertThat(BreakerRules.minable(Material.AIR, 0.0F, plan)).isFalse();
    assertThat(BreakerRules.minable(Material.CAVE_AIR, 0.0F, plan)).isFalse();
    assertThat(BreakerRules.minable(Material.SHORT_GRASS, 0.0F, plan)).isTrue();
  }

  @Test
  void silkTouchAndFortuneApplyFromTheirLevelsWhenTheNodeTurnsThemOn() {
    NodeRecord plain = node(List.of(Material.STONE), true);
    NodeRecord silky = BreakerRules.toggleSilk(plain);
    NodeRecord lucky = BreakerRules.toggleFortune(plain);

    assertThat(BreakerRules.silk(config, 5, plain)).isFalse();
    assertThat(BreakerRules.silk(config, 2, silky)).isFalse();
    assertThat(BreakerRules.silk(config, 3, silky)).isTrue();
    assertThat(BreakerRules.fortune(config, 4, lucky)).isFalse();
    assertThat(BreakerRules.fortune(config, 5, lucky)).isTrue();
    assertThat(BreakerRules.fortune(config, 5, silky)).isFalse();
  }

  @Test
  void silkTouchAndFortuneTurnEachOtherOffAndToggleBackOff() {
    NodeRecord plain = node(List.of(Material.STONE), true).toBuilder().data(Map.of("other", "kept")).build();

    NodeRecord silky = BreakerRules.toggleSilk(plain);
    NodeRecord lucky = BreakerRules.toggleFortune(silky);
    NodeRecord silkyAgain = BreakerRules.toggleSilk(lucky);

    assertThat(silky.data()).containsExactlyInAnyOrderEntriesOf(Map.of("other", "kept", "silk", "true"));
    assertThat(lucky.data()).containsExactlyInAnyOrderEntriesOf(Map.of("other", "kept", "fortune", "true"));
    assertThat(silkyAgain.data()).containsExactlyInAnyOrderEntriesOf(Map.of("other", "kept", "silk", "true"));
    assertThat(BreakerRules.toggleSilk(silkyAgain).data()).containsExactlyInAnyOrderEntriesOf(Map.of("other", "kept"));
    assertThat(BreakerRules.toggleFortune(lucky).data()).containsExactlyInAnyOrderEntriesOf(Map.of("other", "kept"));
    assertThat(BreakerRules.on(silky, BreakerRules.SILK_KEY)).isTrue();
    assertThat(BreakerRules.on(silky, BreakerRules.FORTUNE_KEY)).isFalse();
  }

  @Test
  void bothStoredTogglesLetSilkTouchWin() {
    NodeRecord both = node(List.of(Material.STONE), true).toBuilder().data(Map.of("silk", "true", "fortune", "true")).build();

    assertThat(BreakerRules.silk(config, 5, both)).isTrue();
    assertThat(BreakerRules.fortune(config, 5, both)).isFalse();
    assertThat(BreakerRules.fortune(config, 5, BreakerRules.toggleSilk(both))).isTrue();
  }

  @Test
  void negativeValuesClampToZero() {
    assertThat(GolemancyRules.nonNegative(-2D)).isZero();
    assertThat(GolemancyRules.nonNegative(Double.NaN)).isZero();
    assertThat(GolemancyRules.nonNegative(0.4D)).isEqualTo(0.4D);
  }

  private Block block(int x, int y, int z) {
    return field.block(x, y, z);
  }

  static BlockData placed(Material placement) {
    BlockData data = mock(BlockData.class);
    lenient().when(data.getPlacementMaterial()).thenReturn(placement);
    return data;
  }

  private static NodeRecord node(List<Material> filter, boolean whitelist) {
    List<ItemStack> items = new ArrayList<>(filter.size());
    for (Material type : filter) {
      items.add(GolemTestItems.stack(type, 1));
    }
    return NodeRecord.builder(OWNER, NodeType.BREAKER, BlockFace.UP).filterItems(items).whitelist(whitelist).build();
  }
}
