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
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.text.Text;
import javafx.util.Duration;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;

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
    private String settingCounterId = "none";
    private String settingTimerId = "none";
    private String settingIndicatorId = "none";
    private String settingAttribute = "none";
    private String settingPromptLine2 = "none";
    private int settingCursorPos = 1;
    private int settingCursorMax = 1;
    private Map<String, JsonObject> buttonConfigs = new HashMap<>();
    private Map<Button, String> buttonToFxIdMap = new HashMap<>();
    private Map<String, String> numberButtonMap = new HashMap<>();
    private Map<String, Integer> toggleStates = new HashMap<>();
    private Map<String, Timeline> holdTimers = new HashMap<>();
    private Map<String, Boolean> isHeld = new HashMap<>();
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

        Label line2 = createStyledLabel("   POINTS                              POINTS", "scoreboard-text-line");
        textScoreboard.getChildren().add(line2);

        HBox line3 = new HBox();
        line3.getChildren().add(createStyledLabel("    ", "scoreboard-timer"));
        team1PointsLabel = createStyledLabel("000", "scoreboard-timer");
        line3.getChildren().add(team1PointsLabel);
        periodLabel = createStyledLabel("            PERIOD: 1            ", "scoreboard-timer");
        line3.getChildren().add(periodLabel);
        team2PointsLabel = createStyledLabel("000", "scoreboard-timer");
        line3.getChildren().add(team2PointsLabel);
        textScoreboard.getChildren().add(line3);

        line4Label.setText("   ? B B                                B B ?");
        line4Label.getStyleClass().clear();
        line4Label.getStyleClass().add("scoreboard-text-line");
        textScoreboard.getChildren().add(line4Label);

        Label line5 = createStyledLabel(" ", "scoreboard-text-line");
        textScoreboard.getChildren().add(line5);

        Label line6 = createStyledLabel(" FOULS  TOL      PLAYER - FOUL      TOL   FOULS", "scoreboard-text-line");
        textScoreboard.getChildren().add(line6);

        line7Label.setText("  00     0                    0         0     00");
        line7Label.getStyleClass().clear();
        line7Label.getStyleClass().add("scoreboard-text-line");
        textScoreboard.getChildren().add(line7Label);

        Label line8 = createStyledLabel(" ", "scoreboard-text-line");
        textScoreboard.getChildren().add(line8);

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
                        // Checking buttonE7 and buttonE8 explicitly is bad coupling.  Fix this later!!
                        if (fxId.equals("buttonE8") || fxId.equals("buttonE7")) {
                            VBox content = new VBox(2);
                            content.setAlignment(Pos.CENTER);
                            Label teamLabel = new Label("TEAM");
                            teamLabel.getStyleClass().add("basketball-small-text");
                            Label foulsLabel = new Label("FOULS");
                            foulsLabel.getStyleClass().add("basketball-small-text");
                            Rectangle blueLine = new Rectangle(38, 2, Color.web("#011e41"));
                            Label pointsLabel = new Label("POINTS");
                            pointsLabel.getStyleClass().add("gray-small-text");
                            Rectangle grayLine = new Rectangle(29, 1, Color.web("#555555"));
                            Label wonLabel = new Label("WON");
                            wonLabel.getStyleClass().add("gray-small-text");
                            content.getChildren().addAll(teamLabel, foulsLabel, blueLine, pointsLabel, grayLine, wonLabel);
                            button.setGraphic(content);
                            button.setText("");
                        } else {
                            button.setText(btnConfig.get("label").getAsString());
                        }
                        button.getStyleClass().removeAll("button");
                        button.getStyleClass().add("basketball-text");
                        button.setWrapText(true);
                        buttonToFxIdMap.put(button, fxId);
                        toggleStates.put(fxId, 0);
                        isHeld.put(fxId, false);
                        button.setOnMousePressed(event -> startHoldTimer(fxId));
                        button.setOnMouseReleased(event -> handleButtonRelease(fxId));
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

    private void startHoldTimer(String fxId) {
        System.out.println("Hold Timer started.");
        JsonObject config = buttonConfigs.get(fxId);
        if (config.has("alt1")) {
            JsonObject alt1 = config.getAsJsonObject("alt1");
            int duration = alt1.get("alt1Duration").getAsInt();
            Timeline timer = new Timeline(new KeyFrame(Duration.millis(duration), e -> {
                isHeld.put(fxId, true);
            }));
            timer.setCycleCount(1);
            holdTimers.put(fxId, timer);
            timer.play();
        }
    }

    private void handleButtonRelease(String fxId) {
        Timeline timeline = holdTimers.get(fxId);
        if (timeline != null) {
            timeline.stop();
            holdTimers.remove(fxId);
            System.out.println("Timeline stopped. Does this line ever print?");
        }
        JsonObject config = buttonConfigs.get(fxId);
        String action = config.has("mainAction") ? config.get("mainAction").getAsString() : "none";
        JsonArray targets = config.has("mainTargets") ? config.getAsJsonArray("mainTargets") : null;
        JsonArray states = config.has("mainStates") ? config.getAsJsonArray("mainStates") : null;
        String promptLine1 = config.has("mainPromptLine1") ? config.get("mainPromptLine1").getAsString() : "none";
        String promptLine2 = config.has("mainPromptLine2") ? config.get("mainPromptLine2").getAsString() : "none";
        String attribute = config.has("mainAttribute") ? config.get("mainAttribute").getAsString() : "none";
        int amount = config.has("mainAmount") ? config.get("mainAmount").getAsInt() : 1;
        if (isHeld.getOrDefault(fxId, false)) {
            if (config.has("alt1")) {
                // If we have a "hold Action" substitute it for the "main Action" (otherwise fall through with "main Action")
                JsonObject alt1Config = config.getAsJsonObject("alt1");
                action = alt1Config.has("alt1Action") ? alt1Config.get("alt1Action").getAsString() : "none";
                targets = alt1Config.has("alt1Targets") ? alt1Config.getAsJsonArray("alt1Targets") : null;
                states = alt1Config.has("alt1States") ? alt1Config.getAsJsonArray("alt1States") : null;
                promptLine1 = alt1Config.has("alt1PromptLine1") ? alt1Config.get("alt1PromptLine1").getAsString() : "none";
                promptLine2 = alt1Config.has("alt1PromptLine2") ? alt1Config.get("alt1PromptLine2").getAsString() : "none";
                attribute = config.has("alt1Attribute") ? config.get("alt1Attribute").getAsString() : "none";
                amount = alt1Config.has("alt1Amount") ? alt1Config.get("alt1Amount").getAsInt() : 1;
            }
            isHeld.put(fxId, false);
	}
        if (config.has("alt0") && !settingMode) {
            // This is for tagging buttons like CLEAR or ENTER with special non-setting actions
            JsonObject alt1Config = config.getAsJsonObject("alt0");
            action = alt1Config.has("alt0Action") ? alt1Config.get("alt0Action").getAsString() : "none";
            targets = alt1Config.has("alt0Targets") ? alt1Config.getAsJsonArray("alt0Targets") : null;
            states = alt1Config.has("alt0States") ? alt1Config.getAsJsonArray("alt0States") : null;
            promptLine1 = alt1Config.has("alt0PromptLine1") ? alt1Config.get("alt0PromptLine1").getAsString() : "none";
            promptLine2 = alt1Config.has("alt0PromptLine2") ? alt1Config.get("alt0PromptLine2").getAsString() : "none";
            attribute = config.has("alt0Attribute") ? config.get("alt0Attribute").getAsString() : "none";
            amount = alt1Config.has("alt0Amount") ? alt1Config.get("alt0Amount").getAsInt() : 1;
        }
        // If action = Number or Clear or Enter, there will be no "targets" array
        if ("number".equals(action)) {
            System.out.println("label = " + config.get("label").getAsString() + "  promptLine2 = " + promptLine2);
            handleNumberClick(config.get("label").getAsString());
        } else if ("enter".equals(action)) {
            handleEnterClick();
        } else if ("clear".equals(action)) {
            handleClearClick();
        } else {
            for (JsonElement targetElem : targets) {
                String target = targetElem.getAsString();
                ScoreElement element = target != null ? ruleEngine.getElement(target) : null;
                if (element instanceof ScoreTimer timer) {
                    if ("start".equals(action) && !timer.isRunning()) {
                        timer.startstop();
                    } else if ("stop".equals(action) && timer.isRunning()) {
                        timer.startstop();
                    } else if ("pause".equals(action)) {
                        timer.startstop();
                    } else if ("setCurrentValue".equals(action) && !settingMode) {
                        settingMode = true;
                        settingTimerId = target;
                        settingCounterId = "none";
                        settingIndicatorId = "none";
                        inputBuffer.setLength(0);
                        isInitialPrompt = true;
                        settingCursorPos = 1;
                        settingCursorMax = 1;
                        displayTimerPrompt(timer.getCurrentValue(), promptLine1, promptLine2);
                    } else if ("setAttributeValue".equals(action) && !settingMode) {
                        settingMode = true;
                        settingTimerId = target;
                        settingCounterId = "none";
                        settingIndicatorId = "none";
                        settingAttribute = attribute;
                        inputBuffer.setLength(0);
                        isInitialPrompt = true;
                        settingCursorPos = 1;
                        settingCursorMax = 1;
                        if ("isUpCounting".equals(attribute)) {
                            displayIntPrompt(timer.getIsUpCounting() ? 1: 0, promptLine1, promptLine2);
                        } else if ("allowShift".equals(attribute)) {
                            displayIntPrompt(timer.getAllowShift() ? 1: 0, promptLine1, promptLine2);
                        } else {
                            settingMode = false;
                        }
                    }
                } else if (!settingMode && element instanceof ScoreCounter counter) {
                    if ("increment".equals(action)) {
                        counter.increment(amount);
                    } else if ("decrement".equals(action)) {
                        counter.decrement(amount);
                    } else if ("setCurrentValue".equals(action)) {
                        settingMode = true;
                        settingCounterId = target;
                        settingTimerId = "none";
                        settingIndicatorId = "none";
                        inputBuffer.setLength(0);
                        isInitialPrompt = true;
                        settingCursorPos = 1;
                        settingCursorMax = 1;
                        displayIntPrompt(counter.getCurrentValue(), promptLine1, promptLine2);
                    } else if ("setAttributeValue".equals(action) && !settingMode) {
                        // The Max/Min Value stuff below is just an example -- not really used anywhere
                        settingMode = true;
                        settingCounterId = target;
                        settingTimerId = "none";
                        settingIndicatorId = "none";
                        settingAttribute = attribute;
                        inputBuffer.setLength(0);
                        isInitialPrompt = true;
                        settingCursorPos = 1;
                        settingCursorMax = 1;
                        if ("isMaxValue".equals(attribute)) {
                            displayIntPrompt(counter.getMaxValue(), promptLine1, promptLine2);
                        } else if ("isMinValue".equals(attribute)) {
                            displayIntPrompt(counter.getMinValue(), promptLine1, promptLine2);
                        } else {
                            settingMode = false;
                        }
                    }
                } else if (!settingMode && element instanceof ScoreIndicator indicator) {
                    if ("activate".equals(action) && !indicator.getCurrentValue()) {
                        indicator.setCurrentValue(true);
                        startHornAnimation(indicator);
                    } else if ("deactivate".equals(action) && indicator.getCurrentValue()) {
                        indicator.setCurrentValue(false);
                        // Stop animation??
                    } else if ("setCurrentValue".equals(action)) {
                        settingMode = true;
                        settingIndicatorId = target;
                        settingCounterId = "none";
                        settingTimerId = "none";
                        inputBuffer.setLength(0);
                        isInitialPrompt = true;
                        settingCursorPos = 1;
                        settingCursorMax = 1;
                        displayIntPrompt(indicator.getCurrentValue() ? 1 : 0, promptLine1, promptLine2);
                    } else if ("toggle".equals(action)) {
                        int currentStateIndex = toggleStates.get(fxId);
                        JsonObject state = states.get(currentStateIndex).getAsJsonObject();
                        for (String stateTarget : state.keySet()) {
                            ScoreIndicator indicatorToToggle = (ScoreIndicator) ruleEngine.getElement(stateTarget);
                            if (indicatorToToggle != null) {
                                indicatorToToggle.setCurrentValue(state.get(stateTarget).getAsBoolean());
                            }
                        }
                        int nextStateIndex = (currentStateIndex + 1) % states.size();
                        toggleStates.put(fxId, nextStateIndex);
                    } else if ("setAttributeValue".equals(action) && !settingMode) {
                        // The isExample stuff below is just an example -- not really used anywhere
                        settingMode = true;
                        settingIndicatorId = target;
                        settingCounterId = "none";
                        settingTimerId = "none";
                        settingAttribute = attribute;
                        inputBuffer.setLength(0);
                        isInitialPrompt = true;
                        settingCursorPos = 1;
                        settingCursorMax = 1;
                        if ("isExample".equals(attribute)) {
                            displayIntPrompt(indicator.getCurrentValue() ? 1 : 0, promptLine1, promptLine2);
                        } else {
                            settingMode = false;
                        }
                    }
                }
            }
            settingPromptLine2 = promptLine2;
        }
        updateUI();
    }
    
    private void handleNumberClick(String number) {
        if (!settingMode) return;
        if (number == null) return;
        if ("none".equals(settingPromptLine2)) return;
        isInitialPrompt = false;
        int bracketStart = settingPromptLine2.indexOf('<');
        int bracketEnd = settingPromptLine2.indexOf('>');
        String format = settingPromptLine2.substring(bracketStart + 1, bracketEnd);
        if (settingCursorPos < settingCursorMax) {
            settingCursorPos++;
        }
        if (!"none".equals(settingTimerId) && "none".equals(settingAttribute)) {
            if ("MM:SS".equals(format)) {
                if (inputBuffer.length() < 4) {
                    inputBuffer.append(number);
                } else {
                    inputBuffer.deleteCharAt(0).append(number);
                }
                String input = inputBuffer.toString();
                while (input.length() < 4) {
                    input = "0" + input;
                }
                String displayInput = input.substring(0, 2) + ":" + input.substring(2, 4);
                line2LCD.setText(settingPromptLine2.substring(0, bracketStart + 1) + displayInput + ">");
                settingCursorMax = 4;
            } else if ("SS.X".equals(format)) {
                if (inputBuffer.length() < 3) {
                    inputBuffer.append(number);
                } else {
                    inputBuffer.deleteCharAt(0).append(number);
                }
                String input = inputBuffer.toString();
                while (input.length() < 3) {
                    input = "0" + input;
                }
                String displayInput = input.substring(0, 2) + "." + input.substring(2, 3);
                line2LCD.setText(settingPromptLine2.substring(0, bracketStart + 1) + displayInput + ">");
                settingCursorMax = 3;
            }
        } else if (!"none".equals(settingCounterId) || (!"none".equals(settingTimerId) && !"none".equals(settingAttribute)) || (!"none".equals(settingIndicatorId) && !"none".equals(settingAttribute))) {
            if (settingCounterId.equals("playerNumber")) {
				// This should not be coupled
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
                line2LCD.setText(settingPromptLine2.substring(0, bracketStart + 1) + displayInput + ">");
                settingCursorMax = 2;
            } else if (settingCounterId.equals("playerFoul")) {
				// This should not be coupled
                inputBuffer.setLength(0);
                inputBuffer.append(number);
                line2LCD.setText(settingPromptLine2.substring(0, bracketStart + 1) + number + ">");
                settingCursorMax = 1;
            } else {
                int digitCount = format.replaceAll("[^N]", "").length();
                if (inputBuffer.length() == 0) {
                    inputBuffer.append(" ".repeat(digitCount - 1)).append(number);
                } else if (inputBuffer.length() < digitCount) {
                    inputBuffer.append(number);
                } else {
                    inputBuffer.deleteCharAt(0).append(number);
                }
                line2LCD.setText(settingPromptLine2.substring(0, bracketStart + 1) + inputBuffer.toString() + ">");
                settingCursorMax = digitCount;
            }
        } else if (!"none".equals(settingIndicatorId)) {
            if (inputBuffer.length() == 0) {
                    inputBuffer.append(number);
            } else {
                    inputBuffer.deleteCharAt(0).append(number);
            }
            line2LCD.setText(settingPromptLine2.substring(0, bracketStart + 1) + inputBuffer.toString() + ">");
            settingCursorMax = 1;
        }
    }
    
    private void handleEnterClick() {
        if (!settingMode || isInitialPrompt || inputBuffer.length() == 0 || settingPromptLine2 == null) {
            abortSetFunction();
            return;
        }
	int bracketStart = settingPromptLine2.indexOf('<');
        int bracketEnd = settingPromptLine2.indexOf('>');
        String format = settingPromptLine2.substring(bracketStart + 1, bracketEnd);
        if (!"none".equals(settingTimerId)) {
            ScoreTimer timer = (ScoreTimer) ruleEngine.getElement(settingTimerId);
            if ("none".equals(settingAttribute)) {
                long nanos = parseInput(format, inputBuffer.toString().trim());
                // This needs to be modified to deal with poorly formatted time values
                if (nanos >= timer.getMinValue() && nanos <= timer.getMaxValue()) {
                    timer.setValue(nanos);
                }
            } else if ("allowShift".equals(settingAttribute)) {
                // This does not return to the default value for entries > 1
                boolean newValue = inputBuffer.toString().trim().equals("1");
                timer.setAllowShift(newValue);
            } else if ("isUpCounting".equals(settingAttribute)) {
                // This does not return to the default value for entries > 1
                boolean newValue = inputBuffer.toString().trim().equals("1");
                timer.setIsUpCounting(newValue);
            }
            System.out.println("what happened?");
            settingTimerId = "none";
        } else if (!"none".equals(settingCounterId)) {
            ScoreCounter counter = (ScoreCounter) ruleEngine.getElement(settingCounterId);
            String input = inputBuffer.toString().trim();
            // Should be looking at settingAttribute here...
            if (settingCounterId.equals("playerNumber")) {
                // Should not be coupled like this
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
                // Should not be coupled like this
                int value = Integer.parseInt(input);
                counter.setCurrentValue(value);
                counter.setBlank(false);
            } else {
                int value = Integer.parseInt(input);
                counter.setCurrentValue(value);
                counter.setBlank(false);
            }
            settingCounterId = "none";
        } else if (!"none".equals(settingIndicatorId)) {
            ScoreIndicator indicator = (ScoreIndicator) ruleEngine.getElement(settingIndicatorId);
            if ("none".equals(settingAttribute)) {
                boolean newValue = inputBuffer.toString().trim().equals("1");
                indicator.setCurrentValue(newValue);
            }
        }
        settingMode = false;
        settingPromptLine2 = "none";
        settingCursorPos = 1;
        resetUI();
    }
    
    private void handleClearClick() {
        // This codes [CLEAR] to work as backspace -- which is not normal for an MP controller
        if (!settingMode) return;
        if (isInitialPrompt || inputBuffer.length() < 1 || settingCursorPos < 1) {
            abortSetFunction();
        } else {
            if (isInitialPrompt || inputBuffer.length() < 2 || settingCursorPos < 2) {
                isInitialPrompt = true;
            }
            settingCursorPos--;
            int bracketStart = settingPromptLine2.indexOf('<');
            int bracketEnd = settingPromptLine2.indexOf('>');
            String format = settingPromptLine2.substring(bracketStart + 1, bracketEnd);
            int digitCount = 0;
            for (int i = 0; i < format.length(); i++) {
                if (Character.isLetter(format.charAt(i))) {
                    digitCount++;
                }
            }
            inputBuffer.deleteCharAt(inputBuffer.length() - 1);
            while (inputBuffer.length() < digitCount) {
                inputBuffer.insert(0, !"none".equals(settingTimerId) && "none".equals(settingAttribute) ? "0" : " ");
            }
            StringBuilder display = new StringBuilder();
            display.append(settingPromptLine2.substring(0, bracketStart + 1));
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
    
    private void displayTimerPrompt(long nanos, String promptLine1, String promptLine2) {
        
        if (promptLine2 != null && !"".equals(promptLine2)) {
            // long nanos = timer.getCurrentValue();
            long totalSeconds = nanos / 1_000_000_000L;
            long tenths = (nanos % 1_000_000_000L) / 100_000_000L;
            long minutes = totalSeconds / 60;
            long seconds = totalSeconds % 60;

            StringBuilder display = new StringBuilder();
            int bracketStart = promptLine2.indexOf('<');
            int bracketEnd = promptLine2.indexOf('>');
            display.append(promptLine2.substring(0, bracketStart + 1));

            String format = promptLine2.substring(bracketStart + 1, bracketEnd);
            if (format.equals("MM:SS")) {
                display.append(String.format("%02d:%02d", minutes, seconds));
            } else if (format.equals("SS.X")) {
                display.append(String.format("%02d.%d", seconds, tenths));
            }
            display.append(">");
            line2LCD.setText(display.toString());
        }
        if (promptLine1 != null && !"".equals(promptLine1)) {
            line1LCD.setText(promptLine1);
        }
    }

    private void displayIntPrompt(int currentValue, String promptLine1, String promptLine2) {
		
        if (promptLine2 != null && !"".equals(promptLine2)) {
            StringBuilder display = new StringBuilder();
            int bracketStart = promptLine2.indexOf('<');
            int bracketEnd = promptLine2.indexOf('>');
            display.append(promptLine2.substring(0, bracketStart + 1));
            String format = promptLine2.substring(bracketStart + 1, bracketEnd);
            int digitCount = format.replaceAll("[^N]", "").length();
            String formatStr = "%0" + digitCount + "d";
            display.append(String.format(formatStr, currentValue));
            display.append(">");
            line2LCD.setText(display.toString());
        }
        if (promptLine1 != null && !"".equals(promptLine1)) {
            line1LCD.setText(promptLine1);
        }
    }

    private void abortSetFunction() {
        settingMode = false;
        settingTimerId = "none";
        settingCounterId = "none";
        settingIndicatorId = "none";
        settingPromptLine2 = "none";
        isInitialPrompt = false;
        inputBuffer.setLength(0);
        promptLine1 = "";
        resetUI();
    }

    private long parseInput(String format, String input) {
        long nanos = 0;
        input = input.replaceAll("\\s", "");
        if ("MM:SS".equals(format)) {
            String paddedInput = String.format("%4s", input).replace(' ', '0');
            int minutes = Integer.parseInt(paddedInput.substring(0, 2));
            int seconds = Integer.parseInt(paddedInput.substring(2, 4));
            nanos = (minutes * 60L + seconds) * 1_000_000_000L;
        } else if ("SS.X".equals(format)) {
            String paddedInput = String.format("%3s", input).replace(' ', '0');
            int seconds = Integer.parseInt(paddedInput.substring(0, 2));
            int tenths = Integer.parseInt(paddedInput.substring(2, 3));
            nanos = seconds * 1_000_000_000L + tenths * 100_000_000L;
        } else if ("NN".equals(format)) {
            return Long.parseLong(input);
        }
        return nanos;
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

        ScoreIndicator team1Poss = (ScoreIndicator) ruleEngine.getElement("team1Possession");
        ScoreIndicator team2Poss = (ScoreIndicator) ruleEngine.getElement("team2Possession");
        ScoreIndicator team1Bonus1 = (ScoreIndicator) ruleEngine.getElement("team1Bonus1");
        ScoreIndicator team1Bonus2 = (ScoreIndicator) ruleEngine.getElement("team1Bonus2");
        ScoreIndicator team2Bonus1 = (ScoreIndicator) ruleEngine.getElement("team2Bonus1");
        ScoreIndicator team2Bonus2 = (ScoreIndicator) ruleEngine.getElement("team2Bonus2");
        ScoreCounter playerNumber = (ScoreCounter) ruleEngine.getElement("playerNumber");
        ScoreCounter playerFoul = (ScoreCounter) ruleEngine.getElement("playerFoul");
        ScoreCounter team1Fouls = (ScoreCounter) ruleEngine.getElement("team1Fouls");
        ScoreCounter team2Fouls = (ScoreCounter) ruleEngine.getElement("team2Fouls");

        StringBuilder line4Text = new StringBuilder("   ");
        line4Text.append(team1Poss != null && team1Poss.getCurrentValue() ? "< " : "  ");
        line4Text.append(team1Bonus2 != null && team1Bonus2.getCurrentValue() ? "B " : "  ");
        line4Text.append(team1Bonus1 != null && team1Bonus1.getCurrentValue() ? "B " : "  ");
        line4Text.append("                               ");
        line4Text.append(team2Bonus1 != null && team2Bonus1.getCurrentValue() ? "B " : "  ");
        line4Text.append(team2Bonus2 != null && team2Bonus2.getCurrentValue() ? "B " : "  ");
        line4Text.append(team2Poss != null && team2Poss.getCurrentValue() ? "> " : "  ");
        line4Label.setText(line4Text.toString());

        StringBuilder line7Text = new StringBuilder("  00     0                           0     00");
        if (team1Fouls != null) {
            int value = team1Fouls.getCurrentValue();
            String foulsStr = (value < 10) ? " " + value : String.valueOf(value);
            line7Text.replace(2, 4, foulsStr);
        }
        if (team2Fouls != null) {
            int value = team2Fouls.getCurrentValue();
            String foulsStr = (value < 10) ? " " + value : String.valueOf(value);
            line7Text.replace(43, 45, foulsStr);
        }
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
            line7Text.replace(20, 22, numberStr);
        }
        if (playerFoul != null && !playerFoul.isBlank()) {
            line7Text.replace(27, 28, String.valueOf(playerFoul.getCurrentValue()));
        }
        line7Label.setText(line7Text.toString());

        if (!settingMode) {
            StringBuilder lcdLine2 = new StringBuilder("                ");
            lcdLine2.setCharAt(0, team1Poss != null && team1Poss.getCurrentValue() ? '<' : ' ');
            lcdLine2.setCharAt(1, team1Bonus1 != null && team1Bonus1.getCurrentValue() ?
                (team1Bonus2 != null && team1Bonus2.getCurrentValue() ? 'B' : 'b') : ' ');
            if (team1Fouls != null) {
                lcdLine2.replace(3, 5, String.format("%02d", team1Fouls.getCurrentValue()));
            }
            if (playerNumber != null) {
                lcdLine2.replace(6, 8, playerNumber.isBlank() ? "00" : String.format("%02d", playerNumber.getCurrentValue()));
            }
            lcdLine2.setCharAt(9, playerFoul != null && !playerFoul.isBlank() ? String.valueOf(playerFoul.getCurrentValue()).charAt(0) : '0');
            if (team2Fouls != null) {
                lcdLine2.replace(11, 13, String.format("%02d", team2Fouls.getCurrentValue()));
            }
            lcdLine2.setCharAt(14, team2Bonus1 != null && team2Bonus1.getCurrentValue() ?
                (team2Bonus2 != null && team2Bonus2.getCurrentValue() ? 'B' : 'b') : ' ');
            lcdLine2.setCharAt(15, team2Poss != null && team2Poss.getCurrentValue() ? '>' : ' ');
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
                JsonArray targets = config.has("mainTargets") ? config.getAsJsonArray("mainTargets") : null;
                for (JsonElement targetElem : targets) {
                    String target = targetElem.getAsString();
                    ScoreElement element = target != null ? ruleEngine.getElement(target) : null;
                    boolean disable = evaluateCondition(condition, element);
                    button.setDisable(disable);
                }
            }
        }
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
                String attr = conditionParts[0].trim().replace("mainTargets.", "");
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
            settingTimerId = "none";
            settingCounterId = "none";
            settingIndicatorId = "none";
            settingAttribute = "none";
            settingPromptLine2 = "none";
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