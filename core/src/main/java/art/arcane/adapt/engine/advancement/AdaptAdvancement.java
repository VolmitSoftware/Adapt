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

package art.arcane.adapt.engine.advancement;

import art.arcane.adapt.engine.platform.EngineAdvancementNode;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.server.hud.ClientToasts;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.localization.AdaptKeys;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementNode;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Builder
@Data
public class AdaptAdvancement {
  private static final String MISSING_TITLE = "MISSING TITLE";
  private static final Pattern PADDED_FRAME = Pattern.compile("^((?:\u00a7.)*)\\[\\s{2,}(.*?)(?:\u00a7.)*\\s{2,}]$");
  private static final String MISSING_DESCRIPTION = "MISSING DESCRIPTION";

  private String background;
  @Builder.Default
  private Id icon = Id.minecraft("emerald");
  @Builder.Default
  private CustomModel model = null;
  /** The icon a client with native toasts draws, null for {@link #icon} and {@link #model}. */
  @Builder.Default
  private IconRef clientIcon = null;
  @Builder.Default
  private String title = MISSING_TITLE;
  @Builder.Default
  private String description = MISSING_DESCRIPTION;
  /** The line under this advancement's client toast, null for its description. */
  @Builder.Default
  private String toastBody = null;
  @Builder.Default
  private AdvancementFrame frame = AdvancementFrame.TASK;
  @Builder.Default
  private boolean toast = false;
  @Builder.Default
  private boolean announce = false;
  @Builder.Default
  private AdvancementVisibility visibility = AdvancementVisibility.VANILLA;
  @Builder.Default
  private String key = "root";
  /** The message key under which an unset title and description are looked up, null for {@code adapt.advancement.<key>}. */
  @Builder.Default
  private String textKey = null;
  @Singular
  private List<AdaptAdvancement> children;

  private static final String SKILL_KEY = "skill/";
  private static final String ADAPTATION_KEY = "adaptation/";
  private static final String CONTENT_KEY = "content/";

  /** The advancement id the platform host registers for a key: {@code adapt:<key>}. */
  public static Id advancementId(String key) {
    return Id.adapt(key);
  }

  /** The key of a skill's advancement, {@code skill/<namespace>/<path>}. */
  public static String skillKey(Id skill) {
    return SKILL_KEY + skill.namespace() + "/" + skill.path();
  }

  /** The key of an adaptation's advancement, {@code adaptation/<namespace>/<path>}. */
  public static String adaptationKey(Id adaptation) {
    return ADAPTATION_KEY + adaptation.namespace() + "/" + adaptation.path();
  }

  /**
   * The key of an advancement that the content with id {@code owner} declared as {@code key}: {@code key} itself for
   * built-in content, {@code content/<namespace>/<key>} for any other namespace, so two addons never share one.
   */
  public static String contentKey(Id owner, String key) {
    return Id.ADAPT.equals(owner.namespace()) ? key : CONTENT_KEY + owner.namespace() + "/" + key;
  }

  /** Whether {@code key} is a {@link #skillKey}, the root of a skill's tab. */
  public static boolean isSkillKey(String key) {
    return key != null && key.startsWith(SKILL_KEY);
  }

  /** The adaptation an {@link #adaptationKey} names, or null for any other key. */
  public static Id adaptationOfKey(String key) {
    return key != null && key.startsWith(ADAPTATION_KEY) ? contentOfKey(key.substring(ADAPTATION_KEY.length())) : null;
  }

  private static Id contentOfKey(String rest) {
    int slash = rest.indexOf('/');
    return slash <= 0 ? null : Id.tryParse(rest.substring(0, slash) + ":" + rest.substring(slash + 1));
  }

  /** This advancement as the root of a tab tree, laid out the same way as the UltimateAdvancementAPI tabs. */
  public AdvancementNode toNode() {
    if (background == null) {
      throw new IllegalArgumentException("Background cannot be null");
    }
    return toNode(layoutPositions(), Id.parse(background));
  }

  private AdvancementNode toNode(Map<AdaptAdvancement, LayoutPosition> positions, Id rootBackground) {
    List<AdvancementNode> childNodes = new ArrayList<>();
    if (children != null) {
      for (AdaptAdvancement child : children) {
        childNodes.add(child.toNode(positions, null));
      }
    }

    LayoutPosition position = positions.get(this);
    return new EngineAdvancementNode(advancementId(getKey()), iconItem(), resolveTitle(), resolveDescription(),
        getFrame(), toast, announce, getVisibility(), position.x(), position.y(), 1,
        rootBackground, childNodes);
  }

  /**
   * The toast a client with native toasts shows for this advancement instead of the vanilla popup, in the look of
   * {@code skill} (empty for none).
   */
  ServerFrame.Toast clientToast(String skill) {
    return ClientToasts.toast(toastKind(getKey(), getFrame()),
        clientIcon != null ? clientIcon : ClientToasts.icon(getIcon(), getModel()),
        toastTitle(resolveTitle()), toastBody != null ? toastBody : resolveDescription(), skill);
  }

  /** Drops the padded bracket frame that centres a title in the vanilla advancement tab; a toast has its own frame. */
  static String toastTitle(String title) {
    return PADDED_FRAME.matcher(title).replaceAll("$1$2");
  }

  static ToastKind toastKind(String key, AdvancementFrame frame) {
    if (frame == AdvancementFrame.CHALLENGE) {
      return ToastKind.CHALLENGE;
    }
    if (isSkillKey(key)) {
      return ToastKind.DISCOVERY;
    }
    if (key.startsWith(ADAPTATION_KEY)) {
      return ToastKind.UNLOCK;
    }
    return ToastKind.GENERIC;
  }

  private ItemRef iconItem() {
    CustomModel customModel = getModel();
    return customModel != null
        ? customModel.toItemStack()
        : AdaptCore.platform().host().items().create(getIcon());
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

    String localized = localizedOrNull(lookupKey() + ".title");
    if (localized != null) {
      return localized;
    }

    return title == null ? MISSING_TITLE : title;
  }

  private String resolveDescription() {
    if (description != null && !description.equals(MISSING_DESCRIPTION)) {
      return description;
    }

    String localized = localizedOrNull(lookupKey() + ".description");
    if (localized != null) {
      return localized;
    }

    return description == null ? MISSING_DESCRIPTION : description;
  }

  private String lookupKey() {
    return textKey != null ? textKey : AdaptKeys.id("advancement." + key);
  }

  private static String localizedOrNull(String localizationKey) {
    MessageKey messageKey = AdaptLanguage.declared(localizationKey);
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
