package art.arcane.adapt.mod.platform;

import art.arcane.adapt.platform.advancement.AdvancementHost;
import art.arcane.adapt.platform.fx.FxHost;
import art.arcane.adapt.platform.menu.MenuHost;
import art.arcane.adapt.platform.protection.ProtectionHost;
import art.arcane.adapt.platform.recipe.RecipeHost;
import art.arcane.adapt.platform.viewer.ViewerHost;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Registration point for hosts and factories that live outside the platform package. Feature packages set these
 * from static initialisers or mod constructors before the server starts; the bootstrap reads them
 * when it assembles the platform.
 */
public final class ModHosts {
    private static final List<Consumer<MinecraftServer>> STARTED = new CopyOnWriteArrayList<>();
    private static final List<Consumer<MinecraftServer>> STOPPING = new CopyOnWriteArrayList<>();
    private static final List<Consumer<MinecraftServer>> TICK = new CopyOnWriteArrayList<>();
    private static volatile FxHost fx;
    private static volatile ViewerHost viewers;
    private static volatile MenuHost menus;
    private static volatile ProtectionHost protection;
    private static volatile RecipeHost recipes;
    private static volatile AdvancementHost advancements;
    private static volatile ModDisplayFactory displays;
    private static volatile ModGoalsFactory goals;

    private ModHosts() {
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

    public static void displays(ModDisplayFactory factory) {
        displays = factory;
    }

    public static ModDisplayFactory displays() {
        return displays;
    }

    public static void goals(ModGoalsFactory factory) {
        goals = factory;
    }

    public static ModGoalsFactory goals() {
        return goals;
    }

    /** Runs on the server thread after the server started and the platform was assembled. */
    public static void onServerStarted(Consumer<MinecraftServer> listener) {
        STARTED.add(listener);
    }

    /** Runs on the server thread before the scheduler shuts down. */
    public static void onServerStopping(Consumer<MinecraftServer> listener) {
        STOPPING.add(listener);
    }

    /** Runs on the server thread at the end of every server tick, after scheduled tasks. */
    public static void onServerTick(Consumer<MinecraftServer> listener) {
        TICK.add(listener);
    }

    static List<Consumer<MinecraftServer>> startedListeners() {
        return STARTED;
    }

    static List<Consumer<MinecraftServer>> stoppingListeners() {
        return STOPPING;
    }

    static List<Consumer<MinecraftServer>> tickListeners() {
        return TICK;
    }
}
