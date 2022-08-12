package com.volmit.adapt.content.adaptation.rift;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.content.item.BoundEyeOfEnder;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;


public class RiftGate extends SimpleAdaptation<RiftGate.Config> {
    public RiftGate() {
        super("rift-gate");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Rift","RiftGate", "Description"));
        setDisplayName(Adapt.dLocalize("Rift","RiftGate", "Name"));
        setIcon(Material.END_PORTAL_FRAME);
        setBaseCost(0);
        setCostFactor(0);
        setMaxLevel(1);
        setInitialCost(30);
        setInterval(50);
        registerRecipe(AdaptRecipe.shapeless()
            .key("rift-recall-gate")
            .ingredient(Material.ENDER_PEARL)
                .ingredient(Material.AMETHYST_SHARD)
                .ingredient(Material.EMERALD)
            .result(BoundEyeOfEnder.io.withData(new BoundEyeOfEnder.Data(null)))
            .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.YELLOW + Adapt.dLocalize("Rift","RiftGate", "Lore1"));
        v.addLore(C.RED + Adapt.dLocalize("Rift","RiftGate", "Lore2"));
        v.addLore(C.ITALIC + Adapt.dLocalize("Rift","RiftGate", "Lore3") + C.UNDERLINE + C.RED + Adapt.dLocalize("Rift","RiftGate", "Lore4"));
    }


    @EventHandler
    public void on(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if(hand.getItemMeta() == null|| hand.getItemMeta().getLore() == null || !hasAdaptation(p) ) {
            return;
        }
        if (!hand.getItemMeta().getLore().contains("Ocular Anchor") && !hand.getType().equals(Material.ENDER_EYE)) {
            return;
        }

        Location location = null;


        if(e.getClickedBlock() == null) {
            location = e.getPlayer().getLocation();

        } else {
            location = new Location(e.getClickedBlock().getLocation().getWorld(),
                e.getClickedBlock().getLocation().getX() + 0.5,
                e.getClickedBlock().getLocation().getY() + 1,
                e.getClickedBlock().getLocation().getZ() + 0.5);
        }


        e.setCancelled(true);

        switch(e.getAction()) {
            case LEFT_CLICK_BLOCK -> {
                if(p.isSneaking()) {
                    linkEye(p, location);
                }
            }
            case LEFT_CLICK_AIR -> {
                if(p.isSneaking() && isBound(hand)) {
                    unlinkEye(p);
                } else if(p.isSneaking() && !isBound(hand)) {
                    linkEye(p, location);
                }
            }

            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> { // use
                openEye(p);
            }

        }

    }



    private void openEye(Player p) {
        Location l = BoundEyeOfEnder.getLocation(p.getInventory().getItemInMainHand());
        ItemStack hand = p.getInventory().getItemInMainHand();

        getSkill().xp(p, 75);
        if(hand.getAmount() > 1) { // consume the hand
            hand.setAmount(hand.getAmount() - 1);
        } else {
            p.getInventory().setItemInMainHand(null);
        }
        if (getPlayer(p).getData().getSkillLines().get("rift").getAdaptations().get("rift-resist")!= null
                && getPlayer(p).getData().getSkillLines().get("rift").getAdaptations().get("rift-resist").getLevel() > 0) {
            RiftResist.riftResistStackAdd(p, 150, 3);
        }


            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 10, true, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 85, 0, true, false, false));
        p.playSound(l, Sound.BLOCK_LODESTONE_PLACE, 100f, 0.1f);
        p.playSound(l, Sound.BLOCK_BELL_RESONATE, 100f, 0.1f);
        J.a(() -> {
            double d = 2;
            double pcd = 1000;
            double y = 0.1;
            while(pcd > 0) {
                for(int i = 0; i < 16; i++) {
                    p.getWorld().spawnParticle(Particle.ASH, p.getLocation().clone()
                        .add(Vector.getRandom().subtract(Vector.getRandom()).setY(y).normalize().multiply(d)), 1, 0, 0, 0, 0);
                }

                pcd = pcd - 20;
                d = d - 0.04;
                y = y * 1.07;
                J.sleep(80);
            }
            vfxLevelUp(p);
            p.getLocation().getWorld().playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 5.35f, 0.1f);

            J.s(() -> p.teleport(l, PlayerTeleportEvent.TeleportCause.PLUGIN));
        });

    }

    private boolean isBound(ItemStack stack) {
        if (stack.getType().equals(Material.ENDER_EYE) && BoundEyeOfEnder.getLocation(stack) != null) {
            return true;
        } else {
            return false;
        }
    }


    private void unlinkEye(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();

        if(hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
        } else {
            p.getInventory().setItemInMainHand(null);
        }

        ItemStack eye = new ItemStack(Material.ENDER_EYE);
        p.getInventory().addItem(eye).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
    }

    private void linkEye(Player p, Location location) {
        vfxSingleCuboidOutline(location.getBlock(), location.add(0,1,0).getBlock(), Particle.REVERSE_PORTAL);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_EYE_DEATH, 0.50f, 0.22f);
        ItemStack hand = p.getInventory().getItemInMainHand();

        if(hand.getAmount() == 1) {
            BoundEyeOfEnder.setData(hand, location);
        } else {
            hand.setAmount(hand.getAmount() - 1);
            ItemStack eye = BoundEyeOfEnder.withData(location);
            p.getInventory().addItem(eye).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
        }
    }


    @Override
    public void onTick() {
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
    }
}