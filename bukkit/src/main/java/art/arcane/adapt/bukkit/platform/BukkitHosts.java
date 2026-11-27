package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.api.internal.HostPlatform;
import art.arcane.adapt.platform.advancement.AdvancementHost;
import art.arcane.adapt.platform.fx.FxHost;
import art.arcane.adapt.api.internal.HostMenus;
import art.arcane.adapt.platform.protection.ProtectionHost;
import art.arcane.adapt.api.internal.HostRecipes;
import art.arcane.adapt.platform.viewer.ViewerHost;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Registration point for hosts and factories that live outside the platform package (event bridge,
 * features). Register before the plugin enables; {@link BukkitPlatform#start} reads them when it
 * assembles the platform. While any host is unset, {@link BukkitPlatform#platform()} stays null and the
 * base hosts remain reachable through {@link BukkitPlatform#host()} and {@link BukkitPlatform#scheduler()}.
 */
public final class BukkitHosts {
  private static final List<Consumer<HostPlatform>> STARTED = new CopyOnWriteArrayList<>();
  private static final List<Runnable> STOPPING = new CopyOnWriteArrayList<>();
  private static volatile FxHost fx;
  private static volatile ViewerHost viewers;
  private static volatile HostMenus menus;
  private static volatile ProtectionHost protection;
  private static volatile HostRecipes recipes;
  private static volatile AdvancementHost advancements;
  private static volatile BukkitDisplayFactory displays;
  private static volatile BukkitGoalsFactory goals;

  private BukkitHosts() {
  }

  public static void fx(FxHost host) {
    fx = host;
  }

  public static FxHost fx() {
    return fx;
  }

  public static void viewers(ViewerHost host) {
    viewers = host;
  }

  public static ViewerHost viewers() {
    return viewers;
  }

  public static void menus(HostMenus host) {
    menus = host;
  }

  public static HostMenus menus() {
    return menus;
  }

  public static void protection(ProtectionHost host) {
    protection = host;
  }

  public static ProtectionHost protection() {
    return protection;
  }

  public static void recipes(HostRecipes host) {
    recipes = host;
  }

  public static HostRecipes recipes() {
    return recipes;
  }

  public static void advancements(AdvancementHost host) {
    advancements = host;
  }

  public static AdvancementHost advancements() {
    return advancements;
  }

  public static void displays(BukkitDisplayFactory factory) {
    displays = factory;
  }

  public static BukkitDisplayFactory displays() {
    return displays;
  }

  public static void goals(BukkitGoalsFactory factory) {
    goals = factory;
  }

  public static BukkitGoalsFactory goals() {
    return goals;
  }

  /** Runs on the main thread right after the platform was assembled during plugin enable. */
  public static void onStarted(Consumer<HostPlatform> listener) {
    STARTED.add(listener);
  }

  /** Runs on the main thread during plugin disable, before the scheduler is cancelled. */
  public static void onStopping(Runnable listener) {
    STOPPING.add(listener);
  }

  static List<Consumer<HostPlatform>> startedListeners() {
    return STARTED;
  }

  static List<Runnable> stoppingListeners() {
    return STOPPING;
  }
}
