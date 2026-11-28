package art.arcane.adapt.engine.gui;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.client.view.MutationEntry;
import art.arcane.adapt.client.view.MutationSpreadView;
import art.arcane.adapt.client.view.MutationSpreadViews;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.common.format.C;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MutationSummaryTest {
  @TempDir
  Path data;

  @Test
  void everyBuiltInPairListsEverythingLostAndGainedGroupedAndCapped() {
    List<Class<? extends SimpleSkill<?>>> skills = BuiltinAddon.skills().stream()
        .<Class<? extends SimpleSkill<?>>>map(BuiltinAddon.Entry::type).toList();
    TestPlatform platform = TestPlatform.builder(data).build();
    FakePlayer player = platform.player("Summary", 0.5D, 61D, 0.5D);
    try (TestAdapt adapt = TestAdapt.start(platform, skills)) {
      AdaptPlayer profile = adapt.join(player);
      SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
      PlayerData playerData = profile.getData();
      AdaptConfig.Mutations config = AdaptConfig.get().getMutations();
      List<Skill<?>> roster = new ArrayList<>(registry.getSkills());
      int level = 20;
      for (Skill<?> skill : roster) {
        PlayerSkillLine line = playerData.getSkillLine(SkillHost.lineKey(skill));
        line.setXp(XP.getXpForLevel(level) + 1D);
        line.setKnowledge(level * 3L);
        line.settleWithoutRewards();
        if (!skill.getAdaptations().isEmpty()) {
          line.setAdaptation(skill.getAdaptations().getFirst(), 1);
        }
        level++;
      }

      int pairs = 0;
      int capped = 0;
      for (Skill<?> lead : roster) {
        for (Skill<?> partner : roster) {
          if (lead == partner) {
            continue;
          }
          MutationSpreadView spread = MutationSpreadViews.build(registry, playerData, lead, partner, config, "");
          assertThat(spread.partner()).as("%s + %s is offered", lead.getName(), partner.getName()).isNotEmpty();
          List<MutationSummary.Section> losses = MutationSummary.losses(spread, registry);
          List<MutationSummary.Section> gains = MutationSummary.gains(spread, registry);

          assertThat(entries(losses)).containsExactlyElementsOf(concat(spread.leadSide().lost(), spread.partnerSide().lost()));
          assertThat(losses).allMatch((MutationSummary.Section section) -> section.kind() == MutationSummary.Kind.PARENT);
          assertThat(String.join("", details(losses))).isEqualTo(spread.leadSide().surplus() + spread.partnerSide().surplus());
          assertThat(losses.getFirst().skillId()).isEqualTo(spread.lead());
          assertThat(losses.getFirst().entries()).anyMatch((MutationEntry entry) -> entry.level() > 0);

          List<MutationEntry> gained = new ArrayList<>(spread.mutated());
          gained.addAll(spread.leadSide().upgrades());
          gained.addAll(spread.partnerSide().upgrades());
          gained.addAll(spread.leadSide().kept());
          gained.addAll(spread.partnerSide().kept());
          gained.addAll(spread.leadSide().tools());
          gained.addAll(spread.partnerSide().tools());
          assertThat(entries(gains)).containsExactlyElementsOf(gained);
          assertThat(gains.getFirst().kind()).isEqualTo(MutationSummary.Kind.RESULT);
          assertThat(gains.getFirst().heading()).isEqualTo(spread.name());
          assertThat(gains.getFirst().entries()).hasSize(5);
          assertThat(gains).extracting(MutationSummary.Section::kind).isSorted();

          for (List<MutationSummary.Section> sections : List.of(losses, gains)) {
            List<String> lore = new ArrayList<>();
            for (String line : MutationSummary.lore(sections, registry)) {
              lore.add(C.stripColor(line));
            }
            assertThat(lore).isEqualTo(expectedLore(sections));
            for (MutationSummary.Section section : sections) {
              if (section.entries().size() > LoreLists.CAP) {
                capped++;
              }
            }
          }
          pairs++;
        }
      }
      assertThat(pairs).isEqualTo(552);
      assertThat(capped).isPositive();
    }
  }

  @Test
  void aCappedListShowsTheFirstNamesAndCountsTheRest() {
    List<String> names = new ArrayList<>();
    for (int index = 1; index <= 11; index++) {
      names.add("N" + index);
    }
    assertThat(LoreLists.joined(names)).isEqualTo("N1, N2, N3, N4, N5, N6, N7, N8, and 3 more");
    assertThat(LoreLists.joined(names.subList(0, 8))).isEqualTo("N1, N2, N3, N4, N5, N6, N7, N8");
    assertThat(LoreLists.joined(List.of())).isEmpty();
    assertThat(LoreLists.lines("", names)).extracting(C::stripColor).hasSize(9).endsWith("and 3 more");
  }

  private static List<String> expectedLore(List<MutationSummary.Section> sections) {
    List<String> lines = new ArrayList<>();
    for (MutationSummary.Section section : sections) {
      if (!lines.isEmpty()) {
        lines.add(" ");
      }
      lines.add(section.heading());
      if (!section.detail().isEmpty()) {
        lines.add(section.detail());
      }
      List<MutationEntry> entries = section.entries();
      if (!entries.isEmpty()) {
        List<String> shown = new ArrayList<>();
        for (MutationEntry entry : entries.subList(0, Math.min(LoreLists.CAP, entries.size()))) {
          shown.add(entry.name());
        }
        String line = String.join(", ", shown);
        if (entries.size() > LoreLists.CAP) {
          line += ", and " + (entries.size() - LoreLists.CAP) + " more";
        }
        lines.add(line);
      }
    }
    return lines.stream().map(C::stripColor).toList();
  }

  private static List<MutationEntry> entries(List<MutationSummary.Section> sections) {
    List<MutationEntry> entries = new ArrayList<>();
    for (MutationSummary.Section section : sections) {
      entries.addAll(section.entries());
    }
    return entries;
  }

  private static List<String> details(List<MutationSummary.Section> sections) {
    List<String> details = new ArrayList<>();
    for (MutationSummary.Section section : sections) {
      details.add(section.detail());
    }
    return details;
  }

  private static List<MutationEntry> concat(List<MutationEntry> first, List<MutationEntry> second) {
    List<MutationEntry> all = new ArrayList<>(first);
    all.addAll(second);
    return all;
  }
}
