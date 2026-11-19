package art.arcane.adapt;

import io.papermc.paper.entity.TeleportFlag;
import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** Test setup whose server API differs between Paper versions. */
public final class TestVersion {
  public static final EntityType ITEM_ENTITY = EntityType.ITEM;

  private TestVersion() {
  }

  /** Builds the registry constants against a mocked registry access, so they exist without a server. */
  public static void initializeRegistries() {
    RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
    try (MockedStatic<RegistryAccess> registryAccess = mockStatic(RegistryAccess.class)) {
      registryAccess.when(RegistryAccess::registryAccess).thenReturn(access);
      if (Registry.SOUNDS == null) {
        throw new IllegalStateException("Registry constants did not initialize");
      }
    }
  }

  /** Initializes the block registry so every block lookup answers a fresh mock. */
  public static void mockBlockRegistry() {
    RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
    try (MockedStatic<RegistryAccess> registryAccess = mockStatic(RegistryAccess.class)) {
      registryAccess.when(RegistryAccess::registryAccess).thenReturn(access);
      Registry<BlockType> blocks = Registry.BLOCK;
      doAnswer(call -> mock(BlockType.Typed.class)).when(blocks).get(any(NamespacedKey.class));
      doAnswer(call -> mock(BlockType.Typed.class)).when(blocks).getOrThrow(any(Key.class));
    }
  }

  /** Initializes the block and item registries so every lookup answers a fresh mock. */
  public static void mockBlockAndItemRegistries() {
    RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
    try (MockedStatic<RegistryAccess> registryAccess = mockStatic(RegistryAccess.class)) {
      registryAccess.when(RegistryAccess::registryAccess).thenReturn(access);
      Registry<ItemType> items = Registry.ITEM;
      Registry<BlockType> blocks = Registry.BLOCK;
      doAnswer(call -> mock(BlockType.Typed.class)).when(blocks).get(any(NamespacedKey.class));
      doAnswer(call -> mock(BlockType.Typed.class)).when(blocks).getOrThrow(any(Key.class));
      doAnswer(call -> mock(ItemType.Typed.class)).when(items).get(any(NamespacedKey.class));
      doAnswer(call -> mock(ItemType.Typed.class)).when(items).getOrThrow(any(Key.class));
    }
  }

  /** Initializes the sound registry so every sound lookup answers a fresh mock. */
  public static void mockSoundRegistry() {
    RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
    try (MockedStatic<RegistryAccess> registryAccess = mockStatic(RegistryAccess.class)) {
      registryAccess.when(RegistryAccess::registryAccess).thenReturn(access);
      Registry<Sound> sounds = Registry.SOUNDS;
      doAnswer(call -> mock(Sound.class)).when(sounds).getOrThrow(any(Key.class));
      if (Sound.BLOCK_DEEPSLATE_BREAK == null) {
        throw new IllegalStateException("Sound constants did not initialize");
      }
    }
  }

  /** Makes keys created for the mocked plugin use the given namespace. */
  public static void pluginNamespace(Plugin plugin, String namespace) {
    lenient().when(plugin.namespace()).thenReturn(namespace);
  }

  /** Stubs what the advancement library reads from the server when its classes load. */
  public static void advancementServer(MockedStatic<Bukkit> bukkit) {
    bukkit.when(Bukkit::getBukkitVersion).thenReturn("26.3-R0.1-SNAPSHOT");
  }

  /** Answers the world's spawn of the given type at the given place, handing the spawner the configurator it was given. */
  public static <T extends Entity> void answerSpawn(World world, Location at, Class<T> type, Spawner<T> spawner) {
    when(world.spawn(same(at), same(type), ArgumentMatchers.<Consumer<? super T>>any()))
        .thenAnswer(invocation -> spawner.spawn(invocation.getArgument(2)));
  }

  @FunctionalInterface
  public interface Spawner<T> {
    T spawn(Consumer<? super T> configurator) throws Exception;
  }

  /** Makes the stack's own persistent data view answer the given container. */
  public static void stackData(ItemStack stack, PersistentDataContainer data) {
    when(stack.getPersistentDataContainer()).thenReturn(data);
  }

  /** Makes the entity behave as if the server had no teleport flags. */
  public static void withoutTeleportFlags(Entity entity) {
    lenient().when(entity.teleportAsync(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class), any(TeleportFlag.class)))
        .thenThrow(new NoSuchMethodError("teleport flags absent"));
  }
}
