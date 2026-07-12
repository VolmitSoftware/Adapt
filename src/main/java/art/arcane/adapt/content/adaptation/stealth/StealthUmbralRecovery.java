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

package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class StealthUmbralRecovery extends SimpleAdaptation<StealthUmbralRecovery.Config> {
  public StealthUmbralRecovery() {
    super("stealth-umbral-recovery");
    registerConfiguration(Config.class);
    setIcon(Material.COOKED_BEEF);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BEEF)
        .key("challenge_stealth_umbral_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.COOKED_BEEF)
            .key("challenge_stealth_umbral_2k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_stealth_umbral_200", "stealth.umbral-recovery.recoveries", 200, 400);
    registerMilestone("challenge_stealth_umbral_2k", "stealth.umbral-recovery.recoveries", 2000, 1500);
  }

  static int computeRefund(double base, double factor, double percent) {
    return Math.max(1, (int) Math.round(base + (percent * factor)));
  }

  static int computeExtensionTicks(double base, double factor, double percent) {
    return Math.max(1, (int) Math.round(base + (percent * factor)));
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRefund(level), 0), 1);
    statLore(v, Form.duration(getExtensionTicks(level) * 50D, 1), 2);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    Player killer = e.getEntity().getKiller();
    if (killer == null || !killer.isSneaking()) {
      return;
    }

    int level = getActiveLevel(killer);
    if (level <= 0) {
      return;
    }

    int refund = getRefund(level);
    int extension = getExtensionTicks(level);
    int maxInvisibility = Math.max(extension, getConfig().maxInvisibilityTicks);
    J.runEntity(killer, () -> applyRecovery(killer, refund, extension, maxInvisibility));
  }

  private void applyRecovery(Player p, int refund, int extension, int maxInvisibility) {
    if (!p.isOnline()) {
      return;
    }

    boolean recovered = false;
    if (p.getFoodLevel() < 20) {
      p.setFoodLevel(Math.min(20, p.getFoodLevel() + refund));
      p.setSaturation(Math.min(p.getFoodLevel(), p.getSaturation() + refund));
      recovered = true;
    }

    boolean extended = false;
    PotionEffect current = p.getPotionEffect(PotionEffectType.INVISIBILITY);
    if (current != null) {
      int newDuration = Math.min(maxInvisibility, current.getDuration() + extension);
      if (newDuration > current.getDuration()) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, newDuration, current.getAmplifier(), false, false, false), true);
        extended = true;
      }
    }

    if (!recovered && !extended) {
      return;
    }

    addStat(p, "stealth.umbral-recovery.recoveries", 1);
    xp(p, getConfig().xpOnRecovery);
    fx(p.getEyeLocation(), FxPriority.TRAIL)
        .burst(Particles.SMOKE, 4, 0.2D)
        .particle(Particles.VILLAGER_HAPPY, extended ? 3 : 1, 0, 0.2D, 0, 0.1D, 0)
        .sound(Sound.ENTITY_GENERIC_EAT, 0.35F, 1.4F);
  }

  private int getRefund(int level) {
    return computeRefund(getConfig().refundBase, getConfig().refundFactor, getLevelPercent(level));
  }

  private int getExtensionTicks(int level) {
    return computeExtensionTicks(getConfig().extensionTicksBase, getConfig().extensionTicksFactor, getLevelPercent(level));
  }

  @ConfigDescription("Kills made while sneaking refund hunger and extend any active invisibility window.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base hunger points refunded per sneaking kill.", impact = "Higher values restore more food per kill.")
    double refundBase = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra hunger points refunded gained across levels.", impact = "Higher values refund more food at higher levels.")
    double refundFactor = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base invisibility extension per sneaking kill, in ticks.", impact = "Higher values extend active invisibility longer per kill.")
    double extensionTicksBase = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra invisibility extension gained across levels, in ticks.", impact = "Higher values extend invisibility more at higher levels.")
    double extensionTicksFactor = 120;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum invisibility duration reachable through extension, in ticks.", impact = "Caps how long stacked kills can keep you invisible.")
    int maxInvisibilityTicks = 1200;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Experience granted per recovery.", impact = "Higher values level the adaptation faster.")
    double xpOnRecovery = 8;

    public Config() {
      baseCost = 4;
      costFactor = 0.35;
      maxLevel = 4;
      initialCost = 3;
    }
  }
}
