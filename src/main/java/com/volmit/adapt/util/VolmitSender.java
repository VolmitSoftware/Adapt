/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.volmit.adapt.util;


import com.volmit.adapt.Adapt;
import com.volmit.adapt.util.collection.KList;
import com.volmit.adapt.util.decree.DecreeParameter;
import com.volmit.adapt.util.decree.virtual.VirtualDecreeCommand;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a volume sender. A command sender with extra crap in its
 *
 * @author cyberpwn
 */
public class VolmitSender implements CommandSender {
    @Getter
    private static final Map<String, String> helpCache = new HashMap<>();
    /**
     * -- GETTER --
     *  Get the origin sender this object is wrapping
     *
     */
    @Getter
    private final CommandSender s;
    public final boolean useConsoleCustomColors = true;
    public final boolean useCustomColorsIngame = true;
    public final int spinh = -20;
    public final int spins = 7;
    public final int spinb = 8;
    /**
     * -- GETTER --
     *  Get the command tag
     * <p>
     *
     * -- SETTER --
     *  Set a command tag (prefix for sendMessage)
     *
     */
    @Setter
    @Getter
    private String tag;
    @Getter
    @Setter
    private String command;

    /**
     * Wrap a command sender
     *
     * @param s the command sender
     */
    public VolmitSender(CommandSender s) {
        tag = "";
        this.s = s;
    }

    public VolmitSender(CommandSender s, String tag) {
        this.tag = tag;
        this.s = s;
    }

    public static long getTick() {
        return com.volmit.adapt.util.M.ms() / 16;
    }

    public static String pulse(String colorA, String colorB, double speed) {
        return "<gradient:" + colorA + ":" + colorB + ":" + pulse(speed) + ">";
    }

    public static String pulse(double speed) {
        return Form.f(invertSpread((((getTick() * 15D * speed) % 1000D) / 1000D)), 3).replaceAll("\\Q,\\E", ".").replaceAll("\\Q?\\E", "-");
    }

    public static double invertSpread(double v) {
        return ((1D - v) * 2D) - 1D;
    }

    public static <T> List<T> paginate(List<T> all, int linesPerPage, int page, AtomicBoolean hasNext) {
        int totalPages = (int) Math.ceil((double) all.size() / linesPerPage);
        page = page < 0 ? 0 : page >= totalPages ? totalPages - 1 : page;
        hasNext.set(page < totalPages - 1);
        List<T> d = new ArrayList<>();

        for (int i = linesPerPage * page; i < Math.min(all.size(), linesPerPage * (page + 1)); i++) {
            d.add(all.get(i));
        }

        return d;
    }

    /**
     * Is this sender a player?
     *
     * @return true if it is
     */
    public boolean isPlayer() {
        return getS() instanceof Player;
    }

    /**
     * Is this sender a console?
     *
     * @return true if it is
     */
    public boolean isConsole() {
        return getS() instanceof ConsoleCommandSender;
    }

    /**
     * Force cast to player (be sure to check first)
     *
     * @return a casted player
     */
    public Player player() {
        return (Player) getS();
    }

    @Override
    public boolean isPermissionSet(@NotNull String name) {
        return s.isPermissionSet(name);
    }

    @Override
    public boolean isPermissionSet(@NotNull Permission perm) {
        return s.isPermissionSet(perm);
    }

    @Override
    public boolean hasPermission(@NotNull String name) {
        return s.hasPermission(name);
    }

    @Override
    public boolean hasPermission(@NotNull Permission perm) {
        return s.hasPermission(perm);
    }

