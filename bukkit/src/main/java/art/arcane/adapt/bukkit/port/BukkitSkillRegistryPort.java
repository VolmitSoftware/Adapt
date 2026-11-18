package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistryPort;
import art.arcane.adapt.papi.AdaptPlaceholders;

import java.util.List;

public final class BukkitSkillRegistryPort implements SkillRegistryPort {
  @Override
  public void publishCatalog(long revision, List<Skill<?>> skills) {
    AdaptPlaceholders.get().publishCatalog(revision, skills);
  }
}
