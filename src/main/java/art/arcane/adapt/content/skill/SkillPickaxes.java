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

import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.SkillMessages;

import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.world.AdaptPlayer;
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
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public class SkillPickaxes extends SimpleSkill<SkillPickaxes.Config> {
  private final Cooldowns cooldowns = cooldowns();

  public SkillPickaxes() {
    super("pickaxe", SkillPresentation.of(SkillMessages.PICKAXE_NAME, SkillMessages.PICKAXE_ICON, SkillMessages.PICKAXE_DESCRIPTION));
    registerConfiguration(Config.class);
    setColor(C.GOLD);
    setInterval(2750);
    setIcon(Material.NETHERITE_PICKAXE);
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
        .icon(Material.WOODEN_PICKAXE)
        .key("challenge_pickaxe_1k")
        .model(CustomModel.get(Material.WOODEN_PICKAXE, "advancement", "pickaxe", "challenge_pickaxe_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.STONE_PICKAXE)
            .key("challenge_pickaxe_5k")
            .model(CustomModel.get(Material.STONE_PICKAXE, "advancement", "pickaxe", "challenge_pickaxe_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Material.IRON_PICKAXE)
                .key("challenge_pickaxe_50k")
                .model(CustomModel.get(Material.IRON_PICKAXE, "advancement", "pickaxe", "challenge_pickaxe_50k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());

    registerMilestone("challenge_pickaxe_1k", "pickaxe.blocks.broken", 1000, () -> getConfig().emeraldBonus * 2);
    registerMilestone("challenge_pickaxe_5k", "pickaxe.blocks.broken", 5000, () -> getConfig().emeraldBonus * 5);
    registerMilestone("challenge_pickaxe_50k", "pickaxe.blocks.broken", 50000, () -> getConfig().emeraldBonus * 10);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GOLDEN_PICKAXE).key("challenge_pick_damage_1k")
        .model(CustomModel.get(Material.GOLDEN_PICKAXE, "advancement", "pickaxe", "challenge_pick_damage_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND_PICKAXE)
            .key("challenge_pick_damage_10k")
            .model(CustomModel.get(Material.DIAMOND_PICKAXE, "advancement", "pickaxe", "challenge_pick_damage_10k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_pick_damage_1k", "pickaxe.damage", 1000, () -> getConfig().emeraldBonus);
    registerMilestone("challenge_pick_damage_10k", "pickaxe.damage", 10000, () -> getConfig().emeraldBonus * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.RAW_IRON).key("challenge_pick_value_5k")
        .model(CustomModel.get(Material.RAW_IRON, "advancement", "pickaxe", "challenge_pick_value_5k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.RAW_GOLD)
            .key("challenge_pick_value_50k")
            .model(CustomModel.get(Material.RAW_GOLD, "advancement", "pickaxe", "challenge_pick_value_50k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_pick_value_5k", "pickaxe.blocks.value", 5000, () -> getConfig().emeraldBonus);
    registerMilestone("challenge_pick_value_50k", "pickaxe.blocks.value", 50000, () -> getConfig().emeraldBonus * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_ORE).key("challenge_pick_ores_500")
        .model(CustomModel.get(Material.IRON_ORE, "advancement", "pickaxe", "challenge_pick_ores_500"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND_ORE)
            .key("challenge_pick_ores_5k")
            .model(CustomModel.get(Material.DIAMOND_ORE, "advancement", "pickaxe", "challenge_pick_ores_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_pick_ores_500", "pickaxe.ores", 500, () -> getConfig().emeraldBonus);
    registerMilestone("challenge_pick_ores_5k", "pickaxe.ores", 5000, () -> getConfig().emeraldBonus * 2);

  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!getConfig().getXpForAttackingWithTools
        || !(e.getDamager() instanceof Player p)
        || !checkValidEntity(e.getEntity().getType())
        || !isPickaxe(p.getInventory().getItemInMainHand())) {
      return;
    }

    shouldReturnForPlayer(p, e, () -> {
      AdaptPlayer adaptPlayer = getPlayer(p);
      adaptPlayer.getData().addStat("pickaxe.damage", e.getDamage());
      if (beginCooldown(p)) {
        xp(p, e.getEntity().getLocation(), getConfig().damageXPMultiplier * e.getDamage());
      }
    });
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    Player p = e.getPlayer();
    ItemStack mainHand = p.getInventory().getItemInMainHand();
    if (!isPickaxe(mainHand)) {
      return;
    }

    shouldReturnForPlayer(p, e, () -> {
      Material blockType = e.getBlock().getType();
      double blockValue = getValue(blockType);
      AdaptPlayer adaptPlayer = getPlayer(p);

      adaptPlayer.getData().addStat("pickaxe.blocks.broken", 1);
      adaptPlayer.getData().addStat("pickaxe.blocks.value", blockValue);
      if (blockType.name().contains("_ORE")) {
        adaptPlayer.getData().addStat("pickaxe.ores", 1);
      }

      if (!beginCooldown(p) || XpProvenance.breakXpMultiplier(e.getBlock()) <= 0) {
        return;
      }
      if (mainHand.containsEnchantment(Enchantment.SILK_TOUCH)) {
        xp(p, 5);
      } else {
        Location blockLocation = e.getBlock().getLocation().clone().add(0.5, 0.5, 0.5);
        xp(p, blockLocation, blockXP(e.getBlock(), blockValue));
      }
    });
  }

  public double getValue(Material type) {
    Config c = getConfig();
    double value = super.getValue(type) * c.blockValueMultiplier;
    value += Math.min(c.maxHardnessBonus, type.getHardness());
    value += Math.min(c.maxBlastResistanceBonus, type.getBlastResistance());

    value += switch (type) {
      case COAL_ORE -> c.coalBonus;
      case COPPER_ORE -> c.copperBonus;
      case IRON_ORE -> c.ironBonus;
      case GOLD_ORE -> c.goldBonus;
      case LAPIS_ORE -> c.lapisBonus;
      case DIAMOND_ORE -> c.diamondBonus;
      case EMERALD_ORE -> c.emeraldBonus;
      case NETHER_GOLD_ORE -> c.netherGoldBonus;
      case NETHER_QUARTZ_ORE -> c.netherQuartzBonus;
      case REDSTONE_ORE -> c.redstoneBonus;
      case ANCIENT_DEBRIS -> c.debrisBonus;
      case DEEPSLATE_COAL_ORE -> c.coalBonus * c.deepslateMultiplier;
      case DEEPSLATE_COPPER_ORE -> c.copperBonus * c.deepslateMultiplier;
      case DEEPSLATE_IRON_ORE -> c.ironBonus * c.deepslateMultiplier;
      case DEEPSLATE_GOLD_ORE -> c.goldBonus * c.deepslateMultiplier;
      case DEEPSLATE_LAPIS_ORE -> c.lapisBonus * c.deepslateMultiplier;
      case DEEPSLATE_DIAMOND_ORE -> c.diamondBonus * c.deepslateMultiplier;
      case DEEPSLATE_EMERALD_ORE -> c.emeraldBonus * c.deepslateMultiplier;
      case DEEPSLATE_REDSTONE_ORE -> c.redstoneBonus * c.deepslateMultiplier;
      default -> 0;
    };

    return value * 0.48;
  }


  private boolean beginCooldown(Player p) {
    if (!cooldowns.isReady(p.getUniqueId(), getConfig().cooldownDelay)) {
      return false;
    }
    cooldowns.mark(p.getUniqueId());
    return true;
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @NoArgsConstructor
  protected static class Config {
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
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
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
