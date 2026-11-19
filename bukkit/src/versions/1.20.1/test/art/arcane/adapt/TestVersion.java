package art.arcane.adapt;

import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Registry;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Test setup on Paper 1.20.1: the registry constants come from the server, blocks, items and sounds are enum constants
 * without registries of their own, and plugin keys take their namespace from the plugin name.
 */
public final class TestVersion {
  public static final EntityType ITEM_ENTITY = EntityType.DROPPED_ITEM;

  private TestVersion() {
  }

  /** Builds the registry constants against a mocked server registry lookup, so they exist without a server. */
  public static void initializeRegistries() {
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(() -> Bukkit.getRegistry(any())).thenAnswer(call -> mock(Registry.class, RETURNS_DEEP_STUBS));
      if (Registry.SOUNDS == null) {
        throw new IllegalStateException("Registry constants did not initialize");
      }
    }
  }

  public static void mockBlockRegistry() {
    initializeRegistries();
  }

  public static void mockBlockAndItemRegistries() {
    initializeRegistries();
  }

  public static void mockSoundRegistry() {
    initializeRegistries();
  }

  /** Inventory types are a plain enum here and need no registry. */
  public static void initializeInventoryTypes() {
  }

  /** Makes keys created for the mocked plugin use the given namespace. */
  public static void pluginNamespace(Plugin plugin, String namespace) {
    lenient().when(plugin.getName()).thenReturn(namespace);
  }

  /** The bundled advancement library reads the version and the server implementation package when its classes load. */
  public static void advancementServer(MockedStatic<Bukkit> bukkit) {
    Server server = mock(Server.class);
    bukkit.when(Bukkit::getBukkitVersion).thenReturn("1.20.1-R0.1-SNAPSHOT");
    bukkit.when(Bukkit::getServer).thenReturn(server);
  }

  /** Answers the world's spawn of the given type at the given place; this server hands spawns a Bukkit consumer. */
  public static <T extends Entity> void answerSpawn(World world, Location at, Class<T> type, Spawner<T> spawner) {
    when(world.spawn(same(at), same(type), ArgumentMatchers.<org.bukkit.util.Consumer<T>>any()))
        .thenAnswer(invocation -> {
          org.bukkit.util.Consumer<T> configurator = invocation.getArgument(2);
          return spawner.spawn(configurator::accept);
        });
  }

  @FunctionalInterface
  public interface Spawner<T> {
    T spawn(Consumer<? super T> configurator) throws Exception;
  }

  /** Item stacks expose their persistent data only through the item meta here. */
  public static void stackData(ItemStack stack, PersistentDataContainer data) {
  }

  /** Makes the entity behave as if the server had no teleport flags; here flagged teleports are synchronous. */
  public static void withoutTeleportFlags(Entity entity) {
    lenient().when(entity.teleport(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class), any(TeleportFlag.class)))
        .thenThrow(new NoSuchMethodError("teleport flags absent"));
  }
}
