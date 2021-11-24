package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.content.adaptation.experimental.RiftDoor;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.KMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.SmithingRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

public class CraftingDeconstruction extends SimpleAdaptation<CraftingDeconstruction.Config> {
    private final KList<Integer> holds = new KList<>();

    public CraftingDeconstruction() {
        super("deconstruction");
        setDescription("Deconstruct blocks & items into salvageable base components");
        setIcon(Material.SHEARS);
        setBaseCost(9);
        setMaxLevel(1);
        setInterval(5000);
        setInitialCost(8);
        setCostFactor(1.355);
        registerConfiguration(Config.class);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "Place an any item + shears");
        v.addLore(C.GREEN + "in a smithing table to deconstruct.");
    }

    public ItemStack getDeconstructionOffering(ItemStack forStuff)
    {
        if(forStuff == null)
        {
            return null;
        }

        int maxPow = 0;
        Recipe sr = null;

        for(Recipe i : Bukkit.getRecipesFor(forStuff))
        {
            if(i instanceof ShapelessRecipe r)
            {
                int mp = r.getIngredientList().stream().mapToInt(f -> f.getAmount()).sum();

                if(mp > maxPow)
                {
                    sr = i;
                    maxPow = mp;
                }
            }

            else if(i instanceof ShapedRecipe r)
            {
                int mp = r.getIngredientMap().values().stream().mapToInt(f -> f == null ? 0 : f.getAmount()).sum();

                if(mp > maxPow)
                {
                    sr = i;
                    maxPow = mp;
                }
            }
        }

        if(sr == null)
        {
            return null;
        }

        int v = 0;
        int outa = 1;
        ItemStack sel = null;

        if(sr instanceof ShapelessRecipe r)
        {
            for(ItemStack i : r.getIngredientList())
            {
                if(i.getAmount() * forStuff.getAmount() > v)
                {
                    v = i.getAmount() * forStuff.getAmount();
                    sel = i;
                    outa = r.getResult().getAmount();
                }
            }
        }

        else {
            ShapedRecipe r = (ShapedRecipe) sr;
            KList<ItemStack> ings = new KList<>();

            r.getIngredientMap().forEach((k,vx) -> {
                if(vx == null)
                {
                    return;
                }

                for(ItemStack i : ings)
                {
                    if(vx.getType().equals(i.getType()))
                    {
                        i.setAmount(i.getAmount() + 1);
                        return;
                    }
                }

                ings.add(vx);
            });

            for(ItemStack i : ings)
            {
                if(i != null && i.getAmount() * forStuff.getAmount() > v)
                {
                    v = i.getAmount() * forStuff.getAmount();
                    sel = i;
                    outa = r.getResult().getAmount();
                }
            }
        }

        if(sel != null && sel.getAmount() * forStuff.getAmount() > 1)
        {
            sel = sel.clone();

            int a = ((sel.getAmount() * forStuff.getAmount()) / outa) / 2;

            if(a > sel.getMaxStackSize())
            {
                return null;
            }

            sel.setAmount(a);

            if(getValue(sel) >= getValue(forStuff))
            {
                return null;
            }

            return sel;
        }

        return null;
    }

    public int getShearDamage(ItemStack forStuff)
    {
        return forStuff.getAmount() * 8;
    }

    @EventHandler
    public void on(InventoryClickEvent e) {
        if(e.getView().getTopInventory().getType().equals(InventoryType.SMITHING))
        {
            SmithingInventory s = (SmithingInventory) e.getView().getTopInventory();
            J.s(() -> {
                if(s.getItem(1) != null && s.getItem(1).getType().equals(Material.SHEARS) && s.getItem(0) != null)
                {
                    s.setResult(getDeconstructionOffering(s.getItem(0)));
                }
            });
        }

        if(e.getClickedInventory().getType().equals(InventoryType.SMITHING))
        {
            SmithingInventory s = (SmithingInventory) e.getClickedInventory();
            if(e.getSlotType().equals(InventoryType.SlotType.CRAFTING))
            {
                J.s(() -> {
                    if(s.getItem(1) != null && s.getItem(1).getType().equals(Material.SHEARS) && s.getItem(0) != null)
                    {
                        s.setResult(getDeconstructionOffering(s.getItem(0)));
                    }
                });
            }

            else if(e.getSlotType().equals(InventoryType.SlotType.RESULT))
            {
                if(s.getItem(1) != null && s.getItem(1).getType().equals(Material.SHEARS) && s.getItem(0) != null)
                {
                    ItemStack offering = getDeconstructionOffering(s.getItem(0));

                    if(offering != null)
                    {
                        s.setItem(1, damage(s.getItem(1), s.getItem(0).getAmount()));
                        e.setCursor(offering);
                        e.getClickedInventory().setItem(0, null);
                        e.getWhoClicked().getWorld().playSound(e.getClickedInventory().getLocation(), Sound.BLOCK_BASALT_BREAK, 1F, 0.2f);
                        e.getWhoClicked().getWorld().playSound(e.getClickedInventory().getLocation(), Sound.BLOCK_BEEHIVE_SHEAR, 1F, 0.7f);
                        getSkill().xp((Player)e.getWhoClicked(), getValue(offering));
                    }
                }
            }
        }
    }

    private void updateOffering(Inventory inventory) {
        SmithingInventory s = (SmithingInventory) inventory;

        if(s.getItem(1) != null && s.getItem(1).getType().equals(Material.SHEARS) && s.getItem(0) != null)
        {
            ItemStack offering = getDeconstructionOffering(s.getItem(0));
            s.setResult(offering);
        }
    }

    @Override
    public void onTick() {

    }

    protected static class Config{}
}
