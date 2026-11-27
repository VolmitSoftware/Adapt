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
import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BoundEnderPearl {
  private static final Id ENDER_PEARL = Id.minecraft("ender_pearl");
  public static final CustomItem.Definition<Data> ITEM = CustomItem.builder(Id.adapt("bound_ender_pearl"), ENDER_PEARL,
          StoredJson.codec(BoundEnderPearl::encode, BoundEnderPearl::decode), new Data(null))
      .storedUnder(Id.adapt("2115257945"))
      .recognisedBy(BoundEnderPearl::isBindableItem)
      .allow(VanillaUse.USE)
      .build();
  private static final StoredItem<Data> STORED = new StoredItem<>(ITEM, BoundEnderPearl::applyMeta,
      BoundEnderPearl::applyLore);

  private BoundEnderPearl() {
  }

  public static BlockRef getBlock(ItemRef stack) {
    Data data = STORED.read(stack);
    return data == null ? null : data.getBlock();
  }

  public static void setData(ItemRef item, BlockRef t) {
    STORED.replace(item, new Data(t));
  }

  public static boolean clearBinding(ItemRef item) {
    Data data = STORED.read(item);
    if (data == null || data.getBlock() == null) {
      return false;
    }
    data.setBlock(null);
    STORED.store(item, data);
    return true;
  }

  public static ItemRef withData(BlockRef t) {
    return STORED.create(new Data(t));
  }

  public static ItemRef create(Data data) {
    return STORED.create(data);
  }

  public static boolean isBindableItem(ItemRef item) {
    return STORED.has(item);
  }

  private static void applyLore(Data data, List<String> lore) {
    lore.add(TextFormat.WHITE + Text.key("adapt.items.bound_ender_peral.name").resolve());
    lore.add(TextFormat.GRAY + Text.key("adapt.items.bound_ender_peral.usage1").resolve());
    lore.add(TextFormat.GRAY + Text.key("adapt.items.bound_ender_peral.usage2").resolve());
  }

  private static void applyMeta(Data data, ItemRef item) {
    item.setEnchantment(Id.minecraft("binding_curse"), 10);
    item.hideTooltip(TooltipPart.ATTRIBUTES);
    item.hideTooltip(TooltipPart.ENCHANTMENTS);
    item.setDisplayName(Text.key("adapt.items.bound_ender_peral.name").resolve());
  }

  private static Map<String, Object> encode(Data data) {
    Map<String, Object> object = StoredJson.object();
    object.put("block", StoredJson.blockJson(data.getBlock()));
    return object;
  }

  private static Data decode(Map<String, Object> object) {
    return new Data(StoredJson.block(object.get("block")));
  }

  public static class Data {
    private BlockRef block;

    public Data(BlockRef block) {
      this.block = block;
    }

    public BlockRef getBlock() {
      return block;
    }

    public void setBlock(BlockRef block) {
      this.block = block;
    }

    public static BoundEnderPearl.Data at(BlockRef l) {
      return new BoundEnderPearl.Data(l);
    }

    @Override
    public boolean equals(Object o) {
      return o == this || o instanceof Data other && Objects.equals(block, other.block);
    }

    @Override
    public int hashCode() {
      return Objects.hash(block);
    }

    @Override
    public String toString() {
      return "BoundEnderPearl.Data(block=" + block + ")";
    }
  }
}
