package com.cat.security.service;

import com.cat.data.*;
import com.cat.image.service.FakeImageService;
import com.cat.image.service.ImageService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.util.concurrent.atomic.AtomicReference;

//
@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {
    @Mock
    private ImageService imageService = new FakeImageService();
    @Mock
    private SecurityRepository securityRepository;
    @Mock
    private SecurityService securityService;
    private Map<UUID, Sensor> sensors;

    @BeforeEach
    void init() {
        securityRepository = new PretendDatabaseSecurityRepositoryImpl();
        securityService = new SecurityService(securityRepository, imageService);
        sensors = new LinkedHashMap<>();

        securityService.addSensor(new Sensor("Door", SensorType.DOOR));
        securityService.addSensor(new Sensor("Window", SensorType.WINDOW));
        securityService.addSensor(new Sensor("Garden", SensorType.MOTION));
        securityService.getSensors().forEach(sensor -> sensors.put(sensor.getSensorId(), sensor));

        securityService.setArmingStatus(ArmingStatus.DISARMED);
        securityService.setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @AfterEach
    void close() {
        sensors.forEach((sensorId, sensor) -> securityService.removeSensor(sensor));
        sensors.clear();
    }

    @Test
    public void alarmArmed_sensorActive_alarmStatusShouldBePending() {
        securityService.setArmingStatus(ArmingStatus.ARMED_AWAY);

        sensors.forEach((Id, sensor) -> {
            if (sensor.getName().equals("Garden"))
                securityService.changeSensorActivationStatus(sensor, true);
        });

        Assertions.assertEquals(securityService.getAlarmStatus(), AlarmStatus.PENDING_ALARM);
    }

    @Test
    public void alarmArmed_sensorActiveAndSystemPending_alarmStatusShouldBeAlarm() {
        securityService.setArmingStatus(ArmingStatus.ARMED_AWAY);
        securityService.setAlarmStatus(AlarmStatus.PENDING_ALARM);

        sensors.forEach((Id, sensor) -> {
            if (sensor.getName().equals("Garden"))
                securityService.changeSensorActivationStatus(sensor, true);
        });

        Assertions.assertEquals(securityService.getAlarmStatus(), AlarmStatus.ALARM);
    }

    @Test
    public void alarmPending_allSensorsInactive_alarmShouldBeNoAlarm() {
        securityService.setArmingStatus(ArmingStatus.ARMED_AWAY);
        securityService.setAlarmStatus(AlarmStatus.PENDING_ALARM);

        sensors.forEach((Id, sensor) -> securityService.changeSensorActivationStatus(sensor, false));

        Assertions.assertEquals(securityService.getAlarmStatus(), AlarmStatus.NO_ALARM);
    }

    @Test
    public void alarmAlarm_sensorsChange_shouldNotChangeAlarm() {
        securityService.setArmingStatus(ArmingStatus.ARMED_AWAY);
        securityService.setAlarmStatus(AlarmStatus.ALARM);

        sensors.forEach((Id, sensor) -> securityService.changeSensorActivationStatus(sensor, true));

        Assertions.assertEquals(securityService.getAlarmStatus(), AlarmStatus.ALARM);
    }

    @Test
    public void alarmPending_sensorsAlreadyActive_shouldBeAlarm() {
        securityService.setArmingStatus(ArmingStatus.ARMED_AWAY);
        securityService.setAlarmStatus(AlarmStatus.PENDING_ALARM);

        sensors.forEach((Id, sensor) -> securityService.changeSensorActivationStatus(sensor, true));
        sensors.forEach((Id, sensor) -> {
            if (sensor.getName().equals("Garden"))
                securityService.changeSensorActivationStatus(sensor, true);
        });

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

        Assertions.assertEquals(securityService.getAlarmStatus(), AlarmStatus.ALARM);
    }

    @Test
    public void detectNoCat_sensorsNotActive_alarmShouldBeNoAlarm() {
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
        BufferedImage image = getBufferedImage(false);
        Mockito.when(imageService.imageContainsCat(image, 50.0f)).thenReturn(false);
        securityService.processImage(image);

        Assertions.assertEquals(securityService.getAlarmStatus(), AlarmStatus.NO_ALARM);
    }

    @Test
    public void systemDisarmed_alarmShouldBeNoAlarm() {
        securityService.setArmingStatus(ArmingStatus.DISARMED);

        Assertions.assertEquals(securityService.getAlarmStatus(), AlarmStatus.NO_ALARM);
    }

    @Test
    public void systemArmed_allSensors_shouldBeInactive() {
        sensors.forEach((id, sensor) -> {
            if(sensor.getName().equals("Garden"))
                securityService.changeSensorActivationStatus(sensor, true);
        });
        securityService.setArmingStatus(ArmingStatus.ARMED_AWAY);

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
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        BufferedImage image = getBufferedImage(true);
        Mockito.when(imageService.imageContainsCat(image, 50.0f)).thenReturn(true);
        securityService.processImage(image);

        sensors.forEach((id, sensor) -> {
            if (sensor.getName().equals("Garden"))
                securityService.changeSensorActivationStatus(sensor, true);
        });

        image = getBufferedImage(false);
        Mockito.when(imageService.imageContainsCat(image, 50.0f)).thenReturn(false);
        securityService.processImage(image);

        Assertions.assertEquals(securityService.getAlarmStatus(), AlarmStatus.ALARM);
    }

    @Test
    public void systemArmed_sensorsNotResetToInactive() {
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        sensors.forEach((id, sensor) -> securityService.changeSensorActivationStatus(sensor, true));
        securityService.setArmingStatus(ArmingStatus.ARMED_AWAY);

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