module com.mycompany.msr.amis {

    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires java.net.http;
    requires jakarta.mail;
    requires com.fasterxml.jackson.databind;

    // Apache POI (automatic modules)
    requires org.apache.poi.ooxml;
    requires org.apache.poi.poi;

    // JFoenix
    requires com.jfoenix;
    requires java.base;

    opens com.mycompany.msr.amis to javafx.fxml, com.fasterxml.jackson.databind;

    exports com.mycompany.msr.amis;
}
