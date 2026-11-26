package art.arcane.adapt.content.adaptation.hunter.upgraded;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.protection.ProtectionCheck;
import art.arcane.adapt.api.protection.ProtectorPort;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.content.adaptation.hunter.HunterSnares;
import art.arcane.adapt.content.skill.SkillHunter;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.entity.ItemSpawnEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/** Tracker's Compass, big game extra rolls and live capture, each with the exploit it must refuse. */
class HunterDepthRuntimeTest {
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
    player = platform.player("Predator", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillHunter.class, SkillStealth.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void theCompassSetsAKindForOneHungerAndPointsToTheNearestOne() {
    mutate();
    ApexPredator apex = learn(ApexPredator.class, 1);
    TrackersCompass compass = compass(apex);
    player.holding(compass.create(new TrackersCompass.Data()));
    player.setFoodLevel(20);
    FakeLiving near = animal("cow", 10D);
    animal("cow", 40D);

    PlayerInteractEntityEvent set = adapt.post(new PlayerInteractEntityEvent(player, near, Hand.MAIN_HAND, null));
    apex.onTick();
    platform.scheduler().advance(1);

    assertThat(set.isCancelled()).isTrue();
    assertThat(player.foodLevel()).isEqualTo(19);
    assertThat(compass.read(player.inventory().mainHand()).kinds).containsExactly("minecraft:cow");
    assertThat(player.actionBars()).anySatisfy((String bar) -> assertThat(bar).contains("Cow").contains("10"));
  }

  @Test
  void theCompassRemembersOnlyAsManyKindsAsItsLevelAllows() {
    mutate();
    ApexPredator apex = learn(ApexPredator.class, 1);
    TrackersCompass compass = compass(apex);
    player.holding(compass.create(new TrackersCompass.Data()));
    player.setFoodLevel(20);

    adapt.post(new PlayerInteractEntityEvent(player, animal("cow", 3D), Hand.MAIN_HAND, null));
    adapt.post(new PlayerInteractEntityEvent(player, animal("pig", 4D), Hand.MAIN_HAND, null));

    assertThat(compass.read(player.inventory().mainHand()).kinds).containsExactly("minecraft:pig");
  }

  @Test
  void theCompassNeverTracksPlayers() {
    mutate();
    ApexPredator apex = learn(ApexPredator.class, 3);
    TrackersCompass compass = compass(apex);
    player.holding(compass.create(new TrackersCompass.Data()));
    player.setFoodLevel(20);
    FakePlayer other = platform.player("Prey", 5D, 61D, 0.5D);

    adapt.post(new PlayerInteractEntityEvent(player, other, Hand.MAIN_HAND, null));

    assertThat(compass.read(player.inventory().mainHand()).kinds).isEmpty();
    assertThat(player.foodLevel()).isEqualTo(20);
    assertThat(TrackersCompass.nearest(player, Id.minecraft("player"), 192D, Set.of(other.uuid()))).isNull();
  }

  @Test
  void theCompassDoesNothingForAPlayerWithoutApexPredator() {
    ApexPredator apex = upgrade(ApexPredator.class);
    TrackersCompass compass = compass(apex);
    player.holding(compass.create(new TrackersCompass.Data()));
    player.setFoodLevel(20);

    PlayerInteractEntityEvent set = adapt.post(new PlayerInteractEntityEvent(player, animal("cow", 3D), Hand.MAIN_HAND, null));

    assertThat(set.isCancelled()).isTrue();
    assertThat(compass.read(player.inventory().mainHand()).kinds).isEmpty();
    assertThat(player.foodLevel()).isEqualTo(20);
  }

  @Test
  void namedMobsAreTrackedOnlyAtTheQuarryLevelOnceHit() {
    mutate();
    ApexPredator apex = learn(ApexPredator.class, 3);
    FakeLiving named = animal("cow", 5D);
    named.setCustomName("Bessie", true);

    assertThat(TrackersCompass.nearest(player, Id.minecraft("cow"), 64D, Set.of())).isNull();
    adapt.post(new EntityDamageByEntityEvent(named, player, player, DamageCause.ENTITY_ATTACK,
        Id.minecraft("player_attack"), 1D, false, FinalDamage.IDENTITY));
    TrackersCompass compass = compass(apex);
    TrackersCompass.Data stored = new TrackersCompass.Data();
    stored.kinds.add("minecraft:cow");
    player.holding(compass.create(stored));
    apex.onTick();
    platform.scheduler().advance(1);

    assertThat(player.actionBars()).anySatisfy((String bar) -> assertThat(bar).contains("Cow").contains("5"));
  }

  @Test
  void theCompassPulseSchedulesNothingForAHunterNotHoldingIt() {
    mutate();
    ApexPredator apex = learn(ApexPredator.class, 3);
    player.holding(FakeItem.of("stick"));
    int scheduled = platform.scheduler().entityTaskDelays(player).size();

    apex.onTick();

    assertThat(platform.scheduler().entityTaskDelays(player)).hasSize(scheduled);
  }

  @Test
  void theCompassKeepsItsTargetForAFewPulsesBeforeSearchingAgain() {
    mutate();
    ApexPredator apex = learn(ApexPredator.class, 1);
    TrackersCompass compass = compass(apex);
    TrackersCompass.Data stored = new TrackersCompass.Data();
    stored.kinds.add("minecraft:cow");
    player.holding(compass.create(stored));
    animal("cow", 20D);
    pulse(apex);
    animal("cow", 7D);

    pulse(apex);
    String kept = player.actionBars().getLast();
    for (int i = 0; i < 6; i++) {
      pulse(apex);
    }

    assertThat(kept).contains("20");
    assertThat(player.actionBars().getLast()).contains("7").doesNotContain("20");
  }

  @Test
  void theCompassSearchesAFlatBoxSoDeepCavesAreLeftOut() {
    mutate();
    learn(ApexPredator.class, 3);
    FakeLiving deep = animal("cow", 0.5D);
    deep.moveTo(new Loc(platform.world(), 0.5D, 1D, 0.5D));

    assertThat(TrackersCompass.nearest(player, Id.minecraft("cow"), 192D, Set.of())).isNull();
  }

  @Test
  void naturalBigGameGetsOneExtraRollOfItsOwnLootOnlyOnce() {
    mutate();
    ApexPredator apex = learn(ApexPredator.class, 1);
    apex.getConfig().extraRollChanceBase = 1D;
    FakeLiving ravager = hostile("ravager", 3D);
    platform.host().animals().setLoot(ravager, List.of(FakeItem.of("saddle")));

    EntityDeathEvent first = kill(ravager);
    int afterFirst = count(first, "saddle");
    EntityDeathEvent again = kill(ravager);

    assertThat(afterFirst).isEqualTo(1);
    assertThat(count(again, "saddle")).isEqualTo(1);
  }

  @Test
  void spawnerBredAndSummonedBigGameNeverRolls() {
    mutate();
    ApexPredator apex = learn(ApexPredator.class, 3);
    apex.getConfig().extraRollChanceBase = 1D;
    FakeLiving spawned = hostile("ravager", 3D);
    MobOrigin.record(spawned, CreatureSpawnEvent.Reason.SPAWNER);
    FakeLiving bred = hostile("ravager", 4D);
    MobOrigin.record(bred, CreatureSpawnEvent.Reason.BREEDING);
    FakeLiving wither = hostile("wither", 5D);
    wither.trait(EntityTrait.BOSS);
    adapt.post(new CreatureSpawnEvent(wither, wither.location(), CreatureSpawnEvent.Reason.BUILD_WITHER));
    for (FakeLiving mob : List.of(spawned, bred, wither)) {
      platform.host().animals().setLoot(mob, List.of(FakeItem.of("nether_star")));
    }

    assertThat(count(kill(spawned), "nether_star")).isZero();
    assertThat(count(kill(bred), "nether_star")).isZero();
    assertThat(count(kill(wither), "nether_star")).isZero();
  }

  @Test
  void aSnaredAnimalIsHeldThenLedWithoutMakingAnyItemOrCreature() {
    mutate();
    StalkersTrail trail = learn(StalkersTrail.class, 1);
    player.setSneaking(true);
    placeSnare(trail);
    FakeLiving cow = animal("cow", 2.5D);
    cow.moveTo(new Loc(platform.world(), 2.5D, 61D, 0.5D));
    player.moveTo(new Loc(platform.world(), 20D, 61D, 0.5D));
    int entities = platform.world().entities().size();
    List<String> inventory = snapshot();

    trail.onTick();
    platform.scheduler().advance(2);
    trail.onTick();
    platform.scheduler().advance(2);

    assertThat(cow.modifiers(Attributes.MOVEMENT_SPEED)).isNotEmpty();
    assertThat(trail.hasTickDemand()).isTrue();

    player.moveTo(new Loc(platform.world(), 6.5D, 61D, 0.5D));
    trail.onTick();
    platform.scheduler().advance(2);
    assertThat(cow.modifiers(Attributes.MOVEMENT_SPEED)).isEmpty();

    player.moveTo(new Loc(platform.world(), 40D, 61D, 0.5D));
    trail.onTick();
    platform.scheduler().advance(2);
    trail.onTick();
    platform.scheduler().advance(2);

    assertThat(platform.world().entities()).hasSize(entities);
    assertThat(platform.world().items()).isEmpty();
    assertThat(snapshot()).isEqualTo(inventory);
  }

  @Test
  void aLedCatchIsLeashedToItsTrapperAndLetGoWithoutALead() {
    mutate();
    StalkersTrail trail = learn(StalkersTrail.class, 1);
    player.setSneaking(true);
    placeSnare(trail);
    FakeLiving cow = animal("cow", 2.5D);
    player.moveTo(new Loc(platform.world(), 20D, 61D, 0.5D));
    trail.onTick();
    platform.scheduler().advance(2);
    assertThat(platform.host().animals().leashHolder(cow)).isNull();

    player.moveTo(new Loc(platform.world(), 6.5D, 61D, 0.5D));
    trail.onTick();
    platform.scheduler().advance(2);
    assertThat(platform.host().animals().leashHolder(cow)).isEqualTo(player.uuid());

    player.moveTo(new Loc(platform.world(), 40D, 61D, 0.5D));
    trail.onTick();
    platform.scheduler().advance(2);
    assertThat(platform.host().animals().isLeashed(cow)).isFalse();
    assertThat(trail.hasTickDemand()).isFalse();
    assertThat(platform.world().items()).isEmpty();
  }

  @Test
  void theLeadTheGameDropsWhenALedCatchSlipsItIsCancelled() {
    mutate();
    StalkersTrail trail = learn(StalkersTrail.class, 1);
    player.setSneaking(true);
    placeSnare(trail);
    FakeLiving cow = animal("cow", 2.5D);
    player.moveTo(new Loc(platform.world(), 20D, 61D, 0.5D));
    trail.onTick();
    platform.scheduler().advance(2);
    player.moveTo(new Loc(platform.world(), 6.5D, 61D, 0.5D));
    trail.onTick();
    platform.scheduler().advance(2);
    ItemSpawnEvent held = adapt.post(new ItemSpawnEvent(platform.world().dropItem(cow.location().position(),
        new FakeItem(Id.minecraft("lead"), 1)), cow.location()));
    assertThat(held.isCancelled()).isFalse();

    platform.host().animals().setLeashHolder(cow, null);
    ItemSpawnEvent slipped = adapt.post(new ItemSpawnEvent(platform.world().dropItem(cow.location().position(),
        new FakeItem(Id.minecraft("lead"), 1)), cow.location()));
    assertThat(slipped.isCancelled()).isTrue();

    trail.onTick();
    platform.scheduler().advance(2);
    assertThat(trail.hasTickDemand()).isFalse();
  }

  @Test
  void anAnimalOnSomeoneElsesLeadIsNeverCaught() {
    mutate();
    StalkersTrail trail = learn(StalkersTrail.class, 1);
    player.setSneaking(true);
    placeSnare(trail);
    player.moveTo(new Loc(platform.world(), 20D, 61D, 0.5D));
    FakePlayer farmer = platform.player("Farmer", 3.5D, 61D, 3.5D);
    FakeLiving cow = animal("cow", 2.5D);
    platform.host().animals().setLeashHolder(cow, farmer);

    trail.onTick();
    platform.scheduler().advance(5);

    assertThat(cow.modifiers(Attributes.MOVEMENT_SPEED)).isEmpty();
    assertThat(platform.host().animals().leashHolder(cow)).isEqualTo(farmer.uuid());
  }

  @Test
  void aTrapperHoldingTheMostCatchesStillRootsMonstersBesideACow() {
    mutate();
    StalkersTrail trail = learn(StalkersTrail.class, 1);
    trail.getConfig().maxCaptivesPerPlayer = 1;
    player.setSneaking(true);
    placeSnare(trail);
    FakeLiving first = animal("cow", 2.5D);
    player.moveTo(new Loc(platform.world(), 20D, 61D, 0.5D));
    trail.onTick();
    platform.scheduler().advance(2);
    assertThat(first.modifiers(Attributes.MOVEMENT_SPEED)).isNotEmpty();

    placeSnare(trail);
    FakeLiving second = animal("cow", 3D);
    FakeLiving zombie = hostile("zombie", 3.2D);
    trail.onTick();
    platform.scheduler().advance(2);

    assertThat(zombie.modifiers(Attributes.MOVEMENT_SPEED)).isNotEmpty();
    assertThat(second.modifiers(Attributes.MOVEMENT_SPEED)).isEmpty();
  }

  @Test
  void aCreatureInLandTheTrapperCannotUseIsNotCaughtAndTheSnareStaysArmed() {
    mutate();
    StalkersTrail trail = learn(StalkersTrail.class, 1);
    player.setSneaking(true);
    placeSnare(trail);
    player.moveTo(new Loc(platform.world(), 20D, 61D, 0.5D));
    FakeLiving cow = animal("cow", 3.5D);

    AdaptPorts.install(ProtectorPort.class, new Claim((Loc at) -> at.x() >= 3D && at.x() < 10D));
    try {
      trail.onTick();
      platform.scheduler().advance(5);
    } finally {
      AdaptPorts.uninstall(ProtectorPort.class);
    }
    assertThat(cow.modifiers(Attributes.MOVEMENT_SPEED)).isEmpty();

    trail.onTick();
    platform.scheduler().advance(5);
    assertThat(cow.modifiers(Attributes.MOVEMENT_SPEED)).isNotEmpty();
  }

  @Test
  void aLedCatchIsLetGoWhenItIsInLandTheTrapperCannotUse() {
    mutate();
    StalkersTrail trail = learn(StalkersTrail.class, 1);
    player.setSneaking(true);
    placeSnare(trail);
    FakeLiving cow = animal("cow", 2.5D);
    player.moveTo(new Loc(platform.world(), 20D, 61D, 0.5D));
    trail.onTick();
    platform.scheduler().advance(2);
    player.moveTo(new Loc(platform.world(), 6.5D, 61D, 0.5D));
    trail.onTick();
    platform.scheduler().advance(2);
    assertThat(platform.host().animals().leashHolder(cow)).isEqualTo(player.uuid());

    AdaptPorts.install(ProtectorPort.class, new Claim((Loc at) -> at.x() < 4D));
    try {
      trail.onTick();
      platform.scheduler().advance(2);
    } finally {
      AdaptPorts.uninstall(ProtectorPort.class);
    }

    assertThat(platform.host().animals().isLeashed(cow)).isFalse();
    assertThat(trail.hasTickDemand()).isFalse();
    assertThat(platform.world().items()).isEmpty();
  }

  @Test
  void villagersAreNeverCaughtAndTheSnareStaysArmed() {
    mutate();
    StalkersTrail trail = learn(StalkersTrail.class, 1);
    player.setSneaking(true);
    placeSnare(trail);
    player.moveTo(new Loc(platform.world(), 20D, 61D, 0.5D));
    FakeLiving villager = new FakeLiving(Id.minecraft("villager"), new Loc(platform.world(), 2.5D, 61D, 0.5D));
    villager.trait(EntityTrait.VILLAGER);
    platform.world().add(villager);
    platform.host().track(villager);

    trail.onTick();
    platform.scheduler().advance(5);

    assertThat(villager.modifiers(Attributes.MOVEMENT_SPEED)).isEmpty();
    FakeLiving cow = animal("cow", 2.5D);
    trail.onTick();
    platform.scheduler().advance(5);
    trail.onTick();
    platform.scheduler().advance(5);
    assertThat(cow.modifiers(Attributes.MOVEMENT_SPEED)).isNotEmpty();
  }

  private void placeSnare(StalkersTrail trail) {
    ItemRef snare = HunterSnares.buildSnareItem(1);
    player.holding(snare);
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND, snare,
        platform.world().blockAt(2, 60, 0), BlockFace.UP, null, UseResult.DEFAULT, UseResult.DEFAULT));
    assertThat(player.inventory().mainHand().isEmpty()).isTrue();
  }

  private List<String> snapshot() {
    List<String> items = new ArrayList<>();
    for (ItemRef item : player.inventory().contents()) {
      items.add(item == null || item.isEmpty() ? "" : item.type() + "x" + item.amount());
    }
    return items;
  }

  private int count(EntityDeathEvent death, String type) {
    int total = 0;
    List<ItemRef> all = new ArrayList<>(death.drops());
    all.addAll(player.inventory().contents());
    for (ItemRef item : all) {
      if (item != null && !item.isEmpty() && item.type().equals(Id.minecraft(type))) {
        total += item.amount();
      }
    }
    return total;
  }

  private EntityDeathEvent kill(FakeLiving victim) {
    return adapt.post(new EntityDeathEvent(victim, player, player, player, DamageCause.ENTITY_ATTACK,
        new ArrayList<>(), 0));
  }

  private void pulse(ApexPredator apex) {
    apex.onTick();
    platform.scheduler().advance(1);
  }

  private TrackersCompass compass(ApexPredator apex) {
    return (TrackersCompass) apex.getTools().getFirst();
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("hunter", "stealth")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("hunter"), registry.getSkill("stealth"), "Predator"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = upgrade(type);
    profile.getData().getSkillLine("hunter").setAdaptation(upgrade, level);
    return upgrade;
  }

  private <A extends Adaptation<?>> A upgrade(Class<A> type) {
    return registry.getSkill("hunter").getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
  }

  private FakeLiving animal(String type, double x) {
    FakeLiving mob = new FakeLiving(Id.minecraft(type), new Loc(platform.world(), x, 61D, 0.5D));
    mob.trait(EntityTrait.ANIMAL);
    platform.world().add(mob);
    platform.host().track(mob);
    return mob;
  }

  private FakeLiving hostile(String type, double x) {
    FakeLiving mob = new FakeLiving(Id.minecraft(type), new Loc(platform.world(), x, 61D, 0.5D));
    mob.trait(EntityTrait.MONSTER);
    mob.trait(EntityTrait.ENEMY);
    platform.world().add(mob);
    platform.host().track(mob);
    return mob;
  }

  /** A claim covering the locations {@code where} matches, in which the player may do nothing. */
  private record Claim(Predicate<Loc> where) implements ProtectorPort {
    @Override
    public List<String> defaultProtectors() {
      return List.of("claims");
    }

    @Override
    public List<String> allProtectors() {
      return List.of("claims");
    }

    @Override
    public boolean allows(Set<String> protectors, ProtectionCheck check, PlayerRef player, Loc location,
                          EntityRef victim, Adaptation<?> adaptation) {
      return location == null || !where.test(location);
    }
  }
}
