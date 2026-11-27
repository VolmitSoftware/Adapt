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

package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityPotionEffectEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;

public class SeaborneOxygen extends SimpleAdaptation<SeaborneOxygen.Config> {
  public static final Id ID = Id.adapt("seaborne/oxygen");
  private static final double MAX_CREDIT_TICKS_PER_SAMPLE = 160D;

  private final Map<UUID, Boolean> submerged = playerState();
  private final Map<UUID, Long> lastUnderwaterSample = playerState();
  private final SeaborneNightSight sight = new SeaborneNightSight();

  public SeaborneOxygen() {
    super(ID);
    needs(SeaborneBreath.NEED);
    registerConfiguration(Config.class);
    setLocalizationKey("adapt.seaborn.oxygen");
    setIcon(Id.minecraft("glass_pane"));
    setInterval(3000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("turtle_helmet"))
        .key("challenge_seaborne_oxygen_12k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_seaborne_oxygen_12k", "seaborne.oxygen.bonus-air-ticks", 12000, 300);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("turtle_helmet"))
        .key("challenge_seaborne_vision_72k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_seaborne_vision_72k", "seaborne.turtles-vision.time-underwater", 72000, 400);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    SeaborneBreath.remove(this, player.player());
    sight.clear(player.player());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getAirBoost(level), 0), 1);
    if (level >= getConfig().nightVisionFromLevel) {
      unlockLore(v, Text.key("adapt.seaborn.oxygen.night_vision"));
    }
  }

  public double getAirBoost(int level) {
    return SeaborneBreath.savedAirFraction(level, getConfig().airPerLevelTics, SeaborneBreath.TICKS_PER_PULSE);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityPotionEffectEvent event) {
    if (event.entity() instanceof PlayerRef player) {
      sight.changed(player, event.modifiedType());
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerQuitEvent event) {
    PlayerRef player = event.player();
    sight.clear(player);
    submerged.remove(player.uuid());
    lastUnderwaterSample.remove(player.uuid());
  }

  @Override
  public void onTick() {
    for (PlayerProfile adaptPlayer : learnedCandidates(System.currentTimeMillis())) {
      PlayerRef player = adaptPlayer.player();
      withPlayerThread(player, () -> {
        if (!player.isOnline() || player.world() == null) {
          return;
        }

        UUID id = player.uuid();
        int level = getActiveLevel(player);
        if (level <= 0) {
          sight.clear(player);
          submerged.remove(id);
          lastUnderwaterSample.remove(id);
          return;
        }

        double savedFraction = getAirBoost(level);
        SeaborneBreath.apply(this, player, SeaborneBreath.oxygenBonus(savedFraction));

        boolean inWater = player.isInWater() || player.isSwimming();
        boolean was = submerged.getOrDefault(id, false);
        if (!inWater) {
          sight.clear(player);
          lastUnderwaterSample.remove(id);
          if (was) {
            submerged.put(id, false);
            fx(player.eyeLocation(), FxPriority.AMBIENT)
                .particle(Particles.SPLASH, 6, 0D, 0D, 0D, 0.2D, 0.05D)
                .sound(Sounds.BLOCK_CONDUIT_DEACTIVATE, 0.3F, 1.0F);
          }
          return;
        }

        int savedAirTicks = SeaborneBreath.expectedSavedAirTicks(savedFraction, SeaborneBreath.TICKS_PER_PULSE);
        if (savedAirTicks > 0) {
          addStat(player, "seaborne.oxygen.bonus-air-ticks", savedAirTicks);
        }
        if (level >= getConfig().nightVisionFromLevel && player.isInWater()) {
          sight.refresh(player);
          long now = System.currentTimeMillis();
          double elapsedTicks = elapsedUnderwaterTicks(lastUnderwaterSample.put(id, now), now);
          if (elapsedTicks > 0D) {
            addStat(player, "seaborne.turtles-vision.time-underwater", elapsedTicks);
          }
        } else {
          sight.clear(player);
          lastUnderwaterSample.remove(id);
        }

        if (!was) {
          submerged.put(id, true);
          fx(player.eyeLocation(), FxPriority.TRANSITION)
              .ring(Particles.BUBBLE, 0.45D, 10, 0.0D)
              .dustBurst(4, 0.3D, 0.9F)
              .chord(Sounds.BLOCK_CONDUIT_ACTIVATE, 0.35F, 1.15F, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 1.4F);
          return;
        }

        fx(player.eyeLocation(), FxPriority.AMBIENT)
            .column(Particles.BUBBLE, 2, 0.6D);
      });
    }
  }

  @Override
  public void unregister() {
    for (UUID id : sight.holders()) {
      PlayerRef player = AdaptApi.platform().host().player(id);
      if (player != null) {
        AdaptApi.scheduler().runEntity(player, () -> sight.clear(player));
      } else {
        sight.forget(id);
      }
    }
    submerged.clear();
    lastUnderwaterSample.clear();
    super.unregister();
  }

  static double elapsedUnderwaterTicks(Long previousSample, long now) {
    if (previousSample == null || now <= previousSample) {
      return 0D;
    }
    return Math.min(MAX_CREDIT_TICKS_PER_SAMPLE, (now - previousSample) / 50D);
  }

  @ConfigDescription("Hold more oxygen underwater, and see in the dark while submerged from level 2.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Air ticks saved per level out of every 75 ticks underwater.", impact = "Higher values stretch your breath further; 19 per level never runs out at level 4.")
    int airPerLevelTics = 19;
    @ConfigDoc(value = "First level that also gives Night Vision while your head is underwater.", impact = "Higher values make the sight a later reward; 0 gives it at every level.")
    int nightVisionFromLevel = 2;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      initialCost = 1;
    }
  }
}
