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
    private String settingCounterId = null; // Track which counter is being set
    private JsonObject uiConfig;
    private Map<String, JsonObject> buttonConfigs = new HashMap<>();
    // Field to store the root node
    private Parent root;

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
            e.printStackTrace(); // Log any issues during initialization
    }
        /*timerIds = ruleEngine.getTimerIds();
        JsonObject uiConfig = ruleEngine.getUiConfig();
        if (uiConfig.has("buttons")) {
            JsonArray buttons = uiConfig.getAsJsonArray("buttons");
            for (JsonElement btn : buttons) {
                JsonObject btnConfig = btn.getAsJsonObject();
                String fxId = btnConfig.get("fxId").getAsString();
                buttonConfigs.put(fxId, btnConfig);
                Button button = (Button) root.lookup("#" + fxId); // Requires scene lookup or injection
                if (button != null) {
                    button.setText(btnConfig.get("label").getAsString());
                }
            }
        }
        if (uiConfig.has("buttons")) {
            JsonArray buttons = uiConfig.getAsJsonArray("buttons");
            for (JsonElement btn : buttons) {
                JsonObject btnConfig = btn.getAsJsonObject();
                String id = btnConfig.get("id").getAsString();
                String label = btnConfig.get("label").getAsString();
                Button button = switch (id) { // Map fx:id to Button reference
                    case "buttonPlusOne" -> buttonPlusOne;
                    case "buttonMinusOne" -> buttonMinusOne;
                    default -> null;
                };
                if (button != null) {
                    button.setText(label);
                }
            }
        }
        resetUI();
        startUITimer();
        // Set the supplier for each ScoreIndicator to check the selected timer
        for (ScoreElement element : ruleEngine.getElements()) {
            if (element instanceof ScoreIndicator) {
                ScoreIndicator indicator = (ScoreIndicator) element;
                indicator.setSelectedTimerSupplier(this::getSelectedTimerId);
            }
        }*/
    }
    
    // Method to set the root node
    public void setRoot(Parent root) {
        this.root = root;
    }

    // Method to configure buttons using the root node
    public void configureButtons() {
        JsonObject uiConfig = ruleEngine.getUiConfig();
        if (uiConfig != null && uiConfig.has("buttons")) {
            JsonArray buttons = uiConfig.getAsJsonArray("buttons");
            for (JsonElement btn : buttons) {
                JsonObject btnConfig = btn.getAsJsonObject();
                String fxId = btnConfig.get("fxId").getAsString();
                Button button = (Button) root.lookup("#" + fxId);
                if (button != null) {
                    button.setText(btnConfig.get("label").getAsString());
                } else {
                    System.out.println("Button with fx:id '" + fxId + "' not found.");
                }
            }
        }
    }
    

    /** Returns the ID of the currently selected timer. */
    public String getSelectedTimerId() {
        return timerIds.get(currentTimerIndex);
    }
    
    @FXML
    private void handleGridButton(ActionEvent event) {
        Button button = (Button) event.getSource();
        String fxId = button.getId();
        JsonObject config = buttonConfigs.get(fxId);
        if (config != null) {
            String action = config.get("action").getAsString();
            String target = config.get("target").getAsString();
            ScoreElement element = ruleEngine.getElement(target);
            if (element instanceof ScoreCounter counter && "increment".equals(action)) {
                counter.increment(config.get("amount").getAsInt());
                updateUI();
            }
        }
    }
    
    // Event handlers for Guest Points
    @FXML
    private void handleGuestPlusOne() {
        ScoreCounter counter = (ScoreCounter) ruleEngine.getElement("guestPoints");
        counter.increment(1);
        updateUI();
    }

    @FXML
    private void handleGuestPlusTwo() {
        ScoreCounter counter = (ScoreCounter) ruleEngine.getElement("guestPoints");
        counter.increment(2);
        updateUI();
    }

    @FXML
    private void handleGuestPlusSix() {
        ScoreCounter counter = (ScoreCounter) ruleEngine.getElement("guestPoints");
        counter.increment(6);
        updateUI();
    }

    @FXML
    private void handleGuestMinusOne() {
        ScoreCounter counter = (ScoreCounter) ruleEngine.getElement("guestPoints");
        counter.decrement(1);
        updateUI();
    }

    @FXML
    private void handleGuestSetPoints() {
        settingMode = true;
        settingCounterId = "guestPoints";
        inputBuffer.setLength(0);
        lcdLine2.setText("ENTER GUEST POINTS: ");
    }

    // Event handlers for Home Points
    @FXML
    private void handleHomePlusOne() {
        ScoreCounter counter = (ScoreCounter) ruleEngine.getElement("homePoints");
        counter.increment(1);
        updateUI();
    }

    @FXML
    private void handleHomePlusTwo() {
        ScoreCounter counter = (ScoreCounter) ruleEngine.getElement("homePoints");
        counter.increment(2);
        updateUI();
    }

    @FXML
    private void handleHomePlusSix() {
        ScoreCounter counter = (ScoreCounter) ruleEngine.getElement("homePoints");
        counter.increment(6);
        updateUI();
    }

    @FXML
    private void handleHomeMinusOne() {
        ScoreCounter counter = (ScoreCounter) ruleEngine.getElement("homePoints");
        counter.decrement(1);
        updateUI();
    }

    @FXML
    private void handleHomeSetPoints() {
        settingMode = true;
        settingCounterId = "homePoints";
        inputBuffer.setLength(0);
        lcdLine2.setText("ENTER HOME POINTS: ");
    }

    @FXML
    private void handleNumberClick(ActionEvent event) {
        if (!settingMode) return;
        Button clickedButton = (Button) event.getSource();
        String number = clickedButton.getText();
        inputBuffer.append(number);
        if (settingCounterId != null) {
            lcdLine2.setText("ENTER " + settingCounterId.toUpperCase().replace("POINTS", " POINTS") + ": " + inputBuffer.toString());
        } else {
            lcdLine2.setText("ENTER TIME: " + inputBuffer.toString());
        }
    }

    @FXML
    private void handleBackClick(ActionEvent event) {
        if (!settingMode) return;
        if (inputBuffer.length() == 0) {
            settingMode = false;
            settingCounterId = null;
            resetUI();
        } else {
            inputBuffer.setLength(inputBuffer.length() - 1);
            if (settingCounterId != null) {
                lcdLine2.setText("ENTER " + settingCounterId.toUpperCase().replace("POINTS", " POINTS") + ": " + inputBuffer.toString());
            } else {
                lcdLine2.setText("ENTER TIME: " + inputBuffer.toString());
            }
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
        try {
            int value = Integer.parseInt(inputBuffer.toString());
            if (settingCounterId != null) {
                ScoreCounter counter = (ScoreCounter) ruleEngine.getElement(settingCounterId);
                counter.setCurrentValue(value);
                settingCounterId = null;
            } else {
                ScoreTimer timer = getSelectedTimer();
                String input = inputBuffer.toString();
                String paddedInput = String.format("%4s", input).replace(' ', '0');
                int minutes = Integer.parseInt(paddedInput.substring(0, 2));
                int seconds = Integer.parseInt(paddedInput.substring(2, 4));
                int totalSeconds = minutes * 60 + seconds;
                timer.setValue(totalSeconds);
            }
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
        }
    }

    private void updateUI() {
        if (timerIds == null || timerIds.isEmpty()) {
            timerLabel.setText("No timers available");
            runningIndicator.setFill(javafx.scene.paint.Color.DARKGRAY);
            lcdLine1.setText("No timers");
            hornSymbol.setVisible(false);
            return;
        }
        String hornId = timerIds.get(currentTimerIndex) + "_Horn";
        ScoreIndicator horn = (ScoreIndicator) ruleEngine.getElement(hornId);
        ScoreTimer timer = (ScoreTimer) ruleEngine.getElement(timerIds.get(currentTimerIndex));
        if (timer != null) {
            timerLabel.setText(timer.getDisplayValue());
            runningIndicator.setFill(timer.isRunning() ?
                javafx.scene.paint.Color.RED : javafx.scene.paint.Color.DARKGRAY);
            // Determine if the timer should flash
            boolean shouldFlash = timer.isRunning() && timer.getCurrentValue() <= timer.getFlashZoneThreshold() && timer.getFlashZoneThreshold() >= 0;

            // Manage flashing state
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
        String lcdText = "Timer " + (currentTimerIndex + 1) + ": " + timer.getDisplayValue();
        if (horn != null && horn.getCurrentValue()) {
            if (hornTimeline == null) {
                startHornAnimation(horn);
            }
            lcdText += " *";
        }
        if (timer != null && timer.isRunning() && timer.getCurrentValue() <= timer.getFlashZoneThreshold() && timer.getFlashZoneThreshold() >= 0) {
            lcdText += " F";
        }
        lcdLine1.setText(lcdText);
    }
    
    private void startFlashAnimation(ScoreTimer timer) {
        String pattern = timer.getFlashZonePattern();
        if (pattern == null || pattern.isEmpty()) {
            return;
        }

        String[] steps = pattern.split(",");
        flashTimeline = new Timeline();
        double cumulativeTime = 0;
        boolean firstVisible = Boolean.parseBoolean(steps[0].split(":")[1]); // Get initial visibility

        for (String step : steps) {
            String[] parts = step.split(":");
            double duration = Double.parseDouble(parts[0]);
            boolean visible = Boolean.parseBoolean(parts[1]);
            flashTimeline.getKeyFrames().add(
                new KeyFrame(Duration.millis(cumulativeTime), e -> timerLabel.setVisible(visible))
            );
            cumulativeTime += duration;
        }
        // Add a final KeyFrame to reset to the initial state
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
        timerLabel.setVisible(true); // Ensure visibility when not flashing
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