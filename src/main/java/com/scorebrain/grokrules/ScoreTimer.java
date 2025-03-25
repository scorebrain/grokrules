package com.scorebrain.grokrules;

import com.google.gson.JsonObject;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;

public class ScoreTimer implements ScoreElement {
    private String id;
    private int initialSeconds;
    private int currentSeconds;
    private boolean isCountingDown;
    private boolean isRunning;
    private Timeline timeline;
    private RuleEngine ruleEngine;
    private int flashZoneTrigger; // New: Trigger threshold for flashing
    private int flashPattern;     // New: Pattern ID for flashing

    public ScoreTimer(RuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    @Override
    public void initialize(JsonObject config) {
        this.id = config.get("id").getAsString();
        this.initialSeconds = config.get("initialSeconds").getAsInt();
        this.isCountingDown = config.get("isCountingDown").getAsBoolean();
        this.currentSeconds = initialSeconds;
        this.isRunning = false;
        this.flashZoneTrigger = config.has("flashZoneTrigger") ? config.get("flashZoneTrigger").getAsInt() : 0;
        this.flashPattern = config.has("flashPattern") ? config.get("flashPattern").getAsInt() : 0;
        setupTimeline();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void reset() {
        if (timeline != null) timeline.stop();
        currentSeconds = initialSeconds;
        isRunning = false;
        setupTimeline();
    }

    @Override
    public String getDisplayValue() {
        int minutes = currentSeconds / 60;
        int seconds = currentSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    public boolean increment() {
        if (isRunning) return false;
        currentSeconds++;
        return true;
    }

    public boolean decrement() {
        if (isRunning || currentSeconds <= 0) return false;
        currentSeconds--;
        return true;
    }

    public boolean set(int seconds) {
        if (isRunning) return false;
        if (seconds < 0) return false;
        currentSeconds = seconds;
        setupTimeline();
        return true;
    }

    public boolean startstop() {
        if (isRunning) {
            timeline.stop();
            isRunning = false;
            checkForHorn();
        } else if (currentSeconds > 0) {
            timeline.play();
            isRunning = true;
        } else {
            return false;
        }
        return true;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void start(Runnable onCompleteCallback) {
        if (currentSeconds > 0) {
            timeline.play();
            isRunning = true;
        }
    }

    public int getCurrentSeconds() { // New getter
        return currentSeconds;
    }

    public int getFlashZoneTrigger() { // New getter
        return flashZoneTrigger;
    }

    public int getFlashPattern() { // New getter
        return flashPattern;
    }

    private void setupTimeline() {
        if (timeline != null) timeline.stop();
        timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (isCountingDown && isRunning) {
                currentSeconds--;
                if (currentSeconds <= 0) {
                    timeline.stop();
                    isRunning = false;
                    checkForHorn();
                }
            } else if (!isCountingDown && isRunning) {
                currentSeconds++;
            }
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
    }

    private void checkForHorn() {
        if (currentSeconds <= 0) {
            String hornId = id.equals("timerOne") ? "timerOneHorn" :
                           id.equals("timerTwo") ? "timerTwoHorn" : "timerThreeHorn";
            ScoreIndicator horn = (ScoreIndicator) ruleEngine.getElement(hornId);
            if (horn != null) horn.setCurrentValue(true, false);
        }
    }
}