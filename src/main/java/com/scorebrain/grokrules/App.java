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
//import javafx.util.Duration;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier; // Added import
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;


// Main application class
public class App extends Application {
    private static Scene scene;

    @Override
    public void start(Stage stage) throws IOException {
        
        // Load the FXML file
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("grokrules.fxml"));
        Parent root = fxmlLoader.load();

        // Get the controller instance
        ScoreboardController controller = fxmlLoader.getController();

        // Pass the root node to the controller
        controller.setRoot(root);

        // Configure the buttons after setting the root
        controller.configureButtons();

        // Set up the scene and show the stage
        scene = new Scene(root, 650, 650);
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
    private long initialValue; // Nanoseconds
    private long currentValue; // Nanoseconds
    private boolean isRunning;
    private Instant startTimeStamp;
    private ScoreEventBus eventBus;
    private List<Integer> thresholds; // Seconds
    private int flashZoneThreshold; // Seconds
    private String flashZonePattern;
    private boolean expiredNotified;
    private boolean isDownCounting;
    private long minValue; // Nanoseconds
    private long maxValue; // Nanoseconds
    private long rolloverValue; // Nanoseconds
    private boolean canRollUp;
    private boolean canRollDown;

    public ScoreTimer(ScoreEventBus eventBus) {
        this.eventBus = eventBus;
        this.thresholds = new ArrayList<>();
        this.expiredNotified = false;
    }

    @Override
    public void initialize(JsonObject config) {
        this.id = config.get("id").getAsString();
        this.initialValue = config.get("initialSeconds").getAsLong() * 1_000_000_000L;
        this.currentValue = initialValue;
        this.isRunning = false;
        this.startTimeStamp = null;
        this.expiredNotified = false;
        this.isDownCounting = config.has("isDownCounting") ? config.get("isDownCounting").getAsBoolean() : true;
        this.minValue = config.has("minValue") ? config.get("minValue").getAsLong() : 0L;
        this.maxValue = config.has("maxValue") ? config.get("maxValue").getAsLong() : 59999990000000L;
        this.rolloverValue = config.has("rolloverValue") ? config.get("rolloverValue").getAsLong() : 35999990000000L;
        this.canRollUp = config.has("canRollUp") ? config.get("canRollUp").getAsBoolean() : false;
        this.canRollDown = config.has("canRollDown") ? config.get("canRollDown").getAsBoolean() : false;
        if (config.has("thresholds")) {
            JsonArray thresholdsArray = config.getAsJsonArray("thresholds");
            for (int i = 0; i < thresholdsArray.size(); i++) {
                this.thresholds.add(thresholdsArray.get(i).getAsInt());
            }
        }
        this.flashZoneThreshold = config.has("flashZoneThreshold") ? config.get("flashZoneThreshold").getAsInt() : -1;
        this.flashZonePattern = config.has("flashZonePattern") ? config.get("flashZonePattern").getAsString() : null;
    }

    @Override
    public String getId() {
        return id;
    }

    public boolean startstop() {
        if (isRunning) {
            currentValue = getCurrentValueRaw();
            isRunning = false;
            startTimeStamp = null;
            eventBus.notifyTimerStopped(id);
            if (isDownCounting && currentValue <= minValue && !expiredNotified) {
                eventBus.notifyTimerExpired(id);
                expiredNotified = true;
            } else if (!isDownCounting && currentValue >= maxValue && !expiredNotified) {
                eventBus.notifyTimerExpired(id);
                expiredNotified = true;
            }
        } else {
            if (isDownCounting && currentValue > minValue) {
                startTimeStamp = Instant.now();
                initialValue = currentValue;
                isRunning = true;
                expiredNotified = false;
                eventBus.notifyTimerStarted(id);
            } else if (!isDownCounting && currentValue < maxValue) {
                startTimeStamp = Instant.now();
                initialValue = currentValue;
                isRunning = true;
                expiredNotified = false;
                eventBus.notifyTimerStarted(id);
            } else {
                return false;
            }
        }
        return true;
    }

