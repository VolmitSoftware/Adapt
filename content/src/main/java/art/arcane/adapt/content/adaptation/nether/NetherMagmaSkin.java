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
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.List;

public class NetherMagmaSkin extends SimpleAdaptation<NetherMagmaSkin.Config> {
  public static final Id ID = Id.adapt("nether/magma_skin");
  public static final PlayerPreference<CommonPreferences.Toggle> RETALIATION = CommonPreferences.toggle("retaliation", Text.key("adapt.nethermagmaskin.preferences.preference_retaliation"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> OWN_FIRE = CommonPreferences.toggle("own-fire", Text.key("adapt.nethermagmaskin.preferences.preference_own_fire"), CommonPreferences.Toggle.ON);

  public NetherMagmaSkin() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("magma_cream"));
    setInterval(2000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("magma_cream"))
        .key("challenge_nether_magma_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("magma_block"))
            .key("challenge_nether_magma_2500")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_nether_magma_100", "nether.magma-skin.attackers-ignited", 100, 300);
    registerMilestone("challenge_nether_magma_2500", "nether.magma-skin.attackers-ignited", 2500, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, RETALIATION, OWN_FIRE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.duration(getReflectFireTicks(level) * 50D, 1), 1);
    statLore(v, TextFormat.RED, "+ ", Format.f(getBonusDamage(level), 1), 2);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void onDefend(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof PlayerRef p)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      if (p.fireTicks() <= 0 || !preferenceEnabled(p, RETALIATION)) {
        return;
      }
      int level = getActiveLevel(p);
      if (!NetherMagmaRetort.ignite(this, p, e, getReflectFireTicks(level))) {
        return;
      }

      addStat(p, "nether.magma-skin.attackers-ignited", 1);
      xp(p, getConfig().xpOnReflect);
    });
  }

  @Handle(priority = EventPriority.HIGH)
  public void onAttack(EntityDamageByEntityEvent e) {
    if (!(e.damager() instanceof PlayerRef p) || !(e.entity() instanceof LivingRef target)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      if (p.fireTicks() <= 0) {
        return;
      }
      if (!NetherMagmaRetort.isMelee(e.cause())) {
        return;
      }
      if (!canDamageTarget(p, target)) {
        return;
      }

      int level = getActiveLevel(p);
      double bonus = getBonusDamage(level);
      if (bonus <= 0D) {
        return;
      }

      e.setDamage(e.damage() + bonus);
      int fire = getBonusFireTicks(level);
      if (preferenceEnabled(p, OWN_FIRE)) {
        AdaptApi.scheduler().runEntity(target, () -> target.setFireTicks(Math.max(target.fireTicks(), fire)));
      }
      xp(p, bonus * getConfig().xpPerBonusDamage);
      fx(target.location().add(0D, 0.8D, 0D), FxPriority.COMBAT)
          .burst(Particles.FLAME, 5, 0.3D)
          .particle(Particles.SOUL_FIRE_FLAME, 2, 0D, 0.2D, 0D, 0.2D, 0.0D)
          .sound(Sounds.ENTITY_BLAZE_HURT, 0.35F, 1.5F);
    });
  }

  private int getReflectFireTicks(int level) {
    return reflectFireTicks(getLevelPercent(level), getConfig().reflectFireTicksBase, getConfig().reflectFireTicksFactor);
  }

  private double getBonusDamage(int level) {
    return bonusDamage(getLevelPercent(level), getConfig().bonusDamageBase, getConfig().bonusDamageFactor);
  }

  private int getBonusFireTicks(int level) {
    return reflectFireTicks(getLevelPercent(level), getConfig().bonusFireTicksBase, getConfig().bonusFireTicksFactor);
  }

  static int reflectFireTicks(double levelPercent, double base, double factor) {
    return Math.max(0, (int) Math.round(base + (levelPercent * factor)));
  }

  static double bonusDamage(double levelPercent, double base, double factor) {
    return base + (levelPercent * factor);
  }


  static void retortFx(Adaptation<?> owner, PlayerRef p) {
    Fx.now(owner, p.location().add(0D, 1.0D, 0D), FxPriority.COMBAT)
        .burst(Particles.FLAME, 6, 0.35D)
        .particle(Particles.LAVA, 2, 0D, 0.3D, 0D, 0.2D, 0.0D)
        .chord(Sounds.ENTITY_BLAZE_SHOOT, 0.4F, 1.3F, Sounds.BLOCK_FIRE_AMBIENT, 0.3F, 1.0F);
  }

  @ConfigDescription("While burning, melee attackers catch fire and your own strikes deal bonus fire damage.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Reflect Fire Ticks Base for the Nether Magma Skin adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reflectFireTicksBase = 40;
    @ConfigDoc(value = "Controls Reflect Fire Ticks Factor for the Nether Magma Skin adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reflectFireTicksFactor = 60;
    @ConfigDoc(value = "Controls Bonus Damage Base for the Nether Magma Skin adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusDamageBase = 0.5;
    @ConfigDoc(value = "Controls Bonus Damage Factor for the Nether Magma Skin adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusDamageFactor = 2.5;
    @ConfigDoc(value = "Controls Bonus Fire Ticks Base for the Nether Magma Skin adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusFireTicksBase = 40;
    @ConfigDoc(value = "Controls Bonus Fire Ticks Factor for the Nether Magma Skin adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusFireTicksFactor = 40;
    @ConfigDoc(value = "Controls Xp On Reflect for the Nether Magma Skin adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnReflect = 6;
    @ConfigDoc(value = "Controls Xp Per Bonus Damage for the Nether Magma Skin adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerBonusDamage = 3;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 4;
      initialCost = 1;
    }
  }
}
