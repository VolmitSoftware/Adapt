package art.arcane.adapt.engine.mutation;

import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillMutationKeyTest {
  private static final SkillPair SWORDS_STEALTH = new SkillPair(Id.adapt("swords"), Id.adapt("stealth"));

  @Test
  void keyJoinsBothIdsWithTheSeparator() {
    assertThat(SWORDS_STEALTH.key()).isEqualTo("adapt:swords+adapt:stealth");
    assertThat(PairKeys.parse(SWORDS_STEALTH.key())).isEqualTo(SWORDS_STEALTH);
    assertThat(SkillPair.parse(SWORDS_STEALTH.key())).contains(SWORDS_STEALTH);
  }

  @Test
  void parseNeedsNoWordTableAndAcceptsAnyNamespace() {
    SkillPair addon = PairKeys.parse("alchemist:alchemy+adapt:brewing");
    assertThat(addon).isEqualTo(new SkillPair(Id.of("alchemist", "alchemy"), Id.adapt("brewing")));
    assertThat(PairKeys.parse("swords+stealth")).isEqualTo(SWORDS_STEALTH);
  }

  @Test
  void parseRejectsAnythingButTwoDifferentIds() {
    assertThat(PairKeys.parse("adapt:swords")).isNull();
    assertThat(PairKeys.parse("swords+swords")).isNull();
    assertThat(PairKeys.parse("a+b+c")).isNull();
    assertThat(PairKeys.parse("+stealth")).isNull();
    assertThat(PairKeys.parse("Swords+stealth")).isNull();
    assertThat(PairKeys.parse(null)).isNull();
  }

  @Test
  void contentOfThePairSitsBelowItsId() {
    assertThat(SWORDS_STEALTH.id()).isEqualTo(Id.adapt("mutated/swords/adapt/stealth"));
    assertThat(SWORDS_STEALTH.child("graft")).isEqualTo(Id.adapt("mutated/swords/adapt/stealth/graft"));
    assertThat(SWORDS_STEALTH.involves(Id.adapt("stealth"))).isTrue();
    assertThat(SWORDS_STEALTH.involves(Id.adapt("axes"))).isFalse();
  }
}
