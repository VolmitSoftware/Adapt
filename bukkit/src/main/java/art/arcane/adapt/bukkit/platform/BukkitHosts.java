package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.platform.AdaptPlatform;
import art.arcane.adapt.platform.advancement.AdvancementHost;
import art.arcane.adapt.platform.fx.FxHost;
import art.arcane.adapt.platform.menu.MenuHost;
import art.arcane.adapt.platform.protection.ProtectionHost;
import art.arcane.adapt.platform.recipe.RecipeHost;
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
  private static final List<Consumer<AdaptPlatform>> STARTED = new CopyOnWriteArrayList<>();
  private static final List<Runnable> STOPPING = new CopyOnWriteArrayList<>();
  private static volatile FxHost fx;
  private static volatile ViewerHost viewers;
  private static volatile MenuHost menus;
  private static volatile ProtectionHost protection;
  private static volatile RecipeHost recipes;
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

  public static void menus(MenuHost host) {
    menus = host;
  }

  public static MenuHost menus() {
    return menus;
  }

  public static void protection(ProtectionHost host) {
    protection = host;
  }

  public static ProtectionHost protection() {
    return protection;
  }

  public static void recipes(RecipeHost host) {
    recipes = host;
  }

  public static RecipeHost recipes() {
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
  public static void onStarted(Consumer<AdaptPlatform> listener) {
    STARTED.add(listener);
  }

  /** Runs on the main thread during plugin disable, before the scheduler is cancelled. */
  public static void onStopping(Runnable listener) {
    STOPPING.add(listener);
  }

  static List<Consumer<AdaptPlatform>> startedListeners() {
    return STARTED;
  }

  static List<Runnable> stoppingListeners() {
    return STOPPING;
  }
}
