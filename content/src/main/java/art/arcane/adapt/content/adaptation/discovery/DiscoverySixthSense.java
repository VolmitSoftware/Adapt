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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.RunsWithoutLearnedAdaptation;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.viewer.ViewerDisplayPort;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;


public class DiscoverySixthSense extends SimpleAdaptation<DiscoverySixthSense.Config> {
  public static final Id ID = Id.adapt("discovery/sixth_sense");
  public static final PlayerPreference<DiscoveryPreferences.Structures> STRUCTURES = DiscoveryPreferences.structures("structures", Text.key("adapt.discovery.preferences.discoverysixthsense.structures"));
  public static final PlayerPreference<CommonPreferences.Toggle> HUD = CommonPreferences.toggle("hud", Text.key("adapt.discovery.preferences.discoverysixthsense.hud"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> FULL = CommonPreferences.toggle("full-details", Text.key("adapt.discovery.preferences.discoverysixthsense.full-details"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Scale> FREQUENCY = CommonPreferences.scale("cue-frequency", Text.key("adapt.discovery.preferences.discoverysixthsense.cue-frequency"));

  static final double HARD_MAX_RANGE = 500.0D;
  private static final String HUD_GROUP = "discovery-sixth-sense";
  private static final Id LINE_BLOCK = Id.minecraft("light_blue_stained_glass");

  private final Cooldowns pulseThrottle = cooldowns();
  private final Cooldowns cueThrottle = cooldowns();
  private final StructureSense sense = new StructureSense();
  private final Map<UUID, Box> targetBounds = playerState();
  private long nextPlayerTick;
  private final Map<UUID, StructureTarget> targets = playerState();
  private final Map<UUID, Float> expPins = playerState();

  public DiscoverySixthSense() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("echo_shard"));
    setInterval(2000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("echo_shard"))
        .key("challenge_discovery_sixthsense_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("sculk_sensor"))
            .key("challenge_discovery_sixthsense_1k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_discovery_sixthsense_100", "discovery.sixth-sense.senses", 100, 300);
    registerMilestone("challenge_discovery_sixthsense_1k", "discovery.sixth-sense.senses", 1000, 1000);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    clearTarget(player.player());
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, STRUCTURES, HUD, FULL, FREQUENCY);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(detectionRange(
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
    return requiresMaintenance(super.hasTickDemand(), !targets.isEmpty() || sense.hasSweeps() || sense.hasWork());
  }

  @Override
  public void onTick() {
    sense.tick();
    setInterval(sense.hasWork() ? 50 : 2000);
    long now = System.currentTimeMillis();
    if (now < nextPlayerTick) {
      return;
    }
    nextPlayerTick = now + 2000;
    cleanupInactiveTargets();
    for (PlayerProfile adaptPlayer : learnedCandidates(now)) {
      PlayerRef player = adaptPlayer.player();
      if (player == null || !player.isOnline()) {
        continue;
      }

      AdaptApi.scheduler().runEntity(player, () -> tickOwned(player));
    }
  }

  private void cleanupInactiveTargets() {
    for (UUID playerId : sense.sweepingPlayers()) {
      if (ownerLevel(playerId) > 0) {
        continue;
      }

      PlayerRef player = AdaptApi.platform().host().player(playerId);
      if (player == null) {
        targets.remove(playerId);
        cancelSearch(playerId);
        targetBounds.remove(playerId);
        pulseThrottle.clear(playerId);
        cueThrottle.clear(playerId);
        continue;
      }
      AdaptApi.scheduler().runEntity(player, () -> clearInactiveTargetOwned(player));
    }
  }

  private void clearInactiveTargetOwned(PlayerRef player) {
    UUID playerId = player.uuid();
    if (getActiveLevel(player) > 0) {
      return;
    }
    pulseThrottle.clear(playerId);
    cueThrottle.clear(playerId);
    clearTarget(player);
  }

  private void tickOwned(PlayerRef player) {
    int level = getActiveLevel(player);
    if (level <= 0) {
      clearTarget(player);
      return;
    }

    Loc origin = player.location();
    UUID playerId = player.uuid();
    Box bounds = targetBounds.get(playerId);
    if (bounds != null && DiscoveryStructures.contains(bounds, origin.x(), origin.y(), origin.z())) {
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

    if (sense.running(playerId, origin.world().uid())) {
      return;
    }

    long pulseInterval = playerPulseInterval(player);
    if (!pulseThrottle.isReady(playerId, pulseInterval)) {
      return;
    }
    pulseThrottle.mark(playerId);
    searchOwned(player, origin, range);
  }

  private void searchOwned(PlayerRef player, Loc origin, double range) {
    Id selected = preference(player, STRUCTURES).type();
    List<Id> types = selected == null ? DiscoveryStructures.SENSED : List.of(selected);
    sense.begin(player, origin, range, types, (StructureSense.Sensed candidate) -> acceptCandidateOwned(player, candidate));
    setInterval(50);
  }

  private void acceptCandidateOwned(PlayerRef player, StructureSense.Sensed candidate) {
    UUID playerId = player.uuid();
    if (getActiveLevel(player) <= 0) {
      return;
    }
    Loc origin = player.location();
    if (!candidate.worldId().equals(origin.world().uid())) {
      return;
    }
    if (DiscoveryStructures.contains(candidate.bounds(), origin.x(), origin.y(), origin.z())) {
      clearTarget(player);
      return;
    }
    double range = detectionRange(getLevelPercent(getActiveLevel(player)), getConfig().detectionRangeBase,
        getConfig().detectionRangeFactor, getConfig().maxDetectionRange);
    StructureTarget target = new StructureTarget(candidate.worldId(), candidate.x(), candidate.z(),
        candidate.name(), candidate.symbol(), System.currentTimeMillis());
    if (target.distanceSquared(origin) > range * range) {
      return;
    }
    StructureTarget previous = targets.get(playerId);
    boolean replaced = shouldReplaceTarget(previous, target, origin);
    boolean refreshed = previous != null && previous.sameTarget(target);
    if (replaced || refreshed) {
      targets.put(playerId, target);
      targetBounds.put(playerId, candidate.bounds());
      showTarget(player, origin, target, range, replaced && !refreshed);
    }
  }

  private long playerPulseInterval(PlayerRef player) {
    return (long) (pulseIntervalMillis(getConfig().pulseIntervalMillis) / preference(player, FREQUENCY).multiplier());
  }

  private void cancelSearch(UUID playerId) {
    sense.cancel(playerId);
  }


  private void showCurrentTarget(PlayerRef player, Loc origin, double range) {
    StructureTarget target = targets.get(player.uuid());
    if (target == null) {
      return;
    }

    long targetTtl = targetTtlMillis(getConfig().pulseIntervalMillis, DiscoveryStructures.SENSED.size());
    if (!target.matches(origin.world()) || System.currentTimeMillis() - target.foundAtMillis() > targetTtl) {
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

  private void showTarget(PlayerRef player, Loc origin, StructureTarget target, double range, boolean newlyLocated) {
    double dx = target.x() - origin.x();
    double dz = target.z() - origin.z();
    double distance = Math.sqrt((dx * dx) + (dz * dz));
    if (distance <= 0.0D || distance > range) {
      return;
    }

    if (!preferenceEnabled(player, HUD)) {
      HudStatus.clearAmbient(player, HUD_GROUP);
      restoreExpBar(player);
      return;
    }
    String message = Text.key("adapt.discovery.sixth_sense.hud").withMarkup("symbol", TextFormat.AQUA + target.symbol()).withMarkup("structure", TextFormat.WHITE + target.name()).withMarkup("direction", TextFormat.YELLOW + compassDirection(dx, dz)).withMarkup("distance", TextFormat.GRAY + String.valueOf(Math.round(distance))).resolve();
    if (!preferenceEnabled(player, FULL)) {
      message = compassDirection(dx, dz) + " " + Math.round(distance);
    }
    HudStatus.ambient(player, HUD_GROUP, message);
    pinExpBar(player, distance, range);

    if (newlyLocated && cueThrottle.isReady(player.uuid(), playerPulseInterval(player))) {
      cueThrottle.mark(player.uuid());
      pulseTowards(player, dx / distance, dz / distance, distance);
      addStat(player, "discovery.sixth-sense.senses", 1);
    }
  }

  private void clearTarget(PlayerRef player) {
    cancelSearch(player.uuid());
    targetBounds.remove(player.uuid());
    targets.remove(player.uuid());
    HudStatus.clearAmbient(player, HUD_GROUP);
    restoreExpBar(player);
    displays().clearViewer(getId().toString(), player.uuid());
  }

  private void pinExpBar(PlayerRef player, double distance, double range) {
    if (range <= 0.0D) {
      return;
    }
    float progress = (float) Math.min(1.0D, Math.max(0.0D, 1.0D - (distance / range)));
    float rounded = Math.round(progress * 100F) / 100F;
    Float last = expPins.put(player.uuid(), rounded);
    if (last == null || last != rounded) {
      player.sendExperienceChange(rounded, player.level());
    }
  }

  private void restoreExpBar(PlayerRef player) {
    if (expPins.remove(player.uuid()) != null) {
      player.sendExperienceChange(player.expProgress(), player.level());
    }
  }

  private void pulseTowards(PlayerRef player, double ux, double uz, double horizontal) {
    if (!getConfig().showParticles) {
      return;
    }

    pointTowards(this, player, ux, uz, horizontal);
    fx(player, FxPriority.AMBIENT).sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.22F, 1.9F);
    fx(player.location(), FxPriority.AMBIENT)
        .ring(Particles.END_ROD, 1.1D, 8, 0.1D);
  }

  /** A short glowing line only the player sees, from their eyes along the unit direction {@code (ux, uz)}. */
  public static void pointTowards(Adaptation<?> owner, PlayerRef player, double ux, double uz, double horizontal) {
    pointTowards(owner, player, "structure-direction", ux, uz, horizontal, 50);
  }

  /** {@link #pointTowards(Adaptation, PlayerRef, double, double, double)} under its own display key, for {@code ticks}. */
  public static void pointTowards(Adaptation<?> owner, PlayerRef player, String key, double ux, double uz, double horizontal,
                                  int ticks) {
    double length = Math.min(6D, horizontal);
    Vec3 direction = new Vec3(ux, 0D, uz);
    Loc start = player.eyeLocation().add(direction.multiply(0.65D));
    Loc end = start.add(direction.multiply(length));
    displays().showLine(
        owner.getId().toString(),
        key,
        player,
        start,
        end,
        AdaptApi.platform().host().items().blockState(LINE_BLOCK),
        new Rgb(100, 225, 255),
        0.07D,
        ticks
    );
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    UUID playerId = event.player().uuid();
    pulseThrottle.clear(playerId);
    cueThrottle.clear(playerId);
    clearTarget(event.player());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  @RunsWithoutLearnedAdaptation
  public void onMove(PlayerMoveEvent event) {
    PlayerRef player = event.player();
    UUID playerId = player.uuid();
    if (getActiveLevel(player) > 0) {
      return;
    }

    pulseThrottle.clear(playerId);
    cueThrottle.clear(playerId);
    clearTarget(player);
  }

  @Override
  public void unregister() {
    for (UUID playerId : List.copyOf(targets.keySet())) {
      PlayerRef player = AdaptApi.platform().host().player(playerId);
      if (player != null) {
        AdaptApi.scheduler().runEntity(player, () -> {
          HudStatus.clearAmbient(player, HUD_GROUP);
          restoreExpBar(player);
        });
      }
    }
    sense.close();
    targetBounds.clear();
    targets.clear();
    displays().clearChannel(getId().toString());
    super.unregister();
  }

  private static ViewerDisplayPort displays() {
    return ViewerDisplayPort.current();
  }

  static double detectionRange(double levelPercent, double base, double factor) {
    return detectionRange(levelPercent, base, factor, HARD_MAX_RANGE);
  }

  public static double detectionRange(double levelPercent, double base, double factor, double configuredMax) {
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

  public static String compassDirection(double dx, double dz) {
    if (Math.abs(dx) < 1.0E-9D && Math.abs(dz) < 1.0E-9D) {
      return "•";
    }
    String[] directions = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    double normalized = Math.atan2(-dx, dz) / (Math.PI / 4.0D);
    return directions[Math.floorMod((int) Math.round(normalized), directions.length)];
  }

  public static String structureName(Id structureKey, Id fallbackType) {
    String key = structureKey == null ? fallbackType.path() : structureKey.path();
    return Format.capitalizeWords(key.toLowerCase(Locale.ROOT).replace('_', ' '));
  }

  public static String structureSymbol(Id type) {
    if (DiscoveryStructures.JIGSAW.equals(type)) {
      return "⌂";
    }
    if (DiscoveryStructures.OCEAN_MONUMENT.equals(type) || DiscoveryStructures.OCEAN_RUIN.equals(type) || DiscoveryStructures.SHIPWRECK.equals(type)) {
      return "⚓";
    }
    if (DiscoveryStructures.FORTRESS.equals(type) || DiscoveryStructures.STRONGHOLD.equals(type) || DiscoveryStructures.WOODLAND_MANSION.equals(type)) {
      return "♜";
    }
    return "◇";
  }

  public static boolean shouldReplaceTarget(StructureTarget current, StructureTarget candidate, Loc origin) {
    if (candidate == null) {
      return false;
    }
    if (current == null || !current.matches(origin.world())) {
      return true;
    }
    return candidate.distanceSquared(origin) < current.distanceSquared(origin);
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
    @ConfigDoc(value = "Controls Detection Range Base for the Discovery Sixth Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double detectionRangeBase = 48;
    @ConfigDoc(value = "Controls Detection Range Factor for the Discovery Sixth Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double detectionRangeFactor = 452;
    @ConfigDoc(value = "Maximum structure detection range in blocks, hard-capped at 500.", impact = "Lower values reduce the search radius and HUD reach for every level.")
    double maxDetectionRange = 500;
    @ConfigDoc(value = "Milliseconds between sense pulses for a single player.", impact = "Higher values pulse less often and reduce structure search work.")
    long pulseIntervalMillis = 4000;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 5;
      initialCost = 1;
    }
  }

  public record StructureTarget(UUID worldId, double x, double z, String name, String symbol, long foundAtMillis) {
    public boolean matches(WorldRef world) {
      return world != null && worldId.equals(world.uid());
    }

    public double distanceSquared(Loc origin) {
      if (!matches(origin.world())) {
        return Double.POSITIVE_INFINITY;
      }
      double dx = x - origin.x();
      double dz = z - origin.z();
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
