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

package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.inventorygui.Element;
import io.papermc.paper.event.entity.EntityInsideBlockEvent;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;

import java.util.UUID;

public class AgilityFeatherfoot extends SimpleAdaptation<AgilityFeatherfoot.Config> {
  private static final long SPRINT_INTENT_GRACE_MILLIS = 350L;

  private final Cooldowns fxThrottle = cooldowns();
  private final Cooldowns surfaceContactThrottle = cooldowns();
  private final Cooldowns sprintIntent = cooldowns();

  public AgilityFeatherfoot() {
    super("agility-featherfoot");
    registerConfiguration(Config.class);
    setIcon(Material.RABBIT_FOOT);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.RABBIT_FOOT)
        .key("challenge_agility_featherfoot_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.LEATHER_BOOTS)
            .key("challenge_agility_featherfoot_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_agility_featherfoot_500", "agility.featherfoot.surfaces-ignored", 500, 300);
    registerMilestone("challenge_agility_featherfoot_5k", "agility.featherfoot.surfaces-ignored", 5000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, getSurfacesIgnored(level), 1);
    v.addLore(C.GREEN + " " + Localizer.dLocalize("agility.featherfoot.lore2"));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    if (e.getAction() != Action.PHYSICAL || e.getClickedBlock() == null) {
      return;
    }

    Player p = e.getPlayer();
    Block block = e.getClickedBlock();
    Material type = block.getType();
    boolean pressurePlate = Tag.PRESSURE_PLATES.isTagged(type);
    if (type != Material.FARMLAND && !pressurePlate) {
      return;
    }

    int level = getActiveLevel(p);
    if (!ignoresSurface(type, pressurePlate, hasSprintIntent(p), level, getConfig())) {
      return;
    }

    e.setUseInteractedBlock(Event.Result.DENY);
    recordSurfaceProtection(p, block);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityInsideBlockEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    Block block = e.getBlock();
    Material type = block.getType();
    boolean pressurePlate = Tag.PRESSURE_PLATES.isTagged(type);
    if (type != Material.SWEET_BERRY_BUSH && type != Material.POWDER_SNOW && !pressurePlate) {
      return;
    }

    int level = getActiveLevel(p);
    if (!ignoresSurface(type, pressurePlate, hasSprintIntent(p), level, getConfig())) {
      return;
    }

    e.setCancelled(true);
    if (type == Material.POWDER_SNOW) {
      p.setFreezeTicks(0);
    }
    recordSurfaceProtection(p, block);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerInputEvent e) {
    Player p = e.getPlayer();
    if (shouldTrackSprintIntent(e.getInput().isSprint(), getActiveLevel(p))) {
      sprintIntent.mark(p.getUniqueId());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerToggleSprintEvent e) {
    Player p = e.getPlayer();
    if (shouldTrackSprintIntent(e.isSprinting(), getActiveLevel(p))) {
      sprintIntent.mark(p.getUniqueId());
    }
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    UUID playerId = e.getPlayer().getUniqueId();
    sprintIntent.clear(playerId);
    surfaceContactThrottle.clear(playerId);
    fxThrottle.clear(playerId);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    Player p = e.getPlayer();
    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    rememberSprintIntent(p);
    if (p.getFreezeTicks() <= 0) {
      return;
    }

    Material type = p.getLocation().getBlock().getType();
    if (ignoresSurface(type, false, hasSprintIntent(p), level, getConfig())) {
      p.setFreezeTicks(0);
    }
  }

  private void rememberSprintIntent(Player p) {
    if (p.isSprinting() || p.getCurrentInput().isSprint()) {
      sprintIntent.mark(p.getUniqueId());
    }
  }

  private boolean hasSprintIntent(Player p) {
    boolean currentSprint = p.isSprinting() || p.getCurrentInput().isSprint();
    if (currentSprint) {
      sprintIntent.mark(p.getUniqueId());
    }
    return sprintIntentActive(currentSprint, sprintIntent.remaining(p.getUniqueId(), SPRINT_INTENT_GRACE_MILLIS));
  }

  static boolean sprintIntentActive(boolean currentSprint, long graceRemainingMillis) {
    return currentSprint || graceRemainingMillis > 0L;
  }

  static boolean shouldTrackSprintIntent(boolean sprinting, int level) {
    return sprinting && level > 0;
  }

  static boolean ignoresSurface(Material type, boolean pressurePlate, boolean sprinting, int level, Config config) {
    int minimumLevel = minimumLevelForSurface(type, pressurePlate, config);
    return sprinting && level > 0 && minimumLevel >= 0 && level >= minimumLevel;
  }

  static int minimumLevelForSurface(Material type, boolean pressurePlate, Config config) {
    if (type == Material.FARMLAND) {
      return config.farmlandMinLevel;
    }
    if (pressurePlate) {
      return config.pressurePlateMinLevel;
    }
    if (type == Material.SWEET_BERRY_BUSH) {
      return config.berryBushMinLevel;
    }
    if (type == Material.POWDER_SNOW) {
      return config.powderSnowMinLevel;
    }
    return -1;
  }

  private void recordSurfaceProtection(Player p, Block block) {
    UUID playerId = p.getUniqueId();
    if (!surfaceContactThrottle.isReady(playerId, 600L)) {
      return;
    }
    surfaceContactThrottle.mark(playerId);
    recordProtection(p, block);
  }

  private void recordProtection(Player p, Block block) {
    addStat(p, "agility.featherfoot.surfaces-ignored", 1);
    if (!fxThrottle.isReady(p.getUniqueId(), 600L)) {
      return;
    }

    fxThrottle.mark(p.getUniqueId());
    fx(block.getLocation().add(0.5D, 1.0D, 0.5D), FxPriority.AMBIENT)
        .particle(Particle.CLOUD, 3, 0, 0.05D, 0, 0.15D, 0.02D)
        .sound(Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.3F, 1.6F);
  }

  private int getSurfacesIgnored(int level) {
    int surfaces = 0;
    if (level >= getConfig().farmlandMinLevel) {
      surfaces++;
    }
    if (level >= getConfig().pressurePlateMinLevel) {
      surfaces++;
    }
    if (level >= getConfig().berryBushMinLevel) {
      surfaces++;
    }
    if (level >= getConfig().powderSnowMinLevel) {
      surfaces++;
    }
    return surfaces;
  }

  @ConfigDescription("Sprinting ignores farmland, pressure plates, sweet-berry snags, and eventually powder snow.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Minimum level at which sprinting stops trampling farmland.", impact = "Lower values unlock farmland protection sooner.")
    int farmlandMinLevel = 1;
    @ConfigDoc(value = "Minimum level at which sprinting stops triggering pressure plates.", impact = "Lower values unlock plate immunity sooner.")
    int pressurePlateMinLevel = 2;
    @ConfigDoc(value = "Minimum level at which sprinting ignores sweet-berry-bush slowdown and damage.", impact = "Lower values unlock berry-bush immunity sooner.")
    int berryBushMinLevel = 3;
    @ConfigDoc(value = "Minimum level at which sprinting shrugs off powder-snow freezing.", impact = "Lower values unlock powder-snow crossing sooner.")
    int powderSnowMinLevel = 4;

    public Config() {
      baseCost = 2;
      costFactor = 0.4;
      maxLevel = 4;
      initialCost = 3;
    }
  }
}
