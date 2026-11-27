package art.arcane.adapt.bukkit.feature.pack;

import art.arcane.adapt.pack.surface.PackSession;
import art.arcane.adapt.pack.surface.PackSlot;
import art.arcane.adapt.pack.surface.PackSurface;
import art.arcane.adapt.pack.surface.SlotLook;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuSession;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * One open pack page. Updates keep the window when its kind and row count stay, sending only changed slots and a
 * title only when it changed; cycling slots step on one scheduled task at a time. Owning thread only.
 */
final class BukkitPackSession implements PackSession {
  private final BukkitPackWindows windows;
  private final PlayerRef viewer;
  private final Player player;
  private ChestMenu menu;
  private PackSurface surface;
  private PackWindow window;
  private Map<Integer, PackFrame.Shown> shown = Map.of();
  private long openedTick;
  private int cycleTicket;
  private boolean finished;

  BukkitPackSession(BukkitPackWindows windows, PlayerRef viewer, Player player, ChestMenu menu, PackSurface surface) {
    this.windows = windows;
    this.viewer = viewer;
    this.player = player;
    this.menu = menu;
    this.surface = surface;
  }

  /** Takes over {@code previous}'s window; {@code previous} ends without its close callback. */
  void adopt(BukkitPackSession previous) {
    window = previous.window;
    surface = previous.surface;
    shown = previous.shown;
    openedTick = previous.openedTick;
    previous.finished = true;
    previous.cycleTicket++;
    window.owner(this);
  }

  /** Shows {@code next} and {@code nextSurface}, in place when the window allows it. */
  void render(ChestMenu next, PackSurface nextSurface) {
    boolean canvas = windows.canvasFor(player, next, nextSurface);
    PackSurface shownSurface = canvas ? nextSurface : PackFrame.withoutLower(nextSurface);
    boolean titleChanged = !shownSurface.title().equals(surface.title());
    boolean keep = window != null && window.isOpen() && window.canvas() == canvas && window.rows() == next.rows()
        && (!titleChanged || window.retitlesInPlace());
    menu = next;
    surface = shownSurface;
    if (keep) {
      Map<Integer, PackFrame.Shown> frame = PackFrame.of(next, shownSurface, canvas, ticksOpen());
      for (Integer slot : PackFrame.changed(shown, frame)) {
        window.set(slot, frame.get(slot));
      }
      shown = frame;
      if (titleChanged) {
        window.retitle(shownSurface.title());
      } else {
        window.flush();
      }
    } else {
      replaceWindow(canvas);
    }
    scheduleCycle();
  }

  private void replaceWindow(boolean canvas) {
    PackWindow previous = window;
    if (previous != null && previous.isOpen()) {
      previous.retire(!canvas);
    }
    openedTick = windows.tick();
    Map<Integer, PackFrame.Shown> frame = PackFrame.of(menu, surface, canvas, 0L);
    window = canvas ? windows.openCanvas(player, menu.rows(), surface.title(), frame, this) : null;
    if (window == null && (!canvas || !windows.canvasFor(player, menu, surface))) {
      surface = PackFrame.withoutLower(surface);
      frame = PackFrame.of(menu, surface, false, 0L);
      window = ChestPackWindow.open(player, menu, surface.title(), frame, this);
    }
    shown = frame;
    if (window != null) {
      return;
    }
    if (previous != null && previous.isOpen()) {
      previous.retire(true);
      previous.close();
    }
    finish(previous != null);
  }

  private void scheduleCycle() {
    int ticket = ++cycleTicket;
    if (finished || surface.cycles().isEmpty()) {
      return;
    }
    long wait = PackFrame.untilNextFrame(surface.cycles(), ticksOpen());
    windows.later(player, () -> {
      if (ticket == cycleTicket && isOpen()) {
        stepCycles();
      }
    }, (int) Math.max(1L, Math.min(Integer.MAX_VALUE, wait)));
  }

  private void stepCycles() {
    Map<Integer, PackFrame.Shown> frame = PackFrame.of(menu, surface, window.canvas(), ticksOpen());
    for (Integer slot : PackFrame.changed(shown, frame)) {
      window.set(slot, frame.get(slot));
    }
    shown = frame;
    window.flush();
    scheduleCycle();
  }

  private long ticksOpen() {
    return Math.max(0L, windows.tick() - openedTick);
  }

  void clicked(PackWindow source, int windowSlot, ClickKind kind) {
    if (source != window || finished) {
      return;
    }
    MenuButton button = PackClicks.button(menu, surface, windowSlot);
    if (button == null || !button.clickable()) {
      return;
    }
    try {
      button.action().click(new MenuClick(viewer, this, windowSlot, kind));
    } catch (RuntimeException failure) {
      windows.logger().log(Level.WARNING, "Pack menu click failed", failure);
    }
  }

  void closed(PackWindow source) {
    if (source == window) {
      finish(true);
    }
  }

  private void finish(boolean callback) {
    if (finished) {
      return;
    }
    finished = true;
    cycleTicket++;
    windows.forget(this);
    Consumer<MenuSession> onClose = menu.onClose();
    if (callback && onClose != null) {
      try {
        onClose.accept(this);
      } catch (RuntimeException failure) {
        windows.logger().log(Level.WARNING, "Pack menu close callback failed", failure);
      }
    }
  }

  /** Closes the window without its close callback, for a prompt that takes the screen. */
  void putAway() {
    if (window != null && window.isOpen()) {
      window.retire(true);
      window.close();
    }
    finish(false);
  }

  BukkitPackWindows windows() {
    return windows;
  }

  PackWindow window() {
    return window;
  }

  Player player() {
    return player;
  }

  @Override
  public void update(ChestMenu next, PackSurface nextSurface) {
    PackFrame.check(next, nextSurface);
    if (isOpen()) {
      render(next, nextSurface);
    }
  }

  @Override
  public void update(ChestMenu next) {
    update(next, surface);
  }

  @Override
  public PackSurface surface() {
    return surface;
  }

  @Override
  public void oneShot(PackSlot slot, int ticks) {
    SlotLook look = surface.look(slot);
    PackFrame.Shown item = shown.get(slot.clickSlot(menu.size()));
    if (!isOpen() || look == null || look.cooldownGroup() == null || item == null) {
      return;
    }
    player.setCooldown(PackItems.dressed(item.icon(), look), ticks);
  }

  @Override
  public boolean lowerSlots() {
    return window != null && window.canvas();
  }

  @Override
  public PlayerRef viewer() {
    return viewer;
  }

  @Override
  public ChestMenu menu() {
    return menu;
  }

  @Override
  public boolean isOpen() {
    return !finished && window != null && window.isOpen();
  }

  @Override
  public void close() {
    if (finished) {
      return;
    }
    if (window != null && window.isOpen()) {
      window.close();
    }
    finish(true);
  }
}
