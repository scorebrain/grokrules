package com.scorebrain.grokrules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.util.Duration;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

public class ScoreboardController implements Initializable {

    //private RuleEngine ruleEngine = new RuleEngine("grokruleset.json");
    private RuleEngine ruleEngine = new RuleEngine("BasketballRules00.json");
    //private List<String> timerIds;
    // private int currentTimerIndex = 0;
    private StringBuilder inputBuffer = new StringBuilder();
    private Timeline uiTimer;
    // private Timeline hornTimeline;
    private boolean buttonAlreadyHandled = false;
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
    private Map<String, Integer> toggleStates = new HashMap<>();
    private Map<String, Timeline> holdTimers = new HashMap<>();
    private Map<String, Boolean> isHeld = new HashMap<>();
    private Map<String, Timeline> indicatorTimelines = new HashMap<>();
    private Parent root;
    private boolean isInitialPrompt = false;
    private String promptLine1 = "none";
    private StringBuilder[] textScoreboardLineStrings;
    private Text[] textScoreboardLineText;

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

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            setupScoreboard();
            resetUI();
            startUITimer();
        } catch (Exception e) {
            System.err.println("Exception in initialize:");
            e.printStackTrace();
        }
    }

    private void setupScoreboard() {
        textScoreboard.getChildren().clear();
        textScoreboardLineStrings = new StringBuilder[12];
        textScoreboardLineStrings[0] = new StringBuilder("0123456789A123456789B123456789C123456789D123456789E123456789F123456789G123456789H");
        textScoreboardLineStrings[1] = new StringBuilder(" PLYR FLS PTS   G U E S T                    H O M E    PLYR FLS PTS");
        textScoreboardLineStrings[2] = new StringBuilder(" .##  ##  ##      SCORE        >##:##<        SCORE     .##  ##  ##");
        textScoreboardLineStrings[3] = new StringBuilder(" .##  ##  ##       ###                         ###      .##  ##  ##");
        textScoreboardLineStrings[4] = new StringBuilder(" .##  ##  ##      < B B       PERIOD #        B B >     .##  ##  ##");
        textScoreboardLineStrings[5] = new StringBuilder(" .##  ##  ##                                            .##  ##  ##");
        textScoreboardLineStrings[6] = new StringBuilder(" .##  ##  ##   FOULS TOL    PLAYER - FOUL    TOL FOULS  .##  ##  ##");
        textScoreboardLineStrings[7] = new StringBuilder(" .##  ##  ##    ##    #        ##     #       #   ##    .##  ##  ##");
        textScoreboardLineStrings[8] = new StringBuilder(" .##  ##  ##                                            .##  ##  ##");
        textScoreboardLineStrings[9] = new StringBuilder(" .##  ##  ##     >VL<     SHOT TIMER: >##<     >VR<     .##  ##  ##");
        textScoreboardLineStrings[10] = new StringBuilder(" .##  ##  ##                                            .##  ##  ##");
        textScoreboardLineStrings[11] = new StringBuilder(" .##  ##  ##         [=== BACKBOARD LIGHTS ===]         .##  ##  ##");
        textScoreboardLineText = new Text[12];
        for (int i = 1; i<12; i++) {
            textScoreboardLineText[i] = new Text(textScoreboardLineStrings[i].toString());
            textScoreboardLineText[i].setId("textScoreboard");
            textScoreboard.getChildren().add(textScoreboardLineText[i]);
        }
    }
    
    public void setRoot(Parent root) {
        this.root = root;
    }

    public void configureButtons() {
        JsonObject controllerConfig = ruleEngine.getControllerConfig();
        if (controllerConfig != null) {
            if (controllerConfig.has("buttons")) {
                JsonArray buttons = controllerConfig.getAsJsonArray("buttons");
                for (JsonElement btn : buttons) {
                    JsonObject btnConfig = btn.getAsJsonObject();
                    String fxId = btnConfig.get("fxId").getAsString();
                    buttonConfigs.put(fxId, btnConfig);
                    Button button = getButtonByFxId(fxId);
                    if (button != null) {
                        // Checking buttonE7 and buttonE8 explicitly is bad coupling.  Fix this later!!
                        if (fxId.equals("buttonE8") || fxId.equals("buttonE7")) {
                            button.setMinHeight(55);
                            button.setMaxHeight(55);
                            button.setPrefHeight(55);
                            VBox content = new VBox(2);
                            content.setAlignment(Pos.CENTER);
                            Label teamLabel = new Label("TEAM");
                            teamLabel.getStyleClass().add("basketball-smaller-text");
                            teamLabel.setStyle("-fx-font-size: 7.5");
                            Label foulsLabel = new Label("FOULS");
                            foulsLabel.getStyleClass().add("basketball-smaller-text");
                            foulsLabel.setStyle("-fx-font-size: 7.5");
                            Rectangle blueLine = new Rectangle(38, 2, Color.web("#011e41"));
                            Label pointsLabel = new Label("POINTS");
                            pointsLabel.getStyleClass().add("gray-small-text");
                            pointsLabel.setStyle("-fx-font-size: 6.5");
                            Rectangle grayLine = new Rectangle(29, 1, Color.web("#555555"));
                            Label wonLabel = new Label("WON");
                            wonLabel.getStyleClass().add("gray-small-text");
                            wonLabel.setStyle("-fx-font-size: 6.5");
                            content.getChildren().addAll(teamLabel, foulsLabel, blueLine, pointsLabel, grayLine, wonLabel);
                            button.setGraphic(content);
                            button.setText("");
                        } else {
                            button.setText(btnConfig.get("label").getAsString());
                            if (fxId.equals("buttonB2") || fxId.equals("buttonB3") || fxId.equals("buttonB4")
                                    || fxId.equals("buttonC2") || fxId.equals("buttonC3") || fxId.equals("buttonC4")
                                    || fxId.equals("buttonD2") || fxId.equals("buttonD3") || fxId.equals("buttonD4")
                                    || fxId.equals("buttonE3")) {
                                button.setStyle("-fx-font-size: 16");
                            }
                        }
                        //button.getStyleClass().removeAll("button");
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

    private void startHoldTimer(String fxId) {
        JsonObject config = buttonConfigs.get(fxId);
        if (config.has("alt1")) {
            JsonObject alt1 = config.getAsJsonObject("alt1");
            int duration = alt1.get("alt1Duration").getAsInt();
            Timeline timer = new Timeline(new KeyFrame(Duration.millis(duration), e -> {
                isHeld.put(fxId, true);
                handleButtonEvent(fxId);
            }));
            timer.setCycleCount(1);
            holdTimers.put(fxId, timer);
            timer.play();
        }
        handleButtonPress(fxId);
    }
    
    private void handleButtonPress(String fxId) {
        JsonObject config = buttonConfigs.get(fxId);
        String action = config.has("mainAction") ? config.get("mainAction").getAsString() : "none";
        JsonArray targets = config.has("mainTargets") ? config.getAsJsonArray("mainTargets") : null;
        if (settingMode && ("setCurrentValue".equals(action) || "setAttributeValue".equals(action) 
                || "increment".equals(action) || "decrement".equals(action) || "toggle".equals(action))) {
            abortSetFunction();
            buttonAlreadyHandled = true;
        }
    }
    
    private void handleButtonRelease(String fxId) {
        if (!buttonAlreadyHandled) {
            handleButtonEvent(fxId);
        }
        buttonAlreadyHandled = false;
    }

    private void handleButtonEvent(String fxId) {
        buttonAlreadyHandled = true;
        Timeline timeline = holdTimers.get(fxId);
        if (timeline != null) {
            timeline.stop();
            holdTimers.remove(fxId);
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
                ScoreElement element = target != null ? ruleEngine.getGameElement(target) : null;
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
                        startIndicatorAnimation(indicator);
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
                            ScoreIndicator indicatorToToggle = (ScoreIndicator) ruleEngine.getGameElement(stateTarget);
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
            } else if ("SS".equals(format)) {
                if (inputBuffer.length() < 2) {
                    inputBuffer.append(number);
                } else {
                    inputBuffer.deleteCharAt(0).append(number);
                }
                String input = inputBuffer.toString();
                while (input.length() < 2) {
                    input = "0" + input;
                }
                //String displayInput = input.substring(0, 2) + "." + input.substring(2, 3);
                line2LCD.setText(settingPromptLine2.substring(0, bracketStart + 1) + input + ">");
                settingCursorMax = 2;
            }
        } else if (!"none".equals(settingCounterId) || (!"none".equals(settingTimerId) && !"none".equals(settingAttribute)) || (!"none".equals(settingIndicatorId) && !"none".equals(settingAttribute))) {
            int digitCount = format.replaceAll("[^N]", "").length();
            settingCursorMax = digitCount;
            if (inputBuffer.length() == 0) {
                inputBuffer.append(" ".repeat(digitCount - 1)).append(number);
            } else if (inputBuffer.length() < digitCount) {
                inputBuffer.append(number);
                System.out.println("append number");
            } else {
                boolean zeroCluster = false;
                if (!"none".equals(settingCounterId)) {
                    ScoreCounter counter = (ScoreCounter) ruleEngine.getGameElement(settingCounterId);
                    zeroCluster = counter.hasLeadingZeroTricks();
                }
                String input = inputBuffer.toString();
                if (zeroCluster && Integer.parseInt(input.trim()) == 0 && input.charAt(0) == '0') {
                    inputBuffer.setLength(0);
                    inputBuffer.append(" ".repeat(digitCount - 1)).append(number);
                } else {
                    inputBuffer.deleteCharAt(0).append(number);
                }
            }
            line2LCD.setText(settingPromptLine2.substring(0, bracketStart + 1) + inputBuffer.toString() + ">");
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
        if (!settingMode || isInitialPrompt || inputBuffer.length() == 0 || "none".equals(settingPromptLine2)) {
            abortSetFunction();
            return;
        }
	int bracketStart = settingPromptLine2.indexOf('<');
        int bracketEnd = settingPromptLine2.indexOf('>');
        String format = settingPromptLine2.substring(bracketStart + 1, bracketEnd);
        if (!"none".equals(settingTimerId)) {
            ScoreTimer timer = (ScoreTimer) ruleEngine.getGameElement(settingTimerId);
            timer.setIsBlank(false);
            if ("none".equals(settingAttribute)) {
                //long nanos = parseInput(format, inputBuffer.toString().trim());
                // This needs to be modified to deal with poorly formatted time values
                System.out.println("format = " + format + "   input buffer = " + inputBuffer);
                int hours = 0;
                int minutes = 0;
                int seconds = 0;
                int millis = 0;
                if ("MM:SS".equals(format)) {
                    if (inputBuffer.length() == 1 || inputBuffer.length() == 2) {
                        seconds = Integer.parseInt(inputBuffer.toString().trim());
                    } else if (inputBuffer.length() == 3) {
                        seconds = Integer.parseInt(inputBuffer.substring(1,3).toString());
                        minutes = Integer.parseInt(inputBuffer.substring(0,1).toString().trim());
                    } else if (inputBuffer.length() == 4) {
                        seconds = Integer.parseInt(inputBuffer.substring(2,4).toString());
                        minutes = Integer.parseInt(inputBuffer.substring(0,2).toString().trim());
                    }
                } else if ("SS.X".equals(format)) {
                    if (inputBuffer.length() == 1) {
                        millis = Integer.parseInt(inputBuffer.toString().trim()) * 100;
                    } else if (inputBuffer.length() == 2) {
                        millis = Integer.parseInt(inputBuffer.substring(1,2).toString()) * 100;
                        seconds = Integer.parseInt(inputBuffer.substring(0,1).toString().trim());
                    } else if (inputBuffer.length() == 3) {
                        millis = Integer.parseInt(inputBuffer.substring(2,3).toString()) * 100;
                        seconds = Integer.parseInt(inputBuffer.substring(0,2).toString().trim());
                    }
                } else if ("SS".equals(format)) {
                    seconds = Integer.parseInt(inputBuffer.toString().trim());
                }
                System.out.println("Minutes: " + minutes + "  Seconds: " + seconds + "  Millis: " + millis);
                timer.setHoursMinutesSecondsMillis(hours, minutes, seconds, millis);
                //if (nanos >= timer.getMinValue() && nanos <= timer.getMaxValue()) {
                //    timer.setValue(nanos);
                //}
            } else if ("allowShift".equals(settingAttribute)) {
                int checker = Integer.parseInt(inputBuffer.toString().trim());
                if (checker == 0 || checker == 1) {
                    timer.setAllowShift(checker == 1);
                }
            } else if ("isUpCounting".equals(settingAttribute)) {
                int checker = Integer.parseInt(inputBuffer.toString().trim());
                if (checker == 0 || checker == 1) {
                    timer.setIsUpCounting(checker == 1);
                }
            }
            settingTimerId = "none";
        } else if (!"none".equals(settingCounterId)) {
            ScoreCounter counter = (ScoreCounter) ruleEngine.getGameElement(settingCounterId);
            String input = inputBuffer.toString().trim();
            if (!"".equals(input)) {
                int newValue = Integer.parseInt(input);
                counter.setCurrentValue(newValue);
                // Could try to check the results of setCurrentValue before allowing the changes below...
                counter.setIsBlank(false);
                if (counter.hasLeadingZeroTricks()) {
                    if (input.length() > 1 && input.charAt(0) == '0') {
                        counter.setShowLeadingZero(true);
                    } else {
                        counter.setShowLeadingZero(false);
                    }
                }
            } else {
                System.out.println("User input is blank.");
            }
            settingCounterId = "none";
        } else if (!"none".equals(settingIndicatorId)) {
            ScoreIndicator indicator = (ScoreIndicator) ruleEngine.getGameElement(settingIndicatorId);
            indicator.setIsBlank(false);
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
            } else if (format.equals("SS")) {
                display.append(String.format("%02d", seconds));
            }
            display.append(">");
            line2LCD.setText(display.toString());
        }
        if (!"none".equals(promptLine1)) {
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
        if (!"none".equals(promptLine1)) {
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
        promptLine1 = "none";
        resetUI();
    }
/*
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
*/
    private void updateUI() {
        ScoreTimer linkedTimer;
        ScoreCounter linkedCounter;
        ScoreIndicator linkedIndicator;
        Boolean tempIndicator;
        JsonArray gameLinks;
        String linkId;
        StringBuilder lcdLine1 = new StringBuilder("                ");
        StringBuilder lcdLine2 = new StringBuilder("                ");
        
        ScoreTimer mainTimer = (ScoreTimer) ruleEngine.getDisplayElement("mainTimer");
        gameLinks = mainTimer.getGameLinks();
        linkId = gameLinks.get(0).getAsString();
        linkedTimer = (ScoreTimer) ruleEngine.getGameElement(linkId);
        linkedTimer.checkThresholds();
        mainTimerRunningLight.setStyle(linkedTimer.isRunning() ?
            "-fx-fill: radial-gradient(center 50% 50%, radius 50%, greenyellow, forestgreen);" :
            "-fx-fill: radial-gradient(center 50% 50%, radius 50%, darkred, black);");
        mainTimer.synchronize(linkedTimer);
        
        String timerText;
        if (mainTimer.getVisibility()) {
            timerText = mainTimer.getDisplayValue();
        } else {
            timerText = "     ";
        }
        lcdLine1.replace(4, 5, mainTimer != null ? mainTimer.getIsUpCounting() ? "U" : "D" : "D");
        StringBuilder lcdTimer = new StringBuilder(mainTimer.getDisplayValue());
        if (lcdTimer.charAt(0) == ' ') {
            lcdTimer.setCharAt(0, '0');
        }
        lcdLine1.replace(5, 10, mainTimer != null ? lcdTimer.toString() : "MM:SS");
        textScoreboardLineStrings[mainTimer.getTextScoreboardLine()].replace(mainTimer.getTextScoreboardStartPos(), mainTimer.getTextScoreboardEndPos(), timerText);
        mainTimer.checkThresholds();
        double currentSeconds = mainTimer.getCurrentValue() / 1_000_000_000.0;
        boolean shouldFlash = mainTimer.isRunning() && currentSeconds < mainTimer.getFlashZoneThreshold() && mainTimer.getFlashZoneThreshold() >= 0;
        if (shouldFlash && !isFlashing) {
            startFlashAnimation(mainTimer);
            isFlashing = true;
        } else if (!shouldFlash && isFlashing) {
            stopFlashAnimation(mainTimer);
            isFlashing = false;
        }
        
        ScoreIndicator mainHornLeft = (ScoreIndicator) ruleEngine.getDisplayElement("mainLeft_Horn");
        gameLinks = mainHornLeft.getGameLinks();
        tempIndicator = false;
        for (int i = 0; i < gameLinks.size(); i++) {
                linkId = gameLinks.get(i).getAsString();
                linkedIndicator = (ScoreIndicator) ruleEngine.getGameElement(linkId);
                if (linkedIndicator.getCurrentValue() && !linkedIndicator.isBlank()) {
                    tempIndicator = true;
                    startIndicatorAnimation(linkedIndicator);
                }
        }
        if (tempIndicator && !mainHornLeft.getCurrentValue()) {
            mainHornLeft.setCurrentValue(true);
            startIndicatorAnimation(mainHornLeft);
        }
        textScoreboardLineStrings[2].replace(31, 32, mainHornLeft.getCurrentValue() && !mainHornLeft.isBlank() ? ">" : " ");
        
        ScoreIndicator mainHornRight = (ScoreIndicator) ruleEngine.getDisplayElement("mainRight_Horn");
        gameLinks = mainHornRight.getGameLinks();
        tempIndicator = false;
        for (int i = 0; i < gameLinks.size(); i++) {
                linkId = gameLinks.get(i).getAsString();
                linkedIndicator = (ScoreIndicator) ruleEngine.getGameElement(linkId);
                if (linkedIndicator.getCurrentValue() && !linkedIndicator.isBlank()) {
                    tempIndicator = true;
                    startIndicatorAnimation(linkedIndicator);
                }
        }
        if (tempIndicator && !mainHornRight.getCurrentValue()) {
            mainHornRight.setCurrentValue(true);
            startIndicatorAnimation(mainHornRight);
        }
        textScoreboardLineStrings[2].replace(37, 38, mainHornRight.getCurrentValue() && !mainHornRight.isBlank() ? "<" : " ");

        ScoreCounter team1Points = (ScoreCounter) ruleEngine.getDisplayElement("team1Points");
        gameLinks = team1Points.getGameLinks();
        linkId = gameLinks.get(0).getAsString();
        linkedCounter = (ScoreCounter) ruleEngine.getGameElement(linkId);
        textScoreboardLineStrings[3].replace(19, 22, linkedCounter != null ? String.format("%3d", linkedCounter.getCurrentValue()) : "###");
        lcdLine1.replace(0, 3, linkedCounter != null ? String.format("%03d", linkedCounter.getCurrentValue()) : "###");

        ScoreCounter team2Points = (ScoreCounter) ruleEngine.getDisplayElement("team2Points");
        gameLinks = team2Points.getGameLinks();
        linkId = gameLinks.get(0).getAsString();
        linkedCounter = (ScoreCounter) ruleEngine.getGameElement(linkId);
        textScoreboardLineStrings[3].replace(47, 50, linkedCounter != null ? String.format("%3d", linkedCounter.getCurrentValue()) : "###");
        lcdLine1.replace(13, 16, linkedCounter != null ? String.format("%03d", linkedCounter.getCurrentValue()) : "###");
        
        ScoreIndicator team1Poss = (ScoreIndicator) ruleEngine.getDisplayElement("team1Possession");
        gameLinks = team1Poss.getGameLinks();
        tempIndicator = false;
        for (int i = 0; i < gameLinks.size(); i++) {
                linkId = gameLinks.get(i).getAsString();
                linkedIndicator = (ScoreIndicator) ruleEngine.getGameElement(linkId);
                if (linkedIndicator.getCurrentValue() && !linkedIndicator.isBlank()) {
                    tempIndicator = true;
                    //startIndicatorAnimation(linkedIndicator);
                }
        }
        if (tempIndicator && !team1Poss.getCurrentValue()) {
            team1Poss.setCurrentValue(true);
            //startIndicatorAnimation(team1Poss);
        } else {
            team1Poss.setCurrentValue(tempIndicator);
        }
        textScoreboardLineStrings[4].replace(18, 19, team1Poss != null && team1Poss.getCurrentValue() ? "<" : " ");
        lcdLine2.setCharAt(0, team1Poss != null && team1Poss.getCurrentValue() ? '<' : ' ');
        
        ScoreIndicator team1Bonus2 = (ScoreIndicator) ruleEngine.getDisplayElement("team1Bonus2");
        gameLinks = team1Bonus2.getGameLinks();
        // We have to know that this Indicator is linked to a Counter (not like a Horn linked to manual + auto Horns)
        linkId = gameLinks.get(0).getAsString();
        linkedCounter = (ScoreCounter) ruleEngine.getGameElement(linkId);
        tempIndicator = linkedCounter.getCurrentValue() > 1;
        if (tempIndicator && !team1Bonus2.getCurrentValue()) {
            team1Bonus2.setCurrentValue(true);
            //startIndicatorAnimation(team1Bonus2);
        } else {
            team1Bonus2.setCurrentValue(tempIndicator);
        }
        textScoreboardLineStrings[4].replace(20, 21, team1Bonus2 != null && team1Bonus2.getCurrentValue() ? "B" : " ");
        
        
        ScoreIndicator team1Bonus1 = (ScoreIndicator) ruleEngine.getDisplayElement("team1Bonus1");
        gameLinks = team1Bonus1.getGameLinks();
        // We have to know that this Indicator is linked to a Counter (not like a Horn linked to manual + auto Horns)
        linkId = gameLinks.get(0).getAsString();
        linkedCounter = (ScoreCounter) ruleEngine.getGameElement(linkId);
        tempIndicator = linkedCounter.getCurrentValue() > 0;
        if (tempIndicator && !team1Bonus1.getCurrentValue()) {
            team1Bonus1.setCurrentValue(true);
            //startIndicatorAnimation(team1Bonus1);
        } else {
            team1Bonus1.setCurrentValue(tempIndicator);
        }
        textScoreboardLineStrings[4].replace(22, 23, team1Bonus1 != null && team1Bonus1.getCurrentValue() ? "B" : " ");
        lcdLine2.setCharAt(1, team1Bonus1 != null && team1Bonus1.getCurrentValue() ?
                (team1Bonus2 != null && team1Bonus2.getCurrentValue() ? 'B' : 'b') : ' ');

        ScoreCounter periodCount = (ScoreCounter) ruleEngine.getDisplayElement("periodCount");
        gameLinks = periodCount.getGameLinks();
        linkId = gameLinks.get(0).getAsString();
        linkedCounter = (ScoreCounter) ruleEngine.getGameElement(linkId);
        if (linkedCounter != null) {
            textScoreboardLineStrings[4].replace(37, 38, String.format("%1d", linkedCounter.getCurrentValue()));
        } else {
            textScoreboardLineStrings[4].replace(37, 38, "#");
        }
        lcdLine1.replace(11, 12, linkedCounter != null ? String.format("%1d", linkedCounter.getCurrentValue()) : "#");
        
        ScoreIndicator team2Bonus1 = (ScoreIndicator) ruleEngine.getDisplayElement("team2Bonus1");
        gameLinks = team2Bonus1.getGameLinks();
        // We have to know that this Indicator is linked to a Counter (not like a Horn linked to manual + auto Horns)
        linkId = gameLinks.get(0).getAsString();
        linkedCounter = (ScoreCounter) ruleEngine.getGameElement(linkId);
        tempIndicator = linkedCounter.getCurrentValue() > 0;
        if (tempIndicator && !team2Bonus1.getCurrentValue()) {
            team2Bonus1.setCurrentValue(true);
            // startIndicatorAnimation(team2Bonus1);
        } else {
            team2Bonus1.setCurrentValue(tempIndicator);
        }
        textScoreboardLineStrings[4].replace(46, 47, team2Bonus1 != null && team2Bonus1.getCurrentValue() ? "B" : " ");
        
        ScoreIndicator team2Bonus2 = (ScoreIndicator) ruleEngine.getDisplayElement("team2Bonus2");
        gameLinks = team2Bonus2.getGameLinks();
        // We have to know that this Indicator is linked to a Counter (not like a Horn linked to manual + auto Horns)
        linkId = gameLinks.get(0).getAsString();
        linkedCounter = (ScoreCounter) ruleEngine.getGameElement(linkId);
        tempIndicator = linkedCounter.getCurrentValue() > 1;
        if (tempIndicator && !team2Bonus2.getCurrentValue()) {
            team2Bonus2.setCurrentValue(true);
            //startIndicatorAnimation(team2Bonus2);
        } else {
            team2Bonus2.setCurrentValue(tempIndicator);
        }
        textScoreboardLineStrings[4].replace(48, 49, team2Bonus2 != null && team2Bonus2.getCurrentValue() ? "B" : " ");
        lcdLine2.setCharAt(14, team2Bonus1 != null && team2Bonus1.getCurrentValue() ?
                (team2Bonus2 != null && team2Bonus2.getCurrentValue() ? 'B' : 'b') : ' ');
        
        ScoreIndicator team2Poss = (ScoreIndicator) ruleEngine.getDisplayElement("team2Possession");
        gameLinks = team2Poss.getGameLinks();
        tempIndicator = false;
        for (int i = 0; i < gameLinks.size(); i++) {
                linkId = gameLinks.get(i).getAsString();
                linkedIndicator = (ScoreIndicator) ruleEngine.getGameElement(linkId);
                if (linkedIndicator.getCurrentValue() && !linkedIndicator.isBlank()) {
                    tempIndicator = true;
                    //startIndicatorAnimation(linkedIndicator);
                }
        }
        if (tempIndicator && !team2Poss.getCurrentValue()) {
            team2Poss.setCurrentValue(true);
            //startIndicatorAnimation(team2Poss);
        } else {
            team2Poss.setCurrentValue(tempIndicator);
        }
        textScoreboardLineStrings[4].replace(50, 51, team2Poss != null && team2Poss.getCurrentValue() ? ">" : " ");
        lcdLine2.setCharAt(15, team2Poss != null && team2Poss.getCurrentValue() ? '>' : ' ');
        
        ScoreCounter team1Fouls = (ScoreCounter) ruleEngine.getDisplayElement("team1Fouls");
        gameLinks = team1Fouls.getGameLinks();
        linkId = gameLinks.get(0).getAsString();
        linkedCounter = (ScoreCounter) ruleEngine.getGameElement(linkId);
        textScoreboardLineStrings[7].replace(16, 18, linkedCounter != null ? String.format("%2d", linkedCounter.getCurrentValue()) : "##");
        lcdLine2.replace(3, 5, linkedCounter != null ? String.format("%02d", linkedCounter.getCurrentValue()) : "##");
        
        ScoreCounter team1TimeOuts = (ScoreCounter) ruleEngine.getDisplayElement("team1TimeOuts");
        gameLinks = team1TimeOuts.getGameLinks();
        linkId = gameLinks.get(0).getAsString();
        linkedCounter = (ScoreCounter) ruleEngine.getGameElement(linkId);
        textScoreboardLineStrings[7].replace(22, 23, linkedCounter != null ? String.format("%1d", linkedCounter.getCurrentValue()) : "#");
        
        ScoreCounter playerNumber = (ScoreCounter) ruleEngine.getDisplayElement("playerNumber");
        gameLinks = playerNumber.getGameLinks();
        linkId = gameLinks.get(0).getAsString();
        linkedCounter = (ScoreCounter) ruleEngine.getGameElement(linkId);
        if (linkedCounter != null) {
            if (linkedCounter.isBlank()) {
                textScoreboardLineStrings[7].replace(31, 33, "  ");
            } else {
                String numberStr;
                int value = linkedCounter.getCurrentValue();
                if (value >= 10) {
                    numberStr = String.format("%2d", value);
                } else if (linkedCounter.showLeadingZero()) {
                    numberStr = String.format("0%d", value);
                    } else {
                        numberStr = String.format(" %d", value);
                    }
                textScoreboardLineStrings[7].replace(31, 33, numberStr);
            }
        }
        lcdLine2.replace(6, 8, linkedCounter != null ? String.format("%02d", linkedCounter.getCurrentValue()) : "##");
        
        
        ScoreCounter playerFoul = (ScoreCounter) ruleEngine.getDisplayElement("playerFoul");
        gameLinks = playerFoul.getGameLinks();
        linkId = gameLinks.get(0).getAsString();
        linkedCounter = (ScoreCounter) ruleEngine.getGameElement(linkId);
        if (linkedCounter != null) {
            if (linkedCounter.isBlank()) {
                textScoreboardLineStrings[7].replace(38, 39, " ");
            } else {
                textScoreboardLineStrings[7].replace(38, 39, String.format("%1d", linkedCounter.getCurrentValue()));
            }
        }
        lcdLine2.setCharAt(9, linkedCounter != null ? String.valueOf(linkedCounter.getCurrentValue()).charAt(0) : '#');
        
        ScoreCounter team2TimeOuts = (ScoreCounter) ruleEngine.getDisplayElement("team2TimeOuts");
        gameLinks = team2TimeOuts.getGameLinks();
        linkId = gameLinks.get(0).getAsString();
        linkedCounter = (ScoreCounter) ruleEngine.getGameElement(linkId);
        textScoreboardLineStrings[7].replace(46, 47, linkedCounter != null ? String.format("%1d", linkedCounter.getCurrentValue()) : "#");
        
        ScoreCounter team2Fouls = (ScoreCounter) ruleEngine.getDisplayElement("team2Fouls");
        gameLinks = team2Fouls.getGameLinks();
        linkId = gameLinks.get(0).getAsString();
        linkedCounter = (ScoreCounter) ruleEngine.getGameElement(linkId);
        textScoreboardLineStrings[7].replace(50, 52, linkedCounter != null ? String.format("%2d", linkedCounter.getCurrentValue()) : "##");
        lcdLine2.replace(11, 13, linkedCounter != null ? String.format("%02d", linkedCounter.getCurrentValue()) : "##");

        ScoreTimer shotTimer = (ScoreTimer) ruleEngine.getDisplayElement("shotTimer");
        gameLinks = shotTimer.getGameLinks();
        linkId = gameLinks.get(0).getAsString();
        linkedTimer = (ScoreTimer) ruleEngine.getGameElement(linkId);
        textScoreboardLineStrings[9].replace(39, 41, linkedTimer != null? linkedTimer.getDisplayValue() : "##");

        ScoreIndicator visualHornLeft = (ScoreIndicator) ruleEngine.getDisplayElement("visualLeft_Horn");
        gameLinks = visualHornLeft.getGameLinks();
        tempIndicator = false;
        for (int i = 0; i < gameLinks.size(); i++) {
                linkId = gameLinks.get(i).getAsString();
                linkedIndicator = (ScoreIndicator) ruleEngine.getGameElement(linkId);
                if (linkedIndicator.getCurrentValue() && !linkedIndicator.isBlank()) {
                    tempIndicator = true;
                    startIndicatorAnimation(linkedIndicator);
                }
        }
        if (tempIndicator && !visualHornLeft.getCurrentValue()) {
            visualHornLeft.setCurrentValue(true);
            startIndicatorAnimation(visualHornLeft);
        }
        textScoreboardLineStrings[9].replace(17, 21, visualHornLeft.getCurrentValue() && !visualHornLeft.isBlank() ? ">VL<" : "    ");
        
        ScoreIndicator visualHornRight = (ScoreIndicator) ruleEngine.getDisplayElement("visualRight_Horn");
        gameLinks = visualHornRight.getGameLinks();
        tempIndicator = false;
        for (int i = 0; i < gameLinks.size(); i++) {
                linkId = gameLinks.get(i).getAsString();
                linkedIndicator = (ScoreIndicator) ruleEngine.getGameElement(linkId);
                if (linkedIndicator.getCurrentValue() && !linkedIndicator.isBlank()) {
                    tempIndicator = true;
                    startIndicatorAnimation(linkedIndicator);
                }
        }
        if (tempIndicator && !visualHornRight.getCurrentValue()) {
            visualHornRight.setCurrentValue(true);
            startIndicatorAnimation(visualHornRight);
        }
        textScoreboardLineStrings[9].replace(47, 51, visualHornRight.getCurrentValue() && !visualHornRight.isBlank() ? ">VR<" : "    ");
        
        ScoreIndicator backboardLight = (ScoreIndicator) ruleEngine.getDisplayElement("backboardLight_Horn");
        gameLinks = backboardLight.getGameLinks();
        tempIndicator = false;
        for (int i = 0; i < gameLinks.size(); i++) {
                linkId = gameLinks.get(i).getAsString();
                linkedIndicator = (ScoreIndicator) ruleEngine.getGameElement(linkId);
                if (linkedIndicator.getCurrentValue() && !linkedIndicator.isBlank()) {
                    tempIndicator = true;
                    startIndicatorAnimation(linkedIndicator);
                }
        }
        if (tempIndicator && !backboardLight.getCurrentValue()) {
            backboardLight.setCurrentValue(true);
            startIndicatorAnimation(backboardLight);
        }
        textScoreboardLineStrings[11].replace(21, 47, backboardLight.getCurrentValue() && !backboardLight.isBlank() ? "[=== BACKBOARD LIGHTS ===]" : "                          ");
        
        ScoreIndicator shotTimerLeftHorn = (ScoreIndicator) ruleEngine.getDisplayElement("shotTimerLeft_Horn");
        gameLinks = shotTimerLeftHorn.getGameLinks();
        tempIndicator = false;
        for (int i = 0; i < gameLinks.size(); i++) {
                linkId = gameLinks.get(i).getAsString();
                linkedIndicator = (ScoreIndicator) ruleEngine.getGameElement(linkId);
                if (linkedIndicator.getCurrentValue() && !linkedIndicator.isBlank()) {
                    tempIndicator = true;
                    startIndicatorAnimation(linkedIndicator);
                }
        }
        if (tempIndicator && !shotTimerLeftHorn.getCurrentValue()) {
            shotTimerLeftHorn.setCurrentValue(true);
            startIndicatorAnimation(shotTimerLeftHorn);
        }
        textScoreboardLineStrings[9].replace(38, 39, shotTimerLeftHorn.getCurrentValue() && !shotTimerLeftHorn.isBlank() ? ">" : " ");
        ScoreIndicator shotTimerRightHorn = (ScoreIndicator) ruleEngine.getDisplayElement("shotTimerRight_Horn");
        gameLinks = shotTimerRightHorn.getGameLinks();
        tempIndicator = false;
        for (int i = 0; i < gameLinks.size(); i++) {
                linkId = gameLinks.get(i).getAsString();
                linkedIndicator = (ScoreIndicator) ruleEngine.getGameElement(linkId);
                if (linkedIndicator.getCurrentValue() && !linkedIndicator.isBlank()) {
                    tempIndicator = true;
                    startIndicatorAnimation(linkedIndicator);
                }
        }
        if (tempIndicator && !shotTimerRightHorn.getCurrentValue()) {
            shotTimerRightHorn.setCurrentValue(true);
            startIndicatorAnimation(shotTimerRightHorn);
        }
        textScoreboardLineStrings[9].replace(41, 42, shotTimerRightHorn.getCurrentValue() && !shotTimerRightHorn.isBlank() ? "<" : " ");
        
        for (int i=1; i < 12; i++) {
            textScoreboardLineText[i].setText(textScoreboardLineStrings[i].toString());
        }
        
        if (!settingMode) {
            line2LCD.setText(lcdLine2.toString());
            if ("none".equals(promptLine1)) {
                line1LCD.setText(lcdLine1.toString());
            }
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
                    ScoreElement element = target != null ? ruleEngine.getGameElement(target) : null;
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
            promptLine1 = "none";
            //ScoreTimer timer = (ScoreTimer) ruleEngine.getElement("periodTimer");
            //line1LCD.setText(getTimerDisplayText(timer));
            line1LCD.setText("");
            line3LCD.setText("");
            settingTimerId = "none";
            settingCounterId = "none";
            settingIndicatorId = "none";
            settingAttribute = "none";
            settingPromptLine2 = "none";
        }
    }

/*    private String getTimerDisplayText(ScoreTimer timer) {
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
    }*/

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
                new KeyFrame(Duration.millis(cumulativeTime), e -> timer.setVisibility(visible))
            );
            cumulativeTime += duration;
        }
        flashTimeline.getKeyFrames().add(
            new KeyFrame(Duration.millis(cumulativeTime), e -> timer.setVisibility(firstVisible))
        );
        flashTimeline.setCycleCount(Timeline.INDEFINITE);
        flashTimeline.play();
    }

    private void stopFlashAnimation(ScoreTimer timer) {
        if (flashTimeline != null) {
            flashTimeline.stop();
            flashTimeline = null;
        }
        timer.setVisibility(true);
    }
    
    private void startIndicatorAnimation(ScoreIndicator indicator) {
        if (indicator.getCurrentValue() && indicator.getPattern() != null && indicatorTimelines.get(indicator.getId()) == null) {
            System.out.println("startIndicatorAnimation -- made it inside if statement ... indicator = " + indicator.getId());
            String[] steps = indicator.getPattern().split(",");
            Timeline timer = new Timeline();
            double cumulativeTime = 0;
            for (String step : steps) {
                String[] parts = step.split(":");
                double duration = Double.parseDouble(parts[0]);
                boolean visible = Boolean.parseBoolean(parts[1]);
                timer.getKeyFrames().add(
                    new KeyFrame(Duration.millis(cumulativeTime), e -> {
                        indicator.setIsBlank(!visible);
                    })
                );
                cumulativeTime += duration;
            }
            timer.getKeyFrames().add(
                new KeyFrame(Duration.millis(cumulativeTime), e -> {
                    indicator.setIsBlank(true);
                })
            );
            timer.setOnFinished(e -> {
                indicator.setCurrentValue(false);
                indicatorTimelines.remove(indicator.getId());
                updateUI();
            });
            indicator.setIsBlank(false);
            timer.play();
            indicatorTimelines.put(indicator.getId(), timer);
        }
    }
}