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

package com.volmit.adapt.api.skill;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.potion.BrewingManager;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.api.tick.TickedObject;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.PlayerSkillLine;
import com.volmit.adapt.api.xp.XPMultiplier;
import com.volmit.adapt.content.gui.SkillsGui;
import com.volmit.adapt.content.skill.*;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.M;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SkillRegistry extends TickedObject {
    public static final Map<String, Skill<?>> skills = new ConcurrentHashMap<>();

    public SkillRegistry() {
        super("registry", UUID.randomUUID() + "-sk", 1250);
        registerSkill(SkillAgility.class);
        registerSkill(SkillArchitect.class);
        registerSkill(SkillAxes.class);
        registerSkill(SkillBlocking.class);
        registerSkill(SkillChronos.class);
        registerSkill(SkillCrafting.class);
        registerSkill(SkillDiscovery.class);
        registerSkill(SkillEnchanting.class);
        registerSkill(SkillHerbalism.class);
        registerSkill(SkillHunter.class);
        registerSkill(SkillPickaxes.class);
        registerSkill(SkillRanged.class);
        registerSkill(SkillRift.class);
        registerSkill(SkillSeaborne.class);
        registerSkill(SkillStealth.class);
        registerSkill(SkillSwords.class);
        registerSkill(SkillTaming.class);
        registerSkill(SkillTragOul.class);
        registerSkill(SkillUnarmed.class);
        registerSkill(SkillExcavation.class);
        registerSkill(SkillBrewing.class);
        registerSkill(SkillNether.class);
    }

    @EventHandler
    public void on(PlayerExpChangeEvent e) {
        Player p = e.getPlayer();
        if (e.getAmount() > 0) {
            getPlayer(p).boostXPToRecents( 0.03, 10000);
        }
    }

    private boolean canInteract(Player player, Location targetLocation) {
        return Adapt.instance.getProtectorRegistry().getAllProtectors().stream().allMatch(protector -> protector.canInteract(player, targetLocation, null));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        boolean commonConditions = p.isSneaking() && e.getAction().equals(Action.RIGHT_CLICK_BLOCK) && e.getClickedBlock() != null;
        boolean isLectern = commonConditions && e.getClickedBlock().getType().equals(Material.LECTERN);
        boolean isObserver = commonConditions && e.getClickedBlock().getType().equals(Material.OBSERVER);
        boolean isAdaptActivator = !e.getBlockFace().equals(BlockFace.UP) && !e.getBlockFace().equals(BlockFace.DOWN) && !p.isSneaking() && e.getAction().equals(Action.RIGHT_CLICK_BLOCK)
                && e.getClickedBlock() != null
                && canInteract(p, e.getClickedBlock().getLocation())
                && e.getClickedBlock().getType().equals(Material.valueOf(AdaptConfig.get().adaptActivatorBlock)) && (p.getInventory().getItemInMainHand().getType().equals(Material.AIR)
                || !p.getInventory().getItemInMainHand().getType().isBlock()) &&
                (p.getInventory().getItemInOffHand().getType().equals(Material.AIR) || !p.getInventory().getItemInOffHand().getType().isBlock());

        if (isAdaptActivator) {
            for (Player players : e.getClickedBlock().getWorld().getPlayers()) {
                players.playSound(e.getClickedBlock().getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 0.72f);
                players.playSound(e.getClickedBlock().getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.35f, 0.755f);
            }
            SkillsGui.open(p);
            e.setCancelled(true);
            p.getWorld().spawnParticle(Particle.CRIT_MAGIC, e.getClickedBlock().getLocation().clone().add(0.5, 1, 0.5), 25, 0, 0, 0, 1.1);
            p.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, e.getClickedBlock().getLocation().clone().add(0.5, 1, 0.5), 12, 0, 0, 0, 1.1);
        }

        if (isLectern) {
            ItemStack it = p.getInventory().getItemInMainHand();
            if (it.getItemMeta() != null && !it.getItemMeta().getPersistentDataContainer().getKeys().isEmpty()) {
                e.setCancelled(true);
                playDebug(p);
                it.getItemMeta().getPersistentDataContainer().getKeys().forEach(k -> Bukkit.getServer().getConsoleSender().sendMessage(k + " = " + it.getItemMeta().getPersistentDataContainer().getOrDefault(k, PersistentDataType.STRING, "Not a String")));
            }
        }

        if (isObserver) {
            ItemStack it = p.getInventory().getItemInMainHand();
            if (it.getType().equals(Material.EXPERIENCE_BOTTLE)) {
                e.setCancelled(true);
                Bukkit.getServer().getConsoleSender().sendMessage("   ");
                p.setCooldown(Material.ENCHANTED_BOOK, 3);
                AdaptPlayer a = getPlayer(p);
                playDebug(p);

                String xv = a.getData().getMultiplier() - 1d > 0 ? "+" + Form.pc(a.getData().getMultiplier() - 1D) : Form.pc(a.getData().getMultiplier() - 1D);
                Bukkit.getServer().getConsoleSender().sendMessage("Global" + C.GRAY + ": " + C.GREEN + xv);

                for (XPMultiplier i : a.getData().getMultipliers()) {
                    String vv = i.getMultiplier() > 0 ? "+" + Form.pc(i.getMultiplier()) : Form.pc(i.getMultiplier());
                    Bukkit.getServer().getConsoleSender().sendMessage(C.GREEN + "* " + vv + C.GRAY + " for " + Form.duration(i.getGoodFor() - M.ms(), 0));
                }
                for (XPMultiplier i : Adapt.instance.getAdaptServer().getData().getMultipliers()) {
                    String vv = i.getMultiplier() > 0 ? "+" + Form.pc(i.getMultiplier()) : Form.pc(i.getMultiplier());
                    Bukkit.getServer().getConsoleSender().sendMessage(C.GREEN + "* " + vv + C.GRAY + " for " + Form.duration(i.getGoodFor() - M.ms(), 0));
                }

                for (PlayerSkillLine i : a.getData().getSkillLines().v()) {
                    Skill<?> s = i.getRawSkill(a);
                    String v = i.getMultiplier() - a.getData().getMultiplier() > 0 ? "+" + Form.pc(i.getMultiplier() - a.getData().getMultiplier()) : Form.pc(i.getMultiplier() - a.getData().getMultiplier());
                    Bukkit.getServer().getConsoleSender().sendMessage("  " + s.getDisplayName() + C.GRAY + ": " + s.getColor() + v);
                    for (XPMultiplier j : i.getMultipliers()) {
                        String vv = j.getMultiplier() > 0 ? "+" + Form.pc(j.getMultiplier()) : Form.pc(j.getMultiplier());
                        Bukkit.getServer().getConsoleSender().sendMessage("  " + s.getShortName() + C.GRAY + " " + vv + " for " + Form.duration(j.getGoodFor() - M.ms(), 0));
                    }
                }
            }
        }
    }
        private void playDebug(Player p) {
        p.playSound(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 1f, 0.6f);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.1f);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.6f);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.2f);

    }

    public Skill<?> getSkill(String i) {
        return skills.get(i);
    }

    public List<Skill<?>> getSkills() {
        return skills.v();
    }

    public void registerSkill(Class<? extends Skill<?>> skill) {
        try {
            Skill<?> sk = skill.getConstructor().newInstance();

            if (!sk.isEnabled()) {
                return;
            }

            skills.put(sk.getName(), sk);
            registerRecipes(sk);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    private void unregisterRecipes(Skill<?> s) {
        s.getRecipes().forEach(AdaptRecipe::unregister);
        s.getAdaptations().forEach(i -> {
            i.getRecipes().forEach(AdaptRecipe::unregister);
        });
    }

    private void registerRecipes(Skill<?> s) {
        s.getRecipes().forEach(AdaptRecipe::register);
        s.getAdaptations().forEach(i -> {
            i.getRecipes().forEach(AdaptRecipe::register);
            i.getBrewingRecipes().forEach(r -> BrewingManager.registerRecipe(i.getName(), r));
        });
    }

    @Override
    public void unregister() {
        for (Skill<?> i : skills.v()) {
            i.unregister();
            unregisterRecipes(i);
        }
    }

    @Override
    public void onTick() {

    }
}
