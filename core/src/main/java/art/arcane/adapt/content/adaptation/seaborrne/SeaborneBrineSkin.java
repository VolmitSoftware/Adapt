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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.Map;
import java.util.UUID;

public class SeaborneBrineSkin extends SimpleAdaptation<SeaborneBrineSkin.Config> {
  private static final int REGEN_DURATION_TICKS = 100;
  private static final int REGEN_REFRESH_THRESHOLD_TICKS = 40;
  private static final Id REGENERATION = Id.minecraft("regeneration");

  private final Map<UUID, Long> wetUntil = playerState();
  private final Cooldowns wetFx = cooldowns();
  private final Cooldowns reductionFx = cooldowns();

  public SeaborneBrineSkin() {
    super("seaborne-brine-skin");
    registerConfiguration(Config.class);
    setLocalizationKey("seaborn.brine_skin");
    setIcon(Id.minecraft("kelp"));
    setInterval(2000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("kelp"))
        .key("challenge_seaborne_brine_72k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_seaborne_brine_72k", "seaborne.brine-skin.wet-ticks", 72000, 400);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getRegenAmplifier(level) + 1, 1);
    statLore(v, Form.pc(getDamageReduction(level), 0), 2);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    for (AdaptPlayer adaptPlayer : learnedCandidates(now)) {
      PlayerRef player = adaptPlayer.getPlayer();
      withPlayerThread(player, () -> {
        UUID id = player.uuid();
        if (!player.isOnline()) {
          return;
        }

        int level = getActiveLevel(player);
        if (level <= 0) {
          wetUntil.remove(id);
          return;
        }

        if (!isWet(player)) {
          return;
        }

        wetUntil.put(id, System.currentTimeMillis() + getLingerMillis(level));
        applyRegen(player, level);
        addStat(player, "seaborne.brine-skin.wet-ticks", getInterval() / 50D);
        if (wetFx.isReady(id, 2000L)) {
          wetFx.mark(id);
          fx(player.location().add(0D, 1.0D, 0D), FxPriority.AMBIENT)
              .particle(Particles.SPLASH, 3, 0D, 0.4D, 0D, 0.25D, 0.02D)
              .particle(Particles.GLOW, 2, 0D, 0.5D, 0D, 0.2D, 0D);
        }
      });
    }
  }

  private void applyRegen(PlayerRef player, int level) {
    int amplifier = getRegenAmplifier(level);
    EffectInstance current = player.effect(REGENERATION);
    if (current == null || current.amplifier() < amplifier || current.durationTicks() <= REGEN_REFRESH_THRESHOLD_TICKS) {
      player.addEffect(new EffectInstance(REGENERATION, REGEN_DURATION_TICKS, amplifier, false, false, true));
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef p)) {
      return;
    }

    withPlayerThread(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }

      Long until = wetUntil.get(p.uuid());
      boolean lingering = until != null && System.currentTimeMillis() < until;
      if (!isWet(p) && !lingering) {
        return;
      }

      e.setDamage(e.damage() * (1D - getDamageReduction(level)));
      if (reductionFx.isReady(p.uuid(), 500L)) {
        reductionFx.mark(p.uuid());
        fx(p.location().add(0D, 1.0D, 0D), FxPriority.COMBAT)
            .ring(Particles.SPLASH, 0.6D, 8, 0.1D)
            .sound(Sounds.ITEM_SHIELD_BLOCK, 0.35F, 1.4F);
      }
    });
  }

  private boolean isWet(PlayerRef p) {
    if (p.isInWater() || p.isSwimming()) {
      return true;
    }

    if (!p.world().hasStorm()) {
      return false;
    }

    Loc location = p.location();
    return location.y() >= p.world().highestBlockY(location.blockX(), location.blockZ()) - 1;
  }

  private int getRegenAmplifier(int level) {
    return regenAmplifier(getLevelPercent(level));
  }

  private double getDamageReduction(int level) {
    return damageReduction(getConfig().damageReductionBase, getConfig().damageReductionFactor, getConfig().maxDamageReduction, getLevelPercent(level));
  }

  private long getLingerMillis(int level) {
    return (long) (Math.max(0D, getConfig().lingerSecondsBase + (getLevelPercent(level) * getConfig().lingerSecondsFactor)) * 1000D);
  }

  static int regenAmplifier(double levelPercent) {
    return Math.max(0, Math.min(2, (int) Math.floor(levelPercent * 3D)));
  }

  static double damageReduction(double base, double factor, double max, double levelPercent) {
    return Math.min(max, base + (levelPercent * factor));
  }

  @ConfigDescription("While wet you slowly regenerate and take reduced damage, and the buff lingers briefly after you dry off.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base damage reduction fraction while wet.", impact = "Higher values reduce more damage at low levels.")
    double damageReductionBase = 0.06;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional damage reduction fraction gained across levels.", impact = "Higher values reduce more damage at higher levels.")
    double damageReductionFactor = 0.14;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hard cap on damage reduction fraction.", impact = "Lower values keep the reduction modest even at max level.")
    double maxDamageReduction = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base seconds the wet buff lingers after drying off.", impact = "Higher values keep protection active longer after leaving water at low levels.")
    double lingerSecondsBase = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional linger seconds gained across levels.", impact = "Higher values keep protection active far longer at higher levels.")
    double lingerSecondsFactor = 4;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 2;
    }
  }
}
