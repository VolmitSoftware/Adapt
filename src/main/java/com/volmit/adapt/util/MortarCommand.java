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

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Sound;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a pawn command
 *
 * @author cyberpwn
 */
public abstract class MortarCommand implements ICommand {
    @Getter
    private final List<MortarCommand> children;
    private final List<String> nodes;
    private final List<String> requiredPermissions;
    private final String node;
    @Setter
    @Getter
    private String category;
    @Getter
    private String description;

    /**
     * Override this with a super constructor as most commands shouldn't change
     * these parameters
     *
     * @param node  the node (primary node) i.e. volume
     * @param nodes the aliases. i.e. v, vol, bile
     */
    public MortarCommand(String node, String... nodes) {
        category = "";
        this.node = node;
        this.nodes = new ArrayList<>();
        this.nodes.add(nodes);
        requiredPermissions = new ArrayList<>();
        children = buildChildren();
        description = "No Description";
    }

    @Override
    public List<String> handleTab(MortarSender sender, String[] args) {
        List<String> v = new ArrayList<>();
        if (args.length == 0) {
            for (MortarCommand i : getChildren()) {
                v.add(i.getNode());
            }
        }

        addTabOptions(sender, args, v);

        if (v.isEmpty()) {
            return null;
        }

        if (sender.isPlayer()) {
            SoundPlayer spw = SoundPlayer.of(sender.player().getWorld());
            spw.play(sender.player().getLocation(), Sound.ENTITY_ITEM_FRAME_ROTATE_ITEM, 0.25f, 1.7f);
        }

        return v;
    }

    public abstract void addTabOptions(MortarSender sender, String[] args, List<String> list);

    public void printHelp(MortarSender sender) {
        boolean b = false;

        for (MortarCommand i : getChildren()) {
            for (String j : i.getRequiredPermissions()) {
                sender.hasPermission(j);
            }

            b = true;

            sender.sendMessage(C.GREEN + i.getNode() + " " + C.WHITE + i.getArgsUsage() + C.GRAY + " - " + i.getDescription());
        }

        if (!b) {
            sender.sendMessage("There are either no sub-commands or you do not have permission to use them.");
        }

        if (sender.isPlayer()) {
            SoundPlayer spw = SoundPlayer.of(sender.player().getWorld());
            spw.play(sender.player().getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.28f, 1.4f);
            spw.play(sender.player().getLocation(), Sound.ITEM_AXE_STRIP, 0.35f, 1.7f);
        }
    }

    protected abstract String getArgsUsage();

    protected void setDescription(String description) {
        this.description = description;
    }

    protected void requiresPermission(MortarPermission node) {
        if (node == null) {
            return;
        }

        requiresPermission(node.toString());
    }

    protected void requiresPermission(String node) {
        if (node == null) {
            return;
        }

        requiredPermissions.add(node);
    }

    public void rejectAny(int past, MortarSender sender, String[] a) {
        if (a.length > past) {
            int p = past;

            StringBuilder m = new StringBuilder();

            for (String i : a) {
                p--;
                if (p < 0) {
                    m.append(i).append(", ");
                }
            }

            if (!m.toString().trim().isEmpty()) {
                sender.sendMessage("Parameters Ignored: " + m);
            }
        }
    }

    @Override
    public String getNode() {
        return node;
    }

    @Override
    public List<String> getNodes() {
        return nodes;
    }

    @Override
    public List<String> getAllNodes() {
        return getNodes().copy().qadd(getNode());
    }

    @Override
    public void addNode(String node) {
        getNodes().add(node);
    }

    private List<MortarCommand> buildChildren() {
        List<MortarCommand> p = new ArrayList<>();

        for (Field i : getClass().getDeclaredFields()) {
            if (i.isAnnotationPresent(Command.class)) {
                try {
                    i.setAccessible(true);
                    MortarCommand pc = (MortarCommand) i.getType().getConstructor().newInstance();
                    Command c = i.getAnnotation(Command.class);

                    if (!c.value().trim().isEmpty()) {
                        pc.setCategory(c.value().trim());
                    } else {
                        pc.setCategory(getCategory());
                    }

                    p.add(pc);
                } catch (IllegalArgumentException | IllegalAccessException | InstantiationException |
                         InvocationTargetException | NoSuchMethodException | SecurityException e) {
                    e.printStackTrace();
                }
            }
        }

        return p;
    }

    @Override
    public List<String> getRequiredPermissions() {
        return requiredPermissions;
    }

}
