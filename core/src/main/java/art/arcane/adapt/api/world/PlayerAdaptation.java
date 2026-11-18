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

package art.arcane.adapt.api.world;

import art.arcane.volmlib.util.collection.KMap;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlayerAdaptation {
  public static final String REGION_GRANTED_KEY = "regionGranted";

  private final KMap<String, Object> storage = new KMap<>();
  private String id;
  private volatile int level;

  public boolean isRegionGranted() {
    Object marker = storage.get(REGION_GRANTED_KEY);
    if (marker instanceof Boolean flag) {
      return flag;
    }

    return marker instanceof String text && Boolean.parseBoolean(text);
  }

  public void setRegionGranted(boolean regionGranted) {
    if (regionGranted) {
      storage.put(REGION_GRANTED_KEY, Boolean.TRUE);
      return;
    }

    storage.remove(REGION_GRANTED_KEY);
  }
}
