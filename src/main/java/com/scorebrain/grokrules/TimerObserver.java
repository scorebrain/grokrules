package com.scorebrain.grokrules;

public interface TimerObserver {
    void onTimerStarted(String timerId);
    void onTimerStopped(String timerId);
    void onTimerExpired(String timerId);
    void onThresholdCrossed(String timerId, int threshold);
}