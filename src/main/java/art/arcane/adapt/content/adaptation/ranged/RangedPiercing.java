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

package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.Vector;

public class RangedPiercing extends SimpleAdaptation<RangedPiercing.Config> {
  static final String PIERCE_HITS_META = "adapt-pierce-hits";
  static final String PIERCE_INITIALIZED_META = "adapt-piercing-initialized";

  public RangedPiercing() {
    super("ranged-piercing");
    registerConfiguration(Config.class);
    setLocalizationKey("ranged.arrow_piercing");
    setIcon(Material.FLETCHING_TABLE);
    setInterval(4791);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SPECTRAL_ARROW)
        .key("challenge_ranged_piercing_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SPECTRAL_ARROW)
        .key("challenge_ranged_piercing_4")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_ranged_piercing_500", "ranged.piercing.extra-hits", 500, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, level, 1);
  }

  @EventHandler
  public void on(ProjectileLaunchEvent e) {
    if (!(e.getEntity() instanceof AbstractArrow arrow)
        || isPiercingInitialized(arrow)
        || !(arrow.getShooter() instanceof Player player)) {
      return;
    }
    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }
    arrow.setPierceLevel(arrow.getPierceLevel() + level);
    arrow.setMetadata(
        PIERCE_INITIALIZED_META,
        new FixedMetadataValue(Adapt.instance, true)
    );
    xp(player, 5);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof AbstractArrow arrow)
        || !(arrow.getShooter() instanceof Player player)
        || !(e.getEntity() instanceof LivingEntity target)
        || !isPiercingInitialized(arrow)
        || arrow.getPierceLevel() <= 0
        || isProtectedFriendly(player, target)) {
      return;
    }

    int hits = readHits(arrow) + 1;
    arrow.setMetadata(PIERCE_HITS_META, new FixedMetadataValue(Adapt.instance, hits));

    if (hits > 1) {
      J.runEntity(player, () -> rewardPiercingHitOwned(player, hits));
      emitPierceSpark(target, arrow, hits);
    }
    if (hits >= 3) {
      fx(target, FxPriority.COMBAT)
          .column(Particles.ENCHANTMENT_TABLE, 8, 1.5D)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.5F);
    }
  }

  static int readHits(AbstractArrow arrow) {
    for (MetadataValue value : arrow.getMetadata(PIERCE_HITS_META)) {
      if (value.getOwningPlugin() == Adapt.instance) {
        return value.asInt();
      }
    }
    return 0;
  }

  static boolean isPiercingInitialized(AbstractArrow arrow) {
    for (MetadataValue value : arrow.getMetadata(PIERCE_INITIALIZED_META)) {
      if (value.getOwningPlugin() == Adapt.instance) {
        return value.asBoolean();
      }
    }
    return false;
  }

  static void copyPiercingState(AbstractArrow source, AbstractArrow target) {
    applyPiercingState(target, isPiercingInitialized(source), readHits(source));
  }

  static void applyPiercingState(AbstractArrow target, boolean initialized, int hits) {
    if (!initialized) {
      return;
    }
    target.setMetadata(
        PIERCE_INITIALIZED_META,
        new FixedMetadataValue(Adapt.instance, true)
    );
    if (hits > 0) {
      target.setMetadata(PIERCE_HITS_META, new FixedMetadataValue(Adapt.instance, hits));
    }
  }

  private void rewardPiercingHitOwned(Player player, int hits) {
    if (!player.isOnline()) {
      return;
    }
    addStat(player, "ranged.piercing.extra-hits", 1);
    if (hits >= 4 && grantOnce(player, "challenge_ranged_piercing_4")) {
      FxPresets.learnCelebration(this, player);
    }
  }

  private void emitPierceSpark(LivingEntity target, AbstractArrow arrow, int hits) {
    Location center = target.getLocation().add(0, target.getHeight() * 0.5D, 0);
    Vector velocity = arrow.getVelocity();
    float pitch = 1.0F + (Math.min(hits, 5) * 0.12F);
    if (velocity.lengthSquared() > 1.0E-6D) {
      Vector offset = velocity.clone().normalize().multiply(0.6D);
      Location entry = center.clone().subtract(offset);
      Location exit = center.clone().add(offset);
      fx(entry, FxPriority.COMBAT)
          .line(Particles.CRIT_MAGIC, exit.getX(), exit.getY(), exit.getZ(), 6)
          .dustBurst(Color.WHITE, 4, 0.2D, 0.4F)
          .sound(Sound.ENTITY_ARROW_HIT, 0.8F, pitch);
      return;
    }
    fx(center, FxPriority.COMBAT)
        .burst(Particles.CRIT_MAGIC, 6, 0.25D)
        .dustBurst(Color.WHITE, 4, 0.2D, 0.4F)
        .sound(Sound.ENTITY_ARROW_HIT, 0.8F, pitch);
  }


  @ConfigDescription("Projectiles pierce through multiple targets.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 3;
      costFactor = 0.5;
      initialCost = 8;
    }
  }
}
