package com.fren_gor.ultimateAdvancementAPI.nms.v1_21_R7.advancement;

import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.advancement.AdvancementFrameTypeWrapper;
import net.minecraft.advancements.AdvancementType;
import org.jetbrains.annotations.NotNull;

public class AdvancementFrameTypeWrapper_v1_21_R7 extends AdvancementFrameTypeWrapper {
    private final AdvancementType mcFrameType;
    private final FrameType frameType;

    public AdvancementFrameTypeWrapper_v1_21_R7(@NotNull FrameType frameType) {
        this.frameType = frameType;
        this.mcFrameType = switch (frameType) {
            case TASK -> AdvancementType.TASK;
            case GOAL -> AdvancementType.GOAL;
            case CHALLENGE -> AdvancementType.CHALLENGE;
        };
    }

    @Override
    @NotNull
    public FrameType getFrameType() {
        return frameType;
    }

    @Override
    @NotNull
    public AdvancementType toNMS() {
        return mcFrameType;
    }
}
