package art.arcane.adapt.engine.skill;

import art.arcane.adapt.api.content.Skill;
import java.util.List;

/**
 * Platform-only collaborator of the skill registry, told of the catalog after bootstrap and on every later change: the
 * placeholder catalog (PlaceholderAPI on Bukkit) and the use permissions of whatever is registered. The neutral fallback
 * does nothing.
 */
public interface SkillRegistryPort {
  SkillRegistryPort NONE = new SkillRegistryPort() {
    @Override
    public void publishCatalog(long revision, List<Skill<?>> skills) {
    }
  };

  /** Publishes the skill and adaptation catalog for placeholder lookups and use permissions. */
  void publishCatalog(long revision, List<Skill<?>> skills);
}
