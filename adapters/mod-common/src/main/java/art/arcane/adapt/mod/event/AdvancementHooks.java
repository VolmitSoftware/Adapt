package art.arcane.adapt.mod.event;

import art.arcane.adapt.mod.platform.Compat;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.event.player.PlayerAdvancementDoneEvent;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.server.level.ServerPlayer;

/** Vanilla advancement completion, posted as Paper posts PlayerAdvancementDoneEvent. */
public final class AdvancementHooks {
    private AdvancementHooks() {
    }

    /** A player just completed the advancement; called once, where vanilla grants its rewards. */
    public static void completed(ServerPlayer player, AdvancementHolder advancement) {
        if (!ModEvents.listening(PlayerAdvancementDoneEvent.class)) {
            return;
        }
        AdvancementFrame frame = advancement.value().display()
            .map(display -> frame(Compat.advancementType(display)))
            .orElse(null);
        ModEvents.post(new PlayerAdvancementDoneEvent(ModRefs.player(player), ModRefs.id(advancement.id()), frame));
    }

    private static AdvancementFrame frame(AdvancementType type) {
        return switch (type) {
            case GOAL -> AdvancementFrame.GOAL;
            case CHALLENGE -> AdvancementFrame.CHALLENGE;
            case TASK -> AdvancementFrame.TASK;
        };
    }
}
