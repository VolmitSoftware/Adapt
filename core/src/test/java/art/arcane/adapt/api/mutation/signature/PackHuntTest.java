package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.content.adaptation.hunter.upgraded.ApexPredator;
import art.arcane.adapt.content.adaptation.hunter.upgraded.TrackersCompass;
import art.arcane.adapt.content.skill.SkillHunter;
import art.arcane.adapt.content.skill.SkillTaming;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Hunter and Taming in either order: pets hunt the compass's kind within the leash and ramp with your hits. */
class PackHuntTest {
  private static final Id COW = Id.minecraft("cow");

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private AdaptPlayer profile;
  private MutationKit kit;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Predator", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillHunter.class, SkillTaming.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
    player.holding(compass(COW));
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void petsGoAfterTheCompassKindOnTheirOwn() {
    kit.mutate("hunter", "taming");
    kit.learn("hunter", "taming", Recipe.CONVERGENCE, 1);
    PairMob wolf = PairMob.pet(platform, player, -1.5D, 0.5D);
    PairMob cow = PairMob.animal(platform, COW, 10.5D, 0.5D);

    hunt("hunter", "taming");

    assertThat(wolf.target()).isSameAs(cow);
    assertThat(player.heardSounds()).isNotEmpty();
  }

  @Test
  void tamingAsLeadHuntsToo() {
    kit.mutate("taming", "hunter");
    kit.learn("taming", "hunter", Recipe.CONVERGENCE, 1);
    PairMob wolf = PairMob.pet(platform, player, -1.5D, 0.5D);
    PairMob cow = PairMob.animal(platform, COW, 10.5D, 0.5D);

    hunt("taming", "hunter");

    assertThat(wolf.target()).isSameAs(cow);
  }

  @Test
  void petBitesOnTheQuarryRampWithApexPredatorsStacks() {
    kit.mutate("hunter", "taming");
    kit.learn("hunter", "taming", Recipe.CONVERGENCE, 3);
    profile.getData().getSkillLine("hunter").setAdaptation(apex(), 1);
    PairMob wolf = PairMob.pet(platform, player, -1.5D, 0.5D);
    PairMob cow = PairMob.animal(platform, COW, 10.5D, 0.5D);
    hunt("hunter", "taming");

    assertThat(bite(wolf, cow)).isCloseTo(4D, within(1.0E-9D));
    assertThat(bite(wolf, cow)).isCloseTo(4.4D, within(1.0E-9D));
    adapt.post(new EntityDamageByEntityEvent(cow, player, player, DamageCause.ENTITY_ATTACK,
        Id.minecraft("player_attack"), 4D, false, FinalDamage.IDENTITY));
    assertThat(bite(wolf, cow)).isCloseTo(5.2D, within(1.0E-9D));
    PairMob other = PairMob.animal(platform, COW, 12.5D, 4.5D);
    assertThat(bite(wolf, other)).isCloseTo(4D, within(1.0E-9D));
  }

  @Test
  void withoutApexPredatorPetBitesDoNotRamp() {
    kit.mutate("hunter", "taming");
    kit.learn("hunter", "taming", Recipe.CONVERGENCE, 3);
    PairMob wolf = PairMob.pet(platform, player, -1.5D, 0.5D);
    PairMob cow = PairMob.animal(platform, COW, 10.5D, 0.5D);
    hunt("hunter", "taming");

    assertThat(bite(wolf, cow)).isCloseTo(4D, within(1.0E-9D));
    assertThat(bite(wolf, cow)).isCloseTo(4D, within(1.0E-9D));
  }

  @Test
  void aNewQuarryIsOnlySoughtOnceTheCooldownPassed() {
    kit.mutate("hunter", "taming");
    kit.learn("hunter", "taming", Recipe.CONVERGENCE, 1);
    PairMob wolf = PairMob.pet(platform, player, -1.5D, 0.5D);
    PairMob cow = PairMob.animal(platform, COW, 10.5D, 0.5D);
    hunt("hunter", "taming");
    assertThat(wolf.target()).isSameAs(cow);

    cow.setHealth(0D);
    cow.remove();
    wolf.setTarget(null);
    PairMob next = PairMob.animal(platform, COW, 8.5D, 0.5D);
    hunt("hunter", "taming");
    hunt("hunter", "taming");

    assertThat(wolf.target()).isNull();
    assertThat(next.damageTaken()).isZero();
  }

  @Test
  void withoutAQuarryFarPetsKeepTheirOwnTargets() {
    kit.mutate("hunter", "taming");
    kit.learn("hunter", "taming", Recipe.CONVERGENCE, 1);
    PairMob wolf = PairMob.pet(platform, player, 40.5D, 0.5D);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 44.5D, 61D, 0.5D);
    wolf.setTarget(zombie);

