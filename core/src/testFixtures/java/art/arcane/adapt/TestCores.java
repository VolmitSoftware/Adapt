package art.arcane.adapt;

import art.arcane.adapt.api.ComponentRegistrar;
import art.arcane.adapt.platform.AdaptPlatform;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.advancement.AdvancementHost;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.fx.FxHost;
import art.arcane.adapt.platform.menu.MenuHost;
import art.arcane.adapt.platform.permission.PermissionHost;
import art.arcane.adapt.platform.protection.ProtectionHost;
import art.arcane.adapt.platform.recipe.RecipeHost;
import art.arcane.adapt.platform.viewer.ViewerHost;

import java.lang.reflect.Field;
import java.util.List;

import static org.mockito.Mockito.mock;

/** Installs an unstarted {@link AdaptCore} as the running core so code that reaches {@code AdaptCore.get()} works in tests. */
public final class TestCores {
  private TestCores() {
  }

  public static AdaptPlatform mockPlatform() {
    return new AdaptPlatform(mock(PlatformHost.class), mock(AdaptScheduler.class), mock(FxHost.class),
        mock(ViewerHost.class), mock(MenuHost.class), mock(PermissionHost.class), mock(ProtectionHost.class),
        mock(RecipeHost.class), mock(AdvancementHost.class));
  }

  public static AdaptCore install() {
    return install(mockPlatform());
  }

  public static AdaptCore install(AdaptPlatform platform) {
    AdaptEventBus bus = new AdaptEventBus((owner, event, error) -> {
      throw new AssertionError("handler failed: " + owner, error);
    });
    AdaptCore core = new AdaptCore(platform, bus, new ComponentRegistrar(bus), List.of());
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

  private static void setCurrent(AdaptCore core) {
    try {
      Field field = AdaptCore.class.getDeclaredField("current");
      field.setAccessible(true);
      field.set(null, core);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
