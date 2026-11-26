package art.arcane.adapt.api.mutation.blocks;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BarterGestureTest {
  @Test
  void everyOrderedPairGetsAGestureNeitherParentReserves() {
    List<String> collisions = new ArrayList<>();
    int pairs = 0;
    for (SkillBlocks lead : SkillBlockCatalog.all()) {
      for (SkillBlocks partner : SkillBlockCatalog.all()) {
        if (lead == partner) {
          continue;
        }
        pairs++;
        BarterGesture gesture = BarterGesture.of(lead.skillId(), partner.skillId());
        if (BarterGesture.reserved(lead.skillId()).contains(gesture)
            || BarterGesture.reserved(partner.skillId()).contains(gesture)) {
          collisions.add(lead.skillId() + " + " + partner.skillId() + " -> " + gesture);
        }
        assertThat(BarterGesture.of(partner.skillId(), lead.skillId())).isEqualTo(gesture);
      }
    }
    assertThat(pairs).isEqualTo(24 * 23);
    assertThat(collisions).isEmpty();
  }

  @Test
  void reservationsNameCatalogSkills() {
    for (String skill : BarterGesture.reservingSkills()) {
      assertThat(SkillBlockCatalog.byId(skill)).as(skill).isPresent();
    }
  }

  @Test
  void aPairTakesTheFirstGestureLeftFree() {
    assertThat(BarterGesture.of("swords", "herbalism")).isEqualTo(BarterGesture.SNEAK_SWAP);
    assertThat(BarterGesture.of("chronos", "swords")).isEqualTo(BarterGesture.DOUBLE_SNEAK);
    assertThat(BarterGesture.of("stealth", "chronos")).isEqualTo(BarterGesture.SNEAK_SCROLL);
    assertThat(BarterGesture.of("stealth", "seaborne")).isEqualTo(BarterGesture.SNEAK_SCROLL);
    assertThat(BarterGesture.of("kinetics", "golemancy")).isEqualTo(BarterGesture.SNEAK_LEFT_CLICK);
    assertThat(BarterGesture.reserved("swords")).isEqualTo(Set.of());
  }

  @Test
  void theBarterDescriptionStartsWithThePairsGesture() {
    for (SkillBlocks lead : SkillBlockCatalog.all()) {
      for (SkillBlocks partner : SkillBlockCatalog.all()) {
        if (lead != partner) {
          MutatedAdaptation barter = MutationRecipes.compose(lead, partner).get(Recipe.BARTER.ordinal());
          assertThat(barter.description()).as("%s + %s", lead.skillId(), partner.skillId())
              .startsWith(BarterGesture.of(lead.skillId(), partner.skillId()).phrase() + " to spend ");
        }
      }
    }
  }
}
