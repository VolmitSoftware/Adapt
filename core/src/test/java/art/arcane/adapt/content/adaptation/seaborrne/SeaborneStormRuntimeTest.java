package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.protection.ProtectionCheck;
import art.arcane.adapt.api.protection.ProtectorPort;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismBoonFood;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillSeaborne;
import art.arcane.adapt.content.skill.SkillTragOul;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.EntityExhaustionEvent;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeProjectile;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.testkit.XpPaid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Storm Strike, Tidal Ward, Rain-fed and the folded Night Vision, each with the exploit it must not allow. */
class SeaborneStormRuntimeTest {
  private static final double HIT = 5D;
  private static final Id NIGHT_VISION = Id.minecraft("night_vision");

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
    player = platform.player("Stormcaller", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillSeaborne.class, SkillHerbalism.class, SkillTragOul.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void stormStrikeHitsTheFirstHitAndEveryThirdAfterInTheRain() {
    SeaborneStormStrike storm = openStorm(learn(SeaborneStormStrike.class, 1));
    platform.world().weather(true, false);
    FakeLiving zombie = zombie();

    List<Integer> struck = new ArrayList<>();
    for (int hit = 1; hit <= 7; hit++) {
      if (melee(zombie) > HIT) {
        struck.add(hit);
      }
    }

    assertThat(struck).containsExactly(1, 4, 7);
    assertThat(melee(zombie(4.5D))).isCloseTo(HIT + storm.getDamage(1, false), within(1.0E-9D));
    assertThat(XpPaid.by(profile, storm)).isGreaterThan(0D);
  }

  @Test
  void stormStrikeNeedsRainOnTheAttackerOrWater() {
    openStorm(learn(SeaborneStormStrike.class, 5));
    assertThat(melee(zombie())).isEqualTo(HIT);

    platform.world().weather(true, false);
    platform.world().set(0, 64, 0, "stone");
    assertThat(melee(zombie())).isEqualTo(HIT);

    player.setInWater(true);
    assertThat(melee(zombie())).isGreaterThan(HIT);

    player.setInWater(false);
    platform.world().set(0, 64, 0, "air");
    platform.world().rainFalls(false);
    assertThat(melee(zombie())).isEqualTo(HIT);
  }

  @Test
  void aThunderstormStrikesHarderAndMoreOften() {
    SeaborneStormStrike storm = openStorm(learn(SeaborneStormStrike.class, 1));
    platform.world().weather(true, true);
    FakeLiving zombie = zombie();

    List<Integer> struck = new ArrayList<>();
    double first = 0D;
    for (int hit = 1; hit <= 5; hit++) {
      double dealt = melee(zombie);
      if (dealt > HIT) {
        struck.add(hit);
        first = first == 0D ? dealt : first;
      }
    }

    assertThat(struck).containsExactly(1, 3, 5);
    assertThat(first).isCloseTo(HIT + storm.getDamage(1, true), within(1.0E-9D));
    assertThat(storm.getDamage(1, true)).isGreaterThan(storm.getDamage(1, false));
  }

  @Test
  void aThrownTridentCallsTheStormButASweepDoesNot() {
    openStorm(learn(SeaborneStormStrike.class, 1));
    platform.world().weather(true, false);
    FakeProjectile trident = platform.world().add(new FakeProjectile(SeaborneTridents.TRIDENT,
        new Loc(platform.world(), 2.5D, 61D, 0.5D)));
    trident.setShooter(player);

    EntityDamageByEntityEvent thrown = adapt.post(new EntityDamageByEntityEvent(zombie(), trident, player,
        DamageCause.PROJECTILE, Id.minecraft("trident"), HIT, false, FinalDamage.IDENTITY));
    EntityDamageByEntityEvent sweep = adapt.post(new EntityDamageByEntityEvent(zombie(), player, player,
        DamageCause.ENTITY_SWEEP_ATTACK, Id.minecraft("player_attack"), HIT, false, FinalDamage.IDENTITY));

    assertThat(thrown.damage()).isGreaterThan(HIT);
    assertThat(sweep.damage()).isEqualTo(HIT);
  }

  @Test
  void swingingAcrossACrowdStrikesOncePerGap() {
    learn(SeaborneStormStrike.class, 5);
    platform.world().weather(true, true);

    int strikes = 0;
    for (int index = 0; index < 10; index++) {
      if (melee(zombie(2.5D + index)) > HIT) {
        strikes++;
      }
    }
    FakeLiving again = zombie();
    melee(again);
    melee(again);
    double third = melee(again);

    assertThat(strikes).isEqualTo(1);
    assertThat(third).isEqualTo(HIT);
  }

  @Test
  void farmedMobsPetsAndProtectedPlayersAreNeverStruck() {
    SeaborneStormStrike storm = openStorm(learn(SeaborneStormStrike.class, 5));
    platform.world().weather(true, false);
    FakeLiving spawned = zombie();
    MobOrigin.record(spawned, CreatureSpawnEvent.Reason.SPAWNER);
    FakeLiving pet = platform.world().add(new FakeLiving(Id.minecraft("wolf"), new Loc(platform.world(), 3.5D, 61D, 0.5D)));
    pet.setTameOwner(player.uuid());
    FakePlayer rival = platform.player("Rival", 4.5D, 61D, 0.5D);

    assertThat(melee(spawned)).as("spawner zombie").isEqualTo(HIT);
    assertThat(melee(pet)).as("own wolf").isEqualTo(HIT);
    AdaptPorts.install(ProtectorPort.class, new NoPvp());
    try {
      assertThat(melee(rival)).as("player where PvP is off").isEqualTo(HIT);
    } finally {
      AdaptPorts.uninstall(ProtectorPort.class);
    }
    assertThat(melee(rival)).as("player where PvP is on").isGreaterThan(HIT);
    assertThat(XpPaid.by(profile, storm)).as("strikes on a farmed mob, a pet and a player pay nothing").isZero();
  }

  @Test
  void stormStrikeIsInertWithoutTheAdaptation() {
    platform.world().weather(true, true);
    player.setInWater(true);

    assertThat(melee(zombie())).isEqualTo(HIT);
  }

  @Test
  void tidalWardBuildsToItsCapInWaterAndNotBeyond() {
    SeaborneTidalWard ward = learn(SeaborneTidalWard.class, 1);
    player.setInWater(true);

    for (int second = 0; second < 60; second++) {
      ward.refresh(player, System.currentTimeMillis(), 1D);
    }

    assertThat(player.absorption()).isCloseTo(ward.getCap(1), within(1.0E-9D));
    assertThat(ward.held(player)).isCloseTo(ward.getCap(1), within(1.0E-9D));
  }

  @Test
  void tidalWardBuildsInRainButNotUnderARoofOrInADryBiome() {
    SeaborneTidalWard ward = learn(SeaborneTidalWard.class, 3);
    platform.world().weather(true, false);
    ward.refresh(player, System.currentTimeMillis(), 1D);
    assertThat(player.absorption()).isGreaterThan(0D);

    ward.drop(player);
    platform.world().set(0, 64, 0, "stone");
    ward.refresh(player, System.currentTimeMillis(), 1D);
    assertThat(player.absorption()).isZero();

    platform.world().set(0, 64, 0, "air");
    platform.world().rainFalls(false);
    ward.refresh(player, System.currentTimeMillis(), 1D);
    assertThat(player.absorption()).isZero();
  }

  @Test
  void aHitPausesTheWardSoItCannotOutHealAFight() {
    SeaborneTidalWard ward = learn(SeaborneTidalWard.class, 5);
    player.setInWater(true);
    ward.refresh(player, System.currentTimeMillis(), 1D);
    double built = player.absorption();

    adapt.post(new EntityDamageEvent(player, DamageCause.ENTITY_ATTACK, Id.minecraft("mob_attack"), 1D, FinalDamage.IDENTITY));
    player.setAbsorption(0D);
    for (int second = 0; second < 3; second++) {
      ward.refresh(player, System.currentTimeMillis(), 1D);
    }

    assertThat(built).isGreaterThan(0D);
    assertThat(player.absorption()).isZero();
  }

  @Test
  void leavingAndReenteringTheWaterDoesNotRefillASpentWard() {
    SeaborneTidalWard ward = learn(SeaborneTidalWard.class, 5);
    player.setInWater(true);
    for (int second = 0; second < 20; second++) {
      ward.refresh(player, System.currentTimeMillis(), 1D);
    }
    player.setAbsorption(0D);

    player.setInWater(false);
    ward.refresh(player, System.currentTimeMillis(), 1D);
    player.setInWater(true);
    ward.refresh(player, System.currentTimeMillis(), 1D);

    assertThat(player.absorption()).isLessThanOrEqualTo(ward.getGainPerSecond(5) + 1.0E-9D);
  }

  @Test
  void theWardNeverStacksPastItsOwnCapOnOtherAbsorption() {
    SeaborneTidalWard ward = learn(SeaborneTidalWard.class, 1);
    player.setAttributeBase(Attributes.MAX_ABSORPTION, 8D);
    player.setAbsorption(8D);
    player.setInWater(true);

    for (int second = 0; second < 60; second++) {
      ward.refresh(player, System.currentTimeMillis(), 1D);
    }

    assertThat(player.absorption()).isLessThanOrEqualTo(8D + ward.getCap(1) + 1.0E-9D);
    assertThat(ward.held(player)).isLessThanOrEqualTo(ward.getCap(1) + 1.0E-9D);
  }

  @Test
  void theWardDrainsOnceYouAreDryAndTheLingerIsOver() {
    SeaborneTidalWard ward = learn(SeaborneTidalWard.class, 1);
    ward.getConfig().lingerMillisAtLevelOne = 0D;
    player.setInWater(true);
    for (int second = 0; second < 20; second++) {
      ward.refresh(player, System.currentTimeMillis(), 1D);
    }

    player.setInWater(false);
    ward.refresh(player, System.currentTimeMillis(), 1D);
    double afterOne = player.absorption();
    ward.refresh(player, System.currentTimeMillis(), 1D);

    assertThat(afterOne).as("after one dry second").isCloseTo(ward.getCap(1) - ward.getConfig().drainPerSecond, within(1.0E-9D));
    assertThat(player.absorption()).as("absorption once drained").isZero();
    assertThat(ward.held(player)).as("held once drained").isZero();
  }

  @Test
  void tidalWardIsInertWithoutTheAdaptation() {
    SeaborneTidalWard ward = adaptation(SeaborneTidalWard.class);
    player.setInWater(true);

    ward.refresh(player, System.currentTimeMillis(), 1D);

    assertThat(player.absorption()).isZero();
  }

  @Test
  void rainFedHoldsActivityHungerInRainButNeverRegenerationOrTheHungerEffect() {
    learn(SeaborneRainFed.class, 1);
    platform.world().weather(true, false);

    assertThat(exhaust(EntityExhaustionEvent.Reason.SPRINT)).isZero();
    assertThat(exhaust(EntityExhaustionEvent.Reason.BLOCK_MINED)).isZero();
    assertThat(exhaust(EntityExhaustionEvent.Reason.REGEN)).isEqualTo(6F);
    assertThat(exhaust(EntityExhaustionEvent.Reason.HUNGER_EFFECT)).isEqualTo(6F);

    platform.world().set(0, 64, 0, "stone");
    assertThat(exhaust(EntityExhaustionEvent.Reason.SPRINT)).isEqualTo(6F);
    player.setInWater(true);
    assertThat(exhaust(EntityExhaustionEvent.Reason.SWIM)).isZero();
  }

  @Test
  void rainFedRefillsOnlyItsAllowanceAsBoonFood() {
    SeaborneRainFed fed = learn(SeaborneRainFed.class, 1);
    player.setInWater(true);
    player.setFoodLevel(0);
    player.setSaturation(0F);

    for (int second = 0; second < 60; second++) {
      fed.refill(player, System.currentTimeMillis(), 1D);
    }

    assertThat(player.foodLevel() + player.saturation()).isLessThanOrEqualTo((float) fed.getRefillPerMinute(1) + 1.0E-4F);
    assertThat(player.foodLevel()).isGreaterThan(0);
    assertThat(HerbalismBoonFood.eaten(player)).isZero();
  }

  @Test
  void rainFedGraceStartsAtLevelThree() {
    SeaborneRainFed low = learn(SeaborneRainFed.class, 2);
    player.setInWater(true);
    low.refill(player, System.currentTimeMillis(), 1D);
    player.setInWater(false);
    assertThat(exhaust(EntityExhaustionEvent.Reason.SPRINT)).isEqualTo(6F);

    learn(SeaborneRainFed.class, 5);
    player.setInWater(true);
    low.refill(player, System.currentTimeMillis(), 1D);
    player.setInWater(false);
    assertThat(exhaust(EntityExhaustionEvent.Reason.SPRINT)).isZero();
    assertThat(low.getGraceMillis(3)).isGreaterThan(0L);
    assertThat(low.getGraceMillis(2)).isZero();
  }

  @Test
  void rainFedFoodCannotBeTurnedIntoHealthByTragOul() {
    MutationKit kit = new MutationKit(adapt, profile);
    kit.mutate("herbalism", "tragoul");
    kit.learn("herbalism", "tragoul", Recipe.GRAFT, 1);
    SeaborneRainFed fed = learn(SeaborneRainFed.class, 5);
    player.setHealth(10D);
    player.setFoodLevel(0);
    player.setInWater(true);
    for (int second = 0; second < 60; second++) {
      fed.refill(player, System.currentTimeMillis(), 1D);
    }
    int fedFood = player.foodLevel();

    adapt.post(new BlockBreakEvent(player, crop(1, 61, 2), true, 0));
    assertThat(fedFood).isGreaterThan(0);
    assertThat(player.foodLevel()).isEqualTo(fedFood);
    assertThat(player.health()).isEqualTo(10D);

    player.setFoodLevel(fedFood + 4);
    adapt.post(new BlockBreakEvent(player, crop(1, 61, 3), true, 0));
    assertThat(player.health()).isGreaterThan(10D);
  }

  @Test
  void rainFedIsInertWithoutTheAdaptation() {
    SeaborneRainFed fed = adaptation(SeaborneRainFed.class);
    platform.world().weather(true, false);
    player.setFoodLevel(4);

    fed.refill(player, System.currentTimeMillis(), 60D);

    assertThat(exhaust(EntityExhaustionEvent.Reason.SPRINT)).isEqualTo(6F);
    assertThat(player.foodLevel()).isEqualTo(4);
  }

  @Test
  void oxygenGivesItsOwnNightVisionUnderwaterFromLevelTwoAndLeavesAPotionAlone() {
    SeaborneOxygen oxygen = learn(SeaborneOxygen.class, 1);
    player.setInWater(true);
    oxygen.onTick();
    platform.scheduler().advance(2);
    assertThat(player.effect(NIGHT_VISION)).isNull();

    learn(SeaborneOxygen.class, 2);
    oxygen.onTick();
    platform.scheduler().advance(2);
    assertThat(player.effect(NIGHT_VISION)).isNotNull();

    player.setInWater(false);
    oxygen.onTick();
    platform.scheduler().advance(2);
    assertThat(player.effect(NIGHT_VISION)).isNull();

    player.addEffect(new EffectInstance(NIGHT_VISION, 3600, 0, false, true, true));
    oxygen.onTick();
    platform.scheduler().advance(2);
    assertThat(player.effect(NIGHT_VISION)).isNotNull();
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A adaptation = adaptation(type);
    profile.getData().getSkillLine("seaborne").setAdaptation(adaptation, level);
    return adaptation;
  }

  private <A extends Adaptation<?>> A adaptation(Class<A> type) {
    return registry.getSkill("seaborne").getAdaptations().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
  }

  private static SeaborneStormStrike openStorm(SeaborneStormStrike storm) {
    storm.getConfig().strikeGapMillis = 0L;
    storm.getConfig().targetStrikeGapMillis = 0L;
    return storm;
  }

  private FakeLiving zombie() {
    return zombie(2.5D);
  }

  private FakeLiving zombie(double x) {
    return platform.world().add(new FakeLiving(Id.minecraft("zombie"), new Loc(platform.world(), x, 61D, 0.5D)));
  }

  private double melee(FakeLiving target) {
    return adapt.post(new EntityDamageByEntityEvent(target, player, player, DamageCause.ENTITY_ATTACK,
        Id.minecraft("player_attack"), HIT, false, FinalDamage.IDENTITY)).damage();
  }

  private float exhaust(EntityExhaustionEvent.Reason reason) {
    return adapt.post(new EntityExhaustionEvent(player, reason, 6F)).exhaustion();
  }

  private BlockRef crop(int x, int y, int z) {
    platform.world().set(new BlockPos(x, y, z), new FakeBlockState(Id.minecraft("wheat"), Map.of("age", "7")));
    return platform.world().blockAt(x, y, z);
  }

  /** A protection plugin that turns PvP off everywhere. */
  private static final class NoPvp implements ProtectorPort {
    @Override
    public List<String> defaultProtectors() {
      return List.of("claims");
    }

    @Override
    public List<String> allProtectors() {
      return List.of("claims");
    }

    @Override
    public boolean allows(Set<String> protectors, ProtectionCheck check, PlayerRef player, Loc location, EntityRef victim,
                          Adaptation<?> adaptation) {
      return check != ProtectionCheck.PVP;
    }
  }
}
