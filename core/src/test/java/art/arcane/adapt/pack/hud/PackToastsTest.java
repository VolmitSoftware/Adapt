package art.arcane.adapt.pack.hud;

import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementHost;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PackToastsTest {
  private static final UUID VIEWER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
  private static final Id HERBALISM_MODEL = Id.of("adapt", "adapt_ui/gui/skill/herbalism");
  private static final IconRef SPRITE = new IconRef("minecraft:wheat", 0, null, "adapt:textures/gui/skill/herbalism.png");
  private static final ServerFrame.Toast LEVEL = new ServerFrame.Toast(ToastKind.DISCOVERY, SPRITE, "§aHerbalism",
      "", "herbalism");

  private final AdvancementHost host = mock(AdvancementHost.class);
  private final ItemRef item = mock(ItemRef.class);
  private final List<Id> created = new ArrayList<>();
  private final PlayerRef viewer = mock(PlayerRef.class);

  private record Manifest(Map<String, Id> toasts) implements PackHudManifest {
    @Override
    public String icon(String spriteId) {
      return null;
    }

    @Override
    public Id toastModel(String spriteId) {
      return toasts.get(spriteId);
    }
  }

  private PackToasts toasts(boolean book, PackHudManifest manifest) {
    when(viewer.uuid()).thenReturn(VIEWER_ID);
    return new PackToasts((PlayerRef player) -> book, (Id type) -> {
      created.add(type);
      return item;
    }, () -> manifest, (String skillId) -> "herbalism".equals(skillId) ? 0x4DB344 : -1,
        (String legacy) -> legacy.replace("§a", ""));
  }

  @Test
  void packViewerGetsTheSpriteModelAndSkillColouredTitle() {
    assertThat(toasts(true, PackHudManifest.NONE).show(host, viewer, LEVEL, AdvancementFrame.TASK)).isTrue();

    assertThat(created).containsExactly(Id.minecraft("wheat"));
    verify(item).setItemModel(HERBALISM_MODEL);
    verify(host).toast(viewer, item, "<color:#4db344>Herbalism", AdvancementFrame.TASK);
  }

  @Test
  void skillIconsUseTheKitToastIcon() {
    Id kitIcon = Id.of("adapt", "adapt_ui/toast/herbalism");
    Manifest manifest = new Manifest(Map.of("adapt:gui/skill/herbalism", kitIcon));

    assertThat(toasts(true, manifest).show(host, viewer, LEVEL, AdvancementFrame.TASK)).isTrue();
    verify(item).setItemModel(kitIcon);
  }

  @Test
  void playersWithoutThePackKeepTheirToast() {
    assertThat(toasts(false, PackHudManifest.NONE).show(host, viewer, LEVEL, AdvancementFrame.TASK)).isFalse();

    verify(host, never()).toast(any(), any(), any(), any());
  }

  @Test
  void iconsWithoutASpriteKeepTheirToast() {
    ServerFrame.Toast plain = new ServerFrame.Toast(ToastKind.GENERIC, IconRef.item("minecraft:diamond"), "Hi", "", "");

    assertThat(toasts(true, PackHudManifest.NONE).show(host, viewer, plain, AdvancementFrame.TASK)).isFalse();
    verify(host, never()).toast(any(), any(), any(), any());
  }

  @Test
  void titlesWithoutASkillStayUncolouredAndEscaped() {
    ServerFrame.Toast toast = new ServerFrame.Toast(ToastKind.GENERIC, SPRITE, "<b>Odd", "", "");

    assertThat(toasts(true, PackHudManifest.NONE).title(toast)).isEqualTo("\\<b>Odd");
    assertThat(PackToasts.frame(ToastKind.CHALLENGE)).isEqualTo(AdvancementFrame.CHALLENGE);
    assertThat(PackToasts.frame(ToastKind.LEVEL_UP)).isEqualTo(AdvancementFrame.TASK);
  }
}
