package art.arcane.adapt;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.engine.ComponentRegistrar;
import art.arcane.adapt.engine.addon.AddonLifecycle;
import art.arcane.adapt.engine.bridge.CoreBridge;
import art.arcane.adapt.api.internal.HostMenus;
import art.arcane.adapt.api.internal.HostPermissions;
import art.arcane.adapt.api.internal.HostPlatform;
import art.arcane.adapt.api.internal.HostRecipes;
import art.arcane.adapt.api.internal.HostScheduler;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.advancement.AdvancementHost;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.fx.FxHost;
import art.arcane.adapt.platform.protection.ProtectionHost;
import art.arcane.adapt.platform.viewer.ViewerHost;

import java.lang.reflect.Field;

import static org.mockito.Mockito.mock;

/**
 * Installs an unstarted {@link AdaptCore} as the running core so code that reaches {@code AdaptCore.get()} works in
 * tests. While no started core runs, the installed core also serves the public API through its own bridge.
 */
public final class TestCores {
  private static CoreBridge testBridge;

  private TestCores() {
  }

  public static HostPlatform mockPlatform() {
    return new HostPlatform(mock(PlatformHost.class), mock(HostScheduler.class), mock(FxHost.class),
        mock(ViewerHost.class), mock(HostMenus.class), mock(HostPermissions.class), mock(ProtectionHost.class),
        mock(HostRecipes.class), mock(AdvancementHost.class));
  }

  public static AdaptCore install() {
    return install(mockPlatform());
  }

  public static AdaptCore install(HostPlatform platform) {
    AdaptEventBus bus = new AdaptEventBus((owner, event, error) -> {
      throw new AssertionError("handler failed: " + owner, error);
    });
    AdaptCore core = new AdaptCore(platform, bus, new ComponentRegistrar(bus));
    setCurrent(core);
    return core;
  }

  public static void setField(AdaptCore core, String name, Object value) {
    try {
      Field field = AdaptCore.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(core, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  public static void clear() {
    setCurrent(null);
  }

  public static void use(AdaptCore core) {
    setCurrent(core);
  }

  private static synchronized void setCurrent(AdaptCore core) {
    try {
      Field field = AdaptCore.class.getDeclaredField("current");
      field.setAccessible(true);
      field.set(null, core);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    if (testBridge != null) {
      CoreBridge.stop(testBridge);
      testBridge = null;
    }
    if (core != null && !Engine.isRunning()) {
      testBridge = new CoreBridge(core, new AddonLifecycle(core));
      CoreBridge.start(testBridge);
    }
  }
}
