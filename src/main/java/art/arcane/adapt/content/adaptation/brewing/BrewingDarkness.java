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

package art.arcane.adapt.content.adaptation.brewing;

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.data.WorldData;
import art.arcane.adapt.api.potion.BrewingRecipe;
import art.arcane.adapt.api.potion.PotionBuilder;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.content.matter.BrewingStandOwner;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.Element;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;


public class BrewingDarkness extends SimpleAdaptation<BrewingDarkness.Config> {
    public BrewingDarkness() {
        super("brewing-darkness");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("brewing.darkness.description"));
        setDisplayName(Localizer.dLocalize("brewing.darkness.name"));
        setIcon(Material.BLACK_CONCRETE);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(1335);
        registerBrewingRecipe(BrewingRecipe.builder()
                .id("brewing-darkness")
                .brewingTime(320)
                .fuelCost(16)
                .ingredient(Material.BLACK_CONCRETE)
                .basePotion(PotionBuilder.vanilla(PotionBuilder.Type.REGULAR, PotionType.NIGHT_VISION))
                .result(PotionBuilder.of(PotionBuilder.Type.REGULAR)
                        .setName("Bottled Darkness")
                        .setColor(Color.BLACK)
                        .addEffect(PotionEffectType.DARKNESS, 600, 100, true, true, true)
                        .build())
                .build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.BREWING_STAND)
                .key("challenge_brewing_darkness_25")
                .title(Localizer.dLocalize("advancement.challenge_brewing_darkness_25.title"))
                .description(Localizer.dLocalize("advancement.challenge_brewing_darkness_25.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerMilestone("challenge_brewing_darkness_25", "brewing.darkness.potions-brewed", 25, 300);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Localizer.dLocalize("brewing.darkness.lore1"));
        v.addLore(C.GRAY + "- " + Localizer.dLocalize("brewing.darkness.lore2"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BrewEvent e) {
        if (e.isCancelled()) {
            return;
        }
        BrewingStandOwner owner = WorldData.of(e.getBlock().getWorld()).get(e.getBlock(), BrewingStandOwner.class);
        if (owner != null) {
            getServer().peekData(owner.getOwner()).addStat("brewing.darkness.potions-brewed", 1);
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
    @ConfigDescription("Brew a Potion of Darkness from NightVision Potion and Black Concrete.")
    protected static class Config {
        @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 3;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 1;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 1;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 2;
    }
}
