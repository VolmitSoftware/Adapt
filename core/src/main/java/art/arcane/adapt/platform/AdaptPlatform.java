package art.arcane.adapt.platform;

import art.arcane.adapt.platform.advancement.AdvancementHost;
import art.arcane.adapt.platform.fx.FxHost;
import art.arcane.adapt.platform.menu.MenuHost;
import art.arcane.adapt.platform.permission.PermissionHost;
import art.arcane.adapt.platform.protection.ProtectionHost;
import art.arcane.adapt.platform.recipe.RecipeHost;
import art.arcane.adapt.platform.viewer.ViewerHost;

import java.util.Objects;

/**
 * Everything an adapter hands to core. Built once at startup by the Bukkit plugin or the mod entry
 * point. The event bus is owned by core, not by the adapter.
 */
public record AdaptPlatform(PlatformHost host, AdaptScheduler scheduler, FxHost fx, ViewerHost viewers,
                            MenuHost menus, PermissionHost permissions, ProtectionHost protection,
                            RecipeHost recipes, AdvancementHost advancements) {
  public AdaptPlatform {
    Objects.requireNonNull(host, "host");
    Objects.requireNonNull(scheduler, "scheduler");
    Objects.requireNonNull(fx, "fx");
    Objects.requireNonNull(viewers, "viewers");
    Objects.requireNonNull(menus, "menus");
    Objects.requireNonNull(permissions, "permissions");
    Objects.requireNonNull(protection, "protection");
    Objects.requireNonNull(recipes, "recipes");
    Objects.requireNonNull(advancements, "advancements");
  }

  public boolean has(Capability capability) {
    return host.has(capability);
  }
}
