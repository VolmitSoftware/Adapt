package com.fren_gor.ultimateAdvancementAPI;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.fren_gor.ultimateAdvancementAPI.database.DatabaseManager;
import com.fren_gor.ultimateAdvancementAPI.database.IDatabase;
import com.fren_gor.ultimateAdvancementAPI.database.TeamProgression;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseManagerJoinLifecycleTest {
    private final UUID uuid = UUID.randomUUID();
    private final Map<UUID, TeamProgression> cache = new HashMap<>();
    private DatabaseManager manager;
    private AdvancementMain main;
    private Player player;
    private TeamProgression progression;
    private IDatabase database;

    @BeforeEach
    void setUp() throws Exception {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getBukkitVersion).thenReturn("26.3-R0.1-SNAPSHOT");
            manager = mock(DatabaseManager.class, CALLS_REAL_METHODS);
        }
        main = mock(AdvancementMain.class);
        player = mock(Player.class);
        progression = mock(TeamProgression.class);
        database = mock(IDatabase.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);
        when(progression.isValid()).thenReturn(true);
        when(database.getUnredeemed(0)).thenReturn(List.of());
        cache.put(uuid, progression);
        setField("main", main);
        setField("database", database);
        setField("progressionCache", cache);
    }

    @Test
    void queuedCompletionAfterQuitDoesNotEmitInvalidProgression() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(main.getOwningPlugin()).thenReturn(plugin);
        AtomicReference<Runnable> queued = new AtomicReference<>();
        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            scheduler.when(() -> FoliaScheduler.runEntity(eq(plugin), eq(player), any(Runnable.class), eq(3L)))
                    .thenAnswer(invocation -> {
                        queued.set(invocation.getArgument(2));
                        return true;
                    });
            Consumer<Player> action = active -> {
                try {
                    complete(active);
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            };
            invoke("scheduleJoinAction", new Class<?>[]{Player.class, Consumer.class}, player, action);
            assertThat(queued.get()).isNotNull();
            when(player.isOnline()).thenReturn(false);
            when(progression.isValid()).thenReturn(false);
            cache.clear();
            queued.get().run();
            verify(main, never()).updatePlayer(any(Player.class));
            bukkit.verifyNoInteractions();
        }
    }

    @Test
    void invalidatedProgressionCannotCompleteForOnlinePlayer() throws Exception {
        when(progression.isValid()).thenReturn(false);
        assertCompletionIgnored();
    }

    @Test
    void replacedProgressionCannotCompleteAfterReconnect() throws Exception {
        cache.put(uuid, mock(TeamProgression.class));
        assertCompletionIgnored();
    }

    @Test
    void removedProgressionCannotCompleteAfterDisconnect() throws Exception {
        cache.clear();
        assertCompletionIgnored();
    }

    @Test
    void validOnlineQueuedActionReceivesCapturedPlayer() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(main.getOwningPlugin()).thenReturn(plugin);
        AtomicReference<Runnable> queued = new AtomicReference<>();
        AtomicReference<Player> delivered = new AtomicReference<>();
        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.runEntity(eq(plugin), eq(player), any(Runnable.class), eq(3L)))
                    .thenAnswer(invocation -> {
                        queued.set(invocation.getArgument(2));
                        return true;
                    });
            Consumer<Player> action = delivered::set;
            invoke("scheduleJoinAction", new Class<?>[]{Player.class, Consumer.class}, player, action);
            assertThat(delivered.get()).isNull();
            queued.get().run();
            assertThat(delivered.get()).isSameAs(player);
        }
    }

    private void assertCompletionIgnored() throws Exception {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            complete(player);
            verify(main, never()).updatePlayer(any(Player.class));
            bukkit.verifyNoInteractions();
        }
    }

    private void complete(Player active) throws Exception {
        invoke("completePlayerLoad", new Class<?>[]{Player.class, TeamProgression.class, boolean.class}, active, progression, false);
    }

    private void invoke(String name, Class<?>[] parameters, Object... values) throws Exception {
        Method method = DatabaseManager.class.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        method.invoke(manager, values);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = DatabaseManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }
}
