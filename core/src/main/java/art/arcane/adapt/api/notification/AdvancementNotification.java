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

package art.arcane.adapt.api.notification;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.server.hud.ClientToasts;
import art.arcane.adapt.platform.AdaptPlatform;
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
  @Builder.Default
  private final String title = " ";
  @Builder.Default
  private final String description = " ";
  @Builder.Default
  private final AdaptAdvancementFrame frameType = AdaptAdvancementFrame.TASK;
  @Builder.Default
  private final String group = "default";
  @Builder.Default
  private final ToastKind kind = ToastKind.GENERIC;

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
    if (ClientToasts.capable(player.uuid())
        && ClientToasts.send(player.uuid(), ClientToasts.toast(kind, ClientToasts.icon(getIcon(), getModel()), title, description))) {
      return;
    }
    AdaptPlatform platform = core.getPlatform();
    ItemRef icon = getModel() != null ? getModel().toItemStack() : platform.host().items().create(getIcon());
    platform.advancements().toast(player, icon, title, frameType.platform());
  }

  public String buildTitle() {
    if (description.trim().isEmpty()) {
      return title;
    }

    return title + "\n" + description;
  }
}
