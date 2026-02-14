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

package art.arcane.adapt.content.adaptation.discovery;
import art.arcane.volmlib.util.format.Form;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.math.M;
import art.arcane.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerExpChangeEvent;

import java.util.List;

import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.inventorygui.Element;
import art.arcane.adapt.util.common.misc.SoundPlayer;

import static xyz.xenondevs.particle.utils.MathUtils.RANDOM;

public class DiscoveryUnity extends SimpleAdaptation<DiscoveryUnity.Config> {
    public DiscoveryUnity() {
        super("discovery-unity");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("discovery.unity.description"));
        setDisplayName(Localizer.dLocalize("discovery.unity.name"));
        setIcon(Material.END_CRYSTAL);
        setBaseCost(getConfig().baseCost);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInterval(666);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.EXPERIENCE_BOTTLE)
                .key("challenge_discovery_unity_5k")
                .title(Localizer.dLocalize("advancement.challenge_discovery_unity_5k.title"))
                .description(Localizer.dLocalize("advancement.challenge_discovery_unity_5k.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.ENCHANTING_TABLE)
                        .key("challenge_discovery_unity_50k")
                        .title(Localizer.dLocalize("advancement.challenge_discovery_unity_50k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_discovery_unity_50k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_discovery_unity_5k", "discovery.unity.orbs-distributed", 5000, 400);
        registerMilestone("challenge_discovery_unity_50k", "discovery.unity.orbs-distributed", 50000, 1500);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getXPGained(getLevelPercent(level), 1), 0) + " " + Localizer.dLocalize("discovery.unity.lore1") + C.GRAY + " " + Localizer.dLocalize("discovery.unity.lore2"));
    }

    //Give random XP to the player when they gain XP!
    @EventHandler(priority = EventPriority.LOW)
    public void on(PlayerExpChangeEvent e) {
        Player p = e.getPlayer();
        SoundPlayer sp = SoundPlayer.of(p);
        AdaptPlayer ap = getPlayer(p);
        if (hasAdaptation(p) && e.getAmount() > 0) {
            xp(p, 5);
            sp.play(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.9f);
            //get a random skill that they have unlocked already
            List<PlayerSkillLine> skills = ap.getData().getSkillLines().sortV();
            if (skills.size() > 0) {
                PlayerSkillLine skill = skills.get(RANDOM.nextInt(skills.size()));
                //give them a random amount of XP in that skill
                skill.giveXPFresh(Adapt.instance.getAdaptServer().getPlayer(p).getNot(), getXPGained(getLevelPercent(getLevel(p)), RANDOM.nextInt(3) + 1));
                getPlayer(p).getData().addStat("discovery.unity.orbs-distributed", 1);
            }

        }
    }

    private double getXPGained(double factor, int amount) {
        return amount * getConfig().xpGainedMultiplier * factor;
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
    @ConfigDescription("Collecting Experience Orbs adds XP to random skills.")
    protected static class Config {
        @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 2;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 3;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.3;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 7;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Gained Multiplier for the Discovery Unity adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpGainedMultiplier = 8;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Boost Multiplier for the Discovery Unity adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpBoostMultiplier = 0.01;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Boost Duration for the Discovery Unity adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int xpBoostDuration = 15000;
    }
}
