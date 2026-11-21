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
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ExcavationMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.display.BlockDisplayRef;
import art.arcane.adapt.platform.entity.display.Brightness;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class ExcavationSpelunker extends SimpleAdaptation<ExcavationSpelunker.Config> {
  public static final PlayerPreference<ExcavationPreferences.Palette> COLOR = ExcavationPreferences.palette("color", ExcavationMessages.PREFERENCE_EXCAVATIONSPELUNKER_COLOR);
  public static final PlayerPreference<ExcavationPreferences.Ores> ORES = ExcavationPreferences.ores("ores", ExcavationMessages.PREFERENCE_EXCAVATIONSPELUNKER_ORES);
  public static final PlayerPreference<CommonPreferences.Scale> RANGE = CommonPreferences.scale("range", ExcavationMessages.PREFERENCE_EXCAVATIONSPELUNKER_RANGE);

  private static final int MAX_SCAN_RADIUS = 32;
  private static final int MAX_BLOCK_CHECKS_PER_ACTIVATION = 8192;
  private static final int MAX_HIGHLIGHTS_PER_ACTIVATION = 16;
  private static final Id GLOW_BERRIES = Id.minecraft("glow_berries");
  private static final Id BLOCK_DISPLAY = Id.minecraft("block_display");
  private static final Set<String> ORE_TYPES = Set.of("iron_ore", "gold_ore", "copper_ore", "lapis_ore", "redstone_ore",
      "emerald_ore", "diamond_ore", "coal_ore", "deepslate_iron_ore", "deepslate_gold_ore", "deepslate_copper_ore",
      "deepslate_lapis_ore", "deepslate_redstone_ore", "deepslate_emerald_ore", "deepslate_diamond_ore",
      "deepslate_coal_ore", "nether_gold_ore", "nether_quartz_ore", "ancient_debris");
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, UUID> activeScans = new ConcurrentHashMap<>();
  private final Map<UUID, DisplayHandle> activeMarkers = new ConcurrentHashMap<>();
  private final AtomicBoolean acceptingMarkers = new AtomicBoolean(true);
  private final Object markerLifecycleLock = new Object();

  public ExcavationSpelunker() {
    super("excavation-spelunker");
    registerConfiguration(ExcavationSpelunker.Config.class);
    setIcon(Id.minecraft("golden_helmet"));
    setInterval(20388);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("spyglass"))
        .key("challenge_excavation_spelunker_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_ore"))
            .key("challenge_excavation_spelunker_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_excavation_spelunker_1k", "excavation.spelunker.ores-revealed", 1000, 400);
    registerMilestone("challenge_excavation_spelunker_25k", "excavation.spelunker.ores-revealed", 25000, 1500);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    UUID playerId = player.getPlayer().uuid();
    activeScans.remove(playerId);
    BlockScanScheduler.cancel(this, playerId);
    for (Map.Entry<UUID, DisplayHandle> entry : activeMarkers.entrySet()) {
      if (entry.getValue().owner().equals(playerId)) {
        removeMarker(entry.getKey());
      }
    }
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, COLOR, ORES, RANGE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + AdaptLanguage.text(ExcavationMessages.SPELUNKER_LORE1));
    v.addLore(C.YELLOW + AdaptLanguage.text(
        ExcavationMessages.SPELUNKER_RANGE,
        trusted("range", getScanRadius(level))
    ));
    v.addLore(C.YELLOW + AdaptLanguage.text(ExcavationMessages.SPELUNKER_LORE3));
  }

  @Handle(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void on(PlayerToggleSneakEvent e) {
    if (!e.isSneaking()) {
      return;
    }

    PlayerRef p = e.player();
    if (!hasGlowberries(p) || !hasOreInOffhand(p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    long cooldownMillis = (long) (1000 * getConfig().cooldown);
    if (!cooldowns.isReady(p.uuid(), cooldownMillis)) {
      fx(p.eyeLocation(), FxPriority.TRANSITION)
          .particle(Particles.SMOKE, 2, 0, 0, 0, 0.1D, 0.01D)
          .sound(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.5f, 1.0f);
      return;
    }

    if (activeScans.containsKey(p.uuid())) {
      return;
    }

    int radius = Math.max(1, (int) Math.floor(getScanRadius(level) * preference(p, RANGE).multiplier()));
    Loc origin = p.location();
    Id targetOre = p.inventory().offHand().type();
    if (!preference(p, ORES).allows(targetOre)) {
      return;
    }
    startScan(p, origin, targetOre, radius);
    cooldowns.mark(p.uuid(), cooldownMillis);
  }

  private boolean hasGlowberries(PlayerRef player) {
    return player.inventory().mainHand().type().equals(GLOW_BERRIES);
  }

  private boolean consumeGlowberry(PlayerRef player) {
    ItemRef berries = player.inventory().mainHand();
    return payItemCost(player, "glowberry", AdaptCore.platform().host().items().create(GLOW_BERRIES), 1, () -> {
      berries.setAmount(berries.amount() - 1);
      player.inventory().setMainHand(berries);
      return true;
    });
  }

  private boolean hasOreInOffhand(PlayerRef player) {
    Id offhandType = player.inventory().offHand().type();
    return offhandType.isMinecraft() && ORE_TYPES.contains(offhandType.path());
  }

  private void startScan(PlayerRef p, Loc origin, Id targetOre, int radius) {
    timeline(origin)
        .duration(5)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          fx.ring(Particles.END_ROD, 0.5D + (2.0D * progress), 16, 1.0D);
          if (tick == 0) {
            fx.chord(Sounds.BLOCK_SCULK_SENSOR_CLICKING, 0.6f, 1.0f, Sounds.BLOCK_BEACON_ACTIVATE, 0.5f, 1.4f);
          }
        })
        .start();

    int limit = Math.min(MAX_HIGHLIGHTS_PER_ACTIVATION, Math.max(1, getConfig().maxHighlights));
    int checks = Math.min(MAX_BLOCK_CHECKS_PER_ACTIVATION, Math.max(1, getConfig().maxBlockChecks));
    BlockScanScheduler.ScanRequest request = BlockScanScheduler.ScanRequest.builder(origin)
        .radius(radius)
        .denseRadius(getConfig().denseScanRadius)
        .maxSamples(checks)
        .maxResults(limit)
        .seed(ThreadLocalRandom.current().nextInt())
        .matcher((Id type) -> type.equals(targetOre))
        .completion((BlockScanScheduler.ScanResult result) -> completeScan(p, targetOre, result))
        .build();
    UUID scanId = BlockScanScheduler.submit(this, p.uuid(), request);
    activeScans.put(p.uuid(), scanId);
  }

  private void completeScan(PlayerRef p, Id targetOre, BlockScanScheduler.ScanResult result) {
    UUID playerId = p.uuid();
    if (!result.scanId().equals(activeScans.get(playerId))) {
      return;
    }

    boolean scheduled = Tasks.runEntity(p, () -> {
      if (!activeScans.remove(playerId, result.scanId()) || !p.isOnline()) {
        return;
      }

      List<BlockScanScheduler.Match> matches = result.matches();
      if (matches.isEmpty()) {
        showScanFailure(p);
        return;
      }
      if (!hasGlowberries(p) || !p.inventory().offHand().type().equals(targetOre)) {
        showScanFailure(p);
        return;
      }

      if (!consumeGlowberry(p)) {
        return;
      }

      Rgb color = markerColor(targetOre);
      for (BlockScanScheduler.Match match : matches) {
        addStat(p, "excavation.spelunker.ores-revealed", 1);
        showOreMarker(p, result.world(), match, targetOre, color);
      }
    });
    if (!scheduled) {
      activeScans.remove(playerId, result.scanId());
    }
  }

  private void showScanFailure(PlayerRef p) {
    fx(p.eyeLocation(), FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 4, 0, 0, 0, 0.12D, 0.01D)
        .sound(Sounds.BLOCK_NOTE_BLOCK_HAT, 0.4f, 0.65f);
  }

  private void showOreMarker(PlayerRef p, WorldRef world, BlockScanScheduler.Match match, Id targetOre,
                             Rgb color) {
    Loc markerLocation = new Loc(world, match.x(), match.y(), match.z());
    Tasks.runAt(markerLocation, () -> showOreMarkerAtRegion(p, world, match, markerLocation, targetOre, color));
  }

  private void showOreMarkerAtRegion(PlayerRef p, WorldRef world, BlockScanScheduler.Match match,
                                     Loc markerLocation, Id targetOre, Rgb color) {
    if (!acceptingMarkers.get()) {
      return;
    }
    if (!world.isChunkLoaded(match.x() >> 4, match.z() >> 4)) {
      return;
    }
    if (!world.blockAt(match.x(), match.y(), match.z()).type().equals(targetOre)) {
      return;
    }
    BlockStateRef block = AdaptCore.platform().host().items().blockState(targetOre);
    if (block == null) {
      return;
    }

    BlockDisplayRef marker;
    try {
      marker = world.spawnBlockDisplay(markerLocation, block, (BlockDisplayRef display) -> {
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setGravity(false);
        display.setSilent(true);
        display.setVisibleByDefault(false);
        display.setViewRange((float) Math.max(0.5D, Math.min(2D, getConfig().displayViewRange)));
        display.setShadowRadius(0f);
        display.setBrightness(new Brightness(15, 15));
        display.setBlock(block);
        display.setGlowColorOverride(preference(p, COLOR).color(color));
        display.setGlowing(true);
      });
    } catch (Throwable error) {
      AdaptLog.verbose("Failed to spawn glowing marker for Spelunker: "
          + error.getClass().getSimpleName()
          + (error.getMessage() == null ? "" : " - " + error.getMessage()));
      AdaptLog.error(error);
      return;
    }
    if (marker == null) {
      return;
    }

    UUID markerId = marker.uuid();
    DisplayHandle handle = new DisplayHandle(marker, markerLocation, p.uuid());
    synchronized (markerLifecycleLock) {
      if (!acceptingMarkers.get()) {
        removeDisplayOwned(marker);
        return;
      }
      activeMarkers.put(markerId, handle);
    }
    if (!Tasks.runEntity(p, () -> showMarkerToPlayer(p, markerId, marker))) {
      removeMarker(markerId);
      return;
    }
    if (!Tasks.runEntity(marker, () -> removeMarkerOwned(markerId, marker), getMarkerDurationTicks())) {
      removeMarker(markerId);
    }
  }

  private void showMarkerToPlayer(PlayerRef player, UUID markerId, BlockDisplayRef marker) {
    if (!player.isOnline() || !isPlayerEnabled(player) || !activeMarkers.containsKey(markerId)) {
      removeMarker(markerId);
      return;
    }
    AdaptCore.platform().viewers().showEntity(player, marker);
  }

  private void removeMarker(UUID markerId) {
    DisplayHandle handle = activeMarkers.remove(markerId);
    if (handle == null) {
      return;
    }
    if (!Tasks.runEntity(handle.display(), () -> removeDisplayOwned(handle.display()))) {
      Tasks.runAt(handle.anchor(), () -> removeDisplayOwned(handle.display()));
    }
  }

  private void removeMarkerOwned(UUID markerId, BlockDisplayRef marker) {
    activeMarkers.remove(markerId);
    removeDisplayOwned(marker);
  }

  private void removeDisplayOwned(BlockDisplayRef marker) {
    if (marker.isValid()) {
      marker.remove();
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityRemoveEvent e) {
    if (e.entity().type().equals(BLOCK_DISPLAY)) {
      activeMarkers.remove(e.entity().uuid());
    }
  }

  private void clearMarkers() {
    List<DisplayHandle> handles;
    synchronized (markerLifecycleLock) {
      handles = List.copyOf(activeMarkers.values());
      activeMarkers.clear();
    }
    for (DisplayHandle handle : handles) {
      if (!Tasks.runEntity(handle.display(), () -> removeDisplayOwned(handle.display()))) {
        Tasks.runAt(handle.anchor(), () -> removeDisplayOwned(handle.display()));
      }
    }
  }

  private int getMarkerDurationTicks() {
    return Math.max(20, Math.min(20 * 30, getConfig().highlightDurationTicks));
  }

  static Rgb markerColor(Id type) {
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
    return new Rgb(255, 255, 255);
  }

  @Handle(ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    UUID playerId = e.player().uuid();
    activeScans.remove(playerId);
    BlockScanScheduler.cancel(this, playerId);
  }

  @Override
  public void unregister() {
    acceptingMarkers.set(false);
    super.unregister();
    activeScans.clear();
    BlockScanScheduler.cancelOwner(this);
    clearMarkers();
  }


  private int getScanRadius(int level) {
    long configuredRadius = (long) getConfig().rangeMultiplier * level;
    return (int) Math.min(MAX_SCAN_RADIUS, Math.max(1L, configuredRadius));
  }

  @ConfigDescription("See ores through the ground using Glowberries in your main hand.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown for the Excavation Spelunker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldown = 6.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Range Multiplier for the Excavation Spelunker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int rangeMultiplier = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum world block checks made by one Spelunker activation.", impact = "Higher values improve target detection but increase total budgeted scan work; values above the hard safety cap are clamped.")
    int maxBlockChecks = 8192;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Radius searched completely before the remaining Spelunker budget is spread across the full range.", impact = "Higher values prioritize nearby accuracy; lower values reserve more samples for distant target ore.")
    int denseScanRadius = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum ore markers created by one Spelunker scan.", impact = "Higher values reveal more ore at once but create more temporary entities and effects.")
    int maxHighlights = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration in ticks that glowing ore displays remain visible.", impact = "Higher values keep ore outlines visible longer and retain temporary display entities longer.")
    int highlightDurationTicks = 100;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Client view-range multiplier for glowing ore displays.", impact = "Higher values allow markers to render farther away and values are clamped between 0.5 and 2.0.")
    double displayViewRange = 1.0;

    public Config() {
      baseCost = 5;
      costFactor = 1;
      initialCost = 10;
    }
  }

  private record DisplayHandle(BlockDisplayRef display, Loc anchor, UUID owner) {
  }
}
