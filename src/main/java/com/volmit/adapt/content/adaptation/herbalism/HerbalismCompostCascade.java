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

package com.volmit.adapt.content.adaptation.herbalism;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.SoundPlayer;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class HerbalismCompostCascade extends SimpleAdaptation<HerbalismCompostCascade.Config> {
    public HerbalismCompostCascade() {
        super("herbalism-compost-cascade");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("herbalism.compost_cascade.description"));
        setDisplayName(Localizer.dLocalize("herbalism.compost_cascade.name"));
        setIcon(Material.COMPOSTER);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(600);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.COMPOSTER)
                .key("challenge_herbalism_compost_1k")
                .title(Localizer.dLocalize("advancement.challenge_herbalism_compost_1k.title"))
                .description(Localizer.dLocalize("advancement.challenge_herbalism_compost_1k.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.BONE_MEAL)
                        .key("challenge_herbalism_compost_25k")
                        .title(Localizer.dLocalize("advancement.challenge_herbalism_compost_25k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_herbalism_compost_25k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_herbalism_compost_1k", "herbalism.compost-cascade.items-composted", 1000, 300);
        registerMilestone("challenge_herbalism_compost_25k", "herbalism.compost-cascade.items-composted", 25000, 1000);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getRadius(level)) + C.GRAY + " " + Localizer.dLocalize("herbalism.compost_cascade.lore1"));
        v.addLore(C.GREEN + "+ " + getMaxItems(level) + C.GRAY + " " + Localizer.dLocalize("herbalism.compost_cascade.lore2"));
        v.addLore(C.GREEN + "+ " + Form.pc(getFillChance(level), 0) + C.GRAY + " " + Localizer.dLocalize("herbalism.compost_cascade.lore3"));
        v.addLore(C.YELLOW + "* " + Form.duration(getCooldownTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("herbalism.compost_cascade.lore4"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerInteractEvent e) {
        if (e.isCancelled() || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) {
            return;
        }

        Player p = e.getPlayer();
        if (!hasAdaptation(p) || !p.isSneaking() || e.getClickedBlock().getType() != Material.COMPOSTER || p.hasCooldown(Material.COMPOSTER)) {
            return;
        }

        if (!(e.getClickedBlock().getBlockData() instanceof Levelled levelled)) {
            return;
        }

        int oldLevel = levelled.getLevel();
        if (oldLevel >= 8) {
            return;
        }

        int level = getLevel(p);
        double fillChance = getFillChance(level);
        int maxItems = getMaxItems(level);
        double radius = getRadius(level);
        Location center = e.getClickedBlock().getLocation().clone().add(0.5, 0.5, 0.5);
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        CompostState state = new CompostState(oldLevel);

        processDroppedItems(world, center, radius, state, maxItems, fillChance);
        processMatureCrops(p, world, center, radius, state, maxItems, fillChance);
        processLeafBlocks(p, world, center, radius, level, state, maxItems, fillChance);
        processInventoryItems(p, state, maxItems, fillChance);

        if (state.consumed <= 0) {
            return;
        }

        Levelled updated = (Levelled) e.getClickedBlock().getBlockData();
        updated.setLevel(Math.min(8, Math.max(oldLevel, state.compostLevel)));
        e.getClickedBlock().setBlockData(updated);

        p.setCooldown(Material.COMPOSTER, getCooldownTicks(level));
        e.setCancelled(true);

        getPlayer(p).getData().addStat("harvest.composted", state.consumed);
        getPlayer(p).getData().addStat("herbalism.compost-cascade.items-composted", state.consumed);
        xp(p, center, (state.consumed * getConfig().xpPerItemConsumed) + (state.levelGains * getConfig().xpPerLevelGain));

        SoundPlayer sp = SoundPlayer.of(world);
        sp.play(center, Sound.BLOCK_COMPOSTER_FILL, 0.8f, 1.25f);
        if (updated.getLevel() >= 8) {
            sp.play(center, Sound.BLOCK_COMPOSTER_READY, 1.0f, 1.12f);
        }

        dropRewards(world, center, level, oldLevel, updated.getLevel(), state.consumed);
    }

    private void processDroppedItems(World world, Location center, double radius, CompostState state, int maxItems, double fillChance) {
        if (isComposterDone(state, maxItems)) {
            return;
        }

        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Item item) || isComposterDone(state, maxItems)) {
                continue;
            }

            ItemStack stack = item.getItemStack();
            if (!isItem(stack) || !isCompostable(stack.getType())) {
                continue;
            }

            compostStack(stack, state, maxItems, fillChance);
            if (stack.getAmount() <= 0) {
                item.remove();
            } else {
                item.setItemStack(stack);
            }
        }
    }

    private void processMatureCrops(Player p, World world, Location center, double radius, CompostState state, int maxItems, double fillChance) {
        if (isComposterDone(state, maxItems)) {
            return;
        }

        int r = Math.max(1, (int) Math.ceil(radius));
        double rs = radius * radius;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (isComposterDone(state, maxItems)) {
                        return;
                    }

                    if ((x * x) + (y * y) + (z * z) > rs) {
                        continue;
                    }

                    Block b = world.getBlockAt(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                    if (!isMatureCrop(b)) {
                        continue;
                    }

                    if (!canBlockBreak(p, b.getLocation()) || !canBlockPlace(p, b.getLocation())) {
                        continue;
                    }

                    ItemStack[] drops = b.getDrops().toArray(ItemStack[]::new);
                    if (!replantCrop(b)) {
                        continue;
                    }

                    for (ItemStack drop : drops) {
                        if (!isItem(drop)) {
                            continue;
                        }

                        if (isCompostable(drop.getType()) && !isComposterDone(state, maxItems)) {
                            compostStack(drop, state, maxItems, fillChance);
                        }

                        if (drop.getAmount() > 0) {
                            world.dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), drop);
                        }
                    }
                }
            }
        }
    }

    private void processLeafBlocks(Player p, World world, Location center, double radius, int level, CompostState state, int maxItems, double fillChance) {
        if (isComposterDone(state, maxItems)) {
            return;
        }

        int r = Math.max(1, (int) Math.ceil(radius));
        double rs = radius * radius;
        int bursts = getLeafCompostBursts(level);
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (isComposterDone(state, maxItems)) {
                        return;
                    }

                    if ((x * x) + (y * y) + (z * z) > rs) {
                        continue;
                    }

                    Block b = world.getBlockAt(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                    if (!isLeafBlock(b.getType()) || !canBlockBreak(p, b.getLocation())) {
                        continue;
                    }

                    b.setType(Material.AIR, false);
                    ItemStack leafMass = new ItemStack(Material.OAK_LEAVES, bursts);
                    compostStack(leafMass, state, maxItems, getLeafFillChance(level, fillChance));
                }
            }
        }
    }

    private void processInventoryItems(Player p, CompostState state, int maxItems, double fillChance) {
        if (isComposterDone(state, maxItems)) {
            return;
        }

        ItemStack[] storage = p.getInventory().getStorageContents();
        boolean changed = false;
        for (int i = 0; i < storage.length; i++) {
            if (isComposterDone(state, maxItems)) {
                break;
            }

            ItemStack stack = storage[i];
            if (!isItem(stack) || !isCompostable(stack.getType())) {
                continue;
            }

            compostStack(stack, state, maxItems, fillChance);
            changed = true;
            if (stack.getAmount() <= 0) {
                storage[i] = null;
            }
        }

        if (changed) {
            p.getInventory().setStorageContents(storage);
        }
    }

    private void compostStack(ItemStack stack, CompostState state, int maxItems, double fillChance) {
        while (stack.getAmount() > 0 && !isComposterDone(state, maxItems)) {
            stack.setAmount(stack.getAmount() - 1);
            state.processed++;
            state.consumed++;

            if (ThreadLocalRandom.current().nextDouble() <= fillChance) {
                state.compostLevel++;
                state.levelGains++;
            }
        }
    }

    private void dropRewards(World world, Location center, int level, int oldLevel, int newLevel, int consumed) {
        int boneMeal = getBaseBoneMeal(level) + Math.max(0, consumed / getItemsPerBoneMeal(level));
        if (newLevel >= 8 && oldLevel < 8) {
            boneMeal += getReadyBonusBoneMeal(level);
        }

        if (boneMeal > 0) {
            world.dropItemNaturally(center, new ItemStack(Material.BONE_MEAL, Math.min(64, boneMeal)));
        }

        if (newLevel < 8) {
            return;
        }

        int rolls = getValuableRolls(level);
        for (int i = 0; i < rolls; i++) {
            if (ThreadLocalRandom.current().nextDouble() <= getValuableChance(level)) {
                world.dropItemNaturally(center, rollValuableReward(level));
            }
        }
    }

    private ItemStack rollValuableReward(int level) {
        double lp = getLevelPercent(level);
        double r = ThreadLocalRandom.current().nextDouble();

        if (r < 0.45) {
            return new ItemStack(Material.MOSS_BLOCK, 1 + ThreadLocalRandom.current().nextInt(1 + Math.max(1, (int) Math.round(lp * 3))));
        }

        if (r < 0.7) {
            return new ItemStack(Material.GLOW_BERRIES, 2 + ThreadLocalRandom.current().nextInt(2 + Math.max(1, (int) Math.round(lp * 4))));
        }

        if (r < 0.88) {
            return new ItemStack(Material.AMETHYST_SHARD, 1 + ThreadLocalRandom.current().nextInt(1 + Math.max(1, (int) Math.round(lp * 4))));
        }

        if (r < 0.97) {
            return new ItemStack(Material.EMERALD, 1);
        }

        return new ItemStack(Material.DIAMOND, 1);
    }

    private boolean isMatureCrop(Block b) {
        BlockData data = b.getBlockData();
        if (!(data instanceof Ageable ageable)) {
            return false;
        }

        Material type = b.getType();
        if (type == Material.CHORUS_PLANT || type == Material.SUGAR_CANE || type == Material.BAMBOO) {
            return false;
        }

        return ageable.getAge() >= ageable.getMaximumAge();
    }

    private boolean replantCrop(Block b) {
        BlockData data = b.getBlockData();
        if (!(data instanceof Ageable ageable)) {
            return false;
        }

        ageable.setAge(0);
        b.setBlockData(ageable, true);
        return true;
    }

    private boolean isLeafBlock(Material type) {
        return type.name().endsWith("_LEAVES");
    }

    private boolean isComposterDone(CompostState state, int maxItems) {
        return state.compostLevel >= 8 || state.processed >= maxItems;
    }

    private boolean isCompostable(Material type) {
        String n = type.name().toUpperCase(Locale.ROOT);
        return n.contains("SEEDS")
                || n.contains("SAPLING")
                || n.contains("LEAVES")
                || n.contains("FLOWER")
                || n.contains("MUSHROOM")
                || n.contains("ROOTS")
                || n.contains("VINE")
                || n.contains("KELP")
                || n.contains("DRIPLEAF")
                || n.contains("MOSS")
                || type == Material.WHEAT
                || type == Material.BEETROOT
                || type == Material.CARROT
                || type == Material.POTATO
                || type == Material.POISONOUS_POTATO
                || type == Material.NETHER_WART
                || type == Material.CACTUS
                || type == Material.SUGAR_CANE
                || type == Material.BAMBOO
                || type == Material.SHORT_GRASS
                || type == Material.TALL_GRASS
                || type == Material.SEA_PICKLE;
    }

    private int getMaxItems(int level) {
        return Math.max(1, (int) Math.round(getConfig().maxItemsBase + (getLevelPercent(level) * getConfig().maxItemsFactor)));
    }

    private double getRadius(int level) {
        return Math.max(1, getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor));
    }

    private double getFillChance(int level) {
        return Math.min(getConfig().maxFillChance, getConfig().fillChanceBase + (getLevelPercent(level) * getConfig().fillChanceFactor));
    }

    private double getLeafFillChance(int level, double baseFillChance) {
        return Math.min(1.0, baseFillChance * (getConfig().leafFillChanceMultiplierBase + (getLevelPercent(level) * getConfig().leafFillChanceMultiplierFactor)));
    }

    private int getLeafCompostBursts(int level) {
        return Math.max(1, (int) Math.round(getConfig().leafCompostBurstsBase + (getLevelPercent(level) * getConfig().leafCompostBurstsFactor)));
    }

    private int getCooldownTicks(int level) {
        return Math.max(4, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksReduction)));
    }

    private int getBaseBoneMeal(int level) {
        return Math.max(0, (int) Math.round(getConfig().boneMealBase + (getLevelPercent(level) * getConfig().boneMealFactor)));
    }

    private int getReadyBonusBoneMeal(int level) {
        return Math.max(0, (int) Math.round(getConfig().readyBonusBoneMealBase + (getLevelPercent(level) * getConfig().readyBonusBoneMealFactor)));
    }

    private int getItemsPerBoneMeal(int level) {
        return Math.max(1, (int) Math.round(getConfig().itemsPerBoneMealBase - (getLevelPercent(level) * getConfig().itemsPerBoneMealReduction)));
    }

    private double getValuableChance(int level) {
        return Math.min(getConfig().maxValuableChance, getConfig().valuableChanceBase + (getLevelPercent(level) * getConfig().valuableChanceFactor));
    }

    private int getValuableRolls(int level) {
        return Math.max(0, (int) Math.round(getConfig().valuableRollsBase + (getLevelPercent(level) * getConfig().valuableRollsFactor)));
    }

    @Override
    public void onTick() {

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
    @ConfigDescription("Sneak-right-click a composter to process nearby drops, crops, leaves, and your own compostables.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.72;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double radiusBase = 5.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double radiusFactor = 12.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Items Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxItemsBase = 80.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Items Factor for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxItemsFactor = 240.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Fill Chance Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double fillChanceBase = 0.5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Fill Chance Factor for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double fillChanceFactor = 0.42;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Fill Chance for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxFillChance = 0.98;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Leaf Compost Bursts Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double leafCompostBurstsBase = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Leaf Compost Bursts Factor for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double leafCompostBurstsFactor = 9;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Leaf Fill Chance Multiplier Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double leafFillChanceMultiplierBase = 1.35;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Leaf Fill Chance Multiplier Factor for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double leafFillChanceMultiplierFactor = 0.7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownTicksBase = 36.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Reduction for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownTicksReduction = 28.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Bone Meal Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double boneMealBase = 2.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Bone Meal Factor for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double boneMealFactor = 6.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Ready Bonus Bone Meal Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double readyBonusBoneMealBase = 2.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Ready Bonus Bone Meal Factor for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double readyBonusBoneMealFactor = 8.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Items Per Bone Meal Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double itemsPerBoneMealBase = 20.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Items Per Bone Meal Reduction for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double itemsPerBoneMealReduction = 14.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Valuable Chance Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double valuableChanceBase = 0.01;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Valuable Chance Factor for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double valuableChanceFactor = 0.09;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Valuable Chance for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxValuableChance = 0.12;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Valuable Rolls Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double valuableRollsBase = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Valuable Rolls Factor for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double valuableRollsFactor = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Item Consumed for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerItemConsumed = 1.2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Level Gain for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerLevelGain = 2.8;
    }

    private static class CompostState {
        private int compostLevel;
        private int processed;
        private int consumed;
        private int levelGains;

        private CompostState(int compostLevel) {
            this.compostLevel = compostLevel;
            this.processed = 0;
            this.consumed = 0;
            this.levelGains = 0;
        }
    }
}
