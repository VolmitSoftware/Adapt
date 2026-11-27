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
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
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
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;
import java.util.UUID;

public class DiscoveryKeenEye extends SimpleAdaptation<DiscoveryKeenEye.Config> {
  public static final Id ID = Id.adapt("discovery/keen_eye");
  public static final PlayerPreference<DiscoveryPreferences.Palette> COLOR = DiscoveryPreferences.palette("color", Text.key("adapt.discovery.preferences.discoverykeeneye.color"));
  public static final PlayerPreference<CommonPreferences.Toggle> CHESTS = CommonPreferences.toggle("chests", Text.key("adapt.discovery.preferences.discoverykeeneye.chests"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> SPAWNERS = CommonPreferences.toggle("spawners", Text.key("adapt.discovery.preferences.discoverykeeneye.spawners"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Scale> RANGE = CommonPreferences.scale("range", Text.key("adapt.discovery.preferences.discoverykeeneye.range"));

  public static final Id TRIAL_SPAWNER = Id.minecraft("trial_spawner");
  public static final Rgb CHEST_COLOR = new Rgb(255, 205, 70);
  public static final Rgb SPAWNER_COLOR = new Rgb(120, 220, 255);

  private final Cooldowns scanThrottle = cooldowns();

  public DiscoveryKeenEye() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("ender_eye"));
    setInterval(1000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ender_eye"))
        .key("challenge_discovery_keeneye_250")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("chest"))
            .key("challenge_discovery_keeneye_2500")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_discovery_keeneye_250", "discovery.keen-eye.glimmers", 250, 300);
    registerMilestone("challenge_discovery_keeneye_2500", "discovery.keen-eye.glimmers", 2500, 1200);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    displays().clearViewer(getId().toString(), player.player().uuid());
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, COLOR, CHESTS, SPAWNERS, RANGE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(range(getLevelPercent(level), getConfig().rangeBase, getConfig().rangeFactor), 0), 1);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(glimmerDurationTicks(getLevelPercent(level), getConfig().glimmerDurationTicksBase, getConfig().glimmerDurationTicksFactor) * 50D, 1), 2);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    for (PlayerProfile adaptPlayer : learnedCandidates(now)) {
      PlayerRef player = adaptPlayer.player();
      if (player == null || !player.isOnline()) {
        continue;
      }

      UUID id = player.uuid();
      if (!scanThrottle.isReady(id, getConfig().scanIntervalMillis)) {
        continue;
      }

      scanThrottle.mark(id);
      AdaptApi.scheduler().runEntity(player, () -> scanOwned(player));
    }
  }

  private void scanOwned(PlayerRef player) {
    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }

    double range = range(getLevelPercent(level), getConfig().rangeBase, getConfig().rangeFactor) * preference(player, RANGE).multiplier();
    int glimmerTicks = glimmerDurationTicks(getLevelPercent(level), getConfig().glimmerDurationTicksBase, getConfig().glimmerDurationTicksFactor);
    List<BlockRef> seen = KeenEyeScan.scan(player, range, getConfig().viewConeCos, getConfig().maxHighlightsPerScan,
        (Id type) -> preferenceEnabled(player, KeenEyeScan.isSpawner(type) ? SPAWNERS : CHESTS));
    for (BlockRef state : seen) {
      glimmer(player, state, glimmerTicks);
    }
    int found = seen.size();
    if (found > 0) {
      addStat(player, "discovery.keen-eye.glimmers", found);
    }
  }

  private void glimmer(PlayerRef player, BlockRef state, int ticks) {
    Rgb color = preference(player, COLOR).color(KeenEyeScan.isSpawner(state.type()) ? SPAWNER_COLOR : CHEST_COLOR);
    KeenEyeScan.glimmer(this, player, state, color, ticks);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    UUID playerId = event.player().uuid();
    scanThrottle.clear(playerId);
    displays().clearViewer(getId().toString(), playerId);
  }

  @Override
  public void unregister() {
    displays().clearChannel(getId().toString());
    super.unregister();
  }

  private static ViewerDisplayPort displays() {
    return ViewerDisplayPort.current();
  }

  static double range(double levelPercent, double base, double factor) {
    return Math.max(4D, base + (levelPercent * factor));
  }

  static int glimmerDurationTicks(double levelPercent, double base, double factor) {
    return Math.max(6, (int) Math.round(base + (levelPercent * factor)));
  }


  @ConfigDescription("Chests and spawners in your line of sight briefly appear as private glowing block displays.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Range Base for the Discovery Keen Eye adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rangeBase = 10;
    @ConfigDoc(value = "Controls Range Factor for the Discovery Keen Eye adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rangeFactor = 14;
    @ConfigDoc(value = "Controls Glimmer Duration Ticks Base for the Discovery Keen Eye adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double glimmerDurationTicksBase = 12;
    @ConfigDoc(value = "Controls Glimmer Duration Ticks Factor for the Discovery Keen Eye adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double glimmerDurationTicksFactor = 28;
    @ConfigDoc(value = "Minimum forward-view alignment (cosine) required for a container to glimmer.", impact = "Higher values narrow the detection cone to what you look almost directly at.")
    double viewConeCos = 0.55;
    @ConfigDoc(value = "Maximum containers highlighted per scan, capped internally at 8.", impact = "Lower values reduce particle work in container-dense areas.")
    int maxHighlightsPerScan = 6;
    @ConfigDoc(value = "Milliseconds between line-of-sight scans for a single player.", impact = "Higher values scan less often and reduce ray tracing work.")
    long scanIntervalMillis = 1500;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 5;
      initialCost = 1;
    }
  }
}
