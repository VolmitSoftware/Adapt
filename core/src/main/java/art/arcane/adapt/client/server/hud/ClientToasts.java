package art.arcane.adapt.client.server.hud;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.view.CatalogViews;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.IconSprites;
import art.arcane.adapt.localization.MarkupPort;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.inventorygui.GuiConfig;
import art.arcane.adapt.util.common.misc.CustomModel;

import java.util.UUID;

/** Toast frames and the icons they carry. Text is converted from Adapt markup to legacy section text. */
public final class ClientToasts {
  private ClientToasts() {
  }

  public static boolean capable(UUID playerId) {
    return ClientHud.capable(playerId, ClientProtocol.TOASTS);
  }

  /** Sends a toast to a player with the client toasts; false means the caller keeps its own output. */
  public static boolean send(UUID playerId, ServerFrame.Toast toast) {
    return ClientHud.send(playerId, ClientProtocol.TOASTS, toast);
  }

  public static ServerFrame.Toast toast(ToastKind kind, IconRef icon, String titleMarkup, String bodyMarkup) {
    return new ServerFrame.Toast(kind, icon, legacy(titleMarkup), legacy(bodyMarkup));
  }

  public static IconRef icon(Id item, CustomModel model) {
    return model != null ? CatalogViews.icon(model) : IconRef.item(item.toString());
  }

  /** The skill's icon, naming the skill's sprite so a client with that art draws it instead of the item. */
  public static IconRef skillIcon(Skill<?> skill) {
    return IconSprites.withSprite(CatalogViews.icon(GuiConfig.skillModel(skill.getName(), skill.getIcon(), skill.getModel())),
        IconSprites.skill(skill.getName()));
  }

  /** The adaptation's icon, naming the adaptation's sprite so a client with that art draws it instead of the item. */
  public static IconRef adaptationIcon(Adaptation<?> adaptation) {
    return IconSprites.withSprite(CatalogViews.icon(GuiConfig.adaptationModel(adaptation.getName(), adaptation.getIcon(),
        adaptation.getModel())), IconSprites.adaptation(adaptation.getName()));
  }

  private static String legacy(String markup) {
    if (markup == null) {
      return "";
    }
    String trimmed = markup.trim();
    return trimmed.isEmpty() ? "" : MarkupPort.current().legacy(trimmed);
  }
}
