package art.arcane.adapt.mod.platform;

import art.arcane.adapt.engine.adaptation.EntityGuardPort;
import art.arcane.adapt.engine.minion.MinionIdentity;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

/** Vanilla entity guards: only the plain {@link ServerPlayer} class is a real player; fake player classes count as NPCs. */
public final class ModEntityGuardPort implements EntityGuardPort {
    @Override
    public boolean isServerPlayer(PlayerRef player) {
        return player != null && ModRefs.player(player).getClass() == ServerPlayer.class;
    }

    @Override
    public boolean isShielded(EntityRef entity) {
        Entity target = ModRefs.entity(entity);
        if (target == null) {
            return false;
        }
        if (target instanceof ArmorStand stand && stand.isMarker()) {
            return true;
        }
        if (target instanceof ServerPlayer player && player.getClass() != ServerPlayer.class) {
            return true;
        }
        return MinionIdentity.isMinion(entity);
    }
}
