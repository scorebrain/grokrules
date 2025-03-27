package com.scorebrain.grokrules;

import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.util.Duration;

public class ScoreboardController {

    private RuleEngine ruleEngine = new RuleEngine("grokruleset.json");
    private String[] timerIds = {"timerOne", "timerTwo", "timerThree"};
    private int currentTimerIndex = 0;
    private StringBuilder inputBuffer = new StringBuilder();
    private Timeline uiTimer;
    private Timeline hornTimeline;
    private boolean settingMode = false;

    // UI elements from grokrules.fxml
    @FXML private Label timerLabel;          // Displays the current timer value
    @FXML private Label lcdLine1;           // Top LCD line (e.g., timer status)
    @FXML private Label lcdLine2;           // Bottom LCD line (e.g., input feedback)
    @FXML private Button buttonPlusOne;     // Increment timer by 1 second
    @FXML private Button buttonMinusOne;    // Decrement timer by 1 second
    @FXML private Button buttonSet;         // Enter set mode to input a new value
    @FXML private Button buttonStartStop;   // Toggle timer running state
    @FXML private Button buttonEnter;       // Confirm input in set mode
    @FXML private Button buttonBackspace;   // Backspace in set mode
    @FXML private Circle runningIndicator;  // Shows if the timer is running
    @FXML private Text hornSymbol;          // Indicates horn state
    @FXML private Button buttonHorn;        // Trigger the horn
    @FXML private Button buttonNextTimer;   // Cycle to the next timer
    @FXML private Button button0, button1, button2, button3, button4, 
          button5, button6, button7, button8, button9; // Number buttons for input

    @FXML
    private void initialize() {
        resetUI();
        startUITimer();
    }

    // Handle number button clicks (0-9) in set mode
    @FXML
    private void handleNumberClick(ActionEvent event) {
        if (!settingMode) return;
        Button clickedButton = (Button) event.getSource();
        String number = clickedButton.getText();
        inputBuffer.append(number);
        lcdLine2.setText("ENTER TIME: " + inputBuffer.toString());
    }

    // Handle backspace in set mode
    @FXML
    private void handleBackClick(ActionEvent event) {
        if (!settingMode) return;
        if (inputBuffer.length() == 0) {
            settingMode = false;
            resetUI();
        } else {
            inputBuffer.setLength(inputBuffer.length() - 1);
            lcdLine2.setText("ENTER TIME: " + inputBuffer.toString());
        }
    }

    // Increment the timer by 1 second
    @FXML
    private void handlePlusOne(ActionEvent event) {
        ScoreTimer timer = getSelectedTimer();
        if (timer != null && !timer.isRunning()) {
            timer.increment();
            updateUI();
        } else {
            lcdLine2.setText("Timer is running!");
        }
    }

    // Decrement the timer by 1 second
    @FXML
    private void handleMinusOne(ActionEvent event) {
        ScoreTimer timer = getSelectedTimer();
        if (timer != null && !timer.isRunning()) {
            timer.decrement();
            updateUI();
        } else {
            lcdLine2.setText("Timer is running!");
        }
    }

    // Toggle set mode for entering a new timer value
    @FXML
    private void handleSet(ActionEvent event) {
        ScoreTimer timer = getSelectedTimer();
        if (timer == null || timer.isRunning()) {
            lcdLine2.setText("Timer is running!");
            return;
        }
        settingMode = !settingMode;
        if (settingMode) {
            inputBuffer.setLength(0);
            lcdLine2.setText("ENTER TIME: ");
            buttonPlusOne.setDisable(true);
            buttonMinusOne.setDisable(true);
        } else {
            resetUI();
            buttonPlusOne.setDisable(false);
            buttonMinusOne.setDisable(false);
        }
    }

    // Confirm the entered value in set mode
    @FXML
    private void handleEnter(ActionEvent event) {
        if (!settingMode || inputBuffer.length() == 0) return;
        ScoreTimer timer = getSelectedTimer();
        try {
            String input = inputBuffer.toString();
            String paddedInput = String.format("%4s", input).replace(' ', '0');
            int minutes = Integer.parseInt(paddedInput.substring(0, 2));
            int seconds = Integer.parseInt(paddedInput.substring(2, 4));
            int totalSeconds = minutes * 60 + seconds;
            timer.setValue(totalSeconds);
            settingMode = false;
            buttonPlusOne.setDisable(false);
            buttonMinusOne.setDisable(false);
            resetUI();
        } catch (NumberFormatException e) {
            lcdLine2.setText("Invalid input!");
        }
    }

    // Toggle the timer's running state
    @FXML
    private void handleStartStop(ActionEvent event) {
        ScoreTimer timer = getSelectedTimer();
        if (timer != null) {
            timer.startstop();
            updateUI();
        }
    }

    // Trigger the horn
    @FXML
    private void handleHorn(ActionEvent event) {
        String hornId = timerIds[currentTimerIndex] + "_Horn";
        ScoreIndicator horn = (ScoreIndicator) ruleEngine.getElement(hornId);
        if (horn != null && !horn.getCurrentValue()) {
            horn.setCurrentValue(true);
            startHornAnimation(horn);
            updateUI();
        }
    }

    // Cycle to the next timer
    @FXML
    private void handleNextTimer(ActionEvent event) {
        currentTimerIndex = (currentTimerIndex + 1) % timerIds.length;
        updateUI();
    }

    // Start a periodic UI update timer
    private void startUITimer() {
        if (uiTimer != null) uiTimer.stop();
        uiTimer = new Timeline(new KeyFrame(Duration.seconds(0.1), e -> updateUI()));
        uiTimer.setCycleCount(Timeline.INDEFINITE);
        uiTimer.play();
    }

    // Reset the UI to the default state
    private void resetUI() {
        updateUI();
        if (!settingMode) {
            lcdLine2.setText("");
        }
    }

    // Update the UI based on the current timer and indicator states
    private void updateUI() {
        // ScoreTimer timer = getSelectedTimer();
        String hornId = timerIds[currentTimerIndex] + "_Horn";
        ScoreIndicator horn = (ScoreIndicator) ruleEngine.getElement(hornId);
        ScoreTimer timer = (ScoreTimer) ruleEngine.getElement(timerIds[currentTimerIndex]);
        if (timer != null) {
            timerLabel.setText(timer.getDisplayValue());
            runningIndicator.setFill(timer.isRunning() ? 
                javafx.scene.paint.Color.RED : javafx.scene.paint.Color.DARKGRAY);
        }
        // Update LCD
        String lcdText = "Timer " + (currentTimerIndex + 1) + ": " + timer.getDisplayValue();
        if (horn != null && horn.getCurrentValue()) {
            if (hornTimeline == null) {
                startHornAnimation(horn);
            }
            lcdText += " *"; // Steady * when horn is active
        }
        lcdLine1.setText(lcdText);
    }

    // Get the currently selected timer
    private ScoreTimer getSelectedTimer() {
        return (ScoreTimer) ruleEngine.getElement(timerIds[currentTimerIndex]);
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
                horn.setCurrentValue(false); // Reset state after animation
                hornTimeline = null;         // Clear timeline for next trigger
                updateUI();
            });

            hornSymbol.setVisible(true);
            hornTimeline.play();
        }
    }
}