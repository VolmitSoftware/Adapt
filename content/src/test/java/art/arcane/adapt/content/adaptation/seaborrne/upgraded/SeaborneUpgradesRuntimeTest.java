package art.arcane.adapt.content.adaptation.seaborrne.upgraded;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.content.skill.SkillSeaborne;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.event.player.PlayerAnimationEvent;
import art.arcane.adapt.platform.event.player.PlayerFishEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeProjectile;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.testkit.XpPaid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SeaborneUpgradesRuntimeTest {
  private static final Id REGENERATION = Id.minecraft("regeneration");
  private static final Id RESISTANCE = Id.minecraft("resistance");
  private static final Id TRIDENT = Id.minecraft("trident");
  private static final Id GENERIC = Id.minecraft("generic");

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
    player = platform.player("Diver", 0.5D, 40D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillSeaborne.class, SkillSwords.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void leviathanBreathesSwimsRegeneratesAndRunsTheLentTidalWardAndRainFedWhileWet() {
    mutate();
    Leviathan leviathan = learn(Leviathan.class, 3);
    double air = player.attributeValue(Attributes.OXYGEN_BONUS);
    double swim = player.attributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY);
    player.setSwimming(true);
    player.setFoodLevel(10);
    player.setSaturation(0F);

    for (int second = 0; second < 30; second++) {
      tick(leviathan);
    }

    assertThat(player.attributeValue(Attributes.OXYGEN_BONUS)).isGreaterThan(air);
    assertThat(player.attributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY)).isGreaterThan(swim);
    assertThat(player.effect(RESISTANCE)).isNull();
    assertThat(player.effect(REGENERATION)).isNotNull();
    assertThat(player.effect(REGENERATION).amplifier()).isEqualTo(leviathan.getRegenAmplifier(3));
    assertThat(player.absorption()).isGreaterThan(0D);
    assertThat(player.foodLevel()).isGreaterThan(10);
    assertThat(damage(10D)).isEqualTo(10D);
  }

  @Test
  void leviathanDoesNothingForAPlayerWhoHasNotMutated() {
    Leviathan leviathan = upgrade(Leviathan.class);
    profile.getData().getSkillLine("seaborne").setAdaptation(leviathan, 3);
    player.setSwimming(true);

    tick(leviathan);

    assertThat(leviathan.getLevel(player)).isZero();
    assertThat(player.effect(REGENERATION)).isNull();
    assertThat(player.effect(RESISTANCE)).isNull();
    assertThat(damage(10D)).isEqualTo(10D);
  }

  @Test
  void maelstromJetsFromTheSharedChargesUntilTheyRunOut() {
    mutate();
    Maelstrom maelstrom = learn(Maelstrom.class, 1);
    player.setSwimming(true);
    player.setFoodLevel(20);

    for (int jet = 0; jet < maelstrom.getMaxCharges(1); jet++) {
      player.setVelocity(Vec3.ZERO);
      adapt.post(new PlayerToggleSneakEvent(player, true));
      assertThat(player.velocity().lengthSquared()).isGreaterThan(0D);
    }
    player.setVelocity(Vec3.ZERO);
    adapt.post(new PlayerToggleSneakEvent(player, true));

    assertThat(player.velocity()).isEqualTo(Vec3.ZERO);
  }

  @Test
  void maelstromSurgesOnASwingIntoOpenWaterButNotWhileMiningAndPaysNoXp() {
    mutate();
    Maelstrom maelstrom = learn(Maelstrom.class, 1);
    player.setSwimming(true);
    player.holding(FakeItem.tool("trident", 250));
    platform.world().set(0, 41, 3, "stone");

    player.setVelocity(Vec3.ZERO);
    adapt.post(new PlayerAnimationEvent(player, PlayerAnimationEvent.Animation.ARM_SWING));
    assertThat(player.velocity()).isEqualTo(Vec3.ZERO);

    platform.world().set(0, 41, 3, "air");
    adapt.post(new PlayerAnimationEvent(player, PlayerAnimationEvent.Animation.ARM_SWING));
    assertThat(player.velocity().lengthSquared()).isGreaterThan(0D);
    assertThat(XpPaid.by(profile, maelstrom)).isZero();
  }

  @Test
  void leviathanPaysNoXpForGettingWet() {
    mutate();
    Leviathan leviathan = learn(Leviathan.class, 3);
    player.setSwimming(true);

    tick(leviathan);

    assertThat(player.effect(REGENERATION)).isNotNull();
    assertThat(XpPaid.by(profile, leviathan)).isZero();
  }

  @Test
  void maelstromTridentsHitHarderAndTurnBackOnTheHit() {
    mutate();
    Maelstrom maelstrom = learn(Maelstrom.class, 2);
    FakeProjectile trident = platform.world().add(new FakeProjectile(TRIDENT, new Loc(platform.world(), 10.5D, 40D, 0.5D)));
    trident.setShooter(player);
    trident.setOnGround(false);
    trident.setVelocity(new Vec3(1D, 0D, 0D));
    adapt.post(new ProjectileLaunchEvent(trident));
    FakeLiving target = platform.world().add(zombie());

    EntityDamageByEntityEvent hit = adapt.post(new EntityDamageByEntityEvent(target, trident, player,
        DamageCause.PROJECTILE, Id.minecraft("trident"), 8D, false, FinalDamage.IDENTITY));
    platform.scheduler().advance(4);

    assertThat(hit.damage()).isCloseTo(8D * (1D + maelstrom.getTridentBonus(2)), within(1.0E-9D));
    assertThat(trident.velocity().x()).isLessThan(0D);
  }

  @Test
  void deepFortuneAddsABonusCatchAndLuck() {
    mutate();
    DeepFortune fortune = learn(DeepFortune.class, 1);
    fortune.getConfig().catchChanceAtLevelOne = 1D;
    double luck = player.attributeValue(Attributes.LUCK);

    adapt.post(new PlayerFishEvent(player, null, bobber(), PlayerFishEvent.State.CAUGHT_FISH, 1));
    tick(fortune);

    assertThat(platform.world().items()).hasSize(1);
    assertThat(platform.world().experience()).containsExactly(fortune.getConfig().vanillaXpAtLevelOne);
    assertThat(player.attributeValue(Attributes.LUCK)).isEqualTo(luck + fortune.getLuck(1));
  }

  @Test
  void deepFortuneDoesNothingForAPlayerWhoHasNotMutated() {
    DeepFortune fortune = upgrade(DeepFortune.class);
    profile.getData().getSkillLine("seaborne").setAdaptation(fortune, 1);
    fortune.getConfig().catchChanceAtLevelOne = 1D;

    adapt.post(new PlayerFishEvent(player, null, bobber(), PlayerFishEvent.State.CAUGHT_FISH, 1));

    assertThat(platform.world().items()).isEmpty();
  }

  private void tick(SimpleAdaptation<?> adaptation) {
    adaptation.onTick();
    platform.scheduler().advance(2);
  }

  private double damage(double amount) {
    return adapt.post(new EntityDamageEvent(player, DamageCause.CUSTOM, GENERIC, amount, FinalDamage.IDENTITY)).damage();
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("seaborne", "swords")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("seaborne"), registry.getSkill("swords"), "Tidecaller"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = upgrade(type);
    profile.getData().getSkillLine("seaborne").setAdaptation(upgrade, level);
    return upgrade;
  }

  private <A extends Adaptation<?>> A upgrade(Class<A> type) {
    return SkillHost.of(registry.getSkill("seaborne")).getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
  }

  private FakeProjectile bobber() {
    return new FakeProjectile(Id.minecraft("fishing_bobber"), new Loc(platform.world(), 4.5D, 40D, 0.5D));
  }

  private FakeLiving zombie() {
    return new FakeLiving(Id.minecraft("zombie"), new Loc(platform.world(), 12.5D, 40D, 0.5D));
  }
}
