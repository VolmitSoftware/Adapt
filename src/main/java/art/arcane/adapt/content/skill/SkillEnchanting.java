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
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingAnvilSavant;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingArcaneSiphon;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingBookshelfAttunement;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingCurseCleansing;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingEchoOfKnowledge;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingGrindstoneRecovery;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingInfusionTransfer;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingLapisReturn;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingOfferReroll;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingQuickEnchant;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingRuneSight;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingSoulLink;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingTomeRebinding;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingXPReturn;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.reflect.registries.Particles;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.enchantment.EnchantItemEvent;

public class SkillEnchanting extends SimpleSkill<SkillEnchanting.Config> {
  private final Cooldowns cooldowns = cooldowns();

  public SkillEnchanting() {
    super("enchanting", SkillPresentation.of(SkillMessages.ENCHANTING_NAME, SkillMessages.ENCHANTING_ICON, SkillMessages.ENCHANTING_DESCRIPTION));
    registerConfiguration(Config.class);
    setColor(C.LIGHT_PURPLE);
    setInterval(3909);
    setIcon(Material.KNOWLEDGE_BOOK);
    registerAdaptation(new EnchantingQuickEnchant());
    registerAdaptation(new EnchantingLapisReturn());
    registerAdaptation(new EnchantingXPReturn()); //
    registerAdaptation(new EnchantingAnvilSavant());
    registerAdaptation(new EnchantingOfferReroll());
    registerAdaptation(new EnchantingBookshelfAttunement());
    registerAdaptation(new EnchantingGrindstoneRecovery());
    registerAdaptation(new EnchantingCurseCleansing());
    registerAdaptation(new EnchantingTomeRebinding());
    registerAdaptation(new EnchantingSoulLink());
    registerAdaptation(new EnchantingArcaneSiphon());
    registerAdaptation(new EnchantingRuneSight());
    registerAdaptation(new EnchantingInfusionTransfer());
    registerAdaptation(new EnchantingEchoOfKnowledge());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.CRAFTING_TABLE).key("challenge_enchant_1k")
        .model(CustomModel.get(Material.CRAFTING_TABLE, "advancement", "enchanting", "challenge_enchant_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
            .icon(Material.KNOWLEDGE_BOOK)
            .key("challenge_enchant_5k")
            .model(CustomModel.get(Material.KNOWLEDGE_BOOK, "advancement", "enchanting", "challenge_enchant_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_enchant_1k", "enchanted.items", 1000, () -> getConfig().challengeEnchantReward);
    registerMilestone("challenge_enchant_5k", "enchanted.items", 5000, () -> getConfig().challengeEnchantReward);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.EXPERIENCE_BOTTLE)
        .key("challenge_enchant_power_100")
        .model(CustomModel.get(Material.EXPERIENCE_BOTTLE, "advancement", "enchanting", "challenge_enchant_power_100"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENCHANTING_TABLE)
            .key("challenge_enchant_power_1k")
            .model(CustomModel.get(Material.ENCHANTING_TABLE, "advancement", "enchanting", "challenge_enchant_power_1k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_enchant_power_100", "enchanted.power", 100, () -> getConfig().challengeEnchantReward);
    registerMilestone("challenge_enchant_power_1k", "enchanted.power", 1000, () -> getConfig().challengeEnchantReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BOOKSHELF)
        .key("challenge_enchant_high_25")
        .model(CustomModel.get(Material.BOOKSHELF, "advancement", "enchanting", "challenge_enchant_high_25"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENCHANTED_BOOK)
            .key("challenge_enchant_high_250")
            .model(CustomModel.get(Material.ENCHANTED_BOOK, "advancement", "enchanting", "challenge_enchant_high_250"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_enchant_high_25", "enchanting.high.level", 25, () -> getConfig().challengeEnchantReward);
    registerMilestone("challenge_enchant_high_250", "enchanting.high.level", 250, () -> getConfig().challengeEnchantReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.EXPERIENCE_BOTTLE)
        .key("challenge_enchant_total_500")
        .model(CustomModel.get(Material.EXPERIENCE_BOTTLE, "advancement", "enchanting", "challenge_enchant_total_500"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DRAGON_BREATH)
            .key("challenge_enchant_total_5k")
            .model(CustomModel.get(Material.DRAGON_BREATH, "advancement", "enchanting", "challenge_enchant_total_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_enchant_total_500", "enchanting.total.levels", 500, () -> getConfig().challengeEnchantReward);
    registerMilestone("challenge_enchant_total_5k", "enchanting.total.levels", 5000, () -> getConfig().challengeEnchantReward * 2);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EnchantItemEvent e) {
    Player p = e.getEnchanter();
    shouldReturnForPlayer(p, e, () -> {
      handleEnchantItemEvent(p, e);
    });

  }

  private void handleEnchantItemEvent(Player p, EnchantItemEvent e) {
    AdaptPlayer adaptPlayer = getPlayer(p);
    int power = 0;
    for (int level : e.getEnchantsToAdd().values()) {
      power += level;
    }
    adaptPlayer.getData().addStat("enchanted.items", 1);
    adaptPlayer.getData().addStat("enchanted.power", power);
    Block enchantBlock = e.getEnchantBlock();
    Location tableLocation = enchantBlock == null ? null : enchantBlock.getLocation().add(0.5D, 0.9D, 0.5D);
    if (e.getExpLevelCost() >= 30) {
      adaptPlayer.getData().addStat("enchanting.high.level", 1);
      if (tableLocation != null) {
        celebrateHighEnchant(tableLocation);
      }
    }
    adaptPlayer.getData().addStat("enchanting.total.levels", e.getExpLevelCost());

    if (!cooldowns.isReady(p.getUniqueId(), getConfig().cooldownDelay)) {
      return;
    }
    cooldowns.mark(p.getUniqueId());
    xp(p, getConfig().enchantPowerXPMultiplier * power);
    if (tableLocation != null) {
      fx(tableLocation, FxPriority.AMBIENT)
          .particle(Particles.ENCHANTMENT_TABLE, Math.min(20, 6 + (power * 2)), 0, 0, 0, 0.5D, 0.05D)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.2F);
    }
  }

  private void celebrateHighEnchant(Location location) {
    timeline(location)
        .duration(10)
        .priority(FxPriority.TRANSITION)
        .cullRadius(20.0D)
        .frame((f, tick, progress) -> {
          f.particle(Particles.ENCHANTMENT_TABLE, 2, 0, 0, 0, 0.5D, 0.08D);
          if (tick == 0) {
            f.dome(Particles.END_ROD, 1.6D, 12)
                .chord(Sound.BLOCK_BEACON_ACTIVATE, 0.6F, 1.0F, Sound.ENTITY_PLAYER_LEVELUP, 0.5F, 1.3F);
          }
        })
        .start();
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @NoArgsConstructor
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    String skillColor = "&d";
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Enchant Power XPMultiplier for the Enchanting skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double enchantPowerXPMultiplier = 45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Enchanting skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 5250;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Enchant Reward for the Enchanting skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeEnchantReward = 2500;
  }
}
