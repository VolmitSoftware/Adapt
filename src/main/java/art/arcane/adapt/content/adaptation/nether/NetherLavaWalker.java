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

package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class NetherLavaWalker extends SimpleAdaptation<NetherLavaWalker.Config> {
  public NetherLavaWalker() {
    super("nether-lava-walker");
    registerConfiguration(Config.class);
    setIcon(Material.MAGMA_BLOCK);
    setInterval(1000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.MAGMA_BLOCK)
        .key("challenge_nether_lava_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_INGOT)
            .key("challenge_nether_lava_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_nether_lava_1k", "nether.lava-walker.blocks-walked", 1000, 300);
    registerMilestone("challenge_nether_lava_25k", "nether.lava-walker.blocks-walked", 25000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getStride(level), 0), 1);
    statLore(v, C.YELLOW, "* ", getHungerCost(level), 2);
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void on(PlayerMoveEvent e) {
    Player p = e.getPlayer();
    if (p.getWorld().getEnvironment() != World.Environment.NETHER) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      if (p.isFlying() || p.isGliding() || p.isInsideVehicle() || p.getFoodLevel() <= 0) {
        return;
      }

      Block feet = p.getLocation().getBlock();
      Block below = p.getLocation().clone().add(0, -1, 0).getBlock();
      if (!(isLava(feet) || isLava(below))) {
        return;
      }

      int level = getActiveLevel(p);
      if (getStorageLong(p, "lavaWalkerCooldown", 0L) > System.currentTimeMillis()) {
        return;
      }

      Vector velocity = p.getVelocity();
      Vector dir = p.getLocation().getDirection().setY(0).normalize().multiply(getStride(level));
      p.setVelocity(new Vector(dir.getX(), Math.max(0.16, velocity.getY()), dir.getZ()));
      p.setFallDistance(0);
      p.setFireTicks(0);
      boolean hadFireResistance = p.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE);
      p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, getConfig().fireResistTicks, 0, false, false));

      int hungerCost = getHungerCost(level);
      p.setFoodLevel(Math.max(0, p.getFoodLevel() - hungerCost));
      setStorage(p, "lavaWalkerCooldown", System.currentTimeMillis() + getCooldownMillis(level));
      xp(p, getConfig().xpPerStride);
      addStat(p, "nether.lava-walker.blocks-walked", 1);
      fx(p.getLocation(), FxPriority.TRAIL)
          .trail(Particle.LAVA, dir.getX(), 0.1D, dir.getZ(), 1.0D, 6)
          .trail(Particle.FLAME, dir.getX(), 0.1D, dir.getZ(), 0.8D, 4)
          .particle(Particles.SMOKE, 2, 0D, 0.1D, 0D, 0.15D, 0.02D)
          .chord(Sound.BLOCK_FIRE_EXTINGUISH, 0.25F, 1.4F, Sound.ENTITY_BLAZE_AMBIENT, 0.2F, 1.6F);
      if (!hadFireResistance) {
        fx(p.getLocation(), FxPriority.TRANSITION)
            .dustRing(Color.ORANGE, 0.7D, 8, 1.0F)
            .sound(Sound.BLOCK_FIRE_AMBIENT, 0.2F, 1.2F);
      }
    });
  }

  private boolean isLava(Block b) {
    return b.getType() == Material.LAVA;
  }

  private double getStride(int level) {
    return getConfig().strideBase + (getLevelPercent(level) * getConfig().strideFactor);
  }

  private int getHungerCost(int level) {
    return Math.max(1, (int) Math.round(getConfig().hungerCostBase - (getLevelPercent(level) * getConfig().hungerCostFactor)));
  }

  private long getCooldownMillis(int level) {
    return Math.max(100, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }


  @ConfigDescription("Stride over lava in the Nether at the cost of hunger.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stride Base for the Nether Lava Walker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double strideBase = 0.18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stride Factor for the Nether Lava Walker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double strideFactor = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Hunger Cost Base for the Nether Lava Walker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hungerCostBase = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Hunger Cost Factor for the Nether Lava Walker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hungerCostFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Nether Lava Walker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisBase = 900;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Nether Lava Walker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisFactor = 700;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fire Resist Ticks for the Nether Lava Walker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int fireResistTicks = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Stride for the Nether Lava Walker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerStride = 3.5;

    public Config() {
      costFactor = 0.75;
      initialCost = 4;
    }
  }
}
