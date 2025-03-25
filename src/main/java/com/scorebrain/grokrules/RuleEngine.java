package com.scorebrain.grokrules;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

public class RuleEngine {
    private Map<String, ScoreElement> elements = new HashMap<>();
    private Map<String, JsonObject> elementConfigs = new HashMap<>();
    private Map<String, String> messages = new HashMap<>();

    public RuleEngine(String ruleFilePath) {
        loadRules(ruleFilePath);
    }

    private void loadRules(String ruleFilePath) {
        try {
            Gson gson = new Gson();
            JsonObject rules = gson.fromJson(new FileReader(ruleFilePath), JsonObject.class);
            
            JsonArray elementsArray = rules.getAsJsonArray("elements");
            for (int i = 0; i < elementsArray.size(); i++) {
                JsonObject config = elementsArray.get(i).getAsJsonObject();
                String type = config.get("type").getAsString();
                ScoreElement element;
                switch (type) {
                    case "ScoreTimer":
                        element = new ScoreTimer(this); // Pass RuleEngine instance
                        break;
                    case "ScoreIndicator":
                        element = new ScoreIndicator();
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown element type: " + type);
                }
                element.initialize(config);
                elements.put(element.getId(), element);
                elementConfigs.put(element.getId(), config);
            }
            
            JsonObject messagesObj = rules.get("messages").getAsJsonObject();
            messagesObj.entrySet().forEach(e -> messages.put(e.getKey(), e.getValue().getAsString()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ScoreElement getElement(String id) {
        return elements.get(id);
    }

    public JsonObject getElementConfig(String id) {
        return elementConfigs.get(id);
    }

    public String getMessage(String key) {
        return messages.getOrDefault(key, "");
    }

    public void reset() {
        elements.values().forEach(ScoreElement::reset);
    }
}