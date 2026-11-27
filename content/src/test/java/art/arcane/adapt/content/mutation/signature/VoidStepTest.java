package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.rift.upgraded.PhaseStep;
import art.arcane.adapt.content.adaptation.stealth.upgraded.PhantomDecoy;
import art.arcane.adapt.content.adaptation.rift.RiftTeleports;
import art.arcane.adapt.content.skill.SkillRift;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.RecordingFx;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.common.input.ReportedJumps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Stealth and Rift, either leading, replace Convergence with Void Step. */
class VoidStepTest {
  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private MutationKit kit;
  private AdaptPlayer profile;
  private StealthSignatureMob target;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Shade", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillStealth.class, SkillRift.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
    StealthSignatureMob.floor(platform.world());
    target = StealthSignatureMob.at(platform.world(), 4.5D, 61D, 0.5D);
    player.setSneaking(true);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void aBlinkThenAnEmptyHandedClickStepsBehindTheTargetWithEitherLead() {
    kit.mutate("stealth", "rift");
    kit.learn("stealth", "rift", Recipe.CONVERGENCE, 1);
    decoy(1);
    assertThat(kit.adaptation("stealth", "rift", Recipe.CONVERGENCE).getSignature()).isInstanceOf(VoidStep.class);
    assertThat(kit.mutated("rift", "stealth").getMutationAdaptation(Recipe.CONVERGENCE).getSignature())
        .isInstanceOf(VoidStep.class);

    blink();
    click();

    assertThat(player.location().x()).isCloseTo(4.5D, within(1.0E-9D));
    assertThat(player.location().z()).isLessThan(target.location().z());
  }

  @Test
  void riftLeadingStealthStepsToo() {
    kit.mutate("rift", "stealth");
    kit.learn("rift", "stealth", Recipe.CONVERGENCE, 1);
    decoy(1);

    blink();
    click();

    assertThat(player.location().x()).isCloseTo(4.5D, within(1.0E-9D));
  }

  @Test
  void nothingWithoutABlinkTheMutationOrAnEmptyHand() {
    blink();
    click();
    assertThat(player.location().x()).as("no mutation").isCloseTo(0.5D, within(1.0E-9D));

    kit.mutate("stealth", "rift");
    kit.learn("stealth", "rift", Recipe.CONVERGENCE, 1);
    decoy(1);
    click();
    assertThat(player.location().x()).as("no blink").isCloseTo(0.5D, within(1.0E-9D));

    blink();
    player.holding(FakeItem.tool("iron_sword", 250));
    click();
    assertThat(player.location().x()).as("hand full").isCloseTo(0.5D, within(1.0E-9D));
  }

  @Test
  void aSecondStepWaitsOutTheCooldownEvenAfterAnotherBlink() {
    kit.mutate("stealth", "rift");
    kit.learn("stealth", "rift", Recipe.CONVERGENCE, 1);
    decoy(1);

    blink();
    click();
    player.moveTo(new Loc(platform.world(), 0.5D, 61D, 0.5D));
    blink();
    click();

    assertThat(player.location().x()).isCloseTo(0.5D, within(1.0E-9D));
  }

  @Test
  void aCreatureThatCanSeeYouCannotBeSteppedBehind() {
    kit.mutate("stealth", "rift");
    kit.learn("stealth", "rift", Recipe.CONVERGENCE, 3);
    decoy(1);
    player.setSneaking(false);

    blink();
    click();

    assertThat(player.location().x()).isCloseTo(0.5D, within(1.0E-9D));
  }

  @Test
  void aPelletStepAndAVoidStepShareOneCooldown() {
    kit.mutate("stealth", "rift");
    kit.learn("stealth", "rift", Recipe.CONVERGENCE, 3);
    PhantomDecoy decoy = upgrade("stealth", PhantomDecoy.class);
    profile.getData().getSkillLine("stealth").setAdaptation(decoy, 3);
    player.holding(pellets(decoy, 2));

    adapt.post(new PlayerInteractEntityEvent(player, target, Hand.MAIN_HAND, null));
    platform.scheduler().advance(2);
    assertThat(player.location().x()).as("pellet step").isCloseTo(4.5D, within(1.0E-9D));
    player.moveTo(new Loc(platform.world(), 0.5D, 61D, 0.5D));
    player.holding(FakeItem.empty());
    blink();
    click();

    assertThat(player.location().x()).isCloseTo(0.5D, within(1.0E-9D));
  }

