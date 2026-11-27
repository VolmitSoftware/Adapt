package art.arcane.adapt.pack;

import art.arcane.adapt.pack.surface.PackSession;
import art.arcane.adapt.pack.surface.PackSlot;
import art.arcane.adapt.pack.surface.PackSupport;
import art.arcane.adapt.pack.surface.PackSurface;
import art.arcane.adapt.pack.surface.PackWindows;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuClick;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pack windows in memory: records every open and update, runs clicks and closes like a platform host would. */
public final class FakePackWindows implements PackWindows {
  public final List<Window> opened = new ArrayList<>();
  public final Map<UUID, Window> open = new HashMap<>();
  public PackSupport support = PackSupport.FULL;

  @Override
  public PackSession open(PlayerRef viewer, ChestMenu menu, PackSurface surface) {
    if (!support.lowerSlots() && !surface.lower().isEmpty()) {
      throw new IllegalStateException("lower buttons sent to a top-rows-only viewer");
    }
    if (menu.freePlayerInventory() && !surface.lower().isEmpty()) {
      throw new IllegalArgumentException("free inventory with lower buttons");
    }
    Window window = new Window(viewer, menu, surface, support.lowerSlots());
    Window previous = open.put(viewer.uuid(), window);
    opened.add(window);
    if (previous != null && previous.open) {
      previous.finish();
    }
    return window;
  }

  @Override
  public PackSupport support(PlayerRef viewer) {
    return support;
  }

  public Window window(PlayerRef viewer) {
    Window window = open.get(viewer.uuid());
    return window != null && window.open ? window : null;
  }

  public static final class Window implements PackSession {
    private final PlayerRef viewer;
    private final boolean lowerSlots;
    public ChestMenu menu;
    public PackSurface surface;
    public boolean open = true;
    public int updates;
    public final List<String> oneShots = new ArrayList<>();

    Window(PlayerRef viewer, ChestMenu menu, PackSurface surface, boolean lowerSlots) {
      this.viewer = viewer;
      this.menu = menu;
      this.surface = surface;
      this.lowerSlots = lowerSlots;
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
      return open;
    }

    @Override
    public void update(ChestMenu next) {
      update(next, surface);
    }

    @Override
    public void update(ChestMenu next, PackSurface nextSurface) {
      menu = next;
      surface = nextSurface;
      updates++;
    }

    @Override
    public PackSurface surface() {
      return surface;
    }

    @Override
    public void oneShot(PackSlot slot, int ticks) {
      oneShots.add(slot + "/" + ticks);
    }

    @Override
    public boolean lowerSlots() {
      return lowerSlots;
    }

    @Override
    public void close() {
      if (open) {
        finish();
      }
    }

    /** Clicks a chest slot, or player slot {@code menu.size() + i}. False when nothing there takes clicks. */
    public boolean click(int slot, ClickKind kind) {
      MenuButton button = slot < menu.size() ? menu.button(slot) : surface.lower().get(slot - menu.size());
      if (button == null || button.action() == null) {
        return false;
      }
      button.action().click(new MenuClick(viewer, this, slot, kind));
      return true;
    }

    public boolean click(PackSlot slot, ClickKind kind) {
      return click(slot.clickSlot(menu.size()), kind);
    }

    public MenuButton button(PackSlot slot) {
      return slot.area() == PackSlot.Area.TOP ? menu.button(slot.index()) : surface.lower().get(slot.index());
    }

    void finish() {
      open = false;
      if (menu.onClose() != null) {
        menu.onClose().accept(this);
      }
    }
  }
}
