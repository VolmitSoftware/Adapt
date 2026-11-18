package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemActResult;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.GolemTestItems;
import art.arcane.adapt.api.golem.GolemToolEnchants;
import art.arcane.adapt.api.golem.GolemToolNeed;
import art.arcane.adapt.api.golem.GolemToolPlan;
import art.arcane.adapt.api.golem.GolemToolRules;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeArea;
import art.arcane.adapt.api.golem.node.NodeContext;
import art.arcane.adapt.api.golem.node.NodeCuboid;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.ScanBudget;
import art.arcane.adapt.api.golem.plinth.PlinthService;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BreakerNodeBehaviorTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000024c1");
  private static final NodeKey KEY = new NodeKey(CropField.WORLD, 0, 63, 0, BlockFace.UP);
  private static final long NOW = 70_000L;

  private final GolemBreakerCore.Config config = new GolemBreakerCore.Config();
  private final CropField field = new CropField();
  private final List<GolemTask> posted = new ArrayList<>();
  private final List<ItemRef> carried = new ArrayList<>();
  private final List<Double> xp = new ArrayList<>();
  private final Map<ItemRef, String> tools = new IdentityHashMap<>();
  private final Map<Id, Float> hardness = new HashMap<>();
  private final Set<Id> tileEntities = new HashSet<>();
  private NodeRecord record = record(true, Id.minecraft("cobblestone"), Id.minecraft("stone"), Id.minecraft("deepslate"), Id.minecraft("bedrock"), Id.minecraft("obsidian"),
      Id.minecraft("chest"), Id.minecraft("iron_ore"), Id.minecraft("coal_ore"), Id.minecraft("water"));
  private GolemBreakerCore core;
  private BreakerNodeBehavior behavior;
  private int roomLeft = 64;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<BreakerFeedback> feedback;
  private MockedStatic<XpProvenance> provenance;
  private MockedStatic<GolemToolEnchants> enchants;
  private ItemRef inUse;

  @BeforeEach
  void setUp() {
    core = mock(GolemBreakerCore.class);
    lenient().when(core.config()).thenReturn(config);
    lenient().when(core.blacklist()).thenAnswer(invocation -> BreakerRules.blacklist(config.blacklist, tag -> List.of(), id -> true));
    lenient().when(core.hardness(any(Id.class))).thenAnswer(invocation -> hardness.getOrDefault(invocation.<Id>getArgument(0), 1.0F));
    lenient().when(core.tileEntity(any(Id.class))).thenAnswer(invocation -> tileEntities.contains(invocation.<Id>getArgument(0)));
    List<Id> pickaxes = BreakerRules.tools(BreakerRules.DEFAULT_TOOLS, type -> true, name -> {
    });
    lenient().when(core.pickaxe(anyInt())).thenAnswer(invocation -> BreakerRules.toolAt(pickaxes, invocation.getArgument(0)));
    lenient().when(core.tool(any(Id.class))).thenAnswer(invocation -> pick(invocation.getArgument(0), ""));
    level(2);
    hardness.putAll(Map.of(Id.minecraft("cobblestone"), 2.0F, Id.minecraft("stone"), 1.5F, Id.minecraft("deepslate"), 3.0F, Id.minecraft("bedrock"), -1.0F,
        Id.minecraft("obsidian"), 50.0F, Id.minecraft("chest"), 2.5F, Id.minecraft("iron_ore"), 3.0F, Id.minecraft("coal_ore"), 3.0F, Id.minecraft("dirt"), 0.5F,
        Id.minecraft("water"), 100.0F));
    tileEntities.add(Id.minecraft("chest"));
    field.toolDrops(Id.minecraft("stone"), tool -> CropField.stacks(tools.get(tool).contains("silk") ? Id.minecraft("stone") : Id.minecraft("cobblestone"), 1));
    field.toolDrops(Id.minecraft("coal_ore"), tool -> CropField.stacks(Id.minecraft("coal"), 1 + fortuneOf(tool)));
    field.toolDrops(Id.minecraft("obsidian"), tool -> CropField.stacks(Id.minecraft("obsidian"), 1));
    field.toolDrops(Id.minecraft("diamond_ore"), tool -> CropField.stacks(tools.get(tool).contains("silk") ? Id.minecraft("diamond_ore") : Id.minecraft("diamond"), 1));
    field.needsTier(Id.minecraft("obsidian"), Id.minecraft("diamond_pickaxe"));
    field.needsTier(Id.minecraft("diamond_ore"), Id.minecraft("iron_pickaxe"));
    field.needsTier(Id.minecraft("stone"), Id.minecraft("wooden_pickaxe"));
    field.toolDrops(Id.minecraft("cobblestone"), tool -> CropField.stacks(Id.minecraft("cobblestone"), 1));
    field.toolDrops(Id.minecraft("diamond_block"), tool -> CropField.stacks(Id.minecraft("diamond_block"), 1));
    field.unharvestable(Id.minecraft("iron_ore"));
    behavior = new BreakerNodeBehavior(core);
    runtime = mockStatic(GolemancyRuntime.class, CALLS_REAL_METHODS);
    feedback = mockStatic(BreakerFeedback.class);
    provenance = mockStatic(XpProvenance.class);
    enchants = mockStatic(GolemToolEnchants.class);
    enchants.when(() -> GolemToolEnchants.silkTouch(any(ItemRef.class)))
        .thenAnswer(invocation -> tools.getOrDefault(invocation.<ItemRef>getArgument(0), "").contains("+silk") ? 1 : 0);
    enchants.when(() -> GolemToolEnchants.fortune(any(ItemRef.class))).thenAnswer(invocation -> fortuneOf(invocation.getArgument(0)));
  }

  @AfterEach
  void close() {
    enchants.close();
    provenance.close();
    feedback.close();
    runtime.close();
  }

  @Test
  void itActsForTheBreakerCoreAdaptation() {
    assertThat(behavior.policy()).isSameAs(core);
  }

  @Test
  void aScanPostsBreakJobsOnlyForFilteredBlocksTheLevelCanMine() {
    quarry();

    behavior.scan(node(), budget(true));

    assertThat(jobs()).containsExactly("break -1,64,-1", "break 0,63,0", "break 0,64,-1", "break 1,64,1");
  }

  @Test
  void theHardnessCapRisesWithTheLevel() {
    quarry();

    level(1);
    behavior.scan(node(), budget(true));
    assertThat(jobs()).containsExactly("break -1,64,-1", "break 0,63,0", "break 1,64,1");

    posted.clear();
    level(5);
    behavior.scan(node(), budget(true));
    assertThat(jobs()).containsExactly("break -1,64,-1", "break -1,64,1", "break 0,63,0", "break 0,64,-1", "break 1,64,1");
  }

  @Test
  void anOwnerWithoutTheCorePostsNothing() {
    quarry();
    level(0);

    behavior.scan(node(), budget(true));

    assertThat(posted).isEmpty();
  }

  @Test
  void aNodeWithoutAWhitelistFilterBreaksNothing() {
    quarry();

    record = record(true);
    behavior.scan(node(), budget(true));
    assertThat(posted).isEmpty();

    record = record(false, Id.minecraft("dirt"));
    behavior.scan(node(), budget(true));
    assertThat(posted).isEmpty();
  }

  @Test
  void liquidsAreNeverBrokenEvenUnderAHighCap() {
    config.maxHardnessByLevel = new ArrayList<>(List.of(200.0D));
    field.set(0, 64, 0, Id.minecraft("water"));

    behavior.scan(node(), budget(true));

    assertThat(posted).isEmpty();
  }

  @Test
  void aNodeAnchorIsBrokenLikeAnyOtherBlock() {
    field.set(0, 63, 0, Id.minecraft("stone"), BreakerRulesTest.placed(Id.minecraft("stone")));
    field.set(2, 64, -2, Id.minecraft("stone"), BreakerRulesTest.placed(Id.minecraft("stone")));
    field.set(-2, 64, -2, Id.minecraft("stone"), BreakerRulesTest.placed(Id.minecraft("stone")));
    field.set(2, 64, 2, Id.minecraft("stone"), BreakerRulesTest.placed(Id.minecraft("stone")));

    behavior.scan(node(), budget(true));

    assertThat(jobs()).containsExactly("break -2,64,-2", "break 0,63,0", "break 2,64,-2", "break 2,64,2");
    assertThat(behavior.act(golem(1), task(0, 63, 0))).isEqualTo(GolemActResult.DONE);
    assertThat(behavior.act(golem(1), task(2, 64, -2))).isEqualTo(GolemActResult.DONE);
    assertThat(field.type(0, 63, 0)).isEqualTo(Id.minecraft("air"));
    assertThat(field.type(2, 64, -2)).isEqualTo(Id.minecraft("air"));
  }

  @Test
  void aJobWhoseBlockChangedOrLeftTheAreaIsWithdrawnAndAnOpenJobIsNeverPostedTwice() {
    quarry();
    field.set(0, 64, 0, Id.minecraft("dirt"), BreakerRulesTest.placed(Id.minecraft("dirt")));
    GolemTask stale = task(0, 64, 0);
    GolemTask outside = task(3, 64, 3);
    GolemTask open = task(1, 64, 1);
    posted.addAll(List.of(stale, outside, open));

    behavior.scan(node(), budget(true));

    assertThat(posted).contains(open).doesNotContain(stale, outside);
    assertThat(jobs()).containsExactly("break -1,64,-1", "break 0,63,0", "break 0,64,-1", "break 1,64,1");
  }

  @Test
  void aFullNodePostsNothing() {
    quarry();

    behavior.scan(node(), budget(false));

    assertThat(posted).isEmpty();
  }

  @Test
  void theFirstActWindsUpAndTheNextBreaksTheBlockWithTheLevelsPickaxe() {
    stone(1, 64, 1);
    GolemTask task = task(1, 64, 1);

    assertThat(behavior.act(golem(0), task)).isEqualTo(GolemActResult.WORKING);
    assertThat(field.type(1, 64, 1)).isEqualTo(Id.minecraft("stone"));

    assertThat(behavior.act(golem(1), task)).isEqualTo(GolemActResult.DONE);

    assertThat(field.type(1, 64, 1)).isEqualTo(Id.minecraft("air"));
    assertThat(field.physicsFree).isEmpty();
    assertThat(carriedCounts()).containsExactlyEntriesOf(Map.of("COBBLESTONE", 1));
    assertThat(xp).containsExactly(0.4D);
    verify(core, atLeastOnce()).tool(Id.minecraft("iron_pickaxe"));
    feedback.verify(() -> BreakerFeedback.play(eq(core), any(Loc.class), any(BlockStateRef.class)));
  }

  @Test
  void silkTouchMinesBlocksWholeOnlyWhileTheGolemHoldsASilkTouchPickaxeAndWearsIt() {
    record = BreakerRules.toggleSilk(record);
    stone(1, 64, 1);
    stone(1, 64, 0);
    level(3);
    GolemContext free = golem(1);

    assertThat(behavior.act(free, task(1, 64, 1))).isEqualTo(GolemActResult.DONE);
    inUse = pick(Id.minecraft("iron_pickaxe"), "+silk");
    GolemContext silky = golem(1);
    assertThat(behavior.act(silky, task(1, 64, 0))).isEqualTo(GolemActResult.DONE);

    assertThat(carriedCounts()).containsExactlyInAnyOrderEntriesOf(Map.of("COBBLESTONE", 1, "STONE", 1));
    verify(free, never()).wearTool();
    verify(silky, times(1)).wearTool();
  }

  @Test
  void fortuneAppliesAtTheLevelOfTheFortunePickaxeAndNotAtAllWithoutOne() {
    record = BreakerRules.toggleFortune(record);
    field.set(1, 64, 1, Id.minecraft("coal_ore"), BreakerRulesTest.placed(Id.minecraft("coal_ore")));
    field.set(1, 64, 0, Id.minecraft("coal_ore"), BreakerRulesTest.placed(Id.minecraft("coal_ore")));
    level(5);

    assertThat(behavior.act(golem(1), task(1, 64, 1))).isEqualTo(GolemActResult.DONE);
    inUse = pick(Id.minecraft("iron_pickaxe"), "+fortune3");
    assertThat(behavior.act(golem(1), task(1, 64, 0))).isEqualTo(GolemActResult.DONE);

    assertThat(carriedCounts()).containsExactlyEntriesOf(Map.of("COAL", 5));
  }

  @Test
  void aPlainBreakOfABlockAnIronPickaxeHarvestsNeedsNoToolAtAnyLevel() {
    stone(1, 64, 1);
    field.set(1, 64, 0, Id.minecraft("diamond_ore"), BreakerRulesTest.placed(Id.minecraft("diamond_ore")));
    level(5);

    assertThat(behavior.tools(golem(0), task(1, 64, 1)).needs()).isEmpty();
    assertThat(behavior.tools(golem(0), task(1, 64, 0)).needs()).isEmpty();
  }

  @Test
  void aBlockAboveIronTierNeedsTheCheapestPickaxeOfItsTierAndIsSkippedWithoutOne() {
    field.set(1, 64, 1, Id.minecraft("obsidian"), BreakerRulesTest.placed(Id.minecraft("obsidian")));
    level(5);

    GolemToolPlan plan = behavior.tools(golem(0), task(1, 64, 1));

    assertThat(plan.free()).isFalse();
    GolemToolNeed need = plan.needs().getFirst();
    assertThat(plan.needs()).hasSize(1);
    assertThat(need.suits().test(pick(Id.minecraft("iron_pickaxe"), ""))).isFalse();
    assertThat(need.suits().test(pick(Id.minecraft("diamond_pickaxe"), ""))).isTrue();
    assertThat(need.suits().test(pick(Id.minecraft("netherite_pickaxe"), ""))).isTrue();
    assertThat(need.note().key()).isEqualTo(GolemancyMessages.TOOLS_NEEDS_TIER);
    assertThat(need.note().tool()).isEqualTo("Diamond Pickaxe");
    ItemRef[] chest = {pick(Id.minecraft("netherite_pickaxe"), ""), pick(Id.minecraft("iron_pickaxe"), ""), pick(Id.minecraft("diamond_pickaxe"), "+silk")};
    assertThat(GolemToolRules.slot(chest, need)).isEqualTo(2);
    assertThat(GolemToolRules.choose(plan, null, ignored -> false).kind()).isEqualTo(GolemToolRules.Kind.MISSING);
  }

  @Test
  void aBlockAboveIronTierWearsThePickaxeAndDropsAsAPlainPickaxeOfItsMaterial() {
    field.set(1, 64, 1, Id.minecraft("obsidian"), BreakerRulesTest.placed(Id.minecraft("obsidian")));
    level(5);
    inUse = pick(Id.minecraft("diamond_pickaxe"), "+silk");
    GolemContext golem = golem(1);

    assertThat(behavior.act(golem, task(1, 64, 1))).isEqualTo(GolemActResult.DONE);

    assertThat(carriedCounts()).containsExactlyEntriesOf(Map.of("OBSIDIAN", 1));
    verify(golem, times(1)).wearTool();
    verify(core, atLeastOnce()).tool(Id.minecraft("diamond_pickaxe"));
  }

  @Test
  void silkModePrefersASilkTouchPickaxeThatHarvestsTheBlockAndOtherwiseBreaksFree() {
    record = BreakerRules.toggleSilk(record);
    field.set(1, 64, 1, Id.minecraft("diamond_ore"), BreakerRulesTest.placed(Id.minecraft("diamond_ore")));
    level(3);

    GolemToolPlan plan = behavior.tools(golem(0), task(1, 64, 1));

    assertThat(plan.free()).isTrue();
    assertThat(plan.needs()).singleElement().satisfies(need -> {
      assertThat(need.suits().test(pick(Id.minecraft("iron_pickaxe"), "+silk"))).isTrue();
      assertThat(need.suits().test(pick(Id.minecraft("stone_pickaxe"), "+silk"))).isFalse();
      assertThat(need.suits().test(pick(Id.minecraft("iron_pickaxe"), ""))).isFalse();
      assertThat(need.note().key()).isEqualTo(GolemancyMessages.TOOLS_NEEDS_SILK);
    });
  }

  @Test
  void silkModeAboveIronTierFallsBackToAPlainPickaxeOfTheTier() {
    record = BreakerRules.toggleSilk(record);
    field.set(1, 64, 1, Id.minecraft("obsidian"), BreakerRulesTest.placed(Id.minecraft("obsidian")));
    level(5);

    GolemToolPlan plan = behavior.tools(golem(0), task(1, 64, 1));

    assertThat(plan.free()).isFalse();
    assertThat(plan.needs()).hasSize(2);
    assertThat(plan.needs().get(0).suits().test(pick(Id.minecraft("diamond_pickaxe"), "+silk"))).isTrue();
    assertThat(plan.needs().get(0).suits().test(pick(Id.minecraft("iron_pickaxe"), "+silk"))).isFalse();
    assertThat(plan.needs().get(1).suits().test(pick(Id.minecraft("diamond_pickaxe"), ""))).isTrue();
    GolemToolRules.Choice fallback = GolemToolRules.choose(plan, pick(Id.minecraft("diamond_pickaxe"), ""), ignored -> false);
    assertThat(fallback.kind()).isEqualTo(GolemToolRules.Kind.HELD);
    assertThat(fallback.note().key()).isEqualTo(GolemancyMessages.TOOLS_NEEDS_SILK);
  }

  @Test
  void fortuneModeNeedsAFortunePickaxeThatHarvestsTheBlock() {
    record = BreakerRules.toggleFortune(record);
    field.set(1, 64, 1, Id.minecraft("coal_ore"), BreakerRulesTest.placed(Id.minecraft("coal_ore")));
    level(5);

    GolemToolPlan plan = behavior.tools(golem(0), task(1, 64, 1));

    assertThat(plan.free()).isTrue();
    assertThat(plan.needs()).singleElement().satisfies(need -> {
      assertThat(need.suits().test(pick(Id.minecraft("wooden_pickaxe"), "+fortune1"))).isTrue();
      assertThat(need.suits().test(pick(Id.minecraft("diamond_pickaxe"), "+silk"))).isFalse();
      assertThat(need.note().key()).isEqualTo(GolemancyMessages.TOOLS_NEEDS_FORTUNE);
    });
  }

  @Test
  void aBlockOutsideTheGolemsRegionNeedsNoToolYet() {
    field.set(1, 64, 1, Id.minecraft("obsidian"), BreakerRulesTest.placed(Id.minecraft("obsidian")));
    level(5);
    GolemContext golem = golem(0);
    when(golem.owns(any(Loc.class))).thenReturn(false);

    assertThat(behavior.tools(golem, task(1, 64, 1))).isEqualTo(GolemToolPlan.NONE);
  }

  @Test
  void theEntropyBonusYieldAppliesToTheDropsAndLeftoversFallAtTheBlock() {
    stone(1, 64, 1);
    roomLeft = 1;
    GolemContext golem = golem(1);
    when(golem.withBonusYield(anyList())).thenAnswer(invocation -> {
      List<ItemRef> drops = new ArrayList<>(invocation.<List<ItemRef>>getArgument(0));
      drops.add(GolemTestItems.stack(Id.minecraft("cobblestone"), 1));
      return drops;
    });

    assertThat(behavior.act(golem, task(1, 64, 1))).isEqualTo(GolemActResult.DONE);

    assertThat(carriedCounts()).containsExactlyEntriesOf(Map.of("COBBLESTONE", 1));
    verify(field.world).dropItemNaturally(any(Vec3.class), any(ItemRef.class));
  }

  @Test
  void entropyNeverDoublesPlacedBlocksOrDropsOfTheBrokenBlockItself() {
    level(5);
    record = record(true, Id.minecraft("diamond_block"), Id.minecraft("coal_ore"), Id.minecraft("stone"));
    field.set(1, 64, 1, Id.minecraft("diamond_block"), BreakerRulesTest.placed(Id.minecraft("diamond_block")));
    field.set(1, 64, 0, Id.minecraft("coal_ore"), BreakerRulesTest.placed(Id.minecraft("coal_ore")));
    field.set(0, 64, 1, Id.minecraft("coal_ore"), BreakerRulesTest.placed(Id.minecraft("coal_ore")));
    stone(-1, 64, 1);
    provenance.when(() -> XpProvenance.isPlayerPlaced(field.block(1, 64, 1))).thenReturn(true);
    provenance.when(() -> XpProvenance.isPlayerPlaced(field.block(1, 64, 0))).thenReturn(true);
    GolemContext golem = golem(1);
    when(golem.withBonusYield(anyList())).thenAnswer(invocation -> {
      List<ItemRef> drops = new ArrayList<>();
      for (ItemRef item : invocation.<List<ItemRef>>getArgument(0)) {
        drops.add(item);
        drops.add(GolemTestItems.stack(item.type(), item.amount()));
      }
      return drops;
    });

    assertThat(behavior.act(golem, task(1, 64, 1))).isEqualTo(GolemActResult.DONE);
    assertThat(behavior.act(golem, task(1, 64, 0))).isEqualTo(GolemActResult.DONE);
    assertThat(behavior.act(golem, task(0, 64, 1))).isEqualTo(GolemActResult.DONE);
    record = BreakerRules.toggleSilk(record);
    inUse = pick(Id.minecraft("netherite_pickaxe"), "+silk");
    assertThat(behavior.act(golem, task(-1, 64, 1))).isEqualTo(GolemActResult.DONE);

    assertThat(carriedCounts()).containsExactlyInAnyOrderEntriesOf(Map.of("DIAMOND_BLOCK", 1, "COAL", 3, "STONE", 1));
  }

  @Test
  void animusPlinthBlocksAreLeftAlone() {
    stone(1, 64, 1);
    stone(-1, 64, 1);
    PlinthService plinths = mock(PlinthService.class);
    when(plinths.isPlinthBlock(any(BlockRef.class))).thenAnswer(invocation -> invocation.getArgument(0) == field.block(1, 64, 1));
    runtime.when(GolemancyRuntime::plinths).thenReturn(Optional.of(plinths));

    behavior.scan(node(), budget(true));

    assertThat(jobs()).containsExactly("break -1,64,1");
    assertThat(behavior.act(golem(1), task(1, 64, 1))).isEqualTo(GolemActResult.FAILED);
    assertThat(field.type(1, 64, 1)).isEqualTo(Id.minecraft("stone"));
  }

  @Test
  void aWaterloggedBlockLeavesItsWaterBehind() {
    record = record(true, Id.minecraft("stone_slab"));
    BlockStateRef slab = mock(BlockStateRef.class);
    when(slab.placementItem()).thenReturn(Id.minecraft("stone_slab"));
    when(slab.property("waterlogged")).thenReturn("true");
    field.set(1, 64, 1, Id.minecraft("stone_slab"), slab);
    stone(1, 64, 0);

    assertThat(behavior.act(golem(1), task(1, 64, 1))).isEqualTo(GolemActResult.DONE);
    assertThat(behavior.act(golem(1), task(1, 64, 0))).isEqualTo(GolemActResult.FAILED);
    record = record(true, Id.minecraft("stone"));
    assertThat(behavior.act(golem(1), task(1, 64, 0))).isEqualTo(GolemActResult.DONE);

    assertThat(field.type(1, 64, 1)).isEqualTo(Id.minecraft("water"));
    assertThat(field.type(1, 64, 0)).isEqualTo(Id.minecraft("air"));
  }

  @Test
  void aBlockThatNoLongerMatchesOrSitsOutsideTheAreaFails() {
    field.set(1, 64, 1, Id.minecraft("dirt"), BreakerRulesTest.placed(Id.minecraft("dirt")));
    stone(3, 64, 3);

    assertThat(behavior.act(golem(1), task(1, 64, 1))).isEqualTo(GolemActResult.FAILED);
    assertThat(behavior.act(golem(1), task(3, 64, 3))).isEqualTo(GolemActResult.FAILED);

    assertThat(field.type(3, 64, 3)).isEqualTo(Id.minecraft("stone"));
    assertThat(carried).isEmpty();
  }

  @Test
  void anOwnerWithoutTheCoreFails() {
    stone(1, 64, 1);
    level(0);

    assertThat(behavior.act(golem(1), task(1, 64, 1))).isEqualTo(GolemActResult.FAILED);
  }

  @Test
  void protectionDenialBlocksTheBreakAndKeepsTheBlock() {
    stone(1, 64, 1);
    GolemContext golem = golem(1);
    when(golem.canBreak(any(Loc.class))).thenReturn(false);

    assertThat(behavior.act(golem, task(1, 64, 1))).isEqualTo(GolemActResult.BLOCKED);

    assertThat(field.type(1, 64, 1)).isEqualTo(Id.minecraft("stone"));
    assertThat(carried).isEmpty();
    verify(golem, never()).workXp(anyDouble());
  }

  @Test
  void theActionIntervalFollowsTheOwnersLevel() {
    level(1);
    assertThat(behavior.actIntervalTicks(golem(0), task(1, 64, 1))).isEqualTo(40);
    level(5);
    assertThat(behavior.actIntervalTicks(golem(0), task(1, 64, 1))).isEqualTo(10);
  }

  private void quarry() {
    field.set(0, 63, 0, Id.minecraft("cobblestone"), BreakerRulesTest.placed(Id.minecraft("cobblestone")));
    field.set(-1, 64, -1, Id.minecraft("cobblestone"), BreakerRulesTest.placed(Id.minecraft("cobblestone")));
    stone(1, 64, 1);
    field.set(0, 64, 1, Id.minecraft("dirt"), BreakerRulesTest.placed(Id.minecraft("dirt")));
    field.set(1, 64, -1, Id.minecraft("bedrock"), BreakerRulesTest.placed(Id.minecraft("bedrock")));
    field.set(-1, 64, 1, Id.minecraft("obsidian"), BreakerRulesTest.placed(Id.minecraft("obsidian")));
    field.set(1, 65, 0, Id.minecraft("chest"), BreakerRulesTest.placed(Id.minecraft("chest")));
    field.set(-1, 65, 0, Id.minecraft("iron_ore"), BreakerRulesTest.placed(Id.minecraft("iron_ore")));
    field.set(0, 64, -1, Id.minecraft("deepslate"), BreakerRulesTest.placed(Id.minecraft("deepslate")));
  }

  private void stone(int x, int y, int z) {
    field.set(x, y, z, Id.minecraft("stone"), BreakerRulesTest.placed(Id.minecraft("stone")));
  }

  private void level(int level) {
    lenient().when(core.ownerLevel(OWNER)).thenReturn(level);
  }

  private NodeContext node() {
    NodeContext node = mock(NodeContext.class);
    lenient().when(node.owner()).thenReturn(OWNER);
    lenient().when(node.key()).thenReturn(KEY);
    lenient().when(node.record()).thenAnswer(invocation -> record);
    lenient().when(node.world()).thenReturn(field.world);
    lenient().when(node.nowMillis()).thenReturn(NOW);
    lenient().when(node.owns(any(BlockRef.class))).thenReturn(true);
    lenient().when(node.block(anyInt())).thenAnswer(invocation -> {
      int[] at = NodeArea.position(KEY, record, invocation.getArgument(0));
      return Optional.of(field.block(at[0], at[1], at[2]));
    });
    lenient().when(node.tasks()).thenAnswer(invocation -> List.copyOf(posted));
    lenient().when(node.task(anyString(), any(GolemTarget.class)))
        .thenAnswer(invocation -> GolemTask.builder(KEY, NodeType.BREAKER, invocation.getArgument(0), invocation.getArgument(1)));
    lenient().when(node.post(any(GolemTask.class))).thenAnswer(invocation -> posted.add(invocation.getArgument(0)));
    lenient().when(node.withdraw(any(GolemTask.class))).thenAnswer(invocation -> posted.remove(invocation.<GolemTask>getArgument(0)));
    return node;
  }

  private ScanBudget budget(boolean room) {
    ScanBudget budget = mock(ScanBudget.class);
    lenient().when(budget.start()).thenReturn(0);
    lenient().when(budget.end()).thenReturn(NodeArea.volume(record));
    lenient().when(budget.indices()).thenReturn(IntStream.range(0, NodeArea.volume(record)).toArray());
    lenient().when(budget.hasTaskRoom()).thenReturn(room);
    return budget;
  }

  private GolemContext golem(int actIndex) {
    GolemContext golem = mock(GolemContext.class);
    PlayerRef owner = mock(PlayerRef.class);
    lenient().when(owner.uuid()).thenReturn(OWNER);
    MobRef body = mock(MobRef.class);
    lenient().when(body.location()).thenReturn(new Loc(field.world, 0.5D, 64D, 0.5D));
    lenient().when(golem.owner()).thenReturn(owner);
    lenient().when(golem.golem()).thenReturn(body);
    lenient().when(golem.node()).thenAnswer(invocation -> Optional.of(record));
    lenient().when(golem.world()).thenReturn(field.world);
    lenient().when(golem.nowMillis()).thenReturn(NOW);
    lenient().when(golem.actIndex()).thenReturn(actIndex);
    lenient().when(golem.owns(any(Loc.class))).thenReturn(true);
    lenient().when(golem.canBreak(any(Loc.class))).thenReturn(true);
    lenient().when(golem.withBonusYield(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    lenient().when(golem.toolInUse()).thenAnswer(invocation -> Optional.ofNullable(inUse));
    lenient().when(golem.carry(any(ItemRef.class))).thenAnswer(invocation -> {
      ItemRef item = invocation.getArgument(0);
      if (roomLeft <= 0) {
        return Optional.of(item);
      }
      roomLeft -= item.amount();
      carried.add(item);
      return Optional.empty();
    });
    lenient().doAnswer(invocation -> xp.add(invocation.getArgument(0))).when(golem).workXp(anyDouble());
    return golem;
  }

  private ItemRef pick(Id type, String enchant) {
    ItemRef tool = GolemTestItems.stack(type, 1);
    tools.put(tool, type + enchant);
    return tool;
  }

  private int fortuneOf(ItemRef tool) {
    String label = tools.getOrDefault(tool, "");
    int at = label.indexOf("+fortune");
    return at < 0 ? 0 : Integer.parseInt(label.substring(at + "+fortune".length()));
  }

  private static NodeRecord record(boolean whitelist, Id... filter) {
    List<ItemRef> items = new ArrayList<>(filter.length);
    for (Id type : filter) {
      items.add(GolemTestItems.stack(type, 1));
    }
    return NodeRecord.builder(OWNER, NodeType.BREAKER, BlockFace.UP).area(NodeCuboid.symmetric(2, 1, 2)).filterItems(items).whitelist(whitelist).build();
  }

  private static GolemTask task(int x, int y, int z) {
    return GolemTask.builder(KEY, NodeType.BREAKER, "break", GolemTarget.block(CropField.WORLD, x, y, z)).build();
  }

  private List<String> jobs() {
    List<String> lines = new ArrayList<>();
    for (GolemTask task : posted) {
      GolemTarget target = task.target();
      lines.add(task.type() + " " + target.blockX() + "," + target.blockY() + "," + target.blockZ());
    }
    lines.sort(null);
    return lines;
  }

  private Map<String, Integer> carriedCounts() {
    Map<String, Integer> counts = new TreeMap<>();
    for (ItemRef item : carried) {
      counts.merge(item.type().legacyName(), item.amount(), Integer::sum);
    }
    return counts;
  }
}
