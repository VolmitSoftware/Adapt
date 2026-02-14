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

package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.content.event.AdaptAdaptationTeleportEvent;
import art.arcane.adapt.content.item.BoundEyeOfEnder;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.math.M;
import art.arcane.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;


import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.inventorygui.Element;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.reflect.registries.Particles;

public class RiftGate extends SimpleAdaptation<RiftGate.Config> {
    public RiftGate() {
        super("rift-gate");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("rift.gate.description"));
        setDisplayName(Localizer.dLocalize("rift.gate.name"));
        setIcon(Material.RESPAWN_ANCHOR);
        setBaseCost(0);
        setCostFactor(0);
        setMaxLevel(1);
        setInitialCost(30);
        setInterval(1322);
        registerRecipe(AdaptRecipe.shapeless()
                .key("rift-recall-gate")
                .ingredient(Material.ENDER_PEARL)
                .ingredient(Material.AMETHYST_SHARD)
                .ingredient(Material.EMERALD)
                .result(BoundEyeOfEnder.io.withData(new BoundEyeOfEnder.Data(null)))
                .build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.ENDER_PEARL)
                .key("challenge_rift_gate_100")
                .title(Localizer.dLocalize("advancement.challenge_rift_gate_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_rift_gate_100.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.ENDER_EYE)
                        .key("challenge_rift_gate_50k_dist")
                        .title(Localizer.dLocalize("advancement.challenge_rift_gate_50k_dist.title"))
                        .description(Localizer.dLocalize("advancement.challenge_rift_gate_50k_dist.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_rift_gate_100", "rift.gate.teleports", 100, 400);
        registerMilestone("challenge_rift_gate_50k_dist", "rift.gate.total-distance", 50000, 1500);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.YELLOW + Localizer.dLocalize("rift.gate.lore1"));
        v.addLore(C.RED + Localizer.dLocalize("rift.gate.lore2"));
        v.addLore(C.ITALIC + Localizer.dLocalize("rift.gate.lore3") + C.UNDERLINE + C.RED + Localizer.dLocalize("rift.gate.lore4"));
    }


    @EventHandler
    public void on(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        ItemStack offHand = p.getInventory().getItemInOffHand();
        Location location = e.getClickedBlock() == null ? p.getLocation() : e.getClickedBlock().getLocation();

        // Deny usage if the offhand contains a bindable item
        if (BoundEyeOfEnder.isBindableItem(offHand) && e.getHand() != null && e.getHand().equals(EquipmentSlot.OFF_HAND)) {
            e.setCancelled(true);
            return;
        }

        if (p.getInventory().getItemInMainHand().getType().equals(Material.ENDER_EYE)
                && !p.hasCooldown(Material.ENDER_EYE)
                && hasAdaptation(p)
                && BoundEyeOfEnder.isBindableItem(hand)) {

            e.setCancelled(true);
            Adapt.verbose(" - Player Main hand: " + hand.getType());
            switch (e.getAction()) {
                case LEFT_CLICK_BLOCK, LEFT_CLICK_AIR -> {
                    if (p.isSneaking()) {
                        Adapt.verbose("Linking eye");
                        linkEye(p, location);
                    }
                }
                case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> // use
                {
                    if (isBound(hand)) {
                        openEye(p);
                    }
                }
            }
        }
    }


    private void handleEyeOfEnderInteraction(PlayerInteractEvent event, Player player, Block block) {
        boolean sneaking = player.isSneaking();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        Location location = block == null ? player.getLocation() : block.getLocation();

        switch (event.getAction()) {
            case LEFT_CLICK_BLOCK, LEFT_CLICK_AIR -> {
                if (sneaking) {
                    if (isBound(mainHand)) {
                        unlinkEye(player);
                    } else {
                        linkEye(player, location);
                    }
                }
            }
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> {
                if (isBound(mainHand)) {
                    openEye(player);
                }
            }
            default -> {
            }
        }
    }

    private boolean isBound(ItemStack stack) {
        return stack.getType().equals(Material.ENDER_EYE) && BoundEyeOfEnder.getLocation(stack) != null;
    }

    private void unlinkEye(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        decrementItemstack(hand, p);
        ItemStack eye = new ItemStack(Material.ENDER_EYE);
        p.getInventory().addItem(eye).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
    }

    private void linkEye(Player p, Location location) {
        if (areParticlesEnabled()) {
            vfxCuboidOutline(location.getBlock(), location.add(0, 1, 0).getBlock(), Particle.REVERSE_PORTAL);
        }
        SoundPlayer sp = SoundPlayer.of(p);
        sp.play(p.getLocation(), Sound.ENTITY_ENDER_EYE_DEATH, 0.50f, 0.22f);
        ItemStack hand = p.getInventory().getItemInMainHand();

        if (hand.getAmount() == 1) {
            BoundEyeOfEnder.setData(hand, location);
        } else {
            hand.setAmount(hand.getAmount() - 1);
            ItemStack eye = BoundEyeOfEnder.withData(location);
            p.getInventory().addItem(eye).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
        }
    }


    private void openEye(Player p) {
        Adapt.verbose("Using eye");
        SoundPlayer sp = SoundPlayer.of(p);
        Location l = BoundEyeOfEnder.getLocation(p.getInventory().getItemInMainHand());
        ItemStack hand = p.getInventory().getItemInMainHand();

        if (getConfig().consumeOnUse) {
            xp(p, 75);
            decrementItemstack(hand, p);
        } else {
            if (p.getCooldown(Material.ENDER_EYE) > 0) {
                sp.play(p.getLocation(), Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 1, 1);
                return;
            }
        }
        p.setCooldown(Material.ENDER_EYE, 150);


        if (RiftResist.hasRiftResistPerk(getPlayer(p))) {
            RiftResist.riftResistStackAdd(p, 150, 3);
        }

        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 10, true, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 85, 0, true, false, false));
        sp.play(l, Sound.BLOCK_LODESTONE_PLACE, 1f, 0.1f);
        sp.play(l, Sound.BLOCK_BELL_RESONATE, 1f, 0.1f);

