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

package com.volmit.adapt.content.adaptation.unarmed;

import com.volmit.adapt.AdaptConfig;
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
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class UnarmedComboChain extends SimpleAdaptation<UnarmedComboChain.Config> {
    private final Map<UUID, ComboState> combos = new HashMap<>();

    public UnarmedComboChain() {
        super("unarmed-combo-chain");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("unarmed.combo_chain.description"));
        setDisplayName(Localizer.dLocalize("unarmed.combo_chain.name"));
        setIcon(Material.BLAZE_POWDER);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(1800);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.IRON_INGOT)
                .key("challenge_unarmed_combo_5k")
                .title(Localizer.dLocalize("advancement.challenge_unarmed_combo_5k.title"))
                .description(Localizer.dLocalize("advancement.challenge_unarmed_combo_5k.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_unarmed_combo_5k").goal(5000).stat("unarmed.combo-chain.total-combo-hits").reward(400).build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.BLAZE_POWDER)
                .key("challenge_unarmed_combo_10")
                .title(Localizer.dLocalize("advancement.challenge_unarmed_combo_10.title"))
                .description(Localizer.dLocalize("advancement.challenge_unarmed_combo_10.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.BLAZE_ROD)
                .key("challenge_unarmed_combo_25")
                .title(Localizer.dLocalize("advancement.challenge_unarmed_combo_25.title"))
                .description(Localizer.dLocalize("advancement.challenge_unarmed_combo_25.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + getMaxStacks(level) + C.GRAY + " " + Localizer.dLocalize("unarmed.combo_chain.lore1"));
        v.addLore(C.GREEN + "+ " + Form.f(getDamagePerStack(level)) + C.GRAY + " " + Localizer.dLocalize("unarmed.combo_chain.lore2"));
        v.addLore(C.YELLOW + "* " + Form.duration(getComboWindowMillis(level), 1) + C.GRAY + " " + Localizer.dLocalize("unarmed.combo_chain.lore3"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled() || !(e.getDamager() instanceof Player p) || !hasAdaptation(p)) {
            return;
        }

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (isMelee(hand)) {
            combos.remove(p.getUniqueId());
            return;
        }

        long now = System.currentTimeMillis();
        int level = getLevel(p);
        ComboState state = combos.computeIfAbsent(p.getUniqueId(), id -> new ComboState());

        if (now - state.lastHitMillis > getComboWindowMillis(level)) {
            state.stacks = 0;
        }

        state.lastHitMillis = now;
        state.stacks = Math.min(getMaxStacks(level), state.stacks + 1);

        double bonus = state.stacks * getDamagePerStack(level);
        e.setDamage(e.getDamage() + bonus);
        playComboFeedback(p, e.getEntity().getLocation(), state.stacks, getMaxStacks(level));
        xp(p, bonus * getConfig().xpPerBonusDamage);
        getPlayer(p).getData().addStat("unarmed.combo-chain.total-combo-hits", 1);

        // Special achievements: reach a 10-hit or 25-hit combo
        if (state.stacks >= 10 && AdaptConfig.get().isAdvancements() && !getPlayer(p).getData().isGranted("challenge_unarmed_combo_10")) {
            getPlayer(p).getAdvancementHandler().grant("challenge_unarmed_combo_10");
        }
        if (state.stacks >= 25 && AdaptConfig.get().isAdvancements() && !getPlayer(p).getData().isGranted("challenge_unarmed_combo_25")) {
            getPlayer(p).getAdvancementHandler().grant("challenge_unarmed_combo_25");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerInteractEvent e) {
        Action action = e.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player p = e.getPlayer();
        if (!hasAdaptation(p) || isMelee(p.getInventory().getItemInMainHand())) {
            return;
        }

        ComboState state = combos.get(p.getUniqueId());
        if (state == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - state.lastHitMillis > getConfig().missResetGraceMillis) {
            combos.remove(p.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerQuitEvent e) {
        combos.remove(e.getPlayer().getUniqueId());
    }

    private int getMaxStacks(int level) {
        return Math.max(1, (int) Math.round(getConfig().maxStacksBase + (getLevelPercent(level) * getConfig().maxStacksFactor)));
    }

    private double getDamagePerStack(int level) {
        return getConfig().damagePerStackBase + (getLevelPercent(level) * getConfig().damagePerStackFactor);
    }

    private long getComboWindowMillis(int level) {
        return Math.max(250, (long) Math.round(getConfig().comboWindowMillisBase + (getLevelPercent(level) * getConfig().comboWindowMillisFactor)));
    }

    private void playComboFeedback(Player p, org.bukkit.Location hitLocation, int stacks, int maxStacks) {
        float pitch = Math.min(2.0f, 0.85f + (stacks * 0.09f));
        if (stacks >= maxStacks) {
            SoundPlayer.of(p.getWorld()).play(hitLocation, Sound.BLOCK_ANVIL_PLACE, 0.55f, 1.7f);
            p.spawnParticle(Particle.TOTEM_OF_UNDYING, hitLocation.clone().add(0, 1, 0), 5, 0.2, 0.4, 0.2, 0.05);
            return;
        }

        SoundPlayer.of(p.getWorld()).play(hitLocation, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.55f, pitch);
        p.spawnParticle(Particle.CRIT, hitLocation.clone().add(0, 0.9, 0), 6 + Math.min(16, stacks * 2), 0.22, 0.34, 0.22, 0.1);
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
    @ConfigDescription("Consecutive unarmed hits build combo stacks for increased punch damage.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Stacks Base for the Unarmed Combo Chain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxStacksBase = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Stacks Factor for the Unarmed Combo Chain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxStacksFactor = 8;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Damage Per Stack Base for the Unarmed Combo Chain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damagePerStackBase = 0.2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Damage Per Stack Factor for the Unarmed Combo Chain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damagePerStackFactor = 0.85;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Combo Window Millis Base for the Unarmed Combo Chain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double comboWindowMillisBase = 1300;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Combo Window Millis Factor for the Unarmed Combo Chain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double comboWindowMillisFactor = 1400;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Miss Reset Grace Millis for the Unarmed Combo Chain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        long missResetGraceMillis = 280;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Bonus Damage for the Unarmed Combo Chain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerBonusDamage = 4.1;
    }

    private static class ComboState {
        private int stacks = 0;
        private long lastHitMillis = 0L;
    }
}
