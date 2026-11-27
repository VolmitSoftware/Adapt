package art.arcane.adapt.content.adaptation.pickaxe.upgraded;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.world.BonusBreaks;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.protection.ProtectionCheck;
import art.arcane.adapt.api.protection.Protector;
import art.arcane.adapt.engine.protection.ProtectionProbes;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.engine.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeAutosmelt;
import art.arcane.adapt.content.skill.SkillPickaxes;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.player.PlayerItemDamageEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
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
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

class PickaxeUpgradesRuntimeTest {
  private static final Id AIR = Id.minecraft("air");
  private static final Id RESISTANCE = Id.minecraft("resistance");

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
    player = platform.player("Delver", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("diamond_pickaxe", 1561));
    adapt = TestAdapt.start(platform, List.of(SkillPickaxes.class, SkillStealth.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void motherlodeBreaksTheWholeVeinWithoutSneakingAndDropsASmeltedBonusOre() {
    mutate();
    learn(Motherlode.class, 1);
    world.set(0, 60, 0, "iron_ore").set(1, 60, 0, "iron_ore").set(1, 61, 0, "deepslate_iron_ore");
    BlockRef origin = world.blockAt(0, 60, 0);

    breakBlock(origin);
    origin.setType(AIR, false);
    platform.scheduler().advance(1);

    assertThat(world.blockAt(1, 60, 0).isAir()).isTrue();
    assertThat(world.blockAt(1, 61, 0).isAir()).isTrue();
    assertThat(world.items()).extracting((FakeItemEntity item) -> item.item().type())
        .contains(Id.minecraft("iron_ingot"));
    assertThat(stat("pickaxe.motherlode.ores-mined")).isEqualTo(3D);
  }

  @Test
  void motherlodeDropsNoBonusOreForAnOreThePlayerPlaced() {
    mutate();
    learn(Motherlode.class, 1);
    BlockRef origin = world.set(0, 60, 0, "diamond_ore").blockAt(0, 60, 0);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(any(BlockRef.class))).thenReturn(true);
      breakBlock(origin);
    }
    origin.setType(AIR, false);
    platform.scheduler().advance(1);

    assertThat(world.items()).isEmpty();
  }

  @Test
  void motherlodeDropsNoBonusOreForALoneOre() {
    mutate();
    learn(Motherlode.class, 1);
    BlockRef origin = world.set(0, 60, 0, "diamond_ore").blockAt(0, 60, 0);

    breakBlock(origin);
    origin.setType(AIR, false);
    platform.scheduler().advance(1);

    assertThat(world.items()).isEmpty();
  }

  @Test
  void motherlodeDropsNoBonusOreWithSilkTouch() {
    mutate();
    learn(Motherlode.class, 1);
    player.holding(FakeItem.tool("diamond_pickaxe", 1561).enchant("silk_touch", 1));
    world.set(0, 60, 0, "iron_ore").set(1, 60, 0, "iron_ore").set(1, 61, 0, "iron_ore");
    BlockRef origin = world.blockAt(0, 60, 0);

    breakBlock(origin);
    origin.setType(AIR, false);
    platform.scheduler().advance(1);

    assertThat(world.blockAt(1, 61, 0).isAir()).isTrue();
    assertThat(world.items()).extracting((FakeItemEntity item) -> item.item().type())
        .doesNotContain(Id.minecraft("iron_ingot"));
  }

