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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.adapt.util.reflect.registries.RegistryUtil;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class ExcavationEarthMover extends SimpleAdaptation<ExcavationEarthMover.Config> {
  private static final PotionEffectType SLOWNESS = RegistryUtil.find(PotionEffectType.class, "slowness", "slow");
  private final Cooldowns cooldowns = cooldowns();

  public ExcavationEarthMover() {
    super("excavation-earth-mover");
    registerConfiguration(Config.class);
    setIcon(Material.DIRT);
    setInterval(3730);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.DIRT)
        .key("challenge_excavation_earthmover_250")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_excavation_earthmover_250", "excavation.earth-mover.waves-unleashed", 250, 450);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRadius(level), 1), 1);
    statLore(v, Form.pc(getForce(level), 0), 2);
    statLore(v, Form.duration(getSlowTicks(level) * 50D, 1), 3);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), 4);
    statLore(v, C.RED, "- ", getConfig().hungerCost, 5);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
      return;
    }

    if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player p = e.getPlayer();
    if (!p.isSneaking()) {
      return;
    }

    if (!isShovel(p.getInventory().getItemInMainHand())) {
      return;
    }

    int hungerCost = getConfig().hungerCost;
    if (p.getFoodLevel() < hungerCost) {
      return;
    }

    art.arcane.adapt.api.adaptation.Adaptation.BlockActionContext context = resolveInteractContext(p, p.getLocation());
    if (context == null) {
      return;
    }

    int level = context.level();
    if (!cooldowns.isReady(p.getUniqueId(), getCooldownMillis(level))) {
      return;
    }

    cooldowns.mark(p.getUniqueId());
    p.setFoodLevel(Math.max(0, p.getFoodLevel() - hungerCost));

    double radius = getRadius(level);
    double force = getForce(level);
    int slowTicks = getSlowTicks(level);
    int slowAmplifier = getSlowAmplifier(level);
    BlockData dirtData = Material.DIRT.createBlockData();
    int hit = 0;
    for (Entity nearby : p.getNearbyEntities(radius, getConfig().verticalRange, radius)) {
      if (!(nearby instanceof Monster monster)) {
        continue;
      }

      if (!canDamageTarget(p, monster)) {
        continue;
      }

      Vector direction = monster.getLocation().toVector().subtract(p.getLocation().toVector());
      direction.setY(0);
      if (direction.lengthSquared() < 0.01) {
        direction = new Vector(1, 0, 0);
      }

      direction.normalize().multiply(force).setY(getConfig().liftVelocity);
      monster.setVelocity(direction);
      monster.addPotionEffect(new PotionEffect(SLOWNESS, slowTicks, slowAmplifier, false, true, true));
      if (hit < 8) {
        fx(monster, FxPriority.COMBAT)
            .particle(Particles.BLOCK_CRACK, 4, 0, 0.1D, 0, 0.2D, 0.05D, dirtData);
      }
      hit++;
    }

    renderWave(p.getLocation(), radius);
    addStat(p, "excavation.earth-mover.waves-unleashed", 1);
    if (hit > 0) {
      fx(p.getLocation(), FxPriority.COMBAT).sound(Sound.ENTITY_HOSTILE_BIG_FALL, 0.4f, 0.8f);
      addStat(p, "excavation.earth-mover.mobs-launched", hit);
      xp(p, hit * getConfig().xpPerMobHit);
    } else {
      fx(p.getLocation(), FxPriority.COMBAT).sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.5f);
    }
  }

  private void renderWave(Location center, double radius) {
    ItemStack dirt = new ItemStack(Material.DIRT);
    timeline(center)
        .duration(8)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(32)
        .frame((fx, tick, progress) -> {
          double r = 0.4D + ((radius - 0.4D) * progress);
          int points = 10 + (int) (progress * 14);
          fx.ring(Particles.ITEM_CRACK, r, points, 0.2D, dirt);
          fx.ring(Particle.CLOUD, r, Math.max(8, points / 2), 0.15D);
          if (tick == 0) {
            fx.chord(Sound.BLOCK_ROOTED_DIRT_BREAK, 1.0f, 0.6f, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.4f, 0.5f, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.5f, 0.6f);
          }
          if (tick == 7 && radius > 5.0D) {
            fx.particle(Particle.EXPLOSION, 1, 0, 0.3D, 0, 0, 0);
          }
        })
        .start();
  }

  private double getRadius(int level) {
    return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
  }

  private double getForce(int level) {
    return getConfig().forceBase + (getLevelPercent(level) * getConfig().forceFactor);
  }

  private int getSlowTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().slowTicksBase + (getLevelPercent(level) * getConfig().slowTicksFactor)));
  }

  private int getSlowAmplifier(int level) {
    return Math.max(0, (int) Math.round(getLevelPercent(level) * getConfig().slowAmplifierMax));
  }

  private long getCooldownMillis(int level) {
    return Math.max(1000L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }

  @Override
  public void onTick() {

  }

  @ConfigDescription("Sneak-right-click the air with a shovel to fling a wave of earth that knocks back and slows hostile mobs.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusBase = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Vertical Range for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double verticalRange = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Force Base for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double forceBase = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Force Factor for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double forceFactor = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Lift Velocity for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double liftVelocity = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Slow Ticks Base for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double slowTicksBase = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Slow Ticks Factor for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double slowTicksFactor = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Slow Amplifier Max for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double slowAmplifierMax = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisBase = 16000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisFactor = 8000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hunger points consumed per earth wave.", impact = "Higher values drain more food per wave; activation fails when food is below the cost. Set to 0 to disable the hunger cost.")
    int hungerCost = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Mob Hit for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerMobHit = 6;

    public Config() {
      baseCost = 6;
      costFactor = 0.8;
      initialCost = 7;
    }
  }
}
