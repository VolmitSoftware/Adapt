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

package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.herbalism.*;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.CustomModel;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.HashMap;
import java.util.Map;

public class SkillHerbalism extends SimpleSkill<SkillHerbalism.Config> {
    private final Map<Player, Long> cooldown = new HashMap<>();

    public SkillHerbalism() {
        super("herbalism", Localizer.dLocalize("skill.herbalism.icon"));
        registerConfiguration(Config.class);
        setColor(C.GREEN);
        setInterval(3990);
        setDescription(Localizer.dLocalize("skill.herbalism.description"));
        setDisplayName(Localizer.dLocalize("skill.herbalism.name"));
        setIcon(Material.WHEAT);
        registerAdaptation(new HerbalismGrowthAura());
        registerAdaptation(new HerbalismReplant());
        registerAdaptation(new HerbalismHungryShield());
        registerAdaptation(new HerbalismHungryHippo());
        registerAdaptation(new HerbalismDropToInventory());
        registerAdaptation(new HerbalismLuck());
        registerAdaptation(new HerbalismMyconid());
        registerAdaptation(new HerbalismTerralid());
        registerAdaptation(new HerbalismCraftableMushroomBlocks());
        registerAdaptation(new HerbalismCraftableCobweb());
        registerAdaptation(new HerbalismSeedSower());
        registerAdaptation(new HerbalismCompostCascade());
        registerAdaptation(new HerbalismRootedFooting());
        registerAdaptation(new HerbalismBeeShepherd());
        registerAdaptation(new HerbalismSporeBloom());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.COOKED_BEEF)
                .key("challenge_eat_100")
                .title(Localizer.dLocalize("advancement.challenge_eat_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_eat_100.description"))
                .model(CustomModel.get(Material.COOKED_BEEF, "advancement", "herbalism", "challenge_eat_100"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.COOKED_BEEF)
                        .key("challenge_eat_1000")
                        .title(Localizer.dLocalize("advancement.challenge_eat_1000.title"))
                        .description(Localizer.dLocalize("advancement.challenge_eat_1000.description"))
                        .model(CustomModel.get(Material.COOKED_BEEF, "advancement", "herbalism", "challenge_eat_1000"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                .icon(Material.COOKED_BEEF)
                                .key("challenge_eat_10000")
                                .title(Localizer.dLocalize("advancement.challenge_eat_10000.title"))
                                .description(Localizer.dLocalize("advancement.challenge_eat_10000.description"))
                                .model(CustomModel.get(Material.COOKED_BEEF, "advancement", "herbalism", "challenge_eat_10000"))
                                .frame(AdaptAdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                .build())
                        .build())
                .build());
        registerMilestone("challenge_eat_100", "food.eaten", 100, getConfig().challengeEat100Reward);
        registerMilestone("challenge_eat_1000", "food.eaten", 1000, getConfig().challengeEat1kReward);
        registerMilestone("challenge_eat_10000", "food.eaten", 10000, getConfig().challengeEat1kReward);


        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.COOKED_BEEF)
                .key("challenge_harvest_100")
                .title(Localizer.dLocalize("advancement.challenge_harvest_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_harvest_100.description"))
                .model(CustomModel.get(Material.COOKED_BEEF, "advancement", "herbalism", "harvest_100"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.COOKED_BEEF)
                        .key("challenge_harvest_1000")
                        .title(Localizer.dLocalize("advancement.challenge_harvest_1000.title"))
                        .description(Localizer.dLocalize("advancement.challenge_harvest_1000.description"))
                        .model(CustomModel.get(Material.COOKED_BEEF, "advancement", "herbalism", "harvest_1000"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_harvest_100", "harvest.blocks", 100, getConfig().challengeHarvest100Reward);
        registerMilestone("challenge_harvest_1000", "harvest.blocks", 1000, getConfig().challengeHarvest1kReward);

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.WHEAT_SEEDS)
                .key("challenge_plant_100")
                .title(Localizer.dLocalize("advancement.challenge_plant_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_plant_100.description"))
                .model(CustomModel.get(Material.WHEAT_SEEDS, "advancement", "herbalism", "challenge_plant_100"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.BEETROOT_SEEDS)
                        .key("challenge_plant_1k")
                        .title(Localizer.dLocalize("advancement.challenge_plant_1k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_plant_1k.description"))
                        .model(CustomModel.get(Material.BEETROOT_SEEDS, "advancement", "herbalism", "challenge_plant_1k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .child(AdaptAdvancement.builder()
                                .icon(Material.GOLDEN_CARROT)
                                .key("challenge_plant_5k")
                                .title(Localizer.dLocalize("advancement.challenge_plant_5k.title"))
                                .description(Localizer.dLocalize("advancement.challenge_plant_5k.description"))
                                .model(CustomModel.get(Material.GOLDEN_CARROT, "advancement", "herbalism", "challenge_plant_5k"))
                                .frame(AdaptAdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                .build())
                        .build())
                .build());
        registerMilestone("challenge_plant_100", "harvest.planted", 100, getConfig().challengePlant100Reward);
        registerMilestone("challenge_plant_1k", "harvest.planted", 1000, getConfig().challengePlant1kReward);
        registerMilestone("challenge_plant_5k", "harvest.planted", 5000, getConfig().challengePlant5kReward);

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.COMPOSTER)
                .key("challenge_compost_50")
                .title(Localizer.dLocalize("advancement.challenge_compost_50.title"))
                .description(Localizer.dLocalize("advancement.challenge_compost_50.description"))
                .model(CustomModel.get(Material.COMPOSTER, "advancement", "herbalism", "challenge_compost_50"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.BONE_MEAL)
                        .key("challenge_compost_500")
                        .title(Localizer.dLocalize("advancement.challenge_compost_500.title"))
                        .description(Localizer.dLocalize("advancement.challenge_compost_500.description"))
                        .model(CustomModel.get(Material.BONE_MEAL, "advancement", "herbalism", "challenge_compost_500"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_compost_50", "harvest.composted", 50, getConfig().challengeCompost50Reward);
        registerMilestone("challenge_compost_500", "harvest.composted", 500, getConfig().challengeCompost500Reward);

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.SHEARS)
                .key("challenge_shear_50")
                .title(Localizer.dLocalize("advancement.challenge_shear_50.title"))
                .description(Localizer.dLocalize("advancement.challenge_shear_50.description"))
                .model(CustomModel.get(Material.SHEARS, "advancement", "herbalism", "challenge_shear_50"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.WHITE_WOOL)
                        .key("challenge_shear_250")
                        .title(Localizer.dLocalize("advancement.challenge_shear_250.title"))
                        .description(Localizer.dLocalize("advancement.challenge_shear_250.description"))
                        .model(CustomModel.get(Material.WHITE_WOOL, "advancement", "herbalism", "challenge_shear_250"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_shear_50", "herbalism.sheared", 50, getConfig().challengeShear50Reward);
        registerMilestone("challenge_shear_250", "herbalism.sheared", 250, getConfig().challengeShear250Reward);
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        cooldown.remove(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerItemConsumeEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        shouldReturnForPlayer(e.getPlayer(), e, () -> {
            if (e.getItem().getItemMeta() instanceof PotionMeta o) {
                return;
            }

            handleHerbCooldown(p, () -> {
                xp(p, getConfig().foodConsumeXP);
                getPlayer(p).getData().addStat("food.eaten", 1);
            });


        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerShearEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        shouldReturnForPlayer(e.getPlayer(), e, () -> {
            getPlayer(p).getData().addStat("herbalism.sheared", 1);
            xp(p, e.getEntity().getLocation(), getConfig().shearXP);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerHarvestBlockEvent e) {
        if (e.isCancelled()) {
            return;
        }
        shouldReturnForPlayer(e.getPlayer(), e, () -> handleEvent(e, e.getPlayer(), e.getHarvestedBlock(), "harvest.blocks"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockPlaceEvent e) {
        if (e.isCancelled()) {
            return;
        }
        shouldReturnForPlayer(e.getPlayer(), e, () -> handleEvent(e, e.getPlayer(), e.getBlock(), "harvest.planted"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerInteractEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        shouldReturnForPlayer(e.getPlayer(), e, () -> {
            if (e.useItemInHand().equals(Event.Result.DENY)) {
                return;
            }
            if (e.getClickedBlock() == null) {
                return;
            }
            if (e.getClickedBlock().getType().equals(Material.COMPOSTER)) {
                handleComposterInteraction(e, p);
            }
        });

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockBreakEvent e) {
        if (e.isCancelled()) {
            return;
        }
        shouldReturnForPlayer(e.getPlayer(), e, () -> handleEvent(e, e.getPlayer(), e.getBlock(), "harvest.blocks"));
    }

    private void handleHerbCooldown(Player p, Runnable action) {
        if (cooldown.containsKey(p)) {
            if (cooldown.get(p) + getConfig().harvestXpCooldown > System.currentTimeMillis()) {
                return;
            } else {
                cooldown.remove(p);
            }
        }

        cooldown.put(p, System.currentTimeMillis());
        action.run();
    }

    private void handleEvent(Cancellable e, Player p, Block block, String stat) {
        handleHerbCooldown(p, () -> {
            if (block.getBlockData() instanceof Ageable ageableBlock) {
                xp(p, block.getLocation().clone().add(0.5, 0.5, 0.5), getConfig().harvestPerAgeXP * ageableBlock.getAge());
                getPlayer(p).getData().addStat(stat, 1);
            }
        });
    }

    private void handleComposterInteraction(PlayerInteractEvent e, Player p) {
        Block b = e.getClickedBlock();
        assert b != null;
        if (!(b.getBlockData() instanceof Levelled oldData))
            return;
        int ol = oldData.getLevel();
        J.s(() -> {
            if (!(b.getBlockData() instanceof Levelled newData))
                return;
            int nl = newData.getLevel();
            if (nl > ol || (ol > 0 && nl == 0)) {
                xp(p, e.getClickedBlock().getLocation().clone().add(0.5, 0.5, 0.5), getConfig().composterBaseXP + (nl * getConfig().composterLevelXPMultiplier) + (nl == 0 ? getConfig().composterNonZeroLevelBonus : 5));
                getPlayer(p).getData().addStat("harvest.composted", 1);
            }
        });
    }


    @Override
    public void onTick() {
        checkStatTrackersForOnlinePlayers();
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    public static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        public boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Harvest Xp Cooldown for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public double harvestXpCooldown = 3500;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Food Consume XP for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public double foodConsumeXP = 35;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Shear XP for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public double shearXP = 35;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Harvest Per Age XP for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public double harvestPerAgeXP = 5.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Plant Crop Seeds XP for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public double plantCropSeedsXP = 4.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Composter Base XP for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public double composterBaseXP = 2.5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Composter Level XPMultiplier for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public double composterLevelXPMultiplier = 1.25;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Composter Non Zero Level Bonus for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public double composterNonZeroLevelBonus = 25;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Eat100Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public double challengeEat100Reward = 1250;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Eat1k Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public double challengeEat1kReward = 6250;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Harvest100Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public double challengeHarvest100Reward = 1250;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Harvest1k Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public double challengeHarvest1kReward = 6250;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Plant100 Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public double challengePlant100Reward = 1250;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Plant1k Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public double challengePlant1kReward = 6250;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Plant5k Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public double challengePlant5kReward = 25000;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Compost50 Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public double challengeCompost50Reward = 1250;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Compost500 Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public double challengeCompost500Reward = 6250;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Shear50 Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public double challengeShear50Reward = 1250;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Shear250 Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public double challengeShear250Reward = 6250;
    }
}
