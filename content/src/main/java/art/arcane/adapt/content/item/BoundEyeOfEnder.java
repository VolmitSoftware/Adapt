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
import art.arcane.adapt.platform.value.Loc;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BoundEyeOfEnder {
  private static final Id ENDER_EYE = Id.minecraft("ender_eye");
  public static final CustomItem.Definition<Data> ITEM = CustomItem.builder(Id.adapt("bound_eye_of_ender"), ENDER_EYE,
          StoredJson.codec(BoundEyeOfEnder::encode, BoundEyeOfEnder::decode), new Data(null))
      .storedUnder(Id.adapt("-963509705"))
      .recognisedBy(BoundEyeOfEnder::isBindableItem)
      .allow(VanillaUse.USE)
      .ownCooldownGroup()
      .build();
  private static final StoredItem<Data> STORED = new StoredItem<>(ITEM, BoundEyeOfEnder::applyMeta,
      BoundEyeOfEnder::applyLore);

  private BoundEyeOfEnder() {
  }

  public static Loc getLocation(ItemRef stack) {
    Data data = STORED.read(stack);
    return data == null ? null : data.getLocation();
  }

  public static boolean isBound(ItemRef stack) {
    return STORED.storesField(stack, "location");
  }

  public static void setData(ItemRef item, Loc t) {
    STORED.replace(item, new Data(t));
  }

  public static ItemRef withData(Loc t) {
    return STORED.create(new Data(t));
  }

  public static ItemRef create(Data data) {
    return STORED.create(data);
  }

  public static boolean isBindableItem(ItemRef item) {
    return STORED.has(item);
  }

  public static boolean ensureCooldownGroup(ItemRef stack) {
    return STORED.ensureCooldownGroup(stack);
  }

  private static void applyLore(Data data, List<String> lore) {
    lore.add(TextFormat.WHITE + Text.key("adapt.items.bound_eye_of_ender.name").resolve());
    lore.add(TextFormat.GRAY + Text.key("adapt.items.bound_eye_of_ender.usage1").resolve());
    lore.add(TextFormat.GRAY + Text.key("adapt.items.bound_eye_of_ender.usage2").resolve());
  }

  private static void applyMeta(Data data, ItemRef item) {
    item.setEnchantment(Id.minecraft("binding_curse"), 10);
    item.hideTooltip(TooltipPart.ATTRIBUTES);
    item.hideTooltip(TooltipPart.ENCHANTMENTS);
    item.hideTooltip(TooltipPart.DYE);
    item.setDisplayName(Text.key("adapt.items.bound_eye_of_ender.name").resolve());
  }

  private static Map<String, Object> encode(Data data) {
    Map<String, Object> object = StoredJson.object();
    object.put("location", StoredJson.locationJson(data.getLocation()));
    return object;
  }

  private static Data decode(Map<String, Object> object) {
    return new Data(StoredJson.location(object.get("location")));
  }

  public static class Data {
    private Loc location;

    public Data(Loc location) {
      this.location = location;
    }

    public Loc getLocation() {
      return location;
    }

    public void setLocation(Loc location) {
      this.location = location;
    }

    public static BoundEyeOfEnder.Data at(Loc l) {
      return new BoundEyeOfEnder.Data(l);
    }

    @Override
    public boolean equals(Object o) {
      return o == this || o instanceof Data other && Objects.equals(location, other.location);
    }

    @Override
    public int hashCode() {
      return Objects.hash(location);
    }

    @Override
    public String toString() {
      return "BoundEyeOfEnder.Data(location=" + location + ")";
    }
  }
}