    @Override
    public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value) {
        return s.addAttachment(plugin, name, value);
    }

    @Override
    public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin) {
        return s.addAttachment(plugin);
    }

    @Override
    public PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value, int ticks) {
        return s.addAttachment(plugin, name, value, ticks);
    }

    @Override
    public PermissionAttachment addAttachment(@NotNull Plugin plugin, int ticks) {
        return s.addAttachment(plugin, ticks);
    }

    @Override
    public void removeAttachment(@NotNull PermissionAttachment attachment) {
        s.removeAttachment(attachment);
    }

    @Override
    public void recalculatePermissions() {
        s.recalculatePermissions();
    }

    @Override
    public @NotNull Set<PermissionAttachmentInfo> getEffectivePermissions() {
        return s.getEffectivePermissions();
    }

    @Override
    public boolean isOp() {
        return s.isOp();
    }

    @Override
    public void setOp(boolean value) {
        s.setOp(value);
    }

    public void hr() {
        s.sendMessage("========================================================");
    }

    public void sendTitle(String title, String subtitle, int i, int s, int o) {
        Adapt.audiences.player(player()).showTitle(Title.title(
                createComponent(title),
                createComponent(subtitle),
                Title.Times.times(Duration.ofMillis(i), Duration.ofMillis(s), Duration.ofMillis(o))));
    }

    public void sendProgress(double percent, String thing) {
        //noinspection IfStatementWithIdenticalBranches
        if (percent < 0) {
            int l = 44;
            int g = (int) (1D * l);
            sendTitle(C.ADAPT + thing + " ", 0, 500, 250);
            sendActionNoProcessing(pulse("#00BFFF", "#003366", 1D) + "<underlined> " + Form.repeat(" ", g) + "<reset>" + Form.repeat(" ", 0));
        } else {
            int l = 44;
            int g = (int) (percent * l);
            sendTitle(C.ADAPT + thing + " " + C.BLUE + "<font:minecraft:uniform>" + Form.pc(percent, 0), 0, 500, 250);
            sendActionNoProcessing(pulse("#00BFFF", "#003366", 1D) + "<underlined> " + Form.repeat(" ", g) + "<reset>" + Form.repeat(" ", l - g));
        }
    }

    public void sendAction(String action) {
        Adapt.audiences.player(player()).sendActionBar(createNoPrefixComponent(action));
    }

    public void sendActionNoProcessing(String action) {
        Adapt.audiences.player(player()).sendActionBar(createNoPrefixComponentNoProcessing(action));
    }

    public void sendTitle(String subtitle, int i, int s, int o) {
        Adapt.audiences.player(player()).showTitle(Title.title(
                createNoPrefixComponent(" "),
                createNoPrefixComponent(subtitle),
                Title.Times.times(Duration.ofMillis(i), Duration.ofMillis(s), Duration.ofMillis(o))));
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean canUseCustomColors(VolmitSender volmitSender) {
        return volmitSender.isPlayer() ? useCustomColorsIngame : useConsoleCustomColors;
    }

    private Component createNoPrefixComponent(String message) {
        if (!canUseCustomColors(this)) {
            String t = C.translateAlternateColorCodes('&', MiniMessage.miniMessage().stripTags(message));
            return MiniMessage.miniMessage().deserialize(t);
        }

        String t = C.translateAlternateColorCodes('&', message);
        String a = C.aura(t, spinh, spins, spinb, 0.36);
        return MiniMessage.miniMessage().deserialize(a);
    }

    private Component createNoPrefixComponentNoProcessing(String message) {
        return MiniMessage.builder().postProcessor(c -> c).build().deserialize(message);
    }

    private Component createComponent(String message) {
        if (!canUseCustomColors(this)) {
            String t = C.translateAlternateColorCodes('&', MiniMessage.miniMessage().stripTags(getTag() + message));
            return MiniMessage.miniMessage().deserialize(t);
        }

        String t = C.translateAlternateColorCodes('&', getTag() + message);
        String a = C.aura(t, spinh, spins, spinb);
        return MiniMessage.miniMessage().deserialize(a);
    }

    private Component createComponentRaw(String message) {
        if (!canUseCustomColors(this)) {
            String t = C.translateAlternateColorCodes('&', MiniMessage.miniMessage().stripTags(getTag() + message));
            return MiniMessage.miniMessage().deserialize(t);
        }

        String t = C.translateAlternateColorCodes('&', getTag() + message);
        return MiniMessage.miniMessage().deserialize(t);
    }

    public <T> void showWaiting(String passive, CompletableFuture<T> f) {
        AtomicInteger v = new AtomicInteger();
        AtomicReference<T> g = new AtomicReference<>();
        v.set(J.ar(() -> {
            if (f.isDone() && g.get() != null) {
                J.car(v.get());
                sendAction(" ");
                return;
            }

            sendProgress(-1, passive);
        }, 0));
        J.a(() -> {
            try {
                g.set(f.get());
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        });

    }

    @Override
    public void sendMessage(@NotNull String message) {
        if (s instanceof CommandDummy) {
            return;
        }

        if ((!useCustomColorsIngame && s instanceof Player) || !useConsoleCustomColors) {
            s.sendMessage(C.translateAlternateColorCodes('&', getTag() + message));
            return;
        }

        if (message.contains("<NOMINI>")) {
            s.sendMessage(C.translateAlternateColorCodes('&', getTag() + message.replaceAll("\\Q<NOMINI>\\E", "")));
            return;
        }

        try {
            Adapt.audiences.sender(s).sendMessage(createComponent(message));
        } catch (Throwable e) {
            System.out.println("=============================================");
            e.printStackTrace();
            if (e.getCause() != null) {
                e.getCause().printStackTrace();
            }
            System.out.println("=============================================");
            String t = C.translateAlternateColorCodes('&', getTag() + message);
            String a = C.aura(t, spinh, spins, spinb);

            Adapt.debug("<NOMINI>Failure to parse " + a);
            s.sendMessage(C.translateAlternateColorCodes('&', getTag() + message));
        }
    }

    public void sendMessageBasic(String message) {
        s.sendMessage(C.translateAlternateColorCodes('&', getTag() + message));
    }

    public void sendMessageRaw(String message) {
        if (s instanceof CommandDummy) {
            return;
        }

        if ((!useCustomColorsIngame && s instanceof Player) || !useConsoleCustomColors) {
            s.sendMessage(C.translateAlternateColorCodes('&', message));
            return;
        }

        if (message.contains("<NOMINI>")) {
            s.sendMessage(message.replaceAll("\\Q<NOMINI>\\E", ""));
            return;
        }

        try {
            Adapt.audiences.sender(s).sendMessage(createComponentRaw(message));
        } catch (Throwable e) {
            String t = C.translateAlternateColorCodes('&', getTag() + message);
            String a = C.aura(t, spinh, spins, spinb);

            System.out.println("=============================================");
            e.printStackTrace();
            if (e.getCause() != null) {
                e.getCause().printStackTrace();
            }
            System.out.println("=============================================");
            Adapt.debug("<NOMINI>Failure to parse " + a);
            s.sendMessage(C.translateAlternateColorCodes('&', getTag() + message));
        }
    }

    @Override
    public void sendMessage(String[] messages) {
        for (String str : messages)
            sendMessage(str);
    }

    @Override
    public void sendMessage(UUID uuid, @NotNull String message) {
        sendMessage(message);
    }

    @Override
    public void sendMessage(UUID uuid, String[] messages) {
        sendMessage(messages);
    }

    @Override
    public @NotNull Server getServer() {
        return s.getServer();
    }

    @Override
    public @NotNull String getName() {
        return s.getName();
    }

    @Override
    public @NotNull Spigot spigot() {
        return s.spigot();
    }

    private String pickRandoms(int max, VirtualDecreeCommand i) {
        KList<String> m = new KList<>();
        for (int ix = 0; ix < max; ix++) {
            m.add((i.isNode()
                    ? (i.getNode().getParameters().isNotEmpty())
                    ? "<#B0E0E6>✦ <#00FA9A>"
                    + i.getParentPath()
                    + " <#00CED1>"
                    + i.getName() + " "
                    + i.getNode().getParameters().shuffleCopy(RNG.r).kConvert((f)
                            -> (f.isRequired() || RNG.r.b(0.5)
                            ? "<#f2e15e>" + f.getNames().getRandom() + "="
                            + "<#9370DB>" + f.example()
                            : ""))
                    .toString(" ")
                    : ""
                    : ""));
        }

        return m.removeDuplicates().kConvert((iff) -> iff.replaceAll("\\Q  \\E", " ")).toString("\n");
    }

    public void sendHeader(String name, int overrideLength) {
        int h = name.length() + 2;
        String s = Form.repeat(" ", overrideLength - h - 4);
        String si = Form.repeat("(", 3);
        String so = Form.repeat(")", 3);
        String sf = "[";
        String se = "]";

        if (name.trim().isEmpty()) {
            sendMessageRaw("<font:minecraft:uniform><strikethrough><gradient:#00BFFF:#003366>" + sf + s + "<reset><font:minecraft:uniform><strikethrough><gradient:#003366:#00BFFF>" + s + se);
        } else {
            sendMessageRaw("<font:minecraft:uniform><strikethrough><gradient:#00BFFF:#003366>" + sf + s + si + "<reset> <gradient:#323bbf:#3299bf>" + name + "<reset> <font:minecraft:uniform><strikethrough><gradient:#003366:#00BFFF>" + so + s + se);
        }
    }

    public void sendHeader(String name) {
        sendHeader(name, 40);
    }

    public void sendDecreeHelp(VirtualDecreeCommand v) {
        sendDecreeHelp(v, 0);
    }

    public void sendDecreeHelp(VirtualDecreeCommand v, int page) {
        if (!isPlayer()) {
            for (VirtualDecreeCommand i : v.getNodes()) {
                sendDecreeHelpNode(i);
            }

            return;
        }

        int m = v.getNodes().size();

        sendMessageRaw("\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n");

        if (v.getNodes().isNotEmpty()) {
            sendHeader(v.getPath() + (page > 0 ? (" {" + (page + 1) + "}") : ""));
            if (isPlayer() && v.getParent() != null) {
                sendMessageRaw("<hover:show_text:'" + "<#8B4513>Click to go back to <#4682B4>" + Form.capitalize(v.getParent().getName()) + " Help" + "'><click:run_command:" + v.getParent().getPath() + "><font:minecraft:uniform><#B30000>〈 Back</click></hover>");
            }

            AtomicBoolean next = new AtomicBoolean(false);
            for (VirtualDecreeCommand i : paginate(v.getNodes(), 17, page, next)) {
                sendDecreeHelpNode(i);
            }

            String s = "";
            int l = 75 - (page > 0 ? 10 : 0) - (next.get() ? 10 : 0);

            if (page > 0) {
                s += "<hover:show_text:'<green>Click to go back to page " + page + "'><click:run_command:" + v.getPath() + " help=" + page + "><gradient:#2770b8:#27b84d>〈 Page " + page + "</click></hover><reset> ";
            }

            s += "<reset><font:minecraft:uniform><strikethrough><gradient:#323bbf:#3299bf>" + Form.repeat(" ", l) + "<reset>";

            if (next.get()) {
                s += " <hover:show_text:'<green>Click to go to back to page " + (page + 2) + "'><click:run_command:" + v.getPath() + " help=" + (page + 2) + "><gradient:#4169E1:#228B22>Page " + (page + 2) + " ❭</click></hover>";
            }

            sendMessageRaw(s);
        } else {
            sendMessage(C.RED + "There are no subcommands in this group! Contact support, this is a command design issue!");
        }
    }

    public void sendDecreeHelpNode(VirtualDecreeCommand i) {
        if (isPlayer() || s instanceof CommandDummy) {
            sendMessageRaw(helpCache.computeIfAbsent(i.getPath(), (k) -> {
                String newline = "<reset>\n";

                /// Command
                // Contains main command & aliases
                String realText = i.getPath() + " >" + "<#E6F2FF>⇀<gradient:#00BFFF:#003366> " + i.getName();
                String hoverTitle = i.getNames().copy().reverse().kConvert((f) -> "<#00BFFF>" + f).toString(", ");
                String description = "<#3fe05a>✎ <#6ad97d><font:minecraft:uniform>" + i.getDescription();
                String usage = "<#FF0000>✒ <#A52A2A><font:minecraft:uniform>";

                String onClick;
                if (i.isNode()) {
                    if (i.getNode().getParameters().isEmpty()) {
                        usage += "There are no parameters. Click to type command.";
                        onClick = "suggest_command";
                    } else {
                        usage += "Hover over all of the parameters to learn more.";
                        onClick = "suggest_command";
                    }
                } else {
                    usage += "This is a command category. Click to run.";
                    onClick = "run_command";
                }

                String suggestion = "";
                String suggestions = "";
                if (i.isNode() && i.getNode().getParameters().isNotEmpty()) {
                    suggestion += newline + "<#B0E0E6>✦ <#00FA9A><font:minecraft:uniform>" + i.getParentPath() + " <#00CED1>" + i.getName() + " "
                            + i.getNode().getParameters().kConvert((f) -> "<#9370DB>" + f.example()).toString(" ");
                    suggestions += newline + "<font:minecraft:uniform>" + pickRandoms(Math.min(i.getNode().getParameters().size() + 1, 5), i);
                }

                /// Params
                StringBuilder nodes = new StringBuilder();
                if (i.isNode()) {
                    for (DecreeParameter p : i.getNode().getParameters()) {

                        String nTitle = "<gradient:#9370DB:#BA55D3>" + p.getName();
                        String nHoverTitle = p.getNames().kConvert((ff) -> "<#ff9900>" + ff).toString(", ");
                        String nDescription = "<#2E8B57>✎ <#3CB371><font:minecraft:uniform>" + p.getDescription();
                        String nUsage;
                        String fullTitle;
                        Adapt.debug("Contextual: " + p.isContextual() + " / player: " + isPlayer());
                        if (p.isContextual() && (isPlayer() || s instanceof CommandDummy)) {
                            fullTitle = "<#FFD700>[" + nTitle + "<#FFD700>] ";
                            nUsage = "<#ff9900>➱ <#FFD700><font:minecraft:uniform>The value may be derived from environment context.";
                        } else if (p.isRequired()) {
                            fullTitle = "<red>[" + nTitle + "<red>] ";
                            nUsage = "<#CD5C5C>⚠ <#F08080><font:minecraft:uniform>This parameter is required.";
                        } else if (p.hasDefault()) {
                            fullTitle = "<#F7F7F7>⊰" + nTitle + "<#F7F7F7>⊱";
                            nUsage = "<#1E90FF>✔ <#87CEEB><font:minecraft:uniform>Defaults to \"" + p.getParam().defaultValue() + "\" if undefined.";
                        } else {
                            fullTitle = "<#F7F7F7>⊰" + nTitle + "<#F7F7F7>⊱";
                            nUsage = "<#8A2BE2>✔ <#87CEEB><font:minecraft:uniform>This parameter is optional.";
                        }
                        String type = "<#cc00ff>✢ <#ff33cc><font:minecraft:uniform>This parameter is of type " + p.getType().getSimpleName() + ".";

                        nodes
                                .append("<hover:show_text:'")
                                .append(nHoverTitle).append(newline)
                                .append(nDescription).append(newline)
                                .append(nUsage).append(newline)
                                .append(type)
                                .append("'>")
                                .append(fullTitle)
                                .append("</hover>");
                    }
                } else {
                    nodes = new StringBuilder("<gradient:#AFEEEE:#B0E0E6> - Category of Commands");
                }

                /// Wrapper
                //Newlines for suggestions are added when they're built, to prevent blanklines.
                // ^

                return "<hover:show_text:'" +
                        hoverTitle + newline +
                        description + newline +
                        usage +
                        suggestion + //Newlines for suggestions are added when they're built, to prevent blanklines.
                        suggestions + // ^
                        "'>" +
                        "<click:" +
                        onClick +
                        ":" +
                        realText +
                        "</click>" +
                        "</hover>" +
                        " " +
                        nodes;
            }));
        } else {
            sendMessage(i.getPath());
        }
    }

    public void playSound(Sound sound, float volume, float pitch) {
        if (isPlayer()) {
            player().playSound(player().getLocation(), sound, volume, pitch);
        }
    }
}
