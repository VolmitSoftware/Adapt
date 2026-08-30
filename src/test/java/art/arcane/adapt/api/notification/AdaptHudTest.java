package art.arcane.adapt.api.notification;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.volmlib.util.hud.HudActionBar;
import art.arcane.volmlib.util.hud.HudPriority;
import art.arcane.volmlib.util.hud.HudSegmentCodec;
import art.arcane.volmlib.util.hud.HudSlot;
import art.arcane.volmlib.util.hud.HudStampedSegment;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdaptHudTest extends AdaptTestBase {
  private final Map<Plugin, String> store = new LinkedHashMap<>();
  private Player player;
  private Player.Spigot spigot;

  @BeforeEach
  void startHud() {
    lenient().when(plugin.getName()).thenReturn("Adapt");
    player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    spigot = mock(Player.Spigot.class);
    lenient().when(player.spigot()).thenReturn(spigot);
    doAnswer(invocation -> {
      MetadataValue value = invocation.getArgument(1);
      store.put(value.getOwningPlugin(), value.asString());
      return null;
    }).when(player).setMetadata(eq(HudActionBar.METADATA_KEY), any(MetadataValue.class));
    lenient().doAnswer(invocation -> {
      store.remove((Plugin) invocation.getArgument(1));
      return null;
    }).when(player).removeMetadata(eq(HudActionBar.METADATA_KEY), any(Plugin.class));
    when(player.getMetadata(HudActionBar.METADATA_KEY)).thenAnswer(invocation -> {
      List<MetadataValue> values = new ArrayList<>();
      store.forEach((owner, encoded) -> values.add(new FixedMetadataValue(owner, encoded)));
      return values;
    });
    AdaptHud.start(plugin);
  }

  @AfterEach
  void stopHud() {
    AdaptHud.stop();
    store.clear();
  }

  private String lastSentPlainText() {
    ArgumentCaptor<BaseComponent[]> sent = ArgumentCaptor.forClass(BaseComponent[].class);
    verify(spigot, atLeastOnce()).sendMessage(eq(ChatMessageType.ACTION_BAR), sent.capture());
    return BaseComponent.toPlainText(sent.getValue());
  }

  private HudStampedSegment postedSegment(String purpose) {
    List<HudStampedSegment> posted = HudSegmentCodec.decode(store.get(plugin));
    return posted.stream().filter(segment -> segment.purpose().equals(purpose)).findFirst().orElse(null);
  }

  @Test
  void ambientStatusPublishesCenterLeftStatusSegmentAndClearRemovesIt() {
    AdaptHud.ambientStatus(player, "sixth-sense", "Village E 120m");

    HudStampedSegment segment = postedSegment("adapt:status:sixth-sense");
    assertThat(segment).isNotNull();
    assertThat(segment.priority()).isEqualTo(HudPriority.STATUS);
    assertThat(segment.slots()).containsExactly(HudSlot.CENTER, HudSlot.LEFT);
    assertThat(lastSentPlainText()).isEqualTo("Village E 120m");

    AdaptHud.clearAmbientStatus(player, "sixth-sense");

    assertThat(store).doesNotContainKey(plugin);
    assertThat(lastSentPlainText()).isEqualTo(" ");
  }

  @Test
  void xpTickerPublishesLeftSegment() {
    AdaptHud.xpTicker(player, "Discovery +5XP");

    HudStampedSegment segment = postedSegment("adapt:xp");
    assertThat(segment).isNotNull();
    assertThat(segment.priority()).isEqualTo(HudPriority.AMBIENT);
    assertThat(segment.slots()).containsExactly(HudSlot.LEFT);
    assertThat(lastSentPlainText()).isEqualTo("Discovery +5XP");
  }

  @Test
  void notificationDisplayDurationControlsPublishedHudLifetime() {
    AdaptHud.xpTicker(player, "Discovery +5XP", 4_200L);

    HudStampedSegment xp = postedSegment("adapt:xp");
    assertThat(xp).isNotNull();
    assertThat(xp.ttlMillis()).isEqualTo(4_200L);

    AdaptHud.title(player, "", "Level 12", 900L);

    HudStampedSegment title = postedSegment("adapt:title");
    assertThat(title).isNotNull();
    assertThat(title.ttlMillis()).isEqualTo(900L);
  }

  @Test
  void notificationDisplayDurationRejectsNonPositiveHudLifetimes() {
    assertThat(AdaptHud.normalizeDisplayDuration(0L)).isOne();
    assertThat(AdaptHud.normalizeDisplayDuration(-50L)).isOne();
    assertThat(AdaptHud.normalizeDisplayDuration(750L)).isEqualTo(750L);
  }

  @Test
  void noticeMergesAroundForeignPinnedMonitorSegment() {
    Plugin react = mock(Plugin.class);
    lenient().when(react.getName()).thenReturn("React");
    long now = System.currentTimeMillis();
    store.put(react, HudSegmentCodec.encode(List.of(new HudStampedSegment(
        HudPriority.PINNED, now - 100L, now, 5_000L, List.of(HudSlot.CENTER), "react:monitor", "monitor"))));

    AdaptHud.actionBar(player, "Level up!");

    HudStampedSegment segment = postedSegment("adapt:notice");
    assertThat(segment).isNotNull();
    assertThat(segment.priority()).isEqualTo(HudPriority.NOTICE);
    assertThat(lastSentPlainText()).isEqualTo("monitor  Level up!");
  }

  @Test
  void guiTitlePublishesInteractiveNoticeInsteadOfTitleSurface() {
    AdaptHud.guiTitle(player, " ", "Confirm permanent unlock");

    HudStampedSegment segment = postedSegment("adapt:gui");
    assertThat(segment).isNotNull();
    assertThat(segment.priority()).isEqualTo(HudPriority.INTERACTIVE);
    assertThat(segment.slots()).containsExactly(HudSlot.CENTER, HudSlot.RIGHT);
    assertThat(lastSentPlainText()).isEqualTo("Confirm permanent unlock");
    verify(player, never()).sendTitle(any(), any(), anyInt(), anyInt(), anyInt());
  }

  @Test
  void clearAllRemovesEveryAdaptSegment() {
    AdaptHud.xpTicker(player, "Discovery +5XP");
    AdaptHud.ambientStatus(player, "sixth-sense", "Village E 120m");

    AdaptHud.clear(player);

    assertThat(store).doesNotContainKey(plugin);
  }
}
