package art.arcane.adapt.content.adaptation.golemancy.upgraded;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMastery;
import art.arcane.adapt.api.golem.GolemRuneLayout;
import art.arcane.adapt.api.golem.GolemRunes;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.overseer.Overseer;
import art.arcane.adapt.api.golem.overseer.OverseerReceiver;
import art.arcane.adapt.api.golem.overseer.OverseerSignal;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.content.adaptation.golemancy.GolemFrostFrame;
import art.arcane.adapt.content.skill.SkillCrafting;
import art.arcane.adapt.content.skill.SkillGolemancy;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.value.Id;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GolemancyUpgradesRuntimeTest {
  private static final Id SNOW_GOLEM = Id.minecraft("snow_golem");

  @TempDir
  Path data;

  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private SkillRegistry registry;
  private final List<OverseerReceiver> receivers = new ArrayList<>();

  @BeforeEach
  void start() {
    TestPlatform platform = TestPlatform.builder(data).build();
    player = platform.player("Artificer", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillGolemancy.class, SkillCrafting.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    receivers.forEach(Overseer::unregister);
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void runeforgedOpensEveryRuneSlotPlusTheMasterworkSlotOnAnyGolem() {
    mutate();
    Runeforged runeforged = learn(Runeforged.class, 3);

    GolemRuneLayout layout = GolemRunes.layout(GolemBody.IRON, player.uuid(), false);

    assertThat(layout.unlockedSlots()).isEqualTo(6);
    assertThat(layout.maxTier()).isEqualTo(3);
    assertThat(runeforged.perks(3, false).tierUp()).isTrue();
    assertThat(runeforged.perks(1, false).tierUp()).isFalse();
  }

  @Test
  void runeforgedGivesMasterworksAThirdExtraRuneSlot() {
    mutate();
    Runeforged runeforged = learn(Runeforged.class, 1);

    assertThat(runeforged.perks(1, true).extraRuneSlots()).isEqualTo(runeforged.perks(1, false).extraRuneSlots() + 1);
    assertThat(GolemRunes.layout(GolemBody.IRON, player.uuid(), true).unlockedSlots())
        .isGreaterThan(GolemRunes.layout(GolemBody.IRON, player.uuid(), false).unlockedSlots());
  }

  @Test
  void runeforgedTwinsTwoThenFourThenEveryGolem() {
    mutate();
    Runeforged runeforged = learn(Runeforged.class, 1);
    assertThat(GolemMastery.maxTwins(player.uuid(), null)).isEqualTo(2);

    learn(Runeforged.class, 2);
    assertThat(GolemMastery.maxTwins(player.uuid(), null)).isEqualTo(4);

    learn(Runeforged.class, 3);
    assertThat(GolemMastery.maxTwins(player.uuid(), null)).isEqualTo(Integer.MAX_VALUE);
    assertThat(runeforged.freeTwins()).isEqualTo(2);
    assertThat(runeforged.secondCoreSpeed()).isEqualTo(0.75D);
  }

  @Test
  void runeforgedTwinsNothingForAPlayerWhoHasNotMutated() {
    learn(Runeforged.class, 3);

    assertThat(GolemMastery.maxTwins(player.uuid(), null)).isZero();
  }

  @Test
  void runeforgedDoesNothingForAPlayerWhoHasNotMutated() {
    learn(Runeforged.class, 3);

    assertThat(GolemRunes.layout(GolemBody.IRON, player.uuid(), false).unlockedSlots()).isZero();
  }

  @Test
  void grandOverseerSendsCrookSignalsFarther() {
    mutate();
    GrandOverseer overseer = learn(GrandOverseer.class, 3);
    List<OverseerSignal> heard = listen();

    Overseer.commander().orElseThrow().send(player);

    assertThat(heard).hasSize(1);
    assertThat(heard.getFirst().radius()).isEqualTo(64D * overseer.getReach(3));
  }

  @Test
  void grandOverseerDoesNothingForAPlayerWhoHasNotMutated() {
    learn(GrandOverseer.class, 3);
    List<OverseerSignal> heard = listen();

    Overseer.commander().orElseThrow().send(player);

    assertThat(heard).isEmpty();
  }

  @Test
  void forgemasterShieldsFrostGolemsFromMeltingLikeATopLevelFrostFrame() {
    mutate();
    learn(Forgemaster.class, 3);

    assertThat(melt().isCancelled()).isTrue();
  }

  @Test
  void forgemasterDoesNothingForAPlayerWhoHasNotMutated() {
    learn(Forgemaster.class, 3);

    assertThat(melt().isCancelled()).isFalse();
  }

  @Test
  void aMutatedGolemancerWithoutForgemasterCannotBindOrRunCores() {
    mutate();

    assertThat(original("golem-animus-binding").getActiveLevel(player)).isZero();
    assertThat(GolemMastery.coreUnlocked(player.uuid(), GolemCoreType.HARVESTER)).isFalse();
  }

  @Test
  void forgemasterRunsBindingNodesAndEveryCoreAtTheirTopLevelFromLevelOne() {
    mutate();
    learn(Forgemaster.class, 1);

    Adaptation<?> binding = original("golem-animus-binding");
    assertThat(binding.getActiveLevel(player)).isEqualTo(binding.getMaxLevel());
    Adaptation<?> weaving = original("golem-node-weaving");
    assertThat(GolemancyRuntime.nodeWeaving().orElseThrow().level(player)).isEqualTo(weaving.getMaxLevel());
    for (GolemCoreType core : GolemCoreType.values()) {
      if (core == GolemCoreType.BLANK) {
        continue;
      }
      SimpleAdaptation<?> adaptation = (SimpleAdaptation<?>) original("golem-" + core.id() + "-core");
      assertThat(GolemMastery.coreUnlocked(player.uuid(), core)).as("%s unlocked", core).isTrue();
      assertThat(adaptation.ownerLevel(player.uuid())).as("%s owner level", core).isEqualTo(adaptation.getMaxLevel());
    }
  }

  @Test
  void forgemasterRaisesTheFramesWithItsLevel() {
    mutate();
    Adaptation<?> frost = original("golem-frost-frame");
    learn(Forgemaster.class, 1);
    assertThat(frost.getLevel(profile)).isEqualTo(frost.getMaxLevel() - 2);

    learn(Forgemaster.class, 3);
    assertThat(frost.getLevel(profile)).isEqualTo(frost.getMaxLevel());
  }

  @Test
  void runeforgedLetsMasterworksHappenForAMutatedGolemancer() {
    mutate();
    Adaptation<?> masterwork = original("golem-masterwork");
    assertThat(GolemMastery.maxMasterworks(player.uuid())).isZero();

    learn(Runeforged.class, 3);

    assertThat(masterwork.getActiveLevel(player)).isEqualTo(masterwork.getMaxLevel());
    assertThat(GolemMastery.maxMasterworks(player.uuid())).isPositive();
    assertThat(masterwork.hasTickDemand()).isTrue();
  }

  private EntityDamageEvent melt() {
    MobRef golem = mock(MobRef.class);
    when(golem.type()).thenReturn(SNOW_GOLEM);
    GolemHandle handle = mock(GolemHandle.class);
    when(handle.owner()).thenReturn(player.uuid());
    try (MockedStatic<GolemancyRuntime> runtime = mockStatic(GolemancyRuntime.class, CALLS_REAL_METHODS)) {
      runtime.when(() -> GolemancyRuntime.handle(any())).thenReturn(Optional.of(handle));
      EntityDamageEvent melting = new EntityDamageEvent(golem, DamageCause.MELTING, Id.minecraft("on_fire"), 1D, FinalDamage.IDENTITY);
      frostFrame().on(melting);
      return melting;
    }
  }

  private Adaptation<?> original(String id) {
    return registry.getAdaptation(id);
  }

  private GolemFrostFrame frostFrame() {
    return registry.getSkill("golemancy").getAdaptations().stream().filter(GolemFrostFrame.class::isInstance)
        .map(GolemFrostFrame.class::cast).findFirst().orElseThrow();
  }

  private List<OverseerSignal> listen() {
    List<OverseerSignal> heard = new ArrayList<>();
    OverseerReceiver receiver = heard::add;
    Overseer.register(receiver);
    receivers.add(receiver);
    return heard;
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("golemancy", "crafting")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("golemancy"), registry.getSkill("crafting"), "Artificer"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = registry.getSkill("golemancy").getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
    profile.getData().getSkillLine("golemancy").setAdaptation(upgrade, level);
    return upgrade;
  }
}
