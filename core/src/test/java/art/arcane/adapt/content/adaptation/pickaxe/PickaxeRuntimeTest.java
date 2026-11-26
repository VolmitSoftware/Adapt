package art.arcane.adapt.content.adaptation.pickaxe;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.ViewerDisplayPort;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillPickaxes;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDamageEvent;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerItemDamageEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeItemEntity;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PickaxeRuntimeTest {
  @TempDir
  Path data;

  private TestPlatform platform;
  private FakeWorld world;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    world = platform.world();
    player = platform.player("Miner", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_pickaxe", 250));
    adapt = TestAdapt.start(platform, List.of(SkillPickaxes.class));
    profile = adapt.join(player);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    Skill<?> skill = SkillRegistry.skills.get("pickaxe");
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (type.isInstance(adaptation)) {
        profile.getData().getSkillLine("pickaxe").setAdaptation(adaptation, level);
        return type.cast(adaptation);
      }
    }
    throw new AssertionError("No adaptation " + type.getSimpleName());
  }

  private double stat(String key) {
    return profile.getData().getStat(key);
  }

  private BlockBreakEvent breakBlock(BlockRef block) {
    return adapt.post(new BlockBreakEvent(player, block, true, 0));
  }

  private BlockDropItemEvent drops(BlockRef block, String brokenType, Id spawnerType, ItemRef... items) {
    List<ItemEntityRef> entities = new ArrayList<>();
    for (ItemRef item : items) {
      entities.add(world.createItemEntity(block.center().position(), item));
    }
    return new BlockDropItemEvent(player, block, FakeBlockState.of(brokenType), entities, spawnerType);
  }

  @Test
  void skillCountsBrokenOresAndTheirValue() {
    BlockRef ore = world.set(0, 60, 0, "iron_ore").blockAt(0, 60, 0);

    breakBlock(ore);

    assertThat(stat("pickaxe.blocks.broken")).isEqualTo(1D);
    assertThat(stat("pickaxe.ores")).isEqualTo(1D);
    assertThat(stat("pickaxe.blocks.value")).isPositive();
  }

  @Test
  void skillIgnoresBreaksWithoutAPickaxe() {
    player.holding(FakeItem.of("stick"));

    breakBlock(world.set(0, 60, 0, "iron_ore").blockAt(0, 60, 0));

    assertThat(stat("pickaxe.blocks.broken")).isZero();
  }

  @Test
  void stoneSkinBuildsResistanceTiersFromStoneBreaks() {
    learn(PickaxeStoneSkin.class, 2);
    BlockRef stone = world.set(0, 60, 0, "stone").blockAt(0, 60, 0);

    for (int i = 0; i < 4; i++) {
      breakBlock(stone);
    }
    assertThat(player.effect(Id.minecraft("resistance")).amplifier()).isZero();

    for (int i = 0; i < 4; i++) {
      breakBlock(stone);
    }
    assertThat(player.effect(Id.minecraft("resistance")).amplifier()).isEqualTo(1);
    assertThat(stat("pickaxe.stone-skin.stacks-gained")).isEqualTo(8D);
  }

  @Test
  void unbreakablePactKeepsTheLastDurabilityPoint() {
    learn(PickaxeUnbreakablePact.class, 1);
    FakeItem pickaxe = FakeItem.tool("iron_pickaxe", 250).withDamage(249);
    player.holding(pickaxe);

    PlayerItemDamageEvent event = adapt.post(new PlayerItemDamageEvent(player, pickaxe, 1, 1));

    assertThat(event.isCancelled()).isTrue();
    assertThat(pickaxe.damage()).isEqualTo(249);
  }

  @Test
  void autosmeltTurnsTheNativeRawDropIntoIngots() {
    learn(PickaxeAutosmelt.class, 1);
    BlockRef ore = world.blockAt(0, 60, 0);

    BlockDropItemEvent event = adapt.post(drops(ore, "iron_ore", null, FakeItem.of("raw_iron")));

    assertThat(event.items()).hasSize(1);
    assertThat(event.items().getFirst().item().type()).isEqualTo(Id.minecraft("iron_ingot"));
    assertThat(stat("pickaxe.autosmelt.ores-smelted")).isEqualTo(1D);
  }

  @Test
  void autosmeltLeavesSilkTouchDropsAlone() {
    learn(PickaxeAutosmelt.class, 1);
    player.holding(FakeItem.tool("iron_pickaxe", 250).enchant("silk_touch", 1));

    BlockDropItemEvent event = adapt.post(drops(world.blockAt(0, 60, 0), "iron_ore", null, FakeItem.of("raw_iron")));

    assertThat(event.items().getFirst().item().type()).isEqualTo(Id.minecraft("raw_iron"));
  }

  @Test
  void dropToInventoryCatchesSmeltedDropsAfterAutosmelt() {
    learn(PickaxeAutosmelt.class, 1);
    learn(PickaxeDropToInventory.class, 1);

    BlockDropItemEvent event = adapt.post(drops(world.blockAt(0, 60, 0), "iron_ore", null, FakeItem.of("raw_iron")));

    assertThat(event.items()).isEmpty();
    assertThat(player.inventory().item(1).type()).isEqualTo(Id.minecraft("iron_ingot"));
    assertThat(stat("pickaxe.drop-to-inv.items-caught")).isEqualTo(1D);
  }

  @Test
  void silkSpawnerDropsTheSpawnerWithItsMobAndCommitsAtMonitor() {
    learn(PickaxeSilkSpawner.class, 2);
    player.setSneaking(true);
    BlockRef spawner = world.blockAt(0, 60, 0);

    BlockDropItemEvent event = adapt.post(drops(spawner, "spawner", Id.minecraft("zombie")));

    assertThat(event.items()).hasSize(1);
    FakeItemEntity drop = (FakeItemEntity) event.items().getFirst();
    assertThat(((FakeItem) drop.item()).spawnedType()).isEqualTo(Id.minecraft("zombie"));
    assertThat(drop.owner()).isEqualTo(player.uuid());
    assertThat(stat("pickaxe.silk-spawner.spawners-collected")).isEqualTo(1D);
  }

  @Test
  void silkSpawnerCommitsNothingWhenTheDropIsCancelledAfterPreparing() {
    learn(PickaxeSilkSpawner.class, 2);
    player.setSneaking(true);
    BlockDropItemEvent event = drops(world.blockAt(0, 60, 0), "spawner", Id.minecraft("zombie"));

    adapt.bus().post(event, EventPriority.HIGH);
    event.setCancelled(true);
    adapt.bus().post(event, EventPriority.MONITOR);

    assertThat(event.items()).hasSize(1);
    assertThat(stat("pickaxe.silk-spawner.spawners-collected")).isZero();
  }

  @Test
  void veinminerBreaksTheConnectedVeinAcrossDeepslateOnTheNextTick() {
    learn(PickaxeVeinminer.class, 1);
    player.setSneaking(true);
    world.set(0, 60, 0, "iron_ore").set(1, 60, 0, "iron_ore").set(1, 61, 0, "iron_ore")
        .set(2, 61, 0, "deepslate_iron_ore").set(0, 60, 2, "gold_ore");
    BlockRef origin = world.blockAt(0, 60, 0);

    breakBlock(origin);
    origin.setType(Id.minecraft("air"), false);
    platform.scheduler().advance(1);

    assertThat(platform.protection().broken()).hasSize(3);
    assertThat(world.blockAt(2, 61, 0).isAir()).isTrue();
    assertThat(world.blockAt(0, 60, 2).type()).isEqualTo(Id.minecraft("gold_ore"));
    assertThat(stat("pickaxe.veinminer.ores-veinmined")).isEqualTo(4D);
  }

  @Test
  void veinminerWaitsWhenTheOriginWasNotBroken() {
    learn(PickaxeVeinminer.class, 1);
    player.setSneaking(true);
    world.set(0, 60, 0, "iron_ore").set(1, 60, 0, "iron_ore");

    breakBlock(world.blockAt(0, 60, 0));
    platform.scheduler().advance(1);

    assertThat(platform.protection().broken()).isEmpty();
  }

  @Test
  void tunnelBoreBreaksTheBlockAboveAtLevelOne() {
    learn(PickaxeTunnelBore.class, 1);
    player.setSneaking(true);
    world.set(0, 60, 1, "stone").set(0, 61, 1, "stone").set(1, 60, 1, "stone");
    BlockRef origin = world.blockAt(0, 60, 1);

    breakBlock(origin);
    origin.setType(Id.minecraft("air"), false);
    platform.scheduler().advance(1);

    assertThat(world.blockAt(0, 61, 1).isAir()).isTrue();
    assertThat(world.blockAt(1, 60, 1).type()).isEqualTo(Id.minecraft("stone"));
    assertThat(stat("pickaxe.tunnel-bore.blocks-bored")).isEqualTo(1D);
  }

  @Test
  void tunnelBoreFacingMatchesVanillaYaw() {
    assertThat(PickaxeBore.facing(0F).dz()).isEqualTo(1);
    assertThat(PickaxeBore.facing(90F).dx()).isEqualTo(-1);
    assertThat(PickaxeBore.facing(180F).dz()).isEqualTo(-1);
    assertThat(PickaxeBore.facing(-90F).dx()).isEqualTo(1);
  }

  @Test
  void quarrySenseScansOverTicksAndRevealsOresPrivately() {
    learn(PickaxeQuarrySense.class, 1);
    player.setSneaking(true);
    RecordingDisplays displays = new RecordingDisplays();
    AdaptPorts.install(ViewerDisplayPort.class, displays);
    world.set(3, 60, 0, "diamond_ore").set(-2, 61, 1, "stone");

    PlayerInteractEvent event = adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_AIR, Hand.MAIN_HAND,
        player.inventory().mainHand(), null, null, null, UseResult.DEFAULT, UseResult.DEFAULT));

    assertThat(event.isCancelled()).isTrue();
    adapt.awaitTicks(() -> !displays.shown.isEmpty(), 5_000L);
    assertThat(displays.shown).containsExactly("ore-3:60:0");
    assertThat(player.hasCooldown(Id.minecraft("iron_pickaxe"))).isTrue();
    assertThat(player.inventory().mainHand().damage()).isPositive();
    assertThat(stat("pickaxe.quarry-sense.scans")).isEqualTo(1D);
  }

  @Test
  void deepCoreSpeedsUpDeepslateMining() {
    learn(PickaxeDeepCore.class, 1);
    BlockRef deepslate = world.set(0, 60, 0, "deepslate").blockAt(0, 60, 0);
    double before = player.attributeValue(Attributes.BLOCK_BREAK_SPEED);

    adapt.post(new BlockDamageEvent(player, deepslate, player.inventory().mainHand(), false));
    adapt.awaitTicks(() -> player.attributeValue(Attributes.BLOCK_BREAK_SPEED) > before, 2_000L);

    assertThat(player.attributeValue(Attributes.BLOCK_BREAK_SPEED)).isGreaterThan(before);
  }

  @Test
  void unlearnedAdaptationsStayInert() {
    world.set(0, 60, 0, "iron_ore").set(1, 60, 0, "iron_ore");
    player.setSneaking(true);

    breakBlock(world.blockAt(0, 60, 0));
    world.blockAt(0, 60, 0).setType(Id.minecraft("air"), false);
    platform.scheduler().advance(1);

    assertThat(platform.protection().broken()).isEmpty();
    assertThat(player.effects()).isEmpty();
  }

  private static final class RecordingDisplays implements ViewerDisplayPort {
    private final List<String> shown = new ArrayList<>();

    @Override
    public boolean showBlock(String channel, String key, PlayerRef viewer, Loc location, BlockStateRef block,
                             Rgb glowColor, int durationTicks) {
      shown.add(key);
      return true;
    }

    @Override
    public boolean showLine(String channel, String key, PlayerRef viewer, Loc start, Loc end, BlockStateRef block,
                            Rgb glowColor, double thickness, int durationTicks) {
      shown.add(key);
      return true;
    }

    @Override
    public boolean showCuboid(String channel, String key, PlayerRef viewer, Loc minimum, Vec3 size,
                              BlockStateRef block, Rgb glowColor, boolean inside, int durationTicks) {
      shown.add(key);
      return true;
    }

    @Override
    public boolean showPersistentBlock(String channel, String key, PlayerRef viewer, Loc location,
                                       BlockStateRef block, Rgb glowColor) {
      shown.add(key);
      return true;
    }

    @Override
    public boolean isShowing(String channel, String key, UUID viewerId, Loc location) {
      return shown.contains(key);
    }

    @Override
    public void clearViewer(String channel, UUID viewerId) {
    }

    @Override
    public void clearViewerKey(String channel, String key, UUID viewerId) {
    }

    @Override
    public void clearChannel(String channel) {
    }
  }
}
