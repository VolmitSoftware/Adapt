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

package com.volmit.adapt.util.decree;


//import com.volmit.react.React;
//import com.volmit.react.util.collection.Map;
//import com.volmit.react.util.plugin.VolmitSender;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.util.VolmitSender;

import java.util.HashMap;
import java.util.Map;

public interface DecreeContextHandler<T> {
    Map<Class<?>, DecreeContextHandler<?>> contextHandlers = buildContextHandlers();

    static Map<Class<?>, DecreeContextHandler<?>> buildContextHandlers() {
        Map<Class<?>, DecreeContextHandler<?>> contextHandlers = new HashMap<>();

        try {
            Adapt.initialize("com.volmit.react.util.decree.context").forEach((i)
                    -> contextHandlers.put(((DecreeContextHandler<?>) i).getType(), (DecreeContextHandler<?>) i));
        } catch (Throwable e) {
            e.printStackTrace();
            e.printStackTrace();
        }

        return contextHandlers;
    }

    Class<T> getType();

    T handle(VolmitSender sender);
}
