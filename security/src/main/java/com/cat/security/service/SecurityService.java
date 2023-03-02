package com.cat.security.service;

import com.cat.data.AlarmStatus;
import com.cat.data.ArmingStatus;
import com.cat.data.SecurityRepository;
import com.cat.data.Sensor;
import com.cat.image.service.ImageService;
import lombok.ToString;

import java.awt.image.BufferedImage;
import java.util.*;

/**
 * Service that receives information about changes to the security system. Responsible for
 * forwarding updates to the repository and making any decisions about changing the system state.
 * This is the class that should contain most of the business logic for our system, and it is the
 * class you will be writing unit tests for.
 */
@ToString
public class SecurityService {

    private final ImageService imageService;
    private final SecurityRepository securityRepository;
    private final Set<StatusListener> statusListeners = new HashSet<>();
    private final Map<UUID, Sensor> sensors = new LinkedHashMap<>();
    private Boolean isCatDetected = false;

    public SecurityService(SecurityRepository securityRepository, ImageService imageService) {
        this.securityRepository = securityRepository;
        this.imageService = imageService;
    }

    /**
     * Internal method that handles alarm status changes based on whether
     * the camera currently shows a cat.
     *
     * @param cat True if a cat is detected, otherwise false.
     */
    private void catDetected(Boolean cat) {
        List<Sensor> activeSensors = getActiveSensors();
        if (cat && getArmingStatus() == ArmingStatus.ARMED_HOME) {
            setAlarmStatus(AlarmStatus.ALARM);
        } else if (activeSensors.size() > 0) {
            return;
        } else {
            setAlarmStatus(AlarmStatus.NO_ALARM);
        }

        statusListeners.forEach(sl -> sl.catDetected(cat));
    }

    /**
     * Register the StatusListener for alarm system updates from within the SecurityService.
     *
     * @param statusListener
     */
    public void addStatusListener(StatusListener statusListener) {
        statusListeners.add(statusListener);
    }

    public void removeStatusListener(StatusListener statusListener) {
        statusListeners.remove(statusListener);
    }

    /**
     * Internal method for updating the alarm status when a sensor has been activated.
     */
    private void handleSensorActivated() {
        if (securityRepository.getArmingStatus() == ArmingStatus.DISARMED) {
            return; //no problem if the system is disarmed
        }
        switch (securityRepository.getAlarmStatus()) {
            case NO_ALARM -> setAlarmStatus(AlarmStatus.PENDING_ALARM);
            case PENDING_ALARM -> setAlarmStatus(AlarmStatus.ALARM);
        }

    }

    /**
     * Internal method for updating the alarm status when a sensor has been deactivated
     */
    private void handleSensorDeactivated() {
        List<Sensor> activeSensors = getActiveSensors();
        if (activeSensors.size() > 0)
            return;

        switch (securityRepository.getAlarmStatus()) {
            case PENDING_ALARM -> setAlarmStatus(AlarmStatus.NO_ALARM);
            case ALARM -> setAlarmStatus(AlarmStatus.PENDING_ALARM);
        }
    }

    private List<Sensor> getActiveSensors() {
        List<Sensor> activeSensors = new ArrayList<>();
        sensors.forEach((id, s) -> {
            if (s.getActive())
                activeSensors.add(s);
        });
        return activeSensors;
    }

    /**
     * Change the activation status for the specified sensor and update alarm status if necessary.
     *
     * @param sensor
     * @param active
     */
    public void changeSensorActivationStatus(Sensor sensor, Boolean active) {
        if (!sensor.getActive() && active) {
            sensor.setActive(active);
            handleSensorActivated();
        } else if (sensor.getActive() && !active) {
            sensor.setActive(active);
            handleSensorDeactivated();
        } else if (!sensor.getActive() && !active) {
            sensor.setActive(active);
            handleSensorDeactivated();
        }

        securityRepository.updateSensor(sensor);
    }

    /**
     * Send an image to the SecurityService for processing. The securityService will use it's provided
     * ImageService to analyze the image for cats and update the alarm status accordingly.
     *
     * @param currentCameraImage
     */
    public void processImage(BufferedImage currentCameraImage) {
        isCatDetected = imageService.imageContainsCat(currentCameraImage, 50.0f);
        catDetected(isCatDetected);
    }

    public AlarmStatus getAlarmStatus() {
        return securityRepository.getAlarmStatus();
    }

    /**
     * Change the alarm status of the system and notify all listeners.
     *
     * @param status
     */
    public void setAlarmStatus(AlarmStatus status) {
        securityRepository.setAlarmStatus(status);
        statusListeners.forEach(sl -> sl.notify(status));
    }

    public Set<Sensor> getSensors() {
        return securityRepository.getSensors();
    }

    public void addSensor(Sensor sensor) {
        sensors.put(sensor.getSensorId(), sensor);
        securityRepository.addSensor(sensor);
    }

    public void removeSensor(Sensor sensor) {
        sensors.remove(sensor.getSensorId());
        securityRepository.removeSensor(sensor);
    }

    public ArmingStatus getArmingStatus() {
        return securityRepository.getArmingStatus();
    }

    /**
     * Sets the current arming status for the system. Changing the arming status
     * may update both the alarm status.
     *
     * @param armingStatus
     */
    public void setArmingStatus(ArmingStatus armingStatus) {
        if (armingStatus == ArmingStatus.DISARMED) {
            setAlarmStatus(AlarmStatus.NO_ALARM);
        } else if (isCatDetected) {
            setAlarmStatus(AlarmStatus.ALARM);
        } else {
            sensors.forEach((id, sensor) -> changeSensorActivationStatus(sensor, false));
        }
        securityRepository.setArmingStatus(armingStatus);
    }
}
