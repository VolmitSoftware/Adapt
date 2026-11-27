package art.arcane.adapt.engine.mutation;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.xp.XpAward;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.CatalogViews;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.PlayerViews;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;
import art.arcane.adapt.command.AdaptCommands;
import art.arcane.adapt.command.CommandSource;
import art.arcane.adapt.content.skill.SkillAgility;
import art.arcane.adapt.content.skill.SkillAxes;
import art.arcane.adapt.content.skill.SkillHunter;
import art.arcane.adapt.content.skill.SkillRanged;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.content.skill.SkillUnarmed;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.mutation.kit.Kits;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.engine.xp.XpEarnings;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.config.TomlCodec;
import art.arcane.adapt.util.director.context.AdaptationListingHandler;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SkillMutationsTest {
  @TempDir
  Path data;

  private TestAdapt adapt;
  private FakePlayer player;
  private AdaptPlayer profile;
  private SkillRegistry registry;

  @BeforeEach
  void start() {
    TestPlatform platform = TestPlatform.builder(data).build();
    player = platform.player("Merger", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, SkillStealth.class, SkillAxes.class,
        SkillUnarmed.class, SkillRanged.class, SkillHunter.class, SkillAgility.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void theMutatedSkillIsNamedFromThePartnerAdjectiveAndLeadNounAndUsesTheLeadIcon() {
    MutatedSkill skill = registry.getMutatedSkill(PairKeys.parse("swords+stealth"));

    assertThat(skill.getLocalizedName()).isEqualTo("Silent Duelist");
    assertThat(skill.getIcon()).isEqualTo(registry.getSkill("adapt:swords").getIcon());
    assertThat(skill.getAdaptations()).containsExactlyElementsOf(Stream.of(SkillHost.of(registry.getSkill("adapt:swords")).getUpgrades(),
        SkillHost.of(registry.getSkill("adapt:stealth")).getUpgrades(), skill.getMutationAdaptations()).flatMap(List::stream).toList());
    assertThat(skill.getMutationAdaptations()).hasSize(5);
    assertThat(registry.getSkill("adapt:swords+adapt:stealth")).isSameAs(skill);
    assertThat(registry.getSkills()).doesNotContain(skill);
    assertThat(registry.getMutatedSkill(PairKeys.parse("swords+swords"))).isNull();
    assertThat(registry.getMutatedSkill(PairKeys.parse("swords+nope"))).isNull();
    assertThat(registry.getSkills()).allSatisfy((Skill<?> parent) -> assertThat(Kits.mergeable(parent)).isTrue());
  }

  @Test
  void mergingRefundsEveryAdaptationAndFreesItsPower() {
    PlayerData playerData = profile.getData();
    PlayerSkillLine swords = level("swords", 20, 10);
    PlayerSkillLine stealth = level("stealth", 18, 4);
    Adaptation<?> held = registry.getSkill("swords").getAdaptations().getFirst();
    swords.setAdaptation(held, 2);
    long refund = AdaptationHost.refundCostFor(held, 0, 2);
    assertThat(playerData.getUsedPower()).isEqualTo(1);

    assertThat(mutate("adapt:stealth", "adapt:swords")).isEqualTo(SkillMutations.Result.MUTATED);

    PlayerSkillLine merged = playerData.getSkillLine("swords");
    assertThat(merged.getLine()).isEqualTo("adapt:stealth+adapt:swords");
    assertThat(merged.getAdaptations()).isEmpty();
    assertThat(playerData.getUsedPower()).isZero();
    assertThat(stealth.getXp()).isLessThan(swords.getXp());
    assertThat(merged.getXp()).isEqualTo(stealth.getXp());
    assertThat(merged.getKnowledge()).isEqualTo(4L);
    assertThat(swords.getKnowledge()).isEqualTo(10L + refund);
  }

  @Test
  void theLowerParentSuppliesXpLevelAndKnowledgeAndBothParentLinesGo() {
    PlayerData playerData = profile.getData();
    level("swords", 16, 7);
    PlayerSkillLine stealth = level("stealth", 30, 90);

    mutate("swords", "stealth");

    PlayerSkillLine merged = playerData.getSkillLines().get("adapt:swords+adapt:stealth");
    assertThat(merged.getLevel()).isEqualTo(16);
    assertThat(merged.getKnowledge()).isEqualTo(7L);
    assertThat(playerData.getSkillLines()).doesNotContainKeys("adapt:swords", "adapt:stealth");
    assertThat(playerData.getSkillLine("adapt:stealth")).isSameAs(merged);
    assertThat(playerData.getSkillLineNullable("adapt:swords")).isSameAs(merged);
    assertThat(stealth.getLevel()).isEqualTo(30);
  }

  @Test
  void mergingPaysNoLevelUpRewardsAndLeavesMasterLevelAlone() {
    PlayerData playerData = profile.getData();
    level("swords", 20, 3);
    level("stealth", 25, 5);
    double masterXp = playerData.getMasterXp();
    int masterLevel = playerData.getLevel();

    mutate("swords", "stealth");
    playerData.update(profile);

    PlayerSkillLine merged = playerData.getSkillLine("swords");
    assertThat(merged.getKnowledge()).isEqualTo(3L);
    assertThat(playerData.getMasterXp()).isEqualTo(masterXp);
    assertThat(playerData.getLevel()).isEqualTo(masterLevel);
  }

  @Test
  void mergeRulesRefuseLowLevelsRepeatsSameSkillsAndUnknownSkills() {
    level("swords", 15, 0);
    level("stealth", 14, 0);
    level("axes", 20, 0);
    level("unarmed", 20, 0);

    assertThat(mutate("adapt:swords", "adapt:stealth")).isEqualTo(SkillMutations.Result.LEVEL_TOO_LOW);
    assertThat(mutate("adapt:swords", "adapt:swords")).isEqualTo(SkillMutations.Result.SAME_SKILL);
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("adapt:swords"), null, "Merger"))
        .isEqualTo(SkillMutations.Result.UNKNOWN_SKILL);
    assertThat(mutate("adapt:swords", "adapt:axes")).isEqualTo(SkillMutations.Result.MUTATED);
    assertThat(mutate("adapt:axes", "adapt:unarmed")).isEqualTo(SkillMutations.Result.ALREADY_MUTATED);
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("adapt:swords+adapt:axes"),
        registry.getSkill("adapt:unarmed"), "Merger")).isEqualTo(SkillMutations.Result.UNAVAILABLE_SKILL);
  }

  @Test
  void aPlayerMayHoldAnyNumberOfMutationsByDefault() {
    PlayerData playerData = profile.getData();
    for (String skill : List.of("swords", "stealth", "axes", "unarmed", "ranged", "hunter", "agility")) {
      level(skill, 20, 0);
    }
    playerData.setMasterXp(1D);

    assertThat(AdaptConfig.get().getMutations().getMaxMutations()).isZero();
    assertThat(mutate("adapt:swords", "adapt:stealth")).isEqualTo(SkillMutations.Result.MUTATED);
    assertThat(mutate("adapt:axes", "adapt:unarmed")).isEqualTo(SkillMutations.Result.MUTATED);
    assertThat(mutate("adapt:ranged", "adapt:hunter")).isEqualTo(SkillMutations.Result.MUTATED);
    assertThat(playerData.getMutations()).hasSize(3);
    assertThat(mutate("adapt:agility", "adapt:swords")).isEqualTo(SkillMutations.Result.ALREADY_MUTATED);
  }

  @Test
  void aServerCapStopsMutationsAtTheConfiguredCount() throws Exception {
    PlayerData playerData = profile.getData();
    for (String skill : List.of("swords", "stealth", "axes", "unarmed", "ranged", "hunter")) {
      level(skill, 20, 0);
    }
    AdaptConfig.Mutations capped = TomlCodec.fromToml("maxMutations = 2", AdaptConfig.Mutations.class);
    mutate("swords", "stealth");

    assertThat(capped.allowsAnother(1)).isTrue();
    assertThat(SkillMutations.check(playerData, registry.getSkill("adapt:axes"), registry.getSkill("adapt:unarmed"), capped))
        .isEqualTo(SkillMutations.Result.MUTATED);
    mutate("axes", "unarmed");
    assertThat(SkillMutations.check(playerData, registry.getSkill("adapt:ranged"), registry.getSkill("adapt:hunter"), capped))
        .isEqualTo(SkillMutations.Result.LIMIT_REACHED);
    assertThat(SkillMutations.check(playerData, registry.getSkill("adapt:ranged"), registry.getSkill("adapt:hunter"),
        AdaptConfig.get().getMutations())).isEqualTo(SkillMutations.Result.MUTATED);
  }

  @Test
  void savesRoundTripMutationsAndOldSavesLoadWithoutThem() {
    PlayerData playerData = profile.getData();
    level("swords", 20, 2);
    level("stealth", 20, 2);
    String before = playerData.toJson(true);
    assertThat(before).doesNotContain("mutations");
    PlayerData old = PlayerData.fromJson(before);
    assertThat(old.getMutations()).isEmpty();
    assertThat(old.getSkillLines()).containsKeys("adapt:swords", "adapt:stealth");

    mutate("swords", "stealth");
    PlayerData loaded = PlayerData.fromJson(playerData.toJson(true));

    assertThat(loaded.getMutations()).containsExactly(new SkillPair(Id.adapt("swords"), Id.adapt("stealth")));
    assertThat(loaded.getSkillLineNullable("adapt:stealth").getLine()).isEqualTo("adapt:swords+adapt:stealth");
    assertThat(loaded.getSkillLines().get("adapt:swords+adapt:stealth").getXp())
        .isEqualTo(playerData.getSkillLines().get("adapt:swords+adapt:stealth").getXp());
  }

  @Test
  void awardsToEitherParentLandInTheMutatedLine() {
    level("swords", 20, 0);
    level("stealth", 20, 0);
    mutate("swords", "stealth");
    double before = profile.getData().getSkillLine("swords").getXp();

    XP.award(profile, registry.getSkill("stealth"), XpAward.gameplay(40D, "stealth:test"));
    profile.getData().getSkillLine("swords").flushXpPool(null);

    assertThat(profile.getData().getSkillLine("adapt:swords").getXp()).isGreaterThan(before);
    assertThat(profile.getData().getXpEarnings().snapshot())
        .allMatch(snapshot -> snapshot.source().startsWith("adapt:swords+adapt:stealth/"));
  }

  @Test
  void oneActionPayingBothParentsPaysTheLargerPlusAQuarterOfTheSmaller() {
    PlayerSkillLine line = new PlayerSkillLine();

    double first = line.sameActionPayment(true, 40D, 7L, 0.25D);
    double second = line.sameActionPayment(false, 20D, 7L, 0.25D);
    assertThat(first + second).isEqualTo(45D);

    double partnerFirst = line.sameActionPayment(false, 10D, 8L, 0.25D);
    double leadSecond = line.sameActionPayment(true, 30D, 8L, 0.25D);
    assertThat(partnerFirst).isEqualTo(10D);
    assertThat(leadSecond).isEqualTo(22.5D);

    assertThat(line.sameActionPayment(true, 12D, 9L, 0.25D)).isEqualTo(12D);
    assertThat(line.sameActionPayment(true, 5D, 9L, 0.25D)).isEqualTo(5D);
  }

  @Test
  void mutatedLinesGetOneAndAHalfTimesTheRateAllowance() {
    AdaptConfig.XpEarningLimits limits = AdaptConfig.get().getXpEarningLimits();
    XpEarnings single = new XpEarnings();
    XpEarnings mutated = new XpEarnings();
    long now = 1_000_000L;

    double singleBurst = single.claim("swords", Double.MAX_VALUE, 1D, now);
    double mutatedBurst = mutated.claim("adapt:swords+adapt:stealth", Double.MAX_VALUE, 1D, now);

    assertThat(singleBurst).isEqualTo(limits.getSkillBurstXp());
    assertThat(mutatedBurst).isEqualTo(Math.min(limits.getSkillBurstXp() * 1.5D, limits.getPlayerBurstXp()));
  }

  @Test
  void unmutateConservesKnowledgeFreesPowerAndBringsBothParentsBack() {
    PlayerData playerData = profile.getData();
    level("swords", 22, 6);
    PlayerSkillLine stealth = level("stealth", 25, 9);
    double stealthXp = stealth.getXp();
    mutate("swords", "stealth");
    PlayerSkillLine merged = playerData.getSkillLine("swords");
    double mergedXp = merged.getXp();
    merged.setKnowledge(41L);
    Adaptation<?> upgrade = registry.getSkill("adapt:swords+adapt:stealth").getAdaptations().getFirst();
    merged.setAdaptation(upgrade, 2);
    long refund = AdaptationHost.refundCostFor(upgrade, 0, 2);
    assertThat(playerData.getUsedPower()).isEqualTo(1);

    assertThat(SkillMutations.unmutate(playerData, new SkillPair(Id.adapt("swords"), Id.adapt("stealth")), "Merger")).isEqualTo(SkillMutations.Unmutation.UNMUTATED);

    assertThat(playerData.getMutations()).isEmpty();
    assertThat(playerData.getSkillLines()).doesNotContainKey("adapt:swords+adapt:stealth").containsKeys("adapt:swords", "adapt:stealth");
    assertThat(playerData.getUsedPower()).isZero();
    PlayerSkillLine swords = playerData.getSkillLine("swords");
    PlayerSkillLine back = playerData.getSkillLine("stealth");
    assertThat(swords.getAdaptations()).isEmpty();
    assertThat(swords.getKnowledge() + back.getKnowledge()).isEqualTo(41L + refund);
    assertThat(swords.getKnowledge() - back.getKnowledge()).isBetween(0L, 1L);
    assertThat(swords.getXp()).isEqualTo(mergedXp);
    assertThat(back.getXp()).isEqualTo(stealthXp);
    assertThat(back.getLevel()).isEqualTo(25);
  }

  @Test
  void unmutatePaysNoLevelUpRewards() {
    PlayerData playerData = profile.getData();
    level("swords", 20, 3);
    level("stealth", 30, 5);
    mutate("swords", "stealth");
    double masterXp = playerData.getMasterXp();

    SkillMutations.unmutate(playerData, new SkillPair(Id.adapt("swords"), Id.adapt("stealth")), "Merger");
    long knowledge = playerData.getSkillLine("swords").getKnowledge() + playerData.getSkillLine("stealth").getKnowledge();
    playerData.update(profile);

    assertThat(playerData.getMasterXp()).isEqualTo(masterXp);
    assertThat(playerData.getSkillLine("adapt:swords").getKnowledge() + playerData.getSkillLine("adapt:stealth").getKnowledge())
        .isEqualTo(knowledge).isEqualTo(3L);
  }

  @Test
  void unmutatingOneOfSeveralLeavesTheOthersAlone() {
    PlayerData playerData = profile.getData();
    for (String skill : List.of("swords", "stealth", "axes", "unarmed")) {
      level(skill, 20, 0);
    }
    mutate("swords", "stealth");
    mutate("axes", "unarmed");
    PlayerSkillLine other = playerData.getSkillLine("axes");

    SkillPair named = SkillMutations.mutationNamed(playerData, "Stealth");
    assertThat(named).isEqualTo(new SkillPair(Id.adapt("swords"), Id.adapt("stealth")));
    assertThat(SkillMutations.mutationNamed(playerData, "adapt:axes+adapt:unarmed")).isEqualTo(new SkillPair(Id.adapt("axes"), Id.adapt("unarmed")));
    assertThat(SkillMutations.mutationNamed(playerData, "adapt:ranged")).isNull();
    SkillMutations.unmutate(playerData, named, "Merger");

    assertThat(playerData.getMutations()).containsExactly(new SkillPair(Id.adapt("axes"), Id.adapt("unarmed")));
    assertThat(playerData.getSkillLine("adapt:axes")).isSameAs(other);
    assertThat(playerData.getSkillLine("adapt:unarmed")).isSameAs(other);
    assertThat(playerData.getSkillLine("adapt:swords").getLine()).isEqualTo("adapt:swords");
    assertThat(playerData.mutationOrigin(new SkillPair(Id.adapt("swords"), Id.adapt("stealth")))).isNull();
    assertThat(playerData.mutationOrigin(new SkillPair(Id.adapt("axes"), Id.adapt("unarmed")))).isNotNull();
    assertThat(SkillMutations.unmutate(playerData, named, "Merger")).isEqualTo(SkillMutations.Unmutation.NOT_MUTATED);
  }

  @Test
  void aMutationSavedWithoutItsOriginBringsBothParentsBackAtTheMutatedLevel() {
    level("swords", 20, 0);
    level("stealth", 30, 0);
    mutate("swords", "stealth");
    JsonObject saved = JsonParser.parseString(profile.getData().toJson(true)).getAsJsonObject();
    assertThat(saved.has("mutationOrigins")).isTrue();
    saved.remove("mutationOrigins");
    PlayerData old = PlayerData.fromJson(saved.toString());
    double mergedXp = old.getSkillLineNullable("swords").getXp();
    assertThat(old.mutationOrigin(new SkillPair(Id.adapt("swords"), Id.adapt("stealth")))).isNull();

    SkillMutations.unmutate(old, new SkillPair(Id.adapt("swords"), Id.adapt("stealth")), "Merger");

    assertThat(old.getSkillLineNullable("adapt:swords").getXp()).isEqualTo(mergedXp);
    assertThat(old.getSkillLineNullable("adapt:stealth").getXp()).isEqualTo(mergedXp);
    assertThat(old.getSkillLineNullable("adapt:stealth").getLevel()).isEqualTo(20);
  }

  @Test
  void resetUnmutatesEveryMutationTheWayUnmutateDoes() {
    PlayerData reset = profile.getData();
    PlayerData single = PlayerData.fromJson(reset.toJson(true));
    for (PlayerData playerData : List.of(reset, single)) {
      for (String skill : List.of("swords", "stealth", "axes", "unarmed")) {
        PlayerSkillLine line = playerData.getSkillLine(skill);
        line.setXp(XP.getXpForLevel(20 + skill.length()) + 1D);
        line.setKnowledge(skill.length());
        line.settleWithoutRewards();
      }
      SkillMutations.mutate(playerData, registry.getSkill("swords"), registry.getSkill("stealth"), "Merger");
      SkillMutations.mutate(playerData, registry.getSkill("axes"), registry.getSkill("unarmed"), "Merger");
    }

    assertThat(SkillMutations.unmutateAll(reset, "Merger"))
        .containsExactly(new SkillPair(Id.adapt("swords"), Id.adapt("stealth")), new SkillPair(Id.adapt("axes"), Id.adapt("unarmed")));
    SkillMutations.unmutate(single, new SkillPair(Id.adapt("swords"), Id.adapt("stealth")), "Merger");
    SkillMutations.unmutate(single, new SkillPair(Id.adapt("axes"), Id.adapt("unarmed")), "Merger");

    assertThat(reset.getMutations()).isEmpty();
    for (String skill : List.of("swords", "stealth", "axes", "unarmed")) {
      assertThat(reset.getSkillLineNullable(skill).getXp()).isEqualTo(single.getSkillLineNullable(skill).getXp());
      assertThat(reset.getSkillLineNullable(skill).getKnowledge()).isEqualTo(single.getSkillLineNullable(skill).getKnowledge());
    }
    assertThat(reset.toJson(true)).doesNotContain("mutationOrigins").doesNotContain("\"mutations\"");
  }

  @Test
  void aPairCanMutateAgainAfterAnUnmutate() {
    level("swords", 20, 0);
    level("stealth", 20, 0);
    mutate("swords", "stealth");
    SkillMutations.unmutate(profile.getData(), new SkillPair(Id.adapt("swords"), Id.adapt("stealth")), "Merger");

    assertThat(mutate("adapt:stealth", "adapt:swords")).isEqualTo(SkillMutations.Result.MUTATED);
    assertThat(profile.getData().getSkillLine("adapt:swords").getLine()).isEqualTo("adapt:stealth+adapt:swords");
  }

  @Test
  void unmutateNeedsThePermissionAndCompletesTheTargetsMutations() {
    level("swords", 20, 0);
    level("stealth", 20, 0);
    mutate("swords", "stealth");
    AdaptationListingHandler.initializeAdaptationListings();
    List<String> refused = new ArrayList<>();
    List<String> told = new ArrayList<>();

    assertThat(new AdaptCommands().complete(source(false, refused), "adapt", new String[]{"unmutate", "Merger", ""})).isEmpty();
    new AdaptCommands().execute(source(false, refused), "adapt", new String[]{"unmutate", "Merger", "stealth"});
    adapt.awaitTicks(() -> !refused.isEmpty(), 10_000L);
    assertThat(profile.getData().getMutations()).hasSize(1);

    assertThat(new AdaptCommands().complete(source(true, told), "adapt", new String[]{"unmutate", "Merger", ""}))
        .containsExactly("adapt:swords+adapt:stealth", "adapt:swords", "adapt:stealth");
    assertThat(new AdaptCommands().complete(source(true, told), "adapt", new String[]{"unmutate", "player=Merger", "skill=adapt:st"}))
        .containsExactly("skill=adapt:stealth");
    new AdaptCommands().execute(source(true, told), "adapt", new String[]{"unmutate", "Merger", "stealth"});
    adapt.awaitTicks(() -> profile.getData().getMutations().isEmpty(), 10_000L);
    assertThat(profile.getData().getSkillLine("adapt:stealth").getLine()).isEqualTo("adapt:stealth");
  }

  @Test
  void theClientViewsShowTheMutatedSkillInPlaceOfItsParents() {
    level("swords", 20, 0);
    level("stealth", 18, 6);
    mutate("swords", "stealth");
    String id = "adapt:swords+adapt:stealth";

    CatalogView catalog = CatalogViews.build(registry, player.uuid(), profile.getData().getMutations(), false);
    SkillView swords = catalog.skills().stream().filter(skill -> skill.id().equals("adapt:swords")).findFirst().orElseThrow();
    SkillView mutated = catalog.skills().stream().filter(skill -> skill.id().equals(id)).findFirst().orElseThrow();
    assertThat(mutated.name()).isEqualTo("Silent Duelist");
    assertThat(mutated.description()).contains("Swords leads").contains("Stealth is bound to it");
    SkillView apart = CatalogViews.build(registry, player.uuid(), profile.getData().getMutations(), true).skills().stream()
        .filter(skill -> skill.id().equals(id)).findFirst().orElseThrow();
    assertThat(mutated.description()).startsWith(apart.description());
    assertThat(mutated.icon()).isEqualTo(new IconRef(swords.icon().itemId(), swords.icon().customModelData(),
        swords.icon().itemModelId(), "adapt:textures/gui/emblem/swords.png|adapt:textures/gui/trim/stealth.png"));
    assertThat(mutated.color()).isEqualTo(swords.color());
    assertThat(mutated.adaptations()).extracting(AdaptationView::id).startsWith(
        "adapt:swords/upgrade/witherblade", "adapt:swords/upgrade/tempest_flow", "adapt:swords/upgrade/duelists_verdict",
        "adapt:stealth/upgrade/umbral_form", "adapt:stealth/upgrade/death_mark", "adapt:stealth/upgrade/phantom_decoy");
    assertThat(CatalogViews.build(registry, player.uuid(), List.of(), false).skills())
        .noneMatch(skill -> skill.id().equals(id));

    PlayerView view = PlayerViews.build(registry, profile);
    assertThat(view.skills()).extracting(SkillLineView::skillId).contains(id).doesNotContain("adapt:swords", "adapt:stealth");
    SkillLineView line = view.skills().stream().filter(skill -> skill.skillId().equals(id)).findFirst().orElseThrow();
    assertThat(line.level()).isEqualTo(18);
    assertThat(line.knowledge()).isEqualTo(6L);
    assertThat(line.xpToNext()).isPositive();
    assertThat(view.access().skills()).contains(id).doesNotContain("adapt:swords", "adapt:stealth");
    assertThat(view.access().rows(id)).containsExactly(
        List.of("adapt:swords/upgrade/witherblade", "adapt:swords/upgrade/tempest_flow", "adapt:swords/upgrade/duelists_verdict"),
        List.of("adapt:stealth/upgrade/umbral_form", "adapt:stealth/upgrade/death_mark", "adapt:stealth/upgrade/phantom_decoy"),
        List.of("adapt:mutated/swords/adapt/stealth/graft", "adapt:mutated/swords/adapt/stealth/echo", "adapt:mutated/swords/adapt/stealth/barter",
            "adapt:mutated/swords/adapt/stealth/attunement", "adapt:mutated/swords/adapt/stealth/convergence"));
  }

  @Test
  void mutateTabCompletesSkillIds() {
    AdaptationListingHandler.initializeAdaptationListings();
    CommandSource source = source(true, new ArrayList<>());

    List<String> root = new AdaptCommands().complete(source, "adapt", new String[]{"mut"});
    List<String> lead = new AdaptCommands().complete(source, "adapt", new String[]{"mutate", ""});
    List<String> partner = new AdaptCommands().complete(source, "adapt", new String[]{"mutate", "swords", "st"});

    assertThat(root).contains("mutate");
    assertThat(lead).contains("lead=adapt:swords", "lead=adapt:stealth", "partner=adapt:axes");
    assertThat(partner).isNotEmpty().allMatch(option -> option.contains("st"));
  }

  /** A console-like source that may or may not hold every permission and keeps what it is told. */
  private CommandSource source(boolean permitted, List<String> told) {
    return new CommandSource() {
      @Override
      public String name() {
        return "Merger";
      }

      @Override
      public Optional<PlayerRef> player() {
        return Optional.of(player);
      }

      @Override
      public boolean hasPermission(String node) {
        return permitted || node.equals(AdaptCommands.ROOT_PERMISSION);
      }

      @Override
      public boolean isOp() {
        return false;
      }

      @Override
      public void sendSection(String sectionText) {
        told.add(sectionText);
      }

      @Override
      public void sendLiteral(String plainText) {
        told.add(plainText);
      }

      @Override
      public void sendMarkup(String markup) {
        told.add(markup);
      }
    };
  }

  private SkillMutations.Result mutate(String lead, String partner) {
    return SkillMutations.mutate(profile.getData(), registry.getSkill(lead), registry.getSkill(partner), "Merger");
  }

  private PlayerSkillLine level(String skill, int level, long knowledge) {
    PlayerSkillLine line = profile.getData().getSkillLine(skill);
    line.setXp(XP.getXpForLevel(level) + 1D);
    line.setKnowledge(knowledge);
    line.settleWithoutRewards();
    return line;
  }
}
