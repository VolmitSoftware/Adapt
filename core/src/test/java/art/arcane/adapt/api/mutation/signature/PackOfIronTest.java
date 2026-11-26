package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.golem.GolemBrain;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.taming.upgraded.Alpha;
import art.arcane.adapt.content.adaptation.taming.upgraded.BloodBond;
import art.arcane.adapt.content.adaptation.taming.upgraded.OneMind;
import art.arcane.adapt.content.skill.SkillGolemancy;
import art.arcane.adapt.content.skill.SkillTaming;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Taming leading Golemancy: golems take Alpha's buffs, share hits under Blood Bond and answer the whistle. */
class PackOfIronTest {
  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private AdaptPlayer profile;
  private MutationKit kit;
  private final List<GolemHandle> golems = new ArrayList<>();
  private final GolemBrain brain = mock(GolemBrain.class);
  private MockedStatic<GolemancyRuntime> runtime;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Packmaster", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillTaming.class, SkillGolemancy.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
    GolemRegistry registry = mock(GolemRegistry.class);
    when(registry.loadedOf(player.uuid())).thenAnswer(invocation -> List.copyOf(golems));
    runtime = mockStatic(GolemancyRuntime.class, CALLS_REAL_METHODS);
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
    runtime.when(() -> GolemancyRuntime.brain(any(EntityRef.class))).thenReturn(Optional.of(brain));
    runtime.when(() -> GolemancyRuntime.handle(any(EntityRef.class))).thenReturn(Optional.empty());
  }

  @AfterEach
  void stop() {
    runtime.close();
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void golemsInsideTheAuraTakeAlphasBuffsAndLoseThemOnceTheyLeave() {
    kit.mutate("taming", "golemancy");
    kit.learn("taming", "golemancy", Recipe.CONVERGENCE, 1);
    upgrade(Alpha.class, 1);
    PairMob near = golem(4.5D);
    PairMob far = golem(30.5D);

    lead();
    assertThat(near.attributeValue(Attributes.MAX_HEALTH)).isCloseTo(115D, within(1.0E-6D));
    assertThat(far.attributeValue(Attributes.MAX_HEALTH)).isEqualTo(100D);

    near.moveTo(new Loc(platform.world(), 40.5D, 61D, 0.5D));
    lead();
    assertThat(near.attributeValue(Attributes.MAX_HEALTH)).isEqualTo(100D);
  }

  @Test
  void withoutAlphaOrTheLearnedAdaptationGolemsStayAsTheyAre() {
    kit.mutate("taming", "golemancy");
    PairMob near = golem(4.5D);
    upgrade(Alpha.class, 1);
    lead();
    assertThat(near.attributeValue(Attributes.MAX_HEALTH)).isEqualTo(100D);

    kit.learn("taming", "golemancy", Recipe.CONVERGENCE, 1);
    upgrade(Alpha.class, 0);
    lead();
    assertThat(near.attributeValue(Attributes.MAX_HEALTH)).isEqualTo(100D);
  }

  @Test
  void withBloodBondGolemsTakeAShareOfYourHitsDownToTheirFloor() {
    kit.mutate("taming", "golemancy");
    kit.learn("taming", "golemancy", Recipe.CONVERGENCE, 1);
    upgrade(BloodBond.class, 1);
    PairMob golem = golem(4.5D);

    EntityDamageEvent hit = adapt.post(new EntityDamageEvent(player, DamageCause.ENTITY_ATTACK, Id.minecraft("mob_attack"), 10D,
        FinalDamage.IDENTITY));
    platform.scheduler().advance(2);
    assertThat(hit.damage()).isCloseTo(9D, within(1.0E-9D));
    assertThat(golem.damageTaken()).isCloseTo(1D, within(1.0E-9D));

    golem.setHealth(4D);
    EntityDamageEvent spared = adapt.post(new EntityDamageEvent(player, DamageCause.ENTITY_ATTACK, Id.minecraft("mob_attack"), 10D,
        FinalDamage.IDENTITY));
    assertThat(spared.damage()).isEqualTo(10D);
  }

  @Test
  void golemsShareWhatLandsAfterArmourAndNeverFallsOrBurns() {
    kit.mutate("taming", "golemancy");
    kit.learn("taming", "golemancy", Recipe.CONVERGENCE, 1);
    upgrade(BloodBond.class, 1);
    PairMob golem = golem(4.5D);

    EntityDamageEvent fall = adapt.post(new EntityDamageEvent(player, DamageCause.FALL, Id.minecraft("fall"), 10D,
        FinalDamage.IDENTITY));
    EntityDamageEvent burn = adapt.post(new EntityDamageEvent(player, DamageCause.FIRE_TICK, Id.minecraft("on_fire"), 10D,
        FinalDamage.IDENTITY));
    platform.scheduler().advance(2);
    assertThat(fall.damage()).isEqualTo(10D);
    assertThat(burn.damage()).isEqualTo(10D);
    assertThat(golem.damageTaken()).isZero();

    EntityDamageEvent armoured = adapt.post(new EntityDamageEvent(player, DamageCause.ENTITY_ATTACK, Id.minecraft("mob_attack"),
        20D, (double base) -> base / 2D));
    platform.scheduler().advance(2);
    assertThat(golem.damageTaken()).isCloseTo(1D, within(1.0E-9D));
    assertThat(armoured.damage()).isCloseTo(18D, within(1.0E-9D));
  }

  @Test
  void theWhistleSendsGolemsAtWhatYouClickOnlyWithOneMind() {
    kit.mutate("taming", "golemancy");
    kit.learn("taming", "golemancy", Recipe.CONVERGENCE, 1);
    golem(4.5D);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 6.5D, 61D, 0.5D);
    player.holding(whistle());

    click(zombie);
    verify(brain, never()).command(any(EntityRef.class), anyLong());

    upgrade(OneMind.class, 1);
    click(zombie);
    platform.scheduler().advance(2);
    verify(brain).command(any(EntityRef.class), anyLong());
    assertThat(player.heardSounds()).isNotEmpty();
  }

  @Test
  void theWhistleNeverSendsGolemsAtYourOwnPets() {
    kit.mutate("taming", "golemancy");
    kit.learn("taming", "golemancy", Recipe.CONVERGENCE, 3);
    upgrade(OneMind.class, 1);
    golem(4.5D);
    PairMob wolf = PairMob.pet(platform, player, 6.5D, 0.5D);
    player.holding(whistle());

    click(wolf);
    platform.scheduler().advance(2);

    verify(brain, never()).command(any(EntityRef.class), anyLong());
  }

  @Test
  void theReverseOrderHasNoSignature() {
    kit.mutate("golemancy", "taming");
    assertThat(kit.adaptation("golemancy", "taming", Recipe.CONVERGENCE).getSignature()).isNotInstanceOf(PackOfIron.class);
  }

  private void click(FakeLiving target) {
    adapt.post(new PlayerInteractEntityEvent(player, target, Hand.MAIN_HAND, new Vec3(target.location().x(), 61D, 0.5D)));
  }

  private void lead() {
    ((PackOfIron) kit.adaptation("taming", "golemancy", Recipe.CONVERGENCE).getSignature()).onTick();
    platform.scheduler().advance(2);
  }

  private <A extends Adaptation<?>> void upgrade(Class<A> type, int level) {
    A upgrade = kit.registry().getSkill("taming").getUpgrades().stream().filter(type::isInstance).map(type::cast)
        .findFirst().orElseThrow();
    profile.getData().getSkillLine("taming").setAdaptation(upgrade, level);
  }

  private ItemRef whistle() {
    OneMind mind = kit.registry().getSkill("taming").getUpgrades().stream().filter(OneMind.class::isInstance)
        .map(OneMind.class::cast).findFirst().orElseThrow();
    @SuppressWarnings("unchecked")
    MutationTool<MutationTool.NoData> tool = (MutationTool<MutationTool.NoData>) mind.getTools().getFirst();
    return tool.create(new MutationTool.NoData());
  }

  private PairMob golem(double x) {
    PairMob body = platform.world().add(new PairMob(Id.minecraft("iron_golem"), new Loc(platform.world(), x, 61D, 0.5D)));
    body.setAttributeBase(Attributes.MAX_HEALTH, 100D);
    body.setAttributeBase(Attributes.ATTACK_DAMAGE, 15D);
    body.setHealth(100D);
    GolemHandle handle = mock(GolemHandle.class);
    when(handle.owner()).thenReturn(player.uuid());
    when(handle.entity()).thenReturn(body);
    when(handle.state()).thenReturn(mock(GolemState.class));
    golems.add(handle);
    return body;
  }
}
