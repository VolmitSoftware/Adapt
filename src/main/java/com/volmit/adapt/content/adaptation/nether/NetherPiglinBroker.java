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

package com.volmit.adapt.content.adaptation.nether;

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
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Piglin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PiglinBarterEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class NetherPiglinBroker extends SimpleAdaptation<NetherPiglinBroker.Config> {
    public NetherPiglinBroker() {
        super("nether-piglin-broker");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("nether.piglin_broker.description"));
        setDisplayName(Localizer.dLocalize("nether.piglin_broker.name"));
        setIcon(Material.GOLD_INGOT);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(2300);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.GOLD_INGOT)
                .key("challenge_nether_piglin_100")
                .title(Localizer.dLocalize("advancement.challenge_nether_piglin_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_nether_piglin_100.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.GOLD_BLOCK)
                        .key("challenge_nether_piglin_2500")
                        .title(Localizer.dLocalize("advancement.challenge_nether_piglin_2500.title"))
                        .description(Localizer.dLocalize("advancement.challenge_nether_piglin_2500.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_nether_piglin_100").goal(100).stat("nether.piglin-broker.improved-barters").reward(300).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_nether_piglin_2500").goal(2500).stat("nether.piglin-broker.improved-barters").reward(1000).build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getExtraRollChance(level), 0) + C.GRAY + " " + Localizer.dLocalize("nether.piglin_broker.lore1"));
        v.addLore(C.GREEN + "+ " + Form.pc(getRareBonusChance(level), 0) + C.GRAY + " " + Localizer.dLocalize("nether.piglin_broker.lore2"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PiglinBarterEvent e) {
        Piglin piglin = e.getEntity();
        Player broker = findBroker(piglin, getConfig().brokerRange);
        if (broker == null) {
            return;
        }

        int level = getLevel(broker);
        List<ItemStack> outcome = e.getOutcome();
        if (outcome.isEmpty()) {
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean changed = false;
        if (random.nextDouble() <= getExtraRollChance(level)) {
            ItemStack bonus = outcome.get(random.nextInt(outcome.size())).clone();
            int amount = Math.max(1, (int) Math.round(bonus.getAmount() * getAmountMultiplier(level)));
            bonus.setAmount(Math.min(bonus.getMaxStackSize(), amount));
            outcome.add(bonus);
            changed = true;
        }

        if (random.nextDouble() <= getRareBonusChance(level)) {
            outcome.add(getRareBonusRoll());
            changed = true;
        }

        if (!changed) {
            return;
        }

        SoundPlayer.of(broker.getWorld()).play(broker.getLocation(), Sound.ENTITY_PIGLIN_ADMIRING_ITEM, 0.9f, 1.25f);
        xp(broker, getConfig().xpOnBoostedBarter);
        getPlayer(broker).getData().addStat("nether.piglin-broker.improved-barters", 1);
    }

    private Player findBroker(Piglin piglin, double range) {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : piglin.getWorld().getPlayers()) {
            if (!hasAdaptation(p) || p.getLocation().distanceSquared(piglin.getLocation()) > (range * range)) {
                continue;
            }

            double d = p.getLocation().distanceSquared(piglin.getLocation());
            if (d < bestDist) {
                best = p;
                bestDist = d;
            }
        }

        return best;
    }

    private ItemStack getRareBonusRoll() {
        return switch (ThreadLocalRandom.current().nextInt(5)) {
            case 0 -> new ItemStack(Material.ENDER_PEARL, 1);
            case 1 -> new ItemStack(Material.OBSIDIAN, 2);
            case 2 -> new ItemStack(Material.STRING, 4);
            case 3 -> new ItemStack(Material.IRON_NUGGET, 6);
            default -> new ItemStack(Material.SPECTRAL_ARROW, 2);
        };
    }

    private double getExtraRollChance(int level) {
        return Math.min(getConfig().maxExtraRollChance, getConfig().extraRollChanceBase + (getLevelPercent(level) * getConfig().extraRollChanceFactor));
    }

    private double getRareBonusChance(int level) {
        return Math.min(getConfig().maxRareBonusChance, getConfig().rareBonusChanceBase + (getLevelPercent(level) * getConfig().rareBonusChanceFactor));
    }

    private double getAmountMultiplier(int level) {
        return Math.max(1.0, getConfig().amountMultiplierBase + (getLevelPercent(level) * getConfig().amountMultiplierFactor));
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
    @ConfigDescription("Nearby piglin bartering can yield extra or improved rolls.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Broker Range for the Nether Piglin Broker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double brokerRange = 18;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Extra Roll Chance Base for the Nether Piglin Broker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double extraRollChanceBase = 0.1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Extra Roll Chance Factor for the Nether Piglin Broker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double extraRollChanceFactor = 0.45;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Extra Roll Chance for the Nether Piglin Broker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxExtraRollChance = 0.6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Rare Bonus Chance Base for the Nether Piglin Broker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double rareBonusChanceBase = 0.03;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Rare Bonus Chance Factor for the Nether Piglin Broker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double rareBonusChanceFactor = 0.2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Rare Bonus Chance for the Nether Piglin Broker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxRareBonusChance = 0.25;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Amount Multiplier Base for the Nether Piglin Broker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double amountMultiplierBase = 1.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Amount Multiplier Factor for the Nether Piglin Broker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double amountMultiplierFactor = 0.5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp On Boosted Barter for the Nether Piglin Broker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpOnBoostedBarter = 12;
    }
}
