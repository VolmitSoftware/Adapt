package art.arcane.adapt.util.common.inventorygui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GuiGroupLayoutTest {
  private static final Comparator<String> DEFAULT_ORDER = Comparator.<String>naturalOrder();

  @Test
  void eachGroupStartsOnANewCenteredRow() {
    GuiGroupLayout.Plan<String> plan = GuiGroupLayout.plan(
        List.of(names("a", 2), names("b", 4), names("c", 1)), true, false);

    assertThat(plan.pageCount()).isEqualTo(1);
    List<List<String>> rows = plan.pages().get(0).rows();
    assertThat(rows).hasSize(3);
    assertThat(rows.get(0)).containsExactly("a0", "a1");
    assertThat(rows.get(1)).containsExactly("b0", "b1", "b2", "b3");
    assertThat(rows.get(2)).containsExactly("c0");
    assertThat(centered(rows.get(0))).containsExactly(-1, 1);
    assertThat(centered(rows.get(1))).containsExactly(-2, -1, 1, 2);
    assertThat(centered(rows.get(2))).containsExactly(0);
    assertThat(plan.rows()).isEqualTo(4);
    assertThat(plan.hasNavigationRow()).isTrue();
  }

  @Test
  void aGroupLongerThanOneRowWrapsIntoFurtherCenteredRows() {
    GuiGroupLayout.Plan<String> plan = GuiGroupLayout.plan(
        List.of(names("a", 3), names("b", 12), names("c", 2)), false, false);

    List<List<String>> rows = plan.pages().get(0).rows();
    assertThat(rows).extracting(List::size).containsExactly(3, 9, 3, 2);
    assertThat(rows.get(1)).containsExactlyElementsOf(names("b", 12).subList(0, 9));
    assertThat(rows.get(2)).containsExactlyElementsOf(names("b", 12).subList(9, 12));
    assertThat(centered(rows.get(2))).containsExactly(-1, 0, 1);
    assertThat(centered(rows.get(3))).containsExactly(-1, 1);
  }

  @Test
  void emptyGroupsTakeNoRow() {
    GuiGroupLayout.Plan<String> plan = GuiGroupLayout.plan(
        List.of(List.of(), names("a", 2), List.of(), names("b", 1), List.of()), true, false);

    assertThat(plan.pages().get(0).rows()).extracting(List::size).containsExactly(2, 1);
    assertThat(plan.rows()).isEqualTo(3);
  }

  @Test
  void hiddenAdaptationsLeaveNoGaps() {
    List<String> visible = List.of("alpha", "gamma", "delta", "stray");
    List<List<String>> groups = GuiGroupLayout.arrange(
        visible, name -> name, name -> GuiConfig.UNRANKED, DEFAULT_ORDER,
        List.of(List.of("alpha", "beta", "gamma"), List.of("hidden-one", "hidden-two"), List.of("delta")));

    assertThat(groups).containsExactly(
        List.of("alpha", "gamma"),
        List.of("delta"),
        List.of("stray"));
    GuiGroupLayout.Plan<String> plan = GuiGroupLayout.plan(groups, false, false);
    assertThat(plan.pages().get(0).rows()).extracting(List::size).containsExactly(2, 1, 1);
  }

  @Test
  void declaredOrderAppliesWhenNothingIsConfigured() {
    List<List<String>> groups = GuiGroupLayout.arrange(
        List.of("a", "b", "c", "d"), name -> name, name -> GuiConfig.UNRANKED, DEFAULT_ORDER,
        List.of(List.of("d", "b"), List.of("c", "a")));

    assertThat(groups).containsExactly(List.of("d", "b"), List.of("c", "a"));
  }

  @Test
  void configuredOrderAppliesWithinGroupsBeforeDeclaredOrder() {
    Map<String, Integer> ranks = Map.of("c", 0, "a", 1);
    List<List<String>> groups = GuiGroupLayout.arrange(
        List.of("a", "b", "c", "d", "e"), name -> name, name -> GuiConfig.rankOf(ranks, name), DEFAULT_ORDER,
        List.of(List.of("a", "b", "c"), List.of("d", "e")));

    assertThat(groups).containsExactly(List.of("c", "a", "b"), List.of("d", "e"));
  }

  @Test
  void configuredOrderNeverMovesAnAdaptationAcrossGroups() {
    Map<String, Integer> ranks = Map.of("d", 0, "a", 1);
    List<List<String>> groups = GuiGroupLayout.arrange(
        List.of("a", "b", "c", "d"), name -> name, name -> GuiConfig.rankOf(ranks, name), DEFAULT_ORDER,
        List.of(List.of("a", "b"), List.of("c", "d")));

    assertThat(groups).containsExactly(List.of("a", "b"), List.of("d", "c"));
  }

  @Test
  void ungroupedAdaptationsFormAFinalGroupSortedTheCurrentWay() {
    Map<String, Integer> ranks = Map.of("z", 0);
    List<List<String>> groups = GuiGroupLayout.arrange(
        List.of("m", "z", "k", "a", "q"), name -> name, name -> GuiConfig.rankOf(ranks, name),
        Comparator.comparingInt((String name) -> GuiConfig.rankOf(ranks, name)).thenComparing(Comparator.naturalOrder()),
        List.of(List.of("q")));

    assertThat(groups).containsExactly(List.of("q"), List.of("z", "a", "k", "m"));
  }

  @Test
  void anAdaptationListedInTwoGroupsStaysInTheFirst() {
    List<List<String>> groups = GuiGroupLayout.arrange(
        List.of("a", "b"), name -> name, name -> GuiConfig.UNRANKED, DEFAULT_ORDER,
        List.of(List.of("a"), List.of("a", "b")));

    assertThat(groups).containsExactly(List.of("a"), List.of("b"));
  }

  @Test
  void groupNamesMatchIgnoringCase() {
    List<List<String>> groups = GuiGroupLayout.arrange(
        List.of("Alpha", "beta"), name -> name, name -> GuiConfig.UNRANKED, DEFAULT_ORDER,
        List.of(List.of("beta"), List.of("ALPHA")));

    assertThat(groups).containsExactly(List.of("beta"), List.of("Alpha"));
  }

  @Test
  void withoutGroupsEverythingSortsAsOneGroup() {
    List<List<String>> groups = GuiGroupLayout.arrange(
        List.of("c", "a", "b"), name -> name, name -> GuiConfig.UNRANKED, DEFAULT_ORDER, List.of());

    assertThat(groups).containsExactly(List.of("a", "b", "c"));
    assertThat(GuiGroupLayout.arrange(
        List.<String>of(), name -> name, name -> GuiConfig.UNRANKED, DEFAULT_ORDER, List.of())).isEmpty();
  }

  @Test
  void pagesKeepWholeGroupsTogether() {
    GuiGroupLayout.Plan<String> plan = GuiGroupLayout.plan(
        List.of(names("a", 20), names("b", 9), names("c", 9), names("d", 9)), true, true);

    assertThat(plan.hasNavigationRow()).isTrue();
    assertThat(plan.rows()).isEqualTo(6);
    assertThat(plan.pageCount()).isEqualTo(2);
    assertThat(plan.pages().get(0).rows()).extracting(List::size).containsExactly(9, 9, 2, 9);
    assertThat(plan.pages().get(1).rows()).extracting(List::size).containsExactly(9, 9);
    assertThat(plan.pages().get(0).rows().get(3).get(0)).isEqualTo("b0");
    assertThat(plan.pages().get(1).rows().get(0).get(0)).isEqualTo("c0");
    assertThat(plan.pages().get(1).offset()).isEqualTo(29);
    assertThat(plan.pages().get(1).size()).isEqualTo(18);
  }

  @Test
  void aGroupThatFitsAPageIsMovedWholeToTheNextPage() {
    GuiGroupLayout.Plan<String> plan = GuiGroupLayout.plan(
        List.of(names("a", 27), names("b", 18), names("c", 5)), true, true);

    assertThat(plan.pageCount()).isEqualTo(2);
    assertThat(plan.pages().get(0).rows()).extracting(List::size).containsExactly(9, 9, 9);
    assertThat(plan.pages().get(1).rows()).extracting(List::size).containsExactly(9, 9, 5);
    assertThat(plan.pages().get(1).rows().get(0).get(0)).isEqualTo("b0");
  }

  @Test
  void aGroupSplitsAcrossPagesOnlyWhenItAloneExceedsAPage() {
    GuiGroupLayout.Plan<String> plan = GuiGroupLayout.plan(
        List.of(names("a", 2), names("b", 90), names("c", 3)), true, true);

    int capacity = 4;
    assertThat(plan.rows()).isEqualTo(6);
    assertThat(plan.pageCount()).isEqualTo(4);
    assertThat(plan.pages().get(0).rows()).extracting(List::size).containsExactly(2);
    assertThat(plan.pages().get(1).rows()).hasSize(capacity);
    assertThat(plan.pages().get(2).rows()).hasSize(capacity);
    assertThat(plan.pages().get(3).rows()).extracting(List::size).containsExactly(9, 9, 3);
    assertThat(plan.pages().get(3).rows().get(2)).containsExactly("c0", "c1", "c2");
    assertThat(flatten(plan)).containsExactlyElementsOf(concat(names("a", 2), names("b", 90), names("c", 3)));
  }

  @Test
  void settingsRowShrinksThePageAndKeepsNavigation() {
    GuiGroupLayout.Plan<String> plan = GuiGroupLayout.plan(
        List.of(names("a", 3), names("b", 3), names("c", 3), names("d", 3), names("e", 3), names("f", 3)), false, true);

    assertThat(plan.hasNavigationRow()).isTrue();
    assertThat(plan.rows()).isEqualTo(6);
    assertThat(plan.pageCount()).isEqualTo(2);
    assertThat(plan.pages().get(0).rows()).hasSize(4);
    assertThat(plan.pages().get(1).rows()).hasSize(2);
  }

  @Test
  void withoutNavigationAFullBoardFitsOnePage() {
    GuiGroupLayout.Plan<String> plan = GuiGroupLayout.plan(
        List.of(names("a", 1), names("b", 1), names("c", 1), names("d", 1), names("e", 1), names("f", 1)), false, false);

    assertThat(plan.hasNavigationRow()).isFalse();
    assertThat(plan.rows()).isEqualTo(6);
    assertThat(plan.pageCount()).isEqualTo(1);
  }

  @Test
  void anEmptyPlanKeepsOneContentRow() {
    GuiGroupLayout.Plan<String> plan = GuiGroupLayout.plan(List.of(), true, true);

    assertThat(plan.pageCount()).isEqualTo(1);
    assertThat(plan.pages().get(0).rows()).isEmpty();
    assertThat(plan.pages().get(0).size()).isZero();
    assertThat(plan.rows()).isEqualTo(3);
    assertThat(plan.hasNavigationRow()).isTrue();
  }

  @Test
  void aSingleGroupPlansExactlyLikeTheFlatPager() {
    for (int total = 0; total <= 200; total++) {
      for (boolean navigation : new boolean[]{false, true}) {
        for (boolean settings : new boolean[]{false, true}) {
          List<String> items = names("i", total);
          GuiLayout.PagePlan flat = GuiLayout.planWithSettings(total, navigation, settings);
          List<List<String>> groups = new ArrayList<>();
          if (total > 0) {
            groups.add(items);
          }
          GuiGroupLayout.Plan<String> plan = GuiGroupLayout.plan(groups, navigation, settings);

          assertThat(plan.rows()).as("rows for %s/%s/%s", total, navigation, settings).isEqualTo(flat.rows());
          assertThat(plan.hasNavigationRow()).isEqualTo(flat.hasNavigationRow());
          assertThat(plan.pageCount()).isEqualTo(flat.pageCount());
          for (int page = 0; page < flat.pageCount(); page++) {
            int start = page * flat.itemsPerPage();
            int end = Math.min(total, start + flat.itemsPerPage());
            List<String> expected = items.subList(start, end);
            GuiGroupLayout.Page<String> actual = plan.pages().get(page);
            assertThat(actual.offset()).isEqualTo(start);
            assertThat(actual.size()).isEqualTo(end - start);
            assertThat(actual.rows().stream().flatMap(List::stream).toList()).containsExactlyElementsOf(expected);
            for (int row = 0; row < actual.rows().size(); row++) {
              int rowStart = row * GuiLayout.WIDTH;
              int rowCount = Math.min(GuiLayout.WIDTH, expected.size() - rowStart);
              assertThat(actual.rows().get(row)).hasSize(rowCount);
            }
          }
        }
      }
    }
  }

  @Test
  void everyPagePlanStaysWithinSixRowsAndKeepsEveryItemOnce() {
    for (int groupCount = 0; groupCount <= 7; groupCount++) {
      for (int size = 0; size <= 20; size += 3) {
        for (boolean navigation : new boolean[]{false, true}) {
          for (boolean settings : new boolean[]{false, true}) {
            List<List<String>> groups = new ArrayList<>();
            for (int group = 0; group < groupCount; group++) {
              groups.add(names("g" + group + "-", size + group));
            }
            GuiGroupLayout.Plan<String> plan = GuiGroupLayout.plan(groups, navigation, settings);
            int contentRows = plan.rows() - (plan.hasNavigationRow() ? 1 : 0) - (settings ? 1 : 0);

            assertThat(plan.rows()).isBetween(1, GuiLayout.MAX_ROWS);
            assertThat(contentRows).isGreaterThanOrEqualTo(1);
            if (plan.pageCount() > 1) {
              assertThat(plan.hasNavigationRow()).isTrue();
            }
            for (GuiGroupLayout.Page<String> page : plan.pages()) {
              assertThat(page.rows().size()).isLessThanOrEqualTo(contentRows);
              for (List<String> row : page.rows()) {
                assertThat(row).isNotEmpty().hasSizeLessThanOrEqualTo(GuiLayout.WIDTH);
              }
            }
            assertThat(flatten(plan)).containsExactlyElementsOf(groups.stream().flatMap(List::stream).toList());
          }
        }
      }
    }
  }

  private static List<String> names(String prefix, int count) {
    List<String> names = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      names.add(prefix + i);
    }
    return names;
  }

  private static List<Integer> centered(List<String> row) {
    List<Integer> positions = new ArrayList<>(row.size());
    for (int i = 0; i < row.size(); i++) {
      positions.add(GuiLayout.centeredPosition(i, row.size()));
    }
    return positions;
  }

  private static List<String> flatten(GuiGroupLayout.Plan<String> plan) {
    return plan.pages().stream().flatMap(page -> page.rows().stream()).flatMap(List::stream).toList();
  }

  private static List<String> concat(List<String> first, List<String> second, List<String> third) {
    List<String> all = new ArrayList<>(first);
    all.addAll(second);
    all.addAll(third);
    return all;
  }
}
