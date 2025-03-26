package com.scorebrain.grokrules;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;
import java.util.ArrayList;
import java.util.List;

public class ScoreTimer implements ScoreElement {
    private String id;
    private int initialSeconds;
    private int currentSeconds;
    private boolean isRunning;
    private ScoreEventBus eventBus;
    private List<Integer> thresholds;
    private Timeline timeline;

    public ScoreTimer(ScoreEventBus eventBus) {
        this.eventBus = eventBus;
        this.thresholds = new ArrayList<>();
    }

    @Override
    public void initialize(JsonObject config) {
        this.id = config.get("id").getAsString();
        this.initialSeconds = config.get("initialSeconds").getAsInt(); // Store it
        this.currentSeconds = initialSeconds;
        if (config.has("thresholds")) {
            JsonArray thresholdsArray = config.getAsJsonArray("thresholds");
            // this.thresholds = new ArrayList<>();
            for (int i = 0; i < thresholdsArray.size(); i++) {
                this.thresholds.add(thresholdsArray.get(i).getAsInt());
            }
        }
    }

    @Override
    public String getId() {
        return id;
    }

    public boolean startstop() {
        if (isRunning) {
            if (timeline != null) {
                timeline.pause(); // Pause the existing timeline
            }
            isRunning = false;
            eventBus.notifyTimerStopped(id);
            if (currentSeconds <= 0) {
                eventBus.notifyTimerExpired(id);
            }
        } else {
            if (currentSeconds > 0) { // Only start if time remains
                if (timeline == null) {
                    // Create the timeline only if it doesn’t exist
                    timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
                        currentSeconds--;
                        checkThresholds();
                        if (currentSeconds <= 0) {
                            timeline.stop();
                            isRunning = false;
                            eventBus.notifyTimerExpired(id);
                        }
                    }));
                    timeline.setCycleCount(Timeline.INDEFINITE); // Run until stopped
                }
                timeline.play(); // Start or resume the timeline
                isRunning = true;
                eventBus.notifyTimerStarted(id);
            } else {
                return false; // Can’t start if time is already 0
            }
        }
        return true;
    }

    private void checkThresholds() {
        for (int threshold : thresholds) {
            if (currentSeconds == threshold) {
                eventBus.notifyThresholdCrossed(id, threshold);
            }
        }
    }

    public void setValue(int seconds) {
        this.currentSeconds = seconds;
    }

    public void increment() {
        this.currentSeconds++;
    }

    public void decrement() {
        if (this.currentSeconds > 0) this.currentSeconds--;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public int getCurrentValue() {
        return currentSeconds;
    }

    @Override
    public String getDisplayValue() {
        return String.format("%02d:%02d", currentSeconds / 60, currentSeconds % 60);
    }
    
    @Override
    public void reset() {
        if (timeline != null) {
            timeline.stop(); // Stop the timeline
        }
        currentSeconds = initialSeconds; // Reset to initial value
        isRunning = false;
    }
    /*
    private void setupTimeline() {
        if (timeline == null) {  // Only create if it doesn’t exist
            timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
                if (isCountingDown && isRunning) {
                    System.out.println("Timer " + id + " decrementing to " + (currentSeconds - 1));
                    currentSeconds--;
                    for (int threshold : thresholds) {
                        if (currentSeconds == threshold) {
                            eventBus.notifyThresholdCrossed(id, threshold);
                        }
                    }
                    if (currentSeconds <= 0) {
                        timeline.stop();  // Stop when expired
                        isRunning = false;
                        eventBus.notifyTimerExpired(id);
                    }
                } else if (!isCountingDown && isRunning) {
                    currentSeconds++;
                }
            }));
            timeline.setCycleCount(Timeline.INDEFINITE);
        } else {
            timeline.stop();  // Ensure it’s stopped before reuse
        }
    }
   */
}