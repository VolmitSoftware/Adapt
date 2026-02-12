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

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.version.Version;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class UIWindow implements Window, Listener {
    private final Player viewer;
    private final Map<Integer, Element> elements;
    private WindowDecorator decorator;
    private Callback<Window> eClose;
    private WindowResolution resolution;
    private String title;
    private boolean visible;
    private int viewportPosition;
    private int viewportSize;
    private int highestRow;
    private String tag;
    private Inventory inventory;
    private int clickcheck;
    private boolean doubleclicked;

    public UIWindow(Player viewer) {
        clickcheck = 0;
        doubleclicked = false;
        this.viewer = viewer;
        this.elements = new HashMap<>();
        setTitle("");
        setDecorator(new UIVoidDecorator());
        setResolution(WindowResolution.W9_H6);
        setViewportHeight(getResolution().getMaxHeight());
        setViewportPosition(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(InventoryClickEvent e) {
        if (!e.getWhoClicked().equals(viewer)) {
            return;
        }

        if (!isVisible()) {
            return;
        }

        if (!(e.getInventory().getHolder() instanceof Holder)) {
            return;
        }

        if (e.getClickedInventory() == null) {
            return;
        }

        if (!e.getView().getType().equals(getResolution().getType())) {
            return;
        }

        if (e.getClickedInventory().getType().equals(getResolution().getType())) {
            Element element = getElement(getLayoutPosition(e.getSlot()), getLayoutRow(e.getSlot()));
            if (element != null) {
                flashViewportSlot(e.getSlot());
            }

            switch (e.getAction()) {
                case CLONE_STACK:
                case COLLECT_TO_CURSOR:
                case DROP_ALL_CURSOR:
                case DROP_ALL_SLOT:
                case DROP_ONE_CURSOR:
                case DROP_ONE_SLOT:
                case HOTBAR_MOVE_AND_READD:
                case HOTBAR_SWAP:
                case MOVE_TO_OTHER_INVENTORY:
                case NOTHING:
                case PICKUP_ALL:
                case PICKUP_HALF:
                case PICKUP_ONE:
                case PICKUP_SOME:
                case PLACE_ALL:
                case PLACE_ONE:
                case PLACE_SOME:
                case SWAP_WITH_CURSOR:
                case UNKNOWN:
                    break;
            }

            switch (e.getClick()) {
                case DOUBLE_CLICK -> doubleclicked = true;
                case LEFT -> {
                    clickcheck++;
                    if (clickcheck == 1) {
                        J.s(() ->
                        {
                            if (clickcheck == 1) {
                                clickcheck = 0;

                                if (element != null) {
                                    element.call(ElementEvent.LEFT, element);
                                }
                            }
                        });
                    } else if (clickcheck == 2) {
                        J.s(() ->
                        {
                            if (doubleclicked) {
                                doubleclicked = false;
                            } else {
                                scroll(1);
                            }

                            clickcheck = 0;
                        });
                    }
                }
                case RIGHT -> {
                    if (element != null) {
                        element.call(ElementEvent.RIGHT, element);
                    } else {
                        scroll(-1);
                    }
                }
                case SHIFT_LEFT -> {
                    if (element != null) {
                        element.call(ElementEvent.SHIFT_LEFT, element);
                    }
                }
                case SHIFT_RIGHT -> {
                    if (element != null) {
                        element.call(ElementEvent.SHIFT_RIGHT, element);
                    }
                }
                default -> {
                }
            }
        }

        e.setCancelled(true);

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(InventoryCloseEvent e) {

        if (!e.getPlayer().equals(viewer)) {
            return;
        }

        if (!(e.getInventory().getHolder() instanceof Holder)) {
            return;
        }

        if (isVisible()) {
            close();
            callClosed();
        }
    }

    @Override
    public WindowDecorator getDecorator() {
        return decorator;
    }

    @Override
    public UIWindow setDecorator(WindowDecorator decorator) {
        this.decorator = decorator;
        return this;
    }

    @Override
    public UIWindow close() {
        setVisible(false);
        return this;
    }

    @Override
    public UIWindow open() {
        setVisible(true);
        return this;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public UIWindow setVisible(boolean visible) {
        if (isVisible() == visible) {
            return this;
        }

        if (visible) {
            Bukkit.getPluginManager().registerEvents(this, Adapt.instance);

            var openInventory = viewer.getOpenInventory().getTopInventory();
            if (openInventory.getHolder() instanceof Holder holder) {
                inventory = getCurrentInventory(this, holder);
            } else {
                inventory = createInventory(this);
            }

            this.visible = true;
            updateInventory();
        } else {
            this.visible = false;
            HandlerList.unregisterAll(this);
            viewer.closeInventory();
        }
        return this;
    }

    @Override
    public int getViewportPosition() {
        return viewportPosition;
    }

    @Override
    public UIWindow setViewportPosition(int viewportPosition) {
        this.viewportPosition = viewportPosition;
        scroll(0);
        updateInventory();

        return this;
    }

    @Override
    public int getMaxViewportPosition() {
        return Math.max(0, highestRow - getViewportHeight());
    }

    @Override
    public UIWindow scroll(int direction) {
        viewportPosition = (int) clip(viewportPosition + direction, 0, getMaxViewportPosition()).doubleValue();
        updateInventory();

        return this;
    }

    @Override
    public int getViewportHeight() {
        return viewportSize;
    }

    @Override
    public UIWindow setViewportHeight(int height) {
        viewportSize = (int) clip(height, 1, getResolution().getMaxHeight()).doubleValue();

        if (isVisible()) {
            reopen();
        }

        return this;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public UIWindow setTitle(String title) {
        this.title = title;

        if (isVisible()) {
            reopen();
        }

        return this;
    }

    @Override
    public String getTag() {
        return tag;
    }

    @Override
    public void setTag(String s) {
        tag = s;
    }

    @Override
    public UIWindow setElement(int position, int row, Element e) {
        if (row > highestRow) {
            highestRow = row;
        }

        elements.put(getRealPosition((int) clip(position, -getResolution().getMaxWidthOffset(), getResolution().getMaxWidthOffset()).doubleValue(), row), e);
        updateInventory();
        return this;
    }

    @Override
    public Element getElement(int position, int row) {
        return elements.get(getRealPosition((int) clip(position, -getResolution().getMaxWidthOffset(), getResolution().getMaxWidthOffset()).doubleValue(), row));
    }

    @Override
    public Player getViewer() {
        return viewer;
    }

    @Override
    public UIWindow onClosed(Callback<Window> window) {
        eClose = window;
        return this;
    }

    @Override
    public int getViewportSlots() {
        return getViewportHeight() * getResolution().getWidth();
    }

    @Override
    public int getLayoutRow(int viewportSlottedPosition) {
        return getRow(getRealLayoutPosition(viewportSlottedPosition));
    }

    @Override
    public int getLayoutPosition(int viewportSlottedPosition) {
        return getPosition(viewportSlottedPosition);
    }

    @Override
    public int getRealLayoutPosition(int viewportSlottedPosition) {
        return getRealPosition(getPosition(viewportSlottedPosition), getRow(viewportSlottedPosition) + getViewportPosition());
    }

    @Override
    public int getRealPosition(int position, int row) {
        return (int) (((row * getResolution().getWidth()) + getResolution().getMaxWidthOffset()) + clip(position, -getResolution().getMaxWidthOffset(), getResolution().getMaxWidthOffset()));
    }

    @Override
    public int getRow(int realPosition) {
        return realPosition / getResolution().getWidth();
    }

    @Override
    public int getPosition(int realPosition) {
        return (realPosition % getResolution().getWidth()) - getResolution().getMaxWidthOffset();
    }

    @Override
    public Window callClosed() {
        if (eClose != null) {
            eClose.run(this);
        }

        return this;
    }

    @Override
    public boolean hasElement(int position, int row) {
        return getElement(position, row) != null;
    }

    @Override
    public WindowResolution getResolution() {
        return resolution;
    }

    public Double clip(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }

    @Override
    public Window setResolution(WindowResolution resolution) {
        close();
        this.resolution = resolution;
        setViewportHeight((int) clip(getViewportHeight(), 1, getResolution().getMaxHeight()).doubleValue());
        return this;
    }

    @Override
    public Window clearElements() {
        highestRow = 0;
        elements.clear();
        updateInventory();
        return this;
    }

    @Override
    public Window updateInventory() {
        if (!isVisible() || inventory == null) {
            return this;
        }

        Inventory top = viewer.getOpenInventory().getTopInventory();
        if (!(top.getHolder() instanceof Holder holder) || holder.getWindow() != this) {
            return this;
        }

        if (inventory != top) {
            inventory = top;
        }

        if (Version.SET_TITLE) {
            try {
                viewer.getOpenInventory().setTitle(getTitle());
            } catch (IllegalArgumentException ignored) {
                return this;
            }
        }

        ItemStack[] is = inventory.getContents();
        Set<ItemStack> isf = new HashSet<>();

        for (int i = 0; i < is.length; i++) {
            ItemStack isc = is[i];
            ItemStack isx = computeItemStack(i);
            int layoutRow = getLayoutRow(i);
            int layoutPosition = getLayoutPosition(i);

            if (isx != null && !hasElement(layoutPosition, layoutRow)) {
                ItemStack gg = isx.clone();
                gg.setAmount(gg.getAmount() + 1);
                isf.add(gg);
            }

            if (((isc == null) != (isx == null)) || isx != null && isc != null && !isc.equals(isx)) {
                inventory.setItem(i, isx);
            }
        }

        return this;
    }

    @Override
    public ItemStack computeItemStack(int viewportSlot) {
        int layoutRow = getLayoutRow(viewportSlot);
        int layoutPosition = getLayoutPosition(viewportSlot);
        Element e = hasElement(layoutPosition, layoutRow) ? getElement(layoutPosition, layoutRow) : getDecorator().onDecorateBackground(this, layoutPosition, layoutRow);

        if (e != null) {
            return e.computeItemStack();
        }

        return null;
    }

    @Override
    public Window reopen() {
        if (Version.SET_TITLE) {
            visible = false;
            HandlerList.unregisterAll(this);
            return open();
        }
        return this.close().open();
    }

    @Setter
    @Getter
    private static class Holder implements InventoryHolder {
        private Inventory inventory;
        private WindowResolution resolution;
        private UIWindow window;

        public void unregister() {
            HandlerList.unregisterAll(window);
            window.visible = false;
        }
    }

    private static Inventory createInventory(UIWindow window) {
        var holder = new Holder();
        Inventory inventory;
        if (window.getResolution().getType().equals(InventoryType.CHEST)) {
            inventory = Bukkit.createInventory(holder, window.getViewportHeight() * 9, window.getTitle());
        } else {
            inventory = Bukkit.createInventory(holder, window.getResolution().getType(), window.getTitle());
        }
        holder.setResolution(window.getResolution());
        holder.setInventory(inventory);
        holder.setWindow(window);

        window.viewer.openInventory(inventory);
        return inventory;
    }

    private static Inventory getCurrentInventory(UIWindow window, Holder holder) {
        if (!Version.SET_TITLE || holder.getResolution() != window.getResolution()) {
            holder.window.close();
            return createInventory(window);
        }

        var openInventory = holder.inventory;
        holder.unregister();
        holder.setWindow(window);

        openInventory.clear();
        return openInventory;
    }

    private void flashViewportSlot(int viewportSlot) {
        if (inventory == null || viewportSlot < 0 || viewportSlot >= inventory.getSize()) {
            return;
        }

        ItemStack flash = new UIElement("flash-" + viewportSlot)
                .setMaterial(new MaterialBlock(Material.PAPER))
                .setName(" ")
                .computeItemStack();
        if (flash == null) {
            return;
        }

        inventory.setItem(viewportSlot, flash);
        J.s(() -> {
            if (!isVisible() || inventory == null || viewportSlot < 0 || viewportSlot >= inventory.getSize()) {
                return;
            }
            inventory.setItem(viewportSlot, computeItemStack(viewportSlot));
        }, 2);
    }
}
