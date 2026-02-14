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

package art.arcane.adapt.content.item;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.item.DataItem;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.reflect.registries.ItemFlags;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

import java.util.List;

@AllArgsConstructor
@Data
public class ChronoTimeBombItem implements DataItem<ChronoTimeBombItem.Data> {
    public static ChronoTimeBombItem io = new ChronoTimeBombItem();

    public static boolean isBindableItem(ItemStack stack) {
        if (stack == null || stack.getItemMeta() == null) {
            return false;
        }

        if (stack.getType() == Material.LINGERING_POTION) {
            return io.hasData(stack);
        }

        if (stack.getType() == Material.CLOCK) {
            if (Adapt.instance == null) {
                return false;
            }

            NamespacedKey key = new NamespacedKey(Adapt.instance, Data.class.getCanonicalName().hashCode() + "");
            return stack.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING);
        }

        return false;
    }

    public static ItemStack withData() {
        return io.withData(new Data(System.currentTimeMillis()));
    }

    @Override
    public Material getMaterial() {
        return Material.LINGERING_POTION;
    }

    @Override
    public Class<Data> getType() {
        return Data.class;
    }

    @Override
    public void applyLore(Data data, List<String> lore) {
        lore.add(C.WHITE + Localizer.dLocalize("items.chrono_time_bomb.name"));
        lore.add(C.GRAY + Localizer.dLocalize("items.chrono_time_bomb.usage1"));
    }

    @Override
    public void applyMeta(Data data, ItemMeta meta) {
        if (meta instanceof PotionMeta potionMeta) {
            potionMeta.setBasePotionType(PotionType.WEAKNESS);
            meta = potionMeta;
        }

        meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlags.HIDE_POTION_EFFECTS);
        meta.setDisplayName(Localizer.dLocalize("items.chrono_time_bomb.name"));
    }

    @AllArgsConstructor
    @lombok.Data
    public static class Data {
        private long created;
    }
}
