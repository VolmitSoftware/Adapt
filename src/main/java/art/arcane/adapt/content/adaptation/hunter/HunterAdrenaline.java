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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class HunterAdrenaline extends SimpleAdaptation<HunterAdrenaline.Config> {
  private static final Color LOW_HEALTH_RED = Color.fromRGB(140, 0, 0);

  public HunterAdrenaline() {
    super("hunter-adrenaline");
    registerConfiguration(Config.class);
    setIcon(Material.LEATHER_HELMET);
    setInterval(1911);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_SWORD)
        .key("challenge_hunter_adrenaline_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND_SWORD)
            .key("challenge_hunter_adrenaline_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_hunter_adrenaline_100", "hunter.adrenaline.low-health-kills", 100, 400);
    registerMilestone("challenge_hunter_adrenaline_2500", "hunter.adrenaline.low-health-kills", 2500, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getDamage(level), 0), 1);
  }

  private double getDamage(int level) {
    return ((getLevelPercent(level) * getConfig().damageFactor) + getConfig().damageBase);
  }

  @EventHandler
  public void on(EntityDamageByEntityEvent e) {
    art.arcane.adapt.api.adaptation.Adaptation.AttackContext attack = resolveAttackContext(e);
    if (attack == null) {
      return;
    }

    Player p = attack.attacker();
    double damageMax = getDamage(attack.level());
    double hpp = p.getHealth() / p.getMaxHealth();

    if (hpp >= 1) {
      return;
    }

    damageMax *= (1D - hpp);
    e.setDamage(e.getDamage() * (damageMax + 1D));
    addStat(p, "hunter.adrenaline.low-health-kills", 1);

    if (hpp >= 0.35D) {
      return;
    }

    FxEmitter flare = fx(p.getEyeLocation().subtract(0, 0.4D, 0), FxPriority.COMBAT)
        .dustBurst(LOW_HEALTH_RED, 7, 0.25D, 1.1F)
        .sound(Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.6F, 0.7F);
    if (hpp < 0.2D) {
      flare.chord(Sound.ENTITY_ZOGLIN_ATTACK, 0.25F, 1.6F, Sound.BLOCK_NOTE_BLOCK_BASS, 0.4F, 0.5F);
    }

    if (!(e.getEntity() instanceof LivingEntity victim)) {
      return;
    }

    Location victimCenter = victim.getLocation().add(0, victim.getHeight() * 0.5D, 0);
    fx(victimCenter, FxPriority.COMBAT).burst(Particles.CRIT_MAGIC, 3, 0.2D);

    if (victim.getHealth() - e.getFinalDamage() > 0) {
      return;
    }

    timeline(victimCenter)
        .duration(4)
        .priority(FxPriority.COMBAT)
        .cullRadius(32)
        .frame((f, tick, progress) -> {
          f.dustRing(LOW_HEALTH_RED, 0.9D - (0.7D * progress), 10, 1.1F);
          if (tick == 0) {
            f.sound(Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.7F, 0.9F);
          }
        })
        .start();
  }

  @Override
  public void onTick() {

  }

  @ConfigDescription("Deal more melee damage the lower your health is.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Base for the Hunter Adrenaline adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageBase = 0.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Factor for the Hunter Adrenaline adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageFactor = 0.21;

    public Config() {
      costFactor = 0.4;
      initialCost = 8;
    }
  }
}
