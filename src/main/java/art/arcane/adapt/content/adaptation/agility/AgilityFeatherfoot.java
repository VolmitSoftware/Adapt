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
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class AgilityFeatherfoot extends SimpleAdaptation<AgilityFeatherfoot.Config> {
  private final Cooldowns fxThrottle = cooldowns();

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
    if (e.getAction() != Action.PHYSICAL || e.getClickedBlock() == null || !(e.getPlayer() instanceof Player p)) {
      return;
    }

    if (!p.isSprinting()) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    Block block = e.getClickedBlock();
    Material type = block.getType();
    if (type == Material.FARMLAND && level >= getConfig().farmlandMinLevel) {
      e.setCancelled(true);
      recordProtection(p, block);
      return;
    }

    if (Tag.PRESSURE_PLATES.isTagged(type) && level >= getConfig().pressurePlateMinLevel) {
      e.setCancelled(true);
      recordProtection(p, block);
    }
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p) || !p.isSprinting()) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    EntityDamageEvent.DamageCause cause = e.getCause();
    if (cause == EntityDamageEvent.DamageCause.CONTACT
        && level >= getConfig().berryBushMinLevel
        && touchingBerryBush(p)) {
      e.setCancelled(true);
      recordProtection(p, p.getLocation().getBlock());
      return;
    }

    if (cause == EntityDamageEvent.DamageCause.FREEZE && level >= getConfig().powderSnowMinLevel) {
      e.setCancelled(true);
      p.setFreezeTicks(0);
      recordProtection(p, p.getLocation().getBlock());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    Player p = e.getPlayer();
    if (!p.isSprinting() || p.getFreezeTicks() <= 0) {
      return;
    }

    int level = getActiveLevel(p);
    if (level < getConfig().powderSnowMinLevel) {
      return;
    }

    if (p.getLocation().getBlock().getType() == Material.POWDER_SNOW) {
      p.setFreezeTicks(0);
    }
  }

  private boolean touchingBerryBush(Player p) {
    Block feet = p.getLocation().getBlock();
    return feet.getType() == Material.SWEET_BERRY_BUSH
        || feet.getRelative(0, 1, 0).getType() == Material.SWEET_BERRY_BUSH;
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
    @ConfigDoc(value = "Minimum level at which sprinting ignores sweet-berry-bush damage.", impact = "Lower values unlock berry-bush immunity sooner.")
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
