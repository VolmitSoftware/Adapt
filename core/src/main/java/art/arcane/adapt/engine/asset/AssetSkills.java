package art.arcane.adapt.engine.asset;

import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.mutation.kit.Kits;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Where the asset catalog meets the skill registry: a skill's id and the look a generated theme is drawn from. */
public final class AssetSkills {
  private AssetSkills() {
  }

  /** The id a skill's art is filed under. */
  public static Id id(Skill<?> skill) {
    return skill.getId();
  }

  /** The skills of whatever registry {@code registry} supplies when asked; none while it supplies null. */
  public static SkillLooks of(Supplier<SkillRegistry> registry) {
    return new SkillLooks() {
      @Override
      public List<Id> ids() {
        SkillRegistry current = registry.get();
        return current == null ? List.of() : AssetSkills.ids(current);
      }

      @Override
      public Optional<SkillLook> look(Id skill) {
        return AssetSkills.look(registry.get(), skill);
      }
    };
  }

  /** The ids of every registered skill, in registry order. */
  public static List<Id> ids(SkillRegistry registry) {
    List<Id> ids = new ArrayList<>();
    for (Skill<?> skill : registry.getSkills()) {
      ids.add(id(skill));
    }
    return List.copyOf(ids);
  }

  /** The look of the registered skill {@code id}; empty when no registered skill has that id. */
  public static Optional<SkillLook> look(SkillRegistry registry, Id id) {
    if (registry == null || id == null) {
      return Optional.empty();
    }
    for (Skill<?> skill : registry.getSkills()) {
      if (id(skill).equals(id)) {
        return Optional.of(new SkillLook(id, Kits.of(skill).tint().orElse(skill.getColor()).packed() & 0xFFFFFF,
            skill.getIcon().item()));
      }
    }
    return Optional.empty();
  }
}
