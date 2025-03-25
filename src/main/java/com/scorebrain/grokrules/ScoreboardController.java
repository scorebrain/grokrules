package com.scorebrain.grokrules;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

public class ScoreboardController {
    
    private RuleEngine ruleEngine = new RuleEngine("grokruleset.json");
    private StringBuilder inputBuffer = new StringBuilder();
    private Timeline uiTimer;
    private boolean settingMode = false;
    private String initialSetValue = "";
    private String selectedTimerId = "timerOne"; // Track selection explicitly
    private Timeline flashTimeline = new Timeline(); // New: For flashing patterns
    private int currentFlashPattern = -1;            // New: Track active pattern

    @FXML
    private Label timerLabel;
    
    @FXML
    private Label lcdLine1;
    
    @FXML
    private Label lcdLine2;
    
    @FXML
    private Button buttonPlusOne;
    
    @FXML
    private Button buttonMinusOne;
    
    @FXML
    private Button buttonSet;
    
    @FXML
    private Button buttonStartStop;
    
    @FXML
    private Button buttonEnter;
    
    @FXML
    private Button buttonBackspace;
    
    @FXML
    private Circle runningIndicator;
    
    @FXML
    private Text hornSymbol;
    
    @FXML
    private Button buttonHorn;
    
    @FXML
    private Button buttonNextTimer;

    @FXML
    private void initialize() {
        resetUI();
        startUITimer();
    }

    @FXML
    private void handleNumberClick(ActionEvent event) {
        if (settingMode) {
            Button clickedButton = (Button) event.getSource();
            String number = clickedButton.getText();
            inputBuffer.append(number);
            updateLcdLine2();
        }
    }
    
    @FXML
    private void handleBackClick(ActionEvent event) {
        if (!settingMode) return;
        
        if (inputBuffer.length() == 0) {
            settingMode = false;
            resetUI();
        } else {
            inputBuffer.setLength(inputBuffer.length() - 1);
            updateLcdLine2();
        }
    }
    
    @FXML
    private void handlePlusOne(ActionEvent event) {
        ScoreTimer timer = getSelectedTimer();
        if (timer != null && timer.increment()) {
            updateUI();
        } else {
            lcdLine2.setText("Timer is running!   ");
        }
    }
    
    @FXML
    private void handleMinusOne(ActionEvent event) {
        ScoreTimer timer = getSelectedTimer();
        if (timer != null) {
            if (timer.isRunning()) {
                lcdLine2.setText("Timer is running!   ");
            } else if (!timer.decrement()) {
                lcdLine2.setText("Timer at zero!      ");
            } else {
                updateUI();
            }
        }
    }
    
    @FXML
    private void handleSet(ActionEvent event) {
        ScoreTimer timer = getSelectedTimer();
        if (timer == null || timer.isRunning()) {
            lcdLine2.setText("Timer is running!   ");
            return;
        }
        
        if (settingMode) {
            settingMode = false;
            resetUI();
        } else {
            settingMode = true;
            inputBuffer.setLength(0);
            initialSetValue = timer.getDisplayValue();
            lcdLine2.setText("ENTER TIME  " + initialSetValue);
            buttonPlusOne.setDisable(true);
            buttonMinusOne.setDisable(true);
        }
    }
    
    @FXML
    private void handleEnter(ActionEvent event) {
        if (!settingMode) return;
        
        ScoreTimer timer = getSelectedTimer();
        if (inputBuffer.length() > 0) {
            try {
                String input = inputBuffer.toString();
                String trimmedInput = input.length() > 4 ? input.substring(input.length() - 4) : input;
                String paddedInput = String.format("%4s", trimmedInput).replace(' ', '0');
                int minutes = Integer.parseInt(paddedInput.substring(0, 2));
                int seconds = Integer.parseInt(paddedInput.substring(2, 4));
                int totalSeconds = minutes * 60 + seconds;
                if (timer.set(totalSeconds)) {
                    updateUI();
                }
            } catch (NumberFormatException e) {
                lcdLine2.setText("Invalid input!     ");
            }
        }
        settingMode = false;
        buttonPlusOne.setDisable(false);
        buttonMinusOne.setDisable(false);
        resetUI();
    }
    
    @FXML
    private void handleStartStop(ActionEvent event) {
        ScoreTimer timer = getSelectedTimer();
        if (timer != null && timer.startstop()) {
            updateUI();
        }
    }
    
    @FXML
    private void handleHorn(ActionEvent event) {
        String hornId = getSelectedTimerId().equals("timerOne") ? "timerOneHorn" :
                        getSelectedTimerId().equals("timerTwo") ? "timerTwoHorn" : "timerThreeHorn";
        ScoreIndicator horn = (ScoreIndicator) ruleEngine.getElement(hornId);
        if (horn != null && !horn.getCurrentValue()) {
            horn.setCurrentValue(true, false);
            updateUI();
        }
    }
    
