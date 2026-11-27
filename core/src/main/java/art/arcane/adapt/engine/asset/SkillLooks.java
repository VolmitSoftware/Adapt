package art.arcane.adapt.engine.asset;

import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Optional;

/** The registered skills as the asset catalog sees them: their ids in registry order and each one's look. */
public interface SkillLooks {
  List<Id> ids();

  /** The look of the registered skill {@code skill}; empty when no registered skill has that id. */
  Optional<SkillLook> look(Id skill);
}
