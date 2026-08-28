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

package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityExhaustionEvent;

public class AgilityMarathoner extends SimpleAdaptation<AgilityMarathoner.Config> {
  public AgilityMarathoner() {
    super("agility-marathoner");
    registerConfiguration(Config.class);
    setIcon(Material.LEATHER_BOOTS);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.LEATHER_BOOTS)
        .key("challenge_agility_marathoner_5k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.GOLDEN_BOOTS)
            .key("challenge_agility_marathoner_50k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_agility_marathoner_5k", "agility.marathoner.saturation-saved", 5000, 400);
    registerMilestone("challenge_agility_marathoner_50k", "agility.marathoner.saturation-saved", 50000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getDrainReduction(level), 0), 1);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(EntityExhaustionEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    EntityExhaustionEvent.ExhaustionReason reason = e.getExhaustionReason();
    if (reason != EntityExhaustionEvent.ExhaustionReason.SPRINT
        && reason != EntityExhaustionEvent.ExhaustionReason.JUMP_SPRINT) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    float before = e.getExhaustion();
    if (before <= 0F) {
      return;
    }

    float after = (float) (before * (1D - getDrainReduction(level)));
    if (after >= before) {
      return;
    }

    e.setExhaustion(after);
    double saved = before - after;
    addStat(p, "agility.marathoner.saturation-saved", saved);
    xpSilent(p, saved * getConfig().xpPerSaturationSaved, "agility:marathoner");
  }

  private double getDrainReduction(int level) {
    return Math.min(getConfig().maxDrainReduction,
        getConfig().drainReductionBase + (getLevelPercent(level) * getConfig().drainReductionFactor));
  }

  @ConfigDescription("Sprinting drains less saturation, letting you run further before hunger bites.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base fraction of sprint saturation drain removed before level scaling.", impact = "Higher values save more stamina even at low level.")
    double drainReductionBase = 0.15;
    @ConfigDoc(value = "Additional sprint drain reduction fraction granted at max level.", impact = "Higher values widen the stamina savings from leveling.")
    double drainReductionFactor = 0.45;
    @ConfigDoc(value = "Hard cap on sprint saturation drain reduction regardless of level.", impact = "Higher values allow deeper drain reduction at the top end.")
    double maxDrainReduction = 0.6;
    @ConfigDoc(value = "Experience granted per unit of saturation saved.", impact = "Higher values reward long-distance sprinting with more skill xp.")
    double xpPerSaturationSaved = 0.6;

    public Config() {
      baseCost = 2;
      costFactor = 0.4;
      maxLevel = 5;
      initialCost = 3;
    }
  }
}
