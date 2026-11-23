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

package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

public class HunterAdrenaline extends SimpleAdaptation<HunterAdrenaline.Config> {
  private static final Rgb LOW_HEALTH_RED = new Rgb(140, 0, 0);

  public HunterAdrenaline() {
    super("hunter-adrenaline");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("leather_helmet"));
    setInterval(1911);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_sword"))
        .key("challenge_hunter_adrenaline_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_sword"))
            .key("challenge_hunter_adrenaline_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_hunter_adrenaline_100", "hunter.adrenaline.low-health-kills", 100, 400);
    registerMilestone("challenge_hunter_adrenaline_2500", "hunter.adrenaline.low-health-kills", 2500, 1500);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getDamage(level), 0), 1);
  }

  private double getDamage(int level) {
    return ((getLevelPercent(level) * getConfig().damageFactor) + getConfig().damageBase);
  }

  @Handle
  public void on(EntityDamageByEntityEvent e) {
    Adaptation.AttackContext attack = resolveAttackContext(e);
    if (attack == null) {
      return;
    }

    PlayerRef p = attack.attacker();
    double damageMax = getDamage(attack.level());
    double hpp = p.health() / p.maxHealth();

    if (hpp >= 1) {
      return;
    }

    damageMax *= (1D - hpp);
    e.setDamage(e.damage() * (damageMax + 1D));

    if (hpp >= 0.35D) {
      return;
    }

    FxEmitter flare = fx(p.eyeLocation().add(0, -0.4D, 0), FxPriority.COMBAT)
        .dustBurst(LOW_HEALTH_RED, 7, 0.25D, 1.1F)
        .sound(Sounds.ENTITY_PLAYER_ATTACK_CRIT, 0.6F, 0.7F);
    if (hpp < 0.2D) {
      flare.chord(Sounds.ENTITY_ZOGLIN_ATTACK, 0.25F, 1.6F, Sounds.BLOCK_NOTE_BLOCK_BASS, 0.4F, 0.5F);
    }

    if (!(e.entity() instanceof LivingRef victim)) {
      return;
    }

    Loc victimCenter = victim.location().add(0, victim.height() * 0.5D, 0);
    fx(victimCenter, FxPriority.COMBAT).burst(Particles.CRIT_MAGIC, 3, 0.2D);

    if (victim.health() - e.finalDamage() > 0) {
      return;
    }

    addStat(p, "hunter.adrenaline.low-health-kills", 1);
    timeline(victimCenter)
        .duration(4)
        .priority(FxPriority.COMBAT)
        .cullRadius(32)
        .frame((f, tick, progress) -> {
          f.dustRing(LOW_HEALTH_RED, 0.9D - (0.7D * progress), 10, 1.1F);
          if (tick == 0) {
            f.sound(Sounds.ENTITY_PLAYER_ATTACK_STRONG, 0.7F, 0.9F);
          }
        })
        .start();
  }


  @ConfigDescription("Deal more melee damage the lower your health is.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Base for the Hunter Adrenaline adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageBase = 0.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Factor for the Hunter Adrenaline adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageFactor = 0.21;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
