package com.cat.security.service;

import com.cat.data.*;
import com.cat.image.service.ImageService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

//
@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {
    @Mock
    private ImageService imageService;
    @InjectMocks
    private SecurityRepository securityRepository = new PretendDatabaseSecurityRepositoryImpl();

    private SecurityService securityService;
    private Map<UUID, Sensor> sensors;

    private UUID gardenSensorId;

    private static Stream<Arguments> differentAlarmTypesWithTheSameSensorAndExpectedDifferentAlarmStatus() {
        return Stream.of(
                Arguments.of(
                        ArmingStatus.ARMED_AWAY,
                        AlarmStatus.NO_ALARM,
                        AlarmStatus.PENDING_ALARM
                ),
                Arguments.of(
                        ArmingStatus.ARMED_AWAY,
                        AlarmStatus.PENDING_ALARM,
                        AlarmStatus.ALARM
                )
        );
    }

    @BeforeEach
    void init() {
//        securityRepository = new PretendDatabaseSecurityRepositoryImpl();
        securityService = new SecurityService(securityRepository, imageService);
        sensors = new LinkedHashMap<>();

        securityService.addSensor(new Sensor("Door", SensorType.DOOR));
        securityService.addSensor(new Sensor("Window", SensorType.WINDOW));
        securityService.addSensor(new Sensor("Garden", SensorType.MOTION));
        securityService.getSensors().forEach(sensor -> {
            sensors.put(sensor.getSensorId(), sensor);
            if (sensor.getName().equals("Garden"))
                gardenSensorId = sensor.getSensorId();
        });

        securityService.setArmingStatus(ArmingStatus.DISARMED);
        securityService.setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @AfterEach
    void close() {
        sensors.forEach((sensorId, sensor) -> securityService.removeSensor(sensor));
        sensors.clear();
    }

    @ParameterizedTest
    @MethodSource("differentAlarmTypesWithTheSameSensorAndExpectedDifferentAlarmStatus")
    public void alarmArmed_sensorActiveAndDifferentAlarmStatus_alarmStatusShouldBeDifferent(
            ArmingStatus armingStatus,
            AlarmStatus alarmStatus,
            AlarmStatus expectedAlarmStatus
    ) {
        securityService.setArmingStatus(armingStatus);
        securityService.setAlarmStatus(alarmStatus);

        securityService.changeSensorActivationStatus(sensors.get(gardenSensorId), true);

        Assertions.assertEquals(securityService.getAlarmStatus(), expectedAlarmStatus);
    }

    @Test
    public void alarmPending_allSensorsInactive_alarmShouldBeNoAlarm() {
        securityService.setArmingStatus(ArmingStatus.ARMED_AWAY);
        securityService.setAlarmStatus(AlarmStatus.PENDING_ALARM);

        sensors.forEach((Id, sensor) -> securityService.changeSensorActivationStatus(sensor, false));

        Assertions.assertEquals(securityService.getAlarmStatus(), AlarmStatus.NO_ALARM);
    }

    @Test
    public void alarmActive_sensorsChange_shouldNotChangeAlarm() {
        securityService.setArmingStatus(ArmingStatus.ARMED_AWAY);

        // Activate two sensors are not Garden
        sensors.forEach((id, sensor) -> {
            if (!id.equals(gardenSensorId))
                securityService.changeSensorActivationStatus(sensor, true);
        });

        // Alarm should be ALARM
        Assertions.assertEquals(AlarmStatus.ALARM, securityService.getAlarmStatus());

        // Deactivate the same sensors
        sensors.forEach((id, sensor) -> {
            if (!id.equals(gardenSensorId))
                securityService.changeSensorActivationStatus(sensor, false);
        });

        // Should not affect the alarm status
        Assertions.assertEquals(AlarmStatus.ALARM, securityService.getAlarmStatus());
    }

    @Test
    public void alarmPending_sensorsAlreadyActive_shouldBeAlarm() {
        securityService.setArmingStatus(ArmingStatus.ARMED_AWAY);
        securityService.setAlarmStatus(AlarmStatus.PENDING_ALARM);

        sensors.forEach((Id, sensor) -> securityService.changeSensorActivationStatus(sensor, true));
        securityService.changeSensorActivationStatus(sensors.get(gardenSensorId), true);

        Assertions.assertEquals(securityService.getAlarmStatus(), AlarmStatus.ALARM);
    }

    @Test
    public void sensorDeactived_sensorAlreadyInactive_alarmShouldNotChange() {
        securityService.setArmingStatus(ArmingStatus.ARMED_AWAY);
        AlarmStatus alarmStatus = securityService.getAlarmStatus();

        sensors.forEach((Id, sensor) -> securityService.changeSensorActivationStatus(sensor, false));

        Assertions.assertEquals(securityService.getAlarmStatus(), alarmStatus);
    }

    @Test
    public void detectCat_systemArmedHome_alarmShouldBeAlarm() {
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
        BufferedImage image = getBufferedImage(true);
        Mockito.when(imageService.imageContainsCat(image, 50.0f)).thenReturn(true);

        securityService.processImage(image);

        Assertions.assertEquals(AlarmStatus.ALARM, securityService.getAlarmStatus());
    }

    @Test
    public void containNoCat_sensorsNotActive_alarmShouldBeNoAlarm() {
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
        securityService.setAlarmStatus(AlarmStatus.ALARM);

        AtomicBoolean isAllDeactive = new AtomicBoolean(true);
        sensors.forEach((Id, sensor) -> {
            if (!sensor.getActive())
                isAllDeactive.set(false);
        });

        // camera image does not contain a cat
        BufferedImage image = getBufferedImage(false);
        Mockito.when(imageService.imageContainsCat(image, 50.0f)).thenReturn(false);
        securityService.processImage(image);

        // As long as the sensors are not active, change the status to no alarm
        if (isAllDeactive.get())
            Assertions.assertEquals(AlarmStatus.NO_ALARM, securityService.getAlarmStatus());
    }

    @Test
    public void systemDisarmed_alarmShouldBeNoAlarm() {
        securityService.setArmingStatus(ArmingStatus.DISARMED);

        Assertions.assertEquals(securityService.getAlarmStatus(), AlarmStatus.NO_ALARM);
    }

    @Test
    public void systemArmed_allSensors_shouldBeResetToInactive() {
        sensors.forEach((Id, sensor) -> securityService.changeSensorActivationStatus(sensor, true));

        // System is armed
        securityService.setArmingStatus(ArmingStatus.ARMED_AWAY);

        // Reset all sensors to inactive
        sensors.forEach((id, sensor) -> Assertions.assertFalse(sensor.getActive()));
    }

    @Test
    public void systemArmed_cameraDetectACat_alarmShouldBeAlarm() {
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        BufferedImage image = getBufferedImage(true);
        Mockito.when(imageService.imageContainsCat(image, 50.0f)).thenReturn(true);
        securityService.processImage(image);

        Assertions.assertEquals(securityService.getAlarmStatus(), AlarmStatus.ALARM);
    }

    @Test
    public void systemArmed_oneOfActiveSensorDeactived_alarmShouldNotChange() {
        securityService.setArmingStatus(ArmingStatus.ARMED_AWAY);

        AtomicReference<Sensor> gardenSensor = new AtomicReference<>();
        sensors.forEach((id, sensor) -> {
            securityService.changeSensorActivationStatus(sensor, true);
            if (sensor.getName().equals("Garden"))
                gardenSensor.set(sensor);
        });

        securityService.changeSensorActivationStatus(gardenSensor.getPlain(), false);
        Assertions.assertEquals(securityService.getAlarmStatus(), AlarmStatus.ALARM);
    }

    @Test
    public void systemArmed_detectsACatAndAlarm_secondScanNoCatNoAlarm() {
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        BufferedImage image = getBufferedImage(true);
        Mockito.when(imageService.imageContainsCat(image, 50.0f)).thenReturn(true);
        securityService.processImage(image);
        Assertions.assertEquals(securityService.getAlarmStatus(), AlarmStatus.ALARM);

        image = getBufferedImage(false);
        Mockito.when(imageService.imageContainsCat(image, 50.0f)).thenReturn(false);
        securityService.processImage(image);
        Assertions.assertEquals(securityService.getAlarmStatus(), AlarmStatus.NO_ALARM);
    }

    @Test
    @DisplayName("Even when a cat is detected in the image, the system should go to the NO ALARM state when deactivated.")
    public void catDetected_whenSystemDeactivate_alarmShouldBeNoAlarm() {
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        BufferedImage image = getBufferedImage(true);
        Mockito.when(imageService.imageContainsCat(image, 50.0f)).thenReturn(true);
        securityService.processImage(image);

        Assertions.assertEquals(securityService.getAlarmStatus(), AlarmStatus.ALARM);

        securityService.setArmingStatus(ArmingStatus.DISARMED);

        Assertions.assertEquals(securityService.getAlarmStatus(), AlarmStatus.NO_ALARM);
    }

    @Test
    public void systemArmed_detectsCatAndActivateASensor_secondScanNoCatButSystemShouldBeAlarm() {
        // Arm the system
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        // Scan a picture until it detects a cat
        BufferedImage image = getBufferedImage(true);
        Mockito.when(imageService.imageContainsCat(image, 50.0f)).thenReturn(true);
        securityService.processImage(image);

        // Activate a sensor
        securityService.changeSensorActivationStatus(sensors.get(gardenSensorId), true);

        // Scan a picture again until there is no cat
        image = getBufferedImage(false);
        Mockito.when(imageService.imageContainsCat(image, 50.0f)).thenReturn(false);
        securityService.processImage(image);

        // The system should still be in alarm state as there is a sensor active
        Assertions.assertEquals(AlarmStatus.ALARM, securityService.getAlarmStatus());
    }

    @Test
    public void systemArmed_sensorsNotResetToInactive() {
        // Put all sensors to the active state when disarmed
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        sensors.forEach((id, sensor) -> securityService.changeSensorActivationStatus(sensor, true));

        // Then put the system in the armed state
        securityService.setArmingStatus(ArmingStatus.ARMED_AWAY);

        // Sensors should be inactivated
        sensors.forEach((id, sensor) -> Assertions.assertFalse(sensor.getActive()));
    }

    @Test
    public void systemDisArmed_detectsACatThenSystemArmed_alarmShouldBeAlarm() {
        securityService.setArmingStatus(ArmingStatus.DISARMED);

        BufferedImage image = getBufferedImage(true);
        Mockito.when(imageService.imageContainsCat(image, 50.0f)).thenReturn(true);
        securityService.processImage(image);

        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        Assertions.assertEquals(securityService.getAlarmStatus(), AlarmStatus.ALARM);
    }

    private BufferedImage getBufferedImage(Boolean cat) {
        BufferedImage image;
        String path = cat ? "./src/test/resources/sample-cat.jpg" : "./src/test/resources/sample-not-cat.jpg";
        try {
            image = ImageIO.read(new File(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return image;
    }

}