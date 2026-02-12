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

package com.volmit.adapt.content.adaptation.brewing;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.data.WorldData;
import com.volmit.adapt.api.potion.BrewingRecipe;
import com.volmit.adapt.api.potion.PotionBuilder;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.matter.BrewingStandOwner;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.reflect.registries.PotionTypes;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.potion.PotionEffectType;


public class BrewingAbsorption extends SimpleAdaptation<BrewingAbsorption.Config> {
    public BrewingAbsorption() {
        super("brewing-absorption");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("brewing.absorption.description"));
        setDisplayName(Localizer.dLocalize("brewing.absorption.name"));
        setIcon(Material.QUARTZ);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(1333);
        registerBrewingRecipe(BrewingRecipe.builder()
                .id("brewing-absorption-1")
                .brewingTime(320)
                .fuelCost(16)
                .ingredient(Material.QUARTZ)
                .basePotion(PotionBuilder.vanilla(PotionBuilder.Type.REGULAR, PotionTypes.INSTANT_HEAL))
                .result(PotionBuilder.of(PotionBuilder.Type.REGULAR)
                        .setName("Bottled Absorption")
                        .setColor(Color.GRAY)
                        .addEffect(PotionEffectType.ABSORPTION, 1200, 1, true, true, true)
                        .build())
                .build()
        );
        registerBrewingRecipe(BrewingRecipe.builder()
                .id("brewing-absorption-2")
                .brewingTime(320)
                .fuelCost(32)
                .ingredient(Material.QUARTZ_BLOCK)
                .basePotion(PotionBuilder.vanilla(PotionBuilder.Type.REGULAR, PotionTypes.INSTANT_HEAL))
                .result(PotionBuilder.of(PotionBuilder.Type.REGULAR)
                        .setName("Bottled Haste 2")
                        .setColor(Color.GRAY)
                        .addEffect(PotionEffectType.ABSORPTION, 600, 2, true, true, true)
                        .build())
                .build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.GOLDEN_APPLE)
                .key("challenge_brewing_absorption_25")
                .title(Localizer.dLocalize("advancement.challenge_brewing_absorption_25.title"))
                .description(Localizer.dLocalize("advancement.challenge_brewing_absorption_25.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_brewing_absorption_25").goal(25).stat("brewing.absorption.potions-brewed").reward(300).build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Localizer.dLocalize("brewing.absorption.lore1"));
        v.addLore(C.GREEN + "+ " + Localizer.dLocalize("brewing.absorption.lore2"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BrewEvent e) {
        if (e.isCancelled()) {
            return;
        }
        BrewingStandOwner owner = WorldData.of(e.getBlock().getWorld()).get(e.getBlock(), BrewingStandOwner.class);
        if (owner != null) {
            getServer().peekData(owner.getOwner()).addStat("brewing.absorption.potions-brewed", 1);
        }
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
    @ConfigDescription("Brew a Potion of Absorption from Instant Heal and Quartz.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 2;
    }
}
