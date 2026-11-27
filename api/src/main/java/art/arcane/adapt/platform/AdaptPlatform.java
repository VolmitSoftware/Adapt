package art.arcane.adapt.platform;

import art.arcane.adapt.platform.fx.FxHost;
import art.arcane.adapt.platform.menu.MenuHost;
import art.arcane.adapt.platform.permission.PermissionHost;
import art.arcane.adapt.platform.protection.ProtectionHost;
import art.arcane.adapt.platform.recipe.RecipeHost;
import art.arcane.adapt.platform.viewer.ViewerHost;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * The platform Adapt runs on, as {@code AdaptApi.platform()} returns it. Each host is a view that forwards to the
 * platform's own and leaves out what only Adapt's engine may do (cancel every task, close every menu, replace the
 * advancement tree, remove permissions or recipes registered by others).
 *
 * @param host root platform facts, registries and player and world lookups
 * @param scheduler task scheduling on the right thread or region
 * @param fx particles, sounds and other visual and audio effects
 * @param viewers per-viewer client state: entity hiding, glow, fake equipment, virtual players
 * @param menus chest menus and storage windows
 * @param permissions permission checks and node registration
 * @param protection claim and region checks
 * @param recipes server recipe registration and lookup
 */
@ApiStatus.AvailableSince("1.0.0")
public record AdaptPlatform(PlatformHost host, AdaptScheduler scheduler, FxHost fx, ViewerHost viewers,
                            MenuHost menus, PermissionHost permissions, ProtectionHost protection,
                            RecipeHost recipes) {
  public AdaptPlatform {
    Objects.requireNonNull(host, "host");
    Objects.requireNonNull(scheduler, "scheduler");
    Objects.requireNonNull(fx, "fx");
    Objects.requireNonNull(viewers, "viewers");
    Objects.requireNonNull(menus, "menus");
    Objects.requireNonNull(permissions, "permissions");
    Objects.requireNonNull(protection, "protection");
    Objects.requireNonNull(recipes, "recipes");
  }

  /** True when the platform offers the optional capability; shorthand for {@code host().has(capability)}. */
  public boolean has(Capability capability) {
    return host.has(capability);
  }
}
