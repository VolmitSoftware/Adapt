package art.arcane.adapt.content.adaptation.unarmed.upgraded;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedGlassCannon;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedIronFists;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedPower;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.content.skill.SkillUnarmed;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.MobOrigin;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.engine.xp.XpProvenance;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.block.BlockDamageEvent;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

class UnarmedUpgradesRuntimeTest {
  private static final Id SLOWNESS = Id.minecraft("slowness");
  private static final Id WEAKNESS = Id.minecraft("weakness");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private SkillRegistry registry;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Brawler", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillUnarmed.class, SkillSwords.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void titanFistsDoesNothingForAPlayerWhoHasNotMutated() {
    profile.getData().getSkillLine("unarmed").setAdaptation(upgrade(TitanFists.class), 1);
    FakeLiving target = zombie(2.5D);

    assertThat(punch(target, 1D).damage()).isEqualTo(1D);
    assertThat(adapt.post(softBlockHit()).instaBreak()).isFalse();
  }

  @Test
  void titanFistsHitsHarderWithoutArmor() {
    mutate();
    TitanFists titan = learn(TitanFists.class, 1);

    double naked = punch(zombie(2.5D), 1D).damage();
    player.setEquipment(EquipmentSlot.CHEST, FakeItem.of("diamond_chestplate"));
    double armored = punch(zombie(2.5D), 1D).damage();

    assertThat(naked).isCloseTo(titan.damage(1D, 0D, 1), within(1.0E-9D));
    assertThat(armored).isLessThan(naked).isGreaterThan(1D);
  }

  @Test
  void titanFistsOutpunchesTheMaxedOriginalsByATenthPerLevel() {
    player.setAttributeBase(Attributes.ATTACK_DAMAGE, 1D);
    for (Class<?> original : List.of(UnarmedPower.class, UnarmedIronFists.class, UnarmedGlassCannon.class)) {
      Adaptation<?> adaptation = registry.getSkill("unarmed").getAdaptations().stream().filter(original::isInstance)
          .findFirst().orElseThrow();
      profile.getData().getSkillLine("unarmed").setAdaptation(adaptation, adaptation.getMaxLevel());
    }
    punch(zombie(2.5D), 1D);
    double fullFist = player.attributeValue(Attributes.ATTACK_DAMAGE);
    double originals = punch(zombie(2.5D), fullFist).damage();
    assertThat(fullFist).isGreaterThan(1D);

    mutate();
    TitanFists titan = learn(TitanFists.class, 1);
    double first = punch(zombie(2.5D), 1D).damage();
    learn(TitanFists.class, 2);
    double second = punch(zombie(2.5D), 1D).damage();
    learn(TitanFists.class, 3);
    double third = punch(zombie(2.5D), 1D).damage();

    assertThat(first).isGreaterThanOrEqualTo((originals * 1.1D) - 1.0E-6D);
    assertThat(second).isGreaterThanOrEqualTo((originals * 1.2D) - 1.0E-6D);
    assertThat(third).isGreaterThanOrEqualTo((originals * 1.3D) - 1.0E-6D);
    assertThat(third).isCloseTo(titan.damage(1D, 0D, 3), within(1.0E-9D));
  }

  @Test
  void aSprintPunchBreaksThreeSoftBlocksBehindTheTargetForOneHunger() {
    mutate();
    learn(TitanFists.class, 1);
    platform.items().hardness("dirt", 0.5F, 0.5F);
    List<BlockRef> wall = wall();
    FakeLiving target = platform.world().add(zombie(2.5D));
    player.setFoodLevel(10);
    player.setSprinting(true);

    punch(target, 1D);
    platform.scheduler().advance(2);

    assertThat(wall.stream().filter(BlockRef::isAir).count()).isEqualTo(3L);
    assertThat(platform.protection().broken()).hasSize(3);
    assertThat(player.foodLevel()).isEqualTo(9);
    assertThat(target.velocity().x()).isPositive();
  }

  @Test
  void spammedSprintPunchesWaitForTheCooldownAndAnEmptyBarStopsThem() {
    mutate();
    learn(TitanFists.class, 1);
    platform.items().hardness("dirt", 0.5F, 0.5F);
    FakeLiving target = platform.world().add(zombie(2.5D));
    List<BlockRef> wall = wall();
    player.setSprinting(true);
    player.setFoodLevel(0);

    punch(target, 1D);
    platform.scheduler().advance(2);
    assertThat(wall).noneMatch(BlockRef::isAir);

    player.setFoodLevel(10);
    punch(target, 1D);
    punch(target, 1D);
    punch(target, 1D);
    platform.scheduler().advance(2);
    assertThat(player.foodLevel()).isEqualTo(9);
    assertThat(wall.stream().filter(BlockRef::isAir).count()).isEqualTo(3L);
  }

  @Test
  void aSprintPunchLeavesProtectedBlocks() {
    mutate();
    learn(TitanFists.class, 1);
    platform.items().hardness("dirt", 0.5F, 0.5F);
    List<BlockRef> wall = wall();
    FakeLiving target = platform.world().add(zombie(2.5D));
    player.setFoodLevel(10);
    player.setSprinting(true);
    platform.protection().deny(true);

    punch(target, 1D);
    platform.scheduler().advance(2);
    assertThat(wall).noneMatch(BlockRef::isAir);

  }

  @Test
  void aSprintPunchLeavesPlayerPlacedBlocks() {
    mutate();
    learn(TitanFists.class, 1);
    platform.items().hardness("dirt", 0.5F, 0.5F);
    List<BlockRef> wall = wall();
    FakeLiving target = platform.world().add(zombie(2.5D));
    player.setFoodLevel(10);
    player.setSprinting(true);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(any(BlockRef.class))).thenReturn(true);
      punch(target, 1D);
      platform.scheduler().advance(2);
    }
    assertThat(wall).noneMatch(BlockRef::isAir);
  }

  @Test
  void aSprintPunchKnocksARaisedShieldDown() {
    mutate();
    learn(TitanFists.class, 1);
    FakePlayer blocker = platform.player("Blocker", 2.5D, 61D, 0.5D);
    adapt.join(blocker);
    blocker.setBlocking(true);
    player.setFoodLevel(10);
    player.setSprinting(true);

    punchPlayer(blocker);

    assertThat(blocker.cooldownTicks(Id.minecraft("shield"))).isEqualTo(100);
  }

  @Test
  void titanFistsBreaksSoftBlocksInstantly() {
    mutate();
    learn(TitanFists.class, 1);

    assertThat(adapt.post(softBlockHit()).instaBreak()).isTrue();
  }

  @Test
  void unbrokenComboKeepsStacksThroughAHitAndSlowsPerStack() {
    mutate();
    UnbrokenCombo combo = learn(UnbrokenCombo.class, 3);
    FakeLiving target = zombie(2.5D);

    punch(target, 1D);
    punch(target, 1D);
    adapt.post(new EntityDamageEvent(player, DamageCause.ENTITY_ATTACK, Id.minecraft("mob_attack"), 4D, FinalDamage.IDENTITY));
    double third = punch(target, 1D).damage();

    assertThat(third).isCloseTo(1D + (3 * combo.getDamagePerStack(3)), within(1.0E-9D));
    assertThat(target.effect(SLOWNESS)).isNotNull();
    assertThat(target.effect(SLOWNESS).amplifier()).isEqualTo(2);
  }

  @Test
  void aSneakingHitAtFullStacksSpendsTheComboWeakensAndDisarms() {
    mutate();
    UnbrokenCombo combo = learn(UnbrokenCombo.class, 1);
    withoutHeritageDisarm();
    FakeLiving target = platform.world().add(zombie(2.5D));
    for (int i = 0; i < combo.getMaxStacks(1); i++) {
      punch(target, 1D);
    }
    target.setEquipment(EquipmentSlot.MAIN_HAND, FakeItem.tool("iron_sword", 250));
    player.setSneaking(true);

    double finisher = punch(target, 1D).damage();

    assertThat(finisher).isCloseTo(1D + (combo.getMaxStacks(1) * combo.getFinisherDamagePerStack(1)), within(1.0E-9D));
    assertThat(target.effect(WEAKNESS)).isNotNull();
    assertThat(target.effect(WEAKNESS).amplifier()).isZero();
    assertThat(target.equipment(EquipmentSlot.MAIN_HAND).type().isAir()).isTrue();
    assertThat(platform.world().items()).hasSize(1);
    assertThat(profile.getData().getStat("unarmed.unbroken-combo.finishers")).isEqualTo(1D);

    player.setSneaking(false);
    assertThat(punch(target, 1D).damage()).isCloseTo(1D + combo.getDamagePerStack(1), within(1.0E-9D));
  }

  @Test
  void aFinisherNeedsAFullComboAndWaitsForItsCooldown() {
    mutate();
    UnbrokenCombo combo = learn(UnbrokenCombo.class, 2);
    withoutHeritageDisarm();
    FakeLiving target = platform.world().add(zombie(2.5D));
    int full = combo.getMaxStacks(2);
    punch(target, 1D);
    player.setSneaking(true);
    punch(target, 1D);
    assertThat(profile.getData().getStat("unarmed.unbroken-combo.finishers")).isZero();

    player.setSneaking(false);
    for (int i = 2; i < full; i++) {
      punch(target, 1D);
    }
    player.setSneaking(true);
    punch(target, 1D);
    assertThat(target.effect(WEAKNESS).amplifier()).isEqualTo(1);

    player.setSneaking(false);
    for (int i = 0; i < full; i++) {
      punch(target, 1D);
    }
    player.setSneaking(true);
    punch(target, 1D);
    assertThat(profile.getData().getStat("unarmed.unbroken-combo.finishers")).isEqualTo(1D);
  }

  @Test
  void aFinishedPlayerKeepsTheirItemInTheirOwnInventory() {
    mutate();
    UnbrokenCombo combo = learn(UnbrokenCombo.class, 1);
    withoutHeritageDisarm();
    FakePlayer victim = platform.player("Victim", 2.5D, 61D, 0.5D);
    adapt.join(victim);
    for (int i = 0; i < combo.getMaxStacks(1); i++) {
      punchPlayer(victim);
    }
    victim.holding(FakeItem.tool("iron_sword", 250));
    player.setSneaking(true);

    punchPlayer(victim);

    assertThat(victim.inventory().mainHand().type().isAir()).isTrue();
    assertThat(victim.inventory().contents().stream()
        .filter((ItemRef item) -> item != null && item.type().equals(Id.minecraft("iron_sword")))).hasSize(1);
    assertThat(platform.world().items()).isEmpty();
  }

  @Test
  void aFarmedMobLosesItsItemWithoutDroppingIt() {
    mutate();
    UnbrokenCombo combo = learn(UnbrokenCombo.class, 1);
    withoutHeritageDisarm();
    FakeLiving target = platform.world().add(zombie(2.5D));
    MobOrigin.record(target, CreatureSpawnEvent.Reason.SPAWNER);
    for (int i = 0; i < combo.getMaxStacks(1); i++) {
      punch(target, 1D);
    }
    target.setEquipment(EquipmentSlot.MAIN_HAND, FakeItem.tool("iron_sword", 250));
    player.setSneaking(true);

    punch(target, 1D);

    assertThat(target.equipment(EquipmentSlot.MAIN_HAND).type().isAir()).isTrue();
    assertThat(platform.world().items()).isEmpty();
  }

  @Test
  void eachFinisherLevelHitsHarderPerStackAndWeakensMore() {
    UnbrokenCombo combo = upgrade(UnbrokenCombo.class);
    TitanFists titan = upgrade(TitanFists.class);

    assertThat(combo.getFinisherDamagePerStack(1)).isCloseTo(0.8D, within(1.0E-9D));
    assertThat(combo.getFinisherDamagePerStack(2)).isCloseTo(1.1D, within(1.0E-9D));
    assertThat(combo.getFinisherDamagePerStack(3)).isCloseTo(1.4D, within(1.0E-9D));
    assertThat(List.of(combo.getFinisherWeaknessAmplifier(1), combo.getFinisherWeaknessAmplifier(2),
        combo.getFinisherWeaknessAmplifier(3))).containsExactly(0, 1, 1);
    assertThat(List.of(titan.getSprintCooldownMillis(1), titan.getSprintCooldownMillis(2),
        titan.getSprintCooldownMillis(3))).containsExactly(6000L, 5000L, 4000L);
  }

  @Test
  void aSneakingHitAtFullStacksDoesNothingWithoutUnbrokenCombo() {
    mutate();
    learn(TitanFists.class, 1);
    withoutHeritageDisarm();
    FakeLiving target = platform.world().add(zombie(2.5D));
    for (int i = 0; i < 12; i++) {
      punch(target, 1D);
    }
    target.setEquipment(EquipmentSlot.MAIN_HAND, FakeItem.tool("iron_sword", 250));
    player.setSneaking(true);

    punch(target, 1D);

    assertThat(target.effect(WEAKNESS)).isNull();
    assertThat(target.equipment(EquipmentSlot.MAIN_HAND).type().isAir()).isFalse();
  }

  @Test
  void aFinisherDoesNotAlsoGrabForSeismicThrow() {
    mutate();
    UnbrokenCombo combo = learn(UnbrokenCombo.class, 1);
    learn(SeismicThrow.class, 1);
    withoutHeritageDisarm();
    FakeLiving target = platform.world().add(zombie(2.5D));
    for (int i = 0; i < combo.getMaxStacks(1); i++) {
      punch(target, 1D);
    }
    player.setSneaking(true);

    punchPerPriority(target, 1D);
    player.setSneaking(false);
    adapt.post(new PlayerToggleSneakEvent(player, false));

    assertThat(profile.getData().getStat("unarmed.unbroken-combo.finishers")).isEqualTo(1D);
    assertThat(profile.getData().getStat("unarmed.seismic-throw.throws")).isZero();

    player.setSneaking(true);
    punchPerPriority(target, 1D);
    player.setSneaking(false);
    adapt.post(new PlayerToggleSneakEvent(player, false));
    assertThat(profile.getData().getStat("unarmed.seismic-throw.throws")).isEqualTo(1D);
  }

  @Test
  void seismicThrowLandsWithAShockwave() {
    mutate();
    learn(SeismicThrow.class, 1);
    FakeLiving thrown = platform.world().add(zombie(2.5D));
    thrown.setOnGround(true);
    FakeLiving bystander = platform.world().add(zombie(4.5D));

    player.setSneaking(true);
    punch(thrown, 1D);
    punch(thrown, 1D);
    assertThat(thrown.velocity().lengthSquared()).isPositive();

    thrown.setOnGround(false);
    platform.scheduler().advance(2);
    thrown.moveTo(new Loc(platform.world(), 3.5D, 61D, 0.5D));
    thrown.setOnGround(true);
    platform.scheduler().advance(2);

    assertThat(bystander.velocity().x()).isPositive();
    assertThat(profile.getData().getStat("unarmed.seismic-throw.mobs-shaken")).isEqualTo(1D);
  }

  @Test
  void seismicThrowShockwaveDamagesWhatItPushes() {
    mutate();
    SeismicThrow seismic = learn(SeismicThrow.class, 2);
    FakeLiving thrown = platform.world().add(zombie(2.5D));
    thrown.setOnGround(true);
    FakeLiving bystander = platform.world().add(zombie(4.5D));

    player.setSneaking(true);
    punch(thrown, 1D);
    punch(thrown, 1D);
    thrown.setOnGround(false);
    platform.scheduler().advance(2);
    thrown.moveTo(new Loc(platform.world(), 3.5D, 61D, 0.5D));
    thrown.setOnGround(true);
    platform.scheduler().advance(2);

    assertThat(seismic.getWaveDamage(2)).isGreaterThan(seismic.getWaveDamage(1));
    assertThat(bystander.damageTaken()).isCloseTo(seismic.getWaveDamage(2), within(1.0E-9D));
    assertThat(thrown.damageTaken()).isZero();
  }

  @Test
  void seismicThrowReleasesOnUnsneak() {
    mutate();
    learn(SeismicThrow.class, 1);
    FakeLiving thrown = platform.world().add(zombie(2.5D));

    player.setSneaking(true);
    punch(thrown, 1D);
    player.setSneaking(false);
    adapt.post(new PlayerToggleSneakEvent(player, false));

    assertThat(thrown.velocity().lengthSquared()).isPositive();
    assertThat(profile.getData().getStat("unarmed.seismic-throw.throws")).isEqualTo(1D);
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("unarmed", "swords")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("unarmed"), registry.getSkill("swords"), "Brawler"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = upgrade(type);
    profile.getData().getSkillLine("unarmed").setAdaptation(upgrade, level);
    return upgrade;
  }

  private <A extends Adaptation<?>> A upgrade(Class<A> type) {
    return SkillHost.of(registry.getSkill("unarmed")).getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
  }

  private BlockDamageEvent softBlockHit() {
    platform.items().hardness("dirt", 0.5F, 0.5F);
    BlockRef dirt = platform.world().set(0, 60, 0, "dirt").blockAt(0, 60, 0);
    return new BlockDamageEvent(player, dirt, player.inventory().mainHand(), false);
  }

  private FakeLiving zombie(double x) {
    FakeLiving zombie = new FakeLiving(Id.minecraft("zombie"), new Loc(platform.world(), x, 61D, 0.5D));
    zombie.trait(EntityTrait.MONSTER);
    zombie.trait(EntityTrait.ENEMY);
    zombie.trait(EntityTrait.UNDEAD);
    zombie.trait(EntityTrait.ZOMBIE);
    return zombie;
  }

  private List<BlockRef> wall() {
    player.moveTo(new Loc(platform.world(), 0.5D, 61D, 0.5D, -90F, 0F));
    List<BlockRef> wall = new ArrayList<>();
    for (int x = 3; x <= 5; x++) {
      for (int y = 61; y <= 62; y++) {
        wall.add(platform.world().set(x, y, 0, "dirt").blockAt(x, y, 0));
      }
    }
    return wall;
  }

  private void withoutHeritageDisarm() {
    profile.getData().getPreferences().set("adapt:unarmed/disarm", "enabled", "OFF");
  }

  private void punchPlayer(FakePlayer victim) {
    adapt.post(new EntityDamageByEntityEvent(victim, player, player, DamageCause.ENTITY_ATTACK,
        Id.minecraft("player_attack"), 1D, false, FinalDamage.IDENTITY));
  }

  /** Posts a fresh copy of the hit at each priority sharing one dispatch id, the way the Bukkit bridge does. */
  private EntityDamageByEntityEvent punchPerPriority(FakeLiving target, double damage) {
    EntityDamageByEntityEvent copy = null;
    double current = damage;
    for (EventPriority priority : EventPriority.values()) {
      EntityDamageByEntityEvent next = new EntityDamageByEntityEvent(target, player, player, DamageCause.ENTITY_ATTACK,
          Id.minecraft("player_attack"), current, false, FinalDamage.IDENTITY);
      if (copy != null) {
        next.setDispatchId(copy.dispatchId());
        next.setCancelled(copy.isCancelled());
      }
      adapt.bus().post(next, priority);
      current = next.damage();
      copy = next;
    }
    return copy;
  }

  private EntityDamageByEntityEvent punch(FakeLiving target, double damage) {
    return adapt.post(new EntityDamageByEntityEvent(target, player, player, DamageCause.ENTITY_ATTACK,
        Id.minecraft("player_attack"), damage, false, FinalDamage.IDENTITY));
  }
}
