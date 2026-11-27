package art.arcane.adapt.client.view;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.mutation.PairKeys;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.mutation.upgrade.SkillUpgrades;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MutationSpreadViewsTest {
  @TempDir
  Path data;

  private TestAdapt adapt;
  private AdaptPlayer profile;
  private SkillRegistry registry;

  @BeforeEach
  void start() {
    TestPlatform platform = TestPlatform.builder(data).build();
    FakePlayer player = platform.player("Spread", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, SkillStealth.class, SkillAxes.class,
        SkillUnarmed.class, SkillRanged.class, SkillHunter.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void theSpreadNumbersEqualTheMergeForSeveralPairs() {
    PlayerData playerData = profile.getData();
    level("swords", 22, 9);
    level("stealth", 17, 5);
    level("axes", 15, 0);
    level("unarmed", 31, 40);
    level("ranged", 26, 12);
    level("hunter", 26, 3);
    learnFirst("stealth", 2);
    learnFirst("swords", 1);
    learnFirst("unarmed", 1);
    learnFirst("ranged", 1);

    for (List<String> pair : List.of(List.of("swords", "stealth"), List.of("axes", "unarmed"), List.of("hunter", "ranged"))) {
      Skill<?> lead = skill(pair.get(0));
      Skill<?> partner = skill(pair.get(1));
      MutationSpreadView spread = build(lead, partner);

      assertThat(SkillMutations.mutate(playerData, lead, partner, "Spread")).isEqualTo(SkillMutations.Result.MUTATED);
      PlayerSkillLine merged = playerData.getSkillLine(SkillHost.lineKey(lead));
      assertThat(spread.summary()).as("%s", pair)
          .isEqualTo("Level " + merged.getLevel() + " · " + merged.getKnowledge() + " knowledge");
      assertThat(spread.name()).isEqualTo(registry.getMutatedSkill(PairKeys.parse(merged.getLine())).getLocalizedName());
    }
  }

  @Test
  void eachSideListsItsGainsAndOnlyTheHigherParentGivesUpSurplus() {
    level("swords", 22, 9);
    level("stealth", 17, 5);
    learnFirst("stealth", 2);
    Adaptation<?> learned = skill("stealth").getAdaptations().getFirst();

    MutationSpreadView spread = build(skill("swords"), skill("stealth"));

    MutationSide swords = spread.leadSide();
    MutationSide stealth = spread.partnerSide();
    assertThat(swords.skillId()).isEqualTo("adapt:swords");
    assertThat(swords.level()).isEqualTo(22);
    assertThat(swords.upgrades()).extracting(MutationEntry::id)
        .containsExactlyElementsOf(SkillHost.of(skill("adapt:swords")).getUpgrades().stream().map((Adaptation<?> adaptation) -> adaptation.getId().toString()).toList());
    assertThat(swords.kept()).extracting(MutationEntry::id)
        .containsExactlyElementsOf(SkillUpgrades.kept(skill("adapt:swords")).stream().map((Adaptation<?> adaptation) -> adaptation.getId().toString()).toList());
    assertThat(swords.surplus()).isEqualTo("5 levels · 9 knowledge");
    assertThat(stealth.surplus()).isEmpty();
    assertThat(stealth.lost().getFirst().id()).isEqualTo(learned.getId().toString());
    assertThat(stealth.lost().getFirst().level()).isEqualTo(2);
    assertThat(stealth.lost()).extracting(MutationEntry::id).doesNotContainAnyElementsOf(stealth.kept().stream()
        .filter((MutationEntry kept) -> !kept.id().equals(learned.getId().toString())).map(MutationEntry::id).toList());
    assertThat(spread.mutated()).hasSize(5);
  }

  @Test
  void theLeadAloneHasNoNameNoPartnerSideAndItsOwnGains() {
    level("swords", 22, 9);

    MutationSpreadView spread = build(skill("swords"), null);

    assertThat(spread.partner()).isEmpty();
    assertThat(spread.name()).isEmpty();
    assertThat(spread.mutated()).isEmpty();
    assertThat(spread.partnerSide().skillId()).isEmpty();
    assertThat(spread.leadSide().upgrades()).hasSize(SkillHost.of(skill("adapt:swords")).getUpgrades().size());
    assertThat(spread.leadSide().surplus()).isEmpty();
  }

  @Test
  void ineligibleSkillsAreListedWithTheirReason() throws Exception {
    PlayerData playerData = profile.getData();
    level("swords", 20, 0);
    level("stealth", 20, 0);
    level("axes", 20, 0);
    level("unarmed", 20, 0);
    level("ranged", 9, 0);
    SkillMutations.mutate(playerData, skill("stealth"), skill("axes"), "Spread");

    MutationSpreadView spread = build(skill("swords"), null);

    assertThat(spread.candidates()).extracting(MutationCandidate::skillId)
        .containsExactlyInAnyOrder("adapt:stealth", "adapt:axes", "adapt:unarmed", "adapt:ranged", "adapt:hunter");
    assertThat(spread.candidate("adapt:unarmed").state()).isEqualTo(MutationCandidate.State.ELIGIBLE);
    assertThat(spread.candidate("adapt:unarmed").reason()).isEmpty();
    assertThat(spread.candidate("adapt:stealth").state()).isEqualTo(MutationCandidate.State.ALREADY_MUTATED);
    assertThat(spread.candidate("adapt:stealth").reason()).isEqualTo("Already mutated");
    assertThat(spread.candidate("adapt:ranged").state()).isEqualTo(MutationCandidate.State.LEVEL_TOO_LOW);
    assertThat(spread.candidate("adapt:ranged").level()).isEqualTo(9);

    AdaptConfig.Mutations capped = TomlCodec.fromToml("maxMutations = 1", AdaptConfig.Mutations.class);
    MutationSpreadView full = MutationSpreadViews.build(registry, playerData, skill("swords"), skill("unarmed"), capped, "");
    assertThat(full.candidate("adapt:unarmed").state()).isEqualTo(MutationCandidate.State.LIMIT_REACHED);
    assertThat(full.partner()).isEmpty();
  }

  private MutationSpreadView build(Skill<?> lead, Skill<?> partner) {
    return MutationSpreadViews.build(registry, profile.getData(), lead, partner, AdaptConfig.get().getMutations(), "");
  }

  private void learnFirst(String skill, int level) {
    profile.getData().getSkillLine(skill).setAdaptation(skill(skill).getAdaptations().getFirst(), level);
  }

  private Skill<?> skill(String name) {
    return registry.getSkill(name);
  }

  private void level(String skill, int level, long knowledge) {
    PlayerSkillLine line = profile.getData().getSkillLine(skill);
    line.setXp(XP.getXpForLevel(level) + 1D);
    line.setKnowledge(knowledge);
    line.settleWithoutRewards();
  }
}
