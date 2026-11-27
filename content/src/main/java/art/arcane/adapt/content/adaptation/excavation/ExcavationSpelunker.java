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
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.api.world.BlockScan;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class ExcavationSpelunker extends SimpleAdaptation<ExcavationSpelunker.Config> {
  public static final Id ID = Id.adapt("excavation/spelunker");
  public static final PlayerPreference<ExcavationPreferences.Palette> COLOR = ExcavationPreferences.palette("color", Text.key("adapt.excavation.preferences.excavationspelunker.color"));
  public static final PlayerPreference<ExcavationPreferences.Ores> ORES = ExcavationPreferences.ores("ores", Text.key("adapt.excavation.preferences.excavationspelunker.ores"));
  public static final PlayerPreference<CommonPreferences.Scale> RANGE = CommonPreferences.scale("range", Text.key("adapt.excavation.preferences.excavationspelunker.range"));

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
  private final ExcavationOreMarkers markers = new ExcavationOreMarkers(this, this::isPlayerEnabled);

  public ExcavationSpelunker() {
    super(ID);
    registerConfiguration(ExcavationSpelunker.Config.class);
    setIcon(Id.minecraft("golden_helmet"));
    setInterval(20388);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("spyglass"))
        .key("challenge_excavation_spelunker_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_ore"))
            .key("challenge_excavation_spelunker_25k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_excavation_spelunker_1k", "excavation.spelunker.ores-revealed", 1000, 400);
    registerMilestone("challenge_excavation_spelunker_25k", "excavation.spelunker.ores-revealed", 25000, 1500);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    UUID playerId = player.player().uuid();
    activeScans.remove(playerId);
    BlockScan.cancel(this, playerId);
    markers.clearFor(playerId);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, COLOR, ORES, RANGE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + Text.key("adapt.excavation.spelunker.lore1").resolve());
    v.addLore(TextFormat.YELLOW + Text.key("adapt.excavation.spelunker.range").withMarkup("range", String.valueOf(getScanRadius(level))).resolve());
    v.addLore(TextFormat.YELLOW + Text.key("adapt.excavation.spelunker.lore3").resolve());
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
    return payItemCost(player, "glowberry", AdaptApi.platform().host().items().create(GLOW_BERRIES), 1, () -> {
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
    BlockScan request = BlockScan.around(origin)
        .radius(radius)
        .denseRadius(getConfig().denseScanRadius)
        .maxSamples(checks)
        .maxResults(limit)
        .matching((Id type) -> type.equals(targetOre))
        .onComplete((BlockScan.Result result) -> completeScan(p, targetOre, result))
        .build();
    UUID scanId = request.submit(this, p.uuid());
    activeScans.put(p.uuid(), scanId);
  }

  private void completeScan(PlayerRef p, Id targetOre, BlockScan.Result result) {
    UUID playerId = p.uuid();
    if (!result.scan().equals(activeScans.get(playerId))) {
      return;
    }

    boolean scheduled = AdaptApi.scheduler().runEntity(p, () -> {
      if (!activeScans.remove(playerId, result.scan()) || !p.isOnline()) {
        return;
      }

      List<BlockScan.Match> matches = result.matches();
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

      ExcavationOreMarkers.Style style = new ExcavationOreMarkers.Style(
          preference(p, COLOR).color(markerColor(targetOre)),
          (float) Math.max(0.5D, Math.min(2D, getConfig().displayViewRange)), getMarkerDurationTicks(), true);
      for (BlockScan.Match match : matches) {
        addStat(p, "excavation.spelunker.ores-revealed", 1);
        markers.show(p, result.world(), match, style);
      }
    });
    if (!scheduled) {
      activeScans.remove(playerId, result.scan());
    }
  }

  private void showScanFailure(PlayerRef p) {
    fx(p.eyeLocation(), FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 4, 0, 0, 0, 0.12D, 0.01D)
        .sound(Sounds.BLOCK_NOTE_BLOCK_HAT, 0.4f, 0.65f);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityRemoveEvent e) {
    if (e.entity().type().equals(BLOCK_DISPLAY)) {
      markers.forget(e.entity().uuid());
    }
  }

  private int getMarkerDurationTicks() {
    return Math.max(20, Math.min(20 * 30, getConfig().highlightDurationTicks));
  }

  public static Rgb markerColor(Id type) {
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
    BlockScan.cancel(this, playerId);
  }

  @Override
  public void unregister() {
    markers.close();
    super.unregister();
    activeScans.clear();
    BlockScan.cancelAll(this);
  }


  private int getScanRadius(int level) {
    long configuredRadius = (long) getConfig().rangeMultiplier * level;
    return (int) Math.min(MAX_SCAN_RADIUS, Math.max(1L, configuredRadius));
  }

  @ConfigDescription("See ores through the ground using Glowberries in your main hand.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Cooldown for the Excavation Spelunker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldown = 6.0;
    @ConfigDoc(value = "Controls Range Multiplier for the Excavation Spelunker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int rangeMultiplier = 5;
    @ConfigDoc(value = "Maximum world block checks made by one Spelunker activation.", impact = "Higher values improve target detection but increase total budgeted scan work; values above the hard safety cap are clamped.")
    int maxBlockChecks = 8192;
    @ConfigDoc(value = "Radius searched completely before the remaining Spelunker budget is spread across the full range.", impact = "Higher values prioritize nearby accuracy; lower values reserve more samples for distant target ore.")
    int denseScanRadius = 8;
    @ConfigDoc(value = "Maximum ore markers created by one Spelunker scan.", impact = "Higher values reveal more ore at once but create more temporary entities and effects.")
    int maxHighlights = 16;
    @ConfigDoc(value = "Duration in ticks that glowing ore displays remain visible.", impact = "Higher values keep ore outlines visible longer and retain temporary display entities longer.")
    int highlightDurationTicks = 100;
    @ConfigDoc(value = "Client view-range multiplier for glowing ore displays.", impact = "Higher values allow markers to render farther away and values are clamped between 0.5 and 2.0.")
    double displayViewRange = 1.0;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      initialCost = 3;
    }
  }
}
