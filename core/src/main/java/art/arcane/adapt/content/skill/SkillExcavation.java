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
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.SkillConfig;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.xp.CombatXp;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.excavation.ExcavationBurrow;
import art.arcane.adapt.content.adaptation.excavation.ExcavationDropToInventory;
import art.arcane.adapt.content.adaptation.excavation.ExcavationEarthMover;
import art.arcane.adapt.content.adaptation.excavation.ExcavationGraveDigger;
import art.arcane.adapt.content.adaptation.excavation.ExcavationHaste;
import art.arcane.adapt.content.adaptation.excavation.ExcavationMudlark;
import art.arcane.adapt.content.adaptation.excavation.ExcavationOmniTool;
import art.arcane.adapt.content.adaptation.excavation.ExcavationSeismicPing;
import art.arcane.adapt.content.adaptation.excavation.ExcavationSoftFall;
import art.arcane.adapt.content.adaptation.excavation.ExcavationSpelunker;
import art.arcane.adapt.content.adaptation.excavation.ExcavationTreasureHunter;
import art.arcane.adapt.content.adaptation.excavation.ExcavationTunneler;
import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.SkillMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import lombok.NoArgsConstructor;

import java.util.Set;

public class SkillExcavation extends SimpleSkill<SkillExcavation.Config> {
  private static final Set<String> LOOSE_GROUND = Set.of("gravel", "sand", "red_sand", "clay", "soul_sand", "soul_soil");
  private final Cooldowns cooldowns = cooldowns();

