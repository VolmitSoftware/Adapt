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

package art.arcane.adapt.api.item;

import art.arcane.adapt.util.common.format.C;
import art.arcane.volmlib.util.format.Form;
import lombok.NoArgsConstructor;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public abstract class PotionItem implements DataItem<PotionItem.Data> {
    @Override
    public Class<Data> getType() {
        return Data.class;
    }

    @Override
    public void applyLore(Data data, List<String> lore) {
        lore.add(C.GREEN + "Grants " + data.getType().getName() + " " + Form.toRoman(data.getPower() + 1));
    }

    @Override
    public void applyMeta(Data data, ItemMeta meta) {

    }

    @lombok.Data
    @NoArgsConstructor
    public static class Data {
        private PotionEffectType type;
        private int power;
    }
}
