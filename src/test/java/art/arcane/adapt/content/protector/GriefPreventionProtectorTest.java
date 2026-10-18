package art.arcane.adapt.content.protector;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
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
    lenient().when(store.getClaimAt(eq(claimed), eq(true), any())).thenReturn(claim);
    lenient().when(store.getClaimAt(eq(wild), eq(true), any())).thenReturn(null);
    GriefPrevention.instance = plugin;
  }

  @AfterEach
  void tearDown() {
    GriefPrevention.instance = previous;
  }

  @Test
  void containerTrustGatesChestsAndCreaturesInsideAClaim() {
    when(claim.allowContainers(player)).thenReturn("no container trust");

    assertThat(protector.canAccessChest(player, claimed, null)).isFalse();
    assertThat(protector.canPVE(player, claimed, null)).isFalse();

    when(claim.allowContainers(player)).thenReturn(null);

    assertThat(protector.canAccessChest(player, claimed, null)).isTrue();
    assertThat(protector.canPVE(player, claimed, null)).isTrue();
  }

  @Test
  void accessTrustGatesInteractionsInsideAClaim() {
    when(claim.allowAccess(player)).thenReturn("no access trust");
    assertThat(protector.canInteract(player, claimed, null)).isFalse();

    when(claim.allowAccess(player)).thenReturn(null);
    assertThat(protector.canInteract(player, claimed, null)).isTrue();
  }

  @Test
  void unclaimedLandAllowsEverything() {
    assertThat(protector.canInteract(player, wild, null)).isTrue();
    assertThat(protector.canAccessChest(player, wild, null)).isTrue();
    assertThat(protector.canPVE(player, wild, null)).isTrue();
  }

  @Test
  void creaturesAreUnprotectedWhenGriefPreventionDoesNotProtectThem() {
    plugin.config_claims_protectCreatures = false;
    when(claim.allowContainers(player)).thenReturn("no container trust");

    assertThat(protector.canPVE(player, claimed, null)).isTrue();
    assertThat(protector.canAccessChest(player, claimed, null)).isFalse();
  }
}
