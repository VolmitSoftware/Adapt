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

package com.volmit.adapt.util;

import com.volmit.adapt.util.collection.KList;
import com.volmit.adapt.util.reflect.registries.Enchantments;
import com.volmit.adapt.util.reflect.registries.ItemFlags;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class UIElement implements Element {
    private final String id;
    private final KList<String> lore;
    private MaterialBlock material;
    private CustomModel model;
    private boolean enchanted;
    private String name;
    private double progress;
    private boolean bg;
    private Callback<Element> eLeft;
    private Callback<Element> eRight;
    private Callback<Element> eShiftLeft;
    private Callback<Element> eShiftRight;
    private Callback<Element> eDraggedInto;
    private Callback<Element> eOtherDraggedInto;
    private int count;

    public UIElement(String id) {
        this.id = id;
        lore = new KList<>();
        enchanted = false;
        count = 1;
        material = new MaterialBlock(Material.AIR);
    }

    @Override
    public MaterialBlock getMaterial() {
        return material;
    }

    @Override
    public UIElement setMaterial(MaterialBlock material) {
        this.material = material;
        return this;
    }

    public Double clip(double value, double min, double max) {
        return Double.valueOf(Math.min(max, Math.max(min, value)));
    }

    @Override
    public boolean isEnchanted() {
        return enchanted;
    }

    @Override
    public UIElement setEnchanted(boolean enchanted) {
        this.enchanted = enchanted;
        return this;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public UIElement setName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public CustomModel getModel() {
        return model;
    }

    @Override
    public UIElement setModel(CustomModel model) {
        this.model = model;
        return this;
    }

    @Override
    public KList<String> getLore() {
        return lore;
    }

    @Override
    public UIElement onLeftClick(Callback<Element> clicked) {
        eLeft = clicked;
        return this;
    }

    @Override
    public UIElement onRightClick(Callback<Element> clicked) {
        eRight = clicked;
        return this;
    }

    @Override
    public UIElement onShiftLeftClick(Callback<Element> clicked) {
        eShiftLeft = clicked;
        return this;
    }

    @Override
    public UIElement onShiftRightClick(Callback<Element> clicked) {
        eShiftRight = clicked;
        return this;
    }

    @Override
    public UIElement onDraggedInto(Callback<Element> into) {
        eDraggedInto = into;
        return this;
    }

    @Override
    public UIElement onOtherDraggedInto(Callback<Element> other) {
        eOtherDraggedInto = other;
        return this;
    }

    @Override
    public Element call(ElementEvent event, Element context) {
        try {
            switch (event) {
                case DRAG_INTO:
                    eDraggedInto.run(context);
                    return this;
                case LEFT:
                    eLeft.run(context);
                    return this;
                case OTHER_DRAG_INTO:
                    eOtherDraggedInto.run(context);
                    return this;
                case RIGHT:
                    eRight.run(context);
                    return this;
                case SHIFT_LEFT:
                    eShiftLeft.run(context);
                    return this;
                case SHIFT_RIGHT:
                    eShiftRight.run(context);
                    return this;
            }
        } catch (NullPointerException e) {

        } catch (Throwable e) {
            e.printStackTrace();
        }

        return this;
    }

    @Override
    public Element addLore(String loreLine) {
        if (loreLine != null) {
            for (String line : loreLine.split("\\R")) {
                if (line.isEmpty()) getLore().add("");
                else wrapWordsWithFormatting(line, 55).forEach(getLore()::add);
            }
        }
        return this;
    }

    public List<String> wrapWordsWithFormatting(String f, int l) {
        List<String> result = new ArrayList<>();
        String last = null;
        for (String i : Form.wrapWords(f, l).split("\\R")) {
            if (last != null) result.add(C.getLastColors(last) + i);
            else result.add(i);
            last = i;
        }
        return result;
    }

    @Override
    public Element setBackground(boolean bg) {
        this.bg = bg;
        return this;
    }

    @Override
    public boolean isBackgrond() {
        return bg;
    }

    @Override
    public Element setCount(int c) {
        count = clip(c, 1, 64).intValue();
        return this;
    }

    @Override
    public int getCount() {
        return count;
    }

    @SuppressWarnings("deprecation")
    @Override
    public ItemStack computeItemStack() {
        try {
            ItemStack is = getModel() != null ? getModel().toItemStack() :
                    new ItemStack(getMaterial().getMaterial());
            is.setAmount(getCount());
            is.setDurability(getEffectiveDurability());

            ItemMeta im = is.getItemMeta();
            if (im == null) return is;
            im.setDisplayName(getName());
            im.setLore(getLore().copy());
            if (isEnchanted()) {
                im.addEnchant(Enchantments.DURABILITY, 1, true);
            }
            // Hide all attributes and enchants and stuff!
            im.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            im.addItemFlags(ItemFlags.HIDE_POTION_EFFECTS);
            im.addItemFlags(ItemFlag.HIDE_DYE);
            im.addItemFlags(ItemFlag.HIDE_DESTROYS);
            im.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

            is.setItemMeta(im);
            return is;
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public Element setProgress(double progress) {
        this.progress = clip(progress, 0D, 1D);
        return this;
    }

    @Override
    public double getProgress() {
        return progress;
    }

    @Override
    public short getEffectiveDurability() {
        if (progress == 1D) {
            return 0;
        }

        if (getMaterial().getMaterial().getMaxDurability() == 0) {
            return 0;
        } else {
            int prog = (int) ((double) getMaterial().getMaterial().getMaxDurability() * (1D - getProgress()));
            return clip(prog, 1, (getMaterial().getMaterial().getMaxDurability() - 1)).shortValue();
        }
    }
}
