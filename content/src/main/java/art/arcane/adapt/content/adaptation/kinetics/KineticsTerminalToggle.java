package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.hud.HudGlyph;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KineticsTerminalToggle extends SimpleAdaptation<KineticsTerminalToggle.Config> {
  public static final Id ID = Id.adapt("kinetics/terminal_toggle");
  public static final PlayerPreference<Order> ORDER = PlayerPreference.of("order", Order.class, Order.DIVE_FIRST, List.of(
          new PlayerPreference.Choice<>(Order.DIVE_FIRST, Text.key("adapt.kineticsterminaltoggle.preferences.preference_order_dive_first"), Id.minecraft("anvil"), 1),
          new PlayerPreference.Choice<>(Order.HANG_FIRST, Text.key("adapt.kineticsterminaltoggle.preferences.preference_order_hang_first"), Id.minecraft("feather"), 1),
          new PlayerPreference.Choice<>(Order.DIVE_ONLY, Text.key("adapt.kineticsterminaltoggle.preferences.preference_order_dive_only"), Id.minecraft("pointed_dripstone"), 1),
          new PlayerPreference.Choice<>(Order.HANG_ONLY, Text.key("adapt.kineticsterminaltoggle.preferences.preference_order_hang_only"), Id.minecraft("phantom_membrane"), 1))).labelled(Text.key("adapt.kineticsterminaltoggle.preferences.preference_order"));
  public static final PlayerPreference<Gesture> GESTURE = PlayerPreference.of("gesture", Gesture.class, Gesture.SINGLE, List.of(
          new PlayerPreference.Choice<>(Gesture.SINGLE, Text.key("adapt.kineticsterminaltoggle.preferences.preference_gesture_single"), Id.minecraft("leather_boots"), 1),
          new PlayerPreference.Choice<>(Gesture.DOUBLE, Text.key("adapt.kineticsterminaltoggle.preferences.preference_gesture_double"), Id.minecraft("rabbit_foot"), 1))).labelled(Text.key("adapt.kineticsterminaltoggle.preferences.preference_gesture"));

  static final int MODE_NONE = 0;
  static final int MODE_DIVE = 1;
  static final int MODE_HANG = 2;
  private static final String SLOT_DRAG = "terminal-drag";
  private static final String SLOT_GRAVITY = "terminal-gravity";
  private static final long REFRESH_WINDOW_TICKS = 10L;

  private final Map<UUID, Long> lastTap = playerState();
  private final Map<UUID, AirState> states = playerState();

  public KineticsTerminalToggle() {
    super(ID);
    needs(PlatformNeed.nativeAttribute(Attributes.GRAVITY));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("phantom_membrane"));
    setInterval(9999);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, ORDER, GESTURE);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    AdaptAttributeService.get().removeAll(player.player(), getId().toString());
    states.remove(player.player().uuid());
    lastTap.remove(player.player().uuid());
    HudStatus.clear(player.player().uuid(), this, HudGlyph.TOGGLE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getDragDelta(level), 2), 1);
    statLore(v, Format.f(getGravityDelta(level), 2), 2);
  }

  @Handle
  public void on(PlayerToggleSneakEvent e) {
    if (!e.isSneaking()) {
      return;
    }

    PlayerRef p = e.player();
    AirState state = states.get(p.uuid());
    int airTicks = state == null ? 0 : elapsedAirTicks(p.ticksLived(), state.airborneSinceTick);
    if (!shouldToggle(p.isOnGround(), airTicks, getConfig().minAirTicks)) {
      return;
    }

    if (!hasActiveAdaptation(p)) {
      return;
    }

    if (preference(p, GESTURE) == Gesture.DOUBLE) {
      long now = System.currentTimeMillis();
      Long previous = lastTap.put(p.uuid(), now);
      if (previous == null || now - previous > 350L) {
        return;
      }
      lastTap.remove(p.uuid());
    }
    AirState active = state == null ? new AirState() : state;
    active.mode = switch (preference(p, ORDER)) {
      case DIVE_FIRST -> nextMode(active.mode);
      case HANG_FIRST -> active.mode == MODE_NONE ? MODE_HANG : nextMode(active.mode);
      case DIVE_ONLY -> active.mode == MODE_DIVE ? MODE_NONE : MODE_DIVE;
      case HANG_ONLY -> active.mode == MODE_HANG ? MODE_NONE : MODE_HANG;
    };
    states.put(p.uuid(), active);
    if (active.mode == MODE_NONE) {
      HudStatus.clear(p.uuid(), this, HudGlyph.TOGGLE);
    } else {
      HudStatus.toggle(p.uuid(), this, true);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    if (e.to() == null) {
      return;
    }

    PlayerRef p = e.player();
    if (p.isOnGround()) {
      if (states.isEmpty()) {
        return;
      }
      AirState landed = states.remove(p.uuid());
      if (landed != null && landed.mode != MODE_NONE) {
        AdaptAttributeService.get().removeAll(p, getId().toString());
        HudStatus.clear(p.uuid(), this, HudGlyph.TOGGLE);
      }
      return;
    }

    UUID id = p.uuid();
    AirState state = states.computeIfAbsent(id, k -> new AirState());
    if (state.airborneSinceTick < 0) {
      state.airborneSinceTick = p.ticksLived();
    }
    if (state.mode == MODE_NONE) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      state.mode = MODE_NONE;
      AdaptAttributeService.get().removeAll(p, getId().toString());
      HudStatus.clear(p.uuid(), this, HudGlyph.TOGGLE);
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

  private void refreshMode(PlayerRef p, int mode, int level) {
    AdaptAttributeService attributes = AdaptAttributeService.get();
    if (AdaptApi.platform().host().registries().exists(RegistryKind.ATTRIBUTE, Attributes.AIR_DRAG_MODIFIER)) {
      attributes.applyTimed(p, getId().toString(), SLOT_DRAG, Attributes.AIR_DRAG_MODIFIER, dragAmount(mode, getDragDelta(level)), AttributeOperation.ADD_MULTIPLIED_TOTAL, REFRESH_WINDOW_TICKS);
    }
    attributes.applyTimed(p, getId().toString(), SLOT_GRAVITY, Attributes.GRAVITY, gravityAmount(mode, getGravityDelta(level)), AttributeOperation.ADD_MULTIPLIED_TOTAL, REFRESH_WINDOW_TICKS);
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
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 3;
      initialCost = 1;
    }
  }

  public enum Order { DIVE_FIRST, HANG_FIRST, DIVE_ONLY, HANG_ONLY }

  public enum Gesture { SINGLE, DOUBLE }
}
