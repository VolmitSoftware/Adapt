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

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.DeathSave;
import art.arcane.adapt.api.content.DeathSaveSlot;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.List;

public class TragoulLastRites extends SimpleAdaptation<TragoulLastRites.Config> implements DeathSave {
  public static final Id ID = Id.adapt("tragoul/last_rites");
  public static final DeathSaveSlot DEATH_SAVE = DeathSaveSlot.of(Id.adapt("tragoul_last_rites"));
  public TragoulLastRites() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("totem_of_undying"));
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("totem_of_undying"))
        .key("challenge_tragoul_last_rites_5")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("soul_torch"))
            .key("challenge_tragoul_last_rites_50")
            .frame(AdvancementFrame.CHALLENGE)
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
  public int defaultOrder() {
    return 0;
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + Text.key("adapt.tragoul.last_rites.lore1").resolve());
    statLore(v, Format.duration(getConfig().spiritDurationTicks * 50D, 1), 2);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration((double) getCooldownMillis(level), 1), 3);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    tryPreventDeath(e);
  }

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

      long now = System.currentTimeMillis();
      long cooldownMillis = getCooldownMillis(level);
      if (!TragoulLastRites.DEATH_SAVE.activate(p, now, cooldownMillis)) {
        return;
      }
      HudStatus.cooldown(p.uuid(), this, now, cooldownMillis);

      TragoulSpirit.enter(this, p, e, getConfig().spiritDurationTicks, getConfig().resistanceAmplifier,
          getConfig().targetClearRadius);
      addStat(p, "tragoul.last-rites.deaths-defied", 1);
      xp(p, getConfig().xpPerSave);
    });
  }

  private long getCooldownMillis(int level) {
    return Math.max(30000L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }

  static void spiritEnterFx(Adaptation<?> owner, PlayerRef p, double clearRadius) {
    FxTimeline.follow(owner, p)
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
    Fx.now(owner, p.location().add(0, 0.2, 0), FxPriority.TRANSITION)
        .dustRing(clearRadius, 24, 1.0F)
        .sound(Sounds.BLOCK_CONDUIT_DEACTIVATE, 0.25F, 1.8F);
  }

  static void spiritExitFx(Adaptation<?> owner, PlayerRef p) {
    Fx.now(owner, p.location().add(0, 1.0, 0), FxPriority.TRANSITION)
        .particle(Particles.SOUL, 6, 0, 0.5, 0, 0.2, 0.03)
        .particle(Particles.CRIT, 3, 0, 0.8, 0, 0.3, 0.05)
        .sound(Sounds.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.4F, 1.2F);
  }


  @ConfigDescription("A killing blow leaves you at 1 HP as a fleeting spirit instead of dying.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Duration in ticks of the spirit state after defying death.", impact = "Higher values keep the protection window open longer.")
    int spiritDurationTicks = 60;
    @ConfigDoc(value = "Amplifier of the resistance effect during the spirit state.", impact = "Higher values reduce more damage while in spirit form.")
    int resistanceAmplifier = 3;
    @ConfigDoc(value = "Radius in which hostile mob targets locked onto you are cleared.", impact = "Higher values shake off pursuers from further away.")
    double targetClearRadius = 12;
    @ConfigDoc(value = "Cooldown in milliseconds before level scaling.", impact = "Higher values make death defiance rarer.")
    double cooldownMillisBase = 600000;
    @ConfigDoc(value = "Cooldown reduction in milliseconds granted at max level.", impact = "Higher values let high levels defy death more often.")
    double cooldownMillisFactor = 300000;
    @ConfigDoc(value = "XP granted per death defied.", impact = "Higher values accelerate skill progression from this adaptation.")
    double xpPerSave = 120;

    public Config() {
      baseCost = 7;
      costFactor = 0.7;
      initialCost = 6;
    }
  }
}
