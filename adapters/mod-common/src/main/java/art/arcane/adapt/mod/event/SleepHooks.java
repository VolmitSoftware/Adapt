package art.arcane.adapt.mod.event;

import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.world.NightSkipEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** The night skip, posted as Paper posts TimeSkipEvent with the NIGHT_SKIP reason. */
public final class SleepHooks {
    private SleepHooks() {
    }

    /** Enough players slept: vanilla is about to wake everyone in the level. */
    public static void nightSkipped(ServerLevel level) {
        if (!ModEvents.listening(NightSkipEvent.class)) {
            return;
        }
        List<PlayerRef> sleepers = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.isSleepingLongEnough()) {
                sleepers.add(ModRefs.player(player));
            }
        }
        ModEvents.post(new NightSkipEvent(ModRefs.world(level), sleepers));
    }
}
