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

package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Sounds;
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
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.api.world.BlockScan;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class ExcavationSeismicPing extends SimpleAdaptation<ExcavationSeismicPing.Config> {
  public static final Id ID = Id.adapt("excavation/seismic_ping");
  public static final PlayerPreference<ExcavationPreferences.Palette> COLOR = ExcavationPreferences.palette("color", Text.key("adapt.excavation.preferences.excavationseismicping.color"));
  public static final PlayerPreference<ExcavationPreferences.Ores> ORES = ExcavationPreferences.ores("ores", Text.key("adapt.excavation.preferences.excavationseismicping.ores"));
  public static final PlayerPreference<CommonPreferences.Scale> FREQUENCY = CommonPreferences.scale("cue-frequency", Text.key("adapt.excavation.preferences.excavationseismicping.cue-frequency"));

  static final int GLOW_DURATION_TICKS = 40;
  private static final int MAX_SCAN_RANGE = 32;
  private static final int MAX_BLOCK_CHECKS_PER_ACTIVATION = 2048;
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, UUID> activeScans = new ConcurrentHashMap<>();
  private final Map<UUID, UUID> activeRevealWindows = new ConcurrentHashMap<>();
  private final AtomicBoolean acceptingReveals = new AtomicBoolean(true);
  private final Object revealLifecycleLock = new Object();

  public ExcavationSeismicPing() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("goat_horn"));
    setInterval(2200);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("bell"))
        .key("challenge_excavation_seismic_200")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_excavation_seismic_200", "excavation.seismic-ping.pings-triggered", 200, 400);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    UUID playerId = player.player().uuid();
    activeScans.remove(playerId);
    BlockScan.cancel(this, playerId);
    activeRevealWindows.remove(playerId);
    displays().clearViewer(getId().toString(), playerId);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, COLOR, ORES, FREQUENCY);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getScanRange(level), 1);
    statLore(v, Format.pc(getPingChance(level), 0), 2);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(getCooldownMillis(level), 1), 3);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    if (!acceptingReveals.get()) {
      return;
    }
    PlayerRef p = e.player();
    if (!ExcavationOreScan.isDigTool(p.inventory().mainHand())) {
      return;
    }

    BlockActionContext context = resolveBlockBreakContext(p, e.block().location());
    if (context == null) {
      return;
    }

    UUID playerId = p.uuid();
    if (pingInProgress(activeScans.get(playerId), activeRevealWindows.get(playerId))) {
      return;
    }

    int level = context.level();
    if (!cooldowns.isReady(playerId, pingCooldownMillis(p, level))) {
      return;
    }

    if (ThreadLocalRandom.current().nextDouble() > getPingChance(level)) {
      return;
    }

    Loc blockLocation = e.block().location();
    int scanRange = getScanRange(level);
    startScan(p, blockLocation, scanRange);
  }

  private void startScan(PlayerRef p, Loc origin, int scanRange) {
    WorldRef world = origin.world();
    if (world == null) {
      return;
    }

    ExcavationPreferences.Ores ores = preference(p, ORES);
    int checks = Math.min(MAX_BLOCK_CHECKS_PER_ACTIVATION, Math.max(1, getConfig().maxBlockChecks));
    BlockScan request = ExcavationOreScan.request(origin,
        new ExcavationOreScan.Limits(scanRange, getConfig().denseScanRadius, checks, 1), ores::allows,
        (BlockScan.Result result) -> completeScan(p, origin, scanRange, result));
    synchronized (revealLifecycleLock) {
      UUID playerId = p.uuid();
      if (!acceptingReveals.get()
          || pingInProgress(activeScans.get(playerId), activeRevealWindows.get(playerId))) {
        return;
      }
      UUID scanId = request.submit(this, playerId);
      activeScans.put(playerId, scanId);
    }
  }

  private void completeScan(PlayerRef p, Loc blockLocation, int scanRange,
                            BlockScan.Result result) {
    UUID playerId = p.uuid();
    if (!result.scan().equals(activeScans.get(playerId))) {
      return;
    }

    boolean scheduled = AdaptApi.scheduler().runEntity(p, () -> completeScanOnPlayerThread(p, blockLocation, scanRange, result));
    if (!scheduled) {
      synchronized (revealLifecycleLock) {
        if (acceptingReveals.get() && activeScans.remove(playerId, result.scan())) {
          cooldowns.mark(playerId, pingCooldownMillis(p, getLevel(p)));
        }
      }
    }
  }

  private void completeScanOnPlayerThread(PlayerRef p, Loc blockLocation, int scanRange,
                                          BlockScan.Result result) {
    synchronized (revealLifecycleLock) {
      UUID playerId = p.uuid();
      if (!acceptingReveals.get() || !activeScans.remove(playerId, result.scan()) || !p.isOnline()) {
        return;
      }

      List<BlockScan.Match> matches = result.matches();
      if (matches.isEmpty()) {
        fx(blockLocation.add(0.5, 0.5, 0.5), FxPriority.TRANSITION)
            .sound(Sounds.BLOCK_NOTE_BLOCK_HAT, 0.3f, 0.5f);
        cooldowns.mark(playerId, pingCooldownMillis(p, getLevel(p)));
        return;
      }

      BlockScan.Match target = matches.get(0);
      Loc targetCenter = target.center(result.world());
      Loc playerOrigin = p.eyeLocation();
      UUID revealId = UUID.randomUUID();
      activeRevealWindows.put(playerId, revealId);
      showOreMarker(p, result.world(), target, revealId);
      playPingSound(p, playerOrigin.position().distance(targetCenter.position()), scanRange);
      addStat(p, "excavation.seismic-ping.pings-triggered", 1);
      xp(p, getConfig().xpPerPing + (getValue(target.block()) * getConfig().targetValueXpMultiplier));
    }
  }

  static Rgb oreTint(Id type) {
    if (type == null) {
      return new Rgb(110, 230, 255);
    }

    String name = type.legacyName();
    if (name.contains("REDSTONE")) {
      return new Rgb(255, 60, 60);
    }
    if (name.contains("DIAMOND")) {
      return new Rgb(90, 230, 235);
    }
    if (name.contains("GOLD")) {
      return new Rgb(255, 215, 70);
    }
    if (name.contains("IRON") || name.contains("COPPER")) {
      return new Rgb(210, 180, 150);
    }
    if (name.contains("EMERALD")) {
      return new Rgb(70, 230, 130);
    }
    if (name.contains("LAPIS")) {
      return new Rgb(60, 110, 240);
    }
    return new Rgb(110, 230, 255);
  }

  private void showOreMarker(PlayerRef player, WorldRef world, BlockScan.Match target, UUID revealId) {
    Loc location = new Loc(world, target.x(), target.y(), target.z(), 0F, 0F);
    String markerKey = markerKey(target);
    if (!AdaptApi.scheduler().runAt(location, () -> showOreMarkerAtRegion(player, world, target, location, revealId, markerKey))) {
      completeRevealWindow(player, revealId, markerKey);
    }
  }

  private void showOreMarkerAtRegion(PlayerRef player, WorldRef world, BlockScan.Match target,
                                     Loc location, UUID revealId, String markerKey) {
    if (!world.isChunkLoaded(target.x() >> 4, target.z() >> 4)) {
      completeRevealWindow(player, revealId, markerKey);
      return;
    }
    if (!target.supplied() && !world.blockAt(target.x(), target.y(), target.z()).type().equals(target.block())) {
      completeRevealWindow(player, revealId, markerKey);
      return;
    }

    synchronized (revealLifecycleLock) {
      UUID playerId = player.uuid();
      if (!acceptingReveals.get() || !player.isOnline()
          || !revealId.equals(activeRevealWindows.get(playerId))) {
        activeRevealWindows.remove(playerId, revealId);
        return;
      }
      BlockStateRef block = AdaptApi.platform().host().items().blockState(target.block());
      boolean shown = block != null && displays().showBlock(
          getId().toString(),
          markerKey,
          player,
          location,
          block,
          preference(player, COLOR).color(oreTint(target.block())),
          GLOW_DURATION_TICKS
      );
      if (!shown) {
        completeRevealWindow(player, revealId, markerKey);
        return;
      }
      if (!AdaptApi.scheduler().runEntity(player,
          () -> completeRevealWindow(player, revealId, markerKey), GLOW_DURATION_TICKS)) {
        displays().clearViewerKey(getId().toString(), markerKey, playerId);
        completeRevealWindow(player, revealId, markerKey);
      }
    }
  }

  private static String markerKey(BlockScan.Match target) {
    return "ore-" + target.x() + ":" + target.y() + ":" + target.z();
  }

  private void completeRevealWindow(PlayerRef player, UUID revealId, String markerKey) {
    synchronized (revealLifecycleLock) {
      UUID playerId = player.uuid();
      if (!retireRevealWindow(activeRevealWindows, playerId, revealId, acceptingReveals.get())) {
        return;
      }
      displays().clearViewerKey(getId().toString(), markerKey, playerId);
      cooldowns.mark(playerId, pingCooldownMillis(player, getLevel(player)));
    }
  }

  static boolean pingInProgress(UUID scanId, UUID revealId) {
    return scanId != null || revealId != null;
  }

  static boolean retireRevealWindow(Map<UUID, UUID> activeWindows, UUID playerId, UUID revealId,
                                    boolean acceptingReveals) {
    return acceptingReveals && activeWindows.remove(playerId, revealId);
  }

  private void playPingSound(PlayerRef p, double distance, int range) {
    double normalized = Math.min(1.0, distance / Math.max(1.0, range));
    float pitch = (float) Math.max(0.45, Math.min(1.95, 1.9 - (normalized * 1.1)));
    fx(p.location(), FxPriority.GAMEPLAY)
        .chord(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, pitch, Sounds.BLOCK_NOTE_BLOCK_BIT, 0.65f, (float) Math.min(2.0, pitch + 0.2));
  }

  @Handle(ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    UUID playerId = e.player().uuid();
    synchronized (revealLifecycleLock) {
      activeScans.remove(playerId);
      activeRevealWindows.remove(playerId);
      cooldowns.clear(playerId);
      BlockScan.cancel(this, playerId);
      displays().clearViewer(getId().toString(), playerId);
    }
  }

  @Override
  public void unregister() {
    synchronized (revealLifecycleLock) {
      acceptingReveals.set(false);
      activeScans.clear();
      activeRevealWindows.clear();
      BlockScan.cancelAll(this);
      displays().clearChannel(getId().toString());
    }
    super.unregister();
  }

  private static ViewerDisplayPort displays() {
    return ViewerDisplayPort.current();
  }


  private int getScanRange(int level) {
    return Math.min(MAX_SCAN_RANGE, Math.max(6, (int) Math.round(getConfig().scanRangeBase + (getLevelPercent(level) * getConfig().scanRangeFactor))));
  }

  private double getPingChance(int level) {
    return Math.min(getConfig().maxPingChance, getConfig().pingChanceBase + (getLevelPercent(level) * getConfig().pingChanceFactor));
  }

  private long pingCooldownMillis(PlayerRef p, int level) {
    return (long) (getCooldownMillis(level) / preference(p, FREQUENCY).multiplier());
  }

  private long getCooldownMillis(int level) {
    return Math.max(350L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }

  @ConfigDescription("Mining can emit seismic pings that temporarily reveal a nearby ore block.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Scan Range Base for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double scanRangeBase = 11;
    @ConfigDoc(value = "Controls Scan Range Factor for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double scanRangeFactor = 18;
    @ConfigDoc(value = "Maximum world block checks made by one seismic ping.", impact = "Higher values improve ore detection but increase total budgeted scan work; values above the hard safety cap are clamped.")
    int maxBlockChecks = 1024;
    @ConfigDoc(value = "Radius searched completely before the remaining seismic budget is spread across the full range.", impact = "Higher values prioritize nearby accuracy; lower values reserve more samples for distant ore.")
    int denseScanRadius = 5;
    @ConfigDoc(value = "Controls Ping Chance Base for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double pingChanceBase = 0.14;
    @ConfigDoc(value = "Controls Ping Chance Factor for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double pingChanceFactor = 0.37;
    @ConfigDoc(value = "Controls Max Ping Chance for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxPingChance = 0.6;
    @ConfigDoc(value = "Controls Cooldown Millis Base for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisBase = 2600;
    @ConfigDoc(value = "Controls Cooldown Millis Factor for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisFactor = 1850;
    @ConfigDoc(value = "Controls Xp Per Ping for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerPing = 8;
    @ConfigDoc(value = "Controls Target Value Xp Multiplier for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double targetValueXpMultiplier = 0.5;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }

}
