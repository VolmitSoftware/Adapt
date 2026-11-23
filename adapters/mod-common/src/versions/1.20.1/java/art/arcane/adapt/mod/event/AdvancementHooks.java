package art.arcane.adapt.mod.event;

import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.event.player.PlayerAdvancementDoneEvent;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.FrameType;
import net.minecraft.server.level.ServerPlayer;

/** Vanilla advancement completion, posted as Paper posts PlayerAdvancementDoneEvent. */
public final class AdvancementHooks {
    private AdvancementHooks() {
    }

    /** A player just completed the advancement; called once, where vanilla grants its rewards. */
    public static void completed(ServerPlayer player, Advancement advancement) {
        if (!ModEvents.listening(PlayerAdvancementDoneEvent.class)) {
            return;
        }
        DisplayInfo display = advancement.getDisplay();
        AdvancementFrame frame = display == null ? null : frame(display.getFrame());
        ModEvents.post(new PlayerAdvancementDoneEvent(ModRefs.player(player), ModRefs.id(advancement.getId()), frame));
    }

    private static AdvancementFrame frame(FrameType type) {
        return switch (type) {
            case GOAL -> AdvancementFrame.GOAL;
            case CHALLENGE -> AdvancementFrame.CHALLENGE;
            case TASK -> AdvancementFrame.TASK;
        };
    }
}
