package art.arcane.adapt.mod.feature.protection;

import art.arcane.adapt.api.protection.Protector;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.protection.ProtectionCheck;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * The modded server's own protection, registered as the protector {@code claims}: spawn protection, world border,
 * build height, adventure mode, the PVP rule and team friendly fire, and the loader's break event so claim mods can
 * veto a break. Admins switch it per adaptation by name like any protector.
 */
public final class ModClaimsProtector implements Protector {
    public static final String NAME = "claims";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    public boolean allows(ProtectionCheck check, PlayerRef actor, Loc location, @Nullable EntityRef victim,
                          @Nullable Id adaptation) {
        ServerPlayer player = ModRefs.player(actor);
        ServerLevel level = ModRefs.world(location.world());
        if (player == null || level == null) {
            return false;
        }
        BlockPos pos = BlockPos.containing(location.x(), location.y(), location.z());
        return switch (check) {
            case BLOCK_BREAK -> ModProtectionHost.mayModify(player, level, pos) && ModProtectionHost.probeBreak(player, level, pos);
            case BLOCK_PLACE -> ModProtectionHost.mayPlace(player, level, pos);
            case INTERACT, ACCESS_CHEST -> ModProtectionHost.mayUse(player, level, pos);
            case PVP -> !player.isSpectator() && harmsPlayer(player, level, victim);
            case PVE, REGION -> !player.isSpectator();
            default -> false;
        };
    }

    private static boolean harmsPlayer(ServerPlayer player, ServerLevel level, @Nullable EntityRef victim) {
        Entity target = victim == null ? null : ModRefs.entity(victim);
        if (target == player) {
            return true;
        }
        if (!level.getServer().isPvpAllowed()) {
            return false;
        }
        return !(target instanceof Player other) || player.canHarmPlayer(other);
    }
}
