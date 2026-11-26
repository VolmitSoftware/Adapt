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

package art.arcane.adapt.content.adaptation.unarmed;

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
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

public class UnarmedSecondWind extends SimpleAdaptation<UnarmedSecondWind.Config> {
  public static final Id REGENERATION = Id.minecraft("regeneration");
  private static final DataKey<Byte> PAID = DataKey.of(Id.of("adapt", "unarmed_second_wind_paid"), DataCodecs.BYTE);
  private final Cooldowns secondWindCooldown = cooldowns();

  public UnarmedSecondWind() {
    super("unarmed-second-wind");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("cooked_beef"));
    setInterval(4960);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_ingot"))
        .key("challenge_unarmed_second_wind_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond"))
            .key("challenge_unarmed_second_wind_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_unarmed_second_wind_100", "unarmed.second-wind.second-winds", 100, 400);
    registerMilestone("challenge_unarmed_second_wind_1k", "unarmed.second-wind.second-winds", 1000, 1500);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getFoodRestore(level), 1);
    statLore(v, Form.duration(getRegenDurationTicks(level) * 50D, 1), 2);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    LivingRef victim = e.entity();
    if (victim instanceof PlayerRef) {
      return;
    }

    PlayerRef p = directPlayerKiller(e);
    if (p == null) {
      return;
    }

    if (isProtectedFriendly(p, victim)) {
      return;
    }

    if (isTool(p.inventory().mainHand()) || isTool(p.inventory().offHand())) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (!secondWindCooldown.isReady(p.uuid(), getConfig().cooldownMillis) || !claim(victim)) {
      return;
    }

    secondWindCooldown.mark(p.uuid(), getConfig().cooldownMillis);
    int foodRestore = getFoodRestore(level);
    int regenTicks = getRegenDurationTicks(level);
    restore(p, foodRestore, getConfig().saturationRestore, regenTicks, getConfig().regenAmplifier);

    playBloom(p);
    xp(p, getConfig().xpPerSecondWind, "second-wind");
    addStat(p, "unarmed.second-wind.second-winds", 1);
  }

  private void playBloom(PlayerRef p) {
    fx(p.location().add(0, 1.6D, 0), FxPriority.TRANSITION)
        .chord(Sounds.ENTITY_PLAYER_LEVELUP, 0.5F, 1.7F, Sounds.ENTITY_GENERIC_EAT, 0.4F, 1.2F);
    timeline(p)
        .duration(5)
        .priority(FxPriority.TRANSITION)
        .frame((fx, tick, progress) -> {
          fx.particle(Particles.HEART, 3, 0, 1.4D + (progress * 0.4D), 0, 0.3D, 0.0D);
          fx.particle(Particles.WAX_ON, 2, 0, 1.2D + (progress * 0.4D), 0, 0.25D, 0.0D);
        })
        .start();
  }

  private int getFoodRestore(int level) {
    return Math.max(1, (int) Math.round(getConfig().foodRestoreBase + (getLevelPercent(level) * getConfig().foodRestoreFactor)));
  }

  private int getRegenDurationTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().regenDurationTicksBase + (getLevelPercent(level) * getConfig().regenDurationTicksFactor)));
  }

  /** The second wind on the player's thread: food and saturation back, then a regeneration burst. */
  public static void restore(PlayerRef p, int food, double saturation, int regenTicks, int regenAmplifier) {
    Tasks.runEntity(p, () -> {
      if (!p.isOnline() || p.isDead()) {
        return;
      }
      p.setFoodLevel(Math.min(20, p.foodLevel() + food));
      p.setSaturation((float) Math.min(p.foodLevel(), p.saturation() + saturation));
      p.addEffect(new EffectInstance(REGENERATION, regenTicks, regenAmplifier, false, true, true));
    });
  }

  /** Marks {@code victim}'s death as paid by a second wind; false when one already paid for it. */
  public static boolean claim(LivingRef victim) {
    if (victim.data().has(PAID)) {
      return false;
    }
    victim.data().set(PAID, (byte) 1);
    return true;
  }

  public static PlayerRef directPlayerKiller(EntityDeathEvent event) {
    EntityRef causing = event.causingEntity();
    EntityRef direct = event.directDamager();
    return causing instanceof PlayerRef player && direct != null && direct.uuid().equals(player.uuid()) ? player : null;
  }


  @ConfigDescription("Bare-hand kills restore hunger and grant a short regeneration burst.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base hunger points restored per bare-hand kill.", impact = "Higher values restore more food per kill.")
    double foodRestoreBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional hunger points restored at max level.", impact = "Higher values restore more food per kill as levels increase.")
    double foodRestoreFactor = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Saturation restored per bare-hand kill.", impact = "Higher values keep the hunger bar full for longer.")
    double saturationRestore = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base regeneration duration in ticks per bare-hand kill.", impact = "Higher values keep the regen burst active longer.")
    double regenDurationTicksBase = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional regeneration duration ticks granted at max level.", impact = "Higher values keep the regen burst active longer as levels increase.")
    double regenDurationTicksFactor = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Regeneration amplifier applied by the burst.", impact = "Higher values heal faster during the burst.")
    int regenAmplifier = 0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown in milliseconds between second wind triggers.", impact = "Higher values prevent rapid kill-chains from spamming the heal.")
    long cooldownMillis = 3000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per second wind trigger.", impact = "Higher values speed up unarmed skill progression from kills.")
    double xpPerSecondWind = 18;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      initialCost = 1;
    }
  }
}
