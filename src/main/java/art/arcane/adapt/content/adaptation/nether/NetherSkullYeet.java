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

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.util.UUID;

public class NetherSkullYeet extends SimpleAdaptation<NetherSkullYeet.Config> {

  private final Cooldowns cooldowns = cooldowns();

  public NetherSkullYeet() {
    super("nether-skull-toss");
    registerConfiguration(Config.class);
    setDescription(Localizer.dLocalize("nether.skull_toss.description1") + C.ITALIC + " " + Localizer.dLocalize("nether.skull_toss.description2") + " " + C.GRAY + Localizer.dLocalize("nether.skull_toss.description3"));
    setDisplayName(Localizer.dLocalize("nether.skull_toss.name"));
    setIcon(Material.WITHER_SKELETON_SKULL);
    setInterval(2314);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.WITHER_SKELETON_SKULL)
        .key("challenge_nether_skull_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.WITHER_SKELETON_SKULL)
        .key("challenge_nether_skull_kills_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.WITHER_SKELETON_SKULL)
        .key("challenge_nether_skull_long_bomb")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.HIDDEN)
        .build());
    registerMilestone("challenge_nether_skull_100", "nether.skull-yeet.skulls-thrown", 100, 300);
    registerMilestone("challenge_nether_skull_kills_50", "nether.skull-yeet.skull-kills", 50, 500);
  }

  @Override
  public void addStats(int level, Element v) {
    int chance = getConfig().getBaseCooldown() - getConfig().getLevelCooldown() * level;
    v.addLore(C.GREEN + String.valueOf(chance) + C.GRAY + " " + Localizer.dLocalize("nether.skull_toss.lore1"));
    v.addLore(C.GRAY + Localizer.dLocalize("nether.skull_toss.lore2") + C.DARK_GRAY + Localizer.dLocalize("nether.skull_toss.lore3") + C.GRAY + ", " + Localizer.dLocalize("nether.skull_toss.lore4"));
  }

  private int getCooldownDuration(Player p) {
    return (getConfig().getBaseCooldown() - getConfig().getLevelCooldown() * getLevel(p)) * 20;
  }

  @EventHandler
  public void onRightClick(PlayerInteractEvent e) {
    Player p = e.getPlayer();
    withAdaptedPlayer(p, e, () -> {
      if (e.useItemInHand() == Event.Result.DENY) {
        return;
      }

      if (e.getAction() != Action.LEFT_CLICK_AIR && e.getAction() != Action.LEFT_CLICK_BLOCK) {
        return;
      }
      if (e.getHand() != EquipmentSlot.HAND || e.getItem() == null || e.getMaterial() != Material.WITHER_SKELETON_SKULL) {
        return;
      }

      UUID id = p.getUniqueId();
      int cooldownDuration = getCooldownDuration(p);
      if (!cooldowns.isReady(id, cooldownDuration)) {
        fx(p.getEyeLocation(), FxPriority.TRANSITION)
            .particle(Particles.SMOKE, 3, 0D, 0D, 0D, 0.1D, 0.0D)
            .sound(Sound.BLOCK_CONDUIT_DEACTIVATE, 0.6F, 0.8F);
        return;
      }

      if (p.hasCooldown(p.getInventory().getItemInMainHand().getType())) {
        e.setCancelled(true);
        fx(p.getEyeLocation(), FxPriority.TRANSITION)
            .particle(Particles.SMOKE, 3, 0D, 0D, 0D, 0.1D, 0.0D)
            .sound(Sound.BLOCK_CONDUIT_DEACTIVATE, 0.6F, 0.8F);
        return;
      }

      p.setCooldown(Material.WITHER_SKELETON_SKULL, cooldownDuration);

      if (p.getGameMode() != GameMode.CREATIVE) {
        e.getItem().setAmount(e.getItem().getAmount() - 1);
        cooldowns.mark(id);
      }

      Location eye = p.getEyeLocation();
      Vector dir = eye.getDirection();
      Location spawn = eye.clone().add(new Vector(.5, -.5, .5)).add(dir);
      WitherSkull skull = p.getWorld().spawn(spawn, WitherSkull.class, entity -> {
        entity.setRotation(eye.getYaw(), eye.getPitch());
        entity.setCharged(false);
        entity.setBounce(false);
        entity.setDirection(dir);
        entity.setShooter(p);
        xp(p, 100);
      });
      fx(spawn, FxPriority.GAMEPLAY)
          .trail(Particle.FLAME, dir.getX(), dir.getY(), dir.getZ(), 1.4D, 6)
          .trail(Particles.SMOKE, dir.getX(), dir.getY(), dir.getZ(), 1.1D, 6)
          .particle(Particle.SOUL, 4, 0D, 0D, 0D, 0.1D, 0.02D)
          .chord(Sound.ENTITY_WITHER_SHOOT, 1.0F, 1.0F, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.4F, 1.3F);
      timeline(skull)
          .duration(60)
          .priority(FxPriority.TRAIL)
          .cullRadius(48)
          .frame((f, tick, progress) -> f.particle(Particle.SOUL, 2, 0D, 0D, 0D, 0.02D, 0.0D)
              .particle(Particle.FLAME, 1, 0D, 0D, 0D, 0.02D, 0.0D))
          .start();
      addStat(p, "nether.skull-yeet.skulls-thrown", 1);
    });
  }

  @EventHandler
  public void onEntityDeath(EntityDeathEvent e) {
    LivingEntity dead = e.getEntity();
    if (dead.getLastDamageCause() instanceof EntityDamageByEntityEvent dbe
        && dbe.getDamager() instanceof WitherSkull skull
        && skull.getShooter() instanceof Player p) {
      withAdaptedPlayer(p, () -> {
        addStat(p, "nether.skull-yeet.skull-kills", 1);

        double distance = p.getLocation().distance(dead.getLocation());
        boolean longBomb = distance >= 40;
        Location at = dead.getLocation().add(0D, 0.5D, 0D);
        FxEmitter emit = fx(at, FxPriority.COMBAT)
            .particle(Particles.SMOKE, 8, 0D, 0D, 0D, 0.4D, 0.02D)
            .ring(Particle.SOUL, longBomb ? 2.5D : 1.5D, longBomb ? 20 : 12, 0.2D)
            .sound(Sound.ENTITY_WITHER_HURT, 0.6F, 1.1F);
        if (longBomb) {
          emit.ring(Particle.FLAME, 1.5D, 14, 0.2D)
              .particle(Particle.FLASH, 1, 0D, 0.5D, 0D, 0D, 0.0D)
              .chord(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.0F, Sound.ENTITY_WITHER_HURT, 0.3F, 1.8F);
          if (AdaptConfig.get().isAdvancements() && !getPlayer(p).getData().isGranted("challenge_nether_skull_long_bomb")) {
            getPlayer(p).getAdvancementHandler().grant("challenge_nether_skull_long_bomb");
          }
        }
      });
    }
  }



  @Getter
  @Setter
  @ConfigDescription("Throw Wither Skulls that explode on impact.")
  public static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Cooldown for the Nether Skull Yeet adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    private int baseCooldown = 15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Level Cooldown for the Nether Skull Yeet adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    private int levelCooldown = 5;

    public Config() {
      baseCost = 10;
      costFactor = 0.92;
      maxLevel = 3;
      initialCost = 5;
    }
  }
}
