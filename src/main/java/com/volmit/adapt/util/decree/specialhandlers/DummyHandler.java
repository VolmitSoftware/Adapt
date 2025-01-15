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

package com.volmit.adapt.util.decree.specialhandlers;

import com.volmit.adapt.util.collection.KList;
import com.volmit.adapt.util.decree.DecreeParameterHandler;

public class DummyHandler implements DecreeParameterHandler<Object> {
    @Override
    public KList getPossibilities() {
        return null;
    }

    public boolean isDummy() {
        return true;
    }

    @Override
    public String toString(Object o) {
        return null;
    }

    @Override
    public Object parse(String in, boolean force) {
        return null;
    }

    @Override
    public boolean supports(Class type) {
        return false;
    }
}
