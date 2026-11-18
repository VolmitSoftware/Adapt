package art.arcane.adapt.api.skill;

import java.util.List;

/** Platform-only collaborator of the skill registry: the placeholder catalog (PlaceholderAPI on Bukkit). The neutral fallback does nothing. */
public interface SkillRegistryPort {
  SkillRegistryPort NONE = new SkillRegistryPort() {
    @Override
    public void publishCatalog(long revision, List<Skill<?>> skills) {
    }
  };

  /** Publishes the skill and adaptation catalog for placeholder lookups. */
  void publishCatalog(long revision, List<Skill<?>> skills);
}
