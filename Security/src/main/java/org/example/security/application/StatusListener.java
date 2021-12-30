package org.example.security.application;

import org.example.security.data.AlarmStatus;
import org.example.security.service.SecurityService;

/**
 * Identifies a component that should be notified whenever the system status changes
 */
public interface StatusListener {
    void notify(AlarmStatus status);
    void catDetected(boolean catDetected);
    void sensorStatusChanged(SecurityService securityService);
}
