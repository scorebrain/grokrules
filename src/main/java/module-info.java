module com.scorebrain.grokrules {
    requires javafx.controls;
    requires javafx.fxml;

    opens com.scorebrain.grokrules to javafx.fxml;
    exports com.scorebrain.grokrules;
    requires com.google.gson;
}