  @Test
  void motherlodeDropsNoBonusOreWhenPlacedOresMakeUpTheVein() {
    mutate();
    learn(Motherlode.class, 1);
    world.set(0, 60, 0, "iron_ore").set(1, 60, 0, "iron_ore").set(1, 61, 0, "iron_ore");
    BlockRef origin = world.blockAt(0, 60, 0);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(argThat((BlockRef block) -> block.x() == 1))).thenReturn(true);
      breakBlock(origin);
    }
    origin.setType(AIR, false);
    platform.scheduler().advance(1);

    assertThat(world.blockAt(1, 61, 0).isAir()).isTrue();
    assertThat(world.items()).extracting((FakeItemEntity item) -> item.item().type())
        .doesNotContain(Id.minecraft("iron_ingot"));
  }

  @Test
  void motherlodeSmeltsTheVeinDropsThatTheKeptDropToInventoryCollects() {
    mutate();
    learn(Motherlode.class, 1);

    BlockDropItemEvent event = adapt.post(drops(world.blockAt(0, 60, 0), "iron_ore", FakeItem.of("raw_iron")));

    assertThat(event.items()).isEmpty();
    assertThat(player.inventory().contains(Id.minecraft("iron_ingot"), 1)).isTrue();
    assertThat(player.inventory().contains(Id.minecraft("raw_iron"), 1)).isFalse();
  }

  @Test
  void motherlodeRollsNoExtraIngotForAPlacedOre() {
    mutate();
    learn(Motherlode.class, 3);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS);
         MockedStatic<PickaxeAutosmelt> smelt = mockStatic(PickaxeAutosmelt.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(any(BlockRef.class))).thenReturn(true);
      adapt.post(drops(world.blockAt(0, 60, 0), "iron_ore", FakeItem.of("raw_iron")));

      smelt.verify(() -> PickaxeAutosmelt.replaceRawDrops(any(BlockDropItemEvent.class), any(Id.class),
          any(Adaptation.class), eq(0)));
    }
  }

  @Test
  void motherlodeDoesNothingForAPlayerWhoHasNotMutated() {
    profile.getData().getSkillLine("pickaxe").setAdaptation(upgrade(Motherlode.class), 1);
    world.set(0, 60, 0, "iron_ore").set(1, 60, 0, "iron_ore");
    BlockRef origin = world.blockAt(0, 60, 0);

    breakBlock(origin);
    origin.setType(AIR, false);
    platform.scheduler().advance(1);
    BlockDropItemEvent event = adapt.post(drops(origin, "iron_ore", FakeItem.of("raw_iron")));

    assertThat(world.blockAt(1, 60, 0).type()).isEqualTo(Id.minecraft("iron_ore"));
    assertThat(event.items().getFirst().item().type()).isEqualTo(Id.minecraft("raw_iron"));
  }

  @Test
  void earthshakerBoresTheThreeByThreeFaceThroughStoneDeepslateAndObsidian() {
    mutate();
    learn(Earthshaker.class, 1);
    world.set(-1, 59, 2, "stone").set(0, 59, 2, "deepslate").set(1, 59, 2, "obsidian")
        .set(-1, 60, 2, "tuff").set(0, 60, 2, "stone").set(1, 60, 2, "andesite")
        .set(-1, 61, 2, "stone").set(0, 61, 2, "cobbled_deepslate").set(1, 61, 2, "dirt");
    BlockRef origin = world.blockAt(0, 60, 2);

    breakBlock(origin);
    origin.setType(AIR, false);
    platform.scheduler().advance(1);

    assertThat(world.blockAt(1, 59, 2).isAir()).isTrue();
    assertThat(world.blockAt(0, 59, 2).isAir()).isTrue();
    assertThat(world.blockAt(1, 61, 2).type()).isEqualTo(Id.minecraft("dirt"));
    assertThat(stat("pickaxe.earthshaker.blocks-bored")).isEqualTo(7D);
  }

  @Test
  void earthshakerClosesTheGapToStoneSpeed() {
    assertThat(Earthshaker.stoneSpeedBonus(1.5F)).isZero();
    assertThat(Earthshaker.stoneSpeedBonus(3.0F)).isCloseTo(1.0D, within(1.0E-9D));
    assertThat(Earthshaker.stoneSpeedBonus(50F)).isCloseTo((50D / 1.5D) - 1D, within(1.0E-6D));
  }

  @Test
  void eternalPickKeepsTheLastPointAndHardensWhileMining() {
    mutate();
    learn(EternalPick.class, 1);
    FakeItem pickaxe = FakeItem.tool("diamond_pickaxe", 1561).withDamage(1560);
    player.holding(pickaxe);

    PlayerItemDamageEvent wear = adapt.post(new PlayerItemDamageEvent(player, pickaxe, 1, 1));
    BlockRef stone = world.set(0, 60, 0, "stone").blockAt(0, 60, 0);
    for (int i = 0; i < 4; i++) {
      breakBlock(stone);
    }

    assertThat(wear.isCancelled()).isTrue();
    assertThat(pickaxe.damage()).isEqualTo(1560);
    assertThat(player.effect(RESISTANCE)).isNotNull();
  }

  @Test
  void eternalPickRepairsThePickaxeAsYouMine() {
    mutate();
    EternalPick eternal = learn(EternalPick.class, 1);
    eternal.getConfig().repairChanceBase = 1D;
    FakeItem pickaxe = FakeItem.tool("diamond_pickaxe", 1561).withDamage(100);
    player.holding(pickaxe);

    breakBlock(world.set(0, 60, 0, "stone").blockAt(0, 60, 0));
    platform.scheduler().advance(1);

    assertThat(player.inventory().mainHand().damage()).isEqualTo(100 - eternal.getConfig().restorePerRepair);
  }

  @Test
  void eternalPickDoesNotRepairOnBlocksThePlayerPlaced() {
    mutate();
    EternalPick eternal = learn(EternalPick.class, 1);
    eternal.getConfig().repairChanceBase = 1D;
    FakeItem pickaxe = FakeItem.tool("diamond_pickaxe", 1561).withDamage(100);
    player.holding(pickaxe);
    BlockRef stone = world.set(0, 60, 0, "stone").blockAt(0, 60, 0);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(any(BlockRef.class))).thenReturn(true);
      breakBlock(stone);
    }
    platform.scheduler().advance(1);

    assertThat(player.inventory().mainHand().damage()).isEqualTo(100);
  }

  @Test
  void eternalPickRepairsOnceForAnEarthshakerSwing() {
    mutate();
    EternalPick eternal = learn(EternalPick.class, 1);
    learn(Earthshaker.class, 1);
    eternal.getConfig().repairChanceBase = 1D;
    FakeItem pickaxe = FakeItem.tool("diamond_pickaxe", 1561).withDamage(100);
    player.holding(pickaxe);
    for (int x = -1; x <= 1; x++) {
      for (int y = 59; y <= 61; y++) {
        world.set(x, y, 2, "stone");
      }
    }
    BlockRef origin = world.blockAt(0, 60, 2);

    breakBlock(origin);
    origin.setType(AIR, false);
    platform.scheduler().advance(2);

    assertThat(world.blockAt(1, 59, 2).isAir()).isTrue();
    assertThat(stat("pickaxe.eternal-pick.durability-restored")).isEqualTo(eternal.getConfig().restorePerRepair);
  }

  @Test
  void eternalPickBuildsNoResistanceFromPlacedBlocks() {
    mutate();
    learn(EternalPick.class, 1);
    BlockRef stone = world.set(0, 60, 0, "stone").blockAt(0, 60, 0);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(any(BlockRef.class))).thenReturn(true);
      for (int i = 0; i < 4; i++) {
        breakBlock(stone);
      }
    }

    assertThat(player.effect(RESISTANCE)).isNull();
  }

  @Test
  void eternalPickLetsThePickaxeBreakWithoutAMutation() {
    profile.getData().getSkillLine("pickaxe").setAdaptation(upgrade(EternalPick.class), 1);
    FakeItem pickaxe = FakeItem.tool("diamond_pickaxe", 1561).withDamage(1560);
    player.holding(pickaxe);

    PlayerItemDamageEvent wear = adapt.post(new PlayerItemDamageEvent(player, pickaxe, 1, 1));

    assertThat(wear.isCancelled()).isFalse();
  }

  @Test
  void motherlodeKeepsAMinedSpawnerFromLevelTwoForPickaxeDurability() {
    mutate();
    Motherlode motherlode = learn(Motherlode.class, 2);
    FakeItem pickaxe = FakeItem.tool("diamond_pickaxe", 1561);
    player.holding(pickaxe);
    player.setSneaking(true);

    adapt.post(spawnerDrops(world.blockAt(0, 60, 0)));

    FakeItem spawner = (FakeItem) keptSpawner();
    assertThat(spawner.spawnedType()).isEqualTo(Id.minecraft("zombie"));
    assertThat(spawner.lore()).isEmpty();
    assertThat(pickaxe.damage()).isEqualTo(motherlode.getConfig().spawnerDurability);
    assertThat(stat("pickaxe.motherlode.spawners-kept")).isEqualTo(1D);
  }

  @Test
  void motherlodeKeepsNoSpawnerAtLevelOneOrWithoutTheMutation() {
    player.setSneaking(true);
    profile.getData().getSkillLine("pickaxe").setAdaptation(upgrade(Motherlode.class), 3);
    adapt.post(spawnerDrops(world.blockAt(0, 60, 0)));
    mutate();
    learn(Motherlode.class, 1);
    adapt.post(spawnerDrops(world.blockAt(0, 60, 0)));

    assertThat(keptSpawner()).isNull();
    assertThat(player.inventory().mainHand().damage()).isZero();
  }

  @Test
  void motherlodeLeavesSilkTouchSpawnersToSilkSpawnerAndChargesNothing() {
    mutate();
    learn(Motherlode.class, 3);
    FakeItem pickaxe = FakeItem.tool("diamond_pickaxe", 1561).enchant("silk_touch", 1);
    player.holding(pickaxe);
    player.setSneaking(true);

    adapt.post(spawnerDrops(world.blockAt(0, 60, 0)));

    assertThat(pickaxe.damage()).isZero();
    assertThat(stat("pickaxe.motherlode.spawners-kept")).isZero();
  }

  @Test
  void aWornPickaxeStopsOnlyASneakingKeepSoTheSpawnerCanStillBeDestroyed() {
    mutate();
    learn(Motherlode.class, 2);
    FakeItem pickaxe = FakeItem.tool("diamond_pickaxe", 1561).withDamage(1400);
    player.holding(pickaxe);
    BlockRef spawner = world.set(0, 60, 0, "spawner").blockAt(0, 60, 0);

    player.setSneaking(true);
    BlockBreakEvent kept = breakBlock(spawner);
    adapt.post(spawnerDrops(spawner));
    player.setSneaking(false);
    BlockBreakEvent destroyed = breakBlock(spawner);
    adapt.post(spawnerDrops(spawner));

    assertThat(kept.isCancelled()).isTrue();
    assertThat(destroyed.isCancelled()).isFalse();
    assertThat(keptSpawner()).isNull();
    assertThat(pickaxe.damage()).isEqualTo(1400);
  }

  @Test
  void motherlodeKeepsNoSpawnerWithoutSneakingAndChargesNothing() {
    mutate();
    learn(Motherlode.class, 3);
    FakeItem pickaxe = FakeItem.tool("diamond_pickaxe", 1561);
    player.holding(pickaxe);

    adapt.post(spawnerDrops(world.blockAt(0, 60, 0)));

    assertThat(keptSpawner()).isNull();
    assertThat(pickaxe.damage()).isZero();
  }

  @Test
  void aKeptSpawnerAndAPlacedSpawnerDropNoXp() {
    mutate();
    learn(Motherlode.class, 2);
    BlockRef spawner = world.set(0, 60, 0, "spawner").blockAt(0, 60, 0);

    BlockBreakEvent natural = adapt.post(new BlockBreakEvent(player, spawner, true, 30));
    player.setSneaking(true);
    BlockBreakEvent kept = adapt.post(new BlockBreakEvent(player, spawner, true, 30));
    player.setSneaking(false);
    BlockBreakEvent placed;
    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(any(BlockRef.class))).thenReturn(true);
      placed = adapt.post(new BlockBreakEvent(player, spawner, true, 30));
    }

    assertThat(natural.experience()).isEqualTo(30);
    assertThat(kept.experience()).isZero();
    assertThat(placed.experience()).isZero();
  }

  @Test
  void aSpawnerDropCancelledByAnotherPluginLeavesNothingPending() throws ReflectiveOperationException {
    mutate();
    Motherlode motherlode = learn(Motherlode.class, 2);
    player.setSneaking(true);
    adapt.bus().register(this, BlockDropItemEvent.class, EventPriority.HIGH,
        (BlockDropItemEvent e) -> e.setCancelled(true));
    adapt.post(spawnerDrops(world.blockAt(0, 60, 0)));
    adapt.bus().unregisterAll(this);

    assertThat(pendingSpawners(motherlode)).isEmpty();
    assertThat(player.inventory().mainHand().damage()).isZero();
  }

  @Test
  void aLevelThreeSpawnerNamesItsMobAndIsPlacedOnlyWhereThePlacerMayBuild() {
    mutate();
    learn(Motherlode.class, 3);
    player.setSneaking(true);
    adapt.post(spawnerDrops(world.blockAt(0, 60, 0)));
    ItemRef spawner = keptSpawner();
    BlockRef target = world.blockAt(2, 61, 0);

    BlockPlaceEvent allowed = adapt.post(new BlockPlaceEvent(player, target, target.state(), target, spawner,
        Hand.MAIN_HAND));
    BlockPlaceEvent denied;
    BlockPlaceEvent plain;
    Protector protector = new Claim();
    AdaptApi.protection().register(protector);
    try {
      denied = adapt.post(new BlockPlaceEvent(player, target, target.state(), target, spawner, Hand.MAIN_HAND));
      plain = adapt.post(new BlockPlaceEvent(player, target, target.state(), target, FakeItem.of("spawner"),
          Hand.MAIN_HAND));
    } finally {
      AdaptApi.protection().unregister(protector);
    }

    assertThat(spawner.lore()).anySatisfy((String line) -> assertThat(line).contains("Zombie"));
    assertThat(allowed.isCancelled()).isFalse();
    assertThat(denied.isCancelled()).isTrue();
    assertThat(plain.isCancelled()).isFalse();
  }

  @Test
  void aWardedSpawnerPlacementFiresNoSecondProbeAndLeavesProbesToProtectionPlugins() {
    mutate();
    learn(Motherlode.class, 3);
    player.setSneaking(true);
    adapt.post(spawnerDrops(world.blockAt(0, 60, 0)));
    ItemRef spawner = keptSpawner();
    BlockRef target = world.blockAt(2, 61, 0);

    BlockPlaceEvent placed;
    try (MockedStatic<ProtectionProbes> probes = mockStatic(ProtectionProbes.class, CALLS_REAL_METHODS)) {
      placed = adapt.post(new BlockPlaceEvent(player, target, target.state(), target, spawner, Hand.MAIN_HAND));
      probes.verify(() -> ProtectionProbes.attemptBlockPlaceProbe(any(), any()), never());
    }
    platform.protection().deny(true);
    BlockPlaceEvent probe = new BlockPlaceEvent(player, target, target.state(), target, spawner, Hand.MAIN_HAND);
    probe.markProtectionProbe();
    adapt.post(probe);

    assertThat(placed.isCancelled()).isFalse();
    assertThat(probe.isCancelled()).isFalse();
  }

  @Test
  void motherlodeVeinsIgnoreBonusBreaksSoAChargeCannotStackBonusOres() {
    mutate();
    learn(Motherlode.class, 1);
    world.set(0, 60, 0, "iron_ore").set(1, 60, 0, "iron_ore").set(1, 61, 0, "iron_ore");
    BlockRef origin = world.blockAt(0, 60, 0);

    BonusBreaks.breakBlock(player, origin, 0);
    platform.scheduler().advance(1);

    assertThat(world.blockAt(1, 60, 0).type()).isEqualTo(Id.minecraft("iron_ore"));
    assertThat(stat("pickaxe.motherlode.ores-mined")).isZero();
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("pickaxe", "stealth")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("pickaxe"), registry.getSkill("stealth"), "Delver"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = upgrade(type);
    profile.getData().getSkillLine("pickaxe").setAdaptation(upgrade, level);
    return upgrade;
  }

  private <A extends Adaptation<?>> A upgrade(Class<A> type) {
    return SkillHost.of(registry.getSkill("pickaxe")).getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
  }

  private double stat(String key) {
    return profile.getData().getStat(key);
  }

  private BlockBreakEvent breakBlock(BlockRef block) {
    return adapt.post(new BlockBreakEvent(player, block, true, 0));
  }

  /** The spawner Motherlode kept, which the kept Drop-To-Inventory moves into the inventory. */
  private ItemRef keptSpawner() {
    for (ItemRef item : player.inventory().contents()) {
      if (item != null && !item.isEmpty() && item.type().equals(Id.minecraft("spawner"))) {
        return item;
      }
    }
    return null;
  }

  private static Map<?, ?> pendingSpawners(Motherlode motherlode) throws ReflectiveOperationException {
    Field field = Motherlode.class.getDeclaredField("pendingSpawners");
    field.setAccessible(true);
    return (Map<?, ?>) field.get(motherlode);
  }

  /** A claim where nobody may place. */
  private static final class Claim implements Protector {
    @Override
    public String name() {
      return "claims";
    }

    @Override
    public boolean enabledByDefault() {
      return true;
    }

    @Override
    public boolean allows(ProtectionCheck check, PlayerRef player, Loc location, EntityRef victim,
                          Id adaptation) {
      return check != ProtectionCheck.BLOCK_PLACE;
    }
  }

  private BlockDropItemEvent spawnerDrops(BlockRef block) {
    return new BlockDropItemEvent(player, block, FakeBlockState.of("spawner"), new ArrayList<>(), Id.minecraft("zombie"));
  }

  private BlockDropItemEvent drops(BlockRef block, String brokenType, ItemRef... items) {
    List<ItemEntityRef> entities = new ArrayList<>();
    for (ItemRef item : items) {
      entities.add(world.createItemEntity(block.center().position(), item));
    }
    return new BlockDropItemEvent(player, block, FakeBlockState.of(brokenType), entities, null);
  }
}
