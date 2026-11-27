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

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.EntityExhaustionEvent;
import art.arcane.adapt.platform.value.Id;

public class AgilityMarathoner extends SimpleAdaptation<AgilityMarathoner.Config> {
  public static final Id ID = Id.adapt("agility/marathoner");
  public AgilityMarathoner() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("leather_boots"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("leather_boots"))
        .key("challenge_agility_marathoner_5k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("golden_boots"))
            .key("challenge_agility_marathoner_50k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_agility_marathoner_5k", "agility.marathoner.saturation-saved", 5000, 400);
    registerMilestone("challenge_agility_marathoner_50k", "agility.marathoner.saturation-saved", 50000, 1500);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getDrainReduction(level), 0), 1);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityExhaustionEvent e) {
    PlayerRef p = e.player();
    EntityExhaustionEvent.Reason reason = e.reason();
    if (!isSprintExhaustion(reason, p.isSprinting())) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    float before = e.exhaustion();
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

  public static boolean isSprintExhaustion(EntityExhaustionEvent.Reason reason, boolean sprinting) {
    return reason == EntityExhaustionEvent.Reason.SPRINT
        || reason == EntityExhaustionEvent.Reason.JUMP_SPRINT
        || (reason == EntityExhaustionEvent.Reason.WALK && sprinting);
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
      baseCost = 1;
      costFactor = 0.5;
      maxLevel = 5;
      initialCost = 1;
    }
  }
}
