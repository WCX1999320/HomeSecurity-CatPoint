package com.udacity.catpoint.security.service;

import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.udacity.catpoint.image.service.ImageService;
import com.udacity.catpoint.security.data.*;
import com.udacity.catpoint.security.application.StatusListener;

import java.util.Collections;
import java.util.UUID;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.prefs.BackingStoreException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
public class SecurityServiceTest {
    private SecurityService securityService;
    private Sensor sensor;
    private final String random = UUID.randomUUID().toString();

    @Mock
    private StatusListener statusListener;

    @Mock
    ImageService imageService;

    @Mock
    private SecurityRepository securityRepository;

    private Sensor getNewSensor() {
        return new Sensor(random, SensorType.DOOR);
    }

    private Set<Sensor> getAllSensors(int count, boolean status) {
        Set<Sensor> sensors = new HashSet<>();
        for (int i = 0; i < count; i++) {
            sensors.add(new Sensor(random, SensorType.DOOR));
        }
        sensors.forEach(sensor -> sensor.setActive(status));
        return sensors;
    }

    @BeforeEach
    void init() {
        securityService = new SecurityService(securityRepository, imageService);
        sensor = getNewSensor();
    }

    //1. If alarm is armed and a sensor becomes activated, put the system into pending alarm status.
    @Test
    void systemArmedSensorActivated_toPending() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    //2. If alarm is armed and a sensor becomes activated and the system is already pending alarm, set the alarm status to alarm.
    @Test
    void systemArmedSensorActivatedPending_toAlarm() {
        when(securityService.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository, atMost(2)).setAlarmStatus(AlarmStatus.ALARM); //first call up
    }

    //3. If pending alarm and all sensors are inactive, return to no alarm state.
    @Test
    void pendindAlarmAllSensorsInactive_toNoAlarm() {
        Sensor sensor = new Sensor("mysensor", SensorType.DOOR);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
        sensor.setActive(false);
        Mockito.when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.setAlarmStatus(AlarmStatus.PENDING_ALARM);
        Mockito.verify(securityRepository, Mockito.times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    //4. If alarm is active, change in sensor state should not affect the alarm state.
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void alarmActive_sensorStateShouldNotAffectAlarmState(boolean status) {
        sensor.setActive(false);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
        ArgumentCaptor<Sensor> captor = ArgumentCaptor.forClass(Sensor.class);
        verify(securityRepository, atMostOnce()).updateSensor(captor.capture());
        assertEquals(captor.getValue(), sensor);

        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
        verify(securityRepository, atMost(2)).updateSensor(captor.capture());
        assertEquals(captor.getValue(), sensor);
    }

    //5. If a sensor is activated while already active and the system is in pending state, change it to alarm state.
    @Test
    void sensorActivatedWhileActivePendingAlarm_toAlarm() {
        Sensor sensor = new Sensor("mytestsensor", SensorType.DOOR);
        Mockito.when(securityRepository.getSensors()).thenReturn(getAllSensors(2, true));
        Mockito.when(securityService.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        Mockito.when(securityService.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        ArgumentCaptor<AlarmStatus> captor = ArgumentCaptor.forClass(AlarmStatus.class);
        Mockito.verify(securityRepository, atMostOnce()).setAlarmStatus(captor.capture());
        assertEquals(captor.getValue(), AlarmStatus.ALARM);
    }

    //6. If a sensor is deactivated while already inactive, make no changes to the alarm state.
    @ParameterizedTest
    @EnumSource(value = AlarmStatus.class, names = {"NO_ALARM", "PENDING_ALARM", "ALARM"})
    void sensorDeactivatedWhileInactive_noChangeToAlarm(AlarmStatus status) {
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository, never()).setAlarmStatus(any());
    }

    //7. If the image service identifies an image containing a cat while the system is armed-home, put the system into alarm status.
    @Test
    void armedHomeIdentifiesACat_toAlarm() {
        BufferedImage catImage = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(any(), ArgumentMatchers.anyFloat())).thenReturn(true);
        securityService.processImage(catImage);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    //8. If the image service identifies an image that does not contain a cat, change the status to no alarm as long as the sensors are not active.
    @Test
    void noCat_toNoAlarmAsLongAsSensorsInactive() {
        Set<Sensor> sensors = getAllSensors(3, false);
        when(securityRepository.getSensors()).thenReturn(sensors);
        when(imageService.imageContainsCat(any(), ArgumentMatchers.anyFloat())).thenReturn(false);
        securityService.processImage(mock(BufferedImage.class));
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    //9. If the system is disarmed, set the status to no alarm.
    @Test
    void systemDisarmed_toNoAlarm() {
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    //10. If the system is armed, reset all sensors to inactive.
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void systemArmed_resetToInactive(ArmingStatus status) {
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
        assertTrue(securityService.getSensors().stream().allMatch(sensor -> Boolean.FALSE.equals(sensor.getActive())));
    }

    //11. If the system is armed-home while the camera shows a cat, set the alarm status to alarm.
    @Test
    void systemArmedHomeIdentifiesCat_toAlarm() {
        BufferedImage catImage = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        securityService.processImage(catImage);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    void systemArmed_updateSensor() {
        ArmingStatus armingStatus = ArmingStatus.ARMED_HOME;
        Sensor sensor = new Sensor("test", SensorType.DOOR);
        sensor.setActive(true);
        Mockito.when(securityRepository.getSensors()).thenReturn(Collections.singleton(sensor));
        securityService.setArmingStatus(armingStatus);
        Mockito.verify(securityRepository, Mockito.times(1)).updateSensor(any());
    }

    @ParameterizedTest
    @EnumSource(ArmingStatus.class)
    public void setArmingStatus(ArmingStatus status) {
        securityService.setArmingStatus(status);
    }

    @Test
    public void addRemoveStatusListener() {
        securityService.addStatusListener(statusListener);
        securityService.removeStatusListener(statusListener);
    }

    @Test
    public void addRemoveSensor() {
        Sensor sensor = new Sensor("test", SensorType.DOOR);
        securityService.addSensor(sensor);
        assertNotNull(securityService.getSensors());
        securityService.removeSensor(sensor);
    }

    @ParameterizedTest
    @CsvSource({"NO_ALARM,DOOR,true", "NO_ALARM,DOOR,false", "PENDING_ALARM,DOOR,true", "PENDING_ALARM,DOOR,false",
            "PENDING_ALARM,WINDOW,true", "PENDING_ALARM,WINDOW,false"})
    public void changeSensorStatusWithAlarms(AlarmStatus alarmStatus, SensorType sensorType, Boolean active){
        Mockito.when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        Sensor sensor = new Sensor("test", sensorType);
        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor, active);
        sensor.setActive(false);
        securityService.changeSensorActivationStatus(sensor, active);
        Mockito.when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        sensor = new Sensor("test", sensorType);
        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor, active);
        sensor.setActive(false);
        securityService.changeSensorActivationStatus(sensor, active);
    }
}

