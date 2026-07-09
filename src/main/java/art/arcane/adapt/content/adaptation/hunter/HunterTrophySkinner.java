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

package art.arcane.adapt.content.adaptation.hunter;

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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

public class HunterTrophySkinner extends SimpleAdaptation<HunterTrophySkinner.Config> {
  public HunterTrophySkinner() {
    super("hunter-trophy-skinner");
    registerConfiguration(Config.class);
    setIcon(Material.ZOMBIE_HEAD);
    setInterval(2000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SKELETON_SKULL)
        .key("challenge_hunter_trophy_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ZOMBIE_HEAD)
            .key("challenge_hunter_trophy_heads_100")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_hunter_trophy_50", "hunter.trophy-skinner.trophies-collected", 50, 400);
    registerMilestone("challenge_hunter_trophy_heads_100", "hunter.trophy-skinner.heads-collected", 100, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getDropChance(level), 0), 1);
    statLore(v, Form.pc(getHeadChance(level), 0), 2);
    statLore(v, C.YELLOW, "* ", Form.f(getMinimumRange(level), 1), 3);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    Player killer = e.getEntity().getKiller();
    if (killer == null || !hasActiveAdaptation(killer) || e.getEntity() instanceof Player || !canDamageTarget(killer, e.getEntity())) {
      return;
    }

    int level = getActiveLevel(killer);
    PrecisionContext precision = readPrecisionContext(e, killer, level);
    if (!precision.precise()) {
      return;
    }

    Location corpse = e.getEntity().getLocation().add(0, e.getEntity().getHeight() * 0.5D, 0);
    float markPitch = precision.projectileKill() ? 2.0F : 1.4F;
    timeline(corpse)
        .duration(2)
        .priority(FxPriority.COMBAT)
        .cullRadius(32)
        .frame((f, tick, progress) -> {
          f.ring(Particles.CRIT_MAGIC, 0.6D - (0.5D * progress), 6, 0.0D);
          if (tick == 0) {
            f.chord(Sound.BLOCK_NOTE_BLOCK_PLING, 0.7F, markPitch, Sound.BLOCK_NOTE_BLOCK_HAT, 0.4F, 1.9F);
          }
        })
        .start();

    if (ThreadLocalRandom.current().nextDouble() > getDropChance(level)) {
      return;
    }

    ItemStack trophy = buildTrophyDrop(e.getEntityType(), level, precision.projectileKill());
    if (trophy != null) {
      e.getDrops().add(trophy);
      addStat(killer, "hunter.trophy-skinner.trophies-collected", 1);
      fx(corpse, FxPriority.COMBAT)
          .particle(Particles.VILLAGER_HAPPY, 8, 0, 0.2D, 0, 0.3D, 0)
          .chord(Sound.ENTITY_WOLF_SHAKE, 0.55F, 1.35F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.8F);
    }

    if (ThreadLocalRandom.current().nextDouble() <= getHeadChance(level)) {
      ItemStack head = buildHeadDrop(e.getEntityType());
      if (head != null) {
        e.getDrops().add(head);
        addStat(killer, "hunter.trophy-skinner.heads-collected", 1);
        timeline(corpse)
            .duration(3)
            .priority(FxPriority.TRANSITION)
            .cullRadius(32)
            .frame((f, tick, progress) -> {
              f.column(Particles.TOTEM, 12, 1.4D);
              f.particle(Particle.GLOW, 4, 0, 0.4D, 0, 0.3D, 0);
              if (tick == 0) {
                f.chord(Sound.ENTITY_PLAYER_LEVELUP, 0.4F, 1.2F, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6F, 1.5F);
              }
            })
            .start();
      }
    }

    xp(killer, getConfig().xpPerTrophy);
  }

  private PrecisionContext readPrecisionContext(EntityDeathEvent e, Player killer, int level) {
    boolean projectileKill = false;
    double range = 0;
    if (e.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent damageByEntity) {
      if (damageByEntity.getDamager() instanceof Projectile projectile && projectile.getShooter() == killer) {
        projectileKill = true;
      }
    }

    if (killer.getWorld() == e.getEntity().getWorld()) {
      range = killer.getLocation().distance(e.getEntity().getLocation());
    }

    boolean rangedPrecision = projectileKill && range >= getMinimumRange(level);
    boolean stealthPrecision = killer.isSneaking();
    return new PrecisionContext(projectileKill, rangedPrecision || stealthPrecision);
  }

  private ItemStack buildTrophyDrop(EntityType type, int level, boolean projectileKill) {
    Material material = switch (type) {
      case CREEPER -> Material.GUNPOWDER;
      case SKELETON, BOGGED, WITHER_SKELETON, STRAY -> Material.BONE;
      case ZOMBIE, ZOMBIFIED_PIGLIN, HUSK, DROWNED -> Material.ROTTEN_FLESH;
      case SPIDER, CAVE_SPIDER -> Material.STRING;
      case BLAZE -> Material.BLAZE_POWDER;
      case ENDERMAN -> Material.ENDER_PEARL;
      case WITCH -> Material.REDSTONE;
      case PIGLIN, PIGLIN_BRUTE, HOGLIN, ZOGLIN -> Material.PORKCHOP;
      default -> Material.LEATHER;
    };

    int amount = Math.max(1, (int) Math.round(getConfig().trophyAmountBase + (getLevelPercent(level) * getConfig().trophyAmountFactor)));
    if (projectileKill) {
      amount += 1;
    }

    return new ItemStack(material, Math.min(8, amount));
  }

  private ItemStack buildHeadDrop(EntityType type) {
    Material material = switch (type) {
      case CREEPER -> Material.CREEPER_HEAD;
      case SKELETON, STRAY, BOGGED -> Material.SKELETON_SKULL;
      case WITHER_SKELETON -> Material.WITHER_SKELETON_SKULL;
      case ZOMBIE, HUSK, DROWNED, ZOMBIFIED_PIGLIN -> Material.ZOMBIE_HEAD;
      case PIGLIN, PIGLIN_BRUTE -> Material.PIGLIN_HEAD;
      default -> null;
    };

    return material == null ? null : new ItemStack(material);
  }

  private double getDropChance(int level) {
    return Math.min(getConfig().maxDropChance, getConfig().dropChanceBase + (getLevelPercent(level) * getConfig().dropChanceFactor));
  }

  private double getHeadChance(int level) {
    return Math.min(getConfig().maxHeadChance, getConfig().headChanceBase + (getLevelPercent(level) * getConfig().headChanceFactor));
  }

  private double getMinimumRange(int level) {
    return Math.max(4, getConfig().minimumRangeBase - (getLevelPercent(level) * getConfig().minimumRangeFactor));
  }

  @Override
  public void onTick() {

  }

  @ConfigDescription("Precision kills can grant bonus trophy drops and occasional heads from elite targets.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Drop Chance Base for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double dropChanceBase = 0.14;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Drop Chance Factor for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double dropChanceFactor = 0.3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Maximum Drop Chance for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxDropChance = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Head Chance Base for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double headChanceBase = 0.015;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Head Chance Factor for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double headChanceFactor = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Maximum Head Chance for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxHeadChance = 0.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Trophy Amount Base for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double trophyAmountBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Trophy Amount Factor for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double trophyAmountFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Minimum Range Base for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minimumRangeBase = 18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Minimum Range Factor for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minimumRangeFactor = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls XP Per Trophy for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerTrophy = 16;

    public Config() {
      baseCost = 5;
      costFactor = 0.8;
      initialCost = 5;
    }
  }

  private record PrecisionContext(boolean projectileKill, boolean precise) {
  }
}
