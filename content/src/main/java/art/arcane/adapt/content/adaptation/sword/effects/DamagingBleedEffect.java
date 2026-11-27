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

package art.arcane.adapt.content.adaptation.sword.effects;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.concurrent.ThreadLocalRandom;

/**
 * EffectLib-style bleed: every {@code periodTicks} for {@code iterations} runs, the block break effect of
 * {@code material} plays at the target's eye block or the block below it. The damaging form also deals magic
 * damage, credited to the caster, on every run.
 */
public final class DamagingBleedEffect {
  private static final Id MAGIC = Id.minecraft("magic");
  private static final double HEIGHT = -1.0D;
  private final double damage;
  private final LivingRef target;
  private final PlayerRef source;

  public DamagingBleedEffect(DamageContext context) {
    this.damage = context.damage();
    this.target = context.target();
    this.source = context.source();
  }

  public void start(Id material, int iterations, int periodTicks) {
    bleed(target, material, iterations, periodTicks, () -> AdaptApi.scheduler().runEntity(target, this::applyDamage));
  }

  void applyDamage() {
    target.damage(damage, MAGIC, source);
  }

  /** Bleed visual without damage. */
  public static void startVisual(EntityRef target, Id material, int iterations, int periodTicks) {
    bleed(target, material, iterations, periodTicks, null);
  }

  private static void bleed(EntityRef target, Id material, int iterations, int periodTicks, Runnable pulse) {
    if (iterations < 1) {
      return;
    }
    BlockStateRef state = AdaptApi.platform().host().items().blockState(material);
    schedule(target, state, Math.max(1, periodTicks), iterations, pulse, 1);
  }

  private static void schedule(EntityRef target, BlockStateRef state, int periodTicks, int remaining, Runnable pulse,
                               int delayTicks) {
    AdaptApi.scheduler().runEntity(target, () -> {
      if (!target.isValid() || target.isDead()) {
        return;
      }
      run(target, state, pulse);
      if (remaining > 1) {
        schedule(target, state, periodTicks, remaining - 1, pulse, periodTicks);
      }
    }, delayTicks);
  }

  private static void run(EntityRef target, BlockStateRef state, Runnable pulse) {
    Loc origin = target instanceof LivingRef living ? living.eyeLocation() : target.location();
    Loc at = origin.add(0, ThreadLocalRandom.current().nextFloat() * HEIGHT, 0);
    AdaptApi.platform().fx().blockBreakEffect(at.world(), at.blockPos(), state);
    if (pulse != null) {
      pulse.run();
    }
  }

  public record DamageContext(double damage, LivingRef target, PlayerRef source) {
  }
}
