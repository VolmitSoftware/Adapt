package art.arcane.adapt.engine.player;

import art.arcane.adapt.api.player.OrphanedLine;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.event.AdaptationLearnEvent;
import art.arcane.adapt.api.event.AdaptationUnlearnEvent;
import art.arcane.adapt.api.event.KnowledgeEvent;
import art.arcane.adapt.api.event.SkillLevelUpEvent;
import art.arcane.adapt.api.event.SkillMutateEvent;
import art.arcane.adapt.api.event.SkillUnmutateEvent;
import art.arcane.adapt.api.event.SkillXpEvent;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.player.LearnOptions;
import art.arcane.adapt.api.player.LearnResult;
import art.arcane.adapt.api.player.MutationResult;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.player.SkillProgress;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.xp.XpAward;
import art.arcane.adapt.content.skill.SkillAxes;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.adaptation.AdaptationLearningTransaction;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.engine.xp.XPMultiplier;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerProfileTest {
  private static final Id SWORDS = Id.adapt("swords");
  private static final Id STEALTH = Id.adapt("stealth");

  @TempDir
  Path data;

  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer runtime;
  private EnginePlayerProfile profile;
  private final List<AdaptEvent> events = new ArrayList<>();

  @BeforeEach
  void start() {
    TestPlatform platform = TestPlatform.builder(data).build();
    player = platform.player("Learner", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, SkillStealth.class, SkillAxes.class));
    runtime = adapt.join(player);
    runtime.getData().setMasterXp(XP.getXpForLevel(40));
    profile = PlayerApi.directory().require(player);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void theDirectoryFindsLoadedPlayersOnly() {
    assertThat(PlayerApi.directory().find(player)).contains(profile);
    assertThat(PlayerApi.directory().find(player.uuid())).contains(profile);
    assertThat(PlayerApi.directory().find(UUID.randomUUID())).isEmpty();
    assertThat(PlayerApi.directory().online()).containsExactly(profile);
    assertThat(profile.isReady()).isTrue();
    assertThat(profile.uuid()).isEqualTo(player.uuid());
    FakePlayer stranger = adapt.platform().player("Stranger", 3D, 61D, 3D);
    assertThatThrownBy(() -> PlayerApi.directory().require(stranger)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void skillProgressReadsAndSetsTheSavedLine() {
    SkillProgress swords = profile.skill(SWORDS);
    assertThat(swords.level()).isZero();
    assertThat(swords.xp()).isZero();

    swords.setLevel(7);
    assertThat(swords.level()).isEqualTo(7);
    assertThat(swords.xp()).isEqualTo(XP.getXpForLevel(7));
    assertThat(swords.xpToNextLevel()).isEqualTo(XP.getXpForLevel(8) - XP.getXpForLevel(7));
    swords.setLevel(2);
    assertThat(swords.level()).isEqualTo(2);
    assertThat(line("swords").getLastLevel()).isLessThanOrEqualTo(2);

    swords.addKnowledge(12L);
    swords.addKnowledge(-5L);
    assertThat(swords.knowledge()).isEqualTo(7L);
    swords.addKnowledge(-100L);
    assertThat(swords.knowledge()).isZero();
    assertThat(profile.skills()).extracting(SkillProgress::skill).containsExactly(SWORDS);
  }

  @Test
  void awardFiresTheXpEventBeforeTheGrantAndTheListenerSetsTheAmount() {
    listen(SkillXpEvent.class, (SkillXpEvent event) -> {
      assertThat(profile.skill(SWORDS).xp()).isZero();
      event.setAmount(event.amount() * 2D);
    });

    profile.skill(SWORDS).award(XpAward.of(40D));

    SkillXpEvent event = only(SkillXpEvent.class);
    assertThat(event.skill()).isEqualTo(SWORDS);
    assertThat(event.kind()).isEqualTo(XpAward.Kind.ADMINISTRATIVE);
    assertThat(event.source()).isEqualTo("api");
    assertThat(profile.skill(SWORDS).xp()).isEqualTo(80D);
  }

  @Test
  void aCancelledXpEventGrantsNothing() {
    listen(SkillXpEvent.class, (SkillXpEvent event) -> event.setCancelled(true));

    profile.skill(SWORDS).award(XpAward.of(40D));

    assertThat(profile.skill(SWORDS).xp()).isZero();
  }

  @Test
  void levelUpsFireOncePerLevelAfterTheLevelIsSetWithTheKnowledgeEachGranted() {
    listen(KnowledgeEvent.class, (KnowledgeEvent event) -> event.setAmount(event.amount() + 1L));
    listen(SkillLevelUpEvent.class, (SkillLevelUpEvent event) ->
        assertThat(line("swords").getLastLevel()).isGreaterThanOrEqualTo(event.level()));

    profile.skill(SWORDS).award(XpAward.of(XP.getXpForLevel(3) + 1D));
    line("swords").flushXpPool(null);
    runtime.getData().update(runtime);

    List<SkillLevelUpEvent> levels = all(SkillLevelUpEvent.class);
    assertThat(levels).extracting(SkillLevelUpEvent::level).containsExactly(1, 2, 3);
    long expected = 0L;
    for (int level = 0; level < 3; level++) {
      long granted = PlayerSkillLine.knowledgeForLevelUp(level) + 1L;
      assertThat(levels.get(level).knowledge()).isEqualTo(granted);
      expected += granted;
    }
    assertThat(profile.skill(SWORDS).knowledge()).isEqualTo(expected);
  }

  @Test
  void aCancelledKnowledgeEventLeavesKnowledgeAlone() {
    listen(KnowledgeEvent.class, (KnowledgeEvent event) -> event.setCancelled(true));

    profile.skill(SWORDS).addKnowledge(9L);

    assertThat(profile.skill(SWORDS).knowledge()).isZero();
    assertThat(only(KnowledgeEvent.class).amount()).isEqualTo(9L);
  }

  @Test
  void learningSpendsKnowledgeAfterTheLearnEvent() {
    Adaptation<?> adaptation = first(SWORDS);
    long cost = adaptation.getCostFor(2, 0);
    profile.skill(SWORDS).addKnowledge(cost + 3L);
    listen(AdaptationLearnEvent.class, (AdaptationLearnEvent event) ->
        assertThat(profile.skill(SWORDS).knowledge()).isEqualTo(cost + 3L));

    LearnResult result = profile.learn(adaptation.getId(), 2, LearnOptions.paying().quietly());

    assertThat(result).isEqualTo(new EngineLearnResult(LearnResult.Status.LEARNED, 0, 2, cost));
    AdaptationLearnEvent event = only(AdaptationLearnEvent.class);
    assertThat(event.adaptation()).isEqualTo(adaptation.getId());
    assertThat(event.skill()).isEqualTo(SWORDS);
    assertThat(event.fromLevel()).isZero();
    assertThat(event.level()).isEqualTo(2);
    assertThat(event.cost()).isEqualTo(cost);
    assertThat(event.forced()).isFalse();
    assertThat(profile.level(adaptation.getId())).isEqualTo(2);
    assertThat(profile.skill(SWORDS).adaptations()).containsEntry(adaptation.getId(), 2);
    assertThat(profile.power()).isEqualTo(1);
    assertThat(profile.skill(SWORDS).knowledge()).isEqualTo(3L);
  }

  @Test
  void aCancelledLearnChangesNothing() {
    Adaptation<?> adaptation = first(SWORDS);
    profile.skill(SWORDS).addKnowledge(500L);
    listen(AdaptationLearnEvent.class, (AdaptationLearnEvent event) -> event.setCancelled(true));

    LearnResult result = profile.learn(adaptation.getId(), 1, LearnOptions.paying().quietly());

    assertThat(result.status()).isEqualTo(LearnResult.Status.CANCELLED);
    assertThat(result.toLevel()).isZero();
    assertThat(profile.level(adaptation.getId())).isZero();
    assertThat(profile.skill(SWORDS).knowledge()).isEqualTo(500L);
  }

  @Test
  void learnRefusalsMapToTheirStatus() {
    Adaptation<?> adaptation = first(SWORDS);

    assertThat(profile.learn(adaptation.getId(), 1, LearnOptions.paying()).status())
        .isEqualTo(LearnResult.Status.INSUFFICIENT_KNOWLEDGE);
    assertThat(profile.learn(Id.of("gone", "thing"), 1, LearnOptions.paying()).status())
        .isEqualTo(LearnResult.Status.UNAVAILABLE);
    assertThat(profile.learn(adaptation.getId(), 1, LearnOptions.forced().quietly()).status())
        .isEqualTo(LearnResult.Status.LEARNED);
    assertThat(profile.learn(adaptation.getId(), 1, LearnOptions.forced()).status())
        .isEqualTo(LearnResult.Status.NO_CHANGE);
    runtime.getData().setMasterXp(1D);
    profile.skill(SWORDS).addKnowledge(500L);
    assertThat(profile.learn(second(SWORDS).getId(), 1, LearnOptions.paying()).status())
        .isEqualTo(LearnResult.Status.INSUFFICIENT_POWER);
  }

  @Test
  void everyTransactionResultHasItsOwnPublicStatus() {
    Set<LearnResult.Status> seen = EnumSet.noneOf(LearnResult.Status.class);
    for (AdaptationLearningTransaction.Result result : AdaptationLearningTransaction.Result.values()) {
      LearnResult.Status status = EnginePlayerProfile.status(result);
      assertThat(seen.add(status)).isTrue();
      assertThat(status.name()).isEqualTo(result == AdaptationLearningTransaction.Result.SKILL_LINE_UNAVAILABLE
          ? "UNAVAILABLE" : result.name());
    }
    assertThat(seen).containsExactlyInAnyOrder(LearnResult.Status.values());
    for (SkillMutations.Result result : SkillMutations.Result.values()) {
      assertThat(EnginePlayerProfile.status(result).name()).isEqualTo(result.name());
    }
  }

  @Test
  void unlearningRefundsAfterTheUnlearnEvent() {
    Adaptation<?> adaptation = first(SWORDS);
    long cost = adaptation.getCostFor(2, 0);
    profile.skill(SWORDS).addKnowledge(cost);
    profile.learn(adaptation.getId(), 2, LearnOptions.paying().quietly());
    listen(AdaptationUnlearnEvent.class, (AdaptationUnlearnEvent event) ->
        assertThat(profile.level(adaptation.getId())).isEqualTo(2));

    LearnResult result = profile.unlearn(adaptation.getId(), 0, LearnOptions.paying().quietly());

    long refund = AdaptationHost.refundCostFor(adaptation, 0, 2);
    assertThat(result).isEqualTo(new EngineLearnResult(LearnResult.Status.UNLEARNED, 2, 0, refund));
    AdaptationUnlearnEvent event = only(AdaptationUnlearnEvent.class);
    assertThat(event.fromLevel()).isEqualTo(2);
    assertThat(event.level()).isZero();
    assertThat(event.refund()).isEqualTo(refund);
    assertThat(profile.skill(SWORDS).knowledge()).isEqualTo(refund);
    assertThat(profile.unlearn(adaptation.getId(), 0, LearnOptions.paying()).status())
        .isEqualTo(LearnResult.Status.NO_CHANGE);
  }

  @Test
  void aCancelledUnlearnKeepsTheLevel() {
    Adaptation<?> adaptation = first(SWORDS);
    profile.learn(adaptation.getId(), 1, LearnOptions.forced().quietly());
    listen(AdaptationUnlearnEvent.class, (AdaptationUnlearnEvent event) -> event.setCancelled(true));

    assertThat(profile.unlearn(adaptation.getId(), 0, LearnOptions.forced().quietly()).status())
        .isEqualTo(LearnResult.Status.CANCELLED);
    assertThat(profile.level(adaptation.getId())).isEqualTo(1);
  }

  @Test
  void mutatingAndUnmutatingFireTheirEventsFirst() {
    profile.skill(SWORDS).setLevel(20);
    profile.skill(STEALTH).setLevel(20);
    listen(SkillMutateEvent.class, (SkillMutateEvent event) -> assertThat(profile.mutations()).isEmpty());

    MutationResult mutated = profile.mutate(SWORDS, STEALTH);

    SkillPair pair = new SkillPair(SWORDS, STEALTH);
    assertThat(mutated).isEqualTo(new EngineMutationResult(MutationResult.Status.MUTATED, Optional.of(pair)));
    assertThat(only(SkillMutateEvent.class).pair()).isEqualTo(pair);
    assertThat(profile.mutations()).containsExactly(pair);
    assertThat(profile.mutationOf(STEALTH)).contains(pair);
    assertThat(profile.mutate(SWORDS, Id.adapt("axes")).status()).isEqualTo(MutationResult.Status.ALREADY_MUTATED);
    assertThat(profile.skills()).extracting(SkillProgress::skill).containsExactly(SWORDS);

    listen(SkillUnmutateEvent.class, (SkillUnmutateEvent event) -> assertThat(profile.mutations()).hasSize(1));
    assertThat(profile.unmutate(STEALTH).status()).isEqualTo(MutationResult.Status.UNMUTATED);
    assertThat(only(SkillUnmutateEvent.class).pair()).isEqualTo(pair);
    assertThat(profile.mutations()).isEmpty();
    assertThat(profile.unmutate(STEALTH).status()).isEqualTo(MutationResult.Status.NOT_MUTATED);
    assertThat(profile.skill(SWORDS).level()).isEqualTo(20);
  }

  @Test
  void cancelledMutationsChangeNothing() {
    profile.skill(SWORDS).setLevel(20);
    profile.skill(STEALTH).setLevel(20);
    listen(SkillMutateEvent.class, (SkillMutateEvent event) -> event.setCancelled(true));

    assertThat(profile.mutate(SWORDS, STEALTH).status()).isEqualTo(MutationResult.Status.CANCELLED);
    assertThat(profile.mutations()).isEmpty();
    assertThat(profile.mutate(SWORDS, SWORDS).status()).isEqualTo(MutationResult.Status.SAME_SKILL);
    assertThat(profile.mutate(SWORDS, Id.of("gone", "thing")).status())
        .isEqualTo(MutationResult.Status.UNKNOWN_SKILL);
  }

  @Test
  void preferencesReadAndWriteTheMenuSave() {
    Adaptation<?> adaptation = first(SWORDS);
    profile.learn(adaptation.getId(), 1, LearnOptions.forced().quietly());

    assertThat(profile.preferences().get(adaptation.getId(), CommonPreferences.ENABLED))
        .isEqualTo(CommonPreferences.Toggle.ON);
    assertThat(profile.preferences().set(adaptation.getId(), CommonPreferences.ENABLED, CommonPreferences.Toggle.OFF))
        .isTrue();
    assertThat(profile.preferences().saved(adaptation.getId(), "enabled")).contains("OFF");
    assertThat(profile.preferences().get(adaptation.getId(), CommonPreferences.ENABLED))
        .isEqualTo(CommonPreferences.Toggle.OFF);
    assertThat(profile.preferences().set(adaptation.getId(), "enabled", "SIDEWAYS")).isFalse();
    assertThat(profile.preferences().set(adaptation.getId(), "nonsense", "ON")).isFalse();

    profile.preferences().clear(adaptation.getId(), "enabled");
    assertThat(profile.preferences().saved(adaptation.getId(), "enabled")).isEmpty();
    assertThat(profile.preferences().set(SWORDS, "enabled", "OFF")).isTrue();
    assertThat(profile.preferences().get(SWORDS, CommonPreferences.SKILL_ENABLED))
        .isEqualTo(CommonPreferences.Toggle.OFF);
  }

  @Test
  void statsBoostsAndAdvancementsGoToTheSave() {
    profile.addStat("test.counter", 2.5D);
    assertThat(profile.stat("test.counter")).isEqualTo(2.5D);
    int before = runtime.getData().getMultipliers().size();
    profile.boost(1.5D, 60_000L);
    assertThat(runtime.getData().getMultipliers()).hasSize(before + 1);
    assertThat(profile.isGranted(Id.adapt("swords"), "never")).isFalse();
  }

  @Test
  void sourcedBoostsEndTogetherAndLeaveOtherSourcesRunning() {
    int before = runtime.getData().getMultipliers().size();
    profile.boost("example:surveyor", 1.25D, 60_000L);
    profile.boost("example:surveyor", 1.1D, 60_000L);
    profile.boost("example:other", 1.5D, 60_000L);
    assertThat(runtime.getData().getMultipliers()).hasSize(before + 3);

    profile.endBoosts("example:surveyor");

    assertThat(runtime.getData().getMultipliers()).hasSize(before + 1)
        .anyMatch((XPMultiplier multiplier) -> "example:other".equals(multiplier.getSource()));
  }

  @Test
  void changesOffTheOwningThreadAreRefused() throws InterruptedException {
    AtomicReference<Throwable> refused = new AtomicReference<>();
    Thread other = new Thread(() -> {
      try {
        profile.skill(SWORDS).addKnowledge(1L);
      } catch (Throwable error) {
        refused.set(error);
      }
    });
    other.start();
    other.join();

    assertThat(refused.get()).isInstanceOf(IllegalStateException.class);
    assertThat(profile.skill(SWORDS).knowledge()).isZero();
  }

  @Test
  void resetWipesRegisteredProgressAndKeepsOrphans() {
    Adaptation<?> adaptation = first(SWORDS);
    profile.skill(SWORDS).setLevel(9);
    profile.skill(SWORDS).addKnowledge(40L);
    profile.learn(adaptation.getId(), 1, LearnOptions.forced().quietly());
    profile.addStat("test.counter", 1D);
    OrphansTest.orphanLine(runtime.getData(), "gone:shadow", 30D, 4L);

    profile.reset();

    assertThat(profile.skill(SWORDS).level()).isZero();
    assertThat(profile.skill(SWORDS).knowledge()).isZero();
    assertThat(profile.level(adaptation.getId())).isZero();
    assertThat(profile.stat("test.counter")).isZero();
    assertThat(profile.orphans()).extracting(orphan -> orphan.line()).containsExactly(new OrphanedLine.Line(Id.of("gone", "shadow")));
  }

  @Test
  void eachClearThroughThePublicProfileWipesOnlyItsPart() {
    PlayerProfile shown = AdaptApi.players().require(player);
    Adaptation<?> adaptation = first(SWORDS);
    shown.skill(SWORDS).setLevel(9);
    shown.skill(SWORDS).addKnowledge(40L);
    shown.learn(adaptation.getId(), 1, LearnOptions.forced().quietly());
    shown.addStat("test.counter", 1D);

    shown.clearStats();
    assertThat(shown.stat("test.counter")).isZero();
    assertThat(shown.skill(SWORDS).level()).isEqualTo(9);

    shown.clearAdaptations();
    assertThat(shown.level(adaptation.getId())).isZero();
    assertThat(shown.skill(SWORDS).knowledge()).isEqualTo(40L);

    shown.clearKnowledge();
    assertThat(shown.skill(SWORDS).knowledge()).isZero();
    assertThat(shown.skill(SWORDS).level()).isEqualTo(9);

    shown.clearXp();
    assertThat(shown.skill(SWORDS).xp()).isZero();

    runtime.getData().getSeenBiomes().isNewDiscovery("minecraft:plains");
    shown.clearDiscoveries();
    assertThat(runtime.getData().getSeenBiomes().getSeen()).isEmpty();
  }

  @Test
  void orphansAreRefundedAndPurgedThroughThePublicProfile() {
    PlayerProfile shown = AdaptApi.players().require(player);
    OrphansTest.orphanLine(runtime.getData(), "gone:shadow", 30D, 4L);

    assertThat(shown.refundOrphans("gone")).isZero();
    assertThat(shown.purgeOrphans("gone")).isEqualTo(1);
    assertThat(shown.orphans()).isEmpty();
  }

  @Test
  void aServerBoostAndLineAvailabilityAreOnThePublicTypes() {
    int before = adapt.core().getAdaptServer().getData().getMultipliers().size();
    AdaptApi.players().boost(2D, 60_000L);
    assertThat(adapt.core().getAdaptServer().getData().getMultipliers()).hasSize(before + 1);
    assertThat(AdaptApi.players().require(player).skill(SWORDS).isAvailable()).isTrue();
    assertThat(AdaptApi.players().require(player).skill(Id.of("gone", "shadow")).isAvailable()).isFalse();
  }

  @Test
  void skillResetUnlearnsWithoutRefund() {
    Adaptation<?> adaptation = first(SWORDS);
    profile.skill(SWORDS).setLevel(9);
    profile.skill(SWORDS).addKnowledge(500L);
    profile.learn(adaptation.getId(), 2, LearnOptions.paying().quietly());
    listen(AdaptationUnlearnEvent.class, (AdaptationUnlearnEvent event) -> {
    });

    profile.skill(SWORDS).reset();

    assertThat(profile.level(adaptation.getId())).isZero();
    assertThat(profile.skill(SWORDS).xp()).isZero();
    assertThat(profile.skill(SWORDS).knowledge()).isZero();
    assertThat(all(AdaptationUnlearnEvent.class)).singleElement()
        .satisfies((AdaptationUnlearnEvent event) -> assertThat(event.forced()).isTrue());
  }

  private <E extends AdaptEvent> void listen(Class<E> type, Consumer<E> handler) {
    adapt.bus().register(this, type, EventPriority.NORMAL, false, (E event) -> handler.accept(event));
    adapt.bus().register(this, type, EventPriority.MONITOR, false, events::add);
  }

  private <E extends AdaptEvent> E only(Class<E> type) {
    List<E> matching = all(type);
    assertThat(matching).hasSize(1);
    return matching.getFirst();
  }

  private <E extends AdaptEvent> List<E> all(Class<E> type) {
    List<E> matching = new ArrayList<>();
    for (AdaptEvent event : events) {
      if (type.isInstance(event)) {
        matching.add(type.cast(event));
      }
    }
    return matching;
  }

  private PlayerSkillLine line(String skill) {
    return runtime.getData().getSkillLine(skill);
  }

  private Adaptation<?> first(Id skill) {
    return skill(skill).getAdaptations().getFirst();
  }

  private Adaptation<?> second(Id skill) {
    return skill(skill).getAdaptations().get(1);
  }

  private Skill<?> skill(Id skill) {
    return adapt.core().getAdaptServer().getSkillRegistry().getSkill(skill);
  }
}
