package art.arcane.adapt.content.adaptation.excavation.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.mutation.LingeringSound;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.excavation.ExcavationOreMarkers;
import art.arcane.adapt.content.adaptation.excavation.ExcavationOreScan;
import art.arcane.adapt.content.adaptation.excavation.ExcavationSpelunker;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.api.world.BlockScan;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Super-Seeing Spelunker and Seismic Ping fused: digging with a shovel or pickaxe sweeps the ground around the dig
 * and outlines every ore it finds, hidden veins included, without glow berries or an ore in hand.
 */
public class DeepSight extends SkillUpgrade<DeepSight.Config> {
  public static final Id ID = Id.adapt("excavation/upgrade/deep_sight");

  private static final int MAX_SCAN_RADIUS = 32;
  private static final int MAX_BLOCK_CHECKS = 8192;
  private static final int MAX_HIGHLIGHTS = 16;
  private static final Id BLOCK_DISPLAY = Id.minecraft("block_display");
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, UUID> activeScans = new ConcurrentHashMap<>();
  private final ExcavationOreMarkers markers = new ExcavationOreMarkers(this,
      (PlayerRef player) -> getActiveLevel(player) > 0);

  public DeepSight() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("glow_ink_sac"));
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    UUID playerId = player.player().uuid();
    activeScans.remove(playerId);
    BlockScan.cancel(this, playerId);
    markers.clearFor(playerId);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getScanRadius(level), 1);
    statLore(v, getHighlights(level), 2);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(getCooldownMillis(level), 1), 3);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    PlayerRef p = e.player();
    if (!ExcavationOreScan.isDigTool(p.inventory().mainHand()) || activeScans.containsKey(p.uuid())) {
      return;
    }

    BlockActionContext context = resolveBlockBreakContext(p, e.block().location());
    if (context == null) {
      return;
    }

    int level = context.level();
    long cooldownMillis = getCooldownMillis(level);
    if (!cooldowns.isReady(p.uuid(), cooldownMillis)) {
      return;
    }

    int checks = Math.min(MAX_BLOCK_CHECKS, Math.max(1, getConfig().maxBlockChecks));
    BlockScan request = ExcavationOreScan.request(e.block().location(),
        new ExcavationOreScan.Limits(getScanRadius(level), getConfig().denseScanRadius, checks, getHighlights(level)),
        (Id type) -> true,
        (BlockScan.Result result) -> completeScan(p, result));
    cooldowns.mark(p.uuid(), cooldownMillis);
    activeScans.put(p.uuid(), request.submit(this, p.uuid()));
  }

  private void completeScan(PlayerRef p, BlockScan.Result result) {
    UUID playerId = p.uuid();
    if (!result.scan().equals(activeScans.get(playerId))) {
      return;
    }

    boolean scheduled = AdaptApi.scheduler().runEntity(p, () -> {
      if (!activeScans.remove(playerId, result.scan()) || !p.isOnline() || result.matches().isEmpty()) {
        return;
      }

      float viewRange = (float) Math.max(0.5D, Math.min(2D, getConfig().displayViewRange));
      int durationTicks = getMarkerDurationTicks();
      for (BlockScan.Match match : result.matches()) {
        addStat(p, "excavation.deep-sight.ores-revealed", 1);
        markers.show(p, result.world(), match, new ExcavationOreMarkers.Style(
            ExcavationSpelunker.markerColor(match.block()), viewRange, durationTicks, !match.supplied()));
      }
      cue(p, MutationCue.WORK);
      LingeringSound lingering = linger(p);
      AdaptApi.scheduler().runEntity(p, lingering::stop, durationTicks);
    });
    if (!scheduled) {
      activeScans.remove(playerId, result.scan());
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityRemoveEvent e) {
    if (e.entity().type().equals(BLOCK_DISPLAY)) {
      markers.forget(e.entity().uuid());
    }
  }

  @Handle(ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    UUID playerId = e.player().uuid();
    activeScans.remove(playerId);
    BlockScan.cancel(this, playerId);
  }

  @Override
  public void unregister() {
    markers.close();
    super.unregister();
    activeScans.clear();
    BlockScan.cancelAll(this);
  }

  int getScanRadius(int level) {
    return Math.min(MAX_SCAN_RADIUS, Math.max(1, getConfig().scanRadiusBase + (level * getConfig().scanRadiusPerLevel)));
  }

  int getHighlights(int level) {
    return Math.min(MAX_HIGHLIGHTS, Math.max(1, getConfig().highlightsBase + (level * getConfig().highlightsPerLevel)));
  }

  long getCooldownMillis(int level) {
    return (long) Math.max(getConfig().minimumCooldownMillis,
        getConfig().cooldownMillisBase - (level * getConfig().cooldownMillisPerLevel));
  }

  private int getMarkerDurationTicks() {
    return Math.max(20, Math.min(20 * 30, getConfig().highlightDurationTicks));
  }

  @ConfigDescription("Digging outlines the ores around you through the ground.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Scan radius in blocks before level scaling.", impact = "Higher values find ore farther from the dig; clamped to 32.")
    int scanRadiusBase = 8;
    @ConfigDoc(value = "Scan radius added per level, in blocks.", impact = "Higher values reward higher levels more.")
    int scanRadiusPerLevel = 4;
    @ConfigDoc(value = "Ores outlined per scan before level scaling.", impact = "Higher values show more ore at once; clamped to 16.")
    int highlightsBase = 4;
    @ConfigDoc(value = "Ores outlined per scan added per level.", impact = "Higher values reward higher levels more.")
    int highlightsPerLevel = 3;
    @ConfigDoc(value = "Most world block checks one scan makes.", impact = "Higher values find ore more reliably at more scan cost; clamped to 8192.")
    int maxBlockChecks = 4096;
    @ConfigDoc(value = "Radius searched completely before the rest of the budget spreads over the full radius.", impact = "Higher values favour ore close to the dig.")
    int denseScanRadius = 6;
    @ConfigDoc(value = "How long each outline stays, in ticks.", impact = "Higher values keep outlines up longer; clamped between 20 and 600.")
    int highlightDurationTicks = 100;
    @ConfigDoc(value = "Client view-range multiplier for the outlines.", impact = "Higher values render outlines from farther away; clamped between 0.5 and 2.0.")
    double displayViewRange = 1.0;
    @ConfigDoc(value = "Time between two scans before level scaling, in milliseconds.", impact = "Higher values make scans rarer.")
    double cooldownMillisBase = 6000;
    @ConfigDoc(value = "Cooldown removed per level, in milliseconds.", impact = "Higher values make higher levels scan more often.")
    double cooldownMillisPerLevel = 1000;
    @ConfigDoc(value = "Shortest time between two scans at any level, in milliseconds.", impact = "Lower values allow more frequent scans.")
    double minimumCooldownMillis = 2000;
  }
}
