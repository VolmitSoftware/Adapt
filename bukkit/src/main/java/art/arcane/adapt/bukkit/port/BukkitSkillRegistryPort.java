package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.AdaptPermissionRegistrar;
import art.arcane.adapt.engine.skill.SkillRegistryPort;
import art.arcane.adapt.papi.AdaptPlaceholders;
import org.bukkit.Bukkit;

import java.util.List;

public final class BukkitSkillRegistryPort implements SkillRegistryPort {
  @Override
  public void publishCatalog(long revision, List<Skill<?>> skills) {
    AdaptPlaceholders.get().publishCatalog(revision, skills);
    AdaptPermissionRegistrar.registerAll(Bukkit.getPluginManager(), skills);
  }
}
