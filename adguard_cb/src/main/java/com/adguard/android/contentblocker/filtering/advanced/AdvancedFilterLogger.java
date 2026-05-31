package com.adguard.android.contentblocker.filtering.advanced;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AdvancedFilterLogger {

    private final AdvancedFilterEvent[] events;
    private int nextIndex;
    private int size;

    public AdvancedFilterLogger(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        events = new AdvancedFilterEvent[capacity];
    }

    public synchronized void record(AdvancedFilterEvent event) {
        if (event == null) {
            return;
        }
        events[nextIndex] = event;
        nextIndex = (nextIndex + 1) % events.length;
        if (size < events.length) {
            size++;
        }
    }

    public synchronized List<AdvancedFilterEvent> snapshot() {
        if (size == 0) {
            return Collections.emptyList();
        }

        List<AdvancedFilterEvent> result = new ArrayList<>(size);
        int start = size == events.length ? nextIndex : 0;
        for (int i = 0; i < size; i++) {
            result.add(events[(start + i) % events.length]);
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized void clear() {
        for (int i = 0; i < events.length; i++) {
            events[i] = null;
        }
        nextIndex = 0;
        size = 0;
    }
}
