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

package art.arcane.adapt.content.item;

import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.item.VanillaUse;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.TooltipPart;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class BoundSnowBall {
  private static final Id SNOWBALL = Id.minecraft("snowball");
  public static final CustomItem.Definition<Data> ITEM = CustomItem.builder(Id.adapt("bound_snowball"), SNOWBALL,
          StoredJson.codec(BoundSnowBall::encode, BoundSnowBall::decode), new Data(null))
      .storedUnder(Id.adapt("669102293"))
      .recognisedBy(BoundSnowBall::isBindableItem)
      .allow(VanillaUse.USE)
      .build();
  private static final StoredItem<Data> STORED = new StoredItem<>(ITEM, BoundSnowBall::applyMeta,
      BoundSnowBall::applyLore);

  private BoundSnowBall() {
  }

  public static boolean isBindableItem(ItemRef item) {
    return STORED.has(item);
  }

  public static ItemRef create(Data data) {
    return STORED.create(data);
  }

  private static void applyLore(Data data, List<String> lore) {
    lore.add(TextFormat.WHITE + Text.key("adapt.items.bound_snowball.name").resolve());
    lore.add(TextFormat.GRAY + Text.key("adapt.items.bound_snowball.usage1").resolve());
  }

  private static void applyMeta(Data data, ItemRef item) {
    item.setEnchantment(Id.minecraft("binding_curse"), 10);
    item.hideTooltip(TooltipPart.ATTRIBUTES);
    item.hideTooltip(TooltipPart.ENCHANTMENTS);
    item.hideTooltip(TooltipPart.DYE);
    item.setDisplayName(Text.key("adapt.items.bound_snowball.name").resolve());
  }

  private static Map<String, Object> encode(Data data) {
    Map<String, Object> object = StoredJson.object();
    object.put("player", data.getPlayer() == null ? null : data.getPlayer().toString());
    return object;
  }

  private static Data decode(Map<String, Object> object) {
    String player = StoredJson.string(object, "player");
    try {
      return new Data(player == null ? null : UUID.fromString(player));
    } catch (IllegalArgumentException malformed) {
      return new Data(null);
    }
  }

  public static class Data {
    private UUID player;

    public Data(UUID player) {
      this.player = player;
    }

    public UUID getPlayer() {
      return player;
    }

    public void setPlayer(UUID player) {
      this.player = player;
    }

    @Override
    public boolean equals(Object o) {
      return o == this || o instanceof Data other && Objects.equals(player, other.player);
    }

    @Override
    public int hashCode() {
      return Objects.hash(player);
    }

    @Override
    public String toString() {
      return "BoundSnowBall.Data(player=" + player + ")";
    }
  }
}
