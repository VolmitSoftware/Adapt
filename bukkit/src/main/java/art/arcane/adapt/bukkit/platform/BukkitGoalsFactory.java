package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.platform.entity.ai.MobGoalsRef;
import org.bukkit.entity.Mob;

public interface BukkitGoalsFactory {
  MobGoalsRef goals(Mob mob);
}
