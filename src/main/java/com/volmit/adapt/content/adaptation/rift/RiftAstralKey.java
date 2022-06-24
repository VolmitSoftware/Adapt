package com.volmit.adapt.content.adaptation.rift;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.content.item.BoundEyeOfEnder;
import com.volmit.adapt.content.item.BoundRiftKey;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;

public class RiftAstralKey extends SimpleAdaptation<RiftAstralKey.Config> {


    //TODO: ADD RECIPE OR CONSUMPTION COST
    public RiftAstralKey() {
        super("rift-key");
        setDescription("Its a cosmic car key, but for doors!");
        setIcon(Material.TRIPWIRE_HOOK);
        setBaseCost(0);
        setCostFactor(0);
        setMaxLevel(1);
        setInitialCost(25);
        setInterval(100);
        registerRecipe(AdaptRecipe.shapeless()
                .key("rift-door")
                .ingredient(Material.TRIPWIRE_HOOK)
                .result(BoundRiftKey.io.withData(new BoundRiftKey.Data(null)))
                .build());
        registerConfiguration(Config.class);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "BROKEN DONT USE THIS"); // remove later
    }

    @EventHandler
    public void on(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        ItemMeta handMeta = hand.getItemMeta();
        if(handMeta ==null) {return;}
        handMeta.setLore(handMeta.getLore() == null ? new ArrayList<>() : handMeta.getLore());
        if (!hasAdaptation(p) || !hand.hasItemMeta() || !handMeta.getLore().get(3).equals("Astral Key")) {
            return;
        }
        e.setCancelled(true);



        Action action = e.getAction();
        Block block = e.getClickedBlock();

        switch (e.getAction()) {
            case LEFT_CLICK_BLOCK -> {
                if (block != null && action == Action.LEFT_CLICK_BLOCK && e.getClickedBlock().getType() == Material.OAK_DOOR) {

                    if (p.isSneaking() && !handMeta.getLore().get(1).equals("null")) {
                        // First pos bound

                    } else if (p.isSneaking() && !handMeta.getLore().get(1).equals("null") && !handMeta.getLore().get(2).equals("null")) {
                        //Both Set

                    } else if (p.isSneaking() && handMeta.getLore().get(1).equals("null")) {
                        //No pos bound

                    }
                }

            }
            case RIGHT_CLICK_BLOCK -> {

            }

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