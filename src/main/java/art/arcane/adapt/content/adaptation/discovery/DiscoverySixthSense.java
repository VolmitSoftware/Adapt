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

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.DiscoveryMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.RunsWithoutLearnedAdaptation;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.ViewerDisplayDirector;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.generator.structure.StructureType;
import org.bukkit.util.StructureSearchResult;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class DiscoverySixthSense extends SimpleAdaptation<DiscoverySixthSense.Config> {
  static final double HARD_MAX_RANGE = 500.0D;
  private static final String HUD_GROUP = "discovery-sixth-sense";
  private static volatile List<StructureType> structureTypes;

  private static List<StructureType> structureTypes() {
    List<StructureType> local = structureTypes;
    if (local == null) {
      local = List.of(
          StructureType.BURIED_TREASURE,
          StructureType.DESERT_PYRAMID,
          StructureType.IGLOO,
          StructureType.JIGSAW,
          StructureType.JUNGLE_TEMPLE,
          StructureType.MINESHAFT,
          StructureType.OCEAN_MONUMENT,
          StructureType.OCEAN_RUIN,
          StructureType.RUINED_PORTAL,
          StructureType.SHIPWRECK,
          StructureType.STRONGHOLD,
          StructureType.SWAMP_HUT,
          StructureType.WOODLAND_MANSION,
          StructureType.FORTRESS,
          StructureType.END_CITY,
          StructureType.NETHER_FOSSIL);
      structureTypes = local;
    }
    return local;
  }

  private final Cooldowns pulseThrottle = cooldowns();
  private final Map<UUID, Integer> typeCursors = playerState();
  private final Map<UUID, StructureTarget> targets = playerState();
  private final Map<UUID, Float> expPins = playerState();

  public DiscoverySixthSense() {
    super("discovery-sixth-sense");
    registerConfiguration(Config.class);
    setIcon(Material.ECHO_SHARD);
    setInterval(2000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ECHO_SHARD)
        .key("challenge_discovery_sixthsense_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.SCULK_SENSOR)
            .key("challenge_discovery_sixthsense_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_discovery_sixthsense_100", "discovery.sixth-sense.senses", 100, 300);
    registerMilestone("challenge_discovery_sixthsense_1k", "discovery.sixth-sense.senses", 1000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(detectionRange(
        getLevelPercent(level),
        getConfig().detectionRangeBase,
        getConfig().detectionRangeFactor,
        getConfig().maxDetectionRange
    ), 0), 1);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public boolean hasTickDemand() {
    return requiresMaintenance(super.hasTickDemand(), !targets.isEmpty());
  }

  @Override
  public void onTick() {
    cleanupInactiveTargets();
    long now = System.currentTimeMillis();
    for (AdaptPlayer adaptPlayer : learnedCandidates(now)) {
      Player player = adaptPlayer.getPlayer();
      if (player == null || !player.isOnline()) {
        continue;
      }

      J.runEntity(player, () -> tickOwned(player));
    }
  }

  private void cleanupInactiveTargets() {
    for (UUID playerId : List.copyOf(targets.keySet())) {
      if (getServer().hasOnlineLearner(playerId, getName())) {
        continue;
      }

      Player player = Bukkit.getPlayer(playerId);
      if (player == null) {
        targets.remove(playerId);
        typeCursors.remove(playerId);
        pulseThrottle.clear(playerId);
        continue;
      }
      J.runEntity(player, () -> clearInactiveTargetOwned(player));
    }
  }

  private void clearInactiveTargetOwned(Player player) {
    UUID playerId = player.getUniqueId();
    if (!targets.containsKey(playerId) || getActiveLevel(player) > 0) {
      return;
    }
    typeCursors.remove(playerId);
    pulseThrottle.clear(playerId);
    clearTarget(player);
  }

  private void tickOwned(Player player) {
    int level = getActiveLevel(player);
    if (level <= 0) {
      clearTarget(player);
      return;
    }

    Location origin = player.getLocation();
    UUID playerId = player.getUniqueId();
    if (insideGeneratedStructure(origin)) {
      clearTarget(player);
      return;
    }

    double range = detectionRange(
        getLevelPercent(level),
        getConfig().detectionRangeBase,
        getConfig().detectionRangeFactor,
        getConfig().maxDetectionRange
    );
    showCurrentTarget(player, origin, range);

    long pulseInterval = pulseIntervalMillis(getConfig().pulseIntervalMillis);
    if (!pulseThrottle.isReady(playerId, pulseInterval)) {
      return;
    }
    pulseThrottle.mark(playerId);
    searchOwned(player, origin, range);
  }

  private void searchOwned(Player player, Location origin, double range) {
    UUID playerId = player.getUniqueId();
    int radiusChunks = Math.max(1, (int) Math.ceil(range / 16D));
    List<StructureType> types = structureTypes();
    int cursor = Math.floorMod(typeCursors.getOrDefault(playerId, 0), types.size());
    StructureType type = types.get(cursor);
    typeCursors.put(playerId, advanceTypeCursor(cursor, types.size()));
    World world = player.getWorld();

    StructureSearchResult result;
    try {
      result = world.locateNearestStructure(origin, type, radiusChunks, false);
    } catch (Throwable t) {
      return;
    }

    if (result == null || result.getLocation() == null || result.getLocation().getWorld() != world) {
      return;
    }

    Location target = result.getLocation();
    double dx = target.getX() - origin.getX();
    double dz = target.getZ() - origin.getZ();
    double horizontal = Math.sqrt((dx * dx) + (dz * dz));
    if (horizontal > range) {
      return;
    }

    StructureTarget candidate = new StructureTarget(
        world.getUID(),
        target.getX(),
        target.getZ(),
        structureName(result.getStructure(), type),
        structureSymbol(type),
        M.ms()
    );
    StructureTarget previous = targets.get(playerId);
    boolean replaced = shouldReplaceTarget(previous, candidate, origin);
    boolean refreshed = previous != null && previous.sameTarget(candidate);
    if (replaced || refreshed) {
      targets.put(playerId, candidate);
      showTarget(player, origin, candidate, range, replaced && !refreshed);
    }
  }

  private void showCurrentTarget(Player player, Location origin, double range) {
    StructureTarget target = targets.get(player.getUniqueId());
    if (target == null) {
      return;
    }

    long targetTtl = targetTtlMillis(getConfig().pulseIntervalMillis, structureTypes().size());
    if (!target.matches(origin.getWorld()) || M.ms() - target.foundAtMillis() > targetTtl) {
      clearTarget(player);
      return;
    }

    double distance = Math.sqrt(target.distanceSquared(origin));
    if (distance > range) {
      clearTarget(player);
      return;
    }

    showTarget(player, origin, target, range, false);
  }

  private void showTarget(Player player, Location origin, StructureTarget target, double range, boolean newlyLocated) {
    double dx = target.x() - origin.getX();
    double dz = target.z() - origin.getZ();
    double distance = Math.sqrt((dx * dx) + (dz * dz));
    if (distance <= 0.0D || distance > range) {
      return;
    }

    String message = AdaptLanguage.text(
        DiscoveryMessages.SIXTH_SENSE_HUD,
        trusted("symbol", C.AQUA + target.symbol()),
        trusted("structure", C.WHITE + target.name()),
        trusted("direction", C.YELLOW + compassDirection(dx, dz)),
        trusted("distance", C.GRAY + String.valueOf(Math.round(distance)))
    );
    AdaptHud.ambientStatus(player, HUD_GROUP, message);
    pinExpBar(player, distance, range);

    if (newlyLocated) {
      pulseTowards(player, dx / distance, dz / distance, distance);
      getPlayer(player).getData().addStat("discovery.sixth-sense.senses", 1);
    }
  }

  private boolean insideGeneratedStructure(Location origin) {
    for (GeneratedStructure structure : origin.getChunk().getStructures()) {
      if (structureTypes().contains(structure.getStructure().getStructureType())
          && structure.getBoundingBox().contains(origin.getX(), origin.getY(), origin.getZ())) {
        return true;
      }
    }
    return false;
  }

  private void clearTarget(Player player) {
    targets.remove(player.getUniqueId());
    AdaptHud.clearAmbientStatus(player, HUD_GROUP);
    restoreExpBar(player);
    ViewerDisplayDirector.clearViewer(getName(), player.getUniqueId());
  }

  private void pinExpBar(Player player, double distance, double range) {
    if (range <= 0.0D) {
      return;
    }
    float progress = (float) Math.min(1.0D, Math.max(0.0D, 1.0D - (distance / range)));
    float rounded = Math.round(progress * 100F) / 100F;
    Float last = expPins.put(player.getUniqueId(), rounded);
    if (last == null || last != rounded) {
      player.sendExperienceChange(rounded, player.getLevel());
    }
  }

  private void restoreExpBar(Player player) {
    if (expPins.remove(player.getUniqueId()) != null) {
      player.sendExperienceChange(player.getExp(), player.getLevel());
    }
  }

  private void pulseTowards(Player player, double ux, double uz, double horizontal) {
    if (!getConfig().showParticles) {
      return;
    }

    double length = Math.min(6D, horizontal);
    Vector direction = new Vector(ux, 0D, uz);
    Location start = player.getEyeLocation().add(direction.clone().multiply(0.65D));
    Location end = start.clone().add(direction.multiply(length));
    ViewerDisplayDirector.showLine(
        getName(),
        "structure-direction",
        player,
        start,
        end,
        Material.LIGHT_BLUE_STAINED_GLASS.createBlockData(),
        Color.fromRGB(100, 225, 255),
        0.07D,
        50
    );
    fx(player, FxPriority.AMBIENT).sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.22F, 1.9F);
    fx(player.getLocation(), FxPriority.AMBIENT)
        .ring(Particles.END_ROD, 1.1D, 8, 0.1D);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    UUID playerId = event.getPlayer().getUniqueId();
    pulseThrottle.clear(playerId);
    typeCursors.remove(playerId);
    clearTarget(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  @RunsWithoutLearnedAdaptation
  public void onMove(PlayerMoveEvent event) {
    Player player = event.getPlayer();
    UUID playerId = player.getUniqueId();
    if (!targets.containsKey(playerId) || getActiveLevel(player) > 0) {
      return;
    }

    pulseThrottle.clear(playerId);
    typeCursors.remove(playerId);
    clearTarget(player);
  }

  @Override
  public void unregister() {
    for (UUID playerId : List.copyOf(targets.keySet())) {
      Player player = Bukkit.getPlayer(playerId);
      if (player != null) {
        J.runEntity(player, () -> {
          AdaptHud.clearAmbientStatus(player, HUD_GROUP);
          restoreExpBar(player);
        });
      }
    }
    typeCursors.clear();
    targets.clear();
    ViewerDisplayDirector.clearChannel(getName());
    super.unregister();
  }

  static double detectionRange(double levelPercent, double base, double factor) {
    return detectionRange(levelPercent, base, factor, HARD_MAX_RANGE);
  }

  static double detectionRange(double levelPercent, double base, double factor, double configuredMax) {
    double maximum = Double.isFinite(configuredMax)
        ? Math.max(16.0D, Math.min(HARD_MAX_RANGE, configuredMax))
        : HARD_MAX_RANGE;
    double normalizedLevel = Double.isFinite(levelPercent)
        ? Math.max(0.0D, Math.min(1.0D, levelPercent))
        : 0.0D;
    double safeBase = Double.isFinite(base) ? base : 16.0D;
    double safeFactor = Double.isFinite(factor) ? factor : 0.0D;
    double scaled = safeBase + (normalizedLevel * safeFactor);
    if (!Double.isFinite(scaled)) {
      return 16.0D;
    }
    return Math.min(maximum, Math.max(16.0D, scaled));
  }

  static String compassDirection(double dx, double dz) {
    if (Math.abs(dx) < 1.0E-9D && Math.abs(dz) < 1.0E-9D) {
      return "•";
    }
    String[] directions = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    double normalized = Math.atan2(-dx, dz) / (Math.PI / 4.0D);
    return directions[Math.floorMod((int) Math.round(normalized), directions.length)];
  }

  static String structureName(Structure structure, StructureType fallbackType) {
    Registry<Structure> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);
    NamespacedKey registeredKey = structure == null ? null : registry.getKey(structure);
    String key = registeredKey == null ? fallbackType.getKey().getKey() : registeredKey.getKey();
    return Form.capitalizeWords(key.toLowerCase(Locale.ROOT).replace('_', ' '));
  }

  static String structureSymbol(StructureType type) {
    if (type == StructureType.JIGSAW) {
      return "⌂";
    }
    if (type == StructureType.OCEAN_MONUMENT || type == StructureType.OCEAN_RUIN || type == StructureType.SHIPWRECK) {
      return "⚓";
    }
    if (type == StructureType.FORTRESS || type == StructureType.STRONGHOLD || type == StructureType.WOODLAND_MANSION) {
      return "♜";
    }
    return "◇";
  }

  static boolean shouldReplaceTarget(StructureTarget current, StructureTarget candidate, Location origin) {
    if (candidate == null) {
      return false;
    }
    if (current == null || !current.matches(origin.getWorld())) {
      return true;
    }
    return candidate.distanceSquared(origin) < current.distanceSquared(origin);
  }

  static int advanceTypeCursor(int cursor, int typeCount) {
    if (typeCount <= 1) {
      return 0;
    }

    int normalized = Math.floorMod(cursor, typeCount);
    return normalized == typeCount - 1 ? 0 : normalized + 1;
  }

  static boolean requiresMaintenance(boolean learnerDemand, boolean activeTarget) {
    return learnerDemand || activeTarget;
  }

  static long pulseIntervalMillis(long configuredInterval) {
    return Math.max(2_000L, Math.min(60_000L, configuredInterval));
  }

  static long targetTtlMillis(long configuredInterval, int typeCount) {
    long pulseInterval = pulseIntervalMillis(configuredInterval);
    long fullCycle = pulseInterval * Math.max(1, typeCount);
    return Math.max(30_000L, fullCycle + (pulseInterval * 2L));
  }

  @ConfigDescription("A compact HUD cue names the nearest supported structure and shows its direction and distance.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Detection Range Base for the Discovery Sixth Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double detectionRangeBase = 48;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Detection Range Factor for the Discovery Sixth Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double detectionRangeFactor = 452;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum structure detection range in blocks, hard-capped at 500.", impact = "Lower values reduce the search radius and HUD reach for every level.")
    double maxDetectionRange = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds between sense pulses for a single player.", impact = "Higher values pulse less often and reduce structure search work.")
    long pulseIntervalMillis = 4000;

    public Config() {
      baseCost = 2;
      costFactor = 0.4;
      maxLevel = 5;
      initialCost = 3;
    }
  }

  record StructureTarget(UUID worldId, double x, double z, String name, String symbol, long foundAtMillis) {
    boolean matches(World world) {
      return world != null && worldId.equals(world.getUID());
    }

    double distanceSquared(Location origin) {
      if (!matches(origin.getWorld())) {
        return Double.POSITIVE_INFINITY;
      }
      double dx = x - origin.getX();
      double dz = z - origin.getZ();
      return (dx * dx) + (dz * dz);
    }

    boolean sameTarget(StructureTarget other) {
      return other != null
          && worldId.equals(other.worldId)
          && Double.compare(x, other.x) == 0
          && Double.compare(z, other.z) == 0
          && name.equals(other.name);
    }
  }
}
