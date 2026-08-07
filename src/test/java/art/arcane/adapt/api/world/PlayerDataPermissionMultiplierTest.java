package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerDataPermissionMultiplierTest extends AdaptTestBase {
    private static final String VIP = "adapt.xpmultiplier.vip";
    private static final String MVP = "adapt.xpmultiplier.mvp";
    private static final String ELITE = "adapt.xpmultiplier.elite";

    private AdaptConfig previousConfig;
    private AdaptConfig.PermissionXpMultipliers settings;
    private Player player;

    @BeforeEach
    void installConfigAndServer() throws Exception {
        Field configField = AdaptConfig.class.getDeclaredField("config");
        configField.setAccessible(true);
        previousConfig = (AdaptConfig) configField.get(null);

        AdaptConfig config = new AdaptConfig();
        settings = config.getPermissionXpMultipliers();
        configField.set(null, config);

        AdaptServer server = mock(AdaptServer.class);
        lenient().when(server.getData()).thenReturn(new AdaptServerData());
        lenient().when(plugin.getAdaptServer()).thenReturn(server);

        player = mock(Player.class);
    }

    @AfterEach
    void restoreConfig() throws Exception {
        Field configField = AdaptConfig.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(null, previousConfig);
    }

    @Test
    @DisplayName("a disabled feature leaves the additive boost math untouched")
    void disabledFeatureLeavesBoostMathUntouched() throws Exception {
        configure(false, Map.of(VIP, 2D));
        grant(VIP);
        PlayerData data = new PlayerData();
        data.globalXPMultiplier(0.5D, 60000);

        assertThat(data.computeXpMultiplier(player)).isEqualTo(1.5D);
    }

    @Test
    @DisplayName("an enabled feature without a matching node resolves to no change")
    void enabledFeatureWithoutMatchResolvesToNoChange() throws Exception {
        configure(true, Map.of(VIP, 2D));
        PlayerData data = new PlayerData();
        data.globalXPMultiplier(0.5D, 60000);

        assertThat(data.computeXpMultiplier(player)).isEqualTo(1.5D);
    }

    @Test
    @DisplayName("a matching node multiplies the boosted total")
    void matchingNodeMultipliesTheBoostedTotal() throws Exception {
        configure(true, Map.of(VIP, 1.5D));
        grant(VIP);
        PlayerData data = new PlayerData();
        data.globalXPMultiplier(0.5D, 60000);

        assertThat(data.computeXpMultiplier(player)).isEqualTo(2.25D);
    }

    @Test
    @DisplayName("the highest matching node wins over lower ranks")
    void highestMatchingNodeWins() throws Exception {
        Map<String, Double> nodes = new LinkedHashMap<>();
        nodes.put(VIP, 1.5D);
        nodes.put(MVP, 3D);
        nodes.put(ELITE, 2D);
        configure(true, nodes);
        grant(VIP);
        grant(MVP);
        grant(ELITE);
        PlayerData data = new PlayerData();

        assertThat(data.computeXpMultiplier(player)).isEqualTo(3D);
    }

    @Test
    @DisplayName("stacking is off by default so the highest node still wins")
    void stackingIsOffByDefault() throws Exception {
        Map<String, Double> nodes = new LinkedHashMap<>();
        nodes.put(VIP, 1.5D);
        nodes.put(MVP, 3D);
        nodes.put(ELITE, 2D);
        configure(true, nodes);
        grant(VIP);
        grant(MVP);
        grant(ELITE);
        PlayerData data = new PlayerData();

        assertThat(settings.isStack()).isFalse();
        assertThat(data.computeXpMultiplier(player)).isEqualTo(3D);
    }

    @Test
    @DisplayName("stacking multiplies every held node together")
    void stackingMultipliesEveryHeldNode() throws Exception {
        Map<String, Double> nodes = new LinkedHashMap<>();
        nodes.put(VIP, 1.5D);
        nodes.put(MVP, 3D);
        nodes.put(ELITE, 2D);
        configure(true, nodes);
        stack(true);
        grant(VIP);
        grant(MVP);
        grant(ELITE);
        PlayerData data = new PlayerData();

        assertThat(data.computeXpMultiplier(player)).isEqualTo(9D);
    }

    @Test
    @DisplayName("stacking only multiplies the nodes a player actually holds")
    void stackingOnlyMultipliesHeldNodes() throws Exception {
        Map<String, Double> nodes = new LinkedHashMap<>();
        nodes.put(VIP, 1.5D);
        nodes.put(MVP, 3D);
        nodes.put(ELITE, 2D);
        configure(true, nodes);
        stack(true);
        grant(VIP);
        grant(ELITE);
        PlayerData data = new PlayerData();

        assertThat(data.computeXpMultiplier(player)).isEqualTo(3D);
    }

    @Test
    @DisplayName("stacking with no held node resolves to no change")
    void stackingWithoutMatchResolvesToNoChange() throws Exception {
        configure(true, Map.of(VIP, 4D));
        stack(true);
        PlayerData data = new PlayerData();

        assertThat(data.computeXpMultiplier(player)).isEqualTo(1D);
    }

    @Test
    @DisplayName("stacking skips non positive node values")
    void stackingSkipsNonPositiveNodeValues() throws Exception {
        Map<String, Double> nodes = new LinkedHashMap<>();
        nodes.put(VIP, 0D);
        nodes.put(MVP, -4D);
        nodes.put(ELITE, 1.25D);
        configure(true, nodes);
        stack(true);
        grant(VIP);
        grant(MVP);
        grant(ELITE);
        PlayerData data = new PlayerData();

        assertThat(data.computeXpMultiplier(player)).isEqualTo(1.25D);
    }

    @Test
    @DisplayName("stacked penalty nodes compound below one")
    void stackedPenaltyNodesCompoundBelowOne() throws Exception {
        Map<String, Double> nodes = new LinkedHashMap<>();
        nodes.put(VIP, 0.5D);
        nodes.put(MVP, 0.5D);
        configure(true, nodes);
        stack(true);
        grant(VIP);
        grant(MVP);
        PlayerData data = new PlayerData();

        assertThat(data.computeXpMultiplier(player)).isEqualTo(0.25D);
    }

    @Test
    @DisplayName("non positive node values are ignored")
    void nonPositiveNodeValuesAreIgnored() throws Exception {
        Map<String, Double> nodes = new LinkedHashMap<>();
        nodes.put(VIP, 0D);
        nodes.put(MVP, -4D);
        configure(true, nodes);
        grant(VIP);
        grant(MVP);
        PlayerData data = new PlayerData();

        assertThat(data.computeXpMultiplier(player)).isEqualTo(1D);

        settings.getMultipliers().put(ELITE, 1.25D);
        grant(ELITE);

        assertThat(data.computeXpMultiplier(player)).isEqualTo(1.25D);
    }

    @Test
    @DisplayName("a matching penalty node lowers the multiplier below one")
    void matchingPenaltyNodeLowersTheMultiplier() throws Exception {
        configure(true, Map.of(VIP, 0.5D));
        grant(VIP);
        PlayerData data = new PlayerData();

        assertThat(data.computeXpMultiplier(player)).isEqualTo(0.5D);
    }

    @Test
    @DisplayName("an offline player resolves without a permission factor")
    void offlinePlayerResolvesWithoutPermissionFactor() throws Exception {
        configure(true, Map.of(VIP, 4D));
        PlayerData data = new PlayerData();
        data.globalXPMultiplier(0.25D, 60000);

        assertThat(data.computeXpMultiplier(null)).isEqualTo(1.25D);
    }

    @Test
    @DisplayName("the permission factor is clamped with the additive boosts")
    void permissionFactorIsClampedWithTheAdditiveBoosts() throws Exception {
        configure(true, Map.of(VIP, 4D));
        grant(VIP);
        PlayerData high = new PlayerData();
        high.globalXPMultiplier(600D, 60000);

        assertThat(high.computeXpMultiplier(player)).isEqualTo(1000D);

        PlayerData low = new PlayerData();
        low.globalXPMultiplier(-5D, 60000);

        assertThat(low.computeXpMultiplier(player)).isEqualTo(0.01D);
    }

    @Test
    @DisplayName("update stores the permission adjusted multiplier")
    void updateStoresThePermissionAdjustedMultiplier() throws Exception {
        configure(true, Map.of(VIP, 2D));
        grant(VIP);
        PlayerData data = new PlayerData();
        data.globalXPMultiplier(1D, 60000);
        AdaptPlayer adaptPlayer = mock(AdaptPlayer.class);
        when(adaptPlayer.getPlayer()).thenReturn(player);

        data.update(adaptPlayer);

        assertThat(data.getMultiplier()).isEqualTo(4D);
    }

    private void configure(boolean enabled, Map<String, Double> nodes) throws Exception {
        Field enabledField = AdaptConfig.PermissionXpMultipliers.class.getDeclaredField("enabled");
        enabledField.setAccessible(true);
        enabledField.set(settings, enabled);
        settings.getMultipliers().clear();
        settings.getMultipliers().putAll(nodes);
    }

    private void stack(boolean stacked) throws Exception {
        Field stackField = AdaptConfig.PermissionXpMultipliers.class.getDeclaredField("stack");
        stackField.setAccessible(true);
        stackField.set(settings, stacked);
    }

    private void grant(String node) {
        lenient().when(player.hasPermission(node)).thenReturn(true);
    }
}