        new BukkitRunnable() {
            private long dur = 4000;
            private double radius = 2.0;
            private double adder = 0.0;
            private final Color color = Color.fromBGR(0, 0, 0);
            private boolean initialRingShown = false;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    cancel();
                    return;
                }

                if (!initialRingShown) {
                    vfxFastRing(p.getLocation(), radius, color);
                    initialRingShown = true;
                }

                dur -= 50;
                if (dur <= 0) {
                    cancel();
                    return;
                }

                adder += 0.02;
                radius *= 0.9;
                vfxFastRing(p.getLocation().add(0, adder, 0), radius, color);
            }
        }.runTaskTimer(Adapt.instance, 0L, 1L);
        vfxLevelUp(p);
        sp.play(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 5.35f, 0.1f);
        J.s(() -> {
            AdaptAdaptationTeleportEvent event = new AdaptAdaptationTeleportEvent(!Bukkit.isPrimaryThread(), getPlayer(p), this, p.getLocation(), l);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return;
            }

            getPlayer(p).getData().addStat("rift.teleports", 1);
            getPlayer(p).getData().addStat("rift.gate.teleports", 1);
            getPlayer(p).getData().addStat("rift.gate.total-distance", (int) p.getLocation().distance(l));
            p.teleport(l, PlayerTeleportEvent.TeleportCause.PLUGIN);
            vfxLevelUp(p);
            sp.play(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 5.35f, 0.1f);
        }, 85);
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
    @ConfigDescription("Craft a gate item to teleport to a marked location.")
    protected static class Config {
        @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Consume On Use for the Rift Gate adaptation.", impact = "True enables this behavior and false disables it.")
        boolean consumeOnUse = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Rift Gate adaptation.", impact = "True enables this behavior and false disables it.")
        boolean showParticles = true;
    }
}
