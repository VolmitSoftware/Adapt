package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.content.golem.GolemTestItems;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.content.golem.node.NodeTagLookup;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
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
    public boolean tagged(String tag, Id material) {
      return tag.equals("#minecraft:ice") && (material.equals(Id.minecraft("ice")) || material.equals(Id.minecraft("packed_ice")));
    }
  };
  private static final Set<Id> PICKAXES = Set.of(Id.minecraft("wooden_pickaxe"), Id.minecraft("stone_pickaxe"), Id.minecraft("iron_pickaxe"),
      Id.minecraft("golden_pickaxe"), Id.minecraft("diamond_pickaxe"), Id.minecraft("netherite_pickaxe"));
  private static final Function<String, Collection<Id>> BLOCK_TAGS = tag -> switch (tag) {
    case "#beds" -> List.of(Id.minecraft("red_bed"), Id.minecraft("white_bed"));
    case "#doors" -> List.of(Id.minecraft("oak_door"), Id.minecraft("iron_door"));
    default -> null;
  };

  private final GolemBreakerCore.Config config = new GolemBreakerCore.Config();
  private final CropField field = new CropField();

  @Test
  void theFreeToolIsTheLevelsPickaxeCappedAtIron() {
    assertThat(BreakerRules.freeTool(Id.minecraft("stone_pickaxe"))).isEqualTo(Id.minecraft("stone_pickaxe"));
    assertThat(BreakerRules.freeTool(Id.minecraft("iron_pickaxe"))).isEqualTo(Id.minecraft("iron_pickaxe"));
    assertThat(BreakerRules.freeTool(Id.minecraft("diamond_pickaxe"))).isEqualTo(Id.minecraft("iron_pickaxe"));
    assertThat(BreakerRules.freeTool(Id.minecraft("netherite_pickaxe"))).isEqualTo(Id.minecraft("iron_pickaxe"));
    assertThat(BreakerRules.aboveFree(Id.minecraft("diamond_pickaxe"))).isTrue();
    assertThat(BreakerRules.aboveFree(Id.minecraft("iron_pickaxe"))).isFalse();
  }

  @Test
  void pickaxesCostByMaterialFromWoodToNetherite() {
    List<Id> order = List.of(Id.minecraft("wooden_pickaxe"), Id.minecraft("stone_pickaxe"), Id.minecraft("copper_pickaxe"), Id.minecraft("golden_pickaxe"),
        Id.minecraft("iron_pickaxe"), Id.minecraft("diamond_pickaxe"), Id.minecraft("netherite_pickaxe"));
    for (int index = 1; index < order.size(); index++) {
      assertThat(BreakerRules.cost(GolemTestItems.stack(order.get(index - 1), 1))).isLessThan(BreakerRules.cost(GolemTestItems.stack(order.get(index), 1)));
    }
  }

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
    List<Id> tools = BreakerRules.tools(config.toolByLevel, PICKAXES::contains, rejected::add);
    List<String> custom = new ArrayList<>(List.of("golden_pickaxe", "no_such_tool", "DIAMOND_SHOVEL", "STONE"));
    custom.add(null);

    assertThat(List.of(1, 2, 3, 4, 5)).extracting(level -> BreakerRules.toolAt(tools, level))
        .containsExactly(Id.minecraft("stone_pickaxe"), Id.minecraft("iron_pickaxe"), Id.minecraft("iron_pickaxe"), Id.minecraft("diamond_pickaxe"), Id.minecraft("netherite_pickaxe"));
    assertThat(rejected).isEmpty();
    assertThat(BreakerRules.tools(custom, PICKAXES::contains, rejected::add))
        .containsExactly(Id.minecraft("golden_pickaxe"), Id.minecraft("iron_pickaxe"), Id.minecraft("iron_pickaxe"), Id.minecraft("diamond_pickaxe"), Id.minecraft("netherite_pickaxe"));
    assertThat(rejected).containsExactly("no_such_tool", "DIAMOND_SHOVEL", "STONE", "null");
    assertThat(BreakerRules.toolAt(List.of(), 5)).isEqualTo(Id.minecraft("netherite_pickaxe"));
    assertThat(BreakerRules.toolAt(tools, 0)).isEqualTo(Id.minecraft("stone_pickaxe"));
    assertThat(BreakerRules.toolAt(tools, 9)).isEqualTo(Id.minecraft("netherite_pickaxe"));
    assertThat(BreakerRules.tools(null, PICKAXES::contains, rejected::add)).isEmpty();
  }

  @Test
  void theBlacklistReadsBlockNamesAndBlockTagsAndIgnoresUnknownEntries() {
    List<String> names = new ArrayList<>(List.of("bedrock", " SPAWNER ", "#beds", "#doors", "#no_such_tag", "not_a_block", ""));
    names.add(null);

    Set<Id> blacklist = BreakerRules.blacklist(names, BLOCK_TAGS, id -> !id.path().equals("not_a_block"));

    assertThat(blacklist).containsExactlyInAnyOrder(Id.minecraft("bedrock"), Id.minecraft("spawner"), Id.minecraft("red_bed"), Id.minecraft("white_bed"),
        Id.minecraft("oak_door"), Id.minecraft("iron_door"));
    assertThat(BreakerRules.blacklist(null, BLOCK_TAGS, id -> true)).isEmpty();
  }

  @Test
  void theDefaultBlacklistCoversThePlanList() {
    Set<Id> blacklist = BreakerRules.blacklist(config.blacklist, BLOCK_TAGS, id -> true);

    assertThat(blacklist).contains(Id.minecraft("bedrock"), Id.minecraft("barrier"), Id.minecraft("command_block"), Id.minecraft("chain_command_block"),
        Id.minecraft("repeating_command_block"), Id.minecraft("structure_block"), Id.minecraft("structure_void"), Id.minecraft("jigsaw"), Id.minecraft("spawner"),
        Id.minecraft("trial_spawner"), Id.minecraft("vault"), Id.minecraft("end_portal_frame"), Id.minecraft("reinforced_deepslate"), Id.minecraft("budding_amethyst"),
        Id.minecraft("red_bed"), Id.minecraft("oak_door"));
  }

  @Test
  void onlyAWhitelistFilterWithEntriesChoosesBlocks() {
    field.set(0, 64, 0, Id.minecraft("cobblestone"), placed(Id.minecraft("cobblestone")));

    assertThat(BreakerRules.wanted(node(List.of(), true), block(0, 64, 0), TAGS)).isFalse();
    assertThat(BreakerRules.wanted(node(List.of(Id.minecraft("cobblestone")), false), block(0, 64, 0), TAGS)).isFalse();
    assertThat(BreakerRules.wanted(node(List.of(Id.minecraft("stone")), false), block(0, 64, 0), TAGS)).isFalse();
    assertThat(BreakerRules.wanted(node(List.of(Id.minecraft("stone")), true), block(0, 64, 0), TAGS)).isFalse();
    assertThat(BreakerRules.wanted(node(List.of(Id.minecraft("cobblestone")), true), block(0, 64, 0), TAGS)).isTrue();
    assertThat(BreakerRules.filtered(node(List.of(Id.minecraft("cobblestone")), true))).isTrue();
    assertThat(BreakerRules.filtered(node(List.of(Id.minecraft("cobblestone")), false))).isFalse();
    assertThat(BreakerRules.filtered(node(List.of(), true))).isFalse();
  }

  @Test
  void aFilterEntryMatchesABlockByItsTypeItsPlacingItemOrAnItemTag() {
    field.set(0, 64, 0, Id.minecraft("redstone_wire"), placed(Id.minecraft("redstone")));
    field.set(1, 64, 0, Id.minecraft("packed_ice"), placed(Id.minecraft("packed_ice")));
    NodeRecord redstone = node(List.of(Id.minecraft("redstone")), true);
    NodeRecord ice = NodeRecord.builder(OWNER, NodeType.BREAKER, BlockFace.UP).filterTags(List.of("#minecraft:ice")).build();

    assertThat(BreakerRules.wanted(redstone, block(0, 64, 0), TAGS)).isTrue();
    assertThat(BreakerRules.wanted(ice, block(1, 64, 0), TAGS)).isTrue();
    assertThat(BreakerRules.wanted(ice, block(0, 64, 0), TAGS)).isFalse();
  }

  @Test
  void aBlockIsMinableWhenItIsSolidUnlistedAndNoHarderThanTheLevelAllows() {
    BreakerRules.Plan plan = BreakerRules.plan(config, BreakerRules.blacklist(config.blacklist, BLOCK_TAGS, id -> true), 1, node(List.of(Id.minecraft("stone")), true),
        TAGS, GolemTestItems.stack(Id.minecraft("stone_pickaxe"), 1));

    assertThat(plan.maxHardness()).isEqualTo(2.0D);
    assertThat(BreakerRules.minable(Id.minecraft("stone"), 1.5F, plan)).isTrue();
    assertThat(BreakerRules.minable(Id.minecraft("cobblestone"), 2.0F, plan)).isTrue();
    assertThat(BreakerRules.minable(Id.minecraft("deepslate"), 3.0F, plan)).isFalse();
    assertThat(BreakerRules.minable(Id.minecraft("bedrock"), 1.0F, plan)).isFalse();
    assertThat(BreakerRules.minable(Id.minecraft("barrier"), -1.0F, plan)).isFalse();
    assertThat(BreakerRules.minable(Id.minecraft("nether_portal"), -1.0F, plan)).isFalse();
    assertThat(BreakerRules.minable(Id.minecraft("air"), 0.0F, plan)).isFalse();
    assertThat(BreakerRules.minable(Id.minecraft("cave_air"), 0.0F, plan)).isFalse();
    assertThat(BreakerRules.minable(Id.minecraft("short_grass"), 0.0F, plan)).isTrue();
  }

  @Test
  void silkTouchAndFortuneApplyFromTheirLevelsWhenTheNodeTurnsThemOn() {
    NodeRecord plain = node(List.of(Id.minecraft("stone")), true);
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
    NodeRecord plain = node(List.of(Id.minecraft("stone")), true).toBuilder().data(Map.of("other", "kept")).build();

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
    NodeRecord both = node(List.of(Id.minecraft("stone")), true).toBuilder().data(Map.of("silk", "true", "fortune", "true")).build();

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

  private BlockRef block(int x, int y, int z) {
    return field.block(x, y, z);
  }

  static BlockStateRef placed(Id placement) {
    BlockStateRef data = mock(BlockStateRef.class);
    lenient().when(data.placementItem()).thenReturn(placement);
    return data;
  }

  private static NodeRecord node(List<Id> filter, boolean whitelist) {
    List<ItemRef> items = new ArrayList<>(filter.size());
    for (Id type : filter) {
      items.add(GolemTestItems.stack(type, 1));
    }
    return NodeRecord.builder(OWNER, NodeType.BREAKER, BlockFace.UP).filterItems(items).whitelist(whitelist).build();
  }
}
