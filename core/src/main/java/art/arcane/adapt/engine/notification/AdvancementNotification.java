/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.engine.notification;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.server.hud.ClientToasts;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.pack.hud.PackToasts;
import art.arcane.adapt.api.internal.HostPlatform;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.misc.CustomModel;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdvancementNotification implements Notification {
  @Builder.Default
  private final Id icon = Id.minecraft("diamond");
  @Builder.Default
  private final CustomModel model = null;
  /** The icon a client with native toasts draws, null for {@link #icon} and {@link #model}. */
  @Builder.Default
  private final IconRef clientIcon = null;
  @Builder.Default
  private final String title = " ";
  @Builder.Default
  private final String description = " ";
  @Builder.Default
  private final AdvancementFrame frameType = AdvancementFrame.TASK;
  @Builder.Default
  private final String group = "default";
  @Builder.Default
  private final ToastKind kind = ToastKind.GENERIC;
  /** The skill whose look a client toast takes, empty for none. */
  @Builder.Default
  private final String skill = "";

  @Override
  public long getTotalDuration() {
    return 100; // TODO: Actually calculate
  }

  @Override
  public String getGroup() {
    return group;
  }

  @Override
  public void play(AdaptPlayer p) {
    PlayerRef player = p.getPlayer();
    AdaptCore core = AdaptCore.current();
    if (player == null || core == null) {
      return;
    }
    ServerFrame.Toast toast = ClientToasts.toast(kind,
        clientIcon != null ? clientIcon : ClientToasts.icon(getIcon(), getModel()), title, description, skill);
    if (ClientToasts.capable(player.uuid()) && ClientToasts.send(player.uuid(), toast)) {
      return;
    }
    HostPlatform platform = core.getPlatform();
    if (PackToasts.running().show(platform.advancements(), player, toast, frameType)) {
      return;
    }
    ItemRef icon = getModel() != null ? getModel().toItemStack() : platform.host().items().create(getIcon());
    platform.advancements().toast(player, icon, title, frameType);
  }

  public String buildTitle() {
    if (description.trim().isEmpty()) {
      return title;
    }

    return title + "\n" + description;
  }
}
