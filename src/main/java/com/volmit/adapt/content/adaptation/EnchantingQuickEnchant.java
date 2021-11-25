package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.KMap;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

public class EnchantingQuickEnchant extends SimpleAdaptation<EnchantingQuickEnchant.Config> {
    private final KList<Integer> holds = new KList<>();

    public EnchantingQuickEnchant() {
        super("quick-enchant");
        registerConfiguration(Config.class);
        setDescription("Enchant items by clicking enchant books directly on them.");
        setIcon(Material.WRITABLE_BOOK);
        setBaseCost(6);
        setMaxLevel(7);
        setInterval(5000);
        setInitialCost(8);
        setCostFactor(1.355);
    }

    private int getTotalLevelCount(int level) {
        return level + (level > 4 ? level / 3 : 0);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + getTotalLevelCount(level) + C.GRAY + " Max Combined Levels");
    }

    @EventHandler
    public void on(InventoryClickEvent e) {
        if(e.getWhoClicked() instanceof Player p
            && hasAdaptation(p)
            && e.getAction().equals(InventoryAction.SWAP_WITH_CURSOR)
            && e.getClick().equals(ClickType.LEFT)
            && (e.getSlotType().equals(InventoryType.SlotType.CONTAINER)
            || e.getSlotType().equals(InventoryType.SlotType.ARMOR)
            || e.getSlotType().equals(InventoryType.SlotType.QUICKBAR))
            && e.getCursor() != null
            && e.getCurrentItem() != null
            && e.getCursor().getType().equals(Material.ENCHANTED_BOOK)
            && e.getCursor().getItemMeta() != null
            && e.getCursor().getItemMeta() instanceof EnchantmentStorageMeta eb
            && e.getCurrentItem().getItemMeta() != null
            && e.getCurrentItem().getAmount() == 1
            && e.getCursor().getAmount() == 1) {
            ItemStack item = e.getCurrentItem();
            ItemStack book = e.getCursor();
            KMap<Enchantment, Integer> itemEnchants = new KMap<>(item.getType().equals(Material.ENCHANTED_BOOK)
                ? ((EnchantmentStorageMeta) item.getItemMeta()).getStoredEnchants()
                : item.getEnchantments());
            KMap<Enchantment, Integer> bookEnchants = new KMap<>(eb.getStoredEnchants());
            KMap<Enchantment, Integer> newEnchants = itemEnchants.copy();
            KMap<Enchantment, Integer> addEnchants = new KMap<>();
            int power = itemEnchants.values().stream().mapToInt(i -> i).sum();

            if(bookEnchants.isEmpty()) {
                return;
            }

            for(Enchantment i : bookEnchants.k()) {
                if(itemEnchants.containsKey(i)) {
                    continue;
                }

                power += bookEnchants.get(i);
                newEnchants.put(i, bookEnchants.get(i));
                addEnchants.put(i, bookEnchants.get(i));
                bookEnchants.remove(i);
            }

            if(power > getTotalLevelCount(getLevel(p))) {
                Adapt.actionbar(p, C.RED + "Cannot Enchant an item with more than " + getTotalLevelCount(getLevel(p)) + " power");
                p.playSound(p.getLocation(), Sound.BLOCK_CONDUIT_DEACTIVATE, 0.5f, 1.7f);
                return;
            }

            if(!itemEnchants.equals(newEnchants)) {
                ItemMeta im = item.getItemMeta();

                if(im instanceof EnchantmentStorageMeta sm) {
                    sm.getStoredEnchants().keySet().forEach(sm::removeStoredEnchant);
                    newEnchants.forEach((ec, l) -> sm.addStoredEnchant(ec, l, true));
                    p.sendMessage("---");
                    sm.getStoredEnchants().forEach((k, v) -> p.sendMessage(k.getKey().getKey() + " " + v));
                } else {
                    im.getEnchants().keySet().forEach(im::removeEnchant);
                    newEnchants.forEach((ec, l) -> im.addEnchant(ec, l, true));
                }

                item.setItemMeta(im);
                e.setCurrentItem(item);
                e.setCancelled(true);
                p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.7f);
                p.playSound(p.getLocation(), Sound.BLOCK_DEEPSLATE_TILES_BREAK, 0.5f, 0.7f);
                getSkill().xp(p, 320 * addEnchants.values().stream().mapToInt((i) -> i).sum());

                if(bookEnchants.isEmpty()) {
                    e.setCursor(null);
                } else if(!eb.getStoredEnchants().equals(bookEnchants)) {
                    eb.getStoredEnchants().keySet().forEach(eb::removeStoredEnchant);
                    bookEnchants.forEach((ec, l) -> eb.addStoredEnchant(ec, l, true));
                    book.setItemMeta(eb);
                    e.setCursor(book);
                }
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
