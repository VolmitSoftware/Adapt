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
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeAutosmelt;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeChisel;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeDeepCore;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeDropToInventory;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeGemPolish;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeObsidianRush;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeQuarrySense;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeRepairRhythm;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeSilkSpawner;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeStoneSkin;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeTunnelBore;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeUnbreakablePact;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeVeinminer;
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
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import lombok.NoArgsConstructor;

public class SkillPickaxes extends SimpleSkill<SkillPickaxes.Config> {
  private static final Id SILK_TOUCH = Id.minecraft("silk_touch");
  private final Cooldowns cooldowns = cooldowns();

  public SkillPickaxes() {
    super("pickaxe", SkillPresentation.of(SkillMessages.PICKAXE_NAME, SkillMessages.PICKAXE_ICON, SkillMessages.PICKAXE_DESCRIPTION));
    registerConfiguration(Config.class);
    setColor(C.GOLD);
    setInterval(2750);
    setIcon(Id.minecraft("netherite_pickaxe"));
    registerAdaptation(new PickaxeChisel());
    registerAdaptation(new PickaxeVeinminer());
    registerAdaptation(new PickaxeAutosmelt());
    registerAdaptation(new PickaxeDropToInventory());
    registerAdaptation(new PickaxeSilkSpawner());
    registerAdaptation(new PickaxeQuarrySense());
    registerAdaptation(new PickaxeTunnelBore());
    registerAdaptation(new PickaxeDeepCore());
    registerAdaptation(new PickaxeObsidianRush());
    registerAdaptation(new PickaxeUnbreakablePact());
    registerAdaptation(new PickaxeRepairRhythm());
    registerAdaptation(new PickaxeGemPolish());
    registerAdaptation(new PickaxeStoneSkin());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("wooden_pickaxe"))
        .key("challenge_pickaxe_1k")
        .model(CustomModel.get(Id.minecraft("wooden_pickaxe"), "advancement", "pickaxe", "challenge_pickaxe_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("stone_pickaxe"))
            .key("challenge_pickaxe_5k")
            .model(CustomModel.get(Id.minecraft("stone_pickaxe"), "advancement", "pickaxe", "challenge_pickaxe_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Id.minecraft("iron_pickaxe"))
                .key("challenge_pickaxe_50k")
                .model(CustomModel.get(Id.minecraft("iron_pickaxe"), "advancement", "pickaxe", "challenge_pickaxe_50k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());

    registerMilestone("challenge_pickaxe_1k", "pickaxe.blocks.broken", 1000, () -> getConfig().emeraldBonus * 2);
    registerMilestone("challenge_pickaxe_5k", "pickaxe.blocks.broken", 5000, () -> getConfig().emeraldBonus * 5);
    registerMilestone("challenge_pickaxe_50k", "pickaxe.blocks.broken", 50000, () -> getConfig().emeraldBonus * 10);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("golden_pickaxe")).key("challenge_pick_damage_1k")
        .model(CustomModel.get(Id.minecraft("golden_pickaxe"), "advancement", "pickaxe", "challenge_pick_damage_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_pickaxe"))
            .key("challenge_pick_damage_10k")
            .model(CustomModel.get(Id.minecraft("diamond_pickaxe"), "advancement", "pickaxe", "challenge_pick_damage_10k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_pick_damage_1k", "pickaxe.damage", 1000, () -> getConfig().emeraldBonus);
    registerMilestone("challenge_pick_damage_10k", "pickaxe.damage", 10000, () -> getConfig().emeraldBonus * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("raw_iron")).key("challenge_pick_value_5k")
        .model(CustomModel.get(Id.minecraft("raw_iron"), "advancement", "pickaxe", "challenge_pick_value_5k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("raw_gold"))
            .key("challenge_pick_value_50k")
            .model(CustomModel.get(Id.minecraft("raw_gold"), "advancement", "pickaxe", "challenge_pick_value_50k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_pick_value_5k", "pickaxe.blocks.value", 5000, () -> getConfig().emeraldBonus);
    registerMilestone("challenge_pick_value_50k", "pickaxe.blocks.value", 50000, () -> getConfig().emeraldBonus * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_ore")).key("challenge_pick_ores_500")
        .model(CustomModel.get(Id.minecraft("iron_ore"), "advancement", "pickaxe", "challenge_pick_ores_500"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_ore"))
            .key("challenge_pick_ores_5k")
            .model(CustomModel.get(Id.minecraft("diamond_ore"), "advancement", "pickaxe", "challenge_pick_ores_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_pick_ores_500", "pickaxe.ores", 500, () -> getConfig().emeraldBonus);
    registerMilestone("challenge_pick_ores_5k", "pickaxe.ores", 5000, () -> getConfig().emeraldBonus * 2);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!getConfig().getXpForAttackingWithTools
        || !(e.damager() instanceof PlayerRef p)
        || !checkValidEntity(e.entity())
        || !isPickaxe(p.inventory().mainHand())) {
      return;
    }

    shouldReturnForPlayer(p, e, () -> {
      if (!cooldowns.isReady(p.uuid(), getConfig().cooldownDelay)) {
        return;
      }
      double creditedDamage = CombatXp.credit(getPlayer(p).getData().getCombatXp(), getName(), p, e);
      if (creditedDamage <= 0D) {
        return;
      }
      cooldowns.mark(p.uuid());
      AdaptPlayer adaptPlayer = getPlayer(p);
      adaptPlayer.getData().addStat("pickaxe.damage", creditedDamage);
      xp(p, e.entity().location(), getConfig().damageXPMultiplier * creditedDamage, "pickaxe:combat");
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    PlayerRef p = e.player();
    ItemRef mainHand = p.inventory().mainHand();
    if (!isPickaxe(mainHand)) {
      return;
    }

    shouldReturnForPlayer(p, e, () -> {
      if (XpProvenance.breakXpMultiplier(e.block()) <= 0D) {
        return;
      }
      Id blockType = e.block().type();
      double blockValue = getValue(blockType);
      AdaptPlayer adaptPlayer = getPlayer(p);

      adaptPlayer.getData().addStat("pickaxe.blocks.broken", 1);
      adaptPlayer.getData().addStat("pickaxe.blocks.value", blockValue);
      if (blockType.path().contains("_ore")) {
        adaptPlayer.getData().addStat("pickaxe.ores", 1);
      }

      Loc blockLocation = e.block().center();
      if (mainHand.enchantmentLevel(SILK_TOUCH) > 0) {
        xp(p, blockLocation, 5, "pickaxe:mine");
      } else {
        xp(p, blockLocation, blockXP(e.block(), blockValue), "pickaxe:mine");
      }
    });
  }

  @Override
  public double getValue(Id type) {
    Config c = getConfig();
    ItemOps items = AdaptCore.platform().host().items();
    double value = super.getValue(type) * c.blockValueMultiplier;
    value += Math.min(c.maxHardnessBonus, items.blockHardness(type));
    value += Math.min(c.maxBlastResistanceBonus, items.blastResistance(type));

    value += switch (type.isMinecraft() ? type.path() : "") {
      case "coal_ore" -> c.coalBonus;
      case "copper_ore" -> c.copperBonus;
      case "iron_ore" -> c.ironBonus;
      case "gold_ore" -> c.goldBonus;
      case "lapis_ore" -> c.lapisBonus;
      case "diamond_ore" -> c.diamondBonus;
      case "emerald_ore" -> c.emeraldBonus;
      case "nether_gold_ore" -> c.netherGoldBonus;
      case "nether_quartz_ore" -> c.netherQuartzBonus;
      case "redstone_ore" -> c.redstoneBonus;
      case "ancient_debris" -> c.debrisBonus;
      case "deepslate_coal_ore" -> c.coalBonus * c.deepslateMultiplier;
      case "deepslate_copper_ore" -> c.copperBonus * c.deepslateMultiplier;
      case "deepslate_iron_ore" -> c.ironBonus * c.deepslateMultiplier;
      case "deepslate_gold_ore" -> c.goldBonus * c.deepslateMultiplier;
      case "deepslate_lapis_ore" -> c.lapisBonus * c.deepslateMultiplier;
      case "deepslate_diamond_ore" -> c.diamondBonus * c.deepslateMultiplier;
      case "deepslate_emerald_ore" -> c.emeraldBonus * c.deepslateMultiplier;
      case "deepslate_redstone_ore" -> c.redstoneBonus * c.deepslateMultiplier;
      default -> 0;
    };

    return value * 0.48;
  }


  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @NoArgsConstructor
  protected static class Config extends SkillConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Debris Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double debrisBonus = 210;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    String skillColor = "&6";
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Get Xp For Attacking With Tools for the Pickaxes skill.", impact = "True enables this behavior and false disables it.")
    boolean getXpForAttackingWithTools = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage XPMultiplier for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageXPMultiplier = 6.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Block Value Multiplier for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double blockValueMultiplier = 0.125;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Hardness Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxHardnessBonus = 9;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Blast Resistance Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxBlastResistanceBonus = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Coal Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double coalBonus = 18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Iron Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double ironBonus = 30;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Redstone Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double redstoneBonus = 55;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Copper Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double copperBonus = 22;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Gold Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double goldBonus = 38;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Lapis Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double lapisBonus = 75;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds between Pickaxes combat XP awards.", impact = "Higher values reduce repeated combat XP frequency; lower values reward hits more often.")
    long cooldownDelay = 1250;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Diamond Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double diamondBonus = 175;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Emerald Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double emeraldBonus = 210;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Nether Gold Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double netherGoldBonus = 105;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Nether Quartz Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double netherQuartzBonus = 125;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Deepslate Multiplier for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double deepslateMultiplier = 1.35;
  }
}
