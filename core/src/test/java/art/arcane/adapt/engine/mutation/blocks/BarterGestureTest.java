package art.arcane.adapt.engine.mutation.blocks;

import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.BarterGesture;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.engine.mutation.engine.MutationEngine;
import art.arcane.adapt.engine.mutation.kit.Kits;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BarterGestureTest {
  @TempDir
  static Path data;

  private static TestAdapt adapt;
  private static SkillRegistry registry;

  @BeforeAll
  static void start() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build(), new BuiltinAddon());
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterAll
  static void stop() {
    adapt.close();
  }

  @Test
  void everyOrderedPairGetsAGestureNeitherParentReserves() {
    List<String> collisions = new ArrayList<>();
    int pairs = 0;
    for (Skill<?> lead : registry.getSkills()) {
      for (Skill<?> partner : registry.getSkills()) {
        if (lead == partner) {
          continue;
        }
        pairs++;
        BarterGesture gesture = gesture(lead, partner);
        if (Kits.of(lead).reservedGestures().contains(gesture) || Kits.of(partner).reservedGestures().contains(gesture)) {
          collisions.add(lead.getId() + " + " + partner.getId() + " -> " + gesture);
        }
        assertThat(gesture(partner, lead)).isEqualTo(gesture);
      }
    }
    assertThat(pairs).isEqualTo(24 * 23);
    assertThat(collisions).isEmpty();
  }

  @Test
  void reservationsBelongToRegisteredSkillKits() {
    List<Id> reserving = new ArrayList<>();
    for (Skill<?> skill : registry.getSkills()) {
      if (!skill.getMutationKit().orElseThrow().reservedGestures().isEmpty()) {
        reserving.add(skill.getId());
      }
    }
    assertThat(reserving).hasSize(12);
  }

  @Test
  void aPairTakesTheFirstGestureLeftFree() {
    assertThat(gesture("swords", "herbalism")).isEqualTo(BarterGesture.SNEAK_SWAP);
    assertThat(gesture("chronos", "swords")).isEqualTo(BarterGesture.DOUBLE_SNEAK);
    assertThat(gesture("stealth", "chronos")).isEqualTo(BarterGesture.SNEAK_SCROLL);
    assertThat(gesture("stealth", "seaborne")).isEqualTo(BarterGesture.SNEAK_SCROLL);
    assertThat(gesture("kinetics", "golemancy")).isEqualTo(BarterGesture.SNEAK_LEFT_CLICK);
    assertThat(Kits.of(registry.getSkill(Id.adapt("swords"))).reservedGestures()).isEqualTo(Set.of());
  }

  @Test
  void theBarterDescriptionStartsWithThePairsGesture() {
    for (Skill<?> lead : registry.getSkills()) {
      for (Skill<?> partner : registry.getSkills()) {
        if (lead != partner) {
          MutatedText.Parents parents = new MutatedText.Parents(lead, partner, registry.getPairRegistry());
          String phrase = MutatedText.message(MutatedText.ENGLISH,
              "adapt.mutated.gesture." + gesture(lead, partner).name().toLowerCase(Locale.ROOT));
          assertThat(MutatedText.description(MutatedText.ENGLISH, parents, Recipe.BARTER))
              .as("%s + %s", lead.getId(), partner.getId()).startsWith(phrase + " to spend ");
        }
      }
    }
  }

  private static BarterGesture gesture(String lead, String partner) {
    return MutationEngine.gesture(new SkillPair(Id.adapt(lead), Id.adapt(partner))).orElseThrow();
  }

  private static BarterGesture gesture(Skill<?> lead, Skill<?> partner) {
    return MutationEngine.gesture(new SkillPair(lead.getId(), partner.getId())).orElseThrow();
  }
}
