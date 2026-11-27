package art.arcane.adapt.testkit;

import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.platform.value.Id;

import java.util.function.Supplier;

/**
 * Builds one adaptation and places it on a bare skill of the given id, so its skill lookups work without building
 * every sibling the real skill declares. A core must be running.
 */
public final class PlacedAdaptations {
  private PlacedAdaptations() {
  }

  public static <A extends SimpleAdaptation<?>> A on(Id skill, Supplier<A> factory) {
    BareSkill owner = TestContent.skill(() -> new BareSkill(skill));
    A adaptation = TestContent.adaptation(skill, factory);
    AdaptationHost.of(adaptation).placeOn(SkillHost.of(owner));
    SkillHost.of(owner).addAdaptation(adaptation);
    return adaptation;
  }

  public static final class BareSkill extends SimpleSkill<SkillConfig> {
    BareSkill(Id id) {
      super(id);
      registerConfiguration(SkillConfig.class);
    }
  }
}
