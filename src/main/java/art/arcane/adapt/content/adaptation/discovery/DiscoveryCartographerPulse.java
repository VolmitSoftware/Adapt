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
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.ViewerDisplayDirector;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import org.bukkit.Location;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.generator.structure.StructureType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.StructureSearchResult;
import org.bukkit.util.Vector;

import java.util.UUID;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class DiscoveryCartographerPulse extends SimpleAdaptation<DiscoveryCartographerPulse.Config> {
  private static final int MAX_SEARCH_RADIUS_CHUNKS = 96;
  private final Cooldowns cooldowns = cooldowns();

  public DiscoveryCartographerPulse() {
    super("discovery-cartographer-pulse");
    registerConfiguration(Config.class);
    setIcon(Material.COMPASS);
    setInterval(2000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.COMPASS)
        .key("challenge_discovery_cartographer_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.FILLED_MAP)
            .key("challenge_discovery_cartographer_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_discovery_cartographer_100", "discovery.cartographer-pulse.pulses", 100, 300);
    registerMilestone("challenge_discovery_cartographer_1k", "discovery.cartographer-pulse.pulses", 1000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getSearchRange(level)), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), 2);
    if (getConfig().hungerCost > 0) {
      statLore(v, C.RED, "* ", getConfig().hungerCost, DiscoveryMessages.CARTOGRAPHER_PULSE_LORE_COST_HUNGER);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    if (e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
      return;
    }

    Player p = e.getPlayer();
    int level = getActiveLevel(p, Player::isSneaking);
    if (level <= 0 || p.getInventory().getItemInMainHand().getType() != Material.COMPASS) {
      return;
    }

    if (!cooldowns.isReady(p.getUniqueId(), getCooldownMillis(level))) {
      fx(p.getLocation(), FxPriority.TRANSITION)
          .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05, 0.01)
          .sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.6F);
      return;
    }

    int hungerCost = Math.max(0, getConfig().hungerCost);
    if (hungerCost > 0 && p.getFoodLevel() < hungerCost) {
      fx(p.getLocation(), FxPriority.TRANSITION)
          .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05, 0.01)
          .sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.6F);
      return;
    }

    Location target = locateNearestStructure(p.getWorld(), p.getLocation(), getSearchRange(level));
    cooldowns.mark(p.getUniqueId());
    if (target == null) {
      fx(p.getLocation(), FxPriority.TRANSITION)
          .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05, 0.01)
          .sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.5F);
      ComponentMessenger.sendSection(p, C.GRAY + AdaptLanguage.text(
          DiscoveryMessages.CARTOGRAPHER_PULSE_NOT_FOUND,
          trusted("range", Form.f(getSearchRange(level)))
      ));
      return;
    }

    p.setCompassTarget(target);
    ComponentMessenger.sendSection(p, C.AQUA + AdaptLanguage.text(
        DiscoveryMessages.CARTOGRAPHER_PULSE_TARGET,
        trusted("x", C.WHITE + Form.f(target.getBlockX())),
        trusted("z", Form.f(target.getBlockZ()))
    ));
    if (hungerCost > 0) {
      p.setFoodLevel(Math.max(0, p.getFoodLevel() - hungerCost));
    }

    timeline(p)
        .duration(20)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(24)
        .frame((f, tick, progress) -> {
          f.ring(Particle.ELECTRIC_SPARK, 0.5D + (progress * 4.0D), 12, 0.1D);
          if (tick == 0) {
            f.chord(Sound.ITEM_LODESTONE_COMPASS_LOCK, 0.8F, 1.3F, Sound.BLOCK_BEACON_ACTIVATE, 0.4F, 1.4F);
          }
        })
        .start();

    Location eye = p.getEyeLocation();
    Vector direction = target.toVector().subtract(eye.toVector()).normalize();
    Location lineStart = eye.clone().add(direction.clone().multiply(0.65D));
    Location lineEnd = lineStart.clone().add(direction.multiply(8D));
    ViewerDisplayDirector.showLine(
        getName(),
        "compass-direction",
        p,
        lineStart,
        lineEnd,
        Material.CYAN_STAINED_GLASS.createBlockData(),
        Color.fromRGB(45, 220, 235),
        0.09D,
        80
    );

    xp(p, getConfig().xpPerPulse);
    addStat(p, "discovery.cartographer-pulse.pulses", 1);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    UUID playerId = event.getPlayer().getUniqueId();
    cooldowns.clear(playerId);
    ViewerDisplayDirector.clearViewer(getName(), playerId);
  }

  @Override
  public void unregister() {
    ViewerDisplayDirector.clearChannel(getName());
    super.unregister();
  }

  private Location locateNearestStructure(World world, Location from, int rangeBlocks) {
    int radiusChunks = Math.max(1, Math.min(MAX_SEARCH_RADIUS_CHUNKS, (int) Math.ceil(rangeBlocks / 16D)));
    StructureSearchResult result;
    try {
      result = world.locateNearestStructure(from, StructureType.JIGSAW, radiusChunks, false);
    } catch (Throwable t) {
      return null;
    }

    if (result == null || result.getLocation() == null || result.getLocation().getWorld() != world) {
      return null;
    }

    return result.getLocation();
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
