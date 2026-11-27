package art.arcane.adapt.engine.addon;

import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.platform.value.Id;

import java.util.Objects;
import java.util.function.Supplier;

/** A skill an addon registered: its id, the addon's namespace and how to build it. */
public record SkillSource(Id id, String owner, Supplier<? extends Skill<?>> factory) {
  public SkillSource {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(factory, "factory");
  }

  /** A fresh instance, built as content of the owning addon. */
  public Skill<?> build() {
    return BuildScope.run(owner, "skill " + id, factory);
  }
}
