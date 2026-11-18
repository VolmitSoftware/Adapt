/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.adapt.util.director;

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.util.director.handlers.AdaptationListHandler;
import art.arcane.adapt.util.director.handlers.AdaptationProviderHandler;
import art.arcane.adapt.util.director.handlers.AdaptationSkillListHandler;
import art.arcane.adapt.util.director.handlers.BooleanHandler;
import art.arcane.adapt.util.director.handlers.ByteHandler;
import art.arcane.adapt.util.director.handlers.DoubleHandler;
import art.arcane.adapt.util.director.handlers.FloatHandler;
import art.arcane.adapt.util.director.handlers.IntegerHandler;
import art.arcane.adapt.util.director.handlers.LongHandler;
import art.arcane.adapt.util.director.handlers.ParticleHandler;
import art.arcane.adapt.util.director.handlers.PlayerHandler;
import art.arcane.adapt.util.director.handlers.ShortHandler;
import art.arcane.adapt.util.director.handlers.SkillProviderHandler;
import art.arcane.adapt.util.director.handlers.SoundHandler;
import art.arcane.adapt.util.director.handlers.StringHandler;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.DirectorSystemSupport;

import java.util.List;

public final class DirectorSystem {
  public static final KList<DirectorParameterHandler<?>> handlers = new KList<>(List.<DirectorParameterHandler<?>>of(
      new AdaptationListHandler(),
      new AdaptationProviderHandler(),
      new AdaptationSkillListHandler(),
      new BooleanHandler(),
      new ByteHandler(),
      new DoubleHandler(),
      new FloatHandler(),
      new IntegerHandler(),
      new LongHandler(),
      new ParticleHandler(),
      new PlayerHandler(),
      new ShortHandler(),
      new SkillProviderHandler(),
      new SoundHandler(),
      new StringHandler()
  ));

  private DirectorSystem() {
  }

  /**
   * Get the handler for the specified type
   *
   * @param type The type to handle
   * @return The corresponding {@link DirectorParameterHandler}, or null
   */
  public static DirectorParameterHandler<?> getHandler(Class<?> type) {
    DirectorParameterHandler<?> handler = DirectorSystemSupport.getHandler(handlers, type, (h, t) -> h.supports(t));
    if (handler != null) {
      return handler;
    }

    AdaptLog.error("Unhandled type in Director Parameter: " + type.getName() + ". This is bad!");
    return null;
  }
}
