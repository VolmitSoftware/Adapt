package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillBlocking;
import art.arcane.adapt.content.skill.SkillUnarmed;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
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

/** Unarmed and Blocking replace Convergence with Iron Guard: a bare-hand parry. */
class IronGuardTest {
  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private MutationKit kit;
  private FakeLiving zombie;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Brawler", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillUnarmed.class, SkillBlocking.class));
    AdaptPlayer profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
    zombie = MutationKit.zombie(platform.world(), 0.5D, 61D, 2.5D);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void raisedEmptyHandsParryTheHitSendHalfBackAndLoadTheCounter() {
    kit.mutate("unarmed", "blocking");
    kit.learn("unarmed", "blocking", Recipe.CONVERGENCE, 3);
    assertThat(kit.adaptation("unarmed", "blocking", Recipe.CONVERGENCE).getSignature()).isInstanceOf(IronGuard.class);

    raise();
    EntityDamageByEntityEvent hit = struck(8D);

    assertThat(hit.isCancelled()).isTrue();
    assertThat(zombie.damageTaken()).isCloseTo(4D, within(0.001D));
    assertThat(zombie.hasEffect(Id.minecraft("slowness"))).isTrue();
    assertThat(kit.hit(player, zombie, 10D, false).damage()).isCloseTo(15.5D, within(0.001D));
    assertThat(kit.hit(player, zombie, 10D, false).damage()).isCloseTo(10D, within(0.001D));
    assertThat(player.heardSounds()).isNotEmpty();
  }

  @Test
  void eitherSkillMayLead() {
    kit.mutate("blocking", "unarmed");
    kit.learn("blocking", "unarmed", Recipe.CONVERGENCE, 1);

    raise();

    assertThat(struck(8D).isCancelled()).isTrue();
  }

  @Test
  void nothingParriesWithoutTheLearnedAdaptation() {
    raise();
    assertThat(struck(8D).isCancelled()).isFalse();

    kit.mutate("unarmed", "blocking");
    raise();
    assertThat(struck(8D).isCancelled()).isFalse();
    assertThat(zombie.damageTaken()).isZero();
  }

  @Test
  void aLoadedCounterIsLostWhenTheSignatureIsTurnedOff() {
    kit.mutate("unarmed", "blocking");
    kit.learn("unarmed", "blocking", Recipe.CONVERGENCE, 3);
    raise();
    assertThat(struck(8D).isCancelled()).isTrue();

    kit.adaptation("unarmed", "blocking", Recipe.CONVERGENCE).getSignature().getConfig().enabled = false;

    assertThat(kit.hit(player, zombie, 10D, false).damage()).isCloseTo(10D, within(0.001D));
  }

  @Test
  void aSecondParryWaitsForTheCooldown() {
    kit.mutate("unarmed", "blocking");
    kit.learn("unarmed", "blocking", Recipe.CONVERGENCE, 3);

    raise();
    assertThat(struck(8D).isCancelled()).isTrue();
    raise();

    assertThat(struck(8D).isCancelled()).isFalse();
  }

  @Test
  void spammingRaiseWithAWeaponOrAfterTheWindowDoesNotParry() throws InterruptedException {
    kit.mutate("unarmed", "blocking");
    kit.learn("unarmed", "blocking", Recipe.CONVERGENCE, 1);

    player.holding(FakeItem.tool("iron_sword", 250));
    raise();
    assertThat(struck(8D).isCancelled()).isFalse();

    player.holding(FakeItem.empty());
    raise();
    Thread.sleep(400L);
    assertThat(struck(8D).isCancelled()).isFalse();
    assertThat(zombie.damageTaken()).isZero();
  }

  private void raise() {
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND, FakeItem.empty(),
        platform.world().blockAt(0, 60, 1), BlockFace.UP, new Vec3(0.5D, 61D, 1.5D), UseResult.DEFAULT, UseResult.DEFAULT));
  }

  private EntityDamageByEntityEvent struck(double damage) {
    return adapt.post(new EntityDamageByEntityEvent(player, zombie, zombie, DamageCause.ENTITY_ATTACK,
        Id.minecraft("mob_attack"), damage, false, FinalDamage.IDENTITY));
  }
}
