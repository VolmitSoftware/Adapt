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

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.NetherMessages;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.adaptation.ItemCooldowns;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;


public class NetherSkullYeet extends SimpleAdaptation<NetherSkullYeet.Config> {

  /**
   * The skull is both the ability trigger and the ammunition, so the sweep and
   * the gate share one state on the whole material.
   */
  private final ItemCooldowns cooldowns = ItemCooldowns.forMaterial(Material.WITHER_SKELETON_SKULL);

  public NetherSkullYeet() {
    super("nether-skull-toss");
    registerConfiguration(Config.class);
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
    int cooldown = cooldownSeconds(getConfig().getBaseCooldown(), getConfig().getLevelCooldown(), level);
    v.addLore(C.GREEN + String.valueOf(cooldown) + C.GRAY + " " + AdaptLanguage.text(NetherMessages.SKULL_TOSS_LORE1));
    v.addLore(C.GRAY + AdaptLanguage.text(NetherMessages.SKULL_TOSS_USAGE));
  }

  static int cooldownSeconds(int baseCooldown, int levelCooldown, int level) {
    return Math.max(1, baseCooldown - (levelCooldown * level));
  }

  private int getCooldownSeconds(Player p) {
    return cooldownSeconds(getConfig().getBaseCooldown(), getConfig().getLevelCooldown(), getLevel(p));
  }

  @ReceiveCancelledEvents
  @EventHandler
  public void onRightClick(PlayerInteractEvent e) {
    Player p = e.getPlayer();
    withAdaptedPlayer(p, () -> {
      if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
        return;
      }
      if (e.getHand() != EquipmentSlot.HAND || e.getItem() == null || e.getMaterial() != Material.WITHER_SKELETON_SKULL) {
        return;
      }

      boolean vetoed = e.getClickedBlock() != null
          ? e.useInteractedBlock() == Event.Result.DENY
          : e.useItemInHand() == Event.Result.DENY;
      if (vetoed) {
        e.setCancelled(true);
        return;
      }

      long cooldownMillis = getCooldownSeconds(p) * 1000L;
      if (!cooldowns.isReady(p, cooldownMillis)) {
        e.setCancelled(true);
        fx(p.getEyeLocation(), FxPriority.TRANSITION)
            .particle(Particles.SMOKE, 3, 0D, 0D, 0D, 0.1D, 0.0D)
            .sound(Sound.BLOCK_CONDUIT_DEACTIVATE, 0.6F, 0.8F);
        return;
      }

      e.setCancelled(true);
      if (p.getGameMode() != GameMode.CREATIVE
          && !payItemCost(p, "skull", new ItemStack(Material.WITHER_SKELETON_SKULL), 1, () -> {
            e.getItem().setAmount(e.getItem().getAmount() - 1);
            return true;
          })) {
        return;
      }

      cooldowns.mark(p, cooldownMillis);

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

        boolean longBomb = isLongBomb(p.getLocation(), dead.getLocation(), 40D);
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

  static boolean isLongBomb(Location source, Location target, double minimumDistance) {
    return source.getWorld() != null
        && source.getWorld().equals(target.getWorld())
        && source.distanceSquared(target) >= minimumDistance * minimumDistance;
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
