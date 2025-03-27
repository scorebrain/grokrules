package com.scorebrain.grokrules;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class RuleEngine {
    private ScoreEventBus eventBus = new ScoreEventBus();
    private Map<String, ScoreElement> elements = new HashMap<>();

    public RuleEngine(String ruleFilePath) {
        loadRules(ruleFilePath);
    }

    private void loadRules(String ruleFilePath) {
        // Assume JSON parsing logic here
        try {
            JsonObject json = new Gson().fromJson(new FileReader(ruleFilePath), JsonObject.class);
            JsonArray elementsArray = json.getAsJsonArray("elements");
            
            for (JsonElement element : elementsArray) {
                JsonObject config = element.getAsJsonObject();
                String type = config.get("type").getAsString();
                ScoreElement scoreElement;
                
                if ("ScoreTimer".equals(type)) {
                    scoreElement = new ScoreTimer(eventBus);
                } else if ("ScoreIndicator".equals(type)) {
                    String indID = config.get("id").getAsString();
                    String indObserverID = config.has("observedTimerId") ? config.get("observedTimerId").getAsString() : null;
                    String indTriggerEvent = config.has("triggerEvent") ? config.get("triggerEvent").getAsString() : null;
                    String indPattern = config.has("pattern") ? config.get("pattern").getAsString() : null;
                    scoreElement = new ScoreIndicator(indID, indObserverID, indTriggerEvent, indPattern);
                } else {
                    continue;
                }
                
                scoreElement.initialize(config);
                elements.put(scoreElement.getId(), scoreElement);
                
                if (scoreElement instanceof ScoreIndicator) {
                    ScoreIndicator indicator = (ScoreIndicator) scoreElement;
                    String timerId = config.has("observedTimerId") ? config.get("observedTimerId").getAsString() : null;
                    if (timerId != null) {
                        eventBus.registerTimerObserver(timerId, indicator);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ScoreElement getElement(String id) {
        return elements.get(id);
    }

    public Collection<ScoreElement> getElements() {
        return elements.values();
    }
}