    public void checkThresholds() {
        int currentSeconds = (int) (getCurrentValue() / 1_000_000_000L);
        for (int threshold : thresholds) {
            if (currentSeconds == threshold) {
                eventBus.notifyThresholdCrossed(id, threshold);
            }
        }
        long currentValue = getCurrentValue();
        if (isDownCounting && currentValue <= minValue && isRunning && !expiredNotified) {
            isRunning = false;
            startTimeStamp = null;
            this.currentValue = minValue;
            eventBus.notifyTimerExpired(id);
            expiredNotified = true;
        } else if (!isDownCounting && isRunning) {
            if (currentValue >= rolloverValue && currentValue < maxValue && canRollUp) {
                // Rollover to minValue when reaching rolloverValue
                long elapsedSinceStart = currentValue - initialValue;
                long cycleLength = rolloverValue - minValue;
                long cyclesCompleted = (elapsedSinceStart / cycleLength) + 1;
                initialValue = minValue;
                startTimeStamp = Instant.now().minusNanos(elapsedSinceStart % cycleLength);
                currentValue = getCurrentValueRaw();
            } else if (currentValue >= maxValue && !expiredNotified) {
                isRunning = false;
                startTimeStamp = null;
                this.currentValue = maxValue;
                eventBus.notifyTimerExpired(id);
                expiredNotified = true;
            }
        }
    }

    public void setValue(int seconds) {
        long newValue = seconds * 1_000_000_000L;
        this.currentValue = Math.max(minValue, Math.min(maxValue, newValue));
        if (!isRunning) {
            this.initialValue = currentValue;
        }
        this.expiredNotified = false;
    }

    public void increment() {
        long newValue = currentValue + 1_000_000_000L;
        if (newValue <= maxValue) {
            this.currentValue = newValue;
            if (!isRunning) {
                this.initialValue = currentValue;
            }
            this.expiredNotified = false;
        }
    }

    public void decrement() {
        long newValue = currentValue - 1_000_000_000L;
        if (newValue >= minValue) {
            this.currentValue = newValue;
            if (!isRunning) {
                this.initialValue = currentValue;
            }
            this.expiredNotified = false;
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    private long getCurrentValueRaw() {
        if (isRunning && startTimeStamp != null) {
            long elapsedNanos = Duration.between(startTimeStamp, Instant.now()).toNanos();
            if (isDownCounting) {
                long remaining = initialValue - elapsedNanos;
                if (remaining <= minValue) {
                    return canRollDown ? rolloverValue : minValue;
                }
                return remaining;
            } else {
                long elapsed = initialValue + elapsedNanos;
                return elapsed;
            }
        }
        return currentValue;
    }

    public long getCurrentValue() {
        long rawValue = getCurrentValueRaw();
        return Math.max(minValue, Math.min(maxValue, rawValue));
    }

    @Override
    public String getDisplayValue() {
        long nanos = getCurrentValue();
        long totalSeconds = nanos / 1_000_000_000L;
        if (totalSeconds >= 60) {
            long minutes = totalSeconds / 60;
            long seconds = totalSeconds % 60;
            return String.format("%02d:%02d", minutes, seconds);
        } else {
            long seconds = totalSeconds;
            long tenths = (nanos % 1_000_000_000L) / 100_000_000L;
            return String.format("%02d.%d", seconds, tenths);
        }
    }

    @Override
    public void reset() {
        if (isRunning) {
            startstop();
        }
        currentValue = initialValue;
        startTimeStamp = null;
        expiredNotified = false;
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
    private JsonObject uiConfig;

    public RuleEngine(String ruleFilePath) {
        loadRules(ruleFilePath);
    }

    private void loadRules(String ruleFilePath) {
        try {
            JsonObject json = new Gson().fromJson(new FileReader(ruleFilePath), JsonObject.class);
            if (json == null) {
                System.err.println("Failed to parse JSON: JSON is null");
                return;
            }
            uiConfig = json.has("uiConfig") ? json.getAsJsonObject("uiConfig") : new JsonObject();
            JsonArray elementsArray = json.getAsJsonArray("elements");
            if (elementsArray == null) {
                System.err.println("No 'elements' array in JSON");
                return;
            }
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
            System.out.println("RuleEngine loaded, timerIds: " + timerIds); // Debug output
        } catch (Exception e) {
            System.err.println("Error loading rules from " + ruleFilePath + ":");
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
    
    public JsonObject getUiConfig() {
        return uiConfig;
    }
}