package art.arcane.adapt.client.server.hud;

import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.ViewIcons;
import art.arcane.adapt.localization.MarkupPort;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.misc.CustomModel;

import java.util.UUID;

/** Toast frames and the icons they carry. Text is converted from Adapt markup to legacy section text. */
public final class ClientToasts {
  private ClientToasts() {
  }

  public static boolean capable(UUID playerId) {
    return ClientHud.capable(playerId, ClientProtocol.TOASTS);
  }

  /**
   * Sends a toast to a player with the client toasts, without its skill to a client that cannot read one; false means
   * the caller keeps its own output.
   */
  public static boolean send(UUID playerId, ServerFrame.Toast toast) {
    ServerFrame.Toast sent = ClientHud.capable(playerId, ClientProtocol.SKILL_TOASTS) ? toast : toast.plain();
    return ClientHud.send(playerId, ClientProtocol.TOASTS, sent);
  }

  /** A toast in the look of skill {@code skillId}; null or empty for none. */
  public static ServerFrame.Toast toast(ToastKind kind, IconRef icon, String titleMarkup, String bodyMarkup, String skillId) {
    return new ServerFrame.Toast(kind, icon, legacy(titleMarkup), legacy(bodyMarkup), skillId == null ? "" : skillId);
  }

  public static IconRef icon(Id item, CustomModel model) {
    return model != null ? ViewIcons.model(model) : IconRef.item(item.toString());
  }

  private static String legacy(String markup) {
    if (markup == null) {
      return "";
    }
    String trimmed = markup.trim();
    return trimmed.isEmpty() ? "" : MarkupPort.current().legacy(trimmed);
  }
}
