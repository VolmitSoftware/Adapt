package art.arcane.adapt.api.internal;

import art.arcane.adapt.platform.AdaptPlatform;
import art.arcane.adapt.platform.Capability;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.advancement.AdvancementHost;
import art.arcane.adapt.platform.fx.FxHost;
import art.arcane.adapt.platform.protection.ProtectionHost;
import art.arcane.adapt.platform.viewer.ViewerHost;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * Everything a platform host hands to the engine, with the host-only operations. Built once at startup by the
 * Bukkit plugin or the mod entry point; the engine keeps it. Addons see {@link #consumer()}, a view of forwarding
 * wrappers that never exposes these host objects.
 */
@ApiStatus.Internal
public final class HostPlatform {
  private final PlatformHost host;
  private final HostScheduler scheduler;
  private final FxHost fx;
  private final ViewerHost viewers;
  private final HostMenus menus;
  private final HostPermissions permissions;
  private final ProtectionHost protection;
  private final HostRecipes recipes;
  private final AdvancementHost advancements;
  private final AdaptPlatform consumer;

  public HostPlatform(PlatformHost host, HostScheduler scheduler, FxHost fx, ViewerHost viewers, HostMenus menus,
                      HostPermissions permissions, ProtectionHost protection, HostRecipes recipes,
                      AdvancementHost advancements) {
    this.host = Objects.requireNonNull(host, "host");
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    this.fx = Objects.requireNonNull(fx, "fx");
    this.viewers = Objects.requireNonNull(viewers, "viewers");
    this.menus = Objects.requireNonNull(menus, "menus");
    this.permissions = Objects.requireNonNull(permissions, "permissions");
    this.protection = Objects.requireNonNull(protection, "protection");
    this.recipes = Objects.requireNonNull(recipes, "recipes");
    this.advancements = Objects.requireNonNull(advancements, "advancements");
    this.consumer = ConsumerViews.of(this);
  }

  public PlatformHost host() {
    return host;
  }

  public HostScheduler scheduler() {
    return scheduler;
  }

  public FxHost fx() {
    return fx;
  }

  public ViewerHost viewers() {
    return viewers;
  }

  public HostMenus menus() {
    return menus;
  }

  public HostPermissions permissions() {
    return permissions;
  }

  public ProtectionHost protection() {
    return protection;
  }

  public HostRecipes recipes() {
    return recipes;
  }

  public AdvancementHost advancements() {
    return advancements;
  }

  /** True when the platform offers the optional capability; shorthand for {@code host().has(capability)}. */
  public boolean has(Capability capability) {
    return host.has(capability);
  }

  /** The platform as addons see it, the same instance on every call. */
  public AdaptPlatform consumer() {
    return consumer;
  }
}
