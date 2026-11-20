package art.arcane.adapt.gameplay.fabric;

import art.arcane.adapt.gameplay.mod.ModFixtureCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;

public final class AdaptGameplayFixtureFabric implements ModInitializer {
    private final ModFixtureCommand command = new ModFixtureCommand("fabric");
    private final RecipeSyncProbe recipeSync = new RecipeSyncProbe();

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> command.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> recipeSync.register(dispatcher));
        recipeSync.install();
        Identifier beforeAdapt = Identifier.fromNamespaceAndPath("adaptqa", "before_adapt");
        ServerLifecycleEvents.SERVER_STOPPING.addPhaseOrdering(beforeAdapt, Event.DEFAULT_PHASE);
        ServerLifecycleEvents.SERVER_STOPPING.register(beforeAdapt, command::serverStopping);
    }
}
