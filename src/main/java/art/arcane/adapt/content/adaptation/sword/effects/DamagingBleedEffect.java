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

import art.arcane.adapt.util.common.scheduling.J;
import de.slikey.effectlib.EffectManager;
import de.slikey.effectlib.effect.BleedEffect;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class DamagingBleedEffect extends BleedEffect {
  private final double damage;
  private final LivingEntity target;
  private final Player source;

  public DamagingBleedEffect(EffectManager effectManager, DamageContext context) {
    super(effectManager);
    this.damage = context.damage();
    this.target = context.target();
    this.source = context.source();
  }

  @Override
  public void onRun() {
    super.onRun();
    J.runEntity(target, this::applyDamage);
  }

  void applyDamage() {
    target.damage(damage, source);
  }

  public record DamageContext(double damage, LivingEntity target, Player source) {
  }
}
