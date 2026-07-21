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

package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.ViewerDisplayDirector;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.UUID;

public class DiscoveryKeenEye extends SimpleAdaptation<DiscoveryKeenEye.Config> {
  private static final int HARD_MAX_HIGHLIGHTS = 8;
  private static final Color CHEST_COLOR = Color.fromRGB(255, 205, 70);
  private static final Color SPAWNER_COLOR = Color.fromRGB(120, 220, 255);

  private final Cooldowns scanThrottle = cooldowns();

  public DiscoveryKeenEye() {
    super("discovery-keen-eye");
    registerConfiguration(Config.class);
    setIcon(Material.ENDER_EYE);
    setInterval(1000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_EYE)
        .key("challenge_discovery_keeneye_250")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.CHEST)
            .key("challenge_discovery_keeneye_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_discovery_keeneye_250", "discovery.keen-eye.glimmers", 250, 300);
    registerMilestone("challenge_discovery_keeneye_2500", "discovery.keen-eye.glimmers", 2500, 1200);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(range(getLevelPercent(level), getConfig().rangeBase, getConfig().rangeFactor), 0), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(glimmerDurationTicks(getLevelPercent(level), getConfig().glimmerDurationTicksBase, getConfig().glimmerDurationTicksFactor) * 50D, 1), 2);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    for (AdaptPlayer adaptPlayer : learnedCandidates(now)) {
      Player player = adaptPlayer.getPlayer();
      if (player == null || !player.isOnline()) {
        continue;
      }

      UUID id = player.getUniqueId();
      if (!scanThrottle.isReady(id, getConfig().scanIntervalMillis)) {
        continue;
      }

      scanThrottle.mark(id);
      J.runEntity(player, () -> scanOwned(player));
    }
  }

  private void scanOwned(Player player) {
    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }

    double range = range(getLevelPercent(level), getConfig().rangeBase, getConfig().rangeFactor);
    int glimmerTicks = glimmerDurationTicks(getLevelPercent(level), getConfig().glimmerDurationTicksBase, getConfig().glimmerDurationTicksFactor);
    Location eye = player.getEyeLocation();
    Vector look = eye.getDirection();
    World world = player.getWorld();
    double rangeSq = range * range;
    double cosThreshold = getConfig().viewConeCos;
    int limit = Math.max(1, Math.min(getConfig().maxHighlightsPerScan, HARD_MAX_HIGHLIGHTS));
    int chunkRadius = Math.max(1, (int) Math.ceil(range / 16D));
    int baseCx = eye.getBlockX() >> 4;
    int baseCz = eye.getBlockZ() >> 4;

    int found = 0;
    for (int cx = -chunkRadius; cx <= chunkRadius && found < limit; cx++) {
      for (int cz = -chunkRadius; cz <= chunkRadius && found < limit; cz++) {
        int chunkX = baseCx + cx;
        int chunkZ = baseCz + cz;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
          continue;
        }

        Collection<BlockState> tiles;
        try {
          tiles = world.getChunkAt(chunkX, chunkZ).getTileEntities(block -> isKeenTarget(block.getType()), false);
        } catch (Throwable t) {
          continue;
        }

        for (BlockState state : tiles) {
          if (found >= limit) {
            break;
          }

          double dx = (state.getX() + 0.5D) - eye.getX();
          double dy = (state.getY() + 0.5D) - eye.getY();
          double dz = (state.getZ() + 0.5D) - eye.getZ();
          double distSq = (dx * dx) + (dy * dy) + (dz * dz);
          if (distSq > rangeSq || distSq < 0.5D) {
            continue;
          }

          double dist = Math.sqrt(distSq);
          double dot = ((dx * look.getX()) + (dy * look.getY()) + (dz * look.getZ())) / dist;
          if (dot < cosThreshold) {
            continue;
          }

          if (!hasLineOfSight(world, eye, state, dx, dy, dz, dist)) {
            continue;
          }

          glimmer(player, state, glimmerTicks);
          found++;
        }
      }
    }

    if (found > 0) {
      getPlayer(player).getData().addStat("discovery.keen-eye.glimmers", found);
    }
  }

  private boolean hasLineOfSight(World world, Location eye, BlockState state, double dx, double dy, double dz, double dist) {
    Vector direction = new Vector(dx, dy, dz).normalize();
    RayTraceResult result = world.rayTraceBlocks(eye, direction, dist + 1.0D, FluidCollisionMode.NEVER, true);
    if (result == null || result.getHitBlock() == null) {
      return false;
    }

    return result.getHitBlock().getX() == state.getX()
        && result.getHitBlock().getY() == state.getY()
        && result.getHitBlock().getZ() == state.getZ();
  }

  private void glimmer(Player player, BlockState state, int ticks) {
    Location at = new Location(state.getWorld(), state.getX(), state.getY(), state.getZ());
    Color color = isSpawner(state.getType()) ? SPAWNER_COLOR : CHEST_COLOR;
    ViewerDisplayDirector.showBlock(
        getName(),
        "target-" + state.getX() + ":" + state.getY() + ":" + state.getZ(),
        player,
        at,
        state.getBlockData(),
        color,
        Math.max(6, ticks)
    );
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    UUID playerId = event.getPlayer().getUniqueId();
    scanThrottle.clear(playerId);
    ViewerDisplayDirector.clearViewer(getName(), playerId);
  }

  @Override
  public void unregister() {
    ViewerDisplayDirector.clearChannel(getName());
    super.unregister();
  }

  private boolean isKeenTarget(Material type) {
    return type == Material.CHEST
        || type == Material.TRAPPED_CHEST
        || type == Material.SPAWNER
        || type == Material.TRIAL_SPAWNER;
  }

  private boolean isSpawner(Material type) {
    return type == Material.SPAWNER || type == Material.TRIAL_SPAWNER;
  }

  static double range(double levelPercent, double base, double factor) {
    return Math.max(4D, base + (levelPercent * factor));
  }

  static int glimmerDurationTicks(double levelPercent, double base, double factor) {
    return Math.max(6, (int) Math.round(base + (levelPercent * factor)));
  }


  @ConfigDescription("Chests and spawners in your line of sight briefly appear as private glowing block displays.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Range Base for the Discovery Keen Eye adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rangeBase = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Range Factor for the Discovery Keen Eye adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rangeFactor = 14;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Glimmer Duration Ticks Base for the Discovery Keen Eye adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double glimmerDurationTicksBase = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Glimmer Duration Ticks Factor for the Discovery Keen Eye adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double glimmerDurationTicksFactor = 28;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum forward-view alignment (cosine) required for a container to glimmer.", impact = "Higher values narrow the detection cone to what you look almost directly at.")
    double viewConeCos = 0.55;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum containers highlighted per scan, capped internally at 8.", impact = "Lower values reduce particle work in container-dense areas.")
    int maxHighlightsPerScan = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds between line-of-sight scans for a single player.", impact = "Higher values scan less often and reduce ray tracing work.")
    long scanIntervalMillis = 1500;

    public Config() {
      baseCost = 2;
      costFactor = 0.3;
      maxLevel = 5;
      initialCost = 3;
    }
  }
}
