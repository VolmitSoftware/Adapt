package art.arcane.adapt.engine.addon;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.value.Id;

import java.util.Objects;
import java.util.function.Supplier;

/** An adaptation an addon adds to a skill of any addon. */
public record ExtensionSource(Id skill, Id id, String owner, Supplier<? extends Adaptation<?>> factory) {
  public ExtensionSource {
    Objects.requireNonNull(skill, "skill");
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(factory, "factory");
  }

  /** A fresh instance, built as content of the owning addon. */
  public Adaptation<?> build() {
    return BuildScope.run(owner, "adaptation " + id, factory);
  }
}
