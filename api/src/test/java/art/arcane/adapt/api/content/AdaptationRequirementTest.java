package art.arcane.adapt.api.content;

import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptationRequirementTest {
  @Test
  void parsesTheLevelAfterTheLastAt() {
    assertEquals(Optional.of(new AdaptationRequirement(Id.adapt("herbalism/luck"), 2)),
        AdaptationRequirement.parse("adapt:herbalism/luck@2", "alchemist"));
  }

  @Test
  void aBareIdIsInTheOwnersNamespace() {
    assertEquals(Optional.of(new AdaptationRequirement(Id.of("alchemist", "alchemy/distill"), 3)),
        AdaptationRequirement.parse(" alchemy/distill@3 ", "alchemist"));
    assertEquals(Optional.of(new AdaptationRequirement(Id.adapt("herbalism/luck"), 1)),
        AdaptationRequirement.parse("herbalism/luck@1", "adapt"));
  }

  @Test
  void rejectsColonLevelsAndLevelsBelowOne() {
    assertTrue(AdaptationRequirement.parse("adapt:herbalism/luck:2", "adapt").isEmpty());
    assertTrue(AdaptationRequirement.parse("adapt:herbalism/luck@0", "adapt").isEmpty());
    assertTrue(AdaptationRequirement.parse("adapt:herbalism/luck@", "adapt").isEmpty());
    assertTrue(AdaptationRequirement.parse("@2", "adapt").isEmpty());
  }

  @Test
  void writesTheParsedForm() {
    AdaptationRequirement requirement = new AdaptationRequirement(Id.of("alchemist", "alchemy/distill"), 2);
    assertEquals("alchemist:alchemy/distill@2", requirement.entry());
    assertEquals(Optional.of(requirement), AdaptationRequirement.parse(requirement.entry(), "adapt"));
  }
}
