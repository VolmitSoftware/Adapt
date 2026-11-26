package art.arcane.adapt.content.adaptation.rift.upgraded;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationTeleportPort;
import art.arcane.adapt.api.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.content.skill.SkillRift;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeEntity;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeItemEntity;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeProjectile;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.common.input.ReportedJumps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RiftUpgradesRuntimeTest {
  private static final Id ENDER_PEARL = Id.minecraft("ender_pearl");
  private static final Id RESISTANCE = Id.minecraft("resistance");
  private static final Id LEVITATION = Id.minecraft("levitation");

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
    player = platform.player("Voidwalker", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillRift.class, SkillStealth.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    ReportedJumps.reporting(player.uuid(), false);
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void theMutatedSkillOffersAllThreeUpgrades() {
    mutate();
    MutatedSkill mutated = registry.getMutatedSkill("mutated-rift-stealth");

    assertThat(mutated.getAdaptations()).extracting(Adaptation::getName).startsWith(
        "upgraded-rift-phase-step", "upgraded-rift-pocket-realm", "upgraded-rift-voidborn",
        "upgraded-stealth-umbral-form", "upgraded-stealth-death-mark", "upgraded-stealth-phantom-decoy");
  }

  @Test
  void voidbornDoesNothingForAPlayerWhoHasNotMutated() {
    profile.getData().getSkillLine("rift").setAdaptation(upgrade(Voidborn.class), 3);

    assertThat(adapt.post(target(enderman())).isCancelled()).isFalse();
  }

  @Test
  void voidbornCallsOffEndermenWithoutAPearl() {
    mutate();
    learn(Voidborn.class, 1);

    assertThat(adapt.post(target(enderman())).isCancelled()).isTrue();
  }

  @Test
  void voidbornDropsLevitationWhenYouSneak() {
    mutate();
    learn(Voidborn.class, 1);
    player.addEffect(EffectInstance.of(LEVITATION, 200, 0));

    adapt.post(new PlayerToggleSneakEvent(player, true));

    assertThat(player.effect(LEVITATION)).isNull();
  }

  @Test
  void voidbornBlinksAwayFromLethalDamageForAPearl() {
    mutate();
    learn(Voidborn.class, 1);
    floor(14);
    player.inventory().addItem(new FakeItem(ENDER_PEARL, 2));
    player.setHealth(4D);
    Loc origin = player.location();

    EntityDamageEvent hit = adapt.post(new EntityDamageEvent(player, DamageCause.CUSTOM, Id.minecraft("generic"), 10D,
        FinalDamage.IDENTITY));
    platform.scheduler().advance(1);

    assertThat(hit.isCancelled()).isTrue();
    assertThat(player.location().distance(origin)).isGreaterThan(3D);
    assertThat(player.effect(RESISTANCE)).isNotNull();
    assertThat(pearls()).isEqualTo(1);
  }

  @Test
  void pocketRealmOpensTheEnderChestFromAnEmptyHand() {
    mutate();
    learn(PocketRealm.class, 1);
    player.setSneaking(true);
    player.holding(FakeItem.empty());

    adapt.post(new PlayerInteractEvent(player, InteractAction.LEFT_CLICK_AIR, Hand.MAIN_HAND, FakeItem.empty(), null,
        null, null, UseResult.DEFAULT, UseResult.DEFAULT));

    assertThat(player.openMenuInventory()).isSameAs(player.enderChest());
  }

  @Test
  void pocketRealmRefillsPlacedBlocksFromTheEnderChest() {
    mutate();
    learn(PocketRealm.class, 1);
    player.holding(FakeItem.empty());
    player.enderChest().addItem(new FakeItem(Id.minecraft("stone"), 32));
    platform.world().set(3, 61, 0, "stone");

    adapt.post(new BlockPlaceEvent(player, platform.world().blockAt(3, 61, 0), FakeBlockState.of("air"), null,
        FakeItem.of("stone"), Hand.MAIN_HAND));
    platform.scheduler().advance(1);

    assertThat(player.inventory().mainHand().type()).isEqualTo(Id.minecraft("stone"));
    assertThat(player.inventory().mainHand().amount()).isEqualTo(32);
  }

  @Test
  void pocketRealmPullsNearbyDropsIntoTheEnderChest() throws InterruptedException {
    mutate();
    PocketRealm realm = learn(PocketRealm.class, 1);
    realm.getConfig().pullPulseTicks = 2;
    platform.world().add(new FakeItemEntity(new Loc(platform.world(), 3.5D, 61D, 0.5D),
        new FakeItem(Id.minecraft("diamond"), 3)));
    player.setSneaking(true);
    adapt.post(new PlayerToggleSneakEvent(player, true));

    long deadline = System.currentTimeMillis() + 3000L;
    while (enderChestDiamonds() == 0 && System.currentTimeMillis() < deadline) {
      realm.onTick();
      platform.scheduler().advance(1);
      Thread.sleep(10L);
    }

    assertThat(enderChestDiamonds()).isEqualTo(3);
  }

  @Test
  void phaseStepBouncesAThrownPearlOnce() {
    mutate();
    learn(PhaseStep.class, 1);
    FakeProjectile pearl = platform.world().add(new FakeProjectile(ENDER_PEARL, player.eyeLocation()));
    pearl.setShooter(player);
    pearl.setVelocity(new Vec3(0D, 0D, 1.5D));
    adapt.post(new ProjectileLaunchEvent(pearl));
    platform.world().set(0, 62, 5, "stone");

    ProjectileHitEvent hit = adapt.post(new ProjectileHitEvent(pearl, null, platform.world().blockAt(0, 62, 5),
        BlockFace.NORTH));
    platform.scheduler().advance(2);

    assertThat(hit.isCancelled()).isTrue();
    assertThat(platform.world().entities()).filteredOn((EntityRef entity) -> entity.type().equals(ENDER_PEARL)
        && entity != pearl && entity.isValid()).hasSize(1);
  }

  @Test
  void phaseStepBlinksFarOnADoubleJumpAndLandsWithResistance() {
    mutate();
    PhaseStep step = learn(PhaseStep.class, 1);
    floor(45);
    ReportedJumps.reporting(player.uuid(), true);
    Loc origin = player.location();

    player.setOnGround(true);
    move();
    player.setOnGround(false);
    press();
    press();
    platform.scheduler().advance(1);

    assertThat(player.location().distance(origin)).isGreaterThan(step.getDistance(1) - 6D);
    assertThat(player.effect(RESISTANCE)).isNotNull();
  }

  @Test
  void phaseStepCostsHalfAHeartWithoutAPearl() {
    mutate();
    learn(PhaseStep.class, 1);
    floor(45);
    player.setHealth(20D);

    doubleJump();

    assertThat(player.health()).isEqualTo(19D);
  }

  @Test
  void phaseStepSpendsAPlainPearlFirstAndPaysNoRiftXp() {
    mutate();
    learn(PhaseStep.class, 1);
    floor(45);
    player.setHealth(20D);
    player.inventory().setItem(10, new FakeItem(ENDER_PEARL, 2));
    Loc origin = player.location();

    doubleJump();

    assertThat(player.location().distance(origin)).isGreaterThan(10D);
    assertThat(pearls()).isEqualTo(1);
    assertThat(player.health()).isEqualTo(20D);
    assertThat(profile.getData().getStat("rift.teleports")).isEqualTo(1D);
  }

  @Test
  void phaseStepWillNotSpendTheLastHalfHeart() {
    mutate();
    learn(PhaseStep.class, 1);
    floor(45);
    player.setHealth(1D);
    Loc origin = player.location();

    doubleJump();

    assertThat(player.location().distance(origin)).isLessThan(1D);
    assertThat(player.health()).isEqualTo(1D);
  }

  @Test
  void phaseStepSwapsPlacesWithACreatureTaggedByASneakHit() {
    mutate();
    learn(PhaseStep.class, 3);
    floor(45);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 6.5D, 61D, 0.5D);
    player.setSneaking(true);
    adapt.post(new EntityDamageByEntityEvent(zombie, player, player, DamageCause.ENTITY_ATTACK,
        Id.minecraft("player_attack"), 1D, false, FinalDamage.IDENTITY));
    player.setSneaking(false);
    Loc origin = player.location();
    assertThat(profile.getData().getStat("rift.phase-step.tags")).isEqualTo(1D);

    doubleJump();

    assertThat(player.location().distance(new Loc(platform.world(), 6.5D, 61D, 0.5D))).isLessThan(0.5D);
    assertThat(zombie.location().distance(origin)).isLessThan(0.5D);
  }

  @Test
  void phaseStepTagsNothingBelowLevelTwo() {
    mutate();
    learn(PhaseStep.class, 1);
    floor(45);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 6.5D, 61D, 0.5D);
    player.setSneaking(true);
    adapt.post(new EntityDamageByEntityEvent(zombie, player, player, DamageCause.ENTITY_ATTACK,
        Id.minecraft("player_attack"), 1D, false, FinalDamage.IDENTITY));
    player.setSneaking(false);

    doubleJump();

    assertThat(zombie.location().distance(new Loc(platform.world(), 6.5D, 61D, 0.5D))).isLessThan(0.5D);
  }

  @Test
  void phaseStepNeverSwapsAPlayerBelowLevelThree() {
    mutate();
    learn(PhaseStep.class, 2);
    floor(45);
    FakePlayer other = platform.player("Other", 6.5D, 61D, 0.5D);
    adapt.join(other);
    player.setSneaking(true);
    adapt.post(new EntityDamageByEntityEvent(other, player, player, DamageCause.ENTITY_ATTACK,
        Id.minecraft("player_attack"), 1D, false, FinalDamage.IDENTITY));
    player.setSneaking(false);

    doubleJump();

    assertThat(other.location().distance(new Loc(platform.world(), 6.5D, 61D, 0.5D))).isLessThan(0.5D);
  }

  @Test
  void phaseStepSwapsPlacesWithATaggedPlayerAtLevelThree() {
    mutate();
    learn(PhaseStep.class, 3);
    floor(45);
    FakePlayer other = platform.player("Other", 6.5D, 61D, 0.5D);
    adapt.join(other);
    tagBySneakHit(other);
    Loc origin = player.location();

    doubleJump();

    assertThat(player.location().distance(new Loc(platform.world(), 6.5D, 61D, 0.5D))).isLessThan(0.5D);
    assertThat(other.location().distance(origin)).isLessThan(0.5D);
  }

  @Test
  void phaseStepLeavesBothPlayersWhenTheTargetsTeleportIsRefused() {
    mutate();
    learn(PhaseStep.class, 3);
    floor(45);
    FakePlayer other = platform.player("Other", 6.5D, 61D, 0.5D);
    adapt.join(other);
    tagBySneakHit(other);
    Loc origin = player.location();
    AdaptPorts.install(AdaptationTeleportPort.class, new AdaptationTeleportPort() {
      @Override
      public Loc announce(PlayerRef traveller, Adaptation<?> adaptation, Loc from, Loc to) {
        return traveller.uuid().equals(other.uuid()) ? null : to;
      }
    });
    try {
      doubleJump();
    } finally {
      AdaptPorts.uninstall(AdaptationTeleportPort.class);
    }

    assertThat(other.location().distance(new Loc(platform.world(), 6.5D, 61D, 0.5D))).isLessThan(0.5D);
    assertThat(player.location().distance(new Loc(platform.world(), 6.5D, 61D, 0.5D))).isGreaterThan(1D);
    assertThat(player.location().distance(origin)).isLessThan(1D);
  }

  private void tagBySneakHit(LivingRef target) {
    player.setSneaking(true);
    adapt.post(new EntityDamageByEntityEvent(target, player, player, DamageCause.ENTITY_ATTACK,
        Id.minecraft("player_attack"), 1D, false, FinalDamage.IDENTITY));
    player.setSneaking(false);
  }

  private void doubleJump() {
    ReportedJumps.reporting(player.uuid(), true);
    player.setOnGround(true);
    move();
    player.setOnGround(false);
    press();
    press();
    platform.scheduler().advance(1);
  }

  private void press() {
    ReportedJumps.report(player.uuid(), true);
    ReportedJumps.report(player.uuid(), false);
    move();
  }

  private void move() {
    adapt.post(new PlayerMoveEvent(player, player.location(), player.location()));
  }

  private void floor(int radius) {
    for (int x = -radius; x <= radius; x++) {
      for (int z = -radius; z <= radius; z++) {
        platform.world().set(x, 60, z, "stone");
      }
    }
  }

  private int pearls() {
    int count = 0;
    for (ItemRef stack : player.inventory().contents()) {
      if (stack != null && stack.type().equals(ENDER_PEARL)) {
        count += stack.amount();
      }
    }
    return count;
  }

  private int enderChestDiamonds() {
    int count = 0;
    for (ItemRef stack : player.enderChest().contents()) {
      if (stack != null && stack.type().equals(Id.minecraft("diamond"))) {
        count += stack.amount();
      }
    }
    return count;
  }

  private FakeEntity enderman() {
    return platform.world().add(new FakeEntity(Id.minecraft("enderman"), new Loc(platform.world(), 4.5D, 61D, 0.5D)));
  }

  private EntityTargetEvent target(FakeEntity enderman) {
    return new EntityTargetEvent(enderman, player, EntityTargetEvent.Reason.CLOSEST_PLAYER);
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("rift", "stealth")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("rift"), registry.getSkill("stealth"), "Voidwalker"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = upgrade(type);
    profile.getData().getSkillLine("rift").setAdaptation(upgrade, level);
    return upgrade;
  }

  private <A extends Adaptation<?>> A upgrade(Class<A> type) {
    return registry.getSkill("rift").getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
  }
}
