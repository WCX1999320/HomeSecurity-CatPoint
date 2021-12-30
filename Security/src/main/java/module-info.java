module Security {
    requires java.desktop;
    requires miglayout;
    requires guava;
    requires com.google.gson;
    requires java.prefs;
    requires Image;
    requires java.sql;

    opens org.example.security.data to com.google.gson;
}