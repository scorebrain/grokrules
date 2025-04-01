package com.scorebrain.grokrules;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier; // Added import

// Main application class
public class App extends Application {
    private static Scene scene;

    @Override
    public void start(Stage stage) throws IOException {
        scene = new Scene(loadFXML("grokrules"), 650, 650);
        stage.setTitle("ScoreBrain Grok Rules");
        stage.setScene(scene);
        stage.show();
    }

    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource(fxml + ".fxml"));
        return fxmlLoader.load();
    }

    public static void main(String[] args) {
        launch();
    }
}

// ScoreElement interface
interface ScoreElement {
    void initialize(JsonObject config);
    String getId();
    void reset();
    String getDisplayValue();
}

// TimerObserver interface
interface TimerObserver {
    void onTimerStarted(String timerId);
    void onTimerStopped(String timerId);
    void onTimerExpired(String timerId);
    void onThresholdCrossed(String timerId, int threshold);
}

// ScoreEventBus class
class ScoreEventBus {
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

// ScoreTimer class
class ScoreTimer implements ScoreElement {
    private String id;
    private int initialSeconds;
    private int currentSeconds;
    private boolean isRunning;
    private ScoreEventBus eventBus;
    private List<Integer> thresholds;
    private Timeline timeline;
    private int flashZoneThreshold;
    private String flashZonePattern;

    public ScoreTimer(ScoreEventBus eventBus) {
        this.eventBus = eventBus;
        this.thresholds = new ArrayList<>();
    }

    @Override
    public void initialize(JsonObject config) {
        this.id = config.get("id").getAsString();
        this.initialSeconds = config.get("initialSeconds").getAsInt();
        this.currentSeconds = initialSeconds;
        if (config.has("thresholds")) {
            JsonArray thresholdsArray = config.getAsJsonArray("thresholds");
            for (int i = 0; i < thresholdsArray.size(); i++) {
                this.thresholds.add(thresholdsArray.get(i).getAsInt());
            }
        }
        // Initialize Flash Zone attributes
        this.flashZoneThreshold = config.has("flashZoneThreshold") ? config.get("flashZoneThreshold").getAsInt() : -1;
        this.flashZonePattern = config.has("flashZonePattern") ? config.get("flashZonePattern").getAsString() : null;
    }

    @Override
    public String getId() {
        return id;
    }

    public boolean startstop() {
        if (isRunning) {
            if (timeline != null) {
                timeline.pause();
            }
            isRunning = false;
            eventBus.notifyTimerStopped(id);
            if (currentSeconds <= 0) {
                eventBus.notifyTimerExpired(id);
            }
        } else {
            if (currentSeconds > 0) {
                if (timeline == null) {
                    timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
                        currentSeconds--;
                        checkThresholds();
                        if (currentSeconds <= 0) {
                            timeline.stop();
                            isRunning = false;
                            eventBus.notifyTimerExpired(id);
                        }
                    }));
                    timeline.setCycleCount(Timeline.INDEFINITE);
                }
                timeline.play();
                isRunning = true;
                eventBus.notifyTimerStarted(id);
            } else {
                return false;
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
            timeline.stop();
        }
        currentSeconds = initialSeconds;
        isRunning = false;
    }
    
    public int getFlashZoneThreshold() {
        return flashZoneThreshold;
    }

    public String getFlashZonePattern() {
        return flashZonePattern;
    }
}

// ScoreIndicator class
class ScoreIndicator implements ScoreElement, TimerObserver {
    private String id;
    private boolean currentValue;
    private String observedTimerId;
    private String triggerEvent;
    private String pattern;
    private Supplier<String> selectedTimerSupplier; // Added field to track selected timer

    public ScoreIndicator(String id, String observedTimerId, String triggerEvent, String pattern) {
        this.id = id;
        this.observedTimerId = observedTimerId;
        this.triggerEvent = triggerEvent;
        this.pattern = pattern;
        this.currentValue = false;
    }

    @Override
    public void initialize(JsonObject config) {
        this.id = config.get("id").getAsString();
        this.currentValue = config.has("initialValue") ? config.get("initialValue").getAsBoolean() : false;
        this.observedTimerId = config.has("observedTimerId") ? config.get("observedTimerId").getAsString() : null;
        this.triggerEvent = config.has("triggerEvent") ? config.get("triggerEvent").getAsString() : null;
        this.pattern = config.has("pattern") ? config.get("pattern").getAsString() : null;
    }

