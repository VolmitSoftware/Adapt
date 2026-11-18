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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.RunsWithoutLearnedAdaptation;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.fx.ViewerDisplayPort;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.DiscoveryMessages;
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
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class DiscoverySixthSense extends SimpleAdaptation<DiscoverySixthSense.Config> {
  public static final PlayerPreference<DiscoveryPreferences.Structures> STRUCTURES = DiscoveryPreferences.structures("structures", DiscoveryMessages.PREFERENCE_DISCOVERYSIXTHSENSE_STRUCTURES);
  public static final PlayerPreference<CommonPreferences.Toggle> HUD = CommonPreferences.toggle("hud", DiscoveryMessages.PREFERENCE_DISCOVERYSIXTHSENSE_HUD, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> FULL = CommonPreferences.toggle("full-details", DiscoveryMessages.PREFERENCE_DISCOVERYSIXTHSENSE_FULL, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Scale> FREQUENCY = CommonPreferences.scale("cue-frequency", DiscoveryMessages.PREFERENCE_DISCOVERYSIXTHSENSE_FREQUENCY);

  static final double HARD_MAX_RANGE = 500.0D;
  private static final String HUD_GROUP = "discovery-sixth-sense";
  private static final Id LINE_BLOCK = Id.minecraft("light_blue_stained_glass");

  private final Cooldowns pulseThrottle = cooldowns();
  private final Cooldowns cueThrottle = cooldowns();
  private final GeneratedStructureSearch structureSearch = new GeneratedStructureSearch();
  private final Map<UUID, GeneratedStructureSearch.Sweep> searches = playerState();
  private final Map<UUID, Box> targetBounds = playerState();
  private long nextPlayerTick;
  private final Map<UUID, StructureTarget> targets = playerState();
  private final Map<UUID, Float> expPins = playerState();

  public DiscoverySixthSense() {
    super("discovery-sixth-sense");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("echo_shard"));
    setInterval(2000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("echo_shard"))
        .key("challenge_discovery_sixthsense_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("sculk_sensor"))
            .key("challenge_discovery_sixthsense_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_discovery_sixthsense_100", "discovery.sixth-sense.senses", 100, 300);
    registerMilestone("challenge_discovery_sixthsense_1k", "discovery.sixth-sense.senses", 1000, 1000);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    clearTarget(player.getPlayer());
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, STRUCTURES, HUD, FULL, FREQUENCY);
  }

  @Override
  public void addStats(int level, StatLines v) {
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
    return requiresMaintenance(super.hasTickDemand(), !targets.isEmpty() || !searches.isEmpty() || structureSearch.hasWork());
  }

  @Override
  public void onTick() {
    structureSearch.tick();
    setInterval(structureSearch.hasWork() ? 50 : 2000);
    long now = System.currentTimeMillis();
    if (now < nextPlayerTick) {
      return;
    }
    nextPlayerTick = now + 2000;
    cleanupInactiveTargets();
    for (AdaptPlayer adaptPlayer : learnedCandidates(now)) {
      PlayerRef player = adaptPlayer.getPlayer();
      if (player == null || !player.isOnline()) {
        continue;
      }

      Tasks.runEntity(player, () -> tickOwned(player));
    }
  }

  private void cleanupInactiveTargets() {
    for (UUID playerId : List.copyOf(searches.keySet())) {
      if (getServer().hasOnlineLearner(playerId, getName())) {
        continue;
      }

      PlayerRef player = AdaptCore.platform().host().player(playerId);
      if (player == null) {
        targets.remove(playerId);
        cancelSearch(playerId);
        targetBounds.remove(playerId);
        pulseThrottle.clear(playerId);
        cueThrottle.clear(playerId);
        continue;
      }
      Tasks.runEntity(player, () -> clearInactiveTargetOwned(player));
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

    GeneratedStructureSearch.Sweep pending = searches.get(playerId);
    if (pending != null && !pending.accepts(origin.world().uid())) {
      cancelSearch(playerId);
      pending = null;
    }
    if (pending != null && pending.running()) {
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
    UUID playerId = player.uuid();
    cancelSearch(playerId);
    Id selected = preference(player, STRUCTURES).type();
    List<Id> types = selected == null ? DiscoveryStructures.SENSED : List.of(selected);
    WorldRef world = origin.world();
    GeneratedStructureSearch.Sweep sweep = structureSearch.begin(new GeneratedStructureSearch.Request(
        UUID.randomUUID(), world, world.uid(), origin.x(), origin.z(), range, types,
        candidate -> Tasks.runEntity(player, () -> acceptCandidateOwned(player, candidate))));
    searches.put(playerId, sweep);
    setInterval(50);
  }

  private void acceptCandidateOwned(PlayerRef player, GeneratedStructureSearch.Candidate candidate) {
    UUID playerId = player.uuid();
    GeneratedStructureSearch.Sweep sweep = searches.get(playerId);
    if (sweep == null || !sweep.owns(candidate) || getActiveLevel(player) <= 0) {
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
        candidate.name(), candidate.symbol(), M.ms());
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
    GeneratedStructureSearch.Sweep search = searches.remove(playerId);
    if (search != null) {
      search.cancel();
    }
  }

  private void showCurrentTarget(PlayerRef player, Loc origin, double range) {
    StructureTarget target = targets.get(player.uuid());
    if (target == null) {
      return;
    }

    long targetTtl = targetTtlMillis(getConfig().pulseIntervalMillis, DiscoveryStructures.SENSED.size());
    if (!target.matches(origin.world()) || M.ms() - target.foundAtMillis() > targetTtl) {
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
      AdaptHud.clearAmbientStatus(player, HUD_GROUP);
      restoreExpBar(player);
      return;
    }
    String message = AdaptLanguage.text(
        DiscoveryMessages.SIXTH_SENSE_HUD,
        trusted("symbol", C.AQUA + target.symbol()),
        trusted("structure", C.WHITE + target.name()),
        trusted("direction", C.YELLOW + compassDirection(dx, dz)),
        trusted("distance", C.GRAY + String.valueOf(Math.round(distance)))
    );
    if (!preferenceEnabled(player, FULL)) {
      message = compassDirection(dx, dz) + " " + Math.round(distance);
    }
    AdaptHud.ambientStatus(player, HUD_GROUP, message);
    pinExpBar(player, distance, range);

    if (newlyLocated && cueThrottle.isReady(player.uuid(), playerPulseInterval(player))) {
      cueThrottle.mark(player.uuid());
      pulseTowards(player, dx / distance, dz / distance, distance);
      getPlayer(player).getData().addStat("discovery.sixth-sense.senses", 1);
    }
  }

  private void clearTarget(PlayerRef player) {
    cancelSearch(player.uuid());
    targetBounds.remove(player.uuid());
    targets.remove(player.uuid());
    AdaptHud.clearAmbientStatus(player, HUD_GROUP);
    restoreExpBar(player);
    displays().clearViewer(getName(), player.uuid());
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

    double length = Math.min(6D, horizontal);
    Vec3 direction = new Vec3(ux, 0D, uz);
    Loc start = player.eyeLocation().add(direction.multiply(0.65D));
    Loc end = start.add(direction.multiply(length));
    displays().showLine(
        getName(),
        "structure-direction",
        player,
        start,
        end,
        AdaptCore.platform().host().items().blockState(LINE_BLOCK),
        new Rgb(100, 225, 255),
        0.07D,
        50
    );
    fx(player, FxPriority.AMBIENT).sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.22F, 1.9F);
    fx(player.location(), FxPriority.AMBIENT)
        .ring(Particles.END_ROD, 1.1D, 8, 0.1D);
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
      PlayerRef player = AdaptCore.platform().host().player(playerId);
      if (player != null) {
        Tasks.runEntity(player, () -> {
          AdaptHud.clearAmbientStatus(player, HUD_GROUP);
          restoreExpBar(player);
        });
      }
    }
    structureSearch.close();
    for (GeneratedStructureSearch.Sweep search : searches.values()) {
      search.cancel();
    }
    searches.clear();
    targetBounds.clear();
    targets.clear();
    displays().clearChannel(getName());
    super.unregister();
  }

  private static ViewerDisplayPort displays() {
    return AdaptPorts.get(ViewerDisplayPort.class, ViewerDisplayPort.NONE);
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

  static String structureName(Id structureKey, Id fallbackType) {
    String key = structureKey == null ? fallbackType.path() : structureKey.path();
    return Form.capitalizeWords(key.toLowerCase(Locale.ROOT).replace('_', ' '));
  }

  static String structureSymbol(Id type) {
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

  static boolean shouldReplaceTarget(StructureTarget current, StructureTarget candidate, Loc origin) {
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
    boolean matches(WorldRef world) {
      return world != null && worldId.equals(world.uid());
    }

    double distanceSquared(Loc origin) {
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
