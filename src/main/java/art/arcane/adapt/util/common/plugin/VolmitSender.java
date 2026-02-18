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

package art.arcane.adapt.util.common.plugin;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.util.common.format.AdventureCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.project.command.CommandDummy;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.director.visual.DirectorVisualCommand;
import art.arcane.volmlib.util.director.visual.DirectorVisualCommand.DirectorVisualParameter;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.RNG;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a volume sender. A command sender with extra crap in its
 *
 * @author cyberpwn
 */
public class VolmitSender implements CommandSender {
  @Getter
  private static final KMap<String, String> helpCache = new KMap<>();
  private final CommandSender s;
  public boolean useConsoleCustomColors = true;
  public boolean useCustomColorsIngame = true;
  public int spinh = -20;
  public int spins = 7;
  public int spinb = 8;
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
    return art.arcane.volmlib.util.math.M.ms() / 16;
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
   * Get the command tag
   *
   * @return the command tag
   */
  public String getTag() {
    return tag;
  }

  /**
   * Set a command tag (prefix for sendMessage)
   *
   * @param tag the tag
   */
  public void setTag(String tag) {
    this.tag = tag;
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

  /**
   * Get the origin sender this object is wrapping
   *
   * @return the command sender
   */
  public CommandSender getS() {
    return s;
  }

  @Override
  public boolean isPermissionSet(String name) {
    return s.isPermissionSet(name);
  }

  @Override
  public boolean isPermissionSet(Permission perm) {
    return s.isPermissionSet(perm);
  }

  @Override
  public boolean hasPermission(String name) {
    return s.hasPermission(name);
  }

  @Override
  public boolean hasPermission(Permission perm) {
    return s.hasPermission(perm);
  }

  @Override
  public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
    return s.addAttachment(plugin, name, value);
  }

  @Override
  public PermissionAttachment addAttachment(Plugin plugin) {
    return s.addAttachment(plugin);
  }

