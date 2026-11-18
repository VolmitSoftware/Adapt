package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.server.ServiceChangeEvent;
import art.arcane.adapt.platform.event.world.ChunkEvent;
import art.arcane.adapt.platform.value.ChunkPos;
import art.arcane.adapt.platform.world.WorldRef;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorldTranslationsTest {
  private final List<Throwable> failures = new ArrayList<>();
  private final AdaptEventBus bus = new AdaptEventBus((owner, event, error) -> failures.add(error));
  private GapRefs refs;

  @BeforeEach
  void setUp() {
    refs = new GapRefs();
  }

  @AfterEach
  void tearDown() {
    refs.close();
    assertThat(failures).isEmpty();
  }

  @Test
  void chunkLoadCarriesPositionAndNewFlag() {
    World world = mock(World.class);
    Chunk chunk = mock(Chunk.class);
    when(chunk.getWorld()).thenReturn(world);
    when(chunk.getX()).thenReturn(4);
    when(chunk.getZ()).thenReturn(-7);
    AtomicReference<ChunkEvent> seen = new AtomicReference<>();
    bus.register(this, ChunkEvent.class, EventPriority.MONITOR, seen::set);

    WorldTranslations.CHUNK_LOAD.bridge(new org.bukkit.event.world.ChunkLoadEvent(chunk, true),
        core -> bus.post(core, EventPriority.MONITOR));

    assertThat(seen.get().world()).isSameAs(refs.wrap(world, WorldRef.class));
    assertThat(seen.get().chunk()).isEqualTo(new ChunkPos(4, -7));
    assertThat(seen.get().phase()).isEqualTo(ChunkEvent.Phase.LOAD);
    assertThat(seen.get().newChunk()).isTrue();
  }

  @Test
  void serviceRegistrationReportsTheServiceClassAndOwner() {
    Plugin owner = mock(Plugin.class);
    when(owner.getName()).thenReturn("Gloss");
    RegisteredServiceProvider<Runnable> provider = new RegisteredServiceProvider<>(Runnable.class, () -> {
    }, ServicePriority.Normal, owner);
    org.bukkit.event.server.ServiceRegisterEvent event = mock(org.bukkit.event.server.ServiceRegisterEvent.class);
    doReturn(provider).when(event).getProvider();
    AtomicReference<ServiceChangeEvent> seen = new AtomicReference<>();
    bus.register(this, ServiceChangeEvent.class, EventPriority.MONITOR, seen::set);

    WorldTranslations.SERVICE_REGISTER.bridge(event,
        core -> bus.post(core, EventPriority.MONITOR));

    assertThat(seen.get().service()).isEqualTo(Runnable.class);
    assertThat(seen.get().plugin()).isEqualTo("Gloss");
    assertThat(seen.get().registered()).isTrue();
  }

  @Test
  void everyTranslationHasItsOwnNativeClassAndAHandlerList() throws ReflectiveOperationException {
    Set<Class<?>> seen = new HashSet<>();
    for (Translation<?> translation : BukkitTranslations.all()) {
      assertThat(seen.add(translation.nativeType())).as(translation.nativeType().getName()).isTrue();
      assertThat(handlerList(translation.nativeType())).as(translation.nativeType().getName()).isNotNull();
      assertThat(translation.posted()).isNotEmpty();
    }
  }

  private static HandlerList handlerList(Class<?> type) throws ReflectiveOperationException {
    Class<?> current = type;
    while (current != null && Event.class.isAssignableFrom(current)) {
      try {
        Method method = current.getDeclaredMethod("getHandlerList");
        method.setAccessible(true);
        return (HandlerList) method.invoke(null);
      } catch (NoSuchMethodException missing) {
        current = current.getSuperclass();
      }
    }
    return null;
  }
}
