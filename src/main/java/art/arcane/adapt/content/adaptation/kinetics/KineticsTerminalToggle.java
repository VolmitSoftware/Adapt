package art.arcane.adapt.content.adaptation.kinetics;

import java.util.List;
import art.arcane.adapt.localization.catalog.KineticsMessages;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.Map;
import java.util.UUID;

public class KineticsTerminalToggle extends SimpleAdaptation<KineticsTerminalToggle.Config> {
  public static final PlayerPreference<Order> ORDER = new PlayerPreference<>(Order.class,
      new PlayerPreference.Definition<>("order", KineticsMessages.KINETICSTERMINALTOGGLE_PREFERENCE_ORDER, Order.DIVE_FIRST, List.of(
          new PlayerPreference.Choice<>(Order.DIVE_FIRST, KineticsMessages.KINETICSTERMINALTOGGLE_PREFERENCE_ORDER_DIVE_FIRST, Material.ANVIL, 1),
          new PlayerPreference.Choice<>(Order.HANG_FIRST, KineticsMessages.KINETICSTERMINALTOGGLE_PREFERENCE_ORDER_HANG_FIRST, Material.FEATHER, 1),
          new PlayerPreference.Choice<>(Order.DIVE_ONLY, KineticsMessages.KINETICSTERMINALTOGGLE_PREFERENCE_ORDER_DIVE_ONLY, Material.POINTED_DRIPSTONE, 1),
          new PlayerPreference.Choice<>(Order.HANG_ONLY, KineticsMessages.KINETICSTERMINALTOGGLE_PREFERENCE_ORDER_HANG_ONLY, Material.PHANTOM_MEMBRANE, 1))));
  public static final PlayerPreference<Gesture> GESTURE = new PlayerPreference<>(Gesture.class,
      new PlayerPreference.Definition<>("gesture", KineticsMessages.KINETICSTERMINALTOGGLE_PREFERENCE_GESTURE, Gesture.SINGLE, List.of(
          new PlayerPreference.Choice<>(Gesture.SINGLE, KineticsMessages.KINETICSTERMINALTOGGLE_PREFERENCE_GESTURE_SINGLE, Material.LEATHER_BOOTS, 1),
          new PlayerPreference.Choice<>(Gesture.DOUBLE, KineticsMessages.KINETICSTERMINALTOGGLE_PREFERENCE_GESTURE_DOUBLE, Material.RABBIT_FOOT, 1))));

  static final int MODE_NONE = 0;
  static final int MODE_DIVE = 1;
  static final int MODE_HANG = 2;
  private static final String SLOT_DRAG = "terminal-drag";
  private static final String SLOT_GRAVITY = "terminal-gravity";
  private static final long REFRESH_WINDOW_TICKS = 10L;

  private final Map<UUID, Long> lastTap = playerState();
  private final Map<UUID, AirState> states = playerState();

