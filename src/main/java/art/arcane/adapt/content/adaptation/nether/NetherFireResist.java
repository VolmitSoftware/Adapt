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
package art.arcane.adapt.content.adaptation.nether;
import art.arcane.volmlib.util.format.Form;

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.math.M;
import art.arcane.adapt.util.config.ConfigDescription;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.concurrent.ThreadLocalRandom;

import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.inventorygui.Element;

public class NetherFireResist extends SimpleAdaptation<NetherFireResist.Config> {
    public NetherFireResist() {
        super("nether-fire-resist");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("nether.fire_resist.description"));
        setDisplayName(Localizer.dLocalize("nether.fire_resist.name"));
        setIcon(Material.FIRE_CHARGE);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(4333);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.FIRE_CHARGE)
                .key("challenge_nether_fire_200")
                .title(Localizer.dLocalize("advancement.challenge_nether_fire_200.title"))
                .description(Localizer.dLocalize("advancement.challenge_nether_fire_200.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.MAGMA_CREAM)
                        .key("challenge_nether_fire_5k")
                        .title(Localizer.dLocalize("advancement.challenge_nether_fire_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_nether_fire_5k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_nether_fire_200", "nether.fire-resist.negated", 200, 300);
        registerMilestone("challenge_nether_fire_5k", "nether.fire-resist.negated", 5000, 1000);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.RED + "+ " + Form.pc(getFireResist(level), 0) + C.GRAY + " " + Localizer.dLocalize("nether.fire_resist.lore1"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void on(EntityDamageEvent e) {
        if (e.isCancelled()) {
            return;
        }

        if (!(e.getEntity() instanceof Player p)) {
            return;
        }

        if (!hasAdaptation(p)) {
            return;
        }

        if (e.getCause() != EntityDamageEvent.DamageCause.FIRE && e.getCause() != EntityDamageEvent.DamageCause.FIRE_TICK) {
            return;
        }


        if (ThreadLocalRandom.current().nextDouble() < getFireResist(getLevel(p))) {
            e.setCancelled(true);
            getPlayer(p).getData().addStat("nether.fire-resist.negated", 1);
        }
    }

    public double getFireResist(double level) {
        return getConfig().fireResistBase + (getConfig().fireResistFactor * level);
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

    @Data
    @NoArgsConstructor
    @ConfigDescription("Chance to negate the burning effect.")
    public static class Config {
        @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.75;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 3;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 6;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fire Resist Base for the Nether Fire Resist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double fireResistBase = 0.10;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fire Resist Factor for the Nether Fire Resist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double fireResistFactor = 0.25;
    }
}
