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

package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;

public class AxeSunder extends SimpleAdaptation<AxeSunder.Config> {
  public static final Id ID = Id.adapt("axe/sunder");
  private static final Rgb SUNDER_ORANGE = new Rgb(200, 120, 40);
  private final AxeSunderMarks marks = new AxeSunderMarks(this, Id.of("adapt", "axe-sunder"), this::shredFx,
      this::rewardApplication);

  public AxeSunder() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("netherite_axe"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_axe"))
        .key("challenge_axe_sunder_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_axe"))
            .key("challenge_axe_sunder_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_axe_sunder_500", "axe.sunder.stacks-applied", 500, 500);
    registerMilestone("challenge_axe_sunder_5k", "axe.sunder.stacks-applied", 5000, 1800);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, TextFormat.RED, "- ", Format.f(getShredPerStack(level), 1), 1);
    statLore(v, TextFormat.RED, "x ", getMaxStacks(level), 2);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(getConfig().durationTicks * 50D, 1), 3);
    double toughnessShred = getShredPerStack(level) * getConfig().toughnessShredPerStackRatio;
    if (toughnessShred > 0D) {
      statLore(v, TextFormat.RED, "- ", Format.f(toughnessShred, 1), 4);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    Adaptation.MeleeContext combat = resolveMeleeContext(e, ItemKinds::isAxe);
    if (combat == null) {
      return;
    }

    int level = combat.level();
    marks.apply(combat.attacker(), combat.target(), getMaxStacks(level), getShredPerStack(level),
        getConfig().toughnessShredPerStackRatio, getConfig().durationTicks);
  }

  private void shredFx(LivingRef target) {
    fx(target.location().add(0D, target.height() * 0.6D, 0D), FxPriority.COMBAT)
        .dustRing(SUNDER_ORANGE, 0.7D, 10, 1.0F)
        .particle(Particles.CRIT, 6, 0D, 0D, 0D, 0.25D, 0.05D)
        .sound(Sounds.ITEM_SHIELD_BREAK, 0.5F, 1.2F);
  }

  private void rewardApplication(PlayerRef attacker) {
    addStat(attacker, "axe.sunder.stacks-applied", 1);
    xp(attacker, getConfig().xpPerStack);
  }

  private double getShredPerStack(int level) {
    return getConfig().shredPerStackBase + (getLevelPercent(level) * getConfig().shredPerStackFactor);
  }

  private int getMaxStacks(int level) {
    return Math.max(1, getConfig().maxStacksBase + (int) Math.round(getLevelPercent(level) * getConfig().maxStacksFactor));
  }

  @Override
  public void unregister() {
    marks.clear();
    super.unregister();
  }

  @ConfigDescription("Axe hits shred a target's armor in stacking layers that fade over time.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Armor points removed per Sunder stack before level scaling.", impact = "Higher values make each stack strip more armor.")
    double shredPerStackBase = 1.5;
    @ConfigDoc(value = "Extra armor points per stack granted by leveling.", impact = "Higher values reward leveling with deeper armor shred.")
    double shredPerStackFactor = 1.5;
    @ConfigDoc(value = "Fraction of each stack's armor shred also removed from armor toughness.", impact = "Higher values make Sunder bite harder against heavily armored targets; 0 disables toughness shredding.")
    double toughnessShredPerStackRatio = 0.5;
    @ConfigDoc(value = "Base maximum Sunder stacks before level scaling.", impact = "Higher values allow more stacks at low levels.")
    int maxStacksBase = 2;
    @ConfigDoc(value = "Additional maximum stacks granted at maximum level.", impact = "Higher values let higher levels reach more stacks.")
    int maxStacksFactor = 3;
    @ConfigDoc(value = "Ticks a Sunder application lasts before that layer decays.", impact = "Higher values keep shredded armor down longer between hits.")
    int durationTicks = 120;
    @ConfigDoc(value = "XP granted per Sunder stack applied.", impact = "Higher values accelerate skill progression from shredding armor.")
    double xpPerStack = 3;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 2;
    }
  }
}
