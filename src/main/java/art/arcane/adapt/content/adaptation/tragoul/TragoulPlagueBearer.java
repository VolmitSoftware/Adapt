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

package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TragoulPlagueBearer extends SimpleAdaptation<TragoulPlagueBearer.Config> {
  private static final NamespacedKey PLAGUE_OWNER_KEY = NamespacedKey.fromString("adapt:tragoul_plague_owner");
  private static final NamespacedKey PLAGUE_GENERATION_KEY = NamespacedKey.fromString("adapt:tragoul_plague_generation");
  private static final NamespacedKey PLAGUE_STAMP_KEY = NamespacedKey.fromString("adapt:tragoul_plague_stamp");
  private static final Color PLAGUE_POISON = Color.fromRGB(90, 130, 30);
  private static final Color PLAGUE_WITHER = Color.fromRGB(30, 30, 30);

  public TragoulPlagueBearer() {
    super("tragoul-plague-bearer");
    registerConfiguration(Config.class);
    setIcon(Material.POISONOUS_POTATO);
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.POISONOUS_POTATO)
        .key("challenge_tragoul_plague_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.SPIDER_EYE)
            .key("challenge_tragoul_plague_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_tragoul_plague_100", "tragoul.plague-bearer.mobs-infected", 100, 400);
    registerMilestone("challenge_tragoul_plague_1k", "tragoul.plague-bearer.mobs-infected", 1000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("tragoul.plague_bearer.lore1"));
    v.addLore(C.GREEN + "+ " + Form.f(getSpreadRadius(level), 1) + C.GRAY + " " + Localizer.dLocalize("tragoul.plague_bearer.lore2"));
    v.addLore(C.GREEN + "+ " + Form.duration(getSpreadDurationTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("tragoul.plague_bearer.lore3"));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getEntity() instanceof Monster monster)) {
      return;
    }

    Player p = null;
    if (e.getDamager() instanceof Player player) {
      p = player;
    } else if (e.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
      p = shooter;
    }

    if (p == null) {
      return;
    }

    Player owner = p;
    withAdaptedPlayer(owner, e, () -> {
      if (getActiveLevel(owner) <= 0 || isProtectedFriendly(owner, monster)) {
        return;
      }

      UUID ownerId = owner.getUniqueId();
      if (markIfAfflicted(monster, ownerId)) {
        return;
      }

      J.runEntity(monster, () -> {
        if (monster.isValid() && !monster.isDead()) {
          markIfAfflicted(monster, ownerId);
        }
      }, 2);
    });
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    LivingEntity victim = e.getEntity();
    PersistentDataContainer pdc = victim.getPersistentDataContainer();
    String ownerRaw = pdc.get(PLAGUE_OWNER_KEY, PersistentDataType.STRING);
    if (ownerRaw == null) {
      return;
    }

    long now = System.currentTimeMillis();
    Long stamp = pdc.get(PLAGUE_STAMP_KEY, PersistentDataType.LONG);
    if (stamp == null || now - stamp > getConfig().afflictionFreshnessMillis) {
      return;
    }

    boolean withered = victim.hasPotionEffect(PotionEffectType.WITHER);
    if (!withered && !victim.hasPotionEffect(PotionEffectType.POISON)) {
      return;
    }

    int generation = pdc.getOrDefault(PLAGUE_GENERATION_KEY, PersistentDataType.INTEGER, 0);
    if (generation >= getConfig().maxGenerations) {
      fx(victim.getLocation().add(0, 0.8, 0), FxPriority.AMBIENT).burst(Particles.SMOKE, 2, 0.2);
      return;
    }

    Player owner = Bukkit.getPlayer(UUID.fromString(ownerRaw));
    if (owner == null || !owner.isOnline()) {
      return;
    }

    withAdaptedPlayer(owner, () -> {
      int level = getActiveLevel(owner);
      if (level <= 0) {
        return;
      }

      double radius = getSpreadRadius(level);
      int durationTicks = getSpreadDurationTicks(level);
      PotionEffectType effect = withered ? PotionEffectType.WITHER : PotionEffectType.POISON;
      List<Location> hosts = new ArrayList<>();
      int spread = 0;
      for (Entity entity : victim.getNearbyEntities(radius, radius, radius)) {
        if (!(entity instanceof Monster monster) || monster.isDead() || !monster.isValid()) {
          continue;
        }

        if (!canDamageTarget(owner, monster)) {
          continue;
        }

        PersistentDataContainer targetPdc = monster.getPersistentDataContainer();
        targetPdc.set(PLAGUE_OWNER_KEY, PersistentDataType.STRING, ownerRaw);
        targetPdc.set(PLAGUE_GENERATION_KEY, PersistentDataType.INTEGER, generation + 1);
        targetPdc.set(PLAGUE_STAMP_KEY, PersistentDataType.LONG, now);
        hosts.add(monster.getLocation());
        J.runEntity(monster, () -> {
          if (monster.isValid() && !monster.isDead()) {
            monster.addPotionEffect(new PotionEffect(effect, durationTicks, 0, true, true, true), true);
          }
        });
        spread++;
        if (spread >= getConfig().maxSpreadTargets) {
          break;
        }
      }

      if (spread <= 0) {
        return;
      }

      playSpread(victim.getLocation(), hosts, radius, withered);
      addStat(owner, "tragoul.plague-bearer.mobs-infected", spread);
      xp(owner, spread * getConfig().xpPerInfection);
    });
  }

  private void playSpread(Location corpse, List<Location> hosts, double radius, boolean withered) {
    FxEmitter emitter = fx(corpse.clone().add(0, 0.8, 0), FxPriority.GAMEPLAY);
    emitter.dustBurst(withered ? PLAGUE_WITHER : PLAGUE_POISON, 8, radius * 0.35, 1.2F);
    emitter.particle(Particle.SPORE_BLOSSOM_AIR, 6, 0, 0.6, 0, radius * 0.3, 0.02);
    for (Location host : hosts) {
      emitter.line(Particle.SPORE_BLOSSOM_AIR, host.getX(), host.getY() + 1.0, host.getZ(), 4);
    }
    emitter.chord(Sound.ENTITY_ZOMBIE_INFECT, 0.6F, 1.4F, Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.4F, 1.0F);
  }

  private boolean markIfAfflicted(Monster monster, UUID ownerId) {
    if (!monster.hasPotionEffect(PotionEffectType.POISON) && !monster.hasPotionEffect(PotionEffectType.WITHER)) {
      return false;
    }

    PersistentDataContainer pdc = monster.getPersistentDataContainer();
    boolean firstMark = !pdc.has(PLAGUE_OWNER_KEY, PersistentDataType.STRING);
    pdc.set(PLAGUE_OWNER_KEY, PersistentDataType.STRING, ownerId.toString());
    pdc.set(PLAGUE_STAMP_KEY, PersistentDataType.LONG, System.currentTimeMillis());
    if (!pdc.has(PLAGUE_GENERATION_KEY, PersistentDataType.INTEGER)) {
      pdc.set(PLAGUE_GENERATION_KEY, PersistentDataType.INTEGER, 0);
    }
    if (firstMark) {
      fx(monster.getLocation().add(0, 1.0, 0), FxPriority.AMBIENT)
          .particle(Particle.SPORE_BLOSSOM_AIR, 2, 0, 0, 0, 0.2, 0.01);
    }
    return true;
  }

  private double getSpreadRadius(int level) {
    return Math.max(1, getConfig().spreadRadiusBase + (getLevelPercent(level) * getConfig().spreadRadiusFactor));
  }

  private int getSpreadDurationTicks(int level) {
    return Math.max(40, (int) Math.round(getConfig().spreadDurationTicksBase + (getLevelPercent(level) * getConfig().spreadDurationTicksFactor)));
  }

  @Override
  public void onTick() {
  }

  @ConfigDescription("Mobs that die poisoned or withered by you spread the affliction to nearby hostile mobs.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Spread radius before level scaling.", impact = "Higher values infect hostile mobs further from the corpse.")
    double spreadRadiusBase = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional spread radius granted at max level.", impact = "Higher values increase level-scaled radius growth.")
    double spreadRadiusFactor = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Spread effect duration in ticks before level scaling.", impact = "Higher values keep infected mobs afflicted longer.")
    double spreadDurationTicksBase = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional spread effect duration ticks granted at max level.", impact = "Higher values increase level-scaled duration growth.")
    double spreadDurationTicksFactor = 120;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum plague generations a single affliction can chain through.", impact = "Caps chain length to prevent infinite plague cascades.")
    int maxGenerations = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum hostile mobs infected per death.", impact = "Caps per-death work to protect server performance.")
    int maxSpreadTargets = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Window in milliseconds during which an affliction mark is considered fresh at death.", impact = "Higher values let older poisons still spread on death.")
    long afflictionFreshnessMillis = 15000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per mob infected by the spread.", impact = "Higher values accelerate skill progression from this adaptation.")
    double xpPerInfection = 6;

    public Config() {
      costFactor = 0.72;
      initialCost = 4;
    }
  }
}