    hunt("hunter", "taming");

    assertThat(wolf.target()).isSameAs(zombie);
    assertThat(wolf.pathedTo()).isNull();
  }

  @Test
  void theLevelSetsTheRangeAndThePackSize() {
    kit.mutate("hunter", "taming");
    kit.learn("hunter", "taming", Recipe.CONVERGENCE, 1);
    PairMob first = PairMob.pet(platform, player, -1.5D, 0.5D);
    PairMob second = PairMob.pet(platform, player, -2.5D, 0.5D);
    PairMob third = PairMob.pet(platform, player, -3.5D, 0.5D);
    PairMob far = PairMob.animal(platform, COW, 24.5D, 0.5D);

    hunt("hunter", "taming");
    assertThat(first.target()).isNull();

    kit.learn("hunter", "taming", Recipe.CONVERGENCE, 3);
    hunt("hunter", "taming");
    assertThat(List.of(first.target(), second.target(), third.target())).containsOnly(far);

    PackHunt pack = signature("hunter", "taming");
    assertThat(pack.pets(1)).isEqualTo(2);
    assertThat(pack.pets(3)).isEqualTo(4);
  }

  @Test
  void petsPastTheLeashDropTheQuarryAndComeBack() {
    kit.mutate("hunter", "taming");
    kit.learn("hunter", "taming", Recipe.CONVERGENCE, 1);
    PairMob wolf = PairMob.pet(platform, player, -1.5D, 0.5D);
    PairMob cow = PairMob.animal(platform, COW, 10.5D, 0.5D);
    hunt("hunter", "taming");
    assertThat(wolf.target()).isSameAs(cow);

    wolf.moveTo(new Loc(platform.world(), 40.5D, 61D, 0.5D));
    hunt("hunter", "taming");

    assertThat(wolf.target()).isNull();
    assertThat(wolf.pathedTo()).isNotNull();
  }

  @Test
  void bredNamedAndTamedAnimalsOfTheKindAreNeverHunted() {
    kit.mutate("hunter", "taming");
    kit.learn("hunter", "taming", Recipe.CONVERGENCE, 3);
    PairMob wolf = PairMob.pet(platform, player, -1.5D, 0.5D);
    PairMob bred = PairMob.animal(platform, COW, 4.5D, 0.5D);
    MobOrigin.record(bred, CreatureSpawnEvent.Reason.BREEDING);
    PairMob named = PairMob.animal(platform, COW, 5.5D, 0.5D);
    named.setCustomName("Bessie", true);
    PairMob owned = PairMob.animal(platform, COW, 6.5D, 0.5D);
    owned.trait(EntityTrait.TAMEABLE);
    owned.setTameOwner(player.uuid());

    hunt("hunter", "taming");

    assertThat(wolf.target()).isNull();
  }

  @Test
  void nothingHuntsWithoutTheLearnedAdaptationOrACompass() {
    PairMob wolf = PairMob.pet(platform, player, -1.5D, 0.5D);
    PairMob cow = PairMob.animal(platform, COW, 10.5D, 0.5D);
    kit.mutate("hunter", "taming");
    hunt("hunter", "taming");
    assertThat(wolf.target()).isNull();

    kit.learn("hunter", "taming", Recipe.CONVERGENCE, 1);
    player.holding(FakeItem.empty());
    hunt("hunter", "taming");
    assertThat(wolf.target()).isNull();
    assertThat(cow.damageTaken()).isZero();
  }

  private double bite(FakeLiving wolf, FakeLiving prey) {
    EntityDamageByEntityEvent bite = adapt.post(new EntityDamageByEntityEvent(prey, wolf, wolf, DamageCause.ENTITY_ATTACK,
        Id.minecraft("mob_attack"), 4D, false, FinalDamage.IDENTITY));
    return bite.damage();
  }

  private void hunt(String lead, String partner) {
    signature(lead, partner).onTick();
    platform.scheduler().advance(2);
  }

  private PackHunt signature(String lead, String partner) {
    return (PackHunt) kit.adaptation(lead, partner, Recipe.CONVERGENCE).getSignature();
  }

  private ApexPredator apex() {
    return PairParts.owned("hunter", ApexPredator.class);
  }

  private ItemRef compass(Id kind) {
    TrackersCompass tool = (TrackersCompass) PairParts.owned("hunter", ApexPredator.class).getTools().getFirst();
    ItemRef item = tool.create(new TrackersCompass.Data());
    item.data().set(DataKey.adapt("mutation_tool_data", DataCodecs.STRING),
        "{\"kinds\":[\"" + kind + "\"],\"selected\":0,\"killed\":[]}");
    return item;
  }
}
