package com.scorebrain.grokrules;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("MPController.fxml"));
        Parent root = fxmlLoader.load();

        // Get the controller instance
        ScoreboardController controller = fxmlLoader.getController();

        // Pass the root node to the controller
        controller.setRoot(root);

        // Configure the buttons after setting the root
        controller.configureButtons();

        // Set up the scene and show the stage
        scene = new Scene(root, 1536, 868);
        stage.setTitle("ScoreBrain Grok Rules");
        stage.setScene(scene);
        stage.show();
    }
/*
    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource(fxml + ".fxml"));
        return fxmlLoader.load();
    }
*/
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
    private long initialValue;
    private long currentValue;
    private int currentHours;
    private int currentMinutes;
    private int currentSeconds;
    private int currentMillis;
    private int maxHours;
    private int maxMinutes;
    private int maxSeconds;
    private int maxMillis;
    private boolean isRunning;
    private Instant startTimeStamp;
    private ScoreEventBus eventBus;
    private List<Integer> thresholds;
    private int flashZoneThreshold;
    private String flashZonePattern;
    private boolean expiredNotified;
    private boolean isUpCounting;
    private long minValue;
    private long maxValue;
    private long rolloverValue;
    private boolean canRollUp;
    private boolean canRollDown;
    private boolean allowShift;
    private String constrainWhen; // New field
    private boolean visibility = true;
    private String displayFormat;
    private int textScoreboardLine;
    private int textScoreboardStartPos;
    private int textScoreboardEndPos;
    private JsonArray gameLinks;

    public ScoreTimer(ScoreEventBus eventBus) {
        this.eventBus = eventBus;
        this.thresholds = new ArrayList<>();
        this.expiredNotified = false;
    }

    @Override
    public void initialize(JsonObject config) {
        this.id = config.get("id").getAsString();
        this.initialValue = config.has("initialValue") ? config.get("initialValue").getAsLong() : 0L; // Changed from initialSeconds
        this.currentValue = initialValue;
        updateTimeUnits(initialValue);
        this.isRunning = false;
        this.startTimeStamp = null;
        this.expiredNotified = false;
        this.maxHours = config.has("maxHours") ? config.get("maxHours").getAsInt() : 9;
        this.maxMinutes = config.has("maxMinutes") ? config.get("maxMinutes").getAsInt() : 59;
        this.maxSeconds = config.has("maxSeconds") ? config.get("maxSeconds").getAsInt() : 59;
        this.maxMillis = config.has("maxMillis") ? config.get("maxMillis").getAsInt() : 999;
        this.isUpCounting = config.has("isUpCounting") ? config.get("isUpCounting").getAsBoolean() : false;
        this.allowShift = config.has("allowShift") ? config.get("allowShift").getAsBoolean() : true;
        this.minValue = config.has("minValue") ? config.get("minValue").getAsLong() : 0L;
        long derivedMax = this.maxHours * 60L * 60L * 1_000_000_000L;
        derivedMax = derivedMax + this.maxMinutes * 60L * 1_000_000_000L;
        derivedMax = derivedMax + this.maxSeconds * 1_000_000_000L;
        derivedMax = derivedMax + this.maxMillis * 1_000_000L;
        this.maxValue = config.has("maxValue") ? config.get("maxValue").getAsLong() : derivedMax;
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
        this.constrainWhen = config.has("constrainWhen") ? config.get("constrainWhen").getAsString() : null;
        this.visibility = config.has("initialVisibility") ? config.get("initialVisibility").getAsBoolean() : true;
        this.displayFormat = config.has("format") ? config.get("format").getAsString() : "";
        this.textScoreboardLine = config.has("textScoreboardLine") ? config.get("textScoreboardLine").getAsInt() : 0;
        this.textScoreboardStartPos = config.has("textScoreboardStartPos") ? config.get("textScoreboardStartPos").getAsInt() : 0;
        this.textScoreboardEndPos = config.has("textScoreboardEndPos") ? config.get("textScoreboardEndPos").getAsInt() : 0;
        this.gameLinks = config.has("gameLinks") ? config.get("gameLinks").getAsJsonArray() : null;

        enforceConstraints(); // Apply constraints after initialization
    }

   public void synchronize(ScoreTimer synchy) {
        this.initialValue = synchy.initialValue;
        this.startTimeStamp = synchy.startTimeStamp;
        this.maxHours = synchy.maxHours;
        this.maxMinutes = synchy.maxMinutes;
        this.maxSeconds = synchy.maxSeconds;
        this.maxMillis = synchy.maxMillis;
        this.isUpCounting = synchy.isUpCounting;
        this.allowShift = synchy.allowShift;
        this.minValue = synchy.minValue;
        this.maxValue = synchy.maxValue;
        this.rolloverValue = synchy.rolloverValue;
        this.canRollUp = synchy.canRollUp;
        this.canRollDown = synchy.canRollDown;
        /*
        this.expiredNotified = synchy.expiredNotified;
        this.thresholds = synchy.thresholds;
        this.flashZoneThreshold = synchy.flashZoneThreshold;
        this.flashZonePattern = synchy.flashZonePattern;
        this.constrainWhen = synchy.constrainWhen;
        this.visibility = config.has("initialVisibility") ? config.get("initialVisibility").getAsBoolean() : true;
        this.displayFormat = config.has("format") ? config.get("format").getAsString() : "";
        this.textScoreboardLine = config.has("textScoreboardLine") ? config.get("textScoreboardLine").getAsInt() : 0;
        this.textScoreboardStartPos = config.has("textScoreboardStartPos") ? config.get("textScoreboardStartPos").getAsInt() : 0;
        this.textScoreboardEndPos = config.has("textScoreboardEndPos") ? config.get("textScoreboardEndPos").getAsInt() : 0;
        this.gameLinks = config.has("gameLinks") ? config.get("gameLinks").getAsJsonArray() : null;
        */
        this.currentValue = synchy.getCurrentValueRaw();
        updateTimeUnits(this.currentValue);
        this.isRunning = synchy.isRunning;
    }

     private void enforceConstraints() {
        if (constrainWhen != null) {
            String[] parts = constrainWhen.split("->");
            if (parts.length == 2) {
                String condition = parts[0].trim();
                String action = parts[1].trim();
                if (evaluateCondition(condition)) {
                    String[] actionParts = action.split("=");
                    if (actionParts.length == 2) {
                        String attr = actionParts[0].trim();
                        String value = actionParts[1].trim();
                        if (attr.equals("allowShift")) {
                            boolean newValue = Boolean.parseBoolean(value);
                            if (this.allowShift != newValue) {
                                System.out.println("Warning: Constraint '" + constrainWhen + "' overrides initial allowShift value for " + id);
                                this.allowShift = newValue;
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean evaluateCondition(String condition) {
        String[] conditionParts = condition.split("==");
        if (conditionParts.length != 2) return false;
        String attr = conditionParts[0].trim();
        String value = conditionParts[1].trim();
        switch (attr) {
            case "isUpCounting":
                return this.isUpCounting == Boolean.parseBoolean(value);
            case "allowShift":
                return this.allowShift == Boolean.parseBoolean(value);
            default:
                return false;
        }
    }

    @Override
    public String getId() {
        return id;
    }

    public boolean startstop() {
        if (isRunning) {
            currentValue = getCurrentValueRaw();
            updateTimeUnits(currentValue);
            isRunning = false;
            startTimeStamp = null;
            eventBus.notifyTimerStopped(id);
            if (!isUpCounting && currentValue <= minValue && !expiredNotified) {  // Reversed logic
                eventBus.notifyTimerExpired(id);
                expiredNotified = true;
            } else if (isUpCounting && currentValue >= maxValue && !expiredNotified) {
                eventBus.notifyTimerExpired(id);
                expiredNotified = true;
            }
        } else {
            if (!isUpCounting && currentValue > minValue) {  // Reversed logic
                startTimeStamp = Instant.now();
                initialValue = currentValue;
                isRunning = true;
                expiredNotified = false;
                eventBus.notifyTimerStarted(id);
            } else if (isUpCounting && currentValue < maxValue) {
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
        updateTimeUnits(currentValue);
        if (!isUpCounting && currentValue <= minValue && isRunning && !expiredNotified) {  // Reversed logic
            isRunning = false;
            startTimeStamp = null;
            this.currentValue = minValue;
            eventBus.notifyTimerExpired(id);
            expiredNotified = true;
        } else if (isUpCounting && isRunning) {
            if (currentValue >= rolloverValue && currentValue < maxValue && canRollUp) {
                long elapsedSinceStart = currentValue - initialValue;
                long cycleLength = rolloverValue - minValue;
                long cyclesCompleted = (elapsedSinceStart / cycleLength) + 1;
                initialValue = minValue;
                startTimeStamp = Instant.now().minusNanos(elapsedSinceStart % cycleLength);
                currentValue = getCurrentValueRaw();
                updateTimeUnits(currentValue);
            } else if (currentValue >= maxValue && !expiredNotified) {
                isRunning = false;
                startTimeStamp = null;
                this.currentValue = maxValue;
                updateTimeUnits(this.currentValue);
                eventBus.notifyTimerExpired(id);
                expiredNotified = true;
            }
        }
    }

    public void setValue(long nanos) {
        this.currentValue = Math.max(minValue, Math.min(maxValue, nanos));
        updateTimeUnits(this.currentValue);
        if (!isRunning) {
            this.initialValue = currentValue;
        }
        this.expiredNotified = false;
    }

    public void increment() {
        long newValue = currentValue + 1_000_000_000L;
        if (newValue <= maxValue) {
            this.currentValue = newValue;
            updateTimeUnits(this.currentValue);
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
            updateTimeUnits(this.currentValue);
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
            if (!isUpCounting) {  // Reversed logic
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
    
    private void updateTimeUnits(long value) {
        long timeShifter = value / 1_000_000L;
        if (timeShifter < 1000) {
            this.currentMillis = (int) timeShifter;
            this.currentSeconds = 0;
            this.currentMinutes = 0;
            this.currentHours = 0;
        } else {
            this.currentMillis = (int) (timeShifter % 1_000L);
            timeShifter = timeShifter / 1_000L;
            if (timeShifter < 60) {
                this.currentSeconds = (int) timeShifter;
                this.currentMinutes = 0;
                this.currentHours = 0;
            } else {
                this.currentSeconds = (int) (timeShifter % 60L);
                timeShifter = timeShifter / 60L;
                if (timeShifter < 60) {
                    this.currentMinutes = (int) timeShifter;
                    this.currentHours = 0;
                } else {
                    this.currentMinutes = (int) (timeShifter % 60);
                    timeShifter = timeShifter / 60L;
                    if (timeShifter < 10) {
                        this.currentHours = (int) timeShifter;
                    } else {
                        this.currentHours = (int) (timeShifter % 10);
                    }
                }
            }
        }
    }

    public long getCurrentValue() {
        long rawValue = getCurrentValueRaw();
        return Math.max(minValue, Math.min(maxValue, rawValue));
    }

    @Override
    public String getDisplayValue() {
        if (this.displayFormat == null) {
            return "";
        } else {
            if ("MM:SS".equals(this.displayFormat)) {
                int totalMinutes = this.currentMinutes + this.currentHours * 60;
                if (totalMinutes > this.maxMinutes) {
                    totalMinutes = this.currentMinutes;
                }
                if (totalMinutes == 0 && this.allowShift) {
                    return String.format("%2d.%1d ", this.currentSeconds, this.currentMillis / 100);
                } else {
                    return String.format("%2d:%02d", totalMinutes, this.currentSeconds);
                }
            } else if ("SS".equals(this.displayFormat)) {
                int totalSeconds = this.currentSeconds + this.currentMinutes * 60;
                if (totalSeconds > this.maxSeconds) {
                    totalSeconds = this.currentSeconds;
                }
                return String.format("%2d", totalSeconds);
            } else {
                return "";
            }
        }
        /*
        long nanos = getCurrentValue();
        long totalSeconds = nanos / 1_000_000_000L;
        if (totalSeconds >= 60) {
            long minutes = totalSeconds / 60;
            long seconds = totalSeconds % 60;
            return String.format("%02d:%02d", minutes, seconds);
        } else if (allowShift) {  // Use allowShift to show tenths
            long seconds = totalSeconds;
            long tenths = (nanos % 1_000_000_000L) / 100_000_000L;
            return String.format("%02d.%d", seconds, tenths);
        } else {
            long seconds = totalSeconds;
            return String.format("%02d", seconds);  // No tenths if allowShift is false
        }*/
    }

    @Override
    public void reset() {
        if (isRunning) {
            startstop();
        }
        currentValue = initialValue;
        updateTimeUnits(currentValue);
        startTimeStamp = null;
        expiredNotified = false;
    }

    public int getFlashZoneThreshold() {
        return flashZoneThreshold;
    }

    public String getFlashZonePattern() {
        return flashZonePattern;
    }
    
    public Long getMinValue() {
        return minValue;
    }
    
    public Long getMaxValue() {
        return maxValue;
    }

    public boolean getAllowShift() {
        return allowShift;
    }

    public void setAllowShift(boolean value) {
        this.allowShift = value;
        enforceConstraints(); // Re-check constraints
    }

    public boolean getIsUpCounting() {
        return isUpCounting;
    }

    public void setIsUpCounting(boolean value) {
        this.isUpCounting = value;
        enforceConstraints(); // Re-check constraints
    }
    
    public boolean isBlank() {
        return !this.visibility;
    }
    
    public void setIsBlank(boolean value) {
        this.visibility = !value;
    }
    
    public void setHoursMinutesSecondsMillis(int hours, int minutes, int seconds, int millis) {
        System.out.println("you called? hours=" + hours + "  minutes=" + minutes + "  seconds=" + seconds + "  millis=" + millis);
        System.out.println(maxHours + "   " + maxMinutes + "   " + maxSeconds + "   " + maxMillis + "  ID" + this.id);
        if (hours <= this.maxHours && minutes <= this.maxMinutes && seconds <= this.maxSeconds && millis <= this.maxMillis) {
            System.out.println("passed the test.");
            this.currentHours = hours;
            this.currentMinutes = minutes;
            this.currentSeconds = seconds;
            this.currentMillis = millis;
            this.currentValue = (long) (millis * 1_000_000L + seconds * 1_000_000_000L + minutes * 60_000_000_000L + hours * 3_600_000_000L);
            if (!isRunning) {
                this.initialValue = currentValue;
            }
            this.expiredNotified = false;
            }
    }
    
    public boolean getVisibility() {
        return this.visibility;
    }
    
    public void setVisibility(boolean value) {
        this.visibility = value;
    }
    
    public int getTextScoreboardLine() {
        return this.textScoreboardLine;
    }
    
    public int getTextScoreboardStartPos() {
        return this.textScoreboardStartPos;
    }
    
    public int getTextScoreboardEndPos() {
        return this.textScoreboardEndPos;
    }
    
    public JsonArray getGameLinks() {
        return this.gameLinks;
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
    private boolean visibility = false;
    private String displayFormat;
    private int textScoreboardLine;
    private int textScoreboardStartPos;
    private int textScoreboardEndPos;
    private JsonArray gameLinks;

    @Override
    public void initialize(JsonObject config) {
        this.id = config.get("id").getAsString();
        this.currentValue = config.has("initialValue") ? config.get("initialValue").getAsBoolean() : false;
        this.observedTimerId = config.has("observedTimerId") ? config.get("observedTimerId").getAsString() : null;
        this.triggerEvent = config.has("triggerEvent") ? config.get("triggerEvent").getAsString() : null;
        this.pattern = config.has("pattern") ? config.get("pattern").getAsString() : null;
        this.visibility = config.has("initialVisibility") ? config.get("initialVisibility").getAsBoolean() : true;
        this.displayFormat = config.has("format") ? config.get("format").getAsString() : "";
        this.textScoreboardLine = config.has("textScoreboardLine") ? config.get("textScoreboardLine").getAsInt() : 0;
        this.textScoreboardStartPos = config.has("textScoreboardStartPos") ? config.get("textScoreboardStartPos").getAsInt() : 0;
        this.textScoreboardEndPos = config.has("textScoreboardEndPos") ? config.get("textScoreboardEndPos").getAsInt() : 0;
        this.gameLinks = config.has("gameLinks") ? config.get("gameLinks").getAsJsonArray() : null;
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
            //if (selectedTimerSupplier != null && timerId.equals(selectedTimerSupplier.get())) {
                setCurrentValue(true);
            //}
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
                        //if (selectedTimerSupplier != null && timerId.equals(selectedTimerSupplier.get())) {
                            setCurrentValue(true);
                        //}
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
        return currentValue ? this.displayFormat : " ";
    }
    
    public boolean isBlank() {
        return !this.visibility;
    }
    
    public void setIsBlank(boolean value) {
        this.visibility = !value;
    }
    
    public int getTextScoreboardLine() {
        return this.textScoreboardLine;
    }
    
    public int getTextScoreboardStartPos() {
        return this.textScoreboardStartPos;
    }
    
    public int getTextScoreboardEndPos() {
        return this.textScoreboardEndPos;
    }
    
    public JsonArray getGameLinks() {
        return this.gameLinks;
    }
    
    public String getObservedTimerId() {
        return this.observedTimerId;
    }
}

class ScoreCounter implements ScoreElement {
    private String id;
    private int currentValue;
    private int minValue;
    private int maxValue;
    private int initialValue;
    private Integer rolloverValue;
    private boolean canRollUp;
    private boolean canRollDown;
    private boolean visibility = true;
    private boolean leadingZeroTricks = false;
    private boolean showLeadingZero = false;
    private String displayFormat;
    private int textScoreboardLine;
    private int textScoreboardStartPos;
    private int textScoreboardEndPos;
    private JsonArray gameLinks;

    @Override
    public void initialize(JsonObject config) {
        this.id = config.get("id").getAsString();
        this.initialValue = config.has("initialValue") ? config.get("initialValue").getAsInt() : 0;
        this.minValue = config.has("minValue") ? config.get("minValue").getAsInt() : 0;
        this.maxValue = config.has("maxValue") ? config.get("maxValue").getAsInt() : 0;
        this.currentValue = initialValue;
        this.canRollUp = config.has("canRollUp") ? config.get("canRollUp").getAsBoolean() : false;
        this.canRollDown = config.has("canRollDown") ? config.get("canRollDown").getAsBoolean() : false;
        this.rolloverValue = 0;
        if (this.canRollUp) {
            if (config.has("rolloverValue")) {
                this.rolloverValue = config.get("rolloverValue").getAsInt();
            } else if (this.maxValue != 0) {
                this.rolloverValue = this.maxValue;
            } else {
                this.canRollUp = false;
            }
        }
        if (this.canRollDown) {
            if (config.has("rolloverValue")) {
                this.rolloverValue = config.get("rolloverValue").getAsInt();
            } else {
                this.rolloverValue = this.minValue;
            }
        }
        this.visibility = config.has("initialVisibility") ? config.get("initialVisibility").getAsBoolean() : true;
        this.leadingZeroTricks = config.has("leadingZeroTricks") ? config.get("leadingZeroTricks").getAsBoolean() : false;
        this.showLeadingZero = config.has("showLeadingZero") ? config.get("showLeadingZero").getAsBoolean() : false;
        this.displayFormat = config.has("format") ? config.get("format").getAsString() : "";
        this.textScoreboardLine = config.has("textScoreboardLine") ? config.get("textScoreboardLine").getAsInt() : 0;
        this.textScoreboardStartPos = config.has("textScoreboardStartPos") ? config.get("textScoreboardStartPos").getAsInt() : 0;
        this.textScoreboardEndPos = config.has("textScoreboardEndPos") ? config.get("textScoreboardEndPos").getAsInt() : 0;
        this.gameLinks = config.has("gameLinks") ? config.get("gameLinks").getAsJsonArray() : null;
    } 

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void reset() {
        currentValue = initialValue;
    }
    
    public void increment(int amount) {
        int newValue = currentValue + amount;
        if (canRollUp && rolloverValue != null && currentValue <= rolloverValue && newValue > rolloverValue) {
            // Roll over to minValue when currentValue <= rolloverValue and newValue > rolloverValue
            currentValue = minValue + (newValue - rolloverValue - 1) % (maxValue - minValue + 1);
        } else if (newValue > maxValue) {
            if (canRollUp) {
                // Roll over to minValue when exceeding maxValue
                currentValue = minValue + (newValue - maxValue - 1) % (maxValue - minValue + 1);
            } else {
                currentValue = maxValue;
            }
        } else {
            currentValue = newValue;
        }
    }

    public void decrement(int amount) {
        int newValue = currentValue - amount;
        if (newValue < minValue) {
            if (canRollDown) {
                int rollTarget = (rolloverValue != null && rolloverValue > minValue) ? rolloverValue : maxValue;
                int range = maxValue - minValue + 1;
                int excess = (minValue - newValue - 1) % range;
                currentValue = rollTarget - excess;
            } else {
                currentValue = minValue;
            }
        } else {
            currentValue = newValue;
        }
    }

    public void setCurrentValue(int value) {
        if (value >= this.minValue && value <= this.maxValue) {
            currentValue = value;
        }
        //currentValue = Math.max(minValue, Math.min(value, maxValue));
    }

    public int getCurrentValue() {
        return currentValue;
    }

    @Override
    public String getDisplayValue() {
        return String.valueOf(currentValue);
    }

    public int getMinValue() {
        return minValue;
    }

    public int getMaxValue() {
        return maxValue;
    }
    
    public boolean isBlank() {
        return !this.visibility;
    }
    
    public void setIsBlank(boolean value) {
        this.visibility = !value;
    }
    
    public boolean hasLeadingZeroTricks() {
        return this.leadingZeroTricks;
    }
    
    public void setLeadingZeroTricks(boolean value) {
        this.leadingZeroTricks = value;
    }
    
    public boolean showLeadingZero() {
        return this.showLeadingZero;
    }
    
    public void setShowLeadingZero(boolean value) {
        this.showLeadingZero = value;
    }
    
    public int getTextScoreboardLine() {
        return this.textScoreboardLine;
    }
    
    public int getTextScoreboardStartPos() {
        return this.textScoreboardStartPos;
    }
    
    public int getTextScoreboardEndPos() {
        return this.textScoreboardEndPos;
    }
    
    public JsonArray getGameLinks() {
        return this.gameLinks;
    }
}

// RuleEngine class
class RuleEngine {
    private ScoreEventBus eventBus = new ScoreEventBus();
    private Map<String, ScoreElement> gameElementsMap = new HashMap<>();
    private Map<String, ScoreElement> displayElementsMap = new HashMap<>();
    private List<String> timerIds = new ArrayList<>();
    //private JsonObject uiConfig;
    private JsonArray gameElementsArray;
    private JsonObject controllerConfig;
    private JsonArray displayConfigArray;

    public RuleEngine(String ruleFilePath) {
        loadRules(ruleFilePath);
    }

    private void loadRules(String ruleFilePath) {
        try {
            // First, we load the entire Rules Set JSON file into json.
            JsonObject json = new Gson().fromJson(new FileReader(ruleFilePath), JsonObject.class);
            if (json == null) {
                System.err.println("Failed to parse JSON: JSON is null");
                return;
            }
            //uiConfig = json.has("uiConfig") ? json.getAsJsonObject("uiConfig") : new JsonObject();
            controllerConfig = json.has("Controller-Config") ? json.getAsJsonObject("Controller-Config") : new JsonObject();
            //JsonArray elementsArray = json.getAsJsonArray("elements");
            gameElementsArray = json.getAsJsonArray("Game-Elements");
            displayConfigArray = json.getAsJsonArray("Display-Config");
            if (gameElementsArray == null) {
                System.err.println("No 'Game-Elements' array in JSON");
                return;
            }
            
            for (JsonElement element : gameElementsArray) {
                JsonObject gameElementConfig = element.getAsJsonObject();
                String gameElementType = gameElementConfig.get("type").getAsString();
                ScoreElement scoreElement;

                if ("ScoreTimer".equals(gameElementType)) {
                    scoreElement = new ScoreTimer(eventBus);
                    scoreElement.initialize(gameElementConfig);
                    gameElementsMap.put(scoreElement.getId(), scoreElement);
                    //timerIds.add(scoreElement.getId()); // Add timer ID to list
                } else if ("ScoreCounter".equals(gameElementType)) {
                    scoreElement = new ScoreCounter();
                    scoreElement.initialize(gameElementConfig);
                    gameElementsMap.put(scoreElement.getId(), scoreElement);
                } else if ("ScoreIndicator".equals(gameElementType)) {
                    scoreElement = new ScoreIndicator();
                    scoreElement.initialize(gameElementConfig);
                    gameElementsMap.put(scoreElement.getId(), scoreElement);
                    ScoreIndicator indicator = (ScoreIndicator) scoreElement;
                    String timerId = indicator.getObservedTimerId();
                    if (timerId != null) {
                        eventBus.registerTimerObserver(timerId, indicator);
                    }
                } 
            }
            
            if (displayConfigArray == null) {
                System.err.println("Missing 'Display-Config' from Rule Set JSON");
                return;
            }
            for (JsonElement displayElement : displayConfigArray) {
                JsonObject displayElementConfig = displayElement.getAsJsonObject();
                String elementType = displayElementConfig.get("type").getAsString();
                ScoreElement scoreElement;

                if ("ScoreTimer".equals(elementType)) {
                    scoreElement = new ScoreTimer(eventBus);
                    scoreElement.initialize(displayElementConfig);
                    displayElementsMap.put(scoreElement.getId(), scoreElement);
                    //timerIds.add(scoreElement.getId()); // Add timer ID to list
                } else if ("ScoreCounter".equals(elementType)) {
                    scoreElement = new ScoreCounter();
                    scoreElement.initialize(displayElementConfig);
                    displayElementsMap.put(scoreElement.getId(), scoreElement);
                } else if ("ScoreIndicator".equals(elementType)) {
                    scoreElement = new ScoreIndicator();
                    scoreElement.initialize(displayElementConfig);
                    displayElementsMap.put(scoreElement.getId(), scoreElement);
                    ScoreIndicator indicator = (ScoreIndicator) scoreElement;
                    String timerId = indicator.getObservedTimerId();
                    if (timerId != null) {
                        eventBus.registerTimerObserver(timerId, indicator);
                    }
                } 
            }
            
            System.out.println("RuleEngine loaded, timerIds: " + timerIds); // Debug output
        } catch (Exception e) {
            System.err.println("Error loading rules from " + ruleFilePath + ":");
            e.printStackTrace();
        }
    }

    public ScoreElement getGameElement(String id) {
        return gameElementsMap.get(id);
    }

    public Collection<ScoreElement> getGameElementsMap() {
        return gameElementsMap.values();
    }
    
    public ScoreElement getDisplayElement(String id) {
        return displayElementsMap.get(id);
    }

    public Collection<ScoreElement> getDisplayElementsMap() {
        return displayElementsMap.values();
    }
    /*
    public List<String> getTimerIds() {
        return timerIds;
    }*/
    
    public JsonObject getControllerConfig() {
        return controllerConfig;
    }
}