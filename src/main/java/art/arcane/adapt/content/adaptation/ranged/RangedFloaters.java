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

package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;

public class RangedFloaters extends SimpleAdaptation<RangedFloaters.Config> {
  public RangedFloaters() {
    super("ranged-floaters");
    registerConfiguration(Config.class);
    setIcon(Material.SHULKER_SHELL);
    setInterval(2400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SHULKER_SHELL)
        .key("challenge_ranged_floaters_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_ranged_floaters_200", "ranged.floaters.targets-levitated", 200, 300);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getProcChance(level), 0), 1);
    statLore(v, Form.duration(getDurationTicks(level) * 50D, 1), 2);
    statLore(v, (1 + getAmplifier(level)), 3);
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void on(EntityDamageByEntityEvent e) {
    art.arcane.adapt.api.adaptation.Adaptation.ProjectileContext combat = resolveProjectileContext(e);
    if (combat == null) {
      return;
    }

    Player p = combat.attacker();
    LivingEntity target = combat.target();
    int level = combat.level();
    if (ThreadLocalRandom.current().nextDouble() > getProcChance(level)) {
      return;
    }

    target.addPotionEffect(new PotionEffect(
        PotionEffectType.LEVITATION,
        getDurationTicks(level),
        getAmplifier(level),
        true,
        true,
        true
    ), true);
    addStat(p, "ranged.floaters.targets-levitated", 1);

    int amp = getAmplifier(level);
    fx(target, FxPriority.COMBAT)
        .dustRing(Color.fromRGB(230, 245, 255), 0.7D + (amp * 0.2D), 12, 1.0F)
        .column(Particles.END_ROD, Math.min(16, 10 + (amp * 3)), 2.2D)
        .chord(Sound.ENTITY_SHULKER_SHOOT, 0.6F, 1.45F, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.4F, 1.2F);
    xp(p, getConfig().skillXpOnProc);
  }

  private double getProcChance(int level) {
    return Math.min(getConfig().maxChance, getConfig().chanceBase + (getLevelPercent(level) * getConfig().chanceFactor));
  }

  private int getDurationTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().durationTicksBase + (getLevelPercent(level) * getConfig().durationTicksFactor)));
  }

  private int getAmplifier(int level) {
    return Math.max(0, (int) Math.floor(getLevelPercent(level) * getConfig().maxAmplifier));
  }

  @Override
  public void onTick() {

  }

  @ConfigDescription("Projectiles have a chance to apply levitation to targets.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Chance Base for the Ranged Floaters adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double chanceBase = 0.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Chance Factor for the Ranged Floaters adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double chanceFactor = 0.58;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Chance for the Ranged Floaters adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxChance = 0.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Duration Ticks Base for the Ranged Floaters adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durationTicksBase = 26.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Duration Ticks Factor for the Ranged Floaters adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durationTicksFactor = 110.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Amplifier for the Ranged Floaters adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxAmplifier = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Skill Xp On Proc for the Ranged Floaters adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double skillXpOnProc = 8.0;

    public Config() {
      costFactor = 0.78;
      maxLevel = 6;
      initialCost = 4;
    }
  }
}
