package art.arcane.adapt.pack.screen;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.CatalogViews;
import art.arcane.adapt.client.view.SkillView;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillMenus;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.pack.PackResources;
import art.arcane.adapt.pack.PackSamples;
import art.arcane.adapt.pack.layout.Card;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The skill page's list of every built-in skill and every mutated skill, paged on both tiers. */
class SkillLeavesTest {
  @TempDir
  Path data;

  private final FakePlayer player = new FakePlayer("Viewer", new Loc(new FakeWorld("world"), 0, 64, 0));
  private final PackResources resources = PackSamples.resources();
  private TestAdapt adapt;
  /** Each skill's list cells by skill id, the built-in skills first. */
  private final Map<String, List<PackSkillScreen.Cell>> lists = new LinkedHashMap<>();
  private int builtin;

  @BeforeEach
  void start() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build(), new BuiltinAddon());
    SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
    List<SkillPair> pairs = new ArrayList<>();
    for (Skill<?> lead : registry.getSkills()) {
      for (Skill<?> partner : registry.getSkills()) {
        if (lead != partner && registry.getMutatedSkill(new SkillPair(lead.getId(), partner.getId())) != null) {
          pairs.add(new SkillPair(lead.getId(), partner.getId()));
        }
      }
    }
    CatalogView view = CatalogViews.build(registry, player.uuid(), pairs, true);
    Map<String, SkillView> skills = new HashMap<>();
    view.skills().forEach((SkillView skill) -> skills.put(skill.id(), skill));
    ServerPackBackend backend = new ServerPackBackend();
    List<Skill<?>> listed = new ArrayList<>(registry.getSkills());
    builtin = listed.size();
    for (SkillPair pair : pairs) {
      listed.add(registry.getMutatedSkill(pair));
    }
    for (Skill<?> source : listed) {
      SkillView skill = skills.get(SkillHost.lineKey(source));
      Map<String, AdaptationView> adaptations = new HashMap<>();
      skill.adaptations().forEach((AdaptationView adaptation) -> adaptations.put(adaptation.id(), adaptation));
      List<List<AdaptationView>> groups = new ArrayList<>();
      for (List<Adaptation<?>> row : SkillMenus.adaptationRows(source, player)) {
        List<AdaptationView> group = new ArrayList<>();
        for (Adaptation<?> adaptation : row) {
          group.add(adaptations.get(adaptation.getId().toString()));
        }
        groups.add(group);
      }
      List<SkillView> owners = new ArrayList<>();
      for (String owner : backend.groupOwners(player, skill.id())) {
        owners.add(skills.get(owner));
      }
      lists.put(skill.id(), PackSkillScreen.cells(skill, groups, backend.groupLabels(player, skill.id()), owners));
    }
  }

  @AfterEach
  void stop() {
    adapt.close();
  }

  @Test
  void everyGroupStaysOnOneLeafUnderItsHeadingUnlessALeafCannotHoldIt() {
    List<String> problems = new ArrayList<>();
    each((String skill, List<PackSkillScreen.Cell> cells, Tier tier, List<List<PackSkillScreen.Cell>> leaves) -> {
      for (List<PackSkillScreen.Cell> group : groups(cells)) {
        PackSkillScreen.Cell heading = group.get(0);
        List<Integer> holding = new ArrayList<>();
        for (int leaf = 0; leaf < leaves.size(); leaf++) {
          for (PackSkillScreen.Cell cell : group.subList(1, group.size())) {
            if (leaves.get(leaf).contains(cell) && !holding.contains(leaf)) {
              holding.add(leaf);
            }
          }
        }
        boolean oversize = tier.leaves(group).size() > 1;
        if (holding.size() > 1 && !oversize) {
          problems.add(skill + " " + tier + ": " + heading.heading() + " split over leaves " + holding);
        }
        for (int leaf : holding) {
          List<PackSkillScreen.Cell> cellsOn = leaves.get(leaf);
          int first = Integer.MAX_VALUE;
          for (PackSkillScreen.Cell cell : group.subList(1, group.size())) {
            int at = indexOf(cellsOn, cell);
            first = at < 0 ? first : Math.min(first, at);
          }
          int headed = indexOf(cellsOn, heading);
          if (headed < 0 || headed > first) {
            problems.add(skill + " " + tier + ": " + heading.heading() + " lacks its heading on leaf " + leaf);
          }
        }
      }
    });
    assertThat(problems).isEmpty();
  }

  @Test
  void adaptationsReadLeftToRightLineByLineInListOrder() {
    List<String> problems = new ArrayList<>();
    each((String skill, List<PackSkillScreen.Cell> cells, Tier tier, List<List<PackSkillScreen.Cell>> leaves) -> {
      List<PackSkillScreen.Cell> read = new ArrayList<>();
      for (List<PackSkillScreen.Cell> leaf : leaves) {
        for (int place = 0; place < leaf.size(); place++) {
          PackSkillScreen.Cell cell = leaf.get(place);
          if (cell != null && cell.adaptation() != null) {
            read.add(cell);
          }
          if (place % 2 == 0 && cell == null && place + 1 < leaf.size() && leaf.get(place + 1) != null
              && leaf.get(place + 1).adaptation() != null) {
            problems.add(skill + " " + tier + ": an adaptation on the right of an empty left place");
          }
        }
      }
      List<PackSkillScreen.Cell> listed = new ArrayList<>();
      for (PackSkillScreen.Cell cell : cells) {
        if (cell.adaptation() != null) {
          listed.add(cell);
        }
      }
      if (!same(read, listed)) {
        problems.add(skill + " " + tier + ": adaptations out of list order");
      }
      for (List<PackSkillScreen.Cell> group : groups(cells)) {
        for (int item = 1; item + 1 < group.size(); item++) {
          int[] at = where(leaves, group.get(item));
          int[] next = where(leaves, group.get(item + 1));
          boolean beside = at[0] == next[0] && at[1] % 2 == 0 && next[1] == at[1] + 1;
          boolean below = at[1] % 2 == 1 && next[1] % 2 == 0 && (at[0] != next[0] || nothingBetween(leaves.get(at[0]), at[1], next[1]));
          if (!beside && !below) {
            problems.add(skill + " " + tier + ": " + group.get(0).heading() + " item " + item + " does not lead on to the next");
          }
        }
      }
    });
    assertThat(problems).isEmpty();
  }

  @Test
  void noLeafEndsOnAHeadingAndNoLeafCouldHaveTakenTheNextGroup() {
    List<String> problems = new ArrayList<>();
    each((String skill, List<PackSkillScreen.Cell> cells, Tier tier, List<List<PackSkillScreen.Cell>> leaves) -> {
      for (int index = 0; index < leaves.size(); index++) {
        List<PackSkillScreen.Cell> leaf = leaves.get(index);
        for (int place = 0; place < leaf.size(); place++) {
          if (leaf.get(place) != null && leaf.get(place).heading() != null && !adaptationAfter(leaf, place)) {
            problems.add(skill + " " + tier + ": heading " + leaf.get(place).heading() + " ends leaf " + index);
          }
        }
        if (index + 1 < leaves.size()) {
          int through = lastListed(cells, leaves.get(index + 1));
          if (tier.leaves(cells.subList(0, through + 1)).size() <= index + 1) {
            problems.add(skill + " " + tier + ": leaf " + index + " could have held the next group");
          }
        }
      }
    });
    assertThat(problems).isEmpty();
  }

  @Test
  void theListTakesNoMoreLeavesThanColumnByColumnSaveTaming() {
    Map<String, Integer> expected = new LinkedHashMap<>();
    for (String skill : List.of("agility", "architect", "axes", "brewing", "chronos", "crafting", "excavation", "pickaxe", "ranged",
        "rift", "unarmed", "discovery", "hunter")) {
      expected.put("adapt:" + skill, 1);
    }
    for (String skill : List.of("blocking", "enchanting", "golemancy", "herbalism", "kinetics", "nether", "seaborne", "stealth",
        "swords", "tragoul")) {
      expected.put("adapt:" + skill, 2);
    }
    // Column by column these took the same, but for Discovery and Hunter (two) and Taming (one): Taming's two groups of
    // seven sat side by side, and read left to right the second cannot start above the gap and still fit below it.
    expected.put("adapt:taming", 2);
    Map<String, Integer> now = new LinkedHashMap<>();
    int mutated = 0;
    int mutatedLeaves = 0;
    for (Map.Entry<String, List<PackSkillScreen.Cell>> entry : lists.entrySet()) {
      int leaves = Tier.PLAYER_ROWS.leaves(entry.getValue()).size();
      if (expected.containsKey(entry.getKey())) {
        now.put(entry.getKey(), leaves);
      } else {
        mutated++;
        mutatedLeaves += leaves;
      }
    }
    assertThat(now).isEqualTo(expected);
    assertThat(now.get("adapt:discovery")).as("fewer than the two it took column by column").isEqualTo(1);
    assertThat(mutated).isEqualTo(552);
    assertThat(mutatedLeaves).as("every mutated skill on one leaf, as before").isEqualTo(mutated);
    assertThat(lists.size()).isEqualTo(builtin + mutated);
  }

  /** The player-rows tier with its gap line and header line, and the top-rows tier. */
  private enum Tier {
    PLAYER_ROWS(true),
    TOP_ROWS(false);

    private final boolean lower;

    Tier(boolean lower) {
      this.lower = lower;
    }

    List<List<PackSkillScreen.Cell>> leaves(List<PackSkillScreen.Cell> cells) {
      List<Card> seats = PackSamples.resources().layout().screen(PackSkillScreen.SCREEN, lower).cards();
      return PackSkillScreen.leaves(cells, seats.size(), PackSkillScreen.gap(seats), lower);
    }
  }

  private interface Check {
    void check(String skill, List<PackSkillScreen.Cell> cells, Tier tier, List<List<PackSkillScreen.Cell>> leaves);
  }

  private void each(Check check) {
    for (Tier tier : Tier.values()) {
      for (Map.Entry<String, List<PackSkillScreen.Cell>> entry : lists.entrySet()) {
        check.check(entry.getKey(), entry.getValue(), tier, tier.leaves(entry.getValue()));
      }
    }
  }

  /** Each headed group: its heading, then its adaptations. */
  private static List<List<PackSkillScreen.Cell>> groups(List<PackSkillScreen.Cell> cells) {
    List<List<PackSkillScreen.Cell>> groups = new ArrayList<>();
    for (PackSkillScreen.Cell cell : cells) {
      if (cell.heading() != null) {
        groups.add(new ArrayList<>(List.of(cell)));
      } else if (!groups.isEmpty()) {
        groups.get(groups.size() - 1).add(cell);
      }
    }
    return groups;
  }

  private static int indexOf(List<PackSkillScreen.Cell> cells, PackSkillScreen.Cell wanted) {
    for (int index = 0; index < cells.size(); index++) {
      if (cells.get(index) == wanted) {
        return index;
      }
    }
    return -1;
  }

  private static boolean same(List<PackSkillScreen.Cell> left, List<PackSkillScreen.Cell> right) {
    if (left.size() != right.size()) {
      return false;
    }
    for (int index = 0; index < left.size(); index++) {
      if (left.get(index) != right.get(index)) {
        return false;
      }
    }
    return true;
  }

  /** The leaf and place of {@code cell}. */
  private static int[] where(List<List<PackSkillScreen.Cell>> leaves, PackSkillScreen.Cell cell) {
    for (int leaf = 0; leaf < leaves.size(); leaf++) {
      int at = indexOf(leaves.get(leaf), cell);
      if (at >= 0) {
        return new int[] {leaf, at};
      }
    }
    throw new AssertionError("not placed");
  }

  private static boolean nothingBetween(List<PackSkillScreen.Cell> leaf, int from, int to) {
    for (int place = from + 1; place < to; place++) {
      if (leaf.get(place) != null) {
        return false;
      }
    }
    return to > from;
  }

  private static boolean adaptationAfter(List<PackSkillScreen.Cell> leaf, int place) {
    for (int after = place + 1; after < leaf.size(); after++) {
      if (leaf.get(after) != null) {
        return leaf.get(after).adaptation() != null;
      }
    }
    return false;
  }

  /** The index in {@code cells} of the last cell of the first group {@code leaf} starts. */
  private static int lastListed(List<PackSkillScreen.Cell> cells, List<PackSkillScreen.Cell> leaf) {
    PackSkillScreen.Cell first = null;
    for (PackSkillScreen.Cell cell : leaf) {
      if (cell != null) {
        first = cell;
        break;
      }
    }
    int index = indexOf(cells, first);
    int end = index + 1;
    while (end < cells.size() && cells.get(end).heading() == null) {
      end++;
    }
    return end - 1;
  }
}
