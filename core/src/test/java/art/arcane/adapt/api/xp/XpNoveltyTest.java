package art.arcane.adapt.api.xp;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.volmlib.util.math.M;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class XpNoveltyTest extends AdaptTestBase {
  private final UUID playerId = UUID.randomUUID();
  private final UUID worldId = UUID.randomUUID();
  private final AtomicLong now = new AtomicLong(1_000_000L);
  private final List<WorldRef> worlds = new ArrayList<>();
  private AdaptPlayer runtime;
  private PlayerData profile;
  private PlayerRef player;
  private Loc position;

  @BeforeEach
  void preparePlayer() {
    AdaptServer server = mock(AdaptServer.class);
    runtime = mock(AdaptPlayer.class);
    doReturn(server).when(core).getAdaptServer();
    when(server.getOnlineAdaptPlayer(playerId)).thenReturn(runtime);
    when(runtime.isRuntimeReady()).thenReturn(true);
    profile = new PlayerData();
    when(runtime.getData()).thenAnswer(invocation -> profile);
    reconnect();
  }

  @Test
  void frequentStationaryRewardsReachTheStillnessMultiplier() {
    AdaptConfig.XpIntegrity config = AdaptConfig.get().getXpIntegrity();
    try (MockedStatic<M> clock = clock()) {
      double before = 1.0;
      double after = 1.0;
      for (int second = 0; second <= 60; second++) {
        now.set(1_000_000L + second * 1000L);
        position = position.withRotation(second % 2 == 0 ? 0F : 180F, position.pitch());
        after = award("mine:stone");
        if (second == 59) {
          before = after;
        }
      }
      assertThat(after).isCloseTo(before * config.getStillnessFloorMultiplier(), within(0.000001D));
      position = position.add(1D, 0D, 0D);
      now.addAndGet(1000L);
      assertThat(award("mine:stone")).isCloseTo(before, within(0.000001D));
    }
  }

  @Test
  void tokenOtherActivitiesDoNotRestoreFullDiversity() {
    try (MockedStatic<M> clock = clock()) {
      double repeated = 1.0;
      for (int index = 0; index < 48; index++) {
        repeated = award("mine:stone");
      }
      profile = new PlayerData();
      for (int index = 0; index < 46; index++) {
        award("mine:stone");
      }
      award("craft:planks");
      double tokenVariety = award("harvest:wheat");
      profile = new PlayerData();
      double balanced = 1.0;
      for (int index = 0; index < 48; index++) {
        balanced = award(switch (index % 3) {
          case 0 -> "mine:stone";
          case 1 -> "craft:planks";
          default -> "harvest:wheat";
        });
      }
      assertThat(tokenVariety).isGreaterThan(repeated).isLessThan(balanced);
      assertThat(tokenVariety).isLessThan(balanced * 0.8D);
    }
  }

  @Test
  void serializedProfileRetainsRepetitionStillnessAndFieldCyclesAcrossReconnect() {
    try (MockedStatic<M> clock = clock()) {
      for (int second = 0; second < 60; second++) {
        now.set(1_000_000L + second * 1000L);
        award("mine:stone");
      }
      BlockRef crop = crop();
      assertThat(XpNovelty.fieldCycleMultiplier(player, crop)).isEqualTo(1D);
      now.addAndGet(1000L);
      double before = award("mine:stone");
      double fieldBefore = XpNovelty.fieldCycleMultiplier(player, crop);
      profile = PlayerData.fromJson(profile.toJson(true));
      reconnect();
      assertThat(award("mine:stone")).isCloseTo(before, within(0.000001D));
      assertThat(XpNovelty.fieldCycleMultiplier(player, crop())).isLessThanOrEqualTo(fieldBefore);
    }
  }

  @Test
  void serializedDiversityCountsMatchUninterruptedHistory() {
    try (MockedStatic<M> clock = clock()) {
      for (int index = 0; index < 65; index++) {
        award(index % 7 == 0 ? "craft:planks" : "mine:stone");
      }
      String saved = profile.toJson(true);
      double uninterrupted = award("harvest:wheat");
      profile = PlayerData.fromJson(saved);
      assertThat(award("harvest:wheat")).isCloseTo(uninterrupted, within(0.000001D));
    }
  }

  @Test
  void idleRecoveryExpiresPersistedSpatialAndActivityHistory() {
    try (MockedStatic<M> clock = clock()) {
      for (int index = 0; index < 60; index++) {
        award("mine:stone");
      }
      profile = PlayerData.fromJson(profile.toJson(true));
      now.addAndGet(AdaptConfig.get().getXpIntegrity().getSpatialCellTtlMillis() + 1L);
      assertThat(award("mine:stone")).isEqualTo(1D);
    }
  }

  @Test
  void serializedCellHistoriesStayBoundedAndWorldsRemainDistinct() {
    AdaptConfig.XpIntegrity config = AdaptConfig.get().getXpIntegrity();
    try (MockedStatic<M> clock = clock()) {
      for (int index = 0; index < config.getSpatialCellCap() + 8; index++) {
        position = new Loc(position.world(), index * 16D, position.y(), position.z());
        award("mine:stone");
        XpNovelty.fieldCycleMultiplier(player, crop());
      }
      JsonObject stored = new JsonParser().parse(profile.toJson(true)).getAsJsonObject().getAsJsonObject("xpNovelty");
      assertThat(stored.getAsJsonObject("spatialCells").size()).isEqualTo(config.getSpatialCellCap());
      assertThat(stored.getAsJsonObject("fieldCells").size()).isEqualTo(config.getSpatialCellCap());
      assertThat(stored.getAsJsonArray("window").size()).isEqualTo(config.getEntropyWindow());
      profile = new PlayerData();
      assertThat(award("mine:stone")).isEqualTo(1D);
      assertThat(award("mine:stone")).isLessThan(1D);
      position = new Loc(world(UUID.randomUUID()), position.x(), position.y(), position.z());
      assertThat(award("mine:stone")).isEqualTo(1D);
    }
  }

  private MockedStatic<M> clock() {
    MockedStatic<M> clock = mockStatic(M.class);
    clock.when(M::ms).thenAnswer(invocation -> now.get());
    return clock;
  }

  private double award(String key) {
    return XpNovelty.noveltyMultiplier(player, position, key);
  }

  private void reconnect() {
    player = mock(PlayerRef.class);
    position = new Loc(world(worldId), 100D, 64D, 100D);
    when(player.uuid()).thenReturn(playerId);
    when(player.location()).thenAnswer(invocation -> position);
    when(player.isOnline()).thenReturn(true);
    when(runtime.getPlayer()).thenReturn(player);
  }

  private WorldRef world(UUID id) {
    WorldRef world = mock(WorldRef.class);
    when(world.uid()).thenReturn(id);
    worlds.add(world);
    return world;
  }

  private BlockRef crop() {
    BlockRef block = mock(BlockRef.class);
    lenient().when(block.location()).thenAnswer(invocation -> position);
    return block;
  }
}
