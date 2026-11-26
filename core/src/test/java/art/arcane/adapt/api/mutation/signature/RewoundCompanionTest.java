package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillChronos;
import art.arcane.adapt.content.skill.SkillTaming;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Chronos with Taming: a pet's killing blow rewinds it to where it stood with the health it had. */
class RewoundCompanionTest {
  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private MutationKit kit;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Packmaster", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillChronos.class, SkillTaming.class));
    AdaptPlayer profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void aKillingBlowRewindsThePetToItsEarlierPlaceAndHealth() {
    kit.mutate("chronos", "taming");
    kit.learn("chronos", "taming", Recipe.CONVERGENCE, 3);
    FakeLiving wolf = wolf(-1.5D);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 3.5D, 61D, 0.5D);

    assertThat(hit(wolf, zombie, 6D).isCancelled()).isFalse();
    wolf.setHealth(14D);
    wolf.moveTo(new Loc(platform.world(), 5.5D, 61D, 0.5D));
    EntityDamageEvent lethal = hit(wolf, zombie, 30D);

    assertThat(lethal.isCancelled()).isTrue();
    assertThat(wolf.health()).isEqualTo(20D);
    assertThat(wolf.location().x()).isEqualTo(-1.5D);
    assertThat(player.heardSounds()).isNotEmpty();
  }

  @Test
  void tamingLeadingWorksTheSameWay() {
    kit.mutate("taming", "chronos");
    kit.learn("taming", "chronos", Recipe.CONVERGENCE, 1);
    FakeLiving wolf = wolf(-1.5D);
    wolf.setHealth(5D);

    assertThat(fall(wolf, 10D).isCancelled()).isTrue();
    assertThat(wolf.health()).isEqualTo(5D);
  }

  @Test
  void eachPetRewindsOncePerCooldownWhileOthersStillCan() {
    kit.mutate("chronos", "taming");
    kit.learn("chronos", "taming", Recipe.CONVERGENCE, 3);
    FakeLiving first = wolf(-1.5D);
    FakeLiving second = wolf(-3.5D);

    assertThat(fall(first, 40D).isCancelled()).isTrue();
    assertThat(fall(first, 40D).isCancelled()).isFalse();
    assertThat(fall(second, 40D).isCancelled()).isTrue();
  }

  @Test
  void nothingRewindsWithoutTheMutationOrForSomeoneElsesPet() {
    FakeLiving wolf = wolf(-1.5D);
    assertThat(fall(wolf, 40D).isCancelled()).isFalse();

    kit.mutate("chronos", "taming");
    kit.learn("chronos", "taming", Recipe.CONVERGENCE, 3);
    FakeLiving stray = wolf(-2.5D);
    stray.setTameOwner(UUID.randomUUID());

    assertThat(fall(stray, 40D).isCancelled()).isFalse();
  }

  @Test
  void aBlowThatLeavesThePetAliveIsNotTouched() {
    kit.mutate("chronos", "taming");
    kit.learn("chronos", "taming", Recipe.CONVERGENCE, 3);
    FakeLiving wolf = wolf(-1.5D);

    assertThat(fall(wolf, 4D).isCancelled()).isFalse();
    assertThat(fall(wolf, 40D).isCancelled()).isTrue();
  }

  private FakeLiving wolf(double x) {
    FakeLiving wolf = platform.world().add(new FakeLiving(Id.minecraft("wolf"), new Loc(platform.world(), x, 61D, 0.5D)));
    wolf.trait(EntityTrait.TAMEABLE);
    wolf.setTameOwner(player.uuid());
    wolf.setAttributeBase(Attributes.MAX_HEALTH, 20D);
    wolf.setHealth(20D);
    platform.host().track(wolf);
    return wolf;
  }

  private EntityDamageEvent hit(FakeLiving pet, FakeLiving attacker, double damage) {
    return adapt.post(new EntityDamageByEntityEvent(pet, attacker, attacker, DamageCause.ENTITY_ATTACK,
        Id.minecraft("mob_attack"), damage, false, FinalDamage.IDENTITY));
  }

  private EntityDamageEvent fall(FakeLiving pet, double damage) {
    return adapt.post(new EntityDamageEvent(pet, DamageCause.FALL, Id.minecraft("fall"), damage, FinalDamage.IDENTITY));
  }
}
