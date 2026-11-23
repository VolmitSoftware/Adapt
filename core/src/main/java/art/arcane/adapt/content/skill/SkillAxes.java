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

package art.arcane.adapt.content.skill;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.SkillConfig;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.xp.CombatXp;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.axe.AxeBarkHide;
import art.arcane.adapt.content.adaptation.axe.AxeChop;
import art.arcane.adapt.content.adaptation.axe.AxeCleave;
import art.arcane.adapt.content.adaptation.axe.AxeCraftLogSwap;
import art.arcane.adapt.content.adaptation.axe.AxeDropToInventory;
import art.arcane.adapt.content.adaptation.axe.AxeGroundSmash;
import art.arcane.adapt.content.adaptation.axe.AxeIrisFeller;
import art.arcane.adapt.content.adaptation.axe.AxeLeafVeinminer;
import art.arcane.adapt.content.adaptation.axe.AxeShieldSplitter;
import art.arcane.adapt.content.adaptation.axe.AxeSunder;
import art.arcane.adapt.content.adaptation.axe.AxeThrowingAxe;
import art.arcane.adapt.content.adaptation.axe.AxeWoodVeinminer;
import art.arcane.adapt.content.integration.iris.IrisTreeFellerPort;
import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.SkillMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import lombok.NoArgsConstructor;

import java.util.Set;

public class SkillAxes extends SimpleSkill<SkillAxes.Config> {
  private static final Set<Id> LOG_LIKE = Set.of(
      Id.minecraft("mushroom_stem"),
      Id.minecraft("brown_mushroom_block"),
      Id.minecraft("red_mushroom_block"),
      Id.minecraft("mangrove_roots"),
      Id.minecraft("muddy_mangrove_roots"));
  private final Cooldowns cooldowns = cooldowns();

  public SkillAxes() {
    this(AdaptPorts.get(IrisTreeFellerPort.class, IrisTreeFellerPort.NONE).isAvailable());
  }

