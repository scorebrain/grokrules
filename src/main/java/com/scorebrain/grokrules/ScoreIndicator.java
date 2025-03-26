package com.scorebrain.grokrules;

import com.google.gson.JsonObject;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

public class ScoreIndicator implements ScoreElement, TimerObserver {
    private String id;
    private boolean currentValue;
    private String observedTimerId;
    private String triggerEvent; // e.g., "started", "expired", "threshold:10"
    private String pattern; // e.g., "flash:500" for 500ms flashing

    @Override
    public void initialize(JsonObject config) {
        this.id = config.get("id").getAsString();
        this.currentValue = config.has("initialValue") ? config.get("initialValue").getAsBoolean() : false;
        this.observedTimerId = config.has("observedTimerId") ? config.get("observedTimerId").getAsString() : null;
        this.triggerEvent = config.has("triggerEvent") ? config.get("triggerEvent").getAsString() : null;
        this.pattern = config.has("pattern") ? config.get("pattern").getAsString() : null;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void onTimerStarted(String timerId) {
        if (timerId.equals(observedTimerId) && "started".equals(triggerEvent)) {
            setCurrentValue(true);
        }
    }

    @Override
    public void onTimerStopped(String timerId) {
        if (timerId.equals(observedTimerId) && "stopped".equals(triggerEvent)) {
            setCurrentValue(false);
        }
    }

    @Override
    public void onTimerExpired(String timerId) {
        if (timerId.equals(observedTimerId) && "expired".equals(triggerEvent)) {
            setCurrentValue(true);
            applyPattern();
        }
    }

    @Override
    public void onThresholdCrossed(String timerId, int threshold) {
        if (timerId.equals(observedTimerId) && triggerEvent != null && triggerEvent.equals("threshold:" + threshold)) {
            setCurrentValue(true);
            applyPattern();
        }
    }

    public void setCurrentValue(boolean value) {
        this.currentValue = value;
        if (!value) {
            stopPattern(); // Ensure horn turns off
        } else if (pattern != null) {
            applyPattern();
        }
    }

    private void applyPattern() {
        if (pattern != null && pattern.startsWith("flash:")) {
            int millis = Integer.parseInt(pattern.split(":")[1]); // e.g., "flash:500" means 500ms intervals
            Timeline flash = new Timeline(
                new KeyFrame(Duration.millis(0), e -> this.currentValue = true),      // Start ON
                new KeyFrame(Duration.millis(millis), e -> this.currentValue = false), // OFF
                new KeyFrame(Duration.millis(millis * 2), e -> this.currentValue = true),  // ON
                new KeyFrame(Duration.millis(millis * 3), e -> this.currentValue = false), // OFF
                new KeyFrame(Duration.millis(millis * 4), e -> this.currentValue = false)  // Final OFF
            );
            flash.setCycleCount(1); // Run the pattern once
            flash.setOnFinished(e -> this.currentValue = false); // Ensure OFF when done
            flash.play();
        }
    }

    private void stopPattern() {
        this.currentValue = false;
        // Stop any ongoing Timeline if needed
    }

    public boolean getCurrentValue() {
        return currentValue;
    }
    
    @Override
    public void reset() {
        currentValue = false;
        this.stopPattern();
    }

    @Override
    public String getDisplayValue() {
        return currentValue ? "*" : "";
    }
}