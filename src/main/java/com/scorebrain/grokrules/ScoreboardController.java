package com.scorebrain.grokrules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.util.Duration;
import java.util.function.Supplier;
import javafx.fxml.Initializable;
import javafx.scene.Parent;

public class ScoreboardController implements Initializable {

    private RuleEngine ruleEngine = new RuleEngine("grokruleset.json");
    private List<String> timerIds;
    private int currentTimerIndex = 0;
    private StringBuilder inputBuffer = new StringBuilder();
    private Timeline uiTimer;
    private Timeline hornTimeline;
    private boolean settingMode = false;
    private Timeline flashTimeline;
    private boolean isFlashing = false;
    private String settingCounterId = null; // For counters
    private String settingTimerId = null; // For timers
    private JsonObject currentButtonConfig = null; // Track current set button config
    private Map<String, JsonObject> buttonConfigs = new HashMap<>();
    private Parent root;
    private boolean isInitialPrompt = false; // Track if showing initial value
    private String promptLine1 = ""; // For two-line prompts

    @FXML private Label timerLabel;
    @FXML private Label lcdLine1;
    @FXML private Label lcdLine2;
    @FXML private Button buttonPlusOne;
    @FXML private Button buttonMinusOne;
    @FXML private Button buttonSet;
    @FXML private Button buttonStartStop;
    @FXML private Button buttonEnter;
    @FXML private Button buttonBackspace;
    @FXML private Circle runningIndicator;
    @FXML private Text hornSymbol;
    @FXML private Button buttonHorn;
    @FXML private Button buttonNextTimer;
    @FXML private Button button0, button1, button2, button3, button4,
          button5, button6, button7, button8, button9;
    @FXML private Label guestPointsLabel;
    @FXML private Label homePointsLabel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("Initialize called");
        try {
            timerIds = ruleEngine.getTimerIds();
            System.out.println("timerIds set: " + timerIds);
            resetUI();
            startUITimer();
            for (ScoreElement element : ruleEngine.getElements()) {
                if (element instanceof ScoreIndicator) {
                    ScoreIndicator indicator = (ScoreIndicator) element;
                    indicator.setSelectedTimerSupplier(this::getSelectedTimerId);
                }
            }
        } catch (Exception e) {
            System.err.println("Exception in initialize:");
            e.printStackTrace();
        }
    }

    public void setRoot(Parent root) {
        this.root = root;
    }

    public void configureButtons() {
        JsonObject uiConfig = ruleEngine.getUiConfig();
        if (uiConfig != null && uiConfig.has("buttons")) {
            JsonArray buttons = uiConfig.getAsJsonArray("buttons");
            for (JsonElement btn : buttons) {
                JsonObject btnConfig = btn.getAsJsonObject();
                String fxId = btnConfig.get("fxId").getAsString();
                buttonConfigs.put(fxId, btnConfig);
                Button button = (Button) root.lookup("#" + fxId);
                if (button != null) {
                    button.setText(btnConfig.get("label").getAsString());
                    System.out.println("Labeled button " + fxId + " as '" + btnConfig.get("label").getAsString() + "'");
                } else {
                    System.out.println("Button with fx:id '" + fxId + "' not found.");
                }
            }
        }
    }

    public String getSelectedTimerId() {
        return timerIds.get(currentTimerIndex);
    }

    @FXML
    private void handleGridButton(ActionEvent event) {
        Button button = (Button) event.getSource();
        String fxId = button.getId();
        JsonObject config = buttonConfigs.get(fxId);
        System.out.println("Clicked button: " + fxId);
        if (config != null) {
            String action = config.has("action") ? config.get("action").getAsString() : "none";
            String target = config.has("target") ? config.get("target").getAsString() : "none";
            System.out.println("Action: " + action + ", Target: " + target);
            ScoreElement element = ruleEngine.getElement(target);

            if (settingMode) {
                if (action.startsWith("set")) {
                    // Different-target set button: abort current set, don’t start new set
                    abortSetFunction();
                    return;
                } else if (target.equals(settingTimerId) || target.equals(settingCounterId)) {
                    // Same-target non-set button: abort set, execute new action
                    abortSetFunction();
                } else {
                    // Different-target non-set button: execute and continue set
                    executeNonSetAction(element, action, config);
                    return; // Continue set mode
                }
            }

            executeAction(element, action, config, target);
        } else {
            System.out.println("No config found for button " + fxId);
        }
    }

    private void executeNonSetAction(ScoreElement element, String action, JsonObject config) {
        if (element instanceof ScoreCounter counter) {
            if ("increment".equals(action) && config.has("amount")) {
                counter.increment(config.get("amount").getAsInt());
            } else if ("decrement".equals(action) && config.has("amount")) {
                counter.decrement(config.get("amount").getAsInt());
            }
        } else if (element instanceof ScoreTimer timer) {
            if ("start".equals(action) && !timer.isRunning()) {
                timer.startstop();
            } else if ("stop".equals(action) && timer.isRunning()) {
                timer.startstop();
            }
        }
        updateUI();
    }

    private void executeAction(ScoreElement element, String action, JsonObject config, String target) {
        if (element instanceof ScoreCounter counter && "increment".equals(action)) {
            if (config.has("amount")) {
                counter.increment(config.get("amount").getAsInt());
                updateUI();
            } else {
                System.out.println("No 'amount' specified for increment action");
            }
        } else if (element instanceof ScoreCounter counter && "decrement".equals(action)) {
            if (config.has("amount")) {
                counter.decrement(config.get("amount").getAsInt());
                updateUI();
            } else {
                System.out.println("No 'amount' specified for decrement action");
            }
        } else if (element instanceof ScoreTimer timer) {
            if ("start".equals(action) && !timer.isRunning()) {
                timer.startstop();
                updateUI();
            } else if ("stop".equals(action) && timer.isRunning()) {
                timer.startstop();
                updateUI();
            } else if ("setCurrentValue".equals(action) && !timer.isRunning()) {
                settingMode = true;
                settingTimerId = target;
                currentButtonConfig = config;
                inputBuffer.setLength(0);
                isInitialPrompt = true;
                displayInitialPrompt(timer);
            } else if ("setAllowShift".equals(action) && !timer.isRunning()) {
                settingMode = true;
                settingTimerId = target;
                currentButtonConfig = config;
                inputBuffer.setLength(0);
                isInitialPrompt = true;
                displayBinaryPrompt(timer.getAllowShift(), config.get("promptLine1").getAsString(), config.get("promptLine2").getAsString());
            } else if ("setIsUpCounting".equals(action) && !timer.isRunning()) {
                settingMode = true;
                settingTimerId = target;
                currentButtonConfig = config;
                inputBuffer.setLength(0);
                isInitialPrompt = true;
                displayBinaryPrompt(timer.getIsUpCounting(), config.get("promptLine1").getAsString(), config.get("promptLine2").getAsString());
            }
        }
    }

    private void displayInitialPrompt(ScoreTimer timer) {
        String prompt = getPromptString(currentButtonConfig);
        if (prompt != null) {
            long nanos = timer.getCurrentValue();
            long totalSeconds = nanos / 1_000_000_000L;
            long tenths = (nanos % 1_000_000_000L) / 100_000_000L;
            long minutes = totalSeconds / 60;
            long seconds = totalSeconds % 60;

            StringBuilder display = new StringBuilder();
            int bracketStart = prompt.indexOf('<');
            int bracketEnd = prompt.indexOf('>');
            display.append(prompt.substring(0, bracketStart + 1)); // Literal prefix + "<"

            String format = prompt.substring(bracketStart + 1, bracketEnd);
            if (format.equals("MM:SS")) {
                display.append(String.format("%02d:%02d", minutes, seconds));
            } else if (format.equals("SS.t")) {
                display.append(String.format("%02d.%d", seconds, tenths));
            }
            display.append(">"); // Closing bracket
            lcdLine2.setText(display.toString());
            promptLine1 = ""; // Clear line 1 for single-line prompt
            lcdLine1.setText(getTimerDisplayText());
        }
    }

    private void displayBinaryPrompt(boolean currentValue, String line1, String line2) {
        promptLine1 = line1;
        String displayLine2 = line2.replace("<b>", "<" + (currentValue ? "1" : "0") + ">");
        lcdLine1.setText(promptLine1);
        lcdLine2.setText(displayLine2);
    }

    private String getPromptString(JsonObject config) {
        if (config.has("minutesPrompt")) {
            return config.get("minutesPrompt").getAsString();
        } else if (config.has("secondsPrompt")) {
            return config.get("secondsPrompt").getAsString();
        }
        return null;
    }

    @FXML
    private void handleNumberClick(ActionEvent event) {
        if (!settingMode) return;
        Button clickedButton = (Button) event.getSource();
        String number = clickedButton.getText();

        String action = currentButtonConfig.get("action").getAsString();
        if (action.equals("setAllowShift") || action.equals("setIsUpCounting")) {
            if (!number.equals("0") && !number.equals("1")) return; // Only 0 or 1 allowed
            inputBuffer.setLength(0);
            inputBuffer.append(number);
            lcdLine2.setText(currentButtonConfig.get("promptLine2").getAsString().replace("<b>", "<" + number + ">"));
            isInitialPrompt = false;
        } else {
            String prompt = getPromptString(currentButtonConfig);
            if (prompt == null) return;

            isInitialPrompt = false;
            int bracketStart = prompt.indexOf('<');
            int bracketEnd = prompt.indexOf('>');
            String format = prompt.substring(bracketStart + 1, bracketEnd);
            int digitCount = 0;
            for (char c : format.toCharArray()) {
                if (Character.isLetter(c)) digitCount++;
            }

            if (inputBuffer.length() == 0) {
                inputBuffer.append(" ".repeat(digitCount - 1)).append(number);
            } else if (inputBuffer.length() < digitCount) {
                inputBuffer.append(number);
            } else {
                inputBuffer.deleteCharAt(0).append(number);
            }

            StringBuilder display = new StringBuilder();
            display.append(prompt.substring(0, bracketStart + 1));
            int inputIndex = 0;
            for (char c : format.toCharArray()) {
                if (Character.isLetter(c)) {
                    display.append(inputIndex < inputBuffer.length() ? inputBuffer.charAt(inputIndex) : ' ');
                    inputIndex++;
                } else {
                    display.append(c);
                }
            }
            display.append(">");
            lcdLine2.setText(display.toString());
        }
    }

    @FXML
    private void handleBackClick(ActionEvent event) {
        if (!settingMode) return;
        if (isInitialPrompt || inputBuffer.length() == 0) {
            abortSetFunction();
        } else {
            String action = currentButtonConfig.get("action").getAsString();
            if (action.equals("setAllowShift") || action.equals("setIsUpCounting")) {
                abortSetFunction(); // Binary prompts don’t need backspace
            } else {
                String prompt = getPromptString(currentButtonConfig);
                int bracketStart = prompt.indexOf('<');
                int bracketEnd = prompt.indexOf('>');
                String format = prompt.substring(bracketStart + 1, bracketEnd);
                int digitCount = 0;
                for (char c : format.toCharArray()) {
                    if (Character.isLetter(c)) digitCount++;
                }

                inputBuffer.deleteCharAt(inputBuffer.length() - 1);
                while (inputBuffer.length() < digitCount) {
                    inputBuffer.insert(0, " ");
                }

                StringBuilder display = new StringBuilder();
                display.append(prompt.substring(0, bracketStart + 1));
                int inputIndex = 0;
                for (char c : format.toCharArray()) {
                    if (Character.isLetter(c)) {
                        display.append(inputIndex < inputBuffer.length() ? inputBuffer.charAt(inputIndex) : ' ');
                        inputIndex++;
                    } else {
                        display.append(c);
                    }
                }
                display.append(">");
                lcdLine2.setText(display.toString());
            }
        }
    }

    @FXML
    private void handleEnter(ActionEvent event) {
        if (!settingMode) return;
        if (isInitialPrompt || inputBuffer.length() == 0) {
            abortSetFunction();
            return;
        }
        try {
            String action = currentButtonConfig.get("action").getAsString();
            if (action.equals("setCurrentValue")) {
                String prompt = getPromptString(currentButtonConfig);
                int bracketStart = prompt.indexOf('<');
                int bracketEnd = prompt.indexOf('>');
                String format = prompt.substring(bracketStart + 1, bracketEnd);
                long nanos = parseInput(format, inputBuffer.toString().trim());
                if (settingCounterId != null) {
                    ScoreCounter counter = (ScoreCounter) ruleEngine.getElement(settingCounterId);
                    int seconds = (int) (nanos / 1_000_000_000L);
                    if (seconds >= counter.getMinValue() && seconds <= counter.getMaxValue()) {
                        counter.setCurrentValue(seconds);
                    }
                    settingCounterId = null;
                } else if (settingTimerId != null) {
                    ScoreTimer timer = (ScoreTimer) ruleEngine.getElement(settingTimerId);
                    if (nanos >= timer.getMinValue() && nanos <= timer.getMaxValue()) {
                        timer.setValue(nanos);
                    }
                    settingTimerId = null;
                }
            } else if (action.equals("setAllowShift")) {
                ScoreTimer timer = (ScoreTimer) ruleEngine.getElement(settingTimerId);
                timer.setAllowShift(inputBuffer.toString().trim().equals("1"));
                settingTimerId = null;
            } else if (action.equals("setIsUpCounting")) {
                ScoreTimer timer = (ScoreTimer) ruleEngine.getElement(settingTimerId);
                timer.setIsUpCounting(inputBuffer.toString().trim().equals("1"));
                settingTimerId = null;
            }
            settingMode = false;
            currentButtonConfig = null;
            buttonPlusOne.setDisable(false);
            buttonMinusOne.setDisable(false);
            resetUI();
        } catch (NumberFormatException e) {
            lcdLine2.setText("Invalid input!");
        }
    }

    private void abortSetFunction() {
        settingMode = false;
        settingTimerId = null;
        settingCounterId = null;
        currentButtonConfig = null;
        isInitialPrompt = false;
        inputBuffer.setLength(0);
        buttonPlusOne.setDisable(false);
        buttonMinusOne.setDisable(false);
        promptLine1 = "";
        resetUI();
    }

    private long parseInput(String format, String input) {
        long nanos = 0;
        input = input.replaceAll("\\s", ""); // Remove spaces
        if (format.equals("MM:SS")) {
            String paddedInput = String.format("%4s", input).replace(' ', '0');
            int minutes = Integer.parseInt(paddedInput.substring(0, 2));
            int seconds = Integer.parseInt(paddedInput.substring(2, 4));
            nanos = (minutes * 60L + seconds) * 1_000_000_000L;
        } else if (format.equals("SS.t")) {
            String paddedInput = String.format("%3s", input).replace(' ', '0');
            int seconds = Integer.parseInt(paddedInput.substring(0, 2));
            int tenths = Integer.parseInt(paddedInput.substring(2, 3));
            nanos = seconds * 1_000_000_000L + tenths * 100_000_000L;
        }
        return nanos;
    }

    @FXML
    private void handleGuestPlusOne() { executeCounterAction("guestPoints", "increment", 1); }
    @FXML
    private void handleGuestPlusTwo() { executeCounterAction("guestPoints", "increment", 2); }
    @FXML
    private void handleGuestPlusSix() { executeCounterAction("guestPoints", "increment", 6); }
    @FXML
    private void handleGuestMinusOne() { executeCounterAction("guestPoints", "decrement", 1); }
    @FXML
    private void handleGuestSetPoints() { startCounterSet("guestPoints", "ENTER GUEST POINTS: "); }

    @FXML
    private void handleHomePlusOne() { executeCounterAction("homePoints", "increment", 1); }
    @FXML
    private void handleHomePlusTwo() { executeCounterAction("homePoints", "increment", 2); }
    @FXML
    private void handleHomePlusSix() { executeCounterAction("homePoints", "increment", 6); }
    @FXML
    private void handleHomeMinusOne() { executeCounterAction("homePoints", "decrement", 1); }
    @FXML
    private void handleHomeSetPoints() { startCounterSet("homePoints", "ENTER HOME POINTS: "); }

    private void executeCounterAction(String target, String action, int amount) {
        if (settingMode && (target.equals(settingCounterId) || target.equals(settingTimerId))) {
            abortSetFunction();
        }
        ScoreCounter counter = (ScoreCounter) ruleEngine.getElement(target);
        if ("increment".equals(action)) {
            counter.increment(amount);
        } else if ("decrement".equals(action)) {
            counter.decrement(amount);
        }
        updateUI();
    }

    private void startCounterSet(String target, String prompt) {
        if (settingMode) {
            abortSetFunction();
            return;
        }
        settingMode = true;
        settingCounterId = target;
        currentButtonConfig = null;
        inputBuffer.setLength(0);
        lcdLine2.setText(prompt);
        promptLine1 = "";
        lcdLine1.setText(getTimerDisplayText());
    }

    @FXML
    private void handlePlusOne(ActionEvent event) {
        if (settingMode && "timerOne".equals(settingTimerId)) {
            abortSetFunction();
        }
        ScoreTimer timer = getSelectedTimer();
        if (timer != null && !timer.isRunning()) {
            timer.increment();
            updateUI();
        } else {
            lcdLine2.setText("Timer is running!");
        }
    }

    @FXML
    private void handleMinusOne(ActionEvent event) {
        if (settingMode && "timerOne".equals(settingTimerId)) {
            abortSetFunction();
        }
        ScoreTimer timer = getSelectedTimer();
        if (timer != null && !timer.isRunning()) {
            timer.decrement();
            updateUI();
        } else {
            lcdLine2.setText("Timer is running!");
        }
    }

    @FXML
    private void handleSet(ActionEvent event) {
        // Deprecated, handled by handleGridButton
    }

    @FXML
    private void handleStartStop(ActionEvent event) {
        if (settingMode && "timerOne".equals(settingTimerId)) {
            abortSetFunction();
        }
        ScoreTimer timer = getSelectedTimer();
        if (timer != null) {
            timer.startstop();
            updateUI();
        }
    }

    @FXML
    private void handleHorn(ActionEvent event) {
        if (settingMode && "timerOne".equals(settingTimerId)) {
            abortSetFunction();
        }
        String hornId = timerIds.get(currentTimerIndex) + "_Horn";
        ScoreIndicator horn = (ScoreIndicator) ruleEngine.getElement(hornId);
        if (horn != null && !horn.getCurrentValue()) {
            horn.setCurrentValue(true);
            startHornAnimation(horn);
            updateUI();
        }
    }

    @FXML
    private void handleNextTimer(ActionEvent event) {
        if (settingMode && "timerOne".equals(settingTimerId)) {
            abortSetFunction();
        }
        currentTimerIndex = (currentTimerIndex + 1) % timerIds.size();
        updateUI();
    }

    private void startUITimer() {
        if (uiTimer != null) uiTimer.stop();
        uiTimer = new Timeline(new KeyFrame(Duration.seconds(0.0167), e -> updateUI()));
        uiTimer.setCycleCount(Timeline.INDEFINITE);
        uiTimer.play();
    }

    private void resetUI() {
        updateUI();
        if (!settingMode) {
            lcdLine2.setText("");
            promptLine1 = "";
            lcdLine1.setText(getTimerDisplayText());
        }
    }

    private String getTimerDisplayText() {
        ScoreTimer timer = (ScoreTimer) ruleEngine.getElement(timerIds.get(currentTimerIndex));
        String lcdText = "Timer " + (currentTimerIndex + 1) + ": " + (timer != null ? timer.getDisplayValue() : "N/A");
        String hornId = timerIds.get(currentTimerIndex) + "_Horn";
        ScoreIndicator horn = (ScoreIndicator) ruleEngine.getElement(hornId);
        if (horn != null && horn.getCurrentValue()) {
            if (hornTimeline == null) {
                startHornAnimation(horn);
            }
            lcdText += " *";
        }
        if (timer != null && timer.isRunning() && timer.getCurrentValue() / 1_000_000_000.0 < timer.getFlashZoneThreshold() && timer.getFlashZoneThreshold() >= 0) {
            lcdText += " F";
        }
        return lcdText;
    }

    private void updateUI() {
        if (timerIds == null || timerIds.isEmpty()) {
            timerLabel.setText("No timers available");
            runningIndicator.setFill(javafx.scene.paint.Color.DARKGRAY);
            lcdLine1.setText("No timers");
            hornSymbol.setVisible(false);
            return;
        }
        ScoreTimer timer = (ScoreTimer) ruleEngine.getElement(timerIds.get(currentTimerIndex));
        if (timer != null) {
            timerLabel.setText(timer.getDisplayValue());
            runningIndicator.setFill(timer.isRunning() ?
                javafx.scene.paint.Color.RED : javafx.scene.paint.Color.DARKGRAY);
            timer.checkThresholds();
            double currentSeconds = timer.getCurrentValue() / 1_000_000_000.0;
            boolean shouldFlash = timer.isRunning() && currentSeconds < timer.getFlashZoneThreshold() && timer.getFlashZoneThreshold() >= 0;

            if (shouldFlash && !isFlashing) {
                startFlashAnimation(timer);
                isFlashing = true;
            } else if (!shouldFlash && isFlashing) {
                stopFlashAnimation();
                isFlashing = false;
            }
        } else {
            timerLabel.setText("N/A");
            runningIndicator.setFill(javafx.scene.paint.Color.DARKGRAY);
        }
        ScoreCounter guestPoints = (ScoreCounter) ruleEngine.getElement("guestPoints");
        guestPointsLabel.setText(guestPoints != null ? guestPoints.getDisplayValue() : "N/A");
        ScoreCounter homePoints = (ScoreCounter) ruleEngine.getElement("homePoints");
        homePointsLabel.setText(homePoints != null ? homePoints.getDisplayValue() : "N/A");
        if (guestPoints != null) {
            guestPointsLabel.setText(guestPoints.getDisplayValue());
        }
        if (homePoints != null) {
            homePointsLabel.setText(homePoints.getDisplayValue());
        }
        if (!settingMode || promptLine1.isEmpty()) {
            lcdLine1.setText(getTimerDisplayText());
        } else {
            lcdLine1.setText(promptLine1); // Keep prompt line 1 during setting
        }
    }

    private void startFlashAnimation(ScoreTimer timer) {
        String pattern = timer.getFlashZonePattern();
        if (pattern == null || pattern.isEmpty()) {
            return;
        }
        String[] steps = pattern.split(",");
        flashTimeline = new Timeline();
        double cumulativeTime = 0;
        boolean firstVisible = Boolean.parseBoolean(steps[0].split(":")[1]);
        for (String step : steps) {
            String[] parts = step.split(":");
            double duration = Double.parseDouble(parts[0]);
            boolean visible = Boolean.parseBoolean(parts[1]);
            flashTimeline.getKeyFrames().add(
                new KeyFrame(Duration.millis(cumulativeTime), e -> timerLabel.setVisible(visible))
            );
            cumulativeTime += duration;
        }
        flashTimeline.getKeyFrames().add(
            new KeyFrame(Duration.millis(cumulativeTime), e -> timerLabel.setVisible(firstVisible))
        );
        flashTimeline.setCycleCount(Timeline.INDEFINITE);
        flashTimeline.play();
    }

    private void stopFlashAnimation() {
        if (flashTimeline != null) {
            flashTimeline.stop();
            flashTimeline = null;
        }
        timerLabel.setVisible(true);
    }

    private ScoreTimer getSelectedTimer() {
        return (ScoreTimer) ruleEngine.getElement(timerIds.get(currentTimerIndex));
    }

    private void startHornAnimation(ScoreIndicator horn) {
        if (horn.getCurrentValue() && horn.getPattern() != null && hornTimeline == null) {
            String[] steps = horn.getPattern().split(",");
            hornTimeline = new Timeline();
            double cumulativeTime = 0;
            for (String step : steps) {
                String[] parts = step.split(":");
                double duration = Double.parseDouble(parts[0]);
                boolean visible = Boolean.parseBoolean(parts[1]);
                hornTimeline.getKeyFrames().add(
                    new KeyFrame(Duration.millis(cumulativeTime), e -> hornSymbol.setVisible(visible))
                );
                cumulativeTime += duration;
            }
            hornTimeline.getKeyFrames().add(
                new KeyFrame(Duration.millis(cumulativeTime), e -> hornSymbol.setVisible(false))
            );
            hornTimeline.setOnFinished(e -> {
                horn.setCurrentValue(false);
                hornTimeline = null;
                updateUI();
            });
            hornSymbol.setVisible(true);
            hornTimeline.play();
        }
    }
}