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
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;
import java.util.function.Supplier;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

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
    private String settingCounterId = null;
    private String settingTimerId = null;
    private JsonObject currentButtonConfig = null;
    private Map<String, JsonObject> buttonConfigs = new HashMap<>();
    private Map<Button, String> buttonToFxIdMap = new HashMap<>();
    private Map<String, String> numberButtonMap = new HashMap<>();
    private Parent root;
    private boolean isInitialPrompt = false;
    private String promptLine1 = "";

    @FXML private Label line1LCD;
    @FXML private Label line2LCD;
    @FXML private Label line3LCD;
    @FXML private Rectangle mainTimerRunningLight;
    @FXML private Button buttonB8, buttonB7, buttonB6, buttonB5, buttonD8, buttonD7, buttonF8, buttonF6, buttonF2, buttonF1;
    @FXML private Button buttonE3, buttonD4, buttonD3, buttonD2, buttonC4, buttonC3, buttonC2, buttonB4, buttonB3, buttonB2;
    @FXML private Button buttonE2;
    @FXML private Button buttonE4;
    @FXML private VBox textScoreboard;
    @FXML private Label line1Label, line2Label, line3Label, line4Label, line5Label, line6Label, line7Label, line8Label;
    private Label mainTimerLabel, guestPointsLabel, homePointsLabel;
    private Text mainHornLeft, mainHornRight, flashIndicator;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("Initialize called");
        try {
            timerIds = ruleEngine.getTimerIds();
            System.out.println("timerIds set: " + timerIds);
            setupScoreboard();
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

    private void setupScoreboard() {
        textScoreboard.getChildren().clear();

        HBox line1 = new HBox();
        Label tempLabel = new Label("  G U E S T          ");
        tempLabel.getStyleClass().add("scoreboard-timer");
        line1.getChildren().add(tempLabel);
        mainHornLeft = new Text(">");
        mainHornLeft.setVisible(false);
        mainHornLeft.getStyleClass().add("scoreboard-indicator");
        line1.getChildren().add(mainHornLeft);
        mainTimerLabel = new Label("00:00");
        mainTimerLabel.getStyleClass().add("scoreboard-timer");
        line1.getChildren().add(mainTimerLabel);
        mainHornRight = new Text("<");
        mainHornRight.setVisible(false);
        mainHornRight.getStyleClass().add("scoreboard-indicator");
        line1.getChildren().add(mainHornRight);
        tempLabel = new Label("           H O M E");
        tempLabel.getStyleClass().add("scoreboard-timer");
        line1.getChildren().add(tempLabel);
        textScoreboard.getChildren().add(line1);

        // Line 2: POINTS POINTS
        Label line2 = new Label("   POINTS                              POINTS");
        line2.getStyleClass().add("scoreboard-text-line");
        textScoreboard.getChildren().add(line2);

        // Line 3: guestPoints PERIOD homePoints
        HBox line3 = new HBox();
        line3.getChildren().add(new Label("    "));
        guestPointsLabel = new Label("000");
        guestPointsLabel.getStyleClass().add("scoreboard-timer");
        line3.getChildren().add(guestPointsLabel);
        //line3.getChildren().add(new Label("            PERIOD 1             "));
        tempLabel = new Label("            PERIOD: 1            ");
        tempLabel.getStyleClass().add("scoreboard-timer");
        line3.getChildren().add(tempLabel);
        homePointsLabel = new Label("000");
        homePointsLabel.getStyleClass().add("scoreboard-timer");
        line3.getChildren().add(homePointsLabel);
        textScoreboard.getChildren().add(line3);

        // Line 4: Bonus Indicators (placeholders)
        Label line4 = new Label("   < B B                                B B >");
        line4.getStyleClass().add("scoreboard-text-line");
        textScoreboard.getChildren().add(line4);
        
        // Line 5: Spacer
        Label line5 = new Label(" ");
        line5.getStyleClass().add("scoreboard-text-line");
        textScoreboard.getChildren().add(line5);

        // Line 6: Labels
        Label line6 = new Label(" FOULS  TOL      PLAYER - FOUL      TOL   FOULS");
        line6.getStyleClass().add("scoreboard-text-line");
        textScoreboard.getChildren().add(line6);

        // Line 7: Foul Counters (placeholders)
        Label line7 = new Label("  00     0          00     0         0     00");
        line7.getStyleClass().add("scoreboard-text-line");
        textScoreboard.getChildren().add(line7);

        // Line 8: Spacer
        Label line8 = new Label(" ");
        line8.getStyleClass().add("scoreboard-text-line");
        textScoreboard.getChildren().add(line8);

        // Line 9: Shot Timer (placeholders) + Flash Indicator
        HBox line9 = new HBox();
        tempLabel = new Label("   [===]   VL   SHOT TIMER: >30<   VR   [===]");
        tempLabel.getStyleClass().add("scoreboard-timer");
        line9.getChildren().add(tempLabel);
        flashIndicator = new Text("F");
        flashIndicator.setVisible(false);
        flashIndicator.getStyleClass().add("scoreboard-indicator");
        line9.getChildren().add(flashIndicator);
        textScoreboard.getChildren().add(line9);
        
    }

    public void setRoot(Parent root) {
        this.root = root;
    }

    public void configureButtons() {
        JsonObject uiConfig = ruleEngine.getUiConfig();
        if (uiConfig != null) {
            if (uiConfig.has("buttons")) {
                JsonArray buttons = uiConfig.getAsJsonArray("buttons");
                for (JsonElement btn : buttons) {
                    JsonObject btnConfig = btn.getAsJsonObject();
                    String fxId = btnConfig.get("fxId").getAsString();
                    buttonConfigs.put(fxId, btnConfig);

                    Button button = getButtonByFxId(fxId);
                    if (button != null) {
                        button.setText(btnConfig.get("label").getAsString());
                        button.getStyleClass().removeAll("button");
                        button.getStyleClass().add("basketball-text");
                        button.setWrapText(true);
                        buttonToFxIdMap.put(button, fxId);
                        System.out.println("Labeled button " + fxId + " as '" + btnConfig.get("label").getAsString() + "'");
                    } else {
                        System.out.println("Button with fx:id '" + fxId + "' not found.");
                    }
                }
            }

            if (uiConfig.has("numberButtons")) {
                JsonArray numberButtons = uiConfig.getAsJsonArray("numberButtons");
                for (JsonElement btn : numberButtons) {
                    JsonObject btnConfig = btn.getAsJsonObject();
                    String fxId = btnConfig.get("fxId").getAsString();
                    String digit = btnConfig.get("digit").getAsString();
                    numberButtonMap.put(fxId, digit);
                    Button button = getButtonByFxId(fxId);
                    if (button != null) {
                        buttonToFxIdMap.put(button, fxId);
                    }
                }
            }
        }
    }

    private Button getButtonByFxId(String fxId) {
        switch (fxId) {
            case "buttonB8": return buttonB8;
            case "buttonB7": return buttonB7;
            case "buttonB6": return buttonB6;
            case "buttonB5": return buttonB5;
            case "buttonD8": return buttonD8;
            case "buttonD7": return buttonD7;
            case "buttonF8": return buttonF8;
            case "buttonF6": return buttonF6;
            case "buttonF2": return buttonF2;
            case "buttonF1": return buttonF1;
            case "buttonE3": return buttonE3;
            case "buttonD4": return buttonD4;
            case "buttonD3": return buttonD3;
            case "buttonD2": return buttonD2;
            case "buttonC4": return buttonC4;
            case "buttonC3": return buttonC3;
            case "buttonC2": return buttonC2;
            case "buttonB4": return buttonB4;
            case "buttonB3": return buttonB3;
            case "buttonB2": return buttonB2;
            case "buttonE2": return buttonE2;
            case "buttonE4": return buttonE4;
            default: return null;
        }
    }

    public String getSelectedTimerId() {
        return timerIds.get(currentTimerIndex);
    }

    @FXML
    private void handleGridButton(ActionEvent event) {
        Button button = (Button) event.getSource();
        String fxId = buttonToFxIdMap.get(button);
        System.out.println("Clicked button: " + fxId);
        JsonObject config = buttonConfigs.get(fxId);
        if (config != null) {
            String action = config.has("action") ? config.get("action").getAsString() : "none";
            String target = config.has("target") ? config.get("target").getAsString() : "none";
            System.out.println("Action: " + action + ", Target: " + target);
            ScoreElement element = ruleEngine.getElement(target);

            if (settingMode) {
                if (action.startsWith("set")) {
                    abortSetFunction();
                    return;
                } else if (target.equals(settingTimerId) || target.equals(settingCounterId)) {
                    abortSetFunction();
                } else {
                    executeNonSetAction(element, action, config);
                    return;
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
            } else if ("pause".equals(action)) {
                timer.startstop();
            }
        } else if (element instanceof ScoreIndicator indicator && "activate".equals(action)) {
            if (!indicator.getCurrentValue()) {
                indicator.setCurrentValue(true);
                startHornAnimation(indicator);
            }
        }
        updateUI();
    }

    private void executeAction(ScoreElement element, String action, JsonObject config, String target) {
        if (element instanceof ScoreCounter counter) {
            if ("increment".equals(action)) {
                counter.increment(1);
                updateUI();
            } else if ("setCurrentValue".equals(action)) {
                settingMode = true;
                settingCounterId = target;
                currentButtonConfig = config;
                inputBuffer.setLength(0);
                isInitialPrompt = true;
                displayCounterPrompt(counter);
            }
        } else if (element instanceof ScoreTimer timer) {
            if ("pause".equals(action)) {
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
        } else if (element instanceof ScoreIndicator indicator && "activate".equals(action)) {
            if (!indicator.getCurrentValue()) {
                indicator.setCurrentValue(true);
                startHornAnimation(indicator);
                updateUI();
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
            display.append(prompt.substring(0, bracketStart + 1));

            String format = prompt.substring(bracketStart + 1, bracketEnd);
            if (format.equals("MM:SS")) {
                display.append(String.format("%02d:%02d", minutes, seconds));
            } else if (format.equals("SS.t")) {
                display.append(String.format("%02d.%d", seconds, tenths));
            }
            display.append(">");
            line2LCD.setText(display.toString());
            promptLine1 = currentButtonConfig.has("promptLine1") ? currentButtonConfig.get("promptLine1").getAsString() : "";
            line1LCD.setText(promptLine1.isEmpty() ? prompt : promptLine1);
            line3LCD.setText("");
        }
    }

    private void displayCounterPrompt(ScoreCounter counter) {
        String prompt = getPromptString(currentButtonConfig);
        if (prompt != null) {
            StringBuilder display = new StringBuilder();
            int bracketStart = prompt.indexOf('<');
            int bracketEnd = prompt.indexOf('>');
            display.append(prompt.substring(0, bracketStart + 1));
            display.append(String.format("%02d", counter.getCurrentValue()));
            display.append(">");
            line2LCD.setText(display.toString());
            promptLine1 = currentButtonConfig.has("promptLine1") ? currentButtonConfig.get("promptLine1").getAsString() : "";
            line1LCD.setText(promptLine1.isEmpty() ? prompt : promptLine1);
            line3LCD.setText("");
        }
    }

    private void displayBinaryPrompt(boolean currentValue, String line1, String line2) {
        promptLine1 = line1;
        String displayLine2 = line2.replace("<b>", "<" + (currentValue ? "1" : "0") + ">");
        line1LCD.setText(promptLine1);
        line2LCD.setText(displayLine2);
        line3LCD.setText("");
    }

    private String getPromptString(JsonObject config) {
        if (config.has("minutesPrompt")) {
            return config.get("minutesPrompt").getAsString();
        } else if (config.has("secondsPrompt")) {
            return config.get("secondsPrompt").getAsString();
        } else if (config.has("promptLine2")) {
            return config.get("promptLine2").getAsString();
        }
        return null;
    }

    @FXML
    private void handleNumberClick(ActionEvent event) {
        if (!settingMode) return;
        Button clickedButton = (Button) event.getSource();
        String fxId = buttonToFxIdMap.get(clickedButton);
        String number = numberButtonMap.get(fxId);
        if (number == null) return;

        String action = currentButtonConfig.get("action").getAsString();
        if (action.equals("setAllowShift") || action.equals("setIsUpCounting")) {
            if (!number.equals("0") && !number.equals("1")) return;
            inputBuffer.setLength(0);
            inputBuffer.append(number);
            line2LCD.setText(currentButtonConfig.get("promptLine2").getAsString().replace("<b>", "<" + number + ">"));
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
            line2LCD.setText(display.toString());
        }
    }

    @FXML
    private void handleEnterClick(ActionEvent event) {
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
                if (settingCounterId != null) {
                    int value = Integer.parseInt(inputBuffer.toString().trim());
                    ScoreCounter counter = (ScoreCounter) ruleEngine.getElement(settingCounterId);
                    if (value >= counter.getMinValue() && value <= counter.getMaxValue()) {
                        counter.setCurrentValue(value);
                    }
                    settingCounterId = null;
                } else if (settingTimerId != null) {
                    long nanos = parseInput(format, inputBuffer.toString().trim());
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
            resetUI();
        } catch (NumberFormatException e) {
            line2LCD.setText("Invalid input!");
            line3LCD.setText("Error: " + e.getMessage());
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
                abortSetFunction();
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
                line2LCD.setText(display.toString());
            }
        }
    }

    private void abortSetFunction() {
        settingMode = false;
        settingTimerId = null;
        settingCounterId = null;
        currentButtonConfig = null;
        isInitialPrompt = false;
        inputBuffer.setLength(0);
        promptLine1 = "";
        resetUI();
    }

    private long parseInput(String format, String input) {
        long nanos = 0;
        input = input.replaceAll("\\s", "");
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
        } else if (format.equals("NN")) {
            return Long.parseLong(input);
        }
        return nanos;
    }

    private boolean evaluateCondition(String condition, ScoreElement element) {
        String[] parts = condition.split("\\|\\|");
        for (String part : parts) {
            String trimmedPart = part.trim();
            String[] andParts = trimmedPart.split("&&");
            boolean allTrue = true;
            for (String andPart : andParts) {
                String[] conditionParts = andPart.trim().split("==");
                if (conditionParts.length != 2) continue;
                String attr = conditionParts[0].trim().replace("target.", "");
                String value = conditionParts[1].trim();
                if (element instanceof ScoreTimer timer) {
                    switch (attr) {
                        case "isRunning":
                            allTrue &= timer.isRunning() == Boolean.parseBoolean(value);
                            break;
                        case "allowShift":
                            allTrue &= timer.getAllowShift() == Boolean.parseBoolean(value);
                            break;
                        case "isUpCounting":
                            allTrue &= timer.getIsUpCounting() == Boolean.parseBoolean(value);
                            break;
                    }
                }
                if (!allTrue) break;
            }
            if (allTrue) return true;
        }
        return false;
    }

    private void updateUI() {
        if (timerIds == null || timerIds.isEmpty()) {
            mainTimerLabel.setText("00:00");
            guestPointsLabel.setText("000");
            homePointsLabel.setText("000");
            mainTimerRunningLight.setStyle("-fx-fill: radial-gradient(center 50% 50%, radius 50%, darkred, black);");
            mainHornLeft.setVisible(false);
            mainHornRight.setVisible(false);
            flashIndicator.setVisible(false);
            return;
        }
        ScoreTimer timer = (ScoreTimer) ruleEngine.getElement(timerIds.get(currentTimerIndex));
        if (timer != null) {
            String timerText = timer.getDisplayValue();
            if (timerText.length() == 5) { // MM:SS
                mainTimerLabel.setText(timerText);
            } else if (timerText.length() == 4) { // SS.t
                mainTimerLabel.setText(" " + timerText);
            } else if (timerText.length() == 2) { // SS
                mainTimerLabel.setText("  " + timerText + "  ");
            }
            mainTimerRunningLight.setStyle(timer.isRunning() ?
                "-fx-fill: radial-gradient(center 50% 50%, radius 50%, greenyellow, forestgreen);" :
                "-fx-fill: radial-gradient(center 50% 50%, radius 50%, darkred, black);");
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
            mainTimerLabel.setText("00:00");
            mainTimerRunningLight.setStyle("-fx-fill: radial-gradient(center 50% 50%, radius 50%, darkred, black);");
        }

        ScoreCounter guestPoints = (ScoreCounter) ruleEngine.getElement("guestPoints");
        if (guestPoints != null) {
            guestPointsLabel.setText(String.format("%3d", guestPoints.getCurrentValue()));
        } else {
            guestPointsLabel.setText("000");
        }

        ScoreCounter homePoints = (ScoreCounter) ruleEngine.getElement("homePoints");
        if (homePoints != null) {
            homePointsLabel.setText(String.format("%3d", homePoints.getCurrentValue()));
        } else {
            homePointsLabel.setText("000");
        }

        String hornId = timerIds.get(currentTimerIndex) + "_Horn";
        ScoreIndicator horn = (ScoreIndicator) ruleEngine.getElement(hornId);
        if (horn != null && horn.getCurrentValue()) {
            if (hornTimeline == null) {
                startHornAnimation(horn);
            }
            mainHornLeft.setVisible(true);
            mainHornRight.setVisible(true);
        } else {
            mainHornLeft.setVisible(true);
            mainHornRight.setVisible(true);
        }

        if (!settingMode || promptLine1.isEmpty()) {
            line1LCD.setText(getTimerDisplayText());
        }

        for (Map.Entry<String, JsonObject> entry : buttonConfigs.entrySet()) {
            String fxId = entry.getKey();
            JsonObject config = entry.getValue();
            Button button = getButtonByFxId(fxId);
            if (button != null && config.has("disableWhen")) {
                String condition = config.get("disableWhen").getAsString();
                String target = config.get("target").getAsString();
                ScoreElement element = ruleEngine.getElement(target);
                boolean disable = evaluateCondition(condition, element);
                button.setDisable(disable);
            }
        }
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
            line2LCD.setText("");
            promptLine1 = "";
            line1LCD.setText(getTimerDisplayText());
            line3LCD.setText("");
        }
    }

    private String getTimerDisplayText() {
        ScoreTimer timer = (ScoreTimer) ruleEngine.getElement(timerIds.get(currentTimerIndex));
        String lcdText = "Timer " + (currentTimerIndex + 1) + ": " + (timer != null ? timer.getDisplayValue() : "N/A");
        String hornId = timerIds.get(currentTimerIndex) + "_Horn";
        ScoreIndicator horn = (ScoreIndicator) ruleEngine.getElement(hornId);
        if (horn != null && horn.getCurrentValue()) {
            lcdText += " *";
        }
        if (timer != null && timer.isRunning() && timer.getCurrentValue() / 1_000_000_000.0 < timer.getFlashZoneThreshold() && timer.getFlashZoneThreshold() >= 0) {
            lcdText += " F";
        }
        return lcdText;
    }

    private void startFlashAnimation(ScoreTimer timer) {
        String pattern = timer.getFlashZonePattern();
        if (pattern == null || pattern.isEmpty() || flashIndicator == null) {
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
                new KeyFrame(Duration.millis(cumulativeTime), e -> flashIndicator.setVisible(visible))
            );
            cumulativeTime += duration;
        }
        flashTimeline.getKeyFrames().add(
            new KeyFrame(Duration.millis(cumulativeTime), e -> flashIndicator.setVisible(firstVisible))
        );
        flashTimeline.setCycleCount(Timeline.INDEFINITE);
        flashTimeline.play();
    }

    private void stopFlashAnimation() {
        if (flashTimeline != null) {
            flashTimeline.stop();
            flashTimeline = null;
        }
        if (flashIndicator != null) flashIndicator.setVisible(false);
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
                    new KeyFrame(Duration.millis(cumulativeTime), e -> {
                        mainHornLeft.setVisible(visible);
                        mainHornRight.setVisible(visible);
                    })
                );
                cumulativeTime += duration;
            }
            hornTimeline.getKeyFrames().add(
                new KeyFrame(Duration.millis(cumulativeTime), e -> {
                    mainHornLeft.setVisible(false);
                    mainHornRight.setVisible(false);
                })
            );
            hornTimeline.setOnFinished(e -> {
                horn.setCurrentValue(false);
                hornTimeline = null;
                updateUI();
            });
            mainHornLeft.setVisible(true);
            mainHornRight.setVisible(true);
            hornTimeline.play();
        }
    }
}