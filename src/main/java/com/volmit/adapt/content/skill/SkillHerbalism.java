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

import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.herbalism.*;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Localizer;
import eu.endercentral.crazy_advancements.advancement.AdvancementDisplay;
import eu.endercentral.crazy_advancements.advancement.AdvancementVisibility;
import lombok.NoArgsConstructor;
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
        super("herbalism", Localizer.dLocalize("skill", "herbalism", "icon"));
        registerConfiguration(Config.class);
        setColor(C.GREEN);
        setInterval(3990);
        setDescription(Localizer.dLocalize("skill", "herbalism", "description"));
        setDisplayName(Localizer.dLocalize("skill", "herbalism", "name"));
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
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.COOKED_BEEF)
                .key("challenge_eat_100")
                .title("So much to eat!")
                .description("Eat over 100 Items!")
                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.COOKED_BEEF)
                        .key("challenge_eat_1000")
                        .title("Unquenchable Hunger!")
                        .description("Eat over 1,000 Items!")
                        .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                .icon(Material.COOKED_BEEF)
                                .key("challenge_eat_10000")
                                .title("EVERLASTING HUNGER!")
                                .description("Eat over 10,000 Items!")
                                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                .build())
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_eat_100").goal(100).stat("food.eaten").reward(getConfig().challengeEat100Reward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_eat_1000").goal(1000).stat("food.eaten").reward(getConfig().challengeEat1kReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_eat_10000").goal(10000).stat("food.eaten").reward(getConfig().challengeEat1kReward).build());


        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.COOKED_BEEF)
                .key("challenge_harvest_100")
                .title("Full Harvest")
                .description("Harvest over 100 crops!")
                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.COOKED_BEEF)
                        .key("challenge_harvest_1000")
                        .title("Grand Harvest")
                        .description("Harvest 1,000 crops!")
                        .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_harvest_100").goal(100).stat("harvest.blocks").reward(getConfig().challengeHarvest100Reward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_harvest_1000").goal(1000).stat("harvest.blocks").reward(getConfig().challengeHarvest1kReward).build());
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
        shouldReturnForPlayer(e.getPlayer(), e, () -> xp(p, e.getEntity().getLocation(), getConfig().shearXP));
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
        Levelled c = ((Levelled) e.getClickedBlock().getBlockData());
        int ol = c.getLevel();
        J.s(() -> {
            int nl = ((Levelled) e.getClickedBlock().getBlockData()).getLevel();
            if (nl > ol || (ol > 0 && nl == 0)) {
                xp(p, e.getClickedBlock().getLocation().clone().add(0.5, 0.5, 0.5), getConfig().composterBaseXP + (nl * getConfig().composterLevelXPMultiplier) + (nl == 0 ? getConfig().composterNonZeroLevelBonus : 5));
                getPlayer(p).getData().addStat("harvest.composted", 1);
            }
        });
    }


    @Override
    public void onTick() {

    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    public static class Config {
        public boolean enabled = true;
        public double harvestXpCooldown = 5000;
        public double foodConsumeXP = 25;
        public double shearXP = 25;
        public double harvestPerAgeXP = 2.5;
        public double plantCropSeedsXP = 2.5;
        public double composterBaseXP = 2.5;
        public double composterLevelXPMultiplier = 1.25;
        public double composterNonZeroLevelBonus = 25;
        public double challengeEat100Reward = 1250;
        public double challengeEat1kReward = 6250;
        public double challengeHarvest100Reward = 1250;
        public double challengeHarvest1kReward = 6250;
    }
}
