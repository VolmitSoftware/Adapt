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
  void declaredOrderAppliesWhenNothingIsConfigured() {
    List<List<String>> groups = GuiGroupLayout.arrange(
        List.of("a", "b", "c", "d"), name -> name, name -> GuiConfig.UNRANKED, DEFAULT_ORDER,
        List.of(List.of("d", "b"), List.of("c", "a")));

    assertThat(groups).containsExactly(List.of("d", "b"), List.of("c", "a"));
  }

  @Test
  void configuredOrderAppliesWithinGroupsBeforeDeclaredOrder() {
    Map<String, Integer> ranks = GuiConfig.ranks(List.of("c", "a"), null, "test");
    List<List<String>> groups = GuiGroupLayout.arrange(
        List.of("a", "b", "c", "d", "e"), name -> name, name -> GuiConfig.rankOf(ranks, name), DEFAULT_ORDER,
        List.of(List.of("a", "b", "c"), List.of("d", "e")));

    assertThat(groups).containsExactly(List.of("c", "a", "b"), List.of("d", "e"));
  }

  @Test
  void configuredOrderNeverMovesAnAdaptationAcrossGroups() {
    Map<String, Integer> ranks = GuiConfig.ranks(List.of("d", "a"), null, "test");
    List<List<String>> groups = GuiGroupLayout.arrange(
        List.of("a", "b", "c", "d"), name -> name, name -> GuiConfig.rankOf(ranks, name), DEFAULT_ORDER,
        List.of(List.of("a", "b"), List.of("c", "d")));

    assertThat(groups).containsExactly(List.of("a", "b"), List.of("d", "c"));
  }

  @Test
  void ungroupedAdaptationsFormAFinalGroupSortedTheCurrentWay() {
    Map<String, Integer> ranks = GuiConfig.ranks(List.of("z"), null, "test");
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

  private static List<String> names(String prefix, int count) {
    List<String> names = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      names.add(prefix + i);
    }
    return names;
  }

  private static List<String> concat(List<String> first, List<String> second, List<String> third) {
    List<String> all = new ArrayList<>(first);
    all.addAll(second);
    all.addAll(third);
    return all;
  }
}