  SkillAxes(boolean irisTreeFellerAvailable) {
    super("axes", SkillPresentation.of(SkillMessages.AXES_NAME, SkillMessages.AXES_ICON, SkillMessages.AXES_DESCRIPTION));
    registerConfiguration(Config.class);
    setColor(C.YELLOW);
    setInterval(5251);
    setIcon(Id.minecraft("golden_axe"));
    registerAdaptation(new AxeGroundSmash());
    registerAdaptation(new AxeChop());
    registerAdaptation(new AxeDropToInventory());
    registerAdaptation(new AxeLeafVeinminer());
    if (irisTreeFellerAvailable) {
      registerAdaptation(new AxeIrisFeller());
    }
    registerAdaptation(new AxeWoodVeinminer());
    registerAdaptation(new AxeCraftLogSwap());
    registerAdaptation(new AxeThrowingAxe());
    registerAdaptation(new AxeSunder());
    registerAdaptation(new AxeCleave());
    registerAdaptation(new AxeBarkHide());
    registerAdaptation(new AxeShieldSplitter());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("wooden_axe")).key("challenge_chop_1k")
        .model(CustomModel.get(Id.minecraft("wooden_axe"), "advancement", "axes", "challenge_chop_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
            .icon(Id.minecraft("stone_axe"))
            .key("challenge_chop_5k")
            .model(CustomModel.get(Id.minecraft("stone_axe"), "advancement", "axes", "challenge_chop_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
                .icon(Id.minecraft("iron_axe"))
                .key("challenge_chop_50k")
                .model(CustomModel.get(Id.minecraft("iron_axe"), "advancement", "axes", "challenge_chop_50k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerMilestone("challenge_chop_1k", "axes.blocks.broken", 1000, () -> getConfig().challengeChopReward);
    registerMilestone("challenge_chop_5k", "axes.blocks.broken", 5000, () -> getConfig().challengeChopReward);
    registerMilestone("challenge_chop_50k", "axes.blocks.broken", 50000, () -> getConfig().challengeChopReward);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("golden_axe")).key("challenge_axe_damage_1k")
        .model(CustomModel.get(Id.minecraft("golden_axe"), "advancement", "axes", "challenge_axe_damage_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_axe"))
            .key("challenge_axe_damage_10k")
            .model(CustomModel.get(Id.minecraft("diamond_axe"), "advancement", "axes", "challenge_axe_damage_10k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_axe_damage_1k", "axes.damage", 1000, () -> getConfig().challengeChopReward);
    registerMilestone("challenge_axe_damage_10k", "axes.damage", 10000, () -> getConfig().challengeChopReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("oak_log")).key("challenge_axe_value_5k")
        .model(CustomModel.get(Id.minecraft("oak_log"), "advancement", "axes", "challenge_axe_value_5k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("dark_oak_log"))
            .key("challenge_axe_value_50k")
            .model(CustomModel.get(Id.minecraft("dark_oak_log"), "advancement", "axes", "challenge_axe_value_50k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_axe_value_5k", "axes.blocks.value", 5000, () -> getConfig().challengeChopReward);
    registerMilestone("challenge_axe_value_50k", "axes.blocks.value", 50000, () -> getConfig().challengeChopReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("oak_leaves")).key("challenge_leaves_500")
        .model(CustomModel.get(Id.minecraft("oak_leaves"), "advancement", "axes", "challenge_leaves_500"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("azalea_leaves"))
            .key("challenge_leaves_5k")
            .model(CustomModel.get(Id.minecraft("azalea_leaves"), "advancement", "axes", "challenge_leaves_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_leaves_500", "axes.leaves", 500, () -> getConfig().challengeChopReward);
    registerMilestone("challenge_leaves_5k", "axes.leaves", 5000, () -> getConfig().challengeChopReward * 2);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!getConfig().getXpForAttackingWithTools
        || !(e.damager() instanceof PlayerRef p)
        || !checkValidEntity(e.entity())
        || !isAxe(p.inventory().mainHand())) {
      return;
    }

    shouldReturnForPlayer(p, e, () -> {
      if (p.isDead() || p.isInvulnerable() || !cooldowns.isReady(p.uuid(), getConfig().cooldownDelay)) {
        return;
      }

      double creditedDamage = CombatXp.credit(getPlayer(p).getData().getCombatXp(), getName(), p, e);
      if (creditedDamage <= 0D) {
        return;
      }
      cooldowns.mark(p.uuid());
      AdaptPlayer adaptPlayer = getPlayer(p);
      adaptPlayer.getData().addStat("axes.damage", creditedDamage);
      xp(p, e.entity().location(), combatXp(getConfig().axeHitXp, getConfig().axeDamageXPMultiplier, creditedDamage),
          "axes:combat");
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    PlayerRef p = e.player();
    if (!isAxe(p.inventory().mainHand())) {
      return;
    }

    shouldReturnForPlayer(p, e, () -> {
      if (XpProvenance.breakXpMultiplier(e.block()) <= 0D) {
        return;
      }
      Id blockType = e.block().type();
      if (isLogMaterial(blockType)) {
        double value = getValue(blockType);
        AdaptPlayer adaptPlayer = getPlayer(p);
        adaptPlayer.getData().addStat("axes.blocks.broken", 1);
        adaptPlayer.getData().addStat("axes.blocks.value", getValue(e.block().state()));
        xp(p, e.block().center(), blockXP(e.block(), value), "axes:chop");
      }
      if (blockType.path().endsWith("_leaves")) {
        addStat(p, "axes.leaves", 1);
      }
    });
  }

  static double combatXp(double hitXp, double damageMultiplier, double creditedDamage) {
    if (!Double.isFinite(creditedDamage) || creditedDamage <= 0D) {
      return 0D;
    }
    return Math.max(0D, hitXp) + Math.max(0D, damageMultiplier) * creditedDamage;
  }

  private boolean isLogMaterial(Id type) {
    String name = type.path();
    return LOG_LIKE.contains(type)
        || name.endsWith("_log")
        || name.endsWith("_wood");
  }

  @Override
  public double getValue(Id type) {
    ItemOps items = AdaptCore.platform().host().items();
    double value = super.getValue(type) * getConfig().valueXPMultiplier;
    float hardness = items.blockHardness(type);
    String typeName = type.path();
    value += Math.min(getConfig().maxHardnessBonus, hardness);
    value += Math.min(getConfig().maxBlastResistanceBonus, items.blastResistance(type));

    if (typeName.endsWith("_log") || typeName.endsWith("_wood")) {
      value += getConfig().logOrWoodXPMultiplier;
    }
    if (typeName.endsWith("_leaves")) {
      value += getConfig().leavesMultiplier;
    }

    if (hardness == 0) {
      value = 0;
    }

    return value;
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @NoArgsConstructor
  protected static class Config extends SkillConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    String skillColor = "&e";
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Get Xp For Attacking With Tools for the Axes skill.", impact = "True enables this behavior and false disables it.")
    boolean getXpForAttackingWithTools = true;

    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Hardness Bonus for the Axes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxHardnessBonus = 9;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Blast Resistance Bonus for the Axes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxBlastResistanceBonus = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Chop Reward for the Axes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeChopReward = 1750;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Log Or Wood XPMultiplier for the Axes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double logOrWoodXPMultiplier = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Leaves Multiplier for the Axes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double leavesMultiplier = 0.75;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds between Axes combat XP awards.", impact = "Higher values reduce repeated combat XP frequency; lower values reward hits more often.")
    long cooldownDelay = 1500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Value XPMultiplier for the Axes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double valueXPMultiplier = 0.175;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Axe Damage XPMultiplier for the Axes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double axeDamageXPMultiplier = 7.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Flat XP paid for each credited axe hit, before the damage XP.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double axeHitXp = 5.0;
  }
}