  @Test
  void withoutPhantomDecoyThereIsNoStep() {
    kit.mutate("stealth", "rift");
    kit.learn("stealth", "rift", Recipe.CONVERGENCE, 3);

    blink();
    click();

    assertThat(player.location().x()).isCloseTo(0.5D, within(1.0E-9D));
  }

  @Test
  void theStepRangeIsThePhantomDecoysAndTheBlinkWindowGrowsWithTheLevel() {
    kit.mutate("stealth", "rift");
    VoidStep step = (VoidStep) kit.learn("stealth", "rift", Recipe.CONVERGENCE, 3).getSignature();
    PhantomDecoy decoy = decoy(1);
    target.moveTo(new Loc(platform.world(), decoy.getStepRange(1) + 1.5D, 61D, 0.5D));

    blink();
    click();

    assertThat(player.location().x()).isCloseTo(0.5D, within(1.0E-9D));
    assertThat(step.blinkWindowMillis(1)).isEqualTo(3_000L);
    assertThat(step.blinkWindowMillis(3)).isGreaterThan(step.blinkWindowMillis(2))
        .isGreaterThan(step.blinkWindowMillis(1));
  }

  @Test
  void anUnhuntedPhaseStepBlinkLeavesNoParticles() {
    kit.mutate("stealth", "rift");
    kit.learn("stealth", "rift", Recipe.CONVERGENCE, 1);

    assertThat(phaseBlinkParticles()).isEmpty();
    assertThat(player.location().distance(new Loc(platform.world(), 0.5D, 61D, 0.5D))).isGreaterThan(2D);
  }

  @Test
  void aBlinkWithoutTheSignatureKeepsItsParticles() {
    kit.mutate("stealth", "rift");
    assertThat(phaseBlinkParticles()).isNotEmpty();
  }

  @Test
  void aHuntedBlinkKeepsItsParticles() {
    kit.mutate("stealth", "rift");
    kit.learn("stealth", "rift", Recipe.CONVERGENCE, 1);
    target.setTarget(player);

    assertThat(phaseBlinkParticles()).isNotEmpty();
  }

  private List<RecordingFx.Particle> phaseBlinkParticles() {
    PhaseStep phase = upgrade("rift", PhaseStep.class);
    profile.getData().getSkillLine("rift").setAdaptation(phase, 1);
    for (int x = -45; x <= 45; x++) {
      for (int z = -45; z <= 45; z++) {
        platform.world().set(x, 60, z, "stone");
      }
    }
    platform.fx().clear();
    doubleJump();
    return platform.fx().particles();
  }

  private PhantomDecoy decoy(int level) {
    PhantomDecoy decoy = upgrade("stealth", PhantomDecoy.class);
    profile.getData().getSkillLine("stealth").setAdaptation(decoy, level);
    return decoy;
  }

  private <A extends Adaptation<?>> A upgrade(String skill, Class<A> type) {
    return SkillHost.of(kit.registry().getSkill(skill)).getUpgrades().stream().filter(type::isInstance).map(type::cast)
        .findFirst().orElseThrow();
  }

  private static ItemRef pellets(PhantomDecoy decoy, int amount) {
    ItemRef stack = ((MutationTool<CustomItem.Plain>) decoy.getTools().getFirst()).create(CustomItem.Plain.INSTANCE);
    stack.setAmount(amount);
    return stack;
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

  private void blink() {
    Loc here = player.location();
    RiftTeleports.blinking(player, here);
    adapt.post(new PlayerTeleportEvent(player, here, here, TeleportCause.PLUGIN));
  }

  private void click() {
    adapt.post(new PlayerInteractEntityEvent(player, target, Hand.MAIN_HAND, null));
    platform.scheduler().advance(2);
  }
}
