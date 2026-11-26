package art.arcane.adapt.api.mutation;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.api.xp.XpAward;
import art.arcane.adapt.api.xp.XpEarnings;
import art.arcane.adapt.command.AdaptCommands;
import art.arcane.adapt.command.CommandSource;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.director.context.AdaptationListingHandler;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.CatalogViews;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.PlayerViews;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;
import art.arcane.adapt.content.skill.SkillAgility;
import art.arcane.adapt.content.skill.SkillAxes;
import art.arcane.adapt.content.skill.SkillHunter;
import art.arcane.adapt.content.skill.SkillRanged;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.content.skill.SkillUnarmed;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.config.TomlCodec;
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
    MutatedSkill skill = registry.getMutatedSkill("mutated-swords-stealth");

    assertThat(skill.getLocalizedName()).isEqualTo("Silent Duelist");
    assertThat(skill.getIcon()).isEqualTo(registry.getSkill("swords").getIcon());
    assertThat(skill.getAdaptations()).containsExactlyElementsOf(Stream.of(registry.getSkill("swords").getUpgrades(),
        registry.getSkill("stealth").getUpgrades(), skill.getMutationAdaptations()).flatMap(List::stream).toList());
    assertThat(skill.getMutationAdaptations()).hasSize(5);
    assertThat(registry.getSkill("mutated-swords-stealth")).isSameAs(skill);
    assertThat(registry.getSkills()).doesNotContain(skill);
    assertThat(registry.getMutatedSkill("mutated-swords-swords")).isNull();
    assertThat(registry.getMutatedSkill("mutated-swords-nope")).isNull();
    assertThat(SkillWords.all()).hasSize(24);
  }

  @Test
  void mergingRefundsEveryAdaptationAndFreesItsPower() {
    PlayerData playerData = profile.getData();
    PlayerSkillLine swords = level("swords", 20, 10);
    PlayerSkillLine stealth = level("stealth", 18, 4);
    Adaptation<?> held = registry.getSkill("swords").getAdaptations().getFirst();
    swords.setAdaptation(held, 2);
    long refund = held.getRefundCostFor(0, 2);
    assertThat(playerData.getUsedPower()).isEqualTo(1);

    assertThat(mutate("stealth", "swords")).isEqualTo(SkillMutations.Result.MUTATED);

    PlayerSkillLine merged = playerData.getSkillLine("swords");
    assertThat(merged.getLine()).isEqualTo("mutated-stealth-swords");
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

    PlayerSkillLine merged = playerData.getSkillLines().get("mutated-swords-stealth");
    assertThat(merged.getLevel()).isEqualTo(16);
    assertThat(merged.getKnowledge()).isEqualTo(7L);
    assertThat(playerData.getSkillLines()).doesNotContainKeys("swords", "stealth");
    assertThat(playerData.getSkillLine("stealth")).isSameAs(merged);
    assertThat(playerData.getSkillLineNullable("swords")).isSameAs(merged);
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

    assertThat(mutate("swords", "stealth")).isEqualTo(SkillMutations.Result.LEVEL_TOO_LOW);
    assertThat(mutate("swords", "swords")).isEqualTo(SkillMutations.Result.SAME_SKILL);
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("swords"), null, "Merger"))
        .isEqualTo(SkillMutations.Result.UNKNOWN_SKILL);
    assertThat(mutate("swords", "axes")).isEqualTo(SkillMutations.Result.MUTATED);
    assertThat(mutate("axes", "unarmed")).isEqualTo(SkillMutations.Result.ALREADY_MUTATED);
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("mutated-swords-axes"),
        registry.getSkill("unarmed"), "Merger")).isEqualTo(SkillMutations.Result.UNAVAILABLE_SKILL);
  }

  @Test
  void aPlayerMayHoldAnyNumberOfMutationsByDefault() {
    PlayerData playerData = profile.getData();
    for (String skill : List.of("swords", "stealth", "axes", "unarmed", "ranged", "hunter", "agility")) {
      level(skill, 20, 0);
    }
    playerData.setMasterXp(1D);

    assertThat(AdaptConfig.get().getMutations().getMaxMutations()).isZero();
    assertThat(mutate("swords", "stealth")).isEqualTo(SkillMutations.Result.MUTATED);
    assertThat(mutate("axes", "unarmed")).isEqualTo(SkillMutations.Result.MUTATED);
    assertThat(mutate("ranged", "hunter")).isEqualTo(SkillMutations.Result.MUTATED);
    assertThat(playerData.getMutations()).hasSize(3);
    assertThat(mutate("agility", "swords")).isEqualTo(SkillMutations.Result.ALREADY_MUTATED);
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
    assertThat(SkillMutations.check(playerData, registry.getSkill("axes"), registry.getSkill("unarmed"), capped))
        .isEqualTo(SkillMutations.Result.MUTATED);
    mutate("axes", "unarmed");
    assertThat(SkillMutations.check(playerData, registry.getSkill("ranged"), registry.getSkill("hunter"), capped))
        .isEqualTo(SkillMutations.Result.LIMIT_REACHED);
    assertThat(SkillMutations.check(playerData, registry.getSkill("ranged"), registry.getSkill("hunter"),
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
    assertThat(old.getSkillLines()).containsKeys("swords", "stealth");

    mutate("swords", "stealth");
    PlayerData loaded = PlayerData.fromJson(playerData.toJson(true));

    assertThat(loaded.getMutations()).containsExactly(new SkillMutation("swords", "stealth"));
    assertThat(loaded.getSkillLineNullable("stealth").getLine()).isEqualTo("mutated-swords-stealth");
    assertThat(loaded.getSkillLines().get("mutated-swords-stealth").getXp())
        .isEqualTo(playerData.getSkillLines().get("mutated-swords-stealth").getXp());
  }

  @Test
  void awardsToEitherParentLandInTheMutatedLine() {
    level("swords", 20, 0);
    level("stealth", 20, 0);
    mutate("swords", "stealth");
    double before = profile.getData().getSkillLine("swords").getXp();

    XP.award(profile, registry.getSkill("stealth"), new XpAward("stealth:test", 40D, null, false, XpAward.Kind.GAMEPLAY));
    profile.getData().getSkillLine("swords").flushXpPool(null);

    assertThat(profile.getData().getSkillLine("swords").getXp()).isGreaterThan(before);
    assertThat(profile.getData().getXpEarnings().snapshot())
        .allMatch(snapshot -> snapshot.source().startsWith("mutated-swords-stealth/"));
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
    double mutatedBurst = mutated.claim("mutated-swords-stealth", Double.MAX_VALUE, 1D, now);

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
    Adaptation<?> upgrade = registry.getSkill("mutated-swords-stealth").getAdaptations().getFirst();
    merged.setAdaptation(upgrade, 2);
    long refund = upgrade.getRefundCostFor(0, 2);
    assertThat(playerData.getUsedPower()).isEqualTo(1);

    assertThat(SkillMutations.unmutate(playerData, new SkillMutation("swords", "stealth"), "Merger")).isTrue();

    assertThat(playerData.getMutations()).isEmpty();
    assertThat(playerData.getSkillLines()).doesNotContainKey("mutated-swords-stealth").containsKeys("swords", "stealth");
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

    SkillMutations.unmutate(playerData, new SkillMutation("swords", "stealth"), "Merger");
    long knowledge = playerData.getSkillLine("swords").getKnowledge() + playerData.getSkillLine("stealth").getKnowledge();
    playerData.update(profile);

    assertThat(playerData.getMasterXp()).isEqualTo(masterXp);
    assertThat(playerData.getSkillLine("swords").getKnowledge() + playerData.getSkillLine("stealth").getKnowledge())
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

    SkillMutation named = SkillMutations.mutationNamed(playerData, "Stealth");
    assertThat(named).isEqualTo(new SkillMutation("swords", "stealth"));
    assertThat(SkillMutations.mutationNamed(playerData, "mutated-axes-unarmed")).isEqualTo(new SkillMutation("axes", "unarmed"));
    assertThat(SkillMutations.mutationNamed(playerData, "ranged")).isNull();
    SkillMutations.unmutate(playerData, named, "Merger");

    assertThat(playerData.getMutations()).containsExactly(new SkillMutation("axes", "unarmed"));
    assertThat(playerData.getSkillLine("axes")).isSameAs(other);
    assertThat(playerData.getSkillLine("unarmed")).isSameAs(other);
    assertThat(playerData.getSkillLine("swords").getLine()).isEqualTo("swords");
    assertThat(playerData.mutationOrigin(new SkillMutation("swords", "stealth"))).isNull();
    assertThat(playerData.mutationOrigin(new SkillMutation("axes", "unarmed"))).isNotNull();
    assertThat(SkillMutations.unmutate(playerData, named, "Merger")).isFalse();
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
    assertThat(old.mutationOrigin(new SkillMutation("swords", "stealth"))).isNull();

    SkillMutations.unmutate(old, new SkillMutation("swords", "stealth"), "Merger");

    assertThat(old.getSkillLineNullable("swords").getXp()).isEqualTo(mergedXp);
    assertThat(old.getSkillLineNullable("stealth").getXp()).isEqualTo(mergedXp);
    assertThat(old.getSkillLineNullable("stealth").getLevel()).isEqualTo(20);
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
        .containsExactly(new SkillMutation("swords", "stealth"), new SkillMutation("axes", "unarmed"));
    SkillMutations.unmutate(single, new SkillMutation("swords", "stealth"), "Merger");
    SkillMutations.unmutate(single, new SkillMutation("axes", "unarmed"), "Merger");

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
    SkillMutations.unmutate(profile.getData(), new SkillMutation("swords", "stealth"), "Merger");

    assertThat(mutate("stealth", "swords")).isEqualTo(SkillMutations.Result.MUTATED);
    assertThat(profile.getData().getSkillLine("swords").getLine()).isEqualTo("mutated-stealth-swords");
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
        .containsExactly("mutated-swords-stealth", "swords", "stealth");
    assertThat(new AdaptCommands().complete(source(true, told), "adapt", new String[]{"unmutate", "player=Merger", "skill=st"}))
        .containsExactly("skill=stealth");
    new AdaptCommands().execute(source(true, told), "adapt", new String[]{"unmutate", "Merger", "stealth"});
    adapt.awaitTicks(() -> profile.getData().getMutations().isEmpty(), 10_000L);
    assertThat(profile.getData().getSkillLine("stealth").getLine()).isEqualTo("stealth");
  }

  @Test
  void theClientViewsShowTheMutatedSkillInPlaceOfItsParents() {
    level("swords", 20, 0);
    level("stealth", 18, 6);
    mutate("swords", "stealth");
    String id = "mutated-swords-stealth";

    CatalogView catalog = CatalogViews.build(registry, player.uuid(), profile.getData().getMutations(), false);
    SkillView swords = catalog.skills().stream().filter(skill -> skill.id().equals("swords")).findFirst().orElseThrow();
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
        "upgraded-swords-witherblade", "upgraded-swords-tempest-flow", "upgraded-swords-duelists-verdict",
        "upgraded-stealth-umbral-form", "upgraded-stealth-death-mark", "upgraded-stealth-phantom-decoy");
    assertThat(CatalogViews.build(registry, player.uuid(), List.of(), false).skills())
        .noneMatch(skill -> skill.id().equals(id));

    PlayerView view = PlayerViews.build(registry, profile);
    assertThat(view.skills()).extracting(SkillLineView::skillId).contains(id).doesNotContain("swords", "stealth");
    SkillLineView line = view.skills().stream().filter(skill -> skill.skillId().equals(id)).findFirst().orElseThrow();
    assertThat(line.level()).isEqualTo(18);
    assertThat(line.knowledge()).isEqualTo(6L);
    assertThat(line.xpToNext()).isPositive();
    assertThat(view.access().skills()).contains(id).doesNotContain("swords", "stealth");
    assertThat(view.access().rows(id)).containsExactly(
        List.of("upgraded-swords-witherblade", "upgraded-swords-tempest-flow", "upgraded-swords-duelists-verdict"),
        List.of("upgraded-stealth-umbral-form", "upgraded-stealth-death-mark", "upgraded-stealth-phantom-decoy"),
        List.of("mutated-swords-stealth-graft", "mutated-swords-stealth-echo", "mutated-swords-stealth-barter",
            "mutated-swords-stealth-attunement", "mutated-swords-stealth-convergence"));
  }

  @Test
  void mutateTabCompletesSkillIds() {
    AdaptationListingHandler.initializeAdaptationListings();
    CommandSource source = source(true, new ArrayList<>());

    List<String> root = new AdaptCommands().complete(source, "adapt", new String[]{"mut"});
    List<String> lead = new AdaptCommands().complete(source, "adapt", new String[]{"mutate", ""});
    List<String> partner = new AdaptCommands().complete(source, "adapt", new String[]{"mutate", "swords", "st"});

    assertThat(root).contains("mutate");
    assertThat(lead).contains("lead=swords", "lead=stealth", "partner=axes");
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
