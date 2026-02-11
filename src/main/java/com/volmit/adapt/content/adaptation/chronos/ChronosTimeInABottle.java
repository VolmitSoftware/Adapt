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

package com.volmit.adapt.content.adaptation.chronos;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.content.item.ChronoTimeBottle;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.TreeType;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.Campfire;
import org.bukkit.block.Furnace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Sapling;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.concurrent.ThreadLocalRandom;

public class ChronosTimeInABottle extends SimpleAdaptation<ChronosTimeInABottle.Config> {
    private static final String RECIPE_KEY = "chronos-time-in-a-bottle";

    public ChronosTimeInABottle() {
        super("chronos-time-bottle");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("chronos", "timeinabottle", "description"));
        setDisplayName(Localizer.dLocalize("chronos", "timeinabottle", "name"));
        setIcon(Material.CLOCK);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(1000);

        registerRecipe(AdaptRecipe.shapeless()
                .key(RECIPE_KEY)
                .ingredient(Material.CLOCK)
                .ingredient(Material.POTION)
                .ingredient(Material.GLASS_BOTTLE)
                .result(ChronoTimeBottle.withStoredSeconds(0))
                .build());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(CraftItemEvent e) {
        if (!(e.getRecipe() instanceof Keyed keyed) || !keyed.getKey().getKey().equals(RECIPE_KEY)) {
            return;
        }

        boolean hasSwiftnessPotion = false;
        for (ItemStack item : e.getInventory().getMatrix()) {
            if (item == null || item.getType() != Material.POTION) {
                continue;
            }
            if (item.getItemMeta() instanceof PotionMeta meta && meta.getBasePotionType() == PotionType.SWIFTNESS) {
                hasSwiftnessPotion = true;
                break;
            }
        }

        if (!hasSwiftnessPotion) {
            e.setCancelled(true);
            if (e.getWhoClicked() instanceof Player p && getConfig().playClockSounds) {
                ChronosSoundFX.playClockReject(p);
            }
        }
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + (getConfig().chargePerSecond + (level * getConfig().chargePerSecondPerLevel)) + " " + Localizer.dLocalize("chronos", "timeinabottle", "lore1"));
        v.addLore(C.YELLOW + "+ " + Math.round(getCookTicksPerStoredSecond(level)) + " " + Localizer.dLocalize("chronos", "timeinabottle", "lore2"));
        v.addLore(C.GRAY + "* " + Localizer.dLocalize("chronos", "timeinabottle", "lore3"));
    }

    private double getCookTicksPerStoredSecond(int level) {
        return getConfig().baseCookTicksPerStoredSecond + (level * getConfig().cookTicksPerSecondPerLevel);
    }

    private int getMaxCookTicksPerUse(int level) {
        return getConfig().maxCookTicksPerUse + (level * getConfig().maxCookTicksPerUsePerLevel);
    }

    private double getBrewingTicksPerStoredSecond(int level) {
        return getConfig().baseBrewingTicksPerStoredSecond + (level * getConfig().brewingTicksPerSecondPerLevel);
    }

    private int getMaxBrewingTicksPerUse(int level) {
        return getConfig().maxBrewingTicksPerUse + (level * getConfig().maxBrewingTicksPerUsePerLevel);
    }

    private double getCampfireTicksPerStoredSecond(int level) {
        return getConfig().baseCampfireTicksPerStoredSecond + (level * getConfig().campfireTicksPerSecondPerLevel);
    }

    private int getMaxCampfireTicksPerUse(int level) {
        return getConfig().maxCampfireTicksPerUse + (level * getConfig().maxCampfireTicksPerUsePerLevel);
    }

    private double getFurnaceSpendMultiplier() {
        return Math.max(0.01, getConfig().furnaceSpendMultiplier);
    }

    private double getBrewingSpendMultiplier() {
        return Math.max(0.01, getConfig().brewingSpendMultiplier);
    }

    private double getCampfireSpendMultiplier() {
        return Math.max(0.01, getConfig().campfireSpendMultiplier);
    }

    private double getEntityAgeTicksPerStoredSecond(int level) {
        return getConfig().baseEntityAgeTicksPerStoredSecond + (level * getConfig().entityAgeTicksPerSecondPerLevel);
    }

    private int getMaxEntityAgeTicksPerUse(int level) {
        return getConfig().maxEntityAgeTicksPerUse + (level * getConfig().maxEntityAgeTicksPerUsePerLevel);
    }

    private double getEntitySpendMultiplier() {
        return Math.max(0.01, getConfig().entitySpendMultiplier);
    }

    private int getMaxGrowthStepsPerUse(int level) {
        return getConfig().maxGrowthStepsPerUse + (level * getConfig().maxGrowthStepsPerUsePerLevel);
    }

    private double getGrowthLevelScale(int level) {
        return Math.max(getConfig().minGrowthCostLevelScale, 1D - (level * getConfig().growthCostReductionPerLevel));
    }

    private double getSaplingGrowChance(int level) {
        return Math.max(0, Math.min(1, getConfig().saplingGrowChanceBase + (level * getConfig().saplingGrowChancePerLevel)));
    }

    private TreeType getTreeType(Material type) {
        return switch (type) {
            case OAK_SAPLING -> ThreadLocalRandom.current().nextBoolean() ? TreeType.TREE : TreeType.BIG_TREE;
            case SPRUCE_SAPLING -> ThreadLocalRandom.current().nextBoolean() ? TreeType.REDWOOD : TreeType.TALL_REDWOOD;
            case BIRCH_SAPLING -> TreeType.BIRCH;
            case JUNGLE_SAPLING -> ThreadLocalRandom.current().nextBoolean() ? TreeType.SMALL_JUNGLE : TreeType.JUNGLE;
            case ACACIA_SAPLING -> TreeType.ACACIA;
            case DARK_OAK_SAPLING -> TreeType.DARK_OAK;
            case CHERRY_SAPLING -> TreeType.CHERRY;
            case MANGROVE_PROPAGULE -> ThreadLocalRandom.current().nextBoolean() ? TreeType.MANGROVE : TreeType.TALL_MANGROVE;
            case AZALEA, FLOWERING_AZALEA -> TreeType.AZALEA;
            default -> null;
        };
    }

    private double getGrowthStepCostSeconds(Block target, int level) {
        GrowthProfile profile = detectGrowthProfile(target.getType());
        double naturalSeconds = getNaturalGrowthSeconds(profile);
        int steps = getEstimatedGrowthSteps(target, profile);
        double baseStepSeconds = naturalSeconds / Math.max(1, steps);
        double profileMultiplier = getGrowthProfileCostMultiplier(profile);
        double scaled = baseStepSeconds * profileMultiplier * getConfig().growthCostMultiplier * getGrowthLevelScale(level);
        return Math.max(getConfig().minGrowthStepSeconds, scaled);
    }

    private int getEstimatedGrowthSteps(Block target, GrowthProfile profile) {
        BlockData data = target.getBlockData();
        if (data instanceof Ageable ageable) {
            return Math.max(1, ageable.getMaximumAge());
        }

        if (data instanceof Sapling) {
            return Math.max(1, getConfig().saplingGrowthSteps);
        }

        return switch (profile) {
            case SAPLING -> Math.max(1, getConfig().saplingGrowthSteps);
            case STEM -> Math.max(1, getConfig().stemGrowthSteps);
            case BERRY_BUSH -> Math.max(1, getConfig().berryGrowthSteps);
            case VINE -> Math.max(1, getConfig().vineGrowthSteps);
            case CAVE_VINE -> Math.max(1, getConfig().caveVineGrowthSteps);
            case KELP -> Math.max(1, getConfig().kelpGrowthSteps);
            default -> Math.max(1, getConfig().defaultGrowthSteps);
        };
    }

    private double getNaturalGrowthSeconds(GrowthProfile profile) {
        return switch (profile) {
            case CROP -> getConfig().cropNaturalSeconds;
            case NETHER_WART -> getConfig().netherWartNaturalSeconds;
            case SAPLING -> getConfig().saplingNaturalSeconds;
            case STEM -> getConfig().stemNaturalSeconds;
            case BERRY_BUSH -> getConfig().berryBushNaturalSeconds;
            case VINE -> getConfig().vineNaturalSeconds;
            case CAVE_VINE -> getConfig().caveVineNaturalSeconds;
            case KELP -> getConfig().kelpNaturalSeconds;
            default -> getConfig().defaultGrowableNaturalSeconds;
        };
    }

    private double getGrowthProfileCostMultiplier(GrowthProfile profile) {
        return switch (profile) {
            case CROP -> getConfig().cropCostMultiplier;
            case NETHER_WART -> getConfig().netherWartCostMultiplier;
            case SAPLING -> getConfig().saplingCostMultiplier;
            case STEM -> getConfig().stemCostMultiplier;
            case BERRY_BUSH -> getConfig().berryBushCostMultiplier;
            case VINE -> getConfig().vineCostMultiplier;
            case CAVE_VINE -> getConfig().caveVineCostMultiplier;
            case KELP -> getConfig().kelpCostMultiplier;
            default -> getConfig().defaultGrowableCostMultiplier;
        };
    }

    private GrowthProfile detectGrowthProfile(Material type) {
        String name = type.name();

        if (type == Material.NETHER_WART) {
            return GrowthProfile.NETHER_WART;
        }

        if (type == Material.SWEET_BERRY_BUSH) {
            return GrowthProfile.BERRY_BUSH;
        }

        if (type == Material.KELP || type == Material.KELP_PLANT) {
            return GrowthProfile.KELP;
        }

        if (type == Material.CAVE_VINES || type == Material.CAVE_VINES_PLANT) {
            return GrowthProfile.CAVE_VINE;
        }

        if (type == Material.VINE || type == Material.WEEPING_VINES || type == Material.WEEPING_VINES_PLANT
                || type == Material.TWISTING_VINES || type == Material.TWISTING_VINES_PLANT) {
            return GrowthProfile.VINE;
        }

        if (name.endsWith("_SAPLING") || type == Material.MANGROVE_PROPAGULE || type == Material.AZALEA
                || type == Material.FLOWERING_AZALEA) {
            return GrowthProfile.SAPLING;
        }

        if (type == Material.PUMPKIN_STEM || type == Material.MELON_STEM || type == Material.ATTACHED_MELON_STEM
                || type == Material.ATTACHED_PUMPKIN_STEM) {
            return GrowthProfile.STEM;
        }

        if (type == Material.WHEAT || type == Material.CARROTS || type == Material.POTATOES || type == Material.BEETROOTS
                || type == Material.COCOA || type == Material.TORCHFLOWER_CROP || type == Material.PITCHER_CROP) {
            return GrowthProfile.CROP;
        }

        return GrowthProfile.DEFAULT;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerItemConsumeEvent e) {
        if (ChronoTimeBottle.isBindableItem(e.getItem())) {
            e.setCancelled(true);
            if (getConfig().playClockSounds) {
                ChronosSoundFX.playClockReject(e.getPlayer());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerInteractEvent e) {
        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }

        Player p = e.getPlayer();
        EquipmentSlot handSlot = e.getHand();
        if (handSlot == null) {
            return;
        }

        ItemStack hand = handSlot == EquipmentSlot.OFF_HAND
                ? p.getInventory().getItemInOffHand()
                : p.getInventory().getItemInMainHand();
        if (!ChronoTimeBottle.isBindableItem(hand)) {
            return;
        }

        // Chrono bottles are never drinkable; always deny vanilla potion use.
        e.setUseItemInHand(Event.Result.DENY);

        if (!hasAdaptation(p) || action != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) {
            return;
        }

        Block clicked = e.getClickedBlock();
        if (!canInteract(p, clicked.getLocation())) {
            return;
        }

        int level = getLevel(p);
        double storedSeconds = ChronoTimeBottle.getStoredSeconds(hand);
        if (storedSeconds <= 0) {
            return;
        }

        TimeSpendResult result = accelerateTarget(clicked, storedSeconds, level);
        if (!result.applied()) {
            return;
        }

        e.setCancelled(true);
        ChronoTimeBottle.setStoredSeconds(hand, Math.max(0, storedSeconds - result.spentSeconds()));

        if (getConfig().playClockSounds) {
            ChronosSoundFX.playBottleUse(p, clicked.getLocation().add(0.5, 1.0, 0.5), result.effectTicks());
        }
        if (getConfig().showParticles) {
            p.getWorld().spawnParticle(Particle.ENCHANT, clicked.getLocation().add(0.5, 1.0, 0.5), 32, 0.35, 0.3, 0.35, 0.08);
            p.getWorld().spawnParticle(Particle.END_ROD, clicked.getLocation().add(0.5, 1.0, 0.5), 8, 0.1, 0.2, 0.1, 0.01);
        }

        xp(p, clicked.getLocation().add(0.5, 1.0, 0.5), Math.min(getConfig().maxXPPerUse, result.xpGain()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!ChronoTimeBottle.isBindableItem(hand) || !hasAdaptation(p)) {
            return;
        }

        if (!(e.getRightClicked() instanceof org.bukkit.entity.Ageable ageable)) {
            return;
        }

        if (!canInteract(p, e.getRightClicked().getLocation())) {
            return;
        }

        int currentAge = ageable.getAge();
        if (currentAge == 0) {
            return;
        }

        int level = getLevel(p);
        double storedSeconds = ChronoTimeBottle.getStoredSeconds(hand);
        if (storedSeconds <= 0) {
            return;
        }

        TimeSpendResult result = accelerateAgeableEntity(ageable, storedSeconds, level);
        if (!result.applied()) {
            return;
        }

        e.setCancelled(true);
        ChronoTimeBottle.setStoredSeconds(hand, Math.max(0, storedSeconds - result.spentSeconds()));

        if (getConfig().playClockSounds) {
            ChronosSoundFX.playBottleUse(p, ageable.getLocation().add(0, 1.0, 0), result.effectTicks());
        }
        if (getConfig().showParticles) {
            p.getWorld().spawnParticle(Particle.ENCHANT, ageable.getLocation().add(0, 1.0, 0), 24, 0.3, 0.4, 0.3, 0.05);
            p.getWorld().spawnParticle(Particle.END_ROD, ageable.getLocation().add(0, 1.0, 0), 7, 0.1, 0.25, 0.1, 0.01);
        }

        xp(p, ageable.getLocation().add(0, 1.0, 0), Math.min(getConfig().maxXPPerUse, result.xpGain()));
    }

    private TimeSpendResult accelerateTarget(Block clicked, double storedSeconds, int level) {
        if (clicked.getState() instanceof Furnace furnace) {
            return accelerateFurnace(furnace, storedSeconds, level);
        }

        if (clicked.getState() instanceof BrewingStand brewingStand) {
            return accelerateBrewingStand(brewingStand, storedSeconds, level);
        }

        if (clicked.getState() instanceof Campfire campfire) {
            return accelerateCampfire(campfire, storedSeconds, level);
        }

        return accelerateGrowables(clicked, storedSeconds, level);
    }

    private TimeSpendResult accelerateFurnace(Furnace furnace, double storedSeconds, int level) {
        if (furnace.getInventory().getSmelting() == null || furnace.getInventory().getSmelting().getType().isAir()) {
            return TimeSpendResult.none();
        }

        int totalCookTime = furnace.getCookTimeTotal();
        int currentCookTime = furnace.getCookTime();
        int remainingCookTime = Math.max(0, totalCookTime - currentCookTime);
        if (remainingCookTime <= 0) {
            return TimeSpendResult.none();
        }

        double cookTicksPerStoredSecond = getCookTicksPerStoredSecond(level);
        double spendMultiplier = getFurnaceSpendMultiplier();
        int affordableTicks = (int) Math.floor((storedSeconds / spendMultiplier) * cookTicksPerStoredSecond);
        int advanceTicks = Math.min(remainingCookTime, Math.min(affordableTicks, getMaxCookTicksPerUse(level)));
        if (advanceTicks <= 0) {
            return TimeSpendResult.none();
        }

        furnace.setCookTime((short) Math.min(totalCookTime, currentCookTime + advanceTicks));
        furnace.update(true, true);

        double spentSeconds = (advanceTicks / cookTicksPerStoredSecond) * spendMultiplier;
        return new TimeSpendResult(spentSeconds, advanceTicks, advanceTicks * getConfig().xpPerCookTick);
    }

    private TimeSpendResult accelerateBrewingStand(BrewingStand stand, double storedSeconds, int level) {
        int brewingTime = stand.getBrewingTime();
        if (brewingTime <= 0) {
            return TimeSpendResult.none();
        }

        double brewTicksPerStoredSecond = getBrewingTicksPerStoredSecond(level);
        double spendMultiplier = getBrewingSpendMultiplier();
        int affordableTicks = (int) Math.floor((storedSeconds / spendMultiplier) * brewTicksPerStoredSecond);
        int advanceTicks = Math.min(brewingTime, Math.min(affordableTicks, getMaxBrewingTicksPerUse(level)));
        if (advanceTicks <= 0) {
            return TimeSpendResult.none();
        }

        stand.setBrewingTime(Math.max(0, brewingTime - advanceTicks));
        stand.update(true, true);

        double spentSeconds = (advanceTicks / brewTicksPerStoredSecond) * spendMultiplier;
        return new TimeSpendResult(spentSeconds, advanceTicks, advanceTicks * getConfig().xpPerBrewTick);
    }

    private TimeSpendResult accelerateCampfire(Campfire campfire, double storedSeconds, int level) {
        double campfireTicksPerStoredSecond = getCampfireTicksPerStoredSecond(level);
        double spendMultiplier = getCampfireSpendMultiplier();
        int affordableTicks = (int) Math.floor((storedSeconds / spendMultiplier) * campfireTicksPerStoredSecond);
        int budgetTicks = Math.min(affordableTicks, getMaxCampfireTicksPerUse(level));
        if (budgetTicks <= 0) {
            return TimeSpendResult.none();
        }

        int usedTicks = 0;
        for (int i = 0; i < campfire.getSize() && usedTicks < budgetTicks; i++) {
            ItemStack item = campfire.getItem(i);
            if (item == null || item.getType().isAir()) {
                continue;
            }

            int total = campfire.getCookTimeTotal(i);
            int current = campfire.getCookTime(i);
            int remaining = Math.max(0, total - current);
            if (remaining <= 0) {
                continue;
            }

            int step = Math.min(remaining, budgetTicks - usedTicks);
            campfire.setCookTime(i, current + step);
            usedTicks += step;
        }

        if (usedTicks <= 0) {
            return TimeSpendResult.none();
        }

        campfire.update(true, true);
        double spentSeconds = (usedTicks / campfireTicksPerStoredSecond) * spendMultiplier;
        return new TimeSpendResult(spentSeconds, usedTicks, usedTicks * getConfig().xpPerCampfireTick);
    }

    private TimeSpendResult accelerateAgeableEntity(org.bukkit.entity.Ageable entity, double storedSeconds, int level) {
        int currentAge = entity.getAge();
        if (currentAge == 0) {
            return TimeSpendResult.none();
        }

        double ageTicksPerStoredSecond = getEntityAgeTicksPerStoredSecond(level);
        double spendMultiplier = getEntitySpendMultiplier();
        int affordableTicks = (int) Math.floor((storedSeconds / spendMultiplier) * ageTicksPerStoredSecond);
        int advanceTicks = Math.min(Math.abs(currentAge), Math.min(affordableTicks, getMaxEntityAgeTicksPerUse(level)));
        if (advanceTicks <= 0) {
            return TimeSpendResult.none();
        }

        if (currentAge < 0) {
            entity.setAge(Math.min(0, currentAge + advanceTicks));
        } else {
            entity.setAge(Math.max(0, currentAge - advanceTicks));
        }

        double spentSeconds = (advanceTicks / ageTicksPerStoredSecond) * spendMultiplier;
        return new TimeSpendResult(spentSeconds, advanceTicks, advanceTicks * getConfig().xpPerEntityAgeTick);
    }

    private TimeSpendResult accelerateGrowables(Block clicked, double storedSeconds, int level) {
        int attempts = getMaxGrowthStepsPerUse(level);
        if (attempts <= 0 || storedSeconds <= 0) {
            return TimeSpendResult.none();
        }

        double remainingSeconds = storedSeconds;
        double spentSeconds = 0;
        int successes = 0;
        for (int i = 0; i < attempts; i++) {
            Block target = clicked.getWorld().getBlockAt(clicked.getLocation());
            double stepCost = getGrowthStepCostSeconds(target, level);
            if (remainingSeconds + 1.0E-6 < stepCost) {
                break;
            }

            if (!applyDirectGrowthStep(target, level)) {
                break;
            }

            remainingSeconds -= stepCost;
            spentSeconds += stepCost;
            successes++;
        }

        if (successes <= 0 || spentSeconds <= 0) {
            return TimeSpendResult.none();
        }

        int effectTicks = Math.max(8, successes * 20);
        return new TimeSpendResult(spentSeconds, effectTicks, successes * getConfig().xpPerGrowthStep);
    }

    private boolean applyDirectGrowthStep(Block block, int level) {
        BlockData data = block.getBlockData();
        if (data instanceof Sapling sapling) {
            if (sapling.getStage() < sapling.getMaximumStage()) {
                sapling.setStage(Math.min(sapling.getMaximumStage(), sapling.getStage() + 1));
                block.setBlockData(data, true);
                return true;
            }

            if (!getConfig().allowSaplingTreeGeneration) {
                return false;
            }

            if (ThreadLocalRandom.current().nextDouble() > getSaplingGrowChance(level)) {
                return true;
            }

            TreeType treeType = getTreeType(block.getType());
            return treeType != null && block.getWorld().generateTree(block.getLocation(), treeType);
        }

        if (data instanceof Ageable ageable && ageable.getAge() < ageable.getMaximumAge()) {
            ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + 1));
            block.setBlockData(data, true);
            return true;
        }

        return false;
    }

    private record TimeSpendResult(double spentSeconds, int effectTicks, double xpGain) {
        private static TimeSpendResult none() {
            return new TimeSpendResult(0, 0, 0);
        }

        private boolean applied() {
            return spentSeconds > 0;
        }
    }

    private enum GrowthProfile {
        CROP,
        NETHER_WART,
        SAPLING,
        STEM,
        BERRY_BUSH,
        VINE,
        CAVE_VINE,
        KELP,
        DEFAULT
    }

    @Override
    public void onTick() {
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (!hasAdaptation(p)) {
                continue;
            }

            int level = getLevel(p);
            double chargePerSecond = getConfig().chargePerSecond + (level * getConfig().chargePerSecondPerLevel);

            for (ItemStack stack : p.getInventory().getContents()) {
                if (!ChronoTimeBottle.isBindableItem(stack)) {
                    continue;
                }

                double stored = ChronoTimeBottle.getStoredSeconds(stack);
                double capped = Math.min(getConfig().maxStoredSeconds, stored + chargePerSecond);
                if (capped > stored) {
                    ChronoTimeBottle.setStoredSeconds(stack, capped);
                }
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean permanent = false;
        boolean enabled = true;
        boolean showParticles = true;
        boolean playClockSounds = true;
        int baseCost = 6;
        int maxLevel = 5;
        int initialCost = 6;
        double costFactor = 0.35;
        double maxStoredSeconds = 900;
        double chargePerSecond = 0.1;
        double chargePerSecondPerLevel = 0.02;
        double baseCookTicksPerStoredSecond = 20;
        double cookTicksPerSecondPerLevel = 3;
        int maxCookTicksPerUse = 140;
        int maxCookTicksPerUsePerLevel = 35;
        double furnaceSpendMultiplier = 1;
        double baseBrewingTicksPerStoredSecond = 20;
        double brewingTicksPerSecondPerLevel = 3;
        int maxBrewingTicksPerUse = 140;
        int maxBrewingTicksPerUsePerLevel = 35;
        double brewingSpendMultiplier = 1.05;
        double baseCampfireTicksPerStoredSecond = 20;
        double campfireTicksPerSecondPerLevel = 3;
        int maxCampfireTicksPerUse = 160;
        int maxCampfireTicksPerUsePerLevel = 40;
        double campfireSpendMultiplier = 0.9;
        double baseEntityAgeTicksPerStoredSecond = 20;
        double entityAgeTicksPerSecondPerLevel = 4;
        int maxEntityAgeTicksPerUse = 180;
        int maxEntityAgeTicksPerUsePerLevel = 55;
        double entitySpendMultiplier = 1.35;
        int maxGrowthStepsPerUse = 6;
        int maxGrowthStepsPerUsePerLevel = 2;
        boolean allowSaplingTreeGeneration = true;
        double saplingGrowChanceBase = 0.18;
        double saplingGrowChancePerLevel = 0.04;
        double growthCostMultiplier = 1;
        double growthCostReductionPerLevel = 0.05;
        double minGrowthCostLevelScale = 0.45;
        double minGrowthStepSeconds = 0.06;
        int saplingGrowthSteps = 2;
        int stemGrowthSteps = 7;
        int berryGrowthSteps = 3;
        int vineGrowthSteps = 5;
        int caveVineGrowthSteps = 5;
        int kelpGrowthSteps = 5;
        int defaultGrowthSteps = 4;
        double cropNaturalSeconds = 300;
        double netherWartNaturalSeconds = 420;
        double saplingNaturalSeconds = 900;
        double stemNaturalSeconds = 660;
        double berryBushNaturalSeconds = 260;
        double vineNaturalSeconds = 300;
        double caveVineNaturalSeconds = 280;
        double kelpNaturalSeconds = 240;
        double defaultGrowableNaturalSeconds = 420;
        double cropCostMultiplier = 1;
        double netherWartCostMultiplier = 1.2;
        double saplingCostMultiplier = 2.2;
        double stemCostMultiplier = 1.4;
        double berryBushCostMultiplier = 0.8;
        double vineCostMultiplier = 0.85;
        double caveVineCostMultiplier = 0.9;
        double kelpCostMultiplier = 0.75;
        double defaultGrowableCostMultiplier = 1;
        double xpPerCookTick = 0.08;
        double xpPerBrewTick = 0.08;
        double xpPerCampfireTick = 0.08;
        double xpPerEntityAgeTick = 0.06;
        double xpPerGrowthStep = 2;
        double maxXPPerUse = 55;
    }
}