    @FXML
    private void handleNextTimer(ActionEvent event) {
        String[] timerIds = {"timerOne", "timerTwo", "timerThree"};
        String[] selectorIds = {"timerOneSelected", "timerTwoSelected", "timerThreeSelected"};
        for (int i = 0; i < timerIds.length; i++) {
            ScoreIndicator selector = (ScoreIndicator) ruleEngine.getElement(selectorIds[i]);
            if (selector != null && selector.getCurrentValue()) {
                selector.setCurrentValue(false, false);
                int nextIndex = (i + 1) % timerIds.length;
                ((ScoreIndicator) ruleEngine.getElement(selectorIds[nextIndex])).setCurrentValue(true, false);
                selectedTimerId = timerIds[nextIndex];
                updateUI();
                return;
            }
        }
    }
    
    private void startUITimer() {
        if (uiTimer != null) uiTimer.stop();
        uiTimer = new Timeline(new KeyFrame(Duration.seconds(0.1), e -> updateUI()));
        uiTimer.setCycleCount(Timeline.INDEFINITE);
        uiTimer.play();
    }
    
    private void resetUI() {
        ScoreTimer timer1 = (ScoreTimer) ruleEngine.getElement("timerOne");
        ScoreTimer timer2 = (ScoreTimer) ruleEngine.getElement("timerTwo");
        ScoreTimer timer3 = (ScoreTimer) ruleEngine.getElement("timerThree");
        String hornId = selectedTimerId.equals("timerOne") ? "timerOneHorn" :
                        selectedTimerId.equals("timerTwo") ? "timerTwoHorn" : "timerThreeHorn";
        ScoreIndicator horn = (ScoreIndicator) ruleEngine.getElement(hornId);
        String selected = selectedTimerId.equals("timerOne") ? "1" : 
                         selectedTimerId.equals("timerTwo") ? "2" : "3";
        lcdLine1.setText(String.format("%s %s %s %s", 
            timer1 != null ? timer1.getDisplayValue() : "0:00",
            timer2 != null ? timer2.getDisplayValue() : "0:00",
            timer3 != null ? timer3.getDisplayValue() : "0:00",
            selected) + (horn != null && horn.getCurrentValue() ? "*" : ""));
        lcdLine2.setText("                ");
        timerLabel.setText(getSelectedTimer() != null ? getSelectedTimer().getDisplayValue() : "00:00");
        buttonPlusOne.setDisable(false);
        buttonMinusOne.setDisable(false);
        flashTimeline.stop(); // Reset flashing
        timerLabel.setVisible(true);
        currentFlashPattern = -1;
        updateRunningIndicator();
        updateHornSymbol();
    }
    
    private void updateUI() {
        ScoreTimer timer1 = (ScoreTimer) ruleEngine.getElement("timerOne");
        ScoreTimer timer2 = (ScoreTimer) ruleEngine.getElement("timerTwo");
        ScoreTimer timer3 = (ScoreTimer) ruleEngine.getElement("timerThree");
        String hornId = selectedTimerId.equals("timerOne") ? "timerOneHorn" :
                        selectedTimerId.equals("timerTwo") ? "timerTwoHorn" : "timerThreeHorn";
        ScoreIndicator horn = (ScoreIndicator) ruleEngine.getElement(hornId);
        ScoreTimer selectedTimer = getSelectedTimer();
        String selected = selectedTimerId.equals("timerOne") ? "1" : 
                         selectedTimerId.equals("timerTwo") ? "2" : "3";
        
        // Flash Zone Logic
        boolean inFlashZone = selectedTimer != null && 
                             selectedTimer.isRunning() && 
                             selectedTimer.getCurrentSeconds() <= selectedTimer.getFlashZoneTrigger() && 
                             selectedTimer.getFlashPattern() > 0;
        String lcdText = String.format("%s %s %s %s", 
            timer1 != null ? timer1.getDisplayValue() : "0:00",
            timer2 != null ? timer2.getDisplayValue() : "0:00",
            timer3 != null ? timer3.getDisplayValue() : "0:00",
            selected);
        if (inFlashZone) {
            int pattern = selectedTimer.getFlashPattern();
            if (pattern != currentFlashPattern) {
                setupFlashPattern(pattern);
                currentFlashPattern = pattern;
            }
            if (flashTimeline.getStatus() != Timeline.Status.RUNNING) {
                flashTimeline.play();
                System.out.println("Call to flashTimeline.play()");
            }
            lcdText += " f"; // Indicate Flash Zone on LCD
        } else {
            flashTimeline.stop();
            timerLabel.setVisible(true); // Digits ON when not flashing
            currentFlashPattern = -1;
            if (lcdText.endsWith(" f")) {
                lcdText = lcdText.substring(0, lcdText.length() - 2); // Remove 'f' if present
            }
        }
        lcdText += (horn != null && horn.getCurrentValue() ? "*" : "");
        lcdLine1.setText(lcdText);
        
        timerLabel.setText(selectedTimer != null ? selectedTimer.getDisplayValue() : "00:00");
        if (settingMode) {
            updateLcdLine2();
        } else {
            lcdLine2.setText("                ");
        }
        updateRunningIndicator();
        updateHornSymbol();
    }
    
