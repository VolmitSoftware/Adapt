package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.volmlib.util.event.ProtectionProbe;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class BukkitEventBridgeProtectionProbeTest {
  private final List<Throwable> failures = new ArrayList<>();
  private final AdaptEventBus bus = new AdaptEventBus((owner, event, error) -> failures.add(error));
  private final List<String> seen = new ArrayList<>();
  private Plugin adapt;
  private BukkitEventBridge bridge;

  @BeforeEach
  void setUp() {
    adapt = mock(Plugin.class);
    lenient().when(adapt.isEnabled()).thenReturn(true);
    lenient().when(adapt.getName()).thenReturn("Adapt");
    bridge = new BukkitEventBridge(adapt, bus, List.of(
        new Probe<>(PlayerInteractEvent.class, "interact"),
        new Probe<>(PlayerInteractEntityEvent.class, "entity"),
        new Probe<>(BlockBreakEvent.class, "break"),
        new Probe<>(EntityPickupItemEvent.class, "pickup")));
    bridge.start();
    bus.register(this, Seen.class, EventPriority.NORMAL, false,
        event -> seen.add(event.protectionProbe() ? event.kind + ":probe" : event.kind));
  }

  @AfterEach
  void tearDown() {
    bridge.stop();
    HandlerList.unregisterAll(adapt);
    assertThat(failures).isEmpty();
  }

  @Test
  @DisplayName("protection probes are posted marked, so only component handlers skip them")
  void protectionProbesArePostedMarked() {
    PlayerInteractEvent event = new PlayerInteractEvent(mock(Player.class), Action.RIGHT_CLICK_BLOCK, null,
        mock(Block.class), BlockFace.UP, EquipmentSlot.HAND);

    probe(event);

    assertThat(seen).containsExactly("interact:probe");
    fire(event);
    assertThat(seen).containsExactly("interact:probe", "interact");
  }

  @Test
  void externalProtectionProbesArePostedMarked() {
    PlayerInteractEvent event = new PlayerInteractEvent(mock(Player.class),
        Action.RIGHT_CLICK_BLOCK, null, mock(Block.class), BlockFace.UP, EquipmentSlot.HAND) {
      @Override
      public String getEventName() {
        return "VolmLibProtectionProbe";
      }
    };

    fire(event);

    assertThat(seen).containsExactly("interact:probe");
  }

  @Test
  void entityProtectionProbesArePostedMarked() {
    Player player = mock(Player.class);
    Entity entity = mock(Entity.class);

    fire(ProtectionProbe.entityInteract(player, entity));

    assertThat(seen).containsExactly("entity:probe");
    fire(new PlayerInteractEntityEvent(player, entity));
    assertThat(seen).containsExactly("entity:probe", "entity");
  }

  @Test
  @DisplayName("pickup protection probes are not marked and reach every handler")
  void pickupProtectionProbeReachesComponentHandlers() {
    EntityPickupItemEvent event = new EntityPickupItemEvent(mock(Player.class), mock(Item.class), 0);

    probe(event);

    assertThat(seen).containsExactly("pickup");
  }

  @Test
  @DisplayName("synthetic block authorization probes are posted marked")
  void blockProtectionProbesArePostedMarked() {
    BlockBreakEvent event = new BlockBreakEvent(mock(Block.class), mock(Player.class));

    probe(event);

    assertThat(seen).containsExactly("break:probe");
    fire(event);
    assertThat(seen).containsExactly("break:probe", "break");
  }

  private void probe(Event event) {
    PluginManager plugins = mock(PluginManager.class);
    doAnswer(invocation -> {
      fire(invocation.getArgument(0));
      return null;
    }).when(plugins).callEvent(any(Event.class));
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
      ProtectionEventProbe.dispatch(event);
    }
  }

  private void fire(Event event) {
    for (RegisteredListener listener : event.getHandlers().getRegisteredListeners()) {
      if (listener.getPlugin() != adapt) {
        continue;
      }
      try {
        listener.callEvent(event);
      } catch (Exception failure) {
        throw new IllegalStateException(failure);
      }
    }
  }

  static final class Seen extends AdaptEvent {
    final String kind;

    Seen(String kind) {
      this.kind = kind;
    }
  }

  private static final class Probe<B extends Event> extends Translation<B> {
    private final String kind;

    private Probe(Class<B> nativeType, String kind) {
      super(nativeType, List.of(Seen.class));
      this.kind = kind;
    }

    @Override
    public void bridge(B event, Poster poster) {
      poster.post(new Seen(kind));
    }
  }
}
