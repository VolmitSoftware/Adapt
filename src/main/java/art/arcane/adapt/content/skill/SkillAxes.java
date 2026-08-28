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

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.world.AdaptPlayer;
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
import art.arcane.adapt.content.integration.iris.IrisTreeFellerLink;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class SkillAxes extends SimpleSkill<SkillAxes.Config> {
  private final Cooldowns cooldowns = cooldowns();

  public SkillAxes() {
    this(IrisTreeFellerLink.isAvailable());
  }

  SkillAxes(boolean irisTreeFellerAvailable) {
    super("axes", SkillPresentation.of(SkillMessages.AXES_NAME, SkillMessages.AXES_ICON, SkillMessages.AXES_DESCRIPTION));
    registerConfiguration(Config.class);
    setColor(C.YELLOW);
    setInterval(5251);
    setIcon(Material.GOLDEN_AXE);
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
        .icon(Material.WOODEN_AXE).key("challenge_chop_1k")
        .model(CustomModel.get(Material.WOODEN_AXE, "advancement", "axes", "challenge_chop_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
            .icon(Material.STONE_AXE)
            .key("challenge_chop_5k")
            .model(CustomModel.get(Material.STONE_AXE, "advancement", "axes", "challenge_chop_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
                .icon(Material.IRON_AXE)
                .key("challenge_chop_50k")
                .model(CustomModel.get(Material.IRON_AXE, "advancement", "axes", "challenge_chop_50k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerMilestone("challenge_chop_1k", "axes.blocks.broken", 1000, () -> getConfig().challengeChopReward);
    registerMilestone("challenge_chop_5k", "axes.blocks.broken", 5000, () -> getConfig().challengeChopReward);
    registerMilestone("challenge_chop_50k", "axes.blocks.broken", 50000, () -> getConfig().challengeChopReward);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GOLDEN_AXE).key("challenge_axe_damage_1k")
        .model(CustomModel.get(Material.GOLDEN_AXE, "advancement", "axes", "challenge_axe_damage_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND_AXE)
            .key("challenge_axe_damage_10k")
            .model(CustomModel.get(Material.DIAMOND_AXE, "advancement", "axes", "challenge_axe_damage_10k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_axe_damage_1k", "axes.damage", 1000, () -> getConfig().challengeChopReward);
    registerMilestone("challenge_axe_damage_10k", "axes.damage", 10000, () -> getConfig().challengeChopReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.OAK_LOG).key("challenge_axe_value_5k")
        .model(CustomModel.get(Material.OAK_LOG, "advancement", "axes", "challenge_axe_value_5k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.DARK_OAK_LOG)
            .key("challenge_axe_value_50k")
            .model(CustomModel.get(Material.DARK_OAK_LOG, "advancement", "axes", "challenge_axe_value_50k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_axe_value_5k", "axes.blocks.value", 5000, () -> getConfig().challengeChopReward);
    registerMilestone("challenge_axe_value_50k", "axes.blocks.value", 50000, () -> getConfig().challengeChopReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.OAK_LEAVES).key("challenge_leaves_500")
        .model(CustomModel.get(Material.OAK_LEAVES, "advancement", "axes", "challenge_leaves_500"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.AZALEA_LEAVES)
            .key("challenge_leaves_5k")
            .model(CustomModel.get(Material.AZALEA_LEAVES, "advancement", "axes", "challenge_leaves_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_leaves_500", "axes.leaves", 500, () -> getConfig().challengeChopReward);
    registerMilestone("challenge_leaves_5k", "axes.leaves", 5000, () -> getConfig().challengeChopReward * 2);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!getConfig().getXpForAttackingWithTools
        || !(e.getDamager() instanceof Player p)
        || !checkValidEntity(e.getEntity().getType())
        || !isAxe(p.getInventory().getItemInMainHand())) {
      return;
    }

    shouldReturnForPlayer(p, e, () -> {
      if (e.getEntity().isDead() || e.getEntity().isInvulnerable() || p.isDead() || p.isInvulnerable()) {
        return;
      }
      if (!beginCooldown(p)) {
        return;
      }

      AdaptPlayer adaptPlayer = getPlayer(p);
      adaptPlayer.getData().addStat("axes.damage", e.getDamage());
      xp(p, e.getEntity().getLocation(), getConfig().axeDamageXPMultiplier * e.getDamage());
    });
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    Player p = e.getPlayer();
    if (!isAxe(p.getInventory().getItemInMainHand())) {
      return;
    }

    shouldReturnForPlayer(p, e, () -> {
      Material blockType = e.getBlock().getType();
      String typeName = blockType.name();
      if (isLogMaterial(blockType, typeName)) {
        double value = getValue(blockType);
        AdaptPlayer adaptPlayer = getPlayer(p);
        adaptPlayer.getData().addStat("axes.blocks.broken", 1);
        adaptPlayer.getData().addStat("axes.blocks.value", getValue(e.getBlock().getBlockData()));
        if (beginCooldown(p) && XpProvenance.breakXpMultiplier(e.getBlock()) > 0) {
          xp(p, e.getBlock().getLocation().clone().add(0.5, 0.5, 0.5), blockXP(e.getBlock(), value));
        }
      }
      if (typeName.endsWith("_LEAVES")) {
        addStat(p, "axes.leaves", 1);
      }
    });
  }

  private boolean beginCooldown(Player p) {
    UUID id = p.getUniqueId();
    if (!cooldowns.isReady(id, getConfig().cooldownDelay)) {
      return false;
    }
    cooldowns.mark(id);
    return true;
  }

  private boolean isLogMaterial(Material type, String typeName) {
    return type == Material.MUSHROOM_STEM
        || type == Material.BROWN_MUSHROOM_BLOCK
        || type == Material.RED_MUSHROOM_BLOCK
        || type == Material.MANGROVE_ROOTS
        || type == Material.MUDDY_MANGROVE_ROOTS
        || typeName.endsWith("_LOG")
        || typeName.endsWith("_WOOD");
  }

  public double getValue(Material type) {
    double value = super.getValue(type) * getConfig().valueXPMultiplier;
    float hardness = type.getHardness();
    String typeName = type.name();
    value += Math.min(getConfig().maxHardnessBonus, hardness);
    value += Math.min(getConfig().maxBlastResistanceBonus, type.getBlastResistance());

    if (typeName.endsWith("_LOG") || typeName.endsWith("_WOOD")) {
      value += getConfig().logOrWoodXPMultiplier;
    }
    if (typeName.endsWith("_LEAVES")) {
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
  protected static class Config {
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
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Axes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 1500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Value XPMultiplier for the Axes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double valueXPMultiplier = 0.175;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Axe Damage XPMultiplier for the Axes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double axeDamageXPMultiplier = 7.0;
  }
}
