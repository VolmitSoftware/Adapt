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
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.AdaptationDamageTargets;
import art.arcane.adapt.api.adaptation.ItemCooldowns;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.NetherMessages;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LastDamage;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class NetherSkullYeet extends SimpleAdaptation<NetherSkullYeet.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("sneak", NetherMessages.NETHERSKULLYEET_PREFERENCE_SNEAK, CommonPreferences.Toggle.OFF);

  private static final Id WITHER_SKELETON_SKULL = Id.minecraft("wither_skeleton_skull");
  private static final Id WITHER_SKULL = Id.minecraft("wither_skull");

  /**
   * Skulls this adaptation threw, by entity id. Kept like plugin metadata: in memory only, kept across chunk
   * unloads, dropped once the skull leaves the world for good.
   */
  private static final Set<UUID> THROWN_SKULLS = ConcurrentHashMap.newKeySet();

  /**
   * The skull is both the ability trigger and the ammunition, so the sweep and
   * the gate share one state on the whole material.
   */
  private final ItemCooldowns cooldowns = ItemCooldowns.forMaterial(WITHER_SKELETON_SKULL).ownedBy(this);

  public NetherSkullYeet() {
    super("nether-skull-toss");
    registerConfiguration(Config.class);
    setIcon(WITHER_SKELETON_SKULL);
    setInterval(2314);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("wither_skeleton_skull"))
        .key("challenge_nether_skull_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("wither_skeleton_skull"))
        .key("challenge_nether_skull_kills_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("wither_skeleton_skull"))
        .key("challenge_nether_skull_long_bomb")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.HIDDEN)
        .build());
    registerMilestone("challenge_nether_skull_100", "nether.skull-yeet.skulls-thrown", 100, 300);
    registerMilestone("challenge_nether_skull_kills_50", "nether.skull-yeet.skull-kills", 50, 500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, SNEAK);
  }

  @Override
  public void addStats(int level, StatLines v) {
    int cooldown = cooldownSeconds(getConfig().getBaseCooldown(), getConfig().getLevelCooldown(), level);
    v.addLore(C.GREEN + String.valueOf(cooldown) + C.GRAY + " " + AdaptLanguage.text(NetherMessages.SKULL_TOSS_LORE1));
    v.addLore(C.GRAY + AdaptLanguage.text(NetherMessages.SKULL_TOSS_USAGE));
  }

  static int cooldownSeconds(int baseCooldown, int levelCooldown, int level) {
    return Math.max(1, baseCooldown - (levelCooldown * level));
  }

  private int getCooldownSeconds(PlayerRef p) {
    return cooldownSeconds(getConfig().getBaseCooldown(), getConfig().getLevelCooldown(), getLevel(p));
  }

  static boolean isThrownSkull(EntityRef entity) {
    return entity != null && THROWN_SKULLS.contains(entity.uuid());
  }

  @ReceiveCancelledEvents
  @Handle(ignoreCancelled = false)
  public void onRightClick(PlayerInteractEvent e) {
    PlayerRef p = e.player();
    if (preferenceEnabled(p, SNEAK) && !p.isSneaking()) {
      return;
    }
    withAdaptedPlayer(p, () -> {
      if (e.action() != InteractAction.RIGHT_CLICK_AIR && e.action() != InteractAction.RIGHT_CLICK_BLOCK) {
        return;
      }
      ItemRef item = e.item();
      if (e.hand() != Hand.MAIN_HAND || item == null || item.isEmpty() || !item.type().equals(WITHER_SKELETON_SKULL)) {
        return;
      }

      boolean vetoed = e.clickedBlock() != null
          ? e.useBlock() == UseResult.DENY
          : e.useItem() == UseResult.DENY;
      if (vetoed) {
        e.setCancelled(true);
        return;
      }

      long cooldownMillis = getCooldownSeconds(p) * 1000L;
      if (!cooldowns.isReady(p, cooldownMillis)) {
        e.setCancelled(true);
        fx(p.eyeLocation(), FxPriority.TRANSITION)
            .particle(Particles.SMOKE, 3, 0D, 0D, 0D, 0.1D, 0.0D)
            .sound(Sounds.BLOCK_CONDUIT_DEACTIVATE, 0.6F, 0.8F);
        return;
      }

      e.setCancelled(true);
      if (p.gameMode() != GameMode.CREATIVE
          && !payItemCost(p, "skull", AdaptCore.platform().host().items().create(WITHER_SKELETON_SKULL), 1, () -> {
            item.setAmount(item.amount() - 1);
            p.inventory().setMainHand(item);
            return true;
          })) {
        return;
      }

      cooldowns.mark(p, cooldownMillis);

      Loc eye = p.eyeLocation();
      Vec3 dir = eye.direction();
      Loc spawn = eye.add(.5, -.5, .5).add(dir);
      EntityRef skull = p.world().spawn(WITHER_SKULL, spawn, entity -> {
        if (entity instanceof ProjectileRef projectile) {
          projectile.setRotation(eye.yaw(), eye.pitch());
          projectile.setVelocity(dir.normalize());
          projectile.setShooter(p);
        }
        THROWN_SKULLS.add(entity.uuid());
        xp(p, 100);
      });
      fx(spawn, FxPriority.GAMEPLAY)
          .trail(Particles.FLAME, dir.x(), dir.y(), dir.z(), 1.4D, 6)
          .trail(Particles.SMOKE, dir.x(), dir.y(), dir.z(), 1.1D, 6)
          .particle(Particles.SOUL, 4, 0D, 0D, 0D, 0.1D, 0.02D)
          .chord(Sounds.ENTITY_WITHER_SHOOT, 1.0F, 1.0F, Sounds.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.4F, 1.3F);
      if (skull != null) {
        timeline(skull)
            .duration(60)
            .priority(FxPriority.TRAIL)
            .cullRadius(48)
            .frame((f, tick, progress) -> f.particle(Particles.SOUL, 2, 0D, 0D, 0D, 0.02D, 0.0D)
                .particle(Particles.FLAME, 1, 0D, 0D, 0D, 0.02D, 0.0D))
            .start();
      }
      addStat(p, "nether.skull-yeet.skulls-thrown", 1);
    });
  }

  @Handle(priority = EventPriority.LOWEST)
  public void protectPassiveMobs(EntityDamageByEntityEvent e) {
    if (e.damager().type().equals(WITHER_SKULL)
        && e.damager() instanceof ProjectileRef skull
        && isThrownSkull(skull)
        && skull.shooter() instanceof PlayerRef owner
        && (isProtectedFriendly(owner, e.entity())
        || (e.cause() == DamageCause.ENTITY_EXPLOSION
        && !AdaptationDamageTargets.allows(e.entity(), getConfig().isIgnorePassiveMobs())))) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void forgetSkull(EntityRemoveEvent e) {
    if (e.cause() != EntityRemoveEvent.Cause.UNLOAD && e.cause() != EntityRemoveEvent.Cause.CHANGED_DIMENSION
        && !THROWN_SKULLS.isEmpty()) {
      THROWN_SKULLS.remove(e.entity().uuid());
    }
  }

  @Handle(ignoreCancelled = false)
  public void onEntityDeath(EntityDeathEvent e) {
    if (e.platformCancelled()) {
      return;
    }
    LivingRef dead = e.entity();
    LastDamage last = dead.lastDamage();
    if (last != null
        && last.damager() != null
        && last.damager().type().equals(WITHER_SKULL)
        && last.damager() instanceof ProjectileRef skull
        && skull.shooter() instanceof PlayerRef p) {
      withAdaptedPlayer(p, () -> {
        addStat(p, "nether.skull-yeet.skull-kills", 1);

        boolean longBomb = isLongBomb(p.location(), dead.location(), 40D);
        Loc at = dead.location().add(0D, 0.5D, 0D);
        FxEmitter emit = fx(at, FxPriority.COMBAT)
            .particle(Particles.SMOKE, 8, 0D, 0D, 0D, 0.4D, 0.02D)
            .ring(Particles.SOUL, longBomb ? 2.5D : 1.5D, longBomb ? 20 : 12, 0.2D)
            .sound(Sounds.ENTITY_WITHER_HURT, 0.6F, 1.1F);
        if (longBomb) {
          emit.ring(Particles.FLAME, 1.5D, 14, 0.2D)
              .particle(Particles.FLASH, 1, 0D, 0.5D, 0D, 0D, 0.0D)
              .chord(Sounds.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.0F, Sounds.ENTITY_WITHER_HURT, 0.3F, 1.8F);
          if (AdaptConfig.get().isAdvancements() && !getPlayer(p).getData().isGranted("challenge_nether_skull_long_bomb")) {
            getPlayer(p).getAdvancementHandler().grant("challenge_nether_skull_long_bomb");
          }
        }
      });
    }
  }

  static boolean isLongBomb(Loc source, Loc target, double minimumDistance) {
    return source.world() != null
        && source.world().equals(target.world())
        && source.distanceSquared(target) >= minimumDistance * minimumDistance;
  }



  @Getter
  @Setter
  @ConfigDescription("Throw Wither Skulls that explode on impact.")
  public static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Prevent Skull Toss explosions from damaging passive and neutral mobs.", impact = "When enabled, direct skull impact damage is unchanged.")
    private boolean ignorePassiveMobs = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Cooldown for the Nether Skull Yeet adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    private int baseCooldown = 15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Level Cooldown for the Nether Skull Yeet adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    private int levelCooldown = 5;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      maxLevel = 3;
      initialCost = 3;
    }
  }
}
