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
import art.arcane.adapt.api.adaptation.DeathSaveCooldown;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.damage.PlayerDeathSave;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.TragoulMessages;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

public class TragoulLastRites extends SimpleAdaptation<TragoulLastRites.Config> implements PlayerDeathSave {
  private static final Id INVISIBILITY = Id.minecraft("invisibility");
  private static final Id RESISTANCE = Id.minecraft("resistance");

  public TragoulLastRites() {
    super("tragoul-last-rites");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("totem_of_undying"));
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("totem_of_undying"))
        .key("challenge_tragoul_last_rites_5")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("soul_torch"))
            .key("challenge_tragoul_last_rites_50")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_tragoul_last_rites_5", "tragoul.last-rites.deaths-defied", 5, 500);
    registerMilestone("challenge_tragoul_last_rites_50", "tragoul.last-rites.deaths-defied", 50, 2000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CommonPreferences.DEATH_SAVE_PRIORITY);
  }

  @Override
  public int deathSavePriority(PlayerRef player) {
    return preference(player, CommonPreferences.DEATH_SAVE_PRIORITY).ordinal();
  }

  @Override
  public int defaultOrder() {
    return 0;
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + AdaptLanguage.text(TragoulMessages.LAST_RITES_LORE1));
    statLore(v, Form.duration(getConfig().spiritDurationTicks * 50D, 1), 2);
    statLore(v, C.YELLOW, "* ", Form.duration((double) getCooldownMillis(level), 1), 3);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    tryPreventDeath(e);
  }

  @Override
  public void tryPreventDeath(EntityDamageEvent e) {
    if (e.isCancelled() || !(e.entity() instanceof PlayerRef p)) {
      return;
    }

    if (e.finalDamage() < p.health()) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0 || p.isDead()) {
        return;
      }

      if (!DeathSaveCooldown.LAST_RITES.activate(p, System.currentTimeMillis(), getCooldownMillis(level))) {
        return;
      }

      e.setCancelled(true);
      p.setHealth(1.0);

      int spiritTicks = getConfig().spiritDurationTicks;
      p.addEffect(new EffectInstance(INVISIBILITY, spiritTicks, 0, true, false, true));
      p.addEffect(new EffectInstance(RESISTANCE, spiritTicks, getConfig().resistanceAmplifier, true, false, true));

      double radius = getConfig().targetClearRadius;
      for (EntityRef entity : p.nearbyEntities(radius, radius, radius)) {
        if (!(entity instanceof MobRef mob) || !isTargeting(mob, p)) {
          continue;
        }

        Tasks.runEntity(mob, () -> {
          if (mob.isValid() && isTargeting(mob, p)) {
            mob.setTarget(null);
          }
        });
      }

      playSpiritAscension(p);
      fx(p.location().add(0, 0.2, 0), FxPriority.TRANSITION)
          .dustRing(radius, 24, 1.0F)
          .sound(Sounds.BLOCK_CONDUIT_DEACTIVATE, 0.25F, 1.8F);
      Tasks.runEntity(p, () -> fx(p.location().add(0, 1.0, 0), FxPriority.TRANSITION)
          .particle(Particles.SOUL, 6, 0, 0.5, 0, 0.2, 0.03)
          .particle(Particles.CRIT, 3, 0, 0.8, 0, 0.3, 0.05)
          .sound(Sounds.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.4F, 1.2F), spiritTicks);
      addStat(p, "tragoul.last-rites.deaths-defied", 1);
      xp(p, getConfig().xpPerSave);
    });
  }

  private static boolean isTargeting(MobRef mob, PlayerRef player) {
    EntityRef target = mob.target();
    return target != null && target.uuid().equals(player.uuid());
  }

  private void playSpiritAscension(PlayerRef p) {
    timeline(p)
        .duration(30)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(40)
        .frame((fx, tick, progress) -> {
          if (tick < 6) {
            double spread = 0.3 + (tick * 0.12);
            fx.particle(Particles.SCULK_SOUL, 5, 0, 1.0, 0, spread, 0.05);
            fx.particle(Particles.SOUL, 3, 0, 0.6, 0, spread * 0.8, 0.03);
          } else if (tick < 20) {
            fx.helix(Particles.SOUL_FIRE_FLAME, 0.8, 2.4, 4, progress * Math.PI * 4.0);
          } else {
            fx.particle(Particles.SCULK_SOUL, 3, 0, 1.2, 0, 0.3, 0.02);
          }
          if (tick == 0) {
            fx.chord(Sounds.ITEM_TOTEM_USE, 0.8F, 1.5F, Sounds.BLOCK_BEACON_ACTIVATE, 0.5F, 0.6F, Sounds.BLOCK_NOTE_BLOCK_BASS, 0.3F, 0.5F);
          } else if (tick == 20) {
            fx.sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.4F);
          }
        })
        .start();
  }

  private long getCooldownMillis(int level) {
    return Math.max(30000L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }


  @ConfigDescription("A killing blow leaves you at 1 HP as a fleeting spirit instead of dying.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration in ticks of the spirit state after defying death.", impact = "Higher values keep the protection window open longer.")
    int spiritDurationTicks = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Amplifier of the resistance effect during the spirit state.", impact = "Higher values reduce more damage while in spirit form.")
    int resistanceAmplifier = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Radius in which hostile mob targets locked onto you are cleared.", impact = "Higher values shake off pursuers from further away.")
    double targetClearRadius = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown in milliseconds before level scaling.", impact = "Higher values make death defiance rarer.")
    double cooldownMillisBase = 600000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown reduction in milliseconds granted at max level.", impact = "Higher values let high levels defy death more often.")
    double cooldownMillisFactor = 300000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per death defied.", impact = "Higher values accelerate skill progression from this adaptation.")
    double xpPerSave = 120;

    public Config() {
      baseCost = 6;
      costFactor = 0.85;
      initialCost = 6;
    }
  }
}
