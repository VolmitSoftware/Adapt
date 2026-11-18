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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.fx.ViewerDisplayPort;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.integration.hiddenore.HiddenOrePort;
import art.arcane.adapt.localization.catalog.ExcavationMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.common.world.BlockScanScheduler;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class ExcavationSeismicPing extends SimpleAdaptation<ExcavationSeismicPing.Config> {
  public static final PlayerPreference<ExcavationPreferences.Palette> COLOR = ExcavationPreferences.palette("color", ExcavationMessages.PREFERENCE_EXCAVATIONSEISMICPING_COLOR);
  public static final PlayerPreference<ExcavationPreferences.Ores> ORES = ExcavationPreferences.ores("ores", ExcavationMessages.PREFERENCE_EXCAVATIONSEISMICPING_ORES);
  public static final PlayerPreference<CommonPreferences.Scale> FREQUENCY = CommonPreferences.scale("cue-frequency", ExcavationMessages.PREFERENCE_EXCAVATIONSEISMICPING_FREQUENCY);

  static final int GLOW_DURATION_TICKS = 40;
  private static final int MAX_SCAN_RANGE = 32;
  private static final int MAX_BLOCK_CHECKS_PER_ACTIVATION = 2048;
  private static final Id ANCIENT_DEBRIS = Id.minecraft("ancient_debris");
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, UUID> activeScans = new ConcurrentHashMap<>();
  private final Map<UUID, UUID> activeRevealWindows = new ConcurrentHashMap<>();
  private final AtomicBoolean acceptingReveals = new AtomicBoolean(true);
  private final Object revealLifecycleLock = new Object();

  public ExcavationSeismicPing() {
    super("excavation-seismic-ping");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("goat_horn"));
    setInterval(2200);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("bell"))
        .key("challenge_excavation_seismic_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_excavation_seismic_200", "excavation.seismic-ping.pings-triggered", 200, 400);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    UUID playerId = player.getPlayer().uuid();
    activeScans.remove(playerId);
    BlockScanScheduler.cancel(this, playerId);
    activeRevealWindows.remove(playerId);
    displays().clearViewer(getName(), playerId);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, COLOR, ORES, FREQUENCY);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getScanRange(level), 1);
    statLore(v, Form.pc(getPingChance(level), 0), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), 3);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    if (!acceptingReveals.get()) {
      return;
    }
    PlayerRef p = e.player();
    if (!isExcavationTool(p.inventory().mainHand())) {
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
    if (!cooldowns.isReady(playerId, (long) (getCooldownMillis(level) / preference(p, FREQUENCY).multiplier()))) {
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
    ArrayList<BlockScanScheduler.AdditionalMatch> hiddenMatches = new ArrayList<>(1);
    HiddenOrePort.VeinTarget hidden = nearestVein(origin, scanRange);
    if (hidden != null && hidden.location().world() != null && hidden.location().world().uid().equals(world.uid())
        && ores.allows(hidden.display())) {
      Loc at = hidden.location();
      double dx = at.x() - origin.x();
      double dy = at.y() - origin.y();
      double dz = at.z() - origin.z();
      double distanceSquared = (dx * dx) + (dy * dy) + (dz * dz);
      if (at.blockY() >= world.minHeight()
          && at.blockY() < world.maxHeight()
          && distanceSquared <= (double) scanRange * scanRange) {
        hiddenMatches.add(new BlockScanScheduler.AdditionalMatch(
            at.blockX(), at.blockY(), at.blockZ(), hidden.display(), distanceSquared
        ));
      }
    }

    int checks = Math.min(MAX_BLOCK_CHECKS_PER_ACTIVATION, Math.max(1, getConfig().maxBlockChecks));
    BlockScanScheduler.ScanRequest request = BlockScanScheduler.ScanRequest.builder(origin)
        .radius(scanRange)
        .denseRadius(getConfig().denseScanRadius)
        .maxSamples(checks)
        .maxResults(1)
        .seed(ThreadLocalRandom.current().nextInt())
        .additionalMatches(hiddenMatches)
        .matcher((Id type) -> isOre(type) && ores.allows(type))
        .completion((BlockScanScheduler.ScanResult result) -> completeScan(p, origin, scanRange, result))
        .build();
    synchronized (revealLifecycleLock) {
      UUID playerId = p.uuid();
      if (!acceptingReveals.get()
          || pingInProgress(activeScans.get(playerId), activeRevealWindows.get(playerId))) {
        return;
      }
      UUID scanId = BlockScanScheduler.submit(this, playerId, request);
      activeScans.put(playerId, scanId);
    }
  }

  static HiddenOrePort.VeinTarget nearestVein(HiddenOrePort port, Loc origin, int range) {
    HiddenOrePort.VeinTarget best = null;
    double bestDistanceSq = Double.MAX_VALUE;
    for (HiddenOrePort.VeinTarget vein : port.veins(origin, range)) {
      Loc at = vein.location();
      double dx = (at.blockX() + 0.5) - origin.x();
      double dy = (at.blockY() + 0.5) - origin.y();
      double dz = (at.blockZ() + 0.5) - origin.z();
      double distanceSq = (dx * dx) + (dy * dy) + (dz * dz);
      if (distanceSq < bestDistanceSq) {
        best = vein;
        bestDistanceSq = distanceSq;
      }
    }
    return best;
  }

  private static HiddenOrePort.VeinTarget nearestVein(Loc origin, int range) {
    return nearestVein(AdaptPorts.get(HiddenOrePort.class, HiddenOrePort.NONE), origin, range);
  }

  private void completeScan(PlayerRef p, Loc blockLocation, int scanRange,
                            BlockScanScheduler.ScanResult result) {
    UUID playerId = p.uuid();
    if (!result.scanId().equals(activeScans.get(playerId))) {
      return;
    }

    boolean scheduled = Tasks.runEntity(p, () -> completeScanOnPlayerThread(p, blockLocation, scanRange, result));
    if (!scheduled) {
      synchronized (revealLifecycleLock) {
        if (acceptingReveals.get() && activeScans.remove(playerId, result.scanId())) {
          cooldowns.mark(playerId);
        }
      }
    }
  }

  private void completeScanOnPlayerThread(PlayerRef p, Loc blockLocation, int scanRange,
                                          BlockScanScheduler.ScanResult result) {
    synchronized (revealLifecycleLock) {
      UUID playerId = p.uuid();
      if (!acceptingReveals.get() || !activeScans.remove(playerId, result.scanId()) || !p.isOnline()) {
        return;
      }

      List<BlockScanScheduler.Match> matches = result.matches();
      if (matches.isEmpty()) {
        fx(blockLocation.add(0.5, 0.5, 0.5), FxPriority.TRANSITION)
            .sound(Sounds.BLOCK_NOTE_BLOCK_HAT, 0.3f, 0.5f);
        cooldowns.mark(playerId);
        return;
      }

      BlockScanScheduler.Match target = matches.get(0);
      Loc targetCenter = target.center(result.world());
      Loc playerOrigin = p.eyeLocation();
      UUID revealId = UUID.randomUUID();
      activeRevealWindows.put(playerId, revealId);
      showOreMarker(p, result.world(), target, revealId);
      playPingSound(p, playerOrigin.position().distance(targetCenter.position()), scanRange);
      addStat(p, "excavation.seismic-ping.pings-triggered", 1);
      xp(p, getConfig().xpPerPing + (getValue(target.material()) * getConfig().targetValueXpMultiplier));
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

  private void showOreMarker(PlayerRef player, WorldRef world, BlockScanScheduler.Match target, UUID revealId) {
    Loc location = new Loc(world, target.x(), target.y(), target.z(), 0F, 0F);
    String markerKey = markerKey(target);
    if (!Tasks.runAt(location, () -> showOreMarkerAtRegion(player, world, target, location, revealId, markerKey))) {
      completeRevealWindow(player.uuid(), revealId, markerKey);
    }
  }

  private void showOreMarkerAtRegion(PlayerRef player, WorldRef world, BlockScanScheduler.Match target,
                                     Loc location, UUID revealId, String markerKey) {
    if (!world.isChunkLoaded(target.x() >> 4, target.z() >> 4)) {
      completeRevealWindow(player.uuid(), revealId, markerKey);
      return;
    }
    if (!target.supplied() && !world.blockAt(target.x(), target.y(), target.z()).type().equals(target.material())) {
      completeRevealWindow(player.uuid(), revealId, markerKey);
      return;
    }

    synchronized (revealLifecycleLock) {
      UUID playerId = player.uuid();
      if (!acceptingReveals.get() || !player.isOnline()
          || !revealId.equals(activeRevealWindows.get(playerId))) {
        activeRevealWindows.remove(playerId, revealId);
        return;
      }
      BlockStateRef block = AdaptCore.platform().host().items().blockState(target.material());
      boolean shown = block != null && displays().showBlock(
          getName(),
          markerKey,
          player,
          location,
          block,
          preference(player, COLOR).color(oreTint(target.material())),
          GLOW_DURATION_TICKS
      );
      if (!shown) {
        completeRevealWindow(playerId, revealId, markerKey);
        return;
      }
      if (!Tasks.runEntity(player,
          () -> completeRevealWindow(playerId, revealId, markerKey), GLOW_DURATION_TICKS)) {
        displays().clearViewerKey(getName(), markerKey, playerId);
        completeRevealWindow(playerId, revealId, markerKey);
      }
    }
  }

  private static String markerKey(BlockScanScheduler.Match target) {
    return "ore-" + target.x() + ":" + target.y() + ":" + target.z();
  }

  private void completeRevealWindow(UUID playerId, UUID revealId, String markerKey) {
    synchronized (revealLifecycleLock) {
      if (!retireRevealWindow(activeRevealWindows, playerId, revealId, acceptingReveals.get())) {
        return;
      }
      displays().clearViewerKey(getName(), markerKey, playerId);
      cooldowns.mark(playerId);
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

  private boolean isOre(Id type) {
    return type.equals(ANCIENT_DEBRIS) || type.legacyName().endsWith("_ORE");
  }

  private boolean isExcavationTool(ItemRef item) {
    if (!isItem(item)) {
      return false;
    }

    String name = item.type().legacyName();
    return name.endsWith("_SHOVEL") || name.endsWith("_PICKAXE");
  }

  @Handle(ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    UUID playerId = e.player().uuid();
    synchronized (revealLifecycleLock) {
      activeScans.remove(playerId);
      activeRevealWindows.remove(playerId);
      cooldowns.clear(playerId);
      BlockScanScheduler.cancel(this, playerId);
      displays().clearViewer(getName(), playerId);
    }
  }

  @Override
  public void unregister() {
    synchronized (revealLifecycleLock) {
      acceptingReveals.set(false);
      activeScans.clear();
      activeRevealWindows.clear();
      BlockScanScheduler.cancelOwner(this);
      displays().clearChannel(getName());
    }
    super.unregister();
  }

  private static ViewerDisplayPort displays() {
    return AdaptPorts.get(ViewerDisplayPort.class, ViewerDisplayPort.NONE);
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  private int getScanRange(int level) {
    return Math.min(MAX_SCAN_RANGE, Math.max(6, (int) Math.round(getConfig().scanRangeBase + (getLevelPercent(level) * getConfig().scanRangeFactor))));
  }

  private double getPingChance(int level) {
    return Math.min(getConfig().maxPingChance, getConfig().pingChanceBase + (getLevelPercent(level) * getConfig().pingChanceFactor));
  }

  private long getCooldownMillis(int level) {
    return Math.max(350L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }

  @ConfigDescription("Mining can emit seismic pings that temporarily reveal a nearby ore block.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Scan Range Base for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double scanRangeBase = 11;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Scan Range Factor for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double scanRangeFactor = 18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum world block checks made by one seismic ping.", impact = "Higher values improve ore detection but increase total budgeted scan work; values above the hard safety cap are clamped.")
    int maxBlockChecks = 1024;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Radius searched completely before the remaining seismic budget is spread across the full range.", impact = "Higher values prioritize nearby accuracy; lower values reserve more samples for distant ore.")
    int denseScanRadius = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Ping Chance Base for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double pingChanceBase = 0.14;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Ping Chance Factor for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double pingChanceFactor = 0.37;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Ping Chance for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxPingChance = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisBase = 2600;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisFactor = 1850;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Ping for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerPing = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Target Value Xp Multiplier for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double targetValueXpMultiplier = 0.5;

    public Config() {
      costFactor = 0.78;
      initialCost = 4;
    }
  }

}
