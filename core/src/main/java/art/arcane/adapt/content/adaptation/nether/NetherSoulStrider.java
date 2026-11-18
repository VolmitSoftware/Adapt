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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.catalog.NetherMessages;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

public class NetherSoulStrider extends SimpleAdaptation<NetherSoulStrider.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> IMMUNITY = CommonPreferences.toggle("immunity", NetherMessages.NETHERSOULSTRIDER_PREFERENCE_IMMUNITY, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> BURST = CommonPreferences.toggle("burst", NetherMessages.NETHERSOULSTRIDER_PREFERENCE_BURST, CommonPreferences.Toggle.ON);

  private static final String SLOT_SOUL = "soul";
  private static final String SLOT_STRIDE = "stride";
  private static final String SLOT_BURST = "burst";
  private static final long HOLD_TICKS = 40L;
  private static final long HOLD_REFRESH_MILLIS = 1000L;
  private static final Id SOUL_SAND = Id.minecraft("soul_sand");
  private static final Id SOUL_SOIL = Id.minecraft("soul_soil");
  private static final Rgb BURST_COLOR = Rgb.of(0x5C4A6B);

  public NetherSoulStrider() {
    super("nether-soul-strider");
    registerConfiguration(Config.class);
    setIcon(SOUL_SAND);
    setInterval(1000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("soul_sand"))
        .key("challenge_nether_soul_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("soul_soil"))
            .key("challenge_nether_soul_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_nether_soul_1k", "nether.soul-strider.blocks-walked", 1000, 300);
    registerMilestone("challenge_nether_soul_25k", "nether.soul-strider.blocks-walked", 25000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, IMMUNITY, BURST);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    releaseStride(player.getPlayer());
    AdaptAttributeService.get().removeAll(player.getPlayer(), getName());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getStrideSpeed(level), 2), 1);
    statLore(v, Form.duration(getConfig().burstTicks * 50D, 1), 2);
  }

  @Handle(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void on(PlayerMoveEvent e) {
    PlayerRef p = e.player();
    withAdaptedPlayer(p, e, () -> {
      if (p.isFlying() || p.isGliding() || p.vehicle() != null) {
        return;
      }

      Loc from = e.from();
      Loc to = e.to();
      if (to == null) {
        return;
      }

      Id feet = to.world().blockAt(to).type();
      Id below = to.world().blockAt(to.add(0D, -1D, 0D)).type();
      if (!isSoul(feet) && !isSoul(below)) {
        releaseStride(p);
        return;
      }

      double dx = to.x() - from.x();
      double dz = to.z() - from.z();
      double moveLen = Math.sqrt((dx * dx) + (dz * dz));
      if (moveLen < 0.02D) {
        return;
      }

      int level = getActiveLevel(p);
      long now = System.currentTimeMillis();
      refreshStride(p, level, now);

      long lastOn = getStorageLong(p, "soulStriderLastOn", 0L);
      setStorage(p, "soulStriderLastOn", now);
      if (preferenceEnabled(p, BURST) && level >= getMaxLevel() && now - lastOn > getConfig().burstGapMillis
          && getStorageLong(p, "soulStriderBurstNext", 0L) <= now) {
        setStorage(p, "soulStriderBurstNext", now + getConfig().burstCooldownMillis);
        applyBurst(p);
      }

      addStat(p, "nether.soul-strider.blocks-walked", moveLen);
      if (getStorageLong(p, "soulStriderXpNext", 0L) <= now) {
        setStorage(p, "soulStriderXpNext", now + getConfig().xpIntervalMillis);
        xp(p, getConfig().xpPerStride);
        fx(p.location(), FxPriority.TRAIL)
            .particle(Particles.SOUL_FIRE_FLAME, 2, 0D, 0.05D, 0D, 0.12D, 0.01D)
            .particle(Particles.SMOKE, 1, 0D, 0.05D, 0D, 0.1D, 0.0D);
      }
    });
  }

  private void refreshStride(PlayerRef p, int level, long now) {
    long holdUntil = getStorageLong(p, "soulStriderHoldUntil", 0L);
    if (holdUntil - now > HOLD_REFRESH_MILLIS) {
      return;
    }

    setStorage(p, "soulStriderHoldUntil", now + (HOLD_TICKS * 50L));
    AdaptAttributeService attributes = AdaptAttributeService.get();
    if (preferenceEnabled(p, IMMUNITY)) {
      attributes.applyTimed(p, getName(), SLOT_SOUL, Attributes.MOVEMENT_EFFICIENCY, 1.0D, AttributeOperation.ADD_VALUE, HOLD_TICKS);
    }
    double bonus = getStrideBonus(level);
    if (bonus > 0D) {
      attributes.applyTimed(p, getName(), SLOT_STRIDE, Attributes.MOVEMENT_SPEED, bonus, AttributeOperation.ADD_MULTIPLIED_TOTAL, HOLD_TICKS);
    }
  }

  private void releaseStride(PlayerRef p) {
    if (getStorageLong(p, "soulStriderHoldUntil", 0L) == 0L) {
      return;
    }

    setStorage(p, "soulStriderHoldUntil", 0L);
    AdaptAttributeService attributes = AdaptAttributeService.get();
    attributes.remove(p, getName(), SLOT_SOUL, Attributes.MOVEMENT_EFFICIENCY);
    attributes.remove(p, getName(), SLOT_STRIDE, Attributes.MOVEMENT_SPEED);
  }

  private void applyBurst(PlayerRef p) {
    fx(p.location(), FxPriority.TRANSITION)
        .dustRing(BURST_COLOR, 0.7D, 10, 1.0F)
        .helix(Particles.SOUL_FIRE_FLAME, 0.45D, 1.6D, 8, 0D)
        .chord(Sounds.PARTICLE_SOUL_ESCAPE, 0.35F, 1.3F, Sounds.BLOCK_SOUL_SAND_STEP, 0.5F, 0.8F);
    int durationTicks = getConfig().burstTicks;
    if (durationTicks <= 0) {
      return;
    }

    AdaptAttributeService.get().applyTimed(p, getName(), SLOT_BURST, Attributes.MOVEMENT_SPEED, burstSpeedBonus(getConfig().burstAmplifier), AttributeOperation.ADD_MULTIPLIED_TOTAL, durationTicks);
  }

  private boolean isSoul(Id type) {
    return type.equals(SOUL_SAND) || type.equals(SOUL_SOIL);
  }

  private double getStrideSpeed(int level) {
    return strideSpeed(getLevelPercent(level), getConfig().strideSpeedBase, getConfig().strideSpeedFactor);
  }

  private double getStrideBonus(int level) {
    return strideBonus(getLevelPercent(level), getConfig().strideSpeedBase, getConfig().strideSpeedFactor);
  }

  static double strideSpeed(double levelPercent, double base, double factor) {
    return base + (levelPercent * factor);
  }

  static double strideBonus(double levelPercent, double base, double factor) {
    if (base <= 0D) {
      return 0D;
    }

    return (levelPercent * factor) / base;
  }

  static double burstSpeedBonus(int amplifier) {
    return 0.2D * (amplifier + 1);
  }


  @ConfigDescription("Move at full speed across soul sand and soul soil, gaining soul-speed bursts at mastery.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stride Speed Base for the Nether Soul Strider adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double strideSpeedBase = 0.20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stride Speed Factor for the Nether Soul Strider adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double strideSpeedFactor = 0.10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Burst Ticks for the Nether Soul Strider adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int burstTicks = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Burst Amplifier for the Nether Soul Strider adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int burstAmplifier = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Burst Gap Millis for the Nether Soul Strider adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long burstGapMillis = 600;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Burst Cooldown Millis for the Nether Soul Strider adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long burstCooldownMillis = 3000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Stride for the Nether Soul Strider adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerStride = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Interval Millis for the Nether Soul Strider adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long xpIntervalMillis = 1500;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 3;
    }
  }
}
