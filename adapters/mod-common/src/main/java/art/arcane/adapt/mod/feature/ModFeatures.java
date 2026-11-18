package art.arcane.adapt.mod.feature;

import art.arcane.adapt.mod.feature.advancement.ModAdvancementHost;
import art.arcane.adapt.mod.feature.ai.ModGoals;
import art.arcane.adapt.mod.feature.display.ModDisplays;
import art.arcane.adapt.mod.feature.fx.ModFxHost;
import art.arcane.adapt.mod.feature.menu.ModMenuHost;
import art.arcane.adapt.mod.feature.protection.ModProtectionHost;
import art.arcane.adapt.mod.feature.recipe.ModRecipeHost;
import art.arcane.adapt.mod.feature.viewer.ModViewerHost;
import art.arcane.adapt.mod.platform.ModHosts;
import net.minecraft.server.MinecraftServer;

/** Installs the vanilla-backed feature hosts into {@link ModHosts}. Call once from the mod constructor. */
public final class ModFeatures {
    private static boolean installed;

    private ModFeatures() {
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        ModMenuHost menus = new ModMenuHost();
        ModHosts.fx(new ModFxHost());
        ModHosts.menus(menus);
        ModHosts.viewers(new ModViewerHost());
        ModHosts.protection(new ModProtectionHost());
        ModHosts.recipes(new ModRecipeHost());
        ModHosts.advancements(new ModAdvancementHost());
        ModHosts.displays(new ModDisplays());
        ModHosts.goals(new ModGoals());
        ModHosts.onServerStopping((MinecraftServer server) -> menus.closeAll());
    }
}
