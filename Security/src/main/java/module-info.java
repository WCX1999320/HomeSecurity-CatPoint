module SecurityModule {
    requires transitive java.desktop;
    requires transitive java.prefs;
    requires transitive java.sql;
    requires miglayout.swing;
    requires gson;
    requires com.google.common;
    requires transitive ImageModule;
    opens com.udacity.catpoint.security.data to gson;
    opens com.udacity.catpoint.security.service to gson;
}