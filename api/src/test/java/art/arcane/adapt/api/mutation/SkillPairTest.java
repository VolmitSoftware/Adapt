package art.arcane.adapt.api.mutation;

import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillPairTest {
  @Test
  void aKeyRoundTripsAcrossNamespaces() {
    SkillPair pair = new SkillPair(Id.of("alchemist", "alchemy"), Id.of("adapt", "brewing"));

    assertThat(SkillPair.parse(pair.key())).contains(pair);
    assertThat(pair.id()).isEqualTo(Id.of("alchemist", "mutated/alchemy/adapt/brewing"));
  }

  @Test
  void parseIsEmptyForIdsWithSeveralPathSegments() {
    assertThat(SkillPair.parse("adapt:sword/poisoned_blade+adapt:stealth")).isEmpty();
    assertThat(SkillPair.parse("adapt:swords+adapt:stealth/ghost")).isEmpty();
    assertThat(SkillPair.parse("adapt:mutated/swords/adapt/stealth+adapt:agility")).isEmpty();
  }

  @Test
  void parseIsEmptyWhereTheConstructorWouldThrow() {
    String lead = "a".repeat(80);
    String partner = "b".repeat(80);

    assertThat(SkillPair.parse("x:" + lead + "+y:" + partner)).isEmpty();
    assertThatThrownBy(() -> new SkillPair(Id.of("x", lead), Id.of("y", partner)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("x:" + lead + "+y:" + partner);
    assertThat(SkillPair.parse("adapt:swords+adapt:swords")).isEmpty();
    assertThat(SkillPair.parse("adapt:swords")).isEmpty();
  }
}
