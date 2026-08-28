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
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public class NetherAshwalker extends SimpleAdaptation<NetherAshwalker.Config> {
  public NetherAshwalker() {
    super("nether-ashwalker");
    registerConfiguration(Config.class);
    setIcon(Material.CAMPFIRE);
    setInterval(4000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.CAMPFIRE)
        .key("challenge_nether_ash_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.SOUL_CAMPFIRE)
            .key("challenge_nether_ash_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_nether_ash_200", "nether.ashwalker.damage-negated", 200, 300);
    registerMilestone("challenge_nether_ash_5k", "nether.ashwalker.damage-negated", 5000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, "100%", 1);
    statLore(v, coversCampfire(level, getConfig().campfireUnlockLevel) ? "100%" : "-", 2);
    statLore(v, coversSoulFire(level, getMaxLevel()) ? Form.pc(getConfig().soulFireReduction, 0) : "-", 3);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    EntityDamageEvent.DamageCause cause = e.getCause();
    if (cause == EntityDamageEvent.DamageCause.HOT_FLOOR) {
      negate(p, e, "magma");
      return;
    }

    if (isCampfireDamage(cause)) {
      if (coversCampfire(level, getConfig().campfireUnlockLevel)) {
        negate(p, e, "campfire");
      }
      return;
    }

    if (!isFireDamage(cause)) {
      return;
    }

    Material source = fireSource(e, p);
    if (source == null) {
      return;
    }

    if (isCampfire(source) && coversCampfire(level, getConfig().campfireUnlockLevel)) {
      negate(p, e, "campfire");
      return;
    }

    if (source == Material.SOUL_FIRE && coversSoulFire(level, getMaxLevel())) {
      reduceSoulFire(p, e);
    }
  }

  private void reduceSoulFire(Player p, EntityDamageEvent e) {
    double before = e.getDamage();
    double reduction = soulFireReduction(getConfig().soulFireReduction);
    e.setDamage(Math.max(0D, before * (1D - reduction)));
    p.setFireTicks(Math.min(p.getFireTicks(), Math.max(0, getConfig().soulFireMaxFireTicks)));
    double prevented = Math.max(0D, before - e.getDamage());
    int reduced = (int) Math.round(prevented);
    if (reduced > 0) {
      addStat(p, "nether.ashwalker.damage-negated", reduced);
    }
    xp(p, prevented * getConfig().xpPerNegatedDamage);
    fx(p.getLocation().add(0D, 1.0D, 0D), FxPriority.COMBAT)
        .particle(Particle.SOUL_FIRE_FLAME, 4, 0D, 0.3D, 0D, 0.2D, 0.0D)
        .particle(Particles.SMOKE, 3, 0D, 0.2D, 0D, 0.2D, 0.0D)
        .sound(Sound.BLOCK_SOUL_SAND_STEP, 0.4F, 0.8F);
  }

  private void negate(Player p, EntityDamageEvent e, String flavor) {
    double before = e.getDamage();
    fullyNegateDamage(e);
    p.setFireTicks(0);
    int prevented = (int) Math.round(Math.max(1D, before));
    addStat(p, "nether.ashwalker.damage-negated", prevented);
    xp(p, Math.max(before, 1D) * getConfig().xpPerNegatedDamage);
    float pitch = flavor.equals("magma") ? 1.4F : 1.0F;
    FxEmitter feedback = fx(p.getLocation(), FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 4, 0.2D, 0.1D, 0.2D, 0.2D, 0.01D)
        .particle(Particle.ASH, 3, 0.2D, 0.1D, 0.2D, 0.1D, 0.0D);
    float soundVolume = immunitySoundVolume(getConfig().immunitySoundVolume);
    if (soundVolume > 0F) {
      feedback.sound(Sound.BLOCK_FIRE_EXTINGUISH, soundVolume, pitch);
    }
  }

  static void fullyNegateDamage(EntityDamageEvent event) {
    event.setDamage(0D);
    event.setCancelled(true);
  }

  static float immunitySoundVolume(double configured) {
    if (!Double.isFinite(configured)) {
      return 0F;
    }
    return (float) Math.max(0D, Math.min(1D, configured));
  }

  static double soulFireReduction(double configured) {
    if (!Double.isFinite(configured)) {
      return 0D;
    }
    return Math.max(0D, Math.min(1D, configured));
  }

  private Material fireSource(EntityDamageEvent event, Player p) {
    if (event instanceof EntityDamageByBlockEvent byBlock && byBlock.getDamager() != null) {
      Material eventSource = classifyFireBlock(byBlock.getDamager());
      if (eventSource != null) {
        return eventSource;
      }
    }

    Location loc = p.getLocation();
    Block feet = loc.getBlock();
    Block below = loc.clone().add(0D, -1D, 0D).getBlock();
    Material fromFeet = classifyFireBlock(feet);
    if (fromFeet != null) {
      return fromFeet;
    }

    return classifyFireBlock(below);
  }

  private Material classifyFireBlock(Block b) {
    Material t = b.getType();
    if (t == Material.SOUL_FIRE) {
      return Material.SOUL_FIRE;
    }
    if ((t == Material.CAMPFIRE || t == Material.SOUL_CAMPFIRE) && b.getBlockData() instanceof Lightable lightable && lightable.isLit()) {
      return t;
    }

    return null;
  }

  static boolean isFireDamage(EntityDamageEvent.DamageCause cause) {
    return cause == EntityDamageEvent.DamageCause.FIRE || cause == EntityDamageEvent.DamageCause.FIRE_TICK;
  }

  static boolean isCampfire(Material material) {
    return material == Material.CAMPFIRE || material == Material.SOUL_CAMPFIRE;
  }

  static boolean coversCampfire(int level, int unlockLevel) {
    return level >= unlockLevel;
  }

  static boolean isCampfireDamage(EntityDamageEvent.DamageCause cause) {
    return cause == EntityDamageEvent.DamageCause.CAMPFIRE;
  }

  static boolean coversSoulFire(int level, int maxLevel) {
    return level >= maxLevel;
  }


  @ConfigDescription("Ignore magma-block and campfire burns, and shrug off most soul-fire damage at mastery.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Campfire Unlock Level for the Nether Ashwalker adaptation.", impact = "Higher values require more levels before campfire immunity activates.")
    int campfireUnlockLevel = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Soul Fire Reduction for the Nether Ashwalker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double soulFireReduction = 0.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Soul Fire Max Fire Ticks for the Nether Ashwalker adaptation.", impact = "Lower values shorten how long soul fire keeps you burning.")
    int soulFireMaxFireTicks = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Volume of Ashwalker's extinguish sound after fully cancelling magma or campfire damage.", impact = "Set to 0 to keep immunity silent, or raise up to 1 for audible proc feedback.")
    double immunitySoundVolume = 0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Negated Damage for the Nether Ashwalker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerNegatedDamage = 3;

    public Config() {
      baseCost = 3;
      costFactor = 0.7;
      maxLevel = 3;
      initialCost = 4;
    }
  }
}
