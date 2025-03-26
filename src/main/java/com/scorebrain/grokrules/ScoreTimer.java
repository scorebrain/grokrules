package com.scorebrain.grokrules;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ScoreTimer implements ScoreElement {
    private String id;
    private int currentSeconds;
    private boolean isRunning;
    private ScoreEventBus eventBus;
    private List<Integer> thresholds; // For Flash Zone or other triggers

    public ScoreTimer(ScoreEventBus eventBus) {
        this.eventBus = eventBus;
        this.thresholds = new ArrayList<>();
    }

    @Override
public void initialize(JsonObject config) {
    this.id = config.get("id").getAsString();
    this.currentSeconds = config.get("initialSeconds").getAsInt();
    if (config.has("thresholds")) {
        JsonArray thresholdsArray = config.getAsJsonArray("thresholds");
        this.thresholds = new ArrayList<>();
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
            isRunning = false;
            eventBus.notifyTimerStopped(id);
            if (currentSeconds <= 0) {
                eventBus.notifyTimerExpired(id);
            }
        } else {
            isRunning = true;
            eventBus.notifyTimerStarted(id);
            // Start countdown logic (e.g., a Timeline or Thread)
            startCountdown();
        }
        return true;
    }

    private void startCountdown() {
        // Assuming JavaFX Timeline for countdown
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            currentSeconds--;
            checkThresholds();
            if (currentSeconds <= 0) {
                isRunning = false;
                eventBus.notifyTimerExpired(id);
            }
        }));
        timeline.setCycleCount(currentSeconds);
        timeline.play();
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
        //if (timeline != null) timeline.stop();
        //currentSeconds = initialSeconds;
        isRunning = false;
        //setupTimeline();
    }
}