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

package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class StealthSmokePellet extends SimpleAdaptation<StealthSmokePellet.Config> {
  private static final int PULSE_INTERVAL_TICKS = 10;
  private static final int BLIND_TICKS = PULSE_INTERVAL_TICKS + 30;
  private static final int MAX_AFFECTED_PER_PULSE = 24;

  private final Cooldowns cooldowns = cooldowns();
  private final NamespacedKey pelletKey;
  private final NamespacedKey radiusKey;
  private final NamespacedKey pulsesKey;

  public StealthSmokePellet() {
    super("stealth-smoke-pellet");
    registerConfiguration(Config.class);
    setIcon(Material.GUNPOWDER);
    pelletKey = new NamespacedKey(Adapt.instance, "smoke_pellet");
    radiusKey = new NamespacedKey(Adapt.instance, "smoke_pellet_radius");
    pulsesKey = new NamespacedKey(Adapt.instance, "smoke_pellet_pulses");
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GUNPOWDER)
        .key("challenge_stealth_smoke_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.TNT)
            .key("challenge_stealth_smoke_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_stealth_smoke_100", "stealth.smoke-pellet.thrown", 100, 400);
    registerMilestone("challenge_stealth_smoke_1k", "stealth.smoke-pellet.thrown", 1000, 1500);
  }

  static double computeRadius(double base, double factor, double maxRadius, double percent) {
    return Math.min(maxRadius, base + (percent * factor));
  }

  static int computePulses(double base, double factor, double percent) {
    return Math.max(1, (int) Math.round(base + (percent * factor)));
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRadius(level), 1), 1);
    statLore(v, Form.duration(getPulses(level) * PULSE_INTERVAL_TICKS * 50D, 1), 2);
  }

  @EventHandler
  public void on(PlayerInteractEvent e) {
    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
      return;
    }
    if (e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player p = e.getPlayer();
    if (!p.isSneaking() || !p.getInventory().getItemInMainHand().getType().isAir()) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (!cooldowns.isReady(p.getUniqueId(), getConfig().cooldownMillis)) {
      return;
    }

    if (!p.getInventory().containsAtLeast(new ItemStack(Material.GUNPOWDER), 1)) {
      fx(p.getEyeLocation(), FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 2, 0.12D)
          .sound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.4F, 0.6F);
      return;
    }

    p.getInventory().removeItem(new ItemStack(Material.GUNPOWDER, 1));
    cooldowns.mark(p.getUniqueId());
    throwPellet(p, level);
  }

  private void throwPellet(Player p, int level) {
    double radius = getRadius(level);
    int pulses = getPulses(level);
    Vector velocity = p.getEyeLocation().getDirection().normalize().multiply(getConfig().throwSpeed);
    Snowball ball = p.launchProjectile(Snowball.class, velocity);
    PersistentDataContainer pdc = ball.getPersistentDataContainer();
    pdc.set(pelletKey, PersistentDataType.BYTE, (byte) 1);
    pdc.set(radiusKey, PersistentDataType.DOUBLE, radius);
    pdc.set(pulsesKey, PersistentDataType.INTEGER, pulses);

    addStat(p, "stealth.smoke-pellet.thrown", 1);
    xp(p, getConfig().xpOnThrow);
    fx(p.getEyeLocation(), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 4, 0.15D)
        .sound(Sound.ENTITY_WITCH_THROW, 0.4F, 1.4F);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ProjectileHitEvent e) {
    if (!(e.getEntity() instanceof Snowball ball)) {
      return;
    }

    PersistentDataContainer pdc = ball.getPersistentDataContainer();
    if (!pdc.has(pelletKey, PersistentDataType.BYTE)) {
      return;
    }

    Double radius = pdc.get(radiusKey, PersistentDataType.DOUBLE);
    Integer pulses = pdc.get(pulsesKey, PersistentDataType.INTEGER);
    if (radius == null || pulses == null) {
      return;
    }

    Location impact = ball.getLocation().clone();
    double cloudRadius = radius;
    int cloudPulses = pulses;
    J.runAt(impact, () -> cloudPulse(impact, cloudRadius, cloudPulses));
  }

  private void cloudPulse(Location center, double radius, int pulsesLeft) {
    if (center.getWorld() == null || pulsesLeft <= 0) {
      return;
    }

    fx(center, FxPriority.GAMEPLAY)
        .particle(Particles.SMOKE, (int) Math.max(8, radius * 6), 0, 0, 0, radius * 0.55D, 0.01D)
        .particle(Particle.CAMPFIRE_COSY_SMOKE, (int) Math.max(2, radius), 0, 0, 0, radius * 0.5D, 0.005D);
    if (pulsesLeft % 2 == 0) {
      fx(center, FxPriority.AMBIENT).sound(Sound.BLOCK_FIRE_EXTINGUISH, 0.3F, 0.7F);
    }

    int budget = MAX_AFFECTED_PER_PULSE;
    for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
      if (!(entity instanceof Monster mob)) {
        continue;
      }
      J.runEntity(mob, () -> {
        mob.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, BLIND_TICKS, 0, false, false, false), true);
        mob.setTarget(null);
      });
      if (--budget <= 0) {
        break;
      }
    }

    J.runAt(center, () -> cloudPulse(center, radius, pulsesLeft - 1), PULSE_INTERVAL_TICKS);
  }

  private double getRadius(int level) {
    return computeRadius(getConfig().radiusBase, getConfig().radiusFactor, getConfig().radiusMax, getLevelPercent(level));
  }

  private int getPulses(int level) {
    return computePulses(getConfig().pulsesBase, getConfig().pulsesFactor, getLevelPercent(level));
  }

  @ConfigDescription("Sneak and flick an empty hand to throw a smoke pellet that blinds hostiles in a cloud; costs one gunpowder.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base cloud radius of the smoke pellet.", impact = "Higher values blind mobs across a wider area.")
    double radiusBase = 2.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra cloud radius gained across levels.", impact = "Higher values expand the cloud more per level.")
    double radiusFactor = 2.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum cloud radius after scaling.", impact = "Caps how large the smoke cloud can get.")
    double radiusMax = 6.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base number of blinding pulses in the cloud.", impact = "Higher values make the cloud last longer.")
    double pulsesBase = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra pulses gained across levels.", impact = "Higher values extend cloud duration more per level.")
    double pulsesFactor = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Launch speed of the thrown pellet.", impact = "Higher values throw the pellet farther and faster.")
    double throwSpeed = 1.3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown between pellet throws, in milliseconds.", impact = "Higher values mean longer waits between throws.")
    long cooldownMillis = 1500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Experience granted per pellet thrown.", impact = "Higher values level the adaptation faster.")
    double xpOnThrow = 10;

    public Config() {
      baseCost = 4;
      costFactor = 0.4;
      maxLevel = 3;
      initialCost = 4;
    }
  }
}
