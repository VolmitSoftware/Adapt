package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.insight.GlossInsight;
import art.arcane.adapt.api.insight.GlossInsightPort;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.List;
import java.util.UUID;

final class BukkitGlossInsightPort implements GlossInsightPort {
  private final GlossInsight gloss;

  BukkitGlossInsightPort(Adapt plugin) {
    gloss = new GlossInsight(plugin);
  }

  @Override
  public boolean available() {
    return gloss.available();
  }

  @Override
  public boolean overlayActive(LivingRef probe) {
    return probe != null && gloss.overlayActive(BukkitRefs.living(probe));
  }

  @Override
  public boolean update(PlayerRef viewer, LivingRef target, List<String> details, long durationMillis) {
    return gloss.update(BukkitRefs.player(viewer), BukkitRefs.living(target), details, durationMillis);
  }

  @Override
  public void clear(UUID viewerId) {
    gloss.clear(viewerId);
  }

  @Override
  public void setRestricted(boolean restricted) {
    gloss.setRestricted(restricted);
  }

  @Override
  public void refresh() {
    gloss.refresh();
  }
}
