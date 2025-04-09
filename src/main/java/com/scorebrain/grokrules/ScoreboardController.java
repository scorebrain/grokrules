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
    private Map<String, Integer> toggleStates = new HashMap<>(); // Track toggle state indices
    private Map<String, Timeline> holdTimers = new HashMap<>();  // Timers for hold actions
    private Map<String, Boolean> isHeld = new HashMap<>();       // Track if hold completed
    private Parent root;
    private boolean isInitialPrompt = false;
    private String promptLine1 = "";

    @FXML private Label line1LCD;
    @FXML private Label line2LCD;
    @FXML private Label line3LCD;
    @FXML private Rectangle mainTimerRunningLight;
    @FXML private Button buttonB8, buttonB7, buttonB6, buttonB5, buttonB4, buttonB3, buttonB2, buttonB1;
    @FXML private Button buttonC8, buttonC7, buttonC6, buttonC5, buttonC4, buttonC3, buttonC2, buttonC1;
    @FXML private Button buttonD8, buttonD7, buttonD6, buttonD5, buttonD4, buttonD3, buttonD2, buttonD1;
    @FXML private Button buttonE8, buttonE7, buttonE6, buttonE5, buttonE4, buttonE3, buttonE2, buttonE1;
    @FXML private Button buttonF8, buttonF6, buttonF4, buttonF2, buttonF1;
    @FXML private Button buttonG8, buttonG7, buttonG6;
    @FXML private VBox textScoreboard;
    @FXML private Label line1Label, line2Label, line3Label, line4Label, line5Label, line6Label, line7Label, line8Label, line9Label;
    private Label mainTimerLabel, team1PointsLabel, team2PointsLabel, periodLabel;
    private Text mainHornLeft, mainHornRight;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            timerIds = ruleEngine.getTimerIds();
            setupScoreboard();
            resetUI();
            startUITimer();
            for (ScoreElement element : ruleEngine.getElements()) {
                if (element instanceof ScoreIndicator) {
                    ((ScoreIndicator) element).setSelectedTimerSupplier(this::getSelectedTimerId);
                    // ScoreIndicator indicator = (ScoreIndicator) element;
                    // indicator.setSelectedTimerSupplier(this::getSelectedTimerId);
                }
            }
        } catch (Exception e) {
            System.err.println("Exception in initialize:");
            e.printStackTrace();
        }
    }

    private void setupScoreboard() {
        textScoreboard.getChildren().clear();

        // Line 1: G U E S T >mainTimer< H O M E
        HBox line1 = new HBox();
        Label team1Label = createStyledLabel("  G U E S T          ", "scoreboard-timer");
        line1.getChildren().add(team1Label);
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
        Label team2Label = createStyledLabel("           H O M E", "scoreboard-timer");
        line1.getChildren().add(team2Label);
        textScoreboard.getChildren().add(line1);

        // Line 2: POINTS POINTS
        Label line2 = createStyledLabel("   POINTS                              POINTS", "scoreboard-text-line");
        textScoreboard.getChildren().add(line2);

        // Line 3: team1Points PERIOD periodCount team2Points
        HBox line3 = new HBox();
        line3.getChildren().add(createStyledLabel("    ", "scoreboard-timer"));
        team1PointsLabel = createStyledLabel("000", "scoreboard-timer");
        line3.getChildren().add(team1PointsLabel);
        periodLabel = createStyledLabel("            PERIOD: 1            ", "scoreboard-timer");
        line3.getChildren().add(periodLabel);
        team2PointsLabel = createStyledLabel("000", "scoreboard-timer");
        line3.getChildren().add(team2PointsLabel);
        textScoreboard.getChildren().add(line3);

        // Line 4: Bonus Indicators with possession indicators (use @FXML line4Label)
        line4Label.setText("   ? B B                                B B ?"); // Initial text with ? placeholders
        line4Label.getStyleClass().clear(); // Clear any existing styles if necessary
        line4Label.getStyleClass().add("scoreboard-text-line");
        textScoreboard.getChildren().add(line4Label);

        // Line 5: Spacer
        Label line5 = createStyledLabel(" ", "scoreboard-text-line");
        textScoreboard.getChildren().add(line5);

        // Line 6: Labels
        Label line6 = createStyledLabel(" FOULS  TOL      PLAYER - FOUL      TOL   FOULS", "scoreboard-text-line");
        textScoreboard.getChildren().add(line6);

        // Line 7: Foul Counters with @FXML line7Label
        line7Label.setText("  00     0           -     -         0     00"); // Initial blank state
        line7Label.getStyleClass().clear();
        line7Label.getStyleClass().add("scoreboard-text-line");
        textScoreboard.getChildren().add(line7Label);
    
        // Line 8: Spacer
        Label line8 = createStyledLabel(" ", "scoreboard-text-line");
        textScoreboard.getChildren().add(line8);

        // Line 9: Shot Timer
        Label line9 = createStyledLabel("   [===]   VL   SHOT TIMER: >30<   VR   [===]", "scoreboard-text-line");
        textScoreboard.getChildren().add(line9);
    }

    private Label createStyledLabel(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
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

                        // Initialize toggle buttons
                        if ("toggle".equals(btnConfig.get("action").getAsString())) {
                            toggleStates.put(fxId, 0);
                            isHeld.put(fxId, false);
                            button.setOnMousePressed(event -> startHoldTimer(fxId));
                            button.setOnMouseReleased(event -> handleButtonRelease(fxId));
                        }
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
            case "buttonB4": return buttonB4;
            case "buttonB3": return buttonB3;
            case "buttonB2": return buttonB2;
            case "buttonB1": return buttonB1;
            case "buttonC8": return buttonC8;
            case "buttonC7": return buttonC7;
            case "buttonC6": return buttonC6;
            case "buttonC5": return buttonC5;
            case "buttonC4": return buttonC4;
            case "buttonC3": return buttonC3;
            case "buttonC2": return buttonC2;
            case "buttonC1": return buttonC1;
            case "buttonD8": return buttonD8;
            case "buttonD7": return buttonD7;
            case "buttonD6": return buttonD6;
            case "buttonD5": return buttonD5;
            case "buttonD4": return buttonD4;
            case "buttonD3": return buttonD3;
            case "buttonD2": return buttonD2;
            case "buttonD1": return buttonD1;
            case "buttonE8": return buttonE8;
            case "buttonE7": return buttonE7;
            case "buttonE6": return buttonE6;
            case "buttonE5": return buttonE5;
            case "buttonE4": return buttonE4;
            case "buttonE3": return buttonE3;
            case "buttonE2": return buttonE2;
            case "buttonE1": return buttonE1;
            case "buttonF8": return buttonF8;
            case "buttonF6": return buttonF6;
            case "buttonF4": return buttonF4;
            case "buttonF2": return buttonF2;
            case "buttonF1": return buttonF1;
            case "buttonG8": return buttonG8;
            case "buttonG7": return buttonG7;
            case "buttonG6": return buttonG6;
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
        JsonObject config = buttonConfigs.get(fxId);
        if (config != null) {
            String action = config.has("action") ? config.get("action").getAsString() : "none";
            String target = config.has("target") ? config.get("target").getAsString() : "none";
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

            // Handle toggle action separately since it uses mouse events
            if (!"toggle".equals(action)) {
                executeAction(element, action, config, target);
            }
        }
    }
    
    private void startHoldTimer(String fxId) {
        JsonObject config = buttonConfigs.get(fxId);
        if (config.has("holdAction")) {
            JsonObject holdAction = config.getAsJsonObject("holdAction");
            int duration = holdAction.get("duration").getAsInt();
            Timeline timer = new Timeline(new KeyFrame(Duration.millis(duration), e -> {
                isHeld.put(fxId, true);
                executeHoldAction(fxId);
            }));
            timer.setCycleCount(1);
            holdTimers.put(fxId, timer);
            timer.play();
        }
    }

    private void handleButtonRelease(String fxId) {
        Timeline timer = holdTimers.get(fxId);
        if (timer != null) {
            timer.stop();
            holdTimers.remove(fxId);
        }
        if (!isHeld.getOrDefault(fxId, false)) {
            executeToggleAction(fxId);
        }
        isHeld.put(fxId, false);
    }
    
    private void executeToggleAction(String fxId) {
        JsonObject config = buttonConfigs.get(fxId);
        JsonArray states = config.getAsJsonArray("states");
        int currentStateIndex = toggleStates.get(fxId);
        JsonObject state = states.get(currentStateIndex).getAsJsonObject();
        for (String target : state.keySet()) {
            ScoreIndicator indicator = (ScoreIndicator) ruleEngine.getElement(target);
            if (indicator != null) {
                indicator.setCurrentValue(state.get(target).getAsBoolean());
            }
        }
        int nextStateIndex = (currentStateIndex + 1) % states.size();
        toggleStates.put(fxId, nextStateIndex);
        updateUI();
    }

    private void executeHoldAction(String fxId) {
        JsonObject config = buttonConfigs.get(fxId);
        JsonObject holdAction = config.getAsJsonObject("holdAction");
        String action = holdAction.get("action").getAsString();
        JsonArray targets = holdAction.getAsJsonArray("targets");
        if ("deactivate".equals(action)) {
            for (JsonElement targetElem : targets) {
                String target = targetElem.getAsString();
                ScoreIndicator indicator = (ScoreIndicator) ruleEngine.getElement(target);
                if (indicator != null) {
                    indicator.setCurrentValue(false);
                }
            }
            updateUI();
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
            } else if ("decrement".equals(action)) {
                counter.decrement(1);
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
            String format = prompt.substring(bracketStart + 1, bracketEnd);
            int digitCount = format.replaceAll("[^N]", "").length();
            String formatStr = "%0" + digitCount + "d";
            display.append(String.format(formatStr, counter.getCurrentValue()));
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

        String prompt = getPromptString(currentButtonConfig);
        if (prompt == null) return;

        isInitialPrompt = false;
        int bracketStart = prompt.indexOf('<');
        int bracketEnd = prompt.indexOf('>');
        String format = prompt.substring(bracketStart + 1, bracketEnd);

        if (settingTimerId != null) {
            if (format.equals("MM:SS")) {
                // Handle MM:SS format: 4 digits with colon
                if (inputBuffer.length() < 4) {
                    inputBuffer.append(number);
                } else {
                    inputBuffer.deleteCharAt(0).append(number);
                }
                String input = inputBuffer.toString();
                while (input.length() < 4) {
                    input = "0" + input; // Pad with leading zeros
                }
                String displayInput = input.substring(0, 2) + ":" + input.substring(2, 4);
                line2LCD.setText(prompt.substring(0, bracketStart + 1) + displayInput + ">");
            } else if (format.equals("SS.t")) {
                // Handle SS.t format: 3 digits with dot
                if (inputBuffer.length() < 3) {
                    inputBuffer.append(number);
                } else {
                    inputBuffer.deleteCharAt(0).append(number);
                }
                String input = inputBuffer.toString();
                while (input.length() < 3) {
                    input = "0" + input; // Pad with leading zeros
                }
                String displayInput = input.substring(0, 2) + "." + input.substring(2, 3);
                line2LCD.setText(prompt.substring(0, bracketStart + 1) + displayInput + ">");
            }
        } else if (settingCounterId != null) {
            // Existing counter logic remains unchanged
            if (settingCounterId.equals("playerNumber")) {
                String currentInput = inputBuffer.toString().trim();
                if (currentInput.equals("00") && inputBuffer.length() == 2) {
                    inputBuffer.setLength(0);
                    inputBuffer.append(number);
                } else {
                    inputBuffer.append(number);
                    if (inputBuffer.length() > 2) {
                        inputBuffer.deleteCharAt(0);
                    }
                }
                String input = inputBuffer.toString().trim();
                String displayInput = input.length() == 1 ? " " + input : input;
                line2LCD.setText(prompt.substring(0, bracketStart + 1) + displayInput + ">");
            } else if (settingCounterId.equals("playerFoul")) {
                inputBuffer.setLength(0);
                inputBuffer.append(number);
                line2LCD.setText(prompt.substring(0, bracketStart + 1) + number + ">");
            } else {
                int digitCount = format.replaceAll("[^N]", "").length();
                if (inputBuffer.length() == 0) {
                    inputBuffer.append(" ".repeat(digitCount - 1)).append(number);
                } else if (inputBuffer.length() < digitCount) {
                    inputBuffer.append(number);
                } else {
                    inputBuffer.deleteCharAt(0).append(number);
                }
                line2LCD.setText(prompt.substring(0, bracketStart + 1) + inputBuffer.toString() + ">");
            }
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
                if (settingCounterId != null) {
                    ScoreCounter counter = (ScoreCounter) ruleEngine.getElement(settingCounterId);
                    String input = inputBuffer.toString().trim();
                    if (settingCounterId.equals("playerNumber")) {
                        if (input.length() == 1) {
                            int value = Integer.parseInt(input);
                            counter.setCurrentValue(value);
                            counter.setLeadingZero(false);
                            counter.setBlank(false);
                        } else if (input.length() == 2) {
                            int value = Integer.parseInt(input);
                            counter.setCurrentValue(value);
                            counter.setLeadingZero(input.charAt(0) == '0');
                            counter.setBlank(false);
                        }
                    } else if (settingCounterId.equals("playerFoul")) {
                        int value = Integer.parseInt(input);
                        counter.setCurrentValue(value);
                        counter.setBlank(false);
                    } else {
                        int value = Integer.parseInt(input);
                        counter.setCurrentValue(value);
                    }
                    settingCounterId = null;
                } else if (settingTimerId != null) {
                    ScoreTimer timer = (ScoreTimer) ruleEngine.getElement(settingTimerId);
                    String prompt = getPromptString(currentButtonConfig);
                    int bracketStart = prompt.indexOf('<');
                    int bracketEnd = prompt.indexOf('>');
                    String format = prompt.substring(bracketStart + 1, bracketEnd);
                    long nanos = parseInput(format, inputBuffer.toString().trim());
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
            team1PointsLabel.setText("000");
            team2PointsLabel.setText("000");
            mainTimerRunningLight.setStyle("-fx-fill: radial-gradient(center 50% 50%, radius 50%, darkred, black);");
            mainHornLeft.setVisible(false);
            mainHornRight.setVisible(false);
            return;
        }
        ScoreTimer timer = (ScoreTimer) ruleEngine.getElement(timerIds.get(currentTimerIndex));
        if (timer != null) {
            long nanos = timer.getCurrentValue();
            long totalSeconds = nanos / 1_000_000_000L;
            long tenths = (nanos % 1_000_000_000L) / 100_000_000L;
            String timerText;
            if (totalSeconds >= 60 || !timer.getAllowShift()) {
                long minutes = totalSeconds / 60;
                long seconds = totalSeconds % 60;
                timerText = String.format("%2d:%02d", minutes, seconds);
                if (minutes < 10) timerText = " " + timerText.substring(1);
            } else {
                long seconds = totalSeconds;
                timerText = String.format("%2d.%d ", seconds, tenths);
                if (seconds < 10) timerText = " " + timerText.substring(1);
            }
            mainTimerLabel.setText(timerText);

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

        ScoreCounter team1Points = (ScoreCounter) ruleEngine.getElement("team1Points");
        if (team1Points != null) {
            team1PointsLabel.setText(String.format("%3d", team1Points.getCurrentValue()));
        } else {
            team1PointsLabel.setText("000");
        }

        ScoreCounter team2Points = (ScoreCounter) ruleEngine.getElement("team2Points");
        if (team2Points != null) {
            team2PointsLabel.setText(String.format("%3d", team2Points.getCurrentValue()));
        } else {
            team2PointsLabel.setText("000");
        }
        
        ScoreCounter periodCount = (ScoreCounter) ruleEngine.getElement("periodCount");
        if (periodCount != null) {
            periodLabel.setText("            PERIOD: " + periodCount.getCurrentValue() + "            ");
        } else {
            periodLabel.setText("            PERIOD: 1            ");
        }
        
        // Fetch indicators and counters
        ScoreIndicator team1Poss = (ScoreIndicator) ruleEngine.getElement("team1Possession");
        ScoreIndicator team2Poss = (ScoreIndicator) ruleEngine.getElement("team2Possession");
        ScoreIndicator team1Bonus1 = (ScoreIndicator) ruleEngine.getElement("team1Bonus1");
        ScoreIndicator team1Bonus2 = (ScoreIndicator) ruleEngine.getElement("team1Bonus2");
        ScoreIndicator team2Bonus1 = (ScoreIndicator) ruleEngine.getElement("team2Bonus1");
        ScoreIndicator team2Bonus2 = (ScoreIndicator) ruleEngine.getElement("team2Bonus2");
        ScoreCounter playerNumber = (ScoreCounter) ruleEngine.getElement("playerNumber");
        ScoreCounter playerFoul = (ScoreCounter) ruleEngine.getElement("playerFoul");

        // Update textScoreboard Line 4
        StringBuilder line4Text = new StringBuilder("   ");
        if (team1Poss != null && team1Poss.getCurrentValue()) {
            line4Text.append("< ");
        } else {
            line4Text.append("  ");
        }
        if (team1Bonus2 != null && team1Bonus2.getCurrentValue()) {
            line4Text.append("B ");
        } else {
            line4Text.append("  ");
        }
        if (team1Bonus1 != null && team1Bonus1.getCurrentValue()) {
            line4Text.append("B ");
        } else {
            line4Text.append("  ");
        }
        line4Text.append("                               ");
        if (team2Bonus1 != null && team2Bonus1.getCurrentValue()) {
            line4Text.append("B ");
        } else {
            line4Text.append("  ");
        }
        if (team2Bonus2 != null && team2Bonus2.getCurrentValue()) {
            line4Text.append("B ");
        } else {
            line4Text.append("  ");
        }
        if (team2Poss != null && team2Poss.getCurrentValue()) {
            line4Text.append(">");
        } else {
            line4Text.append(" ");
        }
        line4Label.setText(line4Text.toString());
        
        // Update textScoreboard Line 7
        StringBuilder line7Text = new StringBuilder("  00     0           -     -         0     00");
        if (playerNumber != null && !playerNumber.isBlank()) {
            String numberStr;
            int value = playerNumber.getCurrentValue();
            if (value >= 10) {
                numberStr = String.format("%2d", value);
            } else if (playerNumber.getLeadingZero()) {
                numberStr = String.format("0%d", value);
            } else {
                numberStr = String.format(" %d", value);
            }
            line7Text.replace(20, 22, numberStr); // Columns 21-22 (0-based 20-21)
        }
        if (playerFoul != null && !playerFoul.isBlank()) {
            line7Text.replace(27, 28, String.valueOf(playerFoul.getCurrentValue())); // Column 28 (0-based 27)
        }
        line7Label.setText(line7Text.toString());

        // Update LCD Line 2 only when not in setting mode
        if (!settingMode) {
            StringBuilder lcdLine2 = new StringBuilder("                "); // 16 characters
            if (team1Poss != null && team1Poss.getCurrentValue()) {
                lcdLine2.setCharAt(0, '<');
            }
            if (team1Bonus1 != null && team1Bonus1.getCurrentValue()) {
                lcdLine2.setCharAt(1, team1Bonus2 != null && team1Bonus2.getCurrentValue() ? 'B' : 'b');
            }
            lcdLine2.replace(2, 6, "    ");
            if (playerNumber != null) {
                lcdLine2.replace(6, 8, playerNumber.isBlank() ? "00" : String.format("%02d", playerNumber.getCurrentValue()));
            }
            lcdLine2.setCharAt(8, ' ');
            if (playerFoul != null) {
                lcdLine2.setCharAt(9, playerFoul.isBlank() ? '0' : String.valueOf(playerFoul.getCurrentValue()).charAt(0));
            }
            lcdLine2.replace(10, 15, "     ");
            if (team2Bonus1 != null && team2Bonus1.getCurrentValue()) {
                lcdLine2.setCharAt(14, team2Bonus2 != null && team2Bonus2.getCurrentValue() ? 'B' : 'b');
            }
            if (team2Poss != null && team2Poss.getCurrentValue()) {
                lcdLine2.setCharAt(15, '>');
            }
            line2LCD.setText(lcdLine2.toString());
        }

        String hornId = timerIds.get(currentTimerIndex) + "_Horn";
        ScoreIndicator horn = (ScoreIndicator) ruleEngine.getElement(hornId);
        if (horn != null && horn.getCurrentValue()) {
            if (hornTimeline == null) {
                startHornAnimation(horn);
            }
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
        ScoreCounter team1Points = (ScoreCounter) ruleEngine.getElement("team1Points");
        ScoreCounter team2Points = (ScoreCounter) ruleEngine.getElement("team2Points");
        ScoreCounter periodCount = (ScoreCounter) ruleEngine.getElement("periodCount");

        String team1PointsText = team1Points != null ? String.format("%03d", team1Points.getCurrentValue()) : "000";
        String team2PointsText = team2Points != null ? String.format("%03d", team2Points.getCurrentValue()) : "000";
        String periodCountText = periodCount != null ? String.valueOf(periodCount.getCurrentValue()) : "1";
        String modeText = timer != null ? (timer.getIsUpCounting() ? "U" : "D") : "D";

        String timerText;
        if (timer != null) {
            long nanos = timer.getCurrentValue();
            long totalSeconds = nanos / 1_000_000_000L;
            long tenths = (nanos % 1_000_000_000L) / 100_000_000L;
            if (totalSeconds >= 60 || !timer.getAllowShift()) {
                long minutes = totalSeconds / 60;
                long seconds = totalSeconds % 60;
                timerText = String.format("%02d:%02d", minutes, seconds);
            } else {
                long seconds = totalSeconds;
                timerText = String.format("%02d.%d ", seconds, tenths);
            }
        } else {
            timerText = "00:00";
        }

        return team1PointsText + " " + modeText + timerText + " " + periodCountText + " " + team2PointsText;
    }

    private void startFlashAnimation(ScoreTimer timer) {
        String pattern = timer.getFlashZonePattern();
        if (pattern == null || pattern.isEmpty()) return;
        String[] steps = pattern.split(",");
        flashTimeline = new Timeline();
        double cumulativeTime = 0;
        boolean firstVisible = Boolean.parseBoolean(steps[0].split(":")[1]);
        for (String step : steps) {
            String[] parts = step.split(":");
            double duration = Double.parseDouble(parts[0]);
            boolean visible = Boolean.parseBoolean(parts[1]);
            flashTimeline.getKeyFrames().add(
                new KeyFrame(Duration.millis(cumulativeTime), e -> mainTimerLabel.setVisible(visible))
            );
            cumulativeTime += duration;
        }
        flashTimeline.getKeyFrames().add(
            new KeyFrame(Duration.millis(cumulativeTime), e -> mainTimerLabel.setVisible(firstVisible))
        );
        flashTimeline.setCycleCount(Timeline.INDEFINITE);
        flashTimeline.play();
    }

    private void stopFlashAnimation() {
        if (flashTimeline != null) {
            flashTimeline.stop();
            flashTimeline = null;
        }
        mainTimerLabel.setVisible(true);
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