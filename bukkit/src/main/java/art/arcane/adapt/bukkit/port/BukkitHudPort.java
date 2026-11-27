package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.engine.notification.HudPort;
import art.arcane.adapt.engine.notification.HudSegment;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.volmlib.util.hud.HudActionBar;
import art.arcane.volmlib.util.hud.HudSlot;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

final class BukkitHudPort implements HudPort {
  private final Plugin plugin;
  private HudActionBar bar;

  BukkitHudPort(Plugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public void publish(PlayerRef player, HudSegment segment) {
    Player bukkit = BukkitRefs.player(player);
    if (bukkit == null) {
      return;
    }
    List<HudSlot> slots = new ArrayList<>(segment.slots().size());
    for (art.arcane.adapt.engine.notification.HudSlot slot : segment.slots()) {
      slots.add(HudSlot.valueOf(slot.name()));
    }
    bar().publish(bukkit, new art.arcane.volmlib.util.hud.HudSegment(segment.purpose(), segment.priority(),
        segment.ttlMillis(), slots, segment.text()));
  }

  @Override
  public void clear(PlayerRef player, String purpose) {
    Player bukkit = BukkitRefs.player(player);
    HudActionBar active = current();
    if (bukkit != null && active != null) {
      active.clear(bukkit, purpose);
    }
  }

  @Override
  public void clearAll(PlayerRef player) {
    Player bukkit = BukkitRefs.player(player);
    HudActionBar active = current();
    if (bukkit != null && active != null) {
      active.clearAll(bukkit);
    }
  }

  @Override
  public synchronized void shutdown() {
    HudActionBar active = bar;
    bar = null;
    if (active != null) {
      active.shutdown();
    }
  }

  private synchronized HudActionBar current() {
    return bar;
  }

  private synchronized HudActionBar bar() {
    if (bar == null) {
      bar = new HudActionBar(plugin);
    }
    return bar;
  }
}
