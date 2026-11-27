package art.arcane.adapt.api.internal;

import art.arcane.adapt.api.client.FrameRegistration;
import art.arcane.adapt.api.client.FrameRenderer;
import art.arcane.adapt.api.client.ThemeRegistry;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** The registered hover frame renderers. The client reads them when it draws a hovered tile. */
@ApiStatus.Internal
public final class FrameRegistry implements ThemeRegistry {
  public static final FrameRegistry INSTANCE = new FrameRegistry();

  private final Map<Id, FrameRenderer> renderers = new ConcurrentHashMap<>();

  private FrameRegistry() {
  }

  @Override
  public FrameRegistration frame(Id skill, FrameRenderer renderer) {
    Objects.requireNonNull(skill, "skill");
    Objects.requireNonNull(renderer, "renderer");
    if (renderers.putIfAbsent(skill, renderer) != null) {
      throw new IllegalArgumentException("A hover frame renderer is already registered for " + skill);
    }
    return () -> renderers.remove(skill, renderer);
  }

  public @Nullable FrameRenderer renderer(Id skill) {
    return renderers.get(skill);
  }
}