    private void updateLcdLine2() {
        if (inputBuffer.length() == 0) {
            lcdLine2.setText("ENTER TIME  " + initialSetValue);
        } else {
            String input = inputBuffer.toString();
            String displayInput = input.length() > 4 ? input.substring(input.length() - 4) : String.format("%4s", input).replace(' ', '0');
            String formatted = displayInput.substring(0, 2) + ":" + displayInput.substring(2);
            lcdLine2.setText("ENTER TIME " + padRight(formatted, 6));
        }
    }
    
    private void updateRunningIndicator() {
        ScoreTimer timer = getSelectedTimer();
        if (timer != null && timer.isRunning()) {
            runningIndicator.setFill(javafx.scene.paint.Color.RED);
        } else {
            runningIndicator.setFill(javafx.scene.paint.Color.DARKGRAY);
        }
    }
    
    private void updateHornSymbol() {
        String hornId = selectedTimerId.equals("timerOne") ? "timerOneHorn" :
                        selectedTimerId.equals("timerTwo") ? "timerTwoHorn" : "timerThreeHorn";
        ScoreIndicator horn = (ScoreIndicator) ruleEngine.getElement(hornId);
        if (horn != null && horn.getCurrentValue()) {
            hornSymbol.setVisible(true);
        } else {
            hornSymbol.setVisible(false);
        }
    }
    
    private void setupFlashPattern(int pattern) {
        flashTimeline.stop();   // Stop any existing timeline
        flashTimeline.getKeyFrames().clear();   // Clear old keyframes
        switch (pattern) {
            case 1: // ON 500ms, OFF 500ms
                flashTimeline.getKeyFrames().addAll(
                    new KeyFrame(Duration.millis(0), e -> timerLabel.setVisible(true)),
                    new KeyFrame(Duration.millis(500), e -> timerLabel.setVisible(false)),
                    new KeyFrame(Duration.millis(1000), e -> timerLabel.setVisible(false))
                );
                flashTimeline.setCycleCount(Timeline.INDEFINITE);
                // flashTimeline.play();
                break;
            case 2: // ON 200ms, OFF 100ms, ON 200ms, OFF 100ms, ON 200ms, OFF 200ms
                flashTimeline.getKeyFrames().addAll(
                    new KeyFrame(Duration.millis(0), e -> timerLabel.setVisible(true)),
                    new KeyFrame(Duration.millis(200), e -> timerLabel.setVisible(false)),
                    new KeyFrame(Duration.millis(300), e -> timerLabel.setVisible(true)),
                    new KeyFrame(Duration.millis(500), e -> timerLabel.setVisible(false)),
                    new KeyFrame(Duration.millis(600), e -> timerLabel.setVisible(true)),
                    new KeyFrame(Duration.millis(800), e -> timerLabel.setVisible(false)),
                    new KeyFrame(Duration.millis(1000), e -> timerLabel.setVisible(false))
                );
                flashTimeline.setCycleCount(Timeline.INDEFINITE);
                break;
            case 3: // ON 200ms, OFF 300ms, ON 200ms, OFF 300ms
                flashTimeline.getKeyFrames().addAll(
                    new KeyFrame(Duration.millis(0), e -> timerLabel.setVisible(true)),
                    new KeyFrame(Duration.millis(200), e -> timerLabel.setVisible(false)),
                    new KeyFrame(Duration.millis(500), e -> timerLabel.setVisible(true)),
                    new KeyFrame(Duration.millis(700), e -> timerLabel.setVisible(false)),
                    new KeyFrame(Duration.millis(1000), e -> timerLabel.setVisible(false))
                );
                flashTimeline.setCycleCount(Timeline.INDEFINITE);
                break;
        }
    }
    
    private ScoreTimer getSelectedTimer() {
        return (ScoreTimer) ruleEngine.getElement(selectedTimerId);
    }
    
    private String getSelectedTimerId() {
        return selectedTimerId;
    }
    
    private String padCenter(String text, int width) {
        int padding = (width - text.length()) / 2;
        return " ".repeat(Math.max(0, padding)) + text + " ".repeat(Math.max(0, width - text.length() - padding));
    }
    
    private String padRight(String text, int width) {
        return " ".repeat(Math.max(0, width - text.length())) + text;
    }
}