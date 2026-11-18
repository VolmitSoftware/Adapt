package com.fren_gor.ultimateAdvancementAPI;

import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
import com.fren_gor.ultimateAdvancementAPI.database.TeamProgression;
import com.fren_gor.ultimateAdvancementAPI.util.AdvancementKey;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdvancementTabUnloadedTeamUpdateTest {
    @Test
    @SuppressWarnings("unchecked")
    void pendingUpdateSkipsATeamThatUnloadedBeforeItRan() throws ReflectiveOperationException {
        AdvancementTab tab = mock(AdvancementTab.class);
        Advancement advancement = mock(Advancement.class);
        Field advancementsField = AdvancementTab.class.getDeclaredField("advancements");
        advancementsField.setAccessible(true);
        advancementsField.set(tab, new java.util.HashMap<>(Map.of(mock(AdvancementKey.class), advancement)));

        TeamProgression unloaded = mock(TeamProgression.class);
        when(unloaded.isValid()).thenReturn(false);

        Class<?> runnableType = Class.forName(AdvancementTab.class.getName() + "$AdvsUpdateRunnable");
        Constructor<?> constructor = runnableType.getDeclaredConstructor(AdvancementTab.class);
        constructor.setAccessible(true);
        Runnable update = (Runnable) constructor.newInstance(tab);
        Field pending = runnableType.getDeclaredField("advsToUpdate");
        pending.setAccessible(true);
        ((Set<TeamProgression>) pending.get(update)).add(unloaded);
        Field scheduled = runnableType.getDeclaredField("scheduled");
        scheduled.setAccessible(true);
        scheduled.setBoolean(update, true);

        assertThatCode(update::run).doesNotThrowAnyException();
        verify(advancement, never()).onUpdate(eq(unloaded), any());
    }
}