  @Override
  public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
    return s.addAttachment(plugin, name, value, ticks);
  }

  @Override
  public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
    return s.addAttachment(plugin, ticks);
  }

  @Override
  public void removeAttachment(PermissionAttachment attachment) {
    s.removeAttachment(attachment);
  }

  @Override
  public void recalculatePermissions() {
    s.recalculatePermissions();
  }

  @Override
  public Set<PermissionAttachmentInfo> getEffectivePermissions() {
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
      sendActionNoProcessing("" + "" + pulse("#ff5c5c", "#4d0000", 1D) + "<underlined> " + Form.repeat(" ", g) + "<reset>" + Form.repeat(" ", l - g));
    } else {
      int l = 44;
      int g = (int) (percent * l);
      sendTitle(C.ADAPT + thing + " " + C.BLUE + "<font:minecraft:uniform>" + Form.pc(percent, 0), 0, 500, 250);
      sendActionNoProcessing("" + "" + pulse("#ff5c5c", "#4d0000", 1D) + "<underlined> " + Form.repeat(" ", g) + "<reset>" + Form.repeat(" ", l - g));
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
      String t = C.translateAlternateColorCodes('&', AdventureCompat.stripTags(message));
      return AdventureCompat.deserialize(t);
    }

    String t = C.translateAlternateColorCodes('&', message);
    String a = C.aura(t, spinh, spins, spinb, 0.36);
    return AdventureCompat.deserialize(a);
  }

  private Component createNoPrefixComponentNoProcessing(String message) {
    return AdventureCompat.deserializeNoProcessing(message);
  }

  private Component createComponent(String message) {
    if (!canUseCustomColors(this)) {
      String t = C.translateAlternateColorCodes('&', AdventureCompat.stripTags(getTag() + message));
      return AdventureCompat.deserialize(t);
    }

    String t = C.translateAlternateColorCodes('&', getTag() + message);
    String a = C.aura(t, spinh, spins, spinb);
    return AdventureCompat.deserialize(a);
  }

  private Component createComponentRaw(String message) {
    if (!canUseCustomColors(this)) {
      String t = C.translateAlternateColorCodes('&', AdventureCompat.stripTags(getTag() + message));
      return AdventureCompat.deserialize(t);
    }

    String t = C.translateAlternateColorCodes('&', getTag() + message);
    return AdventureCompat.deserialize(t);
  }

  public <T> void showWaiting(String passive, CompletableFuture<T> f) {
    AtomicInteger v = new AtomicInteger();
    v.set(J.sr(() -> {
      if (f.isDone()) {
        J.csr(v.get());
        sendAction(" ");
        return;
      }

      sendProgress(-1, passive);
    }, 1));
    J.a(() -> {
      try {
        f.get();
      } catch (InterruptedException e) {
        e.printStackTrace();
      } catch (ExecutionException e) {
        e.printStackTrace();
      }
    });

  }

  @Override
  public void sendMessage(String message) {
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
  public void sendMessage(UUID uuid, String message) {
    sendMessage(message);
  }

  @Override
  public void sendMessage(UUID uuid, String[] messages) {
    sendMessage(messages);
  }

  @Override
  public Server getServer() {
    return s.getServer();
  }

  @Override
  public String getName() {
    return s.getName();
  }

  @Override
  public Spigot spigot() {
    return s.spigot();
  }

  private String pickRandoms(int max, DirectorVisualCommand i) {
    KList<String> m = new KList<>();
    for (int ix = 0; ix < max; ix++) {
      m.add((i.isNode()
          ? (i.getNode().getParameters().isNotEmpty())
          ? "<#ffd1d1>✦ <#ff6b6b>"
          + i.getParentPath()
          + " <#ff8a8a>"
          + i.getName() + " "
          + i.getNode().getParameters().shuffleCopy(RNG.r).kConvert((f)
              -> (f.isRequired() || RNG.r.b(0.5)
              ? "<#f2e15e>" + f.getNames().getRandom() + "="
              + "<#ff9ea0>" + f.example()
              : ""))
          .toString(" ")
          : ""
          : ""));
    }

    return m.removeDuplicates().kConvert((iff) -> iff.replaceAll("\\Q  \\E", " ")).toString("\n");
  }

  public void sendHeader(String name, int overrideLength) {
    int len = overrideLength;
    int h = name.length() + 2;
    String s = Form.repeat(" ", len - h - 4);
    String si = Form.repeat("(", 3);
    String so = Form.repeat(")", 3);
    String sf = "[";
    String se = "]";

    if (name.trim().isEmpty()) {
      sendMessageRaw("<font:minecraft:uniform><strikethrough><gradient:#ff5c5c:#4d0000>" + sf + s + "<reset><font:minecraft:uniform><strikethrough><gradient:#4d0000:#ff5c5c>" + s + se);
    } else {
      sendMessageRaw("<font:minecraft:uniform><strikethrough><gradient:#ff5c5c:#4d0000>" + sf + s + si + "<reset> <gradient:#c0392b:#ff6b6b>" + name + "<reset> <font:minecraft:uniform><strikethrough><gradient:#4d0000:#ff5c5c>" + so + s + se);
    }
  }

  public void sendHeader(String name) {
    sendHeader(name, 40);
  }

  public void sendDirectorHelp(DirectorVisualCommand v) {
    sendDirectorHelp(v, 0);
  }

  public void sendDirectorHelp(DirectorVisualCommand v, int page) {
    if (!isPlayer()) {
      for (DirectorVisualCommand i : v.getNodes()) {
        sendDirectorHelpNode(i);
      }

      return;
    }

    sendMessageRaw("\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n");

    if (v.getNodes().isNotEmpty()) {
      sendHeader(v.getPath() + (page > 0 ? (" {" + (page + 1) + "}") : ""));
      if (isPlayer() && v.getParent() != null) {
        sendMessageRaw("<hover:show_text:'" + "<#c0392b>Click to go back to <#ff8a8a>" + Form.capitalize(v.getParent().getName()) + " Help" + "'><click:run_command:" + v.getParent().getPath() + "><font:minecraft:uniform><#ff6b6b>〈 Back</click></hover>");
      }

      AtomicBoolean next = new AtomicBoolean(false);
      for (DirectorVisualCommand i : paginate(v.getNodes(), 17, page, next)) {
        sendDirectorHelpNode(i);
      }

      String s = "";
      int l = 75 - (page > 0 ? 10 : 0) - (next.get() ? 10 : 0);

      if (page > 0) {
        s += "<hover:show_text:'<red>Click to go back to page " + page + "'><click:run_command:" + v.getPath() + " help=" + page + "><gradient:#8b1a1a:#ff6b6b>〈 Page " + page + "</click></hover><reset> ";
      }

      s += "<reset><font:minecraft:uniform><strikethrough><gradient:#c0392b:#ff6b6b>" + Form.repeat(" ", l) + "<reset>";

      if (next.get()) {
        s += " <hover:show_text:'<red>Click to go to back to page " + (page + 2) + "'><click:run_command:" + v.getPath() + " help=" + (page + 2) + "><gradient:#c0392b:#ff6b6b>Page " + (page + 2) + " ❭</click></hover>";
      }

      sendMessageRaw(s);
    } else {
      sendMessage(C.RED + "There are no subcommands in this group! Contact support, this is a command design issue!");
    }
  }

  public void sendDirectorHelpNode(DirectorVisualCommand i) {
    if (isPlayer() || s instanceof CommandDummy) {
      sendMessageRaw(helpCache.computeIfAbsent(i.getPath(), (k) -> {
        String newline = "<reset>\n";

        String realText = i.getPath() + " >" + "<#ffe6e6>⇀<gradient:#ff5c5c:#4d0000> " + i.getName();
        String hoverTitle = i.getNames().copy().reverse().kConvert((f) -> "<#ff5c5c>" + f).toString(", ");
        String description = "<#ff9ea0>✎ <#ffd1d1><font:minecraft:uniform>" + i.getDescription();
        String usage = "<#FF0000>✒ <#8b1a1a><font:minecraft:uniform>";

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
          suggestion += newline + "<#ffd1d1>✦ <#ff6b6b><font:minecraft:uniform>" + i.getParentPath() + " <#ff8a8a>" + i.getName() + " "
              + i.getNode().getParameters().kConvert((f) -> "<#ff9ea0>" + f.example()).toString(" ");
          suggestions += newline + "<font:minecraft:uniform>" + pickRandoms(Math.min(i.getNode().getParameters().size() + 1, 5), i);
        }

        StringBuilder nodes = new StringBuilder();
        if (i.isNode()) {
          for (DirectorVisualParameter p : i.getNode().getParameters()) {
            String nTitle = "<gradient:#ff9ea0:#ff9ea0>" + p.getName();
            String nHoverTitle = p.getNames().kConvert((ff) -> "<#ff9900>" + ff).toString(", ");
            String nDescription = "<#ff9ea0>✎ <#ffd1d1><font:minecraft:uniform>" + p.getDescription();
            String nUsage;
            String fullTitle;
            Adapt.debug("Contextual: " + p.isContextual() + " / player: " + isPlayer());
            if (p.isContextual() && (isPlayer() || s instanceof CommandDummy)) {
              fullTitle = "<#ffcc66>[" + nTitle + "<#ffcc66>] ";
              nUsage = "<#ff9900>➱ <#ffcc66><font:minecraft:uniform>The value may be derived from environment context.";
            } else if (p.isRequired()) {
              fullTitle = "<red>[" + nTitle + "<red>] ";
              nUsage = "<#ff6b6b>⚠ <#ffd1d1><font:minecraft:uniform>This parameter is required.";
            } else if (p.hasDefault()) {
              fullTitle = "<#F7F7F7>⊰" + nTitle + "<#F7F7F7>⊱";
              nUsage = "<#ff8a8a>✔ <#ffd1d1><font:minecraft:uniform>Defaults to \"" + p.getParam().defaultValue() + "\" if undefined.";
            } else {
              fullTitle = "<#F7F7F7>⊰" + nTitle + "<#F7F7F7>⊱";
              nUsage = "<#ff6b6b>✔ <#ffd1d1><font:minecraft:uniform>This parameter is optional.";
            }
            String type = "<#ff6b6b>✢ <#ff9ea0><font:minecraft:uniform>This parameter is of type " + p.getType().getSimpleName() + ".";

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
          nodes = new StringBuilder("<gradient:#ffc1c1:#ffd1d1> - Category of Commands");
        }

        return "<hover:show_text:'" +
            hoverTitle + newline +
            description + newline +
            usage +
            suggestion +
            suggestions +
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
