package com.scorebrain.grokrules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScoreEventBus {
    private Map<String, List<TimerObserver>> observers = new HashMap<>();

    public void registerTimerObserver(String timerId, TimerObserver observer) {
        observers.computeIfAbsent(timerId, k -> new ArrayList<>()).add(observer);
    }

    public void notifyTimerStarted(String timerId) {
        List<TimerObserver> list = observers.getOrDefault(timerId, Collections.emptyList());
        for (TimerObserver observer : list) {
            observer.onTimerStarted(timerId);
        }
    }

    public void notifyTimerStopped(String timerId) {
        List<TimerObserver> list = observers.getOrDefault(timerId, Collections.emptyList());
        for (TimerObserver observer : list) {
            observer.onTimerStopped(timerId);
        }
    }

    public void notifyTimerExpired(String timerId) {
        List<TimerObserver> list = observers.getOrDefault(timerId, Collections.emptyList());
        for (TimerObserver observer : list) {
            observer.onTimerExpired(timerId);
        }
    }

    public void notifyThresholdCrossed(String timerId, int threshold) {
        List<TimerObserver> list = observers.getOrDefault(timerId, Collections.emptyList());
        for (TimerObserver observer : list) {
            observer.onThresholdCrossed(timerId, threshold);
        }
    }
}