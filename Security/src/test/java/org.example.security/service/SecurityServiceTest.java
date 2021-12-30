package org.example.security.service;

import org.example.image.service.ImageService;
import org.example.security.application.StatusListener;
import org.example.security.data.AlarmStatus;
import org.example.security.data.ArmingStatus;
import org.example.security.data.SecurityRepository;
import org.example.security.data.Sensor;
import org.example.security.data.SensorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.params.ParameterizedTest;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SecurityServiceTest {
    private SecurityService securityService;

    private Sensor sensor;

    private final String sensorName = UUID.randomUUID().toString();
    @Mock
    private SecurityRepository securityRepository;
    @Mock
    private ImageService imageService;
    @Mock
    private StatusListener statusListener;
    private Set<Sensor> getAllSensors(int count, boolean status){
        Set<Sensor> sensors = new HashSet<>();
        for (int i = 0; i < count; i++) {
            Sensor sensor = new Sensor(UUID.randomUUID().toString(), SensorType.DOOR);
            sensor.setActive(status);

            sensors.add(sensor);
        }

        return sensors;
    }

    @BeforeEach
    void init() {
        securityService = new SecurityService(securityRepository, imageService);
        sensor = new Sensor(sensorName, SensorType.DOOR);
    }

    @Test
    void if_armedAlarmStatus_and_activatedSensor_then_put_system_into_pendingAlarmStatus() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    @Test
    void if_armedAlarm_activatedSensor_pendingAlarm_then_alarmStatus() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    void if_pendingAlarm_inactiveAllSensors_then_noAlarmStatus() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        sensor.setActive(false);
        securityService.changeSensorActivationStatus(sensor);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @Test
    void if_pendingAlarm_activeSensor_then_noAlarmStatus() {
        lenient().when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        lenient().when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor, false);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @Test
    void if_Alarmed_inactiveAllSensors_then_noAlarmStatus() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        sensor.setActive(false);
        securityService.changeSensorActivationStatus(sensor);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void if_activeAlarm_and_activatedSensor_or_deactivatedSensor_then_activeAlarm(boolean sensorStatus) {
        lenient().when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        lenient().when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        securityService.changeSensorActivationStatus(sensor, sensorStatus);

        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    @Test
    void if_alreadyActiveSensor_activated_pendingAlarmState_then_alarmStatus() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor, true);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    @ParameterizedTest
    @EnumSource(AlarmStatus.class)
    void if_alreadyDeactivatedSensor_deactivated_then_noAlarmStatus(AlarmStatus alarmStatus) {
        lenient().when(securityRepository.getAlarmStatus()).thenReturn(alarmStatus);
        securityService.changeSensorActivationStatus(sensor, false);
        securityService.changeSensorActivationStatus(sensor, false);

        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    @Test
    // If the image service identifies an image containing a cat while the system is armed-home, put the system into alarm status.
    void if_armedHome_and_catImage_then_alarmStatus() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);

        securityService.processImage(eq(any(BufferedImage.class)));

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    // If the image service identifies an image that does not contain a cat, change the status to no alarm as long as the sensors are not active.
    void if_armedHome_and_noCatImage_then_noAlarmStatus() {
        lenient().when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        lenient().when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(false);

        securityService.changeSensorActivationStatus(sensor, false);
        securityService.processImage(eq(any(BufferedImage.class)));

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @Test
    // If the system is disarmed, set the status to no alarm.
    void if_disarmed_then_noAlarmStatus() {
        securityService.setArmingStatus(ArmingStatus.DISARMED);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @Test
    // If the system is armed, reset all sensors to inactive.
    void if_armed_then_resetSensors() {
        Set<Sensor> sensors=getAllSensors(3, true);
        for(Sensor sensor:sensors){
            sensor.setActive(false);
        }
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
        securityService.changeSensorActivationStatus(sensor, false);
        securityService.getSensors().forEach(sensor -> assertFalse(sensor.getActive()));
    }

    @Test
    // If the system is armed-home while the camera shows a cat, set the alarm status to alarm.
    void if_systemIsArmedHome_and_catImage_then_alarmStatus() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);

        securityService.processImage(eq(any(BufferedImage.class)));
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }
    // following tests aren't part of the requirements, just added to get full coverage
    @Test
    void testAddAndRemoveStatusListener() {
        securityService.addStatusListener(statusListener);
        securityService.removeStatusListener(statusListener);
    }

    @Test
    void testAddAndRemoveSensor() {
        securityService.addSensor(sensor);
        securityService.removeSensor(sensor);
    }
    @Test
    void testSetArmingStatus(){
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);

        securityService.processImage(eq(any(BufferedImage.class)));
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
    }
}