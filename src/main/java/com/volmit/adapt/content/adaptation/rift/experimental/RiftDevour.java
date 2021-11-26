package com.volmit.adapt.content.adaptation.rift.experimental;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.content.Enchantments;
import com.volmit.adapt.content.item.BoundDevourCandle;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class RiftDevour extends SimpleAdaptation<RiftDevour.Config> {
    public RiftDevour() {
        super("rift-devour");
        registerConfiguration(Config.class);
        setDescription("Let the void consume");
        setIcon(Material.BLACK_CANDLE);
        setBaseCost(0);
        setCostFactor(0);
        setMaxLevel(1);
        setInitialCost(30);
        setInterval(50);
        registerRecipe(AdaptRecipe.shapeless()
                .key("rift-devour")
                .ingredient(Material.BLACK_CANDLE)
                .ingredient(Material.NETHERITE_SCRAP)
                .result(BoundDevourCandle.io.withData(new BoundDevourCandle.Data(null)))
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.ITALIC + "Consumes items from chest to heal you");
        v.addLore(C.ITALIC + "Needs to be in offhand, active and linked to work");
    }


    @EventHandler
    public void on(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        Block block = e.getClickedBlock();


        if (!hasAdaptation(p) || (!hand.getType().equals(Material.BLACK_CANDLE) && !isBound(hand))) {
            return;
        }
        e.setCancelled(true);

        switch (e.getAction()) {
            case LEFT_CLICK_BLOCK, RIGHT_CLICK_BLOCK -> {
                if (isStorage(block.getBlockData())) { // Ensure its a container
                    if (p.isSneaking()) { // Binding (Sneak Container)
                        linkCandle(p, block);
                    } else if (!p.isSneaking()) {
                        toggleCandle(p);
                    }
                } else if (!isStorage(block.getBlockData())) {
                    if (p.isSneaking()) { //(Sneak NOT Container)
                        p.sendMessage(C.LIGHT_PURPLE + "That's not a container i can consume from");
                    } else if (!p.isSneaking() && isBound(hand)) {
                        toggleCandle(p);
                    }
                }
                e.setCancelled(true);

            }
            case RIGHT_CLICK_AIR, LEFT_CLICK_AIR -> {
                e.setCancelled(true);
            }
        }
    }


    private void toggleCandle(Player p) {
        ItemMeta hand = p.getInventory().getItemInMainHand().getItemMeta();

        if (hand.getEnchants().get(Enchantments.BOUND) == null) {
            hand.addEnchant(Enchantments.BOUND, 0, true);
            p.sendMessage("AGGRANDIZED THE HUNGER");//move
            p.playSound(p.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 100f, 0.1f);
            p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_BURN, 100f, 0.1f);
        } else {
            hand.removeEnchant(Enchantments.BOUND);
            p.sendMessage("SILENCED THE HUNGER");//move
            p.playSound(p.getLocation(), Sound.BLOCK_CANDLE_EXTINGUISH, 100f, 0.1f);
            p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_DEATH, 100f, 0.1f);
        }

    }

    private boolean isBound(ItemStack stack) {
        return stack.getType().equals(Material.BLACK_CANDLE) && BoundDevourCandle.getBlock(stack) != null;
    }


    private void linkCandle(Player p, Block block) {
        ItemStack hand = p.getInventory().getItemInMainHand();

        if (hand.getAmount() == 1) {
            BoundDevourCandle.setData(hand, block);
        } else {
            hand.setAmount(hand.getAmount() - 1);
            ItemStack candle = BoundDevourCandle.withData(block);
            p.getInventory().addItem(candle).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
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