package art.arcane.adapt.mod.event;

import art.arcane.adapt.mod.platform.Compat;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.event.player.PlayerLootGenerateEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.Vec3;

/** Container loot rolled for a player, posted as Paper posts LootGenerateEvent. */
public final class LootHooks {
    private LootHooks() {
    }

    /** A loot table is about to fill a container; only rolls a player triggered by opening it are posted. */
    public static void filling(LootParams params) {
        if (!ModEvents.listening(PlayerLootGenerateEvent.class)) {
            return;
        }
        Entity opener = Compat.lootEntity(params);
        Vec3 origin = Compat.lootOrigin(params);
        if (!(opener instanceof ServerPlayer player) || origin == null) {
            return;
        }
        ModEvents.post(new PlayerLootGenerateEvent(ModRefs.player(player),
            ModEvents.loc(player, origin.x, origin.y, origin.z, 0F, 0F)));
    }
}
