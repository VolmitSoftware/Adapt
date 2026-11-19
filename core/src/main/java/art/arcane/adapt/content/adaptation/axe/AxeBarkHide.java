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

package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.Map;
import java.util.UUID;

public class AxeBarkHide extends SimpleAdaptation<AxeBarkHide.Config> {
  private static final String SLOT_BARK = "bark";
  private static final Id OAK_WOOD = Id.minecraft("oak_wood");
  private final Map<UUID, BarkState> states = playerState();

  public AxeBarkHide() {
    super("axe-bark-hide");
    needs(PlatformNeed.attribute(Attributes.MAX_ABSORPTION));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("oak_wood"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("oak_wood"))
        .key("challenge_axe_bark_hide_2500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_axe_bark_hide_2500", "axe.bark-hide.stacks-gained", 2500, 700);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    if (getActiveLevel(player.getPlayer()) <= 0) {
      AdaptAttributeService.get().removeAll(player.getPlayer(), getName());
    }
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, C.GREEN, "+ ", getAbsorptionCap(level) * 2 + " ♥", 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getGracePeriodTicks(level) * 50D, 1), 2);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    PlayerRef p = e.player();
    ItemRef hand = p.inventory().mainHand();
    if (!isAxe(hand) || !isLog(e.block().type())) {
      return;
    }

    Adaptation.BlockActionContext context = resolveBlockBreakContext(p, e.block().location());
    if (context == null) {
      return;
    }

    int level = context.level();
    long now = System.currentTimeMillis();
    int graceTicks = getGracePeriodTicks(level);
    long graceMs = graceTicks * 50L;
    UUID id = p.uuid();
    BarkState state = states.get(id);
    int current = currentStacks(state, now, graceMs);
    int cap = getAbsorptionCap(level);
    int next = nextStacks(current, cap);
    boolean refill = shouldRefill(current, next, state != null && state.ceilingLost());
    states.put(id, new BarkState(next, now, false));

    if (next > current) {
      addStat(p, "axe.bark-hide.stacks-gained", 1);
      xp(p, getConfig().xpPerStack);
      fx(p.location().add(0D, 1D, 0D), FxPriority.TRANSITION)
          .particle(Particles.blockCrack(AdaptCore.platform().host().items().blockState(OAK_WOOD)), 6, 0.3D, 0.6D, 0.3D, 0.02D, 0.0D)
          .sound(Sounds.BLOCK_WOOD_PLACE, 0.4F, 0.7F + (next * 0.12F));
    }

    double points = absorptionPoints(next);
    AdaptAttributeService.get().applyTimed(p, getName(), SLOT_BARK, Attributes.MAX_ABSORPTION, points, AttributeOperation.ADD_VALUE, graceTicks);
    if (refill) {
      p.setAbsorption(Math.max(p.absorption(), points));
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent e) {
    UUID id = e.player().uuid();
    BarkState state = states.get(id);
    if (state != null) {
      states.put(id, new BarkState(state.stacks(), state.lastWorkAt(), true));
    }
  }

  static int currentStacks(BarkState state, long now, long graceMs) {
    return (state == null || now - state.lastWorkAt() > graceMs) ? 0 : state.stacks();
  }

  static int nextStacks(int current, int cap) {
    return Math.min(current + 1, cap);
  }

  static double absorptionPoints(int stacks) {
    return stacks * 4.0D;
  }

  static boolean shouldRefill(int current, int next, boolean ceilingLost) {
    return next > current || ceilingLost;
  }

  private int getAbsorptionCap(int level) {
    return Math.max(1, getConfig().absorptionCapBase + (int) Math.round(getLevelPercent(level) * getConfig().absorptionCapFactor));
  }

  private int getGracePeriodTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().gracePeriodTicksBase + (getLevelPercent(level) * getConfig().gracePeriodTicksFactor)));
  }

  record BarkState(int stacks, long lastWorkAt, boolean ceilingLost) {
  }

  @ConfigDescription("Chopping logs layers on short-lived absorption that fades once you stop working.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base maximum absorption stacks before level scaling. Each stack is two hearts.", impact = "Higher values grant more absorption at low levels.")
    int absorptionCapBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional absorption stacks unlocked at maximum level.", impact = "Higher values raise the absorption ceiling with level.")
    int absorptionCapFactor = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Grace ticks the absorption persists after your last chop before level scaling.", impact = "Higher values keep bark absorption alive longer between logs.")
    double gracePeriodTicksBase = 100;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra grace ticks granted by leveling.", impact = "Higher values extend how long absorption lingers after chopping.")
    double gracePeriodTicksFactor = 200;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted each time a fresh bark absorption stack is added.", impact = "Higher values accelerate skill progression from building bark hide.")
    double xpPerStack = 2;

    public Config() {
      baseCost = 4;
      costFactor = 0.5;
      maxLevel = 5;
      initialCost = 4;
    }
  }
}