  public SkillExcavation() {
    super("excavation", SkillPresentation.of(SkillMessages.EXCAVATION_NAME, SkillMessages.EXCAVATION_ICON, SkillMessages.EXCAVATION_DESCRIPTION));
    registerConfiguration(Config.class);
    setColor(C.YELLOW);
    setInterval(5953);
    setIcon(Id.minecraft("diamond_shovel"));
    registerAdaptation(new ExcavationHaste());
    registerAdaptation(new ExcavationSpelunker());
    registerAdaptation(new ExcavationOmniTool());
    registerAdaptation(new ExcavationDropToInventory());
    registerAdaptation(new ExcavationSeismicPing());
    registerAdaptation(new ExcavationTunneler());
    registerAdaptation(new ExcavationTreasureHunter());
    registerAdaptation(new ExcavationSoftFall());
    registerAdaptation(new ExcavationEarthMover());
    registerAdaptation(new ExcavationBurrow());
    registerAdaptation(new ExcavationGraveDigger());
    registerAdaptation(new ExcavationMudlark());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("wooden_shovel")).key("challenge_excavate_1k")
        .model(CustomModel.get(Id.minecraft("wooden_shovel"), "advancement", "excavation", "challenge_excavate_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
            .icon(Id.minecraft("knowledge_book"))
            .key("challenge_excavate_5k")
            .model(CustomModel.get(Id.minecraft("knowledge_book"), "advancement", "excavation", "challenge_excavate_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
                .icon(Id.minecraft("stone_shovel"))
                .key("challenge_excavate_50k")
                .model(CustomModel.get(Id.minecraft("stone_shovel"), "advancement", "excavation", "challenge_excavate_50k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerMilestone("challenge_excavate_1k", "excavation.blocks.broken", 1000, () -> getConfig().challengeExcavationReward);
    registerMilestone("challenge_excavate_5k", "excavation.blocks.broken", 5000, () -> getConfig().challengeExcavationReward);
    registerMilestone("challenge_excavate_50k", "excavation.blocks.broken", 50000, () -> getConfig().challengeExcavationReward);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("golden_shovel")).key("challenge_dig_damage_1k")
        .model(CustomModel.get(Id.minecraft("golden_shovel"), "advancement", "excavation", "challenge_dig_damage_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_shovel"))
            .key("challenge_dig_damage_10k")
            .model(CustomModel.get(Id.minecraft("diamond_shovel"), "advancement", "excavation", "challenge_dig_damage_10k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_dig_damage_1k", "excavation.damage", 1000, () -> getConfig().challengeExcavationReward);
    registerMilestone("challenge_dig_damage_10k", "excavation.damage", 10000, () -> getConfig().challengeExcavationReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("clay_ball")).key("challenge_dig_value_5k")
        .model(CustomModel.get(Id.minecraft("clay_ball"), "advancement", "excavation", "challenge_dig_value_5k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("brick"))
            .key("challenge_dig_value_50k")
            .model(CustomModel.get(Id.minecraft("brick"), "advancement", "excavation", "challenge_dig_value_50k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_dig_value_5k", "excavation.blocks.value", 5000, () -> getConfig().challengeExcavationReward);
    registerMilestone("challenge_dig_value_50k", "excavation.blocks.value", 50000, () -> getConfig().challengeExcavationReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("gravel")).key("challenge_dig_gravel_500")
        .model(CustomModel.get(Id.minecraft("gravel"), "advancement", "excavation", "challenge_dig_gravel_500"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("flint"))
            .key("challenge_dig_gravel_5k")
            .model(CustomModel.get(Id.minecraft("flint"), "advancement", "excavation", "challenge_dig_gravel_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_dig_gravel_500", "excavation.gravel", 500, () -> getConfig().challengeExcavationReward);
    registerMilestone("challenge_dig_gravel_5k", "excavation.gravel", 5000, () -> getConfig().challengeExcavationReward * 2);
  }


  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!getConfig().getXpForAttackingWithTools
        || !(e.damager() instanceof PlayerRef p)
        || !checkValidEntity(e.entity())
        || !isShovel(p.inventory().mainHand())) {
      return;
    }
    shouldReturnForPlayer(p, e, () -> handleEntityDamageByPlayer(p, e));
  }

  private void handleEntityDamageByPlayer(PlayerRef p, EntityDamageByEntityEvent e) {
    AdaptPlayer a = getPlayer(p);
    ItemRef hand = a.getPlayer().inventory().mainHand();
    if (isShovel(hand)) {
      if (!cooldowns.isReady(p.uuid(), getConfig().cooldownDelay)) {
        return;
      }
      cooldowns.mark(p.uuid());
      double creditedDamage = CombatXp.credit(getPlayer(p).getData().getCombatXp(), getName(), p, e);
      if (creditedDamage <= 0D) {
        return;
      }
      addStat(p, "excavation.damage", creditedDamage);
      xp(a.getPlayer(), e.entity().location(), getConfig().axeDamageXPMultiplier * creditedDamage, "excavation:combat");
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    PlayerRef p = e.player();
    if (!isShovel(p.inventory().mainHand())) {
      return;
    }
    shouldReturnForPlayer(p, e, () -> handleBlockBreakWithShovel(p, e));

  }

  private void handleBlockBreakWithShovel(PlayerRef p, BlockBreakEvent e) {
    if (XpProvenance.breakXpMultiplier(e.block()) <= 0) {
      return;
    }

    addStat(p, "excavation.blocks.broken", 1);
    addStat(p, "excavation.blocks.value", getValue(e.block().state()));
    Id blockType = e.block().type();
    if (blockType.isMinecraft() && LOOSE_GROUND.contains(blockType.path())) {
      addStat(p, "excavation.gravel", 1);
    }
    if (!cooldowns.isReady(p.uuid(), getConfig().cooldownDelay)) {
      return;
    }
    cooldowns.mark(p.uuid());
    double v = getValue(blockType);
    xp(p, e.block().center(), blockXP(e.block(), v));
  }

  @Override
  public double getValue(Id type) {
    ItemOps items = AdaptCore.platform().host().items();
    double value = super.getValue(type) * getConfig().valueXPMultiplier;
    value += Math.min(getConfig().maxHardnessBonus, items.blockHardness(type));
    value += Math.min(getConfig().maxBlastResistanceBonus, items.blastResistance(type));
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
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Get Xp For Attacking With Tools for the Excavation skill.", impact = "True enables this behavior and false disables it.")
    boolean getXpForAttackingWithTools = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Hardness Bonus for the Excavation skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxHardnessBonus = 9;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Blast Resistance Bonus for the Excavation skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxBlastResistanceBonus = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Excavation Reward for the Excavation skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeExcavationReward = 1200;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Value XPMultiplier for the Excavation skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double valueXPMultiplier = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Excavation skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 1250;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Axe Damage XPMultiplier for the Excavation skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double axeDamageXPMultiplier = 4.0;
  }
}
