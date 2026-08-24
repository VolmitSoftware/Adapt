package com.fren_gor.ultimateAdvancementAPI;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AdvancementTabFoliaSchedulerBindingTest {
    private static final String UPDATE_RUNNABLE_CLASS = "AdvancementTab$AdvsUpdateRunnable.class";
    private static final String PLATFORM_SCHEDULER = "com/fren_gor/ultimateAdvancementAPI/util/AdvancementUtils";
    private static final String LEGACY_SCHEDULER = "org/bukkit/scheduler/BukkitScheduler";

    @Test
    void advancementTreeUpdatesUseThePlatformScheduler() throws IOException {
        byte[] bytecode;
        try (InputStream resource = AdvancementTab.class.getResourceAsStream(UPDATE_RUNNABLE_CLASS)) {
            assertThat(resource).isNotNull();
            bytecode = resource.readAllBytes();
        }

        String constantPool = new String(bytecode, StandardCharsets.ISO_8859_1);
        assertThat(constantPool)
            .contains(PLATFORM_SCHEDULER)
            .doesNotContain(LEGACY_SCHEDULER);
    }
}
