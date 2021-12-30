module ImageModule {
    exports com.udacity.catpoint.image.service;
    requires org.slf4j;
    requires transitive software.amazon.awssdk.services.rekognition;
    requires transitive software.amazon.awssdk.auth;
    requires transitive software.amazon.awssdk.regions;
    requires transitive software.amazon.awssdk.core;
    requires java.desktop;
}