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

package art.arcane.adapt.api.advancement;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.AdaptMessages;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;
import com.fren_gor.ultimateAdvancementAPI.AdvancementTab;
import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.BaseAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.RootAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementDisplay;
import com.fren_gor.ultimateAdvancementAPI.database.TeamProgression;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Builder
@Data
public class AdaptAdvancement {
  private static final String MISSING_TITLE = "MISSING TITLE";
  private static final String MISSING_DESCRIPTION = "MISSING DESCRIPTION";

  private String background;
  @Builder.Default
  private Material icon = Material.EMERALD;
  @Builder.Default
  private CustomModel model = null;
  @Builder.Default
  private String title = MISSING_TITLE;
  @Builder.Default
  private String description = MISSING_DESCRIPTION;
  @Builder.Default
  private AdaptAdvancementFrame frame = AdaptAdvancementFrame.TASK;
  @Builder.Default
  private boolean toast = false;
  @Builder.Default
  private boolean announce = false;
  @Builder.Default
  private AdvancementVisibility visibility = AdvancementVisibility.VANILLA;
  @Builder.Default
  private String key = "root";
  @Singular
  private List<AdaptAdvancement> children;

  private Advancement toAdvancement(Advancement parent, LayoutPosition position) {
    CustomModel customModel = getModel();
    ItemStack icon = customModel != null ?
        customModel.toItemStack() :
        new ItemStack(getIcon());
    AdvancementDisplay d = new AdvancementDisplay.Builder(icon, resolveTitle())
        .description(resolveDescription())
        .frame(getFrame().toUaaFrame())
        .showToast(toast)
        .announceChat(announce)
        .x(position.x())
        .y(position.y())
        .build();

    if (parent == null) {
      if (background == null) {
        throw new IllegalArgumentException("Background cannot be null");
      }

      return new MainAdvancement(Adapt.instance.getManager().createAdvancementTab(getKey()), getKey(), d, background);
    }

    return new SubAdvancement(getKey(), d, parent, getVisibility());
  }

  public KList<Advancement> toAdvancements() {
    KList<Advancement> advancements = new KList<>();
    appendAdvancements(advancements, null, layoutPositions());
    return advancements;
  }

  Map<AdaptAdvancement, LayoutPosition> layoutPositions() {
    Map<AdaptAdvancement, LayoutPosition> positions = new IdentityHashMap<>();
    assignLayout(positions, new LayoutCursor(), 0);
    return positions;
  }

  private String resolveTitle() {
    if (title != null && !title.equals(MISSING_TITLE)) {
      return title;
    }

    String localized = localizedOrNull("advancement." + key + ".title");
    if (localized != null) {
      return localized;
    }

    return title == null ? MISSING_TITLE : title;
  }

  private String resolveDescription() {
    if (description != null && !description.equals(MISSING_DESCRIPTION)) {
      return description;
    }

    String localized = localizedOrNull("advancement." + key + ".description");
    if (localized != null) {
      return localized;
    }

    return description == null ? MISSING_DESCRIPTION : description;
  }

  private static String localizedOrNull(String localizationKey) {
    MessageKey messageKey = AdaptMessages.catalog().key(localizationKey);
    if (!(messageKey instanceof TextKey textKey)) {
      return null;
    }
    return AdaptLanguage.text(textKey);
  }

  private float assignLayout(Map<AdaptAdvancement, LayoutPosition> positions, LayoutCursor cursor, int depth) {
    float y;
    if (children == null || children.isEmpty()) {
      y = cursor.claimRow();
    } else {
      float firstChildY = children.getFirst().assignLayout(positions, cursor, depth + 1);
      float lastChildY = firstChildY;
      for (int index = 1; index < children.size(); index++) {
        lastChildY = children.get(index).assignLayout(positions, cursor, depth + 1);
      }
      y = (firstChildY + lastChildY) / 2F;
    }

    positions.put(this, new LayoutPosition(1F + depth, 1F + y));
    return y;
  }

  private void appendAdvancements(KList<Advancement> advancements, Advancement parent,
                                   Map<AdaptAdvancement, LayoutPosition> positions) {
    Advancement advancement = toAdvancement(parent, positions.get(this));
    if (children != null && !children.isEmpty()) {
      for (AdaptAdvancement child : children) {
        child.appendAdvancements(advancements, advancement, positions);
      }
    }

    advancements.add(advancement);
  }

  private static class MainAdvancement extends RootAdvancement {

    public MainAdvancement(@NotNull AdvancementTab advancementTab, @NotNull String key, @NotNull AdvancementDisplay display, @NotNull String backgroundTexture) {
      super(advancementTab, key, display, backgroundTexture);
    }

    @Override
    public void grant(@NotNull Player player, boolean giveRewards) {
      super.grant(player, giveRewards);
      try {
        getAdvancementTab().showTab(player);
      } catch (Throwable t) {
        Adapt.verbose("Failed to show advancement tab '" + getKey() + "' for " + player.getName() + ": "
            + t.getClass().getSimpleName()
            + (t.getMessage() == null ? "" : " (" + t.getMessage() + ")"));
      }
    }

    @Override
    public void revoke(@NotNull Player player) {
      super.revoke(player);
      try {
        getAdvancementTab().hideTab(player);
      } catch (Throwable t) {
        Adapt.verbose("Failed to hide advancement tab '" + getKey() + "' for " + player.getName() + ": "
            + t.getClass().getSimpleName()
            + (t.getMessage() == null ? "" : " (" + t.getMessage() + ")"));
      }
    }
  }

  private static class SubAdvancement extends BaseAdvancement {
    private final AdvancementVisibility visibility;

    public SubAdvancement(@NotNull String key,
                          @NotNull AdvancementDisplay display,
                          @NotNull Advancement parent,
                          @NotNull AdvancementVisibility visibility) {
      super(key, display, parent);
      this.visibility = visibility;
    }

    @Override
    public boolean isVisible(@NotNull TeamProgression progression) {
      return visibility.isVisible(this, progression);
    }
  }

  record LayoutPosition(float x, float y) {
  }

  private static final class LayoutCursor {
    private float nextRow;

    private float claimRow() {
      float row = nextRow;
      nextRow++;
      return row;
    }
  }
}