    /** Sets the supplier that provides the currently selected timer ID. */
    public void setSelectedTimerSupplier(Supplier<String> supplier) {
        this.selectedTimerSupplier = supplier;
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
            // Only activate if the timer is currently selected
            if (selectedTimerSupplier != null && timerId.equals(selectedTimerSupplier.get())) {
                setCurrentValue(true);
            }
        }
    }

    @Override
    public void onThresholdCrossed(String timerId, int threshold) {
        if (timerId.equals(observedTimerId) && triggerEvent.startsWith("threshold:")) {
            String[] parts = triggerEvent.split(":");
            if (parts.length == 2) {
                try {
                    int triggerThreshold = Integer.parseInt(parts[1]);
                    if (triggerThreshold == threshold) {
                        // Only activate if the timer is currently selected
                        if (selectedTimerSupplier != null && timerId.equals(selectedTimerSupplier.get())) {
                            setCurrentValue(true);
                        }
                    }
                } catch (NumberFormatException e) {
                    // Ignore if threshold value is malformed
                }
            }
        }
    }

    public void setCurrentValue(boolean value) {
        this.currentValue = value;
    }

    public boolean getCurrentValue() {
        return currentValue;
    }

    public String getPattern() {
        return pattern;
    }

    @Override
    public void reset() {
        currentValue = false;
    }

    @Override
    public String getDisplayValue() {
        return currentValue ? "*" : "";
    }
}

class ScoreCounter implements ScoreElement {
    private String id;
    private int currentValue;
    private int minValue;
    private int maxValue;
    private int initialValue;

    @Override
    public void initialize(JsonObject config) {
        this.id = config.get("id").getAsString();
        this.initialValue = config.get("initialValue").getAsInt();
        this.minValue = config.get("minValue").getAsInt();
        this.maxValue = config.get("maxValue").getAsInt();
        this.currentValue = initialValue;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void reset() {
        currentValue = initialValue;
    }

    @Override
    public String getDisplayValue() {
        return String.valueOf(currentValue);
    }

    public void increment(int amount) {
        currentValue = Math.min(currentValue + amount, maxValue);
    }

    public void decrement(int amount) {
        currentValue = Math.max(currentValue - amount, minValue);
    }

    public void setCurrentValue(int value) {
        currentValue = Math.max(minValue, Math.min(value, maxValue));
    }

    public int getCurrentValue() {
        return currentValue;
    }
}

// RuleEngine class
class RuleEngine {
    private ScoreEventBus eventBus = new ScoreEventBus();
    private Map<String, ScoreElement> elements = new HashMap<>();
    private List<String> timerIds = new ArrayList<>();

    public RuleEngine(String ruleFilePath) {
        loadRules(ruleFilePath);
    }

    private void loadRules(String ruleFilePath) {
        try {
            JsonObject json = new Gson().fromJson(new FileReader(ruleFilePath), JsonObject.class);
            JsonArray elementsArray = json.getAsJsonArray("elements");

            for (JsonElement element : elementsArray) {
                JsonObject config = element.getAsJsonObject();
                String type = config.get("type").getAsString();
                ScoreElement scoreElement;

                if ("ScoreTimer".equals(type)) {
                    scoreElement = new ScoreTimer(eventBus);
                    scoreElement.initialize(config);
                    elements.put(scoreElement.getId(), scoreElement);
                    timerIds.add(scoreElement.getId()); // Add timer ID to list
                } else if ("ScoreIndicator".equals(type)) {
                    String indID = config.get("id").getAsString();
                    String indObserverID = config.has("observedTimerId") ? config.get("observedTimerId").getAsString() : null;
                    String indTriggerEvent = config.has("triggerEvent") ? config.get("triggerEvent").getAsString() : null;
                    String indPattern = config.has("pattern") ? config.get("pattern").getAsString() : null;
                    scoreElement = new ScoreIndicator(indID, indObserverID, indTriggerEvent, indPattern);
                    scoreElement.initialize(config);
                    elements.put(scoreElement.getId(), scoreElement);
                    ScoreIndicator indicator = (ScoreIndicator) scoreElement;
                    String timerId = config.has("observedTimerId") ? config.get("observedTimerId").getAsString() : null;
                    if (timerId != null) {
                        eventBus.registerTimerObserver(timerId, indicator);
                    }
                } else if ("ScoreCounter".equals(type)) {
                    scoreElement = new ScoreCounter();
                    scoreElement.initialize(config);
                    elements.put(scoreElement.getId(), scoreElement);
                } else {
                    continue;
                }
/*
                if (scoreElement instanceof ScoreIndicator) {
                    ScoreIndicator indicator = (ScoreIndicator) scoreElement;
                    String timerId = config.has("observedTimerId") ? config.get("observedTimerId").getAsString() : null;
                    if (timerId != null) {
                        eventBus.registerTimerObserver(timerId, indicator);
                    }
                }
*/
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ScoreElement getElement(String id) {
        return elements.get(id);
    }

    public Collection<ScoreElement> getElements() {
        return elements.values();
    }
    
    public List<String> getTimerIds() {
        return timerIds;
    }
}