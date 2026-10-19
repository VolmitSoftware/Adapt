package art.arcane.adapt.api.recipe;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

import static org.assertj.core.api.Assertions.assertThat;

class MultisetMatchTest {
  private static final BiPredicate<String, String> EQUALS = Objects::equals;

  @Test
  void orderNeverMatters() {
    List<String> specs = List.of("SUGAR", "SUGAR", "FEATHER", "FEATHER");

    assertThat(MultisetMatch.matches(specs, List.of("FEATHER", "SUGAR", "FEATHER", "SUGAR"), EQUALS)).isTrue();
    assertThat(MultisetMatch.matches(specs, List.of("SUGAR", "FEATHER", "SUGAR", "FEATHER"), EQUALS)).isTrue();
  }

  @Test
  void countsMustMatchExactly() {
    List<String> specs = List.of("SUGAR", "SUGAR", "FEATHER", "FEATHER");

    assertThat(MultisetMatch.matches(specs, List.of("SUGAR", "SUGAR", "SUGAR", "FEATHER"), EQUALS)).isFalse();
    assertThat(MultisetMatch.matches(specs, List.of("SUGAR", "SUGAR", "FEATHER"), EQUALS)).isFalse();
    assertThat(MultisetMatch.matches(specs, List.of("SUGAR", "SUGAR", "FEATHER", "FEATHER", "FEATHER"), EQUALS)).isFalse();
  }

  @Test
  void anEmptySlotNeverSatisfiesASpec() {
    List<String> specs = List.of("BREEZE_ROD", "BREEZE_ROD", "BREEZE_ROD", "BREEZE_ROD");

    assertThat(MultisetMatch.matches(specs, Arrays.asList("BREEZE_ROD", null, "BREEZE_ROD", "BREEZE_ROD"), EQUALS)).isFalse();
  }

  @Test
  void overlappingSpecsAreReassignedToFindAFullMatch() {
    BiPredicate<String, String> accepts = (spec, item) -> spec.equals("ANY") || spec.equals(item);
    List<String> specs = List.of("ANY", "IRON_INGOT");

    assertThat(MultisetMatch.matches(specs, List.of("IRON_INGOT", "OBSIDIAN"), accepts)).isTrue();
    assertThat(MultisetMatch.matches(specs, List.of("OBSIDIAN", "OBSIDIAN"), accepts)).isFalse();
  }

  @Test
  void twoEmptyListsMatch() {
    assertThat(MultisetMatch.matches(List.<String>of(), List.<String>of(), EQUALS)).isTrue();
  }
}
