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
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.fx.ViewerDisplayPort;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.DiscoveryMessages;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.UUID;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class DiscoveryCartographerPulse extends SimpleAdaptation<DiscoveryCartographerPulse.Config> {
  public static final PlayerPreference<DiscoveryPreferences.Structures> STRUCTURES = DiscoveryPreferences.structures("structures", DiscoveryMessages.PREFERENCE_DISCOVERYCARTOGRAPHERPULSE_STRUCTURES);
  public static final PlayerPreference<DiscoveryPreferences.Palette> COLOR = DiscoveryPreferences.palette("color", DiscoveryMessages.PREFERENCE_DISCOVERYCARTOGRAPHERPULSE_COLOR);
  public static final PlayerPreference<CommonPreferences.Toggle> LINE = CommonPreferences.toggle("direction-line", DiscoveryMessages.PREFERENCE_DISCOVERYCARTOGRAPHERPULSE_LINE, CommonPreferences.Toggle.ON);

  private static final int MAX_SEARCH_RADIUS_CHUNKS = 96;
  private static final Id COMPASS = Id.minecraft("compass");
  private static final Id LINE_BLOCK = Id.minecraft("cyan_stained_glass");
  private final Cooldowns cooldowns = cooldowns();

  public DiscoveryCartographerPulse() {
    super("discovery-cartographer-pulse");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("compass"));
    setInterval(2000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("compass"))
        .key("challenge_discovery_cartographer_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("filled_map"))
            .key("challenge_discovery_cartographer_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_discovery_cartographer_100", "discovery.cartographer-pulse.pulses", 100, 300);
    registerMilestone("challenge_discovery_cartographer_1k", "discovery.cartographer-pulse.pulses", 1000, 1000);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    displays().clearViewer(getName(), player.getPlayer().uuid());
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, STRUCTURES, COLOR, LINE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getSearchRange(level)), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), 2);
    if (getConfig().hungerCost > 0) {
      statLore(v, C.RED, "* ", getConfig().hungerCost, DiscoveryMessages.CARTOGRAPHER_PULSE_LORE_COST_HUNGER);
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    if (e.hand() != Hand.MAIN_HAND) {
      return;
    }

    InteractAction action = e.action();
    if (action != InteractAction.RIGHT_CLICK_AIR && action != InteractAction.RIGHT_CLICK_BLOCK) {
      return;
    }

    PlayerRef p = e.player();
    int level = getActiveLevel(p, PlayerRef::isSneaking);
    if (level <= 0 || !COMPASS.equals(p.inventory().mainHand().type())) {
      return;
    }

    if (!cooldowns.isReady(p.uuid(), getCooldownMillis(level))) {
      fx(p.location(), FxPriority.TRANSITION)
          .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05, 0.01)
          .sound(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.6F);
      return;
    }

    int hungerCost = Math.max(0, getConfig().hungerCost);
    if (hungerCost > 0 && p.foodLevel() < hungerCost) {
      fx(p.location(), FxPriority.TRANSITION)
          .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05, 0.01)
          .sound(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.6F);
      return;
    }

    Loc target = locateNearestStructure(p, p.world(), p.location(), getSearchRange(level));
    cooldowns.mark(p.uuid());
    if (target == null) {
      fx(p.location(), FxPriority.TRANSITION)
          .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05, 0.01)
          .sound(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.5F);
      p.sendMessage(C.GRAY + AdaptLanguage.text(
          DiscoveryMessages.CARTOGRAPHER_PULSE_NOT_FOUND,
          trusted("range", Form.f(getSearchRange(level)))
      ));
      return;
    }

    p.setCompassTarget(target);
    p.sendMessage(C.AQUA + AdaptLanguage.text(
        DiscoveryMessages.CARTOGRAPHER_PULSE_TARGET,
        trusted("x", C.WHITE + Form.f(target.blockX())),
        trusted("z", Form.f(target.blockZ()))
    ));
    if (hungerCost > 0) {
      p.setFoodLevel(Math.max(0, p.foodLevel() - hungerCost));
    }

    timeline(p)
        .duration(20)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(24)
        .frame((f, tick, progress) -> {
          f.ring(Particles.ELECTRIC_SPARK, 0.5D + (progress * 4.0D), 12, 0.1D);
          if (tick == 0) {
            f.chord(Sounds.ITEM_LODESTONE_COMPASS_LOCK, 0.8F, 1.3F, Sounds.BLOCK_BEACON_ACTIVATE, 0.4F, 1.4F);
          }
        })
        .start();

    Loc eye = p.eyeLocation();
    Vec3 direction = target.position().subtract(eye.position()).normalize();
    Loc lineStart = eye.add(direction.multiply(0.65D));
    Loc lineEnd = lineStart.add(direction.multiply(8D));
    if (preferenceEnabled(p, LINE)) {
    displays().showLine(
        getName(),
        "compass-direction",
        p,
        lineStart,
        lineEnd,
        AdaptCore.platform().host().items().blockState(LINE_BLOCK),
        preference(p, COLOR).color(new Rgb(45, 220, 235)),
        0.09D,
        80
    );

    }
    xp(p, getConfig().xpPerPulse);
    addStat(p, "discovery.cartographer-pulse.pulses", 1);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    UUID playerId = event.player().uuid();
    cooldowns.clear(playerId);
    displays().clearViewer(getName(), playerId);
  }

  @Override
  public void unregister() {
    displays().clearChannel(getName());
    super.unregister();
  }

  private Loc locateNearestStructure(PlayerRef player, WorldRef world, Loc from, int rangeBlocks) {
    int radiusChunks = Math.max(1, Math.min(MAX_SEARCH_RADIUS_CHUNKS, (int) Math.ceil(rangeBlocks / 16D)));
    try {
      return world.locateNearestStructure(from, preference(player, STRUCTURES).type() == null ? DiscoveryStructures.JIGSAW : preference(player, STRUCTURES).type(), radiusChunks, false);
    } catch (Throwable t) {
      return null;
    }
  }

  private static ViewerDisplayPort displays() {
    return AdaptPorts.get(ViewerDisplayPort.class, ViewerDisplayPort.NONE);
  }

  private int getSearchRange(int level) {
    return Math.max(128, (int) Math.round(getConfig().searchRangeBase + (getLevelPercent(level) * getConfig().searchRangeFactor)));
  }

  private long getCooldownMillis(int level) {
    return Math.max(1500L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }


  @ConfigDescription("Sneak-right-click with a compass to lock onto a nearby structure and show a private glowing direction line.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Search Range Base for the Discovery Cartographer Pulse adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double searchRangeBase = 640;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Search Range Factor for the Discovery Cartographer Pulse adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double searchRangeFactor = 768;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Discovery Cartographer Pulse adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisBase = 26000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Discovery Cartographer Pulse adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisFactor = 14000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Pulse for the Discovery Cartographer Pulse adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerPulse = 25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Food points consumed per compass pulse.", impact = "Higher values make each pulse cost more hunger; 0 disables the cost.")
    int hungerCost = 2;

    public Config() {
      costFactor = 0.7;
      maxLevel = 4;
      initialCost = 4;
    }
  }
}
