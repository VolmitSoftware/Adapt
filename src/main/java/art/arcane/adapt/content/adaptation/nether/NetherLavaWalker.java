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

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.Element;
import art.arcane.volmlib.util.format.Form;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class NetherLavaWalker extends SimpleAdaptation<NetherLavaWalker.Config> {
    public NetherLavaWalker() {
        super("nether-lava-walker");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("nether.lava_walker.description"));
        setDisplayName(Localizer.dLocalize("nether.lava_walker.name"));
        setIcon(Material.MAGMA_BLOCK);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(1000);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.MAGMA_BLOCK)
                .key("challenge_nether_lava_1k")
                .title(Localizer.dLocalize("advancement.challenge_nether_lava_1k.title"))
                .description(Localizer.dLocalize("advancement.challenge_nether_lava_1k.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.NETHERITE_INGOT)
                        .key("challenge_nether_lava_25k")
                        .title(Localizer.dLocalize("advancement.challenge_nether_lava_25k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_nether_lava_25k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_nether_lava_1k", "nether.lava-walker.blocks-walked", 1000, 300);
        registerMilestone("challenge_nether_lava_25k", "nether.lava-walker.blocks-walked", 25000, 1000);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getStride(level), 0) + C.GRAY + " " + Localizer.dLocalize("nether.lava_walker.lore1"));
        v.addLore(C.YELLOW + "* " + getHungerCost(level) + C.GRAY + " " + Localizer.dLocalize("nether.lava_walker.lore2"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void on(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!hasAdaptation(p) || !p.getWorld().getEnvironment().name().contains("NETHER")) {
            return;
        }

        if (p.isFlying() || p.isGliding() || p.isInsideVehicle() || p.getFoodLevel() <= 0) {
            return;
        }

        Block feet = p.getLocation().getBlock();
        Block below = p.getLocation().clone().add(0, -1, 0).getBlock();
        if (!(isLava(feet) || isLava(below))) {
            return;
        }

        int level = getLevel(p);
        if (getStorageLong(p, "lavaWalkerCooldown", 0L) > System.currentTimeMillis()) {
            return;
        }

        Vector velocity = p.getVelocity();
        Vector dir = p.getLocation().getDirection().setY(0).normalize().multiply(getStride(level));
        p.setVelocity(new Vector(dir.getX(), Math.max(0.16, velocity.getY()), dir.getZ()));
        p.setFallDistance(0);
        p.setFireTicks(0);
        p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, getConfig().fireResistTicks, 0, false, false));

        int hungerCost = getHungerCost(level);
        p.setFoodLevel(Math.max(0, p.getFoodLevel() - hungerCost));
        setStorage(p, "lavaWalkerCooldown", System.currentTimeMillis() + getCooldownMillis(level));
        xp(p, getConfig().xpPerStride);
        getPlayer(p).getData().addStat("nether.lava-walker.blocks-walked", 1);
    }

    private boolean isLava(Block b) {
        return b.getType() == Material.LAVA;
    }

    private double getStride(int level) {
        return getConfig().strideBase + (getLevelPercent(level) * getConfig().strideFactor);
    }

    private int getHungerCost(int level) {
        return Math.max(1, (int) Math.round(getConfig().hungerCostBase - (getLevelPercent(level) * getConfig().hungerCostFactor)));
    }

    private long getCooldownMillis(int level) {
        return Math.max(100, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
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
    @ConfigDescription("Stride over lava in the Nether at the cost of hunger.")
    protected static class Config {
        @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.75;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stride Base for the Nether Lava Walker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double strideBase = 0.18;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stride Factor for the Nether Lava Walker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double strideFactor = 0.6;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Hunger Cost Base for the Nether Lava Walker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double hungerCostBase = 3;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Hunger Cost Factor for the Nether Lava Walker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double hungerCostFactor = 2;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Nether Lava Walker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownMillisBase = 900;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Nether Lava Walker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownMillisFactor = 700;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fire Resist Ticks for the Nether Lava Walker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int fireResistTicks = 80;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Stride for the Nether Lava Walker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerStride = 3.5;
    }
}
