package art.arcane.adapt;

import io.papermc.paper.entity.TeleportFlag;
import io.papermc.paper.registry.RegistryAccess;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.BlockType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.bukkit.inventory.MenuType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** Test setup on Paper 1.21.1: sounds are an enum, and plugin keys take their namespace from the plugin name. */
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
      answerNull(Registry.BLOCK);
      initialize(BlockType.class);
      answerMocks(Registry.BLOCK, BlockType.Typed.class);
    }
  }

  /** Initializes the block and item registries so every lookup answers a fresh mock. */
  public static void mockBlockAndItemRegistries() {
    RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
    try (MockedStatic<RegistryAccess> registryAccess = mockStatic(RegistryAccess.class)) {
      registryAccess.when(RegistryAccess::registryAccess).thenReturn(access);
      answerNull(Registry.ITEM);
      answerNull(Registry.BLOCK);
      initialize(ItemType.class);
      initialize(BlockType.class);
      answerMocks(Registry.ITEM, ItemType.Typed.class);
      answerMocks(Registry.BLOCK, BlockType.Typed.class);
    }
  }

  /** Initializes the inventory types, whose menu types are registry constants here. */
  public static void initializeInventoryTypes() {
    RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
    try (MockedStatic<RegistryAccess> registryAccess = mockStatic(RegistryAccess.class)) {
      registryAccess.when(RegistryAccess::registryAccess).thenReturn(access);
      answerNull(Registry.MENU);
      initialize(MenuType.class);
      initialize(InventoryType.class);
    }
  }

  /** The types look their own constants up while they initialize, before they can be mocked, so those resolve to null. */
  private static void answerNull(Registry<?> registry) {
    doReturn(null).when(registry).get(any(NamespacedKey.class));
    doReturn(null).when(registry).getOrThrow(any(NamespacedKey.class));
  }

  private static void initialize(Class<?> type) {
    try {
      Class.forName(type.getName(), true, type.getClassLoader());
    } catch (ClassNotFoundException missing) {
      throw new IllegalStateException(missing);
    }
  }

  private static void answerMocks(Registry<?> registry, Class<?> typed) {
    doAnswer(call -> mock(typed)).when(registry).get(any(NamespacedKey.class));
    doAnswer(call -> mock(typed)).when(registry).getOrThrow(any(NamespacedKey.class));
  }

  /** Sounds are enum constants here; only the registry constants need building. */
  public static void mockSoundRegistry() {
    initializeRegistries();
  }

  /** Makes keys created for the mocked plugin use the given namespace. */
  public static void pluginNamespace(Plugin plugin, String namespace) {
    lenient().when(plugin.getName()).thenReturn(namespace);
  }

  /** The bundled advancement library reads the version and the server implementation package when its classes load. */
  public static void advancementServer(MockedStatic<Bukkit> bukkit) {
    Server server = mock(Server.class);
    bukkit.when(Bukkit::getBukkitVersion).thenReturn("1.21.1-R0.1-SNAPSHOT");
    bukkit.when(Bukkit::getServer).thenReturn(server);
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
