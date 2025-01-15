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

package com.volmit.adapt.api.recipe;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

@AllArgsConstructor
@Data
public class MaterialChar {
    private char character;
    private RecipeChoice choice;

    public MaterialChar(char character, Tag<Material> tag) {
        this.character = character;
        this.choice = new RecipeChoice.MaterialChoice(tag);
    }

    public MaterialChar(char character, Material... material) {
        this.character = character;
        this.choice = new RecipeChoice.MaterialChoice(material);
    }

    public MaterialChar(char character, ItemStack... itemStack) {
        this.character = character;
        this.choice = new RecipeChoice.ExactChoice(itemStack);
    }
}
