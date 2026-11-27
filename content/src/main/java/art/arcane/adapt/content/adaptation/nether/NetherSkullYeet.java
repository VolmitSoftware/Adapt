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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.ReceiveCancelledEvents;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LastDamage;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

import java.util.List;


public class NetherSkullYeet extends SimpleAdaptation<NetherSkullYeet.Config> {
  public static final Id ID = Id.adapt("nether/skull_toss");
  public static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("sneak", Text.key("adapt.netherskullyeet.preferences.preference_sneak"), CommonPreferences.Toggle.OFF);

  private final NetherSkullToss toss = new NetherSkullToss(this);

  public NetherSkullYeet() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(NetherSkullToss.WITHER_SKELETON_SKULL);
    setInterval(2314);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("wither_skeleton_skull"))
        .key("challenge_nether_skull_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("wither_skeleton_skull"))
        .key("challenge_nether_skull_kills_50")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("wither_skeleton_skull"))
        .key("challenge_nether_skull_long_bomb")
        .frame(AdvancementFrame.CHALLENGE)
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
    v.addLore(TextFormat.GREEN + String.valueOf(cooldown) + TextFormat.GRAY + " " + Text.key("adapt.nether.skull_toss.lore1").resolve());
    v.addLore(TextFormat.GRAY + Text.key("adapt.nether.skull_toss.usage").resolve());
  }

  public static int cooldownSeconds(int baseCooldown, int levelCooldown, int level) {
    return Math.max(1, baseCooldown - (levelCooldown * level));
  }

  private int getCooldownSeconds(PlayerRef p) {
    return cooldownSeconds(getConfig().getBaseCooldown(), getConfig().getLevelCooldown(), getLevel(p));
  }

  @ReceiveCancelledEvents
  @Handle(ignoreCancelled = false)
  public void onRightClick(PlayerInteractEvent e) {
    PlayerRef p = e.player();
    if (preferenceEnabled(p, SNEAK) && !p.isSneaking()) {
      return;
    }
    withAdaptedPlayer(p, () -> {
      if (toss.toss(e, p, getCooldownSeconds(p) * 1000L, 100)) {
        addStat(p, "nether.skull-yeet.skulls-thrown", 1);
      }
    });
  }

  @Handle(priority = EventPriority.LOWEST)
  public void protectPassiveMobs(EntityDamageByEntityEvent e) {
    if (NetherSkullToss.spares(this, e, getConfig().isIgnorePassiveMobs())) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void forgetSkull(EntityRemoveEvent e) {
    NetherSkullToss.forget(e);
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
        && last.damager().type().equals(NetherSkullToss.WITHER_SKULL)
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
          if (AdaptApi.settings().advancements() && !getPlayer(p).isGranted(getId(), "challenge_nether_skull_long_bomb")) {
            getPlayer(p).grant(getId(), "challenge_nether_skull_long_bomb");
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



  static void tossDeniedFx(Adaptation<?> owner, PlayerRef p) {
    Fx.now(owner, p.eyeLocation(), FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 3, 0D, 0D, 0D, 0.1D, 0.0D)
        .sound(Sounds.BLOCK_CONDUIT_DEACTIVATE, 0.6F, 0.8F);
  }

  static void tossFx(Adaptation<?> owner, Loc spawn, Vec3 dir, EntityRef skull) {
    Fx.now(owner, spawn, FxPriority.GAMEPLAY)
        .trail(Particles.FLAME, dir.x(), dir.y(), dir.z(), 1.4D, 6)
        .trail(Particles.SMOKE, dir.x(), dir.y(), dir.z(), 1.1D, 6)
        .particle(Particles.SOUL, 4, 0D, 0D, 0D, 0.1D, 0.02D)
        .chord(Sounds.ENTITY_WITHER_SHOOT, 1.0F, 1.0F, Sounds.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.4F, 1.3F);
    if (skull != null) {
      FxTimeline.follow(owner, skull)
          .duration(60)
          .priority(FxPriority.TRAIL)
          .cullRadius(48)
          .frame((f, tick, progress) -> f.particle(Particles.SOUL, 2, 0D, 0D, 0D, 0.02D, 0.0D)
              .particle(Particles.FLAME, 1, 0D, 0D, 0D, 0.02D, 0.0D))
          .start();
    }
  }

  @ConfigDescription("Throw Wither Skulls that explode on impact.")
  public static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Prevent Skull Toss explosions from damaging passive and neutral mobs.", impact = "When enabled, direct skull impact damage is unchanged.")
    private boolean ignorePassiveMobs = false;
    @ConfigDoc(value = "Controls Base Cooldown for the Nether Skull Yeet adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    private int baseCooldown = 15;
    @ConfigDoc(value = "Controls Level Cooldown for the Nether Skull Yeet adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    private int levelCooldown = 5;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      maxLevel = 3;
      initialCost = 3;
    }

    public boolean isIgnorePassiveMobs() {
      return ignorePassiveMobs;
    }

    public void setIgnorePassiveMobs(boolean ignorePassiveMobs) {
      this.ignorePassiveMobs = ignorePassiveMobs;
    }

    public int getBaseCooldown() {
      return baseCooldown;
    }

    public int getLevelCooldown() {
      return levelCooldown;
    }
  }
}
