package com.volmit.adapt.api.advancement;

import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementFrameType;

public enum AdaptAdvancementFrame {
    TASK,
    GOAL,
    CHALLENGE;

    public AdvancementFrameType toUaaFrame() {
        return switch (this) {
            case GOAL -> AdvancementFrameType.GOAL;
            case CHALLENGE -> AdvancementFrameType.CHALLENGE;
            case TASK -> AdvancementFrameType.TASK;
        };
    }
}
