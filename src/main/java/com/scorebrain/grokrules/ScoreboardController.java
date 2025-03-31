package com.scorebrain.grokrules;

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

public class ScoreboardController {

    private RuleEngine ruleEngine = new RuleEngine("grokruleset.json");
    private String[] timerIds = {"timerOne", "timerTwo", "timerThree"};
    private int currentTimerIndex = 0;
    private StringBuilder inputBuffer = new StringBuilder();
    private Timeline uiTimer;
    private Timeline hornTimeline;
    private boolean settingMode = false;

    // UI elements from grokrules.fxml
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

    @FXML
    private void initialize() {
        resetUI();
        startUITimer();
        // Set the supplier for each ScoreIndicator to check the selected timer
        for (ScoreElement element : ruleEngine.getElements()) {
            if (element instanceof ScoreIndicator) {
                ScoreIndicator indicator = (ScoreIndicator) element;
                indicator.setSelectedTimerSupplier(this::getSelectedTimerId);
            }
        }
    }

    /** Returns the ID of the currently selected timer. */
    public String getSelectedTimerId() {
        return timerIds[currentTimerIndex];
    }

    @FXML
    private void handleNumberClick(ActionEvent event) {
        if (!settingMode) return;
        Button clickedButton = (Button) event.getSource();
        String number = clickedButton.getText();
        inputBuffer.append(number);
        lcdLine2.setText("ENTER TIME: " + inputBuffer.toString());
    }

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

    @FXML
    private void handleStartStop(ActionEvent event) {
        ScoreTimer timer = getSelectedTimer();
        if (timer != null) {
            timer.startstop();
            updateUI();
        }
    }

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

    @FXML
    private void handleNextTimer(ActionEvent event) {
        currentTimerIndex = (currentTimerIndex + 1) % timerIds.length;
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
        }
    }

    private void updateUI() {
        String hornId = timerIds[currentTimerIndex] + "_Horn";
        ScoreIndicator horn = (ScoreIndicator) ruleEngine.getElement(hornId);
        ScoreTimer timer = (ScoreTimer) ruleEngine.getElement(timerIds[currentTimerIndex]);
        if (timer != null) {
            timerLabel.setText(timer.getDisplayValue());
            runningIndicator.setFill(timer.isRunning() ?
                javafx.scene.paint.Color.RED : javafx.scene.paint.Color.DARKGRAY);
        }
        String lcdText = "Timer " + (currentTimerIndex + 1) + ": " + timer.getDisplayValue();
        if (horn != null && horn.getCurrentValue()) {
            if (hornTimeline == null) {
                startHornAnimation(horn);
            }
            lcdText += " *";
        }
        lcdLine1.setText(lcdText);
    }

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
                horn.setCurrentValue(false);
                hornTimeline = null;
                updateUI();
            });

            hornSymbol.setVisible(true);
            hornTimeline.play();
        }
    }
}