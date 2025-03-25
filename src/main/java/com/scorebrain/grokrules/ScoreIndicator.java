package com.scorebrain.grokrules;

import com.google.gson.JsonObject;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

public class ScoreIndicator implements ScoreElement {
    private String id;
    private boolean currentValue;
    private int currentPattern; // 1 = single, 2 = double beep, 3 = triple beep
    private Timeline patternTimeline;
	private boolean patternRunning = false; // Tracks if a pattern is active

    public ScoreIndicator() {
        this.patternTimeline = new Timeline();
    }

    @Override
    public void initialize(JsonObject config) {
        this.id = config.get("id").getAsString();
        this.currentValue = config.has("initialValue") ? config.get("initialValue").getAsBoolean() : false;
        this.currentPattern = config.has("currentPattern") ? config.get("currentPattern").getAsInt() : 0;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void reset() {
        currentValue = false;
        patternTimeline.stop();
    }

    @Override
    public String getDisplayValue() {
        return currentValue ? "*" : "";
    }

    public boolean getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(boolean value, boolean force) {
        if (force || value != currentValue) {
            currentValue = value;
            if (value && currentPattern > 0 && !patternRunning) {
                patternRunning = true;
                patternTimeline.stop();
                patternTimeline.getKeyFrames().clear();
                switch (currentPattern) {
                    case 1: // Single beep
                        patternTimeline.getKeyFrames().addAll(
                            new KeyFrame(Duration.ZERO, e -> currentValue = true),
                            new KeyFrame(Duration.seconds(2.5), e -> {
                                currentValue = false;
                                patternRunning = false;
                                patternTimeline.stop();
                            })
                        );
                        break;
                    case 2: // Double beep
                        patternTimeline.getKeyFrames().addAll(
                            new KeyFrame(Duration.ZERO, e -> currentValue = true),
                            new KeyFrame(Duration.millis(300), e -> currentValue = false),
                            new KeyFrame(Duration.millis(600), e -> currentValue = true),
                            new KeyFrame(Duration.millis(900), e -> {
                                currentValue = false;
                                patternRunning = false;
                                patternTimeline.stop();
                            })
                        );
                        break;
                    case 3: // Triple beep
                        patternTimeline.getKeyFrames().addAll(
                            new KeyFrame(Duration.ZERO, e -> currentValue = true),
                            new KeyFrame(Duration.millis(500), e -> currentValue = false),
                            new KeyFrame(Duration.millis(1000), e -> currentValue = true),
                            new KeyFrame(Duration.millis(1500), e -> currentValue = false),
                            new KeyFrame(Duration.millis(2000), e -> currentValue = true),
                            new KeyFrame(Duration.millis(3000), e -> {
                                currentValue = false;
                                patternRunning = false;
                                patternTimeline.stop();
                            })
                        );
                        break;
                }
                patternTimeline.playFromStart();
            } else if (!value) {
                currentValue = false;
                patternTimeline.stop();
                patternRunning = false;
            }
        }
    }

    public int getCurrentPattern() {
        return currentPattern;
    }
}