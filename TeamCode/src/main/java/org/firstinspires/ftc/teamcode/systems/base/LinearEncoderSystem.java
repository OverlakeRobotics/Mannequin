package org.firstinspires.ftc.teamcode.systems.base;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorSimple;

import org.firstinspires.ftc.teamcode.components.motors.DriveMotor;
import org.firstinspires.ftc.teamcode.components.scale.ExponentialRamp;
import org.firstinspires.ftc.teamcode.components.scale.Point;
import org.firstinspires.ftc.teamcode.components.scale.Ramp;
import org.firstinspires.ftc.teamcode.components.limitswitch.LimitSwitch;
import org.firstinspires.ftc.teamcode.opmodes.IBaseOpMode;

/**
 * Created by Michael on 3/15/2018.
 */

public abstract class LinearEncoderSystem extends LinearSystem {

    private static double REGRESS_POWER = 0.2;
    private static double RAMP_POWER_CUTOFF = 0.3;
    private static double CALIBRATION_POWER = 0.3;
    private static int LIMIT_SENSOR_SIZE_TICKS = 20;

    private int maxEncoderTicks; // the distance between the limit Sensors (bet

    private int zero;

    private int currentPosition;
    private LimitSwitch maxLimitSensor;
    private LimitSwitch minLimitSensor;
    private DriveMotor dcMotor;


    public LinearEncoderSystem(IBaseOpMode opMode, String systemName, int maxTicks, DcMotor dcMotor,
                               LimitSwitch maxLimitSensor, LimitSwitch minLimitSensor) {
        super(opMode, systemName);

        this.maxEncoderTicks = maxTicks;
        this.dcMotor = new DriveMotor(dcMotor);
        this.maxLimitSensor = maxLimitSensor;
        this.minLimitSensor = minLimitSensor;

        initializeMotor();
        initializeTelemetry();
    }

    // May need to be overridden
    public void initializeMotor() {
        dcMotor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        dcMotor.setMode(DcMotor.RunMode.RUN_TO_POSITION);
        dcMotor.setDirection(DcMotorSimple.Direction.FORWARD);
        calibrateSystem();
    }

    private void initializeTelemetry() {
        log.info("Current Position:         ", currentPosition);
        log.info("Zero:                     ", currentPosition);
        log.info("Target position:          ", dcMotor.getTargetPosition());
        log.info("dcMotor current position: ", dcMotor.getCurrentPosition());
    }

    private int updateCurrentPosition(int startPosition) {
        return (dcMotor.getCurrentPosition() - zero) - startPosition;
    }

    public void goToPosition(double targetPosition, double power) {
        int startPosition = dcMotor.getCurrentPosition();
        int driveTicks = (int) ((targetPosition * maxEncoderTicks) - currentPosition);
        if (targetPosition == 0) {
            dcMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            dcMotor.run(-power);
            checkForBounds();
        } else if (targetPosition == 1) {
            dcMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            dcMotor.run(power);
            checkForBounds();
        } else {
            dcMotor.setMode(DcMotor.RunMode.RUN_TO_POSITION);
            dcMotor.setTargetPosition(driveTicks);

            Ramp ramp = new ExponentialRamp(new Point(0, RAMP_POWER_CUTOFF), new Point(targetPosition, power));

            while (dcMotor.isBusy()) {
                updateCurrentPosition(startPosition);

                // ramp assumes the distance away from the target is positive,
                // so we make it positive here and account for the direction when
                // the motor power is set.
                double direction = 1.0;
                if (driveTicks < 0)
                {
                    driveTicks = -driveTicks;
                    direction = -1.0;
                }

                double scaledPower = ramp.scaleX(driveTicks);

                dcMotor.run(direction * scaledPower);
                checkForBounds();
            }
            dcMotor.run(0);
        }
    }

    public void goToPosition(int targetPosition, double power) {
        if (targetPosition > positions.length) {
            throw new IllegalArgumentException("Target postition (" + targetPosition +
                    ") is beyond range of positions (" + positions.length + ")");
        }
        goToPosition(positions[targetPosition], power);
    }

    // Should be overwritten if the system does not always begin at the minimum of its range
    public void calibrateSystem() {
        goToPosition(0.0, CALIBRATION_POWER);
    }

    private void checkForBounds() {
        if (maxLimitSensor.isPressed() || currentPosition >= maxEncoderTicks) {
            dcMotor.run(0);
            regressFromLimitSensor();
        } else if (minLimitSensor.isPressed() || currentPosition <= 0) {
            dcMotor.run(0);
            regressFromLimitSensor();
        }
    }

    private void regressFromLimitSensor() {
        boolean topTriggered = false;
        dcMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        int startPosition = dcMotor.getCurrentPosition();
        while (minLimitSensor.isPressed() || maxLimitSensor.isPressed()) {
            if (maxLimitSensor.isPressed()) {
                topTriggered = true;
                dcMotor.run(REGRESS_POWER);
            } else if (minLimitSensor.isPressed()) {
                topTriggered = false;
                dcMotor.run(-REGRESS_POWER);
            }
            updateCurrentPosition(startPosition);
        }

        dcMotor.run(0);
        if (topTriggered) {
            zero = dcMotor.getCurrentPosition() - maxEncoderTicks;
            currentPosition = maxEncoderTicks;
        } else {
            currentPosition = 0;
            zero = dcMotor.getCurrentPosition();
            dcMotor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        }
    }
}