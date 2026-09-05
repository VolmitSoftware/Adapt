package art.arcane.gloss.api;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.UUID;

public interface GlossAPI {
  boolean updateEntityInsight(Plugin owner, Player viewer, LivingEntity target, List<String> details, long durationMillis);

  void clearEntityInsight(Plugin owner, UUID viewerId);

  void restrictEntityOverlays(Plugin owner, boolean restricted);
}
