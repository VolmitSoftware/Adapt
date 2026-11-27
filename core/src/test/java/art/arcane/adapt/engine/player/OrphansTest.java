package art.arcane.adapt.engine.player;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.event.SkillUnmutateEvent;
import art.arcane.adapt.api.player.LearnOptions;
import art.arcane.adapt.api.player.LearnResult;
import art.arcane.adapt.api.player.MutationResult;
import art.arcane.adapt.api.player.OrphanedLine;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.engine.mutation.MutationOrigin;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerAdaptation;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.api.xp.XpAward;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OrphansTest {
  private static final Id SWORDS = Id.adapt("swords");
  private static final Id GONE_SKILL = Id.of("gone", "shadow");
  private static final Id GONE_ADAPTATION = Id.of("gone", "swords/edge");
  private static final Id AFTERSHOCK = Id.of("example", "swords/aftershock");
  private static final Id GEOLOGY = Id.of("example", "geology");

  @TempDir
  Path data;

  private TestAdapt adapt;
  private AdaptPlayer runtime;
  private EnginePlayerProfile profile;

  @BeforeEach
  void start() {
    TestPlatform platform = TestPlatform.builder(data).build();
    FakePlayer player = platform.player("Keeper", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, SkillStealth.class));
    runtime = adapt.join(player);
    profile = PlayerApi.directory().require(player);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void missingSkillsAndAdaptationsAreListedAndCountTowardNothing() {
    orphanLine(runtime.getData(), GONE_SKILL.toString(), 120D, 6L)
        .getAdaptations().put("gone:shadow/step", stored("gone:shadow/step", 2, 4L));
    profile.skill(SWORDS).setLevel(4);
    PlayerSkillLine swords = runtime.getData().getSkillLine("swords");
    swords.getAdaptations().put(GONE_ADAPTATION.toString(), stored(GONE_ADAPTATION.toString(), 3, 13L));

    List<OrphanedLine> orphans = profile.orphans();

    assertThat(orphans).containsExactly(
        new EngineOrphanedLine(SWORDS.toString(), new OrphanedLine.Line(SWORDS), Optional.of(GONE_ADAPTATION), "gone", 0D, 13L,
            Map.of(GONE_ADAPTATION, 3)),
        new EngineOrphanedLine(GONE_SKILL.toString(), new OrphanedLine.Line(GONE_SKILL), Optional.empty(), "gone", 120D, 6L,
            Map.of(Id.of("gone", "shadow/step"), 2)));
    assertThat(profile.skills()).extracting(progress -> progress.skill()).containsExactly(SWORDS);
    assertThat(profile.skill(SWORDS).adaptations()).isEmpty();
    assertThat(profile.power()).isZero();
  }

  @Test
  void linesAreTypedAsSkillOrPairAndUnreadableKeysAreNotListed() {
    orphanLine(runtime.getData(), GONE_SKILL.toString(), 120D, 6L);
    orphanLine(runtime.getData(), "Not A Key!", 50D, 1L);
    orphanLine(runtime.getData(), "gone:shadow+lost:echo", 10D, 2L);

    assertThat(profile.orphans()).extracting(OrphanedLine::line).containsExactly(
        new OrphanedLine.Line(GONE_SKILL),
        new OrphanedLine.Pair(new SkillPair(GONE_SKILL, Id.of("lost", "echo"))));
  }

  @Test
  void refundReturnsTheRecordedKnowledgeToTheHoldingLineAndLeavesWholeLines() {
    orphanLine(runtime.getData(), GONE_SKILL.toString(), 120D, 6L);
    profile.skill(SWORDS).setLevel(4);
    runtime.getData().getSkillLine("swords").getAdaptations()
        .put(GONE_ADAPTATION.toString(), stored(GONE_ADAPTATION.toString(), 3, 13L));

    assertThat(profile.refundOrphans("elsewhere")).isZero();
    long refunded = profile.refundOrphans("gone");

    assertThat(refunded).isEqualTo(13L);
    assertThat(profile.skill(SWORDS).knowledge()).isEqualTo(refunded);
    assertThat(profile.orphans()).extracting(OrphanedLine::line).containsExactly(new OrphanedLine.Line(GONE_SKILL));
  }

  @Test
  void purgeDeletesEveryOrphanOfTheNamespace() {
    orphanLine(runtime.getData(), GONE_SKILL.toString(), 120D, 6L);
    orphanLine(runtime.getData(), "kept:other", 5D, 0L);
    profile.skill(SWORDS).setLevel(4);
    runtime.getData().getSkillLine("swords").getAdaptations()
        .put(GONE_ADAPTATION.toString(), stored(GONE_ADAPTATION.toString(), 3, 13L));

    assertThat(profile.purgeOrphans("gone")).isEqualTo(2);

    assertThat(profile.orphans()).extracting(OrphanedLine::namespace).containsExactly("kept");
    assertThat(profile.skill(SWORDS).knowledge()).isZero();
    assertThat(profile.skill(SWORDS).level()).isEqualTo(4);
  }

  @Test
  void learningRecordsWhatTheLevelsCostAtTheAdaptationsOwnPrices() {
    Adaptation<?> builtIn = firstSwordsAdaptation();
    long cost = builtIn.getCostFor(2, 0);

    assertThat(profile.learn(builtIn.getId(), 2, LearnOptions.forced().quietly()).status())
        .isEqualTo(LearnResult.Status.LEARNED);

    assertThat(runtime.getData().getSkillLine("swords").getAdaptation(builtIn.getId().toString()).getKnowledge())
        .isEqualTo(cost)
        .isPositive();
  }

  @Test
  void anAddonAdaptationOnABuiltInLineIsListedWithOnlyItsOwnRefundableKnowledge() {
    Adaptation<?> builtIn = builtInSwordsProgress();
    long lineKnowledge = profile.skill(SWORDS).knowledge();

    assertThat(profile.orphans()).containsExactly(
        new EngineOrphanedLine(SWORDS.toString(), new OrphanedLine.Line(SWORDS), Optional.of(AFTERSHOCK), "example", 0D, 11L, Map.of(AFTERSHOCK, 2)),
        new EngineOrphanedLine(GEOLOGY.toString(), new OrphanedLine.Line(GEOLOGY), Optional.empty(), "example", 300D, 9L, Map.of()));
    assertThat(lineKnowledge).isEqualTo(5L);
    assertThat(profile.level(builtIn.getId())).isEqualTo(1);
  }

  @Test
  void refundCreditsTheAddonAdaptationsKnowledgeAndLeavesTheBuiltInLineAlone() {
    Adaptation<?> builtIn = builtInSwordsProgress();
    PlayerSkillLine swords = runtime.getData().getSkillLine("swords");
    double xp = swords.getXp();
    long knowledge = swords.getKnowledge();

    assertThat(profile.refundOrphans("example")).isEqualTo(11L);

    assertThat(profile.skill(SWORDS).knowledge()).isEqualTo(knowledge + 11L);
    assertThat(profile.skill(SWORDS).level()).isEqualTo(9);
    assertThat(swords.getXp()).isEqualTo(xp);
    assertThat(profile.level(builtIn.getId())).isEqualTo(1);
    assertThat(swords.getAdaptations()).containsOnlyKeys(builtIn.getId().toString());
    assertThat(profile.orphans()).extracting(OrphanedLine::line).containsExactly(new OrphanedLine.Line(GEOLOGY));
  }

  @Test
  void purgeDeletesTheAddonAdaptationAndTheWholeAddonLineButNoBuiltInProgress() {
    Adaptation<?> builtIn = builtInSwordsProgress();
    PlayerSkillLine swords = runtime.getData().getSkillLine("swords");
    double xp = swords.getXp();
    long knowledge = swords.getKnowledge();

    assertThat(profile.purgeOrphans("example")).isEqualTo(2);

    assertThat(profile.skill(SWORDS).knowledge()).isEqualTo(knowledge);
    assertThat(profile.skill(SWORDS).level()).isEqualTo(9);
    assertThat(swords.getXp()).isEqualTo(xp);
    assertThat(profile.level(builtIn.getId())).isEqualTo(1);
    assertThat(swords.getAdaptations()).containsOnlyKeys(builtIn.getId().toString());
    assertThat(runtime.getData().getSkillLineNullable(GEOLOGY.toString())).isNull();
    assertThat(profile.orphans()).isEmpty();
  }

  @Test
  void aRegionGrantedAddonAdaptationRefundsNothingAndIsDeleted() {
    profile.skill(SWORDS).setLevel(4);
    PlayerAdaptation granted = stored(AFTERSHOCK.toString(), 1, 6L);
    granted.setRegionGranted(true);
    runtime.getData().getSkillLine("swords").getAdaptations().put(AFTERSHOCK.toString(), granted);

    assertThat(profile.orphans()).extracting(OrphanedLine::knowledge).containsExactly(0L);
    assertThat(profile.refundOrphans("example")).isZero();

    assertThat(profile.skill(SWORDS).knowledge()).isZero();
    assertThat(profile.orphans()).isEmpty();
  }

  @Test
  void aMutationWithAMissingPartnerIsSuspendedListedAndRestoredByUnmutate() {
    SkillPair suspended = suspend(XP.getXpForLevel(12));

    assertThat(runtime.getData().getSkillLine("swords")).isNull();
    assertThat(profile.skill(SWORDS).isAvailable()).isFalse();
    assertThat(profile.skill(SWORDS).level()).isEqualTo(12);
    assertThat(profile.orphans()).containsExactly(new EngineOrphanedLine(suspended.key(), new OrphanedLine.Pair(suspended), Optional.empty(), "gone",
        XP.getXpForLevel(12), 7L, Map.of()));
    assertThat(profile.skills()).isEmpty();

    assertThat(profile.unmutate(SWORDS).status()).isEqualTo(MutationResult.Status.UNMUTATED);

    assertThat(profile.mutations()).isEmpty();
    assertThat(profile.skill(SWORDS).isAvailable()).isTrue();
    assertThat(profile.skill(SWORDS).level()).isEqualTo(12);
    assertThat(profile.orphans()).extracting(OrphanedLine::line).containsExactly(new OrphanedLine.Line(GONE_SKILL));
    profile.skill(SWORDS).award(XpAward.of(5D));
    assertThat(profile.skill(SWORDS).xp()).isGreaterThan(XP.getXpForLevel(12));
  }

  @Test
  void purgingASuspendedMutationRestoresTheSurvivingParentAndDropsTheMissingOne() {
    suspend(XP.getXpForLevel(12));
    int[] splits = {0};
    adapt.bus().register(this, SkillUnmutateEvent.class, EventPriority.NORMAL, false,
        (SkillUnmutateEvent event) -> splits[0]++);

    assertThat(profile.purgeOrphans("gone")).isEqualTo(1);

    assertThat(splits[0]).isEqualTo(1);
    assertThat(profile.mutations()).isEmpty();
    assertThat(profile.orphans()).isEmpty();
    assertThat(profile.skill(SWORDS).level()).isEqualTo(12);
  }

  private SkillPair suspend(double xp) {
    SkillPair mutation = new SkillPair(SWORDS, GONE_SKILL);
    PlayerSkillLine merged = new PlayerSkillLine();
    merged.setLine(mutation.key());
    merged.setXp(xp);
    merged.setKnowledge(7L);
    merged.settleWithoutRewards();
    runtime.getData().installMutation(mutation, merged, new MutationOrigin(0D, 0D));
    return mutation;
  }

  static PlayerSkillLine orphanLine(PlayerData data, String key, double xp, long knowledge) {
    PlayerSkillLine line = new PlayerSkillLine();
    line.setLine(key);
    line.setXp(xp);
    line.setKnowledge(knowledge);
    line.settleWithoutRewards();
    data.getSkillLines().put(key, line);
    return line;
  }

  /**
   * Swords at level 9 with spare knowledge and one built-in adaptation learned, plus what an uninstalled
   * {@code example} addon left behind: its adaptation on the Swords line and its own Geology line.
   */
  private Adaptation<?> builtInSwordsProgress() {
    Adaptation<?> builtIn = firstSwordsAdaptation();
    profile.skill(SWORDS).setLevel(9);
    profile.skill(SWORDS).addKnowledge(5L);
    assertThat(profile.learn(builtIn.getId(), 1, LearnOptions.forced().quietly()).status())
        .isEqualTo(LearnResult.Status.LEARNED);
    runtime.getData().getSkillLine("swords").getAdaptations()
        .put(AFTERSHOCK.toString(), stored(AFTERSHOCK.toString(), 2, 11L));
    orphanLine(runtime.getData(), GEOLOGY.toString(), 300D, 9L);
    return builtIn;
  }

  private Adaptation<?> firstSwordsAdaptation() {
    return adapt.core().getAdaptServer().getSkillRegistry().getSkill(SWORDS).getAdaptations().getFirst();
  }

  private static PlayerAdaptation stored(String id, int level, long knowledge) {
    PlayerAdaptation adaptation = new PlayerAdaptation();
    adaptation.setId(id);
    adaptation.setLevel(level);
    adaptation.setKnowledge(knowledge);
    return adaptation;
  }
}
