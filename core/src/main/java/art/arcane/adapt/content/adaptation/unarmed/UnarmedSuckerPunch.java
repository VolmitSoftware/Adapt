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

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.UnarmedMessages;
import art.arcane.adapt.platform.entity.LastDamage;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

public class UnarmedSuckerPunch extends SimpleAdaptation<UnarmedSuckerPunch.Config> {
  private static final Rgb KNOCKOUT_COLOR = new Rgb(255, 255, 255);

  public UnarmedSuckerPunch() {
    super("unarmed-sucker-punch");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("obsidian"));
    setInterval(4944);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_ingot"))
        .key("challenge_unarmed_sucker_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_unarmed_sucker_500", "unarmed.sucker-punch.sucker-punches", 500, 400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("diamond"))
        .key("challenge_unarmed_knockout")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_unarmed_knockout", "unarmed.sucker-punch.one-punch-kills", 50, 1000);
  }


  @Override
  public void addStats(int level, StatLines v) {
    double f = getLevelPercent(level);
    double d = getDamage(f);
    statLore(v, Form.pc(d, 0), 1);
    v.addLore(C.GRAY + AdaptLanguage.text(UnarmedMessages.SUCKER_PUNCH_LORE2));
  }

  private double getDamage(double f) {
    return getConfig().baseDamage + (f * getConfig().damageFactor);
  }

  @Handle(priority = EventPriority.NORMAL)
  public void on(EntityDamageByEntityEvent e) {
    Adaptation.AttackContext attack = resolveAttackContext(e);
    if (attack == null) {
      return;
    }

    PlayerRef p = attack.attacker();
    if (!isSuckerPunch(p)) {
      return;
    }
    double factor = getLevelPercent(attack.level());

    if (factor <= 0) {
      return;
    }

    e.setDamage(e.damage() * (1 + getDamage(factor)));
    Loc impact = e.entity().location().add(0, 1.0D, 0);
    Vec3 look = p.location().direction();
    fx(impact, FxPriority.COMBAT)
        .particle(Particles.CRIT, 12, 0, 0, 0, 0.2D, 0.1D)
        .particle(Particles.SWEEP_ATTACK, 1, 0, 0, 0, 0, 0)
        .trail(Particles.CRIT, look.x(), look.y(), look.z(), 1.2D, 6)
        .chord(Sounds.ENTITY_PLAYER_ATTACK_STRONG, 1.0F, 1.8F, Sounds.ENTITY_PLAYER_ATTACK_CRIT, 0.7F, 1.4F, Sounds.BLOCK_BASALT_BREAK, 0.9F, 0.55F);
    xp(p, 6.221 * e.damage(), "sucker-punch");
    addStat(p, "unarmed.sucker-punch.sucker-punches", 1);
    if (e.damage() > 5) {
      xp(p, 0.42 * e.damage(), "bonus-damage");
      timeline(e.entity().location().add(0, 1.0D, 0))
          .duration(4)
          .priority(FxPriority.COMBAT)
          .frame((fx, tick, progress) -> fx.ring(Particles.ELECTRIC_SPARK, 1.2D - (0.9D * progress), 10, 0))
          .start();
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    LivingRef victim = e.entity();
    LastDamage last = victim.lastDamage();
    if (last != null
        && last.damager() instanceof PlayerRef p
        && hasActiveAdaptation(p)
        && p.isSprinting()
        && p.inventory().mainHand().type().isAir()) {
      if (victim.maxHealth() <= last.finalDamage()) {
        addStat(p, "unarmed.sucker-punch.one-punch-kills", 1);
        Loc ko = victim.location().add(0, 1.0D, 0);
        fx(ko, FxPriority.COMBAT)
            .particle(Particles.FLASH, 1, 0, 0, 0, 0, 0)
            .burst(Particles.CRIT, 24, 0.4D)
            .chord(Sounds.ITEM_TOTEM_USE, 0.5F, 1.9F, Sounds.ENTITY_GENERIC_EXPLODE, 0.4F, 1.6F, Sounds.BLOCK_ANVIL_LAND, 0.35F, 1.8F);
        timeline(victim.location())
            .duration(6)
            .priority(FxPriority.TRANSITION)
            .frame((fx, tick, progress) -> fx.dustRing(KNOCKOUT_COLOR, 2.5D * progress, 24, 1.1F))
            .start();
      }
    }
  }

  /** A sprinting hit with an empty main hand, the punch Sucker Punch and Titan Fists' sprint punch reward. */
  public static boolean isSuckerPunch(PlayerRef p) {
    return p.isSprinting() && p.inventory().mainHand().type().isAir();
  }

  @ConfigDescription("Sprint punches with an empty main hand deal extra damage.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Damage for the Unarmed Sucker Punch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseDamage = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Factor for the Unarmed Sucker Punch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageFactor = 0.55;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      initialCost = 1;
    }
  }
}
