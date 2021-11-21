package com.volmit.adapt.util;

public interface IRare {
    int getRarity();

    static int get(Object v) {
        return v instanceof IRare ? ((IRare) v).getRarity() : 1;
    }
}
