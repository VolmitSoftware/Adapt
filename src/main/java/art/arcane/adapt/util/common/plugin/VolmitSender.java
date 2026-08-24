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
import art.arcane.volmlib.util.format.Form;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a volume sender. A command sender with extra crap in its
 *
 * @author cyberpwn
 */
public class VolmitSender implements CommandSender {
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
    return AdventureCompat.deserialize(createMiniMessage(message));
  }

  private String createMiniMessage(String message) {
    if (!canUseCustomColors(this)) {
      String t = C.translateAlternateColorCodes('&', AdventureCompat.stripTags(getTag() + message));
      return t;
    }

    String t = C.translateAlternateColorCodes('&', getTag() + message);
    return C.aura(t, spinh, spins, spinb);
  }

  private Component createComponentRaw(String message) {
    return AdventureCompat.deserialize(createMiniMessageRaw(message));
  }

  private String createMiniMessageRaw(String message) {
    if (!canUseCustomColors(this)) {
      String t = C.translateAlternateColorCodes('&', AdventureCompat.stripTags(getTag() + message));
      return t;
    }

    String t = C.translateAlternateColorCodes('&', getTag() + message);
    return t;
  }

  private boolean deliverRichMessage(String miniMessage) {
    try {
      s.getClass().getMethod("sendRichMessage", String.class).invoke(s, miniMessage);
      return true;
    } catch (Throwable ignored) {
      return false;
    }
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
        Adapt.error(e);
      } catch (ExecutionException e) {
        Adapt.error(e);
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

    if (deliverRichMessage(createMiniMessage(message))) {
      return;
    }

    s.sendMessage(C.translateAlternateColorCodes('&', getTag() + message));
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

    if (deliverRichMessage(createMiniMessageRaw(message))) {
      return;
    }

    s.sendMessage(C.translateAlternateColorCodes('&', getTag() + message));
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
  public Component name() {
    return Component.text(getName());
  }

  @Override
  public Spigot spigot() {
    return s.spigot();
  }


  public void playSound(Sound sound, float volume, float pitch) {
    if (isPlayer()) {
      player().playSound(player().getLocation(), sound, volume, pitch);
    }
  }
}
