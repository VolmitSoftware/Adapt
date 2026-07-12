/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockReceiveGameEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class StealthTrapSense extends SimpleAdaptation<StealthTrapSense.Config> {
  private static final int MAX_SESSION_VISITS_PER_TICK = 32;
  private static final int MAX_BLOCKS_PER_SCAN = 4096;
  private static final int MAX_GLIMMERS_PER_SCAN = 24;

  private final Map<UUID, TrapSession> sessions;

  public StealthTrapSense() {
    super("stealth-trap-sense");
    registerConfiguration(Config.class);
    setIcon(Material.TRIPWIRE_HOOK);
    setInterval(400);
    sessions = playerState();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SCULK_SENSOR)
        .key("challenge_stealth_trap_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.SCULK_SHRIEKER)
            .key("challenge_stealth_trap_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_stealth_trap_500", "stealth.trap-sense.traps-revealed", 500, 400);
    registerMilestone("challenge_stealth_trap_5k", "stealth.trap-sense.traps-revealed", 5000, 1500);
  }

  static boolean isTrapBlock(Material type) {
    return switch (type) {
      case TRIPWIRE, TRIPWIRE_HOOK, SCULK_SENSOR, CALIBRATED_SCULK_SENSOR, SCULK_SHRIEKER -> true;
      default -> type.name().endsWith("_PRESSURE_PLATE");
    };
  }

  static boolean isSculkTrap(Material type) {
    return type == Material.SCULK_SENSOR || type == Material.CALIBRATED_SCULK_SENSOR || type == Material.SCULK_SHRIEKER;
  }

  static double computeRange(double base, double factor, double percent) {
    return Math.max(3D, Math.min(8D, base + (percent * factor)));
  }

  static double computeMercy(double maxChance, double percent) {
    return Math.max(0D, Math.min(1D, maxChance * percent));
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRange(level), 1), 1);
    statLore(v, C.YELLOW, "* ", Form.pc(getMercy(level), 0), 2);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    UUID id = p.getUniqueId();
    if (!e.isSneaking()) {
      sessions.remove(id);
      return;
    }

    if (getActiveLevel(p) <= 0) {
      return;
    }

    sessions.putIfAbsent(id, new TrapSession(p, id));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    sessions.remove(e.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockReceiveGameEvent e) {
    Material type = e.getBlock().getType();
    if (!isSculkTrap(type)) {
      return;
    }

    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    int level = getActiveLevel(p, Player::isSneaking);
    if (level <= 0) {
      return;
    }

    double mercy = getMercy(level);
    if (mercy <= 0) {
      return;
    }

    if (ThreadLocalRandom.current().nextDouble() < mercy) {
      e.setCancelled(true);
      Location center = e.getBlock().getLocation().add(0.5D, 0.8D, 0.5D);
      fx(center, FxPriority.AMBIENT)
          .particle(Particle.SCULK_SOUL, 2, 0, 0, 0, 0.06D, 0.01D)
          .sound(Sound.BLOCK_SCULK_SENSOR_CLICKING_STOP, 0.25F, 1.4F);
    }
  }

  @Override
  public void onTick() {
    if (sessions.isEmpty()) {
      return;
    }

    long now = System.currentTimeMillis();
    int visited = 0;
    for (TrapSession session : sessions.values()) {
      if (visited++ >= MAX_SESSION_VISITS_PER_TICK) {
        break;
      }
      if (now < session.nextScanAt || !session.processing.compareAndSet(false, true)) {
        continue;
      }
      if (!J.runEntity(session.player, () -> {
        try {
          scan(session);
        } finally {
          session.processing.set(false);
        }
      })) {
        session.processing.set(false);
        sessions.remove(session.id, session);
      }
    }
  }

  private void scan(TrapSession session) {
    Player p = session.player;
    UUID id = session.id;
    session.nextScanAt = System.currentTimeMillis() + Math.max(200L, getConfig().scanIntervalMillis);
    if (sessions.get(id) != session || !p.isOnline() || !p.isSneaking() || getActiveLevel(p) <= 0) {
      sessions.remove(id, session);
      return;
    }

    int level = getActiveLevel(p);
    int r = (int) Math.ceil(getRange(level));
    Location base = p.getLocation();
    World world = base.getWorld();
    if (world == null) {
      return;
    }

    int bx = base.getBlockX();
    int by = base.getBlockY();
    int bz = base.getBlockZ();
    int minY = Math.max(world.getMinHeight(), by - r);
    int maxY = Math.min(world.getMaxHeight() - 1, by + r);

    int scanned = 0;
    int glimmers = 0;
    int revealed = 0;
    for (int dx = -r; dx <= r; dx++) {
      for (int dz = -r; dz <= r; dz++) {
        for (int y = minY; y <= maxY; y++) {
          if (++scanned > MAX_BLOCKS_PER_SCAN) {
            finishScan(p, revealed);
            return;
          }
          Block block = world.getBlockAt(bx + dx, y, bz + dz);
          Material type = block.getType();
          if (!isTrapBlock(type)) {
            continue;
          }
          revealed++;
          if (glimmers < MAX_GLIMMERS_PER_SCAN) {
            glimmer(block.getLocation().add(0.5D, 0.25D, 0.5D), type);
            glimmers++;
          }
        }
      }
    }

    finishScan(p, revealed);
  }

  private void finishScan(Player p, int revealed) {
    if (revealed > 0) {
      addStat(p, "stealth.trap-sense.traps-revealed", Math.min(revealed, MAX_GLIMMERS_PER_SCAN));
    }
  }

  private void glimmer(Location center, Material type) {
    Particle particle = isSculkTrap(type) ? Particle.SCULK_SOUL : Particle.ELECTRIC_SPARK;
    fx(center, FxPriority.AMBIENT).particle(particle, 1, 0, 0, 0, 0.03D, 0.0D);
  }

  @Override
  public void unregister() {
    sessions.clear();
    super.unregister();
  }

  private double getRange(int level) {
    return computeRange(getConfig().rangeBase, getConfig().rangeFactor, getLevelPercent(level));
  }

  private double getMercy(int level) {
    return computeMercy(getConfig().mercyMaxChance, getLevelPercent(level));
  }

  private static final class TrapSession {
    private final Player player;
    private final UUID id;
    private final AtomicBoolean processing = new AtomicBoolean();
    private volatile long nextScanAt;

    private TrapSession(Player player, UUID id) {
      this.player = player;
      this.id = id;
    }
  }

  @ConfigDescription("While sneaking, nearby tripwires, pressure plates, and sculk sensors glimmer; at higher levels your triggers raise shrieker warning levels more slowly.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base detection range for revealing traps, in blocks.", impact = "Higher values reveal traps from farther away.")
    double rangeBase = 4.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra detection range gained across levels, in blocks.", impact = "Higher values extend detection range more per level; capped at 8 blocks.")
    double rangeFactor = 4.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum chance to suppress a sculk warning trigger at full level.", impact = "Higher values let higher levels avoid raising shrieker warning levels more often.")
    double mercyMaxChance = 0.7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds between trap scans while sneaking.", impact = "Lower values refresh glimmers faster; higher values reduce block scan cost.")
    long scanIntervalMillis = 500;

    public Config() {
      baseCost = 3;
      costFactor = 0.35;
      maxLevel = 4;
      initialCost = 3;
    }
  }
}
