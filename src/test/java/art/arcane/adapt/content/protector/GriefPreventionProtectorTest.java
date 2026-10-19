package art.arcane.adapt.content.protector;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Squid;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GriefPreventionProtectorTest {
  private final GriefPreventionProtector protector = new GriefPreventionProtector();
  private final Player player = mock(Player.class);
  private final World world = mock(World.class);
  private final Location claimed = new Location(world, 10, 64, 10);
  private final Location wild = new Location(world, 500, 64, 500);
  private GriefPrevention previous;
  private GriefPrevention plugin;
  private Claim claim;

  @BeforeEach
  void setUp() {
    previous = GriefPrevention.instance;
    plugin = mock(GriefPrevention.class);
    DataStore store = mock(DataStore.class);
    claim = mock(Claim.class);
    plugin.dataStore = store;
    plugin.config_claims_protectCreatures = true;
    lenient().when(plugin.namespace()).thenReturn("griefprevention");
    lenient().when(store.getClaimAt(eq(claimed), eq(true), any())).thenReturn(claim);
    lenient().when(store.getClaimAt(eq(wild), eq(true), any())).thenReturn(null);
    lenient().when(claim.allowContainers(player)).thenReturn("no container trust");
    lenient().when(claim.allowAccess(player)).thenReturn("no access trust");
    GriefPrevention.instance = plugin;
  }

  @AfterEach
  void tearDown() {
    GriefPrevention.instance = previous;
  }

  @Test
  void containerTrustGatesChestsInsideAClaim() {
    assertThat(protector.canAccessChest(player, claimed, null)).isFalse();

    when(claim.allowContainers(player)).thenReturn(null);

    assertThat(protector.canAccessChest(player, claimed, null)).isTrue();
  }

  @Test
  void aMonsterInsideAClaimIsDamageableWithoutAnyTrust() {
    Zombie zombie = entity(Zombie.class, claimed);

    assertThat(protector.canPVE(player, zombie, null)).isTrue();
  }

  @Test
  void aCreatureInsideAClaimNeedsContainerTrustWhileCreaturesAreProtected() {
    Cow cow = entity(Cow.class, claimed);
    Squid squid = entity(Squid.class, claimed);

    assertThat(protector.canPVE(player, cow, null)).isFalse();
    assertThat(protector.canPVE(player, squid, null)).isFalse();

    when(claim.allowContainers(player)).thenReturn(null);

    assertThat(protector.canPVE(player, cow, null)).isTrue();
    assertThat(protector.canPVE(player, squid, null)).isTrue();
  }

  @Test
  void creaturesAreUnprotectedWhenGriefPreventionDoesNotProtectThem() {
    plugin.config_claims_protectCreatures = false;

    assertThat(protector.canPVE(player, entity(Cow.class, claimed), null)).isTrue();
    assertThat(protector.canAccessChest(player, claimed, null)).isFalse();
  }

  @Test
  void hostileAnimalsCountAsMonstersTheWayGriefPreventionJudgesThem() {
    Rabbit killer = entity(Rabbit.class, claimed);
    Rabbit bunny = entity(Rabbit.class, claimed);
    when(killer.getRabbitType()).thenReturn(Rabbit.Type.THE_KILLER_BUNNY);
    when(bunny.getRabbitType()).thenReturn(Rabbit.Type.BROWN);
    Hoglin hunting = hoglin(true, false);
    Hoglin lured = hoglin(true, true);
    Hoglin idle = hoglin(false, false);

    assertThat(protector.canPVE(player, killer, null)).isTrue();
    assertThat(protector.canPVE(player, bunny, null)).isFalse();
    assertThat(protector.canPVE(player, hunting, null)).isTrue();
    assertThat(protector.canPVE(player, lured, null)).isFalse();
    assertThat(protector.canPVE(player, idle, null)).isFalse();
  }

  @Test
  void locationOnlyDamageChecksAndInteractionsAreNotGatedByClaimTrust() {
    assertThat(protector.canPVE(player, claimed, null)).isTrue();
    assertThat(protector.canInteract(player, claimed, null)).isTrue();
  }

  @Test
  void unclaimedLandAllowsEverything() {
    assertThat(protector.canInteract(player, wild, null)).isTrue();
    assertThat(protector.canAccessChest(player, wild, null)).isTrue();
    assertThat(protector.canPVE(player, entity(Cow.class, wild), null)).isTrue();
  }

  private Hoglin hoglin(boolean targeting, boolean luredByPlayer) {
    Hoglin hoglin = entity(Hoglin.class, claimed);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    lenient().when(hoglin.getPersistentDataContainer()).thenReturn(data);
    lenient().when(data.has(any(NamespacedKey.class), eq(PersistentDataType.BYTE))).thenReturn(luredByPlayer);
    lenient().when(hoglin.getTarget()).thenReturn(targeting ? player : null);
    return hoglin;
  }

  private static <T extends Entity> T entity(Class<T> type, Location at) {
    T entity = mock(type);
    lenient().when(entity.getLocation()).thenReturn(at);
    return entity;
  }
}
