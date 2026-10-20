package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.minion.MinionBurden;
import art.arcane.adapt.api.minion.MinionBurdens;
import art.arcane.adapt.api.minion.ServantOrder;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.entity.StackExclusion;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TragoulSkeletalServantBindingTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000005a1");
  private static final NamespacedKey SERVANT_KEY = NamespacedKey.fromString("adapt:tragoul_servant_owner");
  private static final long LIFETIME_MILLIS = 60_000L;
  private static final int LIFETIME_TICKS = 1_200;

  private final List<Runnable> timers = new ArrayList<>();
  private final List<Runnable> immediate = new ArrayList<>();
  private final World world = mock(World.class);
  private final Player owner = mock(Player.class);
  private MockedStatic<J> scheduling;
  private MockedStatic<Bukkit> bukkit;
  private MockedStatic<StackExclusion> stacks;
  private MockedStatic<AdaptAttributeService> attributes;
  private MinionBurden burden;
  private TragoulSkeletalServant adaptation;

  @BeforeEach
  void construct() throws ReflectiveOperationException {
    burden = MinionBurdens.install();
    scheduling = mockStatic(J.class);
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class), anyInt())).thenAnswer(invocation -> timers.add(invocation.getArgument(1)));
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class))).thenAnswer(invocation -> immediate.add(invocation.getArgument(1)));
    bukkit = mockStatic(Bukkit.class);
    stacks = mockStatic(StackExclusion.class);
    attributes = mockStatic(AdaptAttributeService.class);
    attributes.when(AdaptAttributeService::get).thenReturn(mock(AdaptAttributeService.class));
    lenient().when(owner.getUniqueId()).thenReturn(OWNER);
    adaptation = spy(new TragoulSkeletalServant());
    lenient().doNothing().when(adaptation).crumbleFx(any(Skeleton.class));
  }

  @AfterEach
  void close() throws ReflectiveOperationException {
    attributes.close();
    stacks.close();
    bukkit.close();
    scheduling.close();
    MinionBurdens.uninstall();
  }

  @Test
  void aSurrenderedServantLeavesTimedTrackingFreesItsCapSlotAndDropsItsSummonCost() {
    Skeleton servant = servant(OWNER);
    adaptation.track(owner, servant, System.currentTimeMillis() + LIFETIME_MILLIS, LIFETIME_TICKS);
    assertThat(adaptation.servants(OWNER)).containsExactly(servant);
    assertThat(adaptation.expiresAt(servant)).isPositive();
    assertThat(burden.activeCount(OWNER)).isEqualTo(1);

    assertThat(adaptation.surrender(servant)).isTrue();

    assertThat(adaptation.servants(OWNER)).isEmpty();
    assertThat(adaptation.expiresAt(servant)).isZero();
    assertThat(adaptation.order(servant)).isEqualTo(ServantOrder.FREE);
    assertThat(TragoulSkeletalServant.isServant(servant)).isFalse();
    assertThat(burden.activeCount(OWNER)).isZero();
    assertThat(adaptation.surrender(servant)).isFalse();
  }

  @Test
  void aSurrenderedServantOutlivesItsTimerWhileATrackedOneCrumblesWhenItsTimeRunsOut() {
    Skeleton kept = servant(OWNER);
    Skeleton expiring = servant(OWNER);
    adaptation.track(owner, kept, System.currentTimeMillis() + LIFETIME_MILLIS, LIFETIME_TICKS);
    adaptation.track(owner, expiring, System.currentTimeMillis() + LIFETIME_MILLIS, LIFETIME_TICKS);
    adaptation.surrender(kept);

    for (Runnable timer : List.copyOf(timers)) {
      timer.run();
    }

    verify(kept, never()).remove();
    verify(expiring).remove();
  }

  @Test
  void anOwnersLogoutDismissesTrackedServantsButNotASurrenderedOne() {
    Skeleton kept = servant(OWNER);
    Skeleton tracked = servant(OWNER);
    adaptation.track(owner, kept, System.currentTimeMillis() + LIFETIME_MILLIS, LIFETIME_TICKS);
    adaptation.track(owner, tracked, System.currentTimeMillis() + LIFETIME_MILLIS, LIFETIME_TICKS);
    adaptation.surrender(kept);

    adaptation.on(new PlayerQuitEvent(owner, (Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED));
    for (Runnable task : List.copyOf(immediate)) {
      task.run();
    }

    verify(kept, never()).remove();
    verify(tracked).remove();
  }

  @Test
  void onlyASkeletalServantCanBeSurrendered() {
    Skeleton wild = skeleton(Map.of());

    assertThat(adaptation.surrender(wild)).isFalse();
  }

  @Test
  void crumblingRemovesTheBodyWithTheServantRemovalEffect() {
    Skeleton bound = skeleton(Map.of());

    adaptation.crumble(bound);

    verify(adaptation).crumbleFx(bound);
    verify(bound).remove();
  }

  @Test
  void loadingTheConfigurationSetsTheHealthCostEveryLivingServantCarries() throws ReflectiveOperationException {
    assertThat(adaptation.reloadConfigSnapshot("healthCostPerMinion = 3.5\n", dataFolder.toPath().resolve("cost.toml").toFile(), false)).isTrue();
    assertThat(MinionBurdens.healthPerMinion(burden)).isEqualTo(3.5D);

    assertThat(adaptation.reloadConfigSnapshot("healthCostEnabled = false\n", dataFolder.toPath().resolve("off.toml").toFile(), false)).isTrue();
    assertThat(MinionBurdens.healthPerMinion(burden)).isZero();
  }

  private Skeleton servant(UUID ownerId) {
    Map<NamespacedKey, Object> values = new HashMap<>();
    values.put(SERVANT_KEY, ownerId.toString());
    return skeleton(values);
  }

  private Skeleton skeleton(Map<NamespacedKey, Object> initial) {
    Skeleton skeleton = mock(Skeleton.class);
    Map<NamespacedKey, Object> values = new HashMap<>(initial);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    lenient().when(data.has(any(NamespacedKey.class), any(PersistentDataType.class)))
        .thenAnswer(invocation -> values.containsKey(invocation.<NamespacedKey>getArgument(0)));
    lenient().when(data.get(any(NamespacedKey.class), any(PersistentDataType.class)))
        .thenAnswer(invocation -> values.get(invocation.<NamespacedKey>getArgument(0)));
    lenient().doAnswer(invocation -> values.remove(invocation.<NamespacedKey>getArgument(0))).when(data).remove(any(NamespacedKey.class));
    lenient().when(skeleton.getPersistentDataContainer()).thenReturn(data);
    lenient().when(skeleton.getUniqueId()).thenReturn(UUID.randomUUID());
    lenient().when(skeleton.isValid()).thenReturn(true);
    lenient().when(skeleton.getLocation()).thenReturn(new Location(world, 0D, 64D, 0D));
    return skeleton;
  }
}
