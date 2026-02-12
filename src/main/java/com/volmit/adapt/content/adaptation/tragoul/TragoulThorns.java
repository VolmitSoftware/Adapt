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

package com.volmit.adapt.content.adaptation.tragoul;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.config.ConfigDescription;
import de.slikey.effectlib.effect.BleedEffect;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;


public class TragoulThorns extends SimpleAdaptation<TragoulThorns.Config> {
    private final Map<Player, Long> cooldowns;

    public TragoulThorns() {
        super("tragoul-thorns");
        registerConfiguration(TragoulThorns.Config.class);
        setDescription(Localizer.dLocalize("tragoul.thorns.description"));
        setDisplayName(Localizer.dLocalize("tragoul.thorns.name"));
        setIcon(Material.ECHO_SHARD);
        setInterval(25000);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        cooldowns = new HashMap<>();
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.CACTUS)
                .key("challenge_tragoul_thorns_500")
                .title(Localizer.dLocalize("advancement.challenge_tragoul_thorns_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_tragoul_thorns_500.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.IRON_CHESTPLATE)
                        .key("challenge_tragoul_thorns_5k")
                        .title(Localizer.dLocalize("advancement.challenge_tragoul_thorns_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_tragoul_thorns_5k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_tragoul_thorns_500").goal(500).stat("tragoul.thorns.damage-reflected").reward(400).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_tragoul_thorns_5k").goal(5000).stat("tragoul.thorns.damage-reflected").reward(1500).build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.CACTUS)
                .key("challenge_tragoul_thorns_kill")
                .title(Localizer.dLocalize("advancement.challenge_tragoul_thorns_kill.title"))
                .description(Localizer.dLocalize("advancement.challenge_tragoul_thorns_kill.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "" + getConfig().damageMultiplierPerLevel * level + "x " + Localizer.dLocalize("tragoul.thorns.lore1"));
    }



    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getEntity() instanceof Player p && hasAdaptation(p)) {
            Long cooldown = cooldowns.get(p);
            if (cooldown != null && cooldown + 1500 > System.currentTimeMillis())
                return;

            cooldowns.put(p, System.currentTimeMillis());

            LivingEntity le = null;

            if (e.getDamager() instanceof LivingEntity) {
                le = (LivingEntity) e.getDamager();
            } else if (e.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity) {
                le = (LivingEntity) projectile.getShooter();
            }

            if (le != null) {
                if (getConfig().showParticles) {
                    BleedEffect blood = new BleedEffect(Adapt.instance.adaptEffectManager);  // Enemy gets blood
                    blood.setEntity(le);
                    blood.height = -1;
                    blood.iterations = 1;
                    blood.start();
                }
                double reflectedDamage = getConfig().damageMultiplierPerLevel * getLevel(p);
                double healthBefore = le.getHealth();
                le.damage(reflectedDamage, p);
                getPlayer(p).getData().addStat("tragoul.thorns.damage-reflected", (int) reflectedDamage);
                if (healthBefore <= reflectedDamage && AdaptConfig.get().isAdvancements() && !getPlayer(p).getData().isGranted("challenge_tragoul_thorns_kill")) {
                    getPlayer(p).getAdvancementHandler().grant("challenge_tragoul_thorns_kill");
                }
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }


    @Override
    public void onTick() {
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    @ConfigDescription("Reflect damage back to your attacker.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Tragoul Thorns adaptation.", impact = "True enables this behavior and false disables it.")
        boolean showParticles = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Damage Multiplier Per Level for the Tragoul Thorns adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damageMultiplierPerLevel = 1.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 1.10;
    }
}