  public KineticsTerminalToggle() {
    super("kinetics-terminal-toggle");
    registerConfiguration(Config.class);
    setIcon(Material.PHANTOM_MEMBRANE);
    setInterval(9999);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, ORDER, GESTURE);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    AdaptAttributeService.get().removeAll(player.getPlayer(), getName());
    states.remove(player.getPlayer().getUniqueId());
    lastTap.remove(player.getPlayer().getUniqueId());
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getDragDelta(level), 2), 1);
    statLore(v, Form.f(getGravityDelta(level), 2), 2);
  }

  @EventHandler(ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent e) {
    if (!e.isSneaking()) {
      return;
    }

    Player p = e.getPlayer();
    AirState state = states.get(p.getUniqueId());
    int airTicks = state == null ? 0 : elapsedAirTicks(p.getTicksLived(), state.airborneSinceTick);
    if (!shouldToggle(p.isOnGround(), airTicks, getConfig().minAirTicks)) {
      return;
    }

    if (!hasActiveAdaptation(p)) {
      return;
    }

    if (preference(p, GESTURE) == Gesture.DOUBLE) {
      long now = System.currentTimeMillis();
      Long previous = lastTap.put(p.getUniqueId(), now);
      if (previous == null || now - previous > 350L) {
        return;
      }
      lastTap.remove(p.getUniqueId());
    }
    AirState active = state == null ? new AirState() : state;
    active.mode = switch (preference(p, ORDER)) {
      case DIVE_FIRST -> nextMode(active.mode);
      case HANG_FIRST -> active.mode == MODE_NONE ? MODE_HANG : nextMode(active.mode);
      case DIVE_ONLY -> active.mode == MODE_DIVE ? MODE_NONE : MODE_DIVE;
      case HANG_ONLY -> active.mode == MODE_HANG ? MODE_NONE : MODE_HANG;
    };
    states.put(p.getUniqueId(), active);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    if (e.getTo() == null) {
      return;
    }

    Player p = e.getPlayer();
    if (p.isOnGround()) {
      if (states.isEmpty()) {
        return;
      }
      AirState landed = states.remove(p.getUniqueId());
      if (landed != null && landed.mode != MODE_NONE) {
        AdaptAttributeService.get().removeAll(p, getName());
      }
      return;
    }

    UUID id = p.getUniqueId();
    AirState state = states.computeIfAbsent(id, k -> new AirState());
    if (state.airborneSinceTick < 0) {
      state.airborneSinceTick = p.getTicksLived();
    }
    if (state.mode == MODE_NONE) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      state.mode = MODE_NONE;
      AdaptAttributeService.get().removeAll(p, getName());
      return;
    }

    refreshMode(p, state.mode, level);
  }

  static boolean shouldToggle(boolean onGround, int airTicks, int minAirTicks) {
    return !onGround && airTicks >= minAirTicks;
  }

  static int nextMode(int currentMode) {
    return currentMode == MODE_DIVE ? MODE_HANG : MODE_DIVE;
  }

  static int elapsedAirTicks(int currentTick, int airborneSinceTick) {
    if (airborneSinceTick < 0 || currentTick < airborneSinceTick) {
      return 0;
    }
    return currentTick - airborneSinceTick;
  }

  static double dragAmount(int mode, double dragDelta) {
    return mode == MODE_DIVE ? -dragDelta : dragDelta;
  }

  static double gravityAmount(int mode, double gravityDelta) {
    return mode == MODE_DIVE ? gravityDelta : -gravityDelta;
  }

  private void refreshMode(Player p, int mode, int level) {
    AdaptAttributeService attributes = AdaptAttributeService.get();
    if (Attributes.AIR_DRAG_MODIFIER != null) {
      attributes.applyTimed(p, getName(), SLOT_DRAG, Attributes.AIR_DRAG_MODIFIER, dragAmount(mode, getDragDelta(level)), AttributeModifier.Operation.MULTIPLY_SCALAR_1, REFRESH_WINDOW_TICKS);
    }
    if (Attributes.GRAVITY != null) {
      attributes.applyTimed(p, getName(), SLOT_GRAVITY, Attributes.GRAVITY, gravityAmount(mode, getGravityDelta(level)), AttributeModifier.Operation.MULTIPLY_SCALAR_1, REFRESH_WINDOW_TICKS);
    }
  }

  private double getDragDelta(int level) {
    return getConfig().dragDeltaBase + (getLevelPercent(level) * getConfig().dragDeltaFactor);
  }

  private double getGravityDelta(int level) {
    return getConfig().gravityDeltaBase + (getLevelPercent(level) * getConfig().gravityDeltaFactor);
  }

  private static final class AirState {
    private int airborneSinceTick = -1;
    private int mode = MODE_NONE;
  }

  @ConfigDescription("Sneak in midair to switch between a hard dive and a drifting hang.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base air drag shift applied while a midair mode is active.", impact = "Higher values make dives cut through the air harder and hangs drift slower.")
    double dragDeltaBase = 0.2;
    @ConfigDoc(value = "Additional air drag shift gained at maximum level.", impact = "Higher values widen the gap between low-level and max-level mode strength.")
    double dragDeltaFactor = 0.4;
    @ConfigDoc(value = "Base gravity shift applied while a midair mode is active.", impact = "Higher values make dives fall faster and hangs fall slower.")
    double gravityDeltaBase = 0.2;
    @ConfigDoc(value = "Additional gravity shift gained at maximum level.", impact = "Higher values widen the gap between low-level and max-level mode strength.")
    double gravityDeltaFactor = 0.4;
    @ConfigDoc(value = "Minimum airborne game ticks before sneaking can toggle a midair mode.", impact = "Higher values require longer falls before the toggle arms; lower values arm it sooner.")
    int minAirTicks = 6;

    public Config() {
      baseCost = 4;
      costFactor = 0.45;
      maxLevel = 3;
      initialCost = 2;
    }
  }

  public enum Order { DIVE_FIRST, HANG_FIRST, DIVE_ONLY, HANG_ONLY }

  public enum Gesture { SINGLE, DOUBLE }
}
