module security {
    exports com.cat.data;
    exports com.cat.security.service;
    requires image;
    requires java.desktop;
    requires com.google.common;
    requires com.google.gson;
    requires java.prefs;
    opens com.cat.data to com.google.gson;
}