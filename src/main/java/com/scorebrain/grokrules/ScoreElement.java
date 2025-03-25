package com.scorebrain.grokrules;

import com.google.gson.JsonObject;

public interface ScoreElement {
    void initialize(JsonObject config);
    String getId();
    void reset();
    String getDisplayValue();
}