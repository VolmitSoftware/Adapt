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

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.RegistryUtil;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ExcavationEarthMover extends SimpleAdaptation<ExcavationEarthMover.Config> {
  private static final PotionEffectType SLOWNESS = RegistryUtil.find(PotionEffectType.class, "slowness", "slow");
  private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

  public ExcavationEarthMover() {
    super("excavation-earth-mover");
    registerConfiguration(Config.class);
    setDescription(Localizer.dLocalize("excavation.earth_mover.description"));
    setDisplayName(Localizer.dLocalize("excavation.earth_mover.name"));
    setIcon(Material.DIRT);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    setInterval(3730);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.DIRT)
        .key("challenge_excavation_earthmover_250")
        .title(Localizer.dLocalize("advancement.challenge_excavation_earthmover_250.title"))
        .description(Localizer.dLocalize("advancement.challenge_excavation_earthmover_250.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_excavation_earthmover_250", "excavation.earth-mover.waves-unleashed", 250, 450);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Form.f(getRadius(level), 1) + C.GRAY + " " + Localizer.dLocalize("excavation.earth_mover.lore1"));
    v.addLore(C.GREEN + "+ " + Form.pc(getForce(level), 0) + C.GRAY + " " + Localizer.dLocalize("excavation.earth_mover.lore2"));
    v.addLore(C.GREEN + "+ " + Form.duration(getSlowTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("excavation.earth_mover.lore3"));
    v.addLore(C.YELLOW + "* " + Form.duration(getCooldownMillis(level), 1) + C.GRAY + " " + Localizer.dLocalize("excavation.earth_mover.lore4"));
    v.addLore(C.RED + "- " + getConfig().hungerCost + C.GRAY + " " + Localizer.dLocalize("excavation.earth_mover.lore5"));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    cooldowns.remove(e.getPlayer().getUniqueId());
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

    long now = System.currentTimeMillis();
    long nextReady = cooldowns.getOrDefault(p.getUniqueId(), 0L);
    if (now < nextReady) {
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
    cooldowns.put(p.getUniqueId(), now + getCooldownMillis(level));
    p.setFoodLevel(Math.max(0, p.getFoodLevel() - hungerCost));

    double radius = getRadius(level);
    double force = getForce(level);
    int slowTicks = getSlowTicks(level);
    int slowAmplifier = getSlowAmplifier(level);
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
      hit++;
    }

    renderWave(p, radius);
    SoundPlayer sp = SoundPlayer.of(p.getWorld());
    sp.play(p.getLocation(), Sound.BLOCK_ROOTED_DIRT_BREAK, 1.0f, 0.6f);
    sp.play(p.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.4f, 0.5f);
    getPlayer(p).getData().addStat("excavation.earth-mover.waves-unleashed", 1);
    if (hit > 0) {
      getPlayer(p).getData().addStat("excavation.earth-mover.mobs-launched", hit);
      xp(p, hit * getConfig().xpPerMobHit);
    }
  }

  private void renderWave(Player p, double radius) {
    if (!areParticlesEnabled()) {
      return;
    }

    World world = p.getWorld();
    Location base = p.getLocation().add(0, 0.2, 0);
    ItemStack dirt = new ItemStack(Material.DIRT);
    for (int pulse = 0; pulse < 3; pulse++) {
      double r = radius * ((pulse + 1) / 3.0);
      int points = 10 + (pulse * 6);
      J.runEntity(p, () -> {
        for (int i = 0; i < points; i++) {
          double angle = (Math.PI * 2 * i) / points;
          Location at = base.clone().add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
          world.spawnParticle(Particle.ITEM, at, 3, 0.12, 0.1, 0.12, 0.04, dirt);
          world.spawnParticle(Particle.CLOUD, at, 1, 0.05, 0.05, 0.05, 0.01);
        }
      }, pulse * 2);
    }
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

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }

  @NoArgsConstructor
  @ConfigDescription("Sneak-right-click the air with a shovel to fling a wave of earth that knocks back and slows hostile mobs.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 5;
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
  }
}
