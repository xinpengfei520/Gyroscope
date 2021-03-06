package com.kircherelectronics.accelerationexplorer.filter;

import android.hardware.SensorManager;

/*
 * Acceleration Explorer
 * Copyright (C) 2013-2015, Kaleb Kircher - Kircher Engineering, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * ImuLaCf stands for inertial movement unit linear acceleration complementary
 * filter. Orientation is added because the filter applies the complementary
 * filter to the Euler angles (orientation) from the gyroscope and
 * acceleration/magnetic sensors, respectively.
 * 
 * The complementary filter is a frequency domain filter. In its strictest
 * sense, the definition of a complementary filter refers to the use of two or
 * more transfer functions, which are mathematical complements of one another.
 * Thus, if the data from one sensor is operated on by G(s), then the data from
 * the other sensor is operated on by I-G(s), and the sum of the transfer
 * functions is I, the identity matrix.
 * 
 * ImuLaCfOrientation attempts to fuse magnetometer, gravity and gyroscope
 * sensors together to produce an accurate measurement of the rotation of the
 * device.
 * 
 * The magnetometer and acceleration sensors are used to determine one of the
 * two orientation estimations of the device. This measurement is subject to the
 * constraint that the device must not be accelerating and hard and soft-iron
 * distortions are not present in the local magnetic field..
 * 
 * The gyroscope is used to determine the second of two orientation estimations
 * of the device. The gyroscope can have a shorter response time and is not
 * effected by linear acceleration or magnetic field distortions, however it
 * experiences drift and has to be compensated periodically by the
 * acceleration/magnetic sensors to remain accurate.
 * 
 * Euler angles and rotation matrices are used to integrate the measurements of
 * the gyroscope and to convert the orientation vector back into a rotation
 * matrix so it can be integrated by the gyroscope. This is not ideal because
 * Euler angles suffer from singularities known as gimbal lock.
 * 
 * The rotation matrix for the magnetic/acceleration sensor is only required to
 * determine the orientation vector, no integrations are required.
 * 
 * The gyroscope provides the angular rotation speeds for all three axes. To
 * find the orientation of the device, the rotation speeds must be integrated
 * over time. This can be accomplished by multiplying the angular speeds by the
 * time intervals between sensor updates. The calculation produces the rotation
 * increment. Integrating these values again produces the absolute orientation
 * of the device. Small errors are produced at each iteration causing the gyro
 * to drift away from the true orientation.
 * 
 * To eliminate both the drift and noise from the orientation, the gyroscope
 * measurements are applied only for orientation changes in short time
 * intervals. The magnetometer/acceleration fusion is used for long time
 * intervals. This is equivalent to low-pass filtering of the accelerometer and
 * magnetic field sensor signals and high-pass filtering of the gyroscope
 * signals.
 * 
 * @author Kaleb
 * @version %I%, %G%
 * http://developer.android.com/reference/android/hardware/SensorEvent.html#values
 * 
 * The gist of this algorithm was written by Paul Lawitzki.
 * http://www.thousand-thoughts.com/2012/03/android-sensor-fusion-tutorial/
 * 
 */

public class ImuLaCfOrientation implements ImuLinearAccelerationInterface
{
	private static final String tag = ImuLaCfOrientation.class.getSimpleName();

	public static final float EPSILON = 0.000000001f;

	// private static final float NS2S = 1.0f / 10000.0f;
	// Nano-second to second conversion
	private static final float NS2S = 1.0f / 1000000000.0f;

	private boolean hasOrientation = false;

	// The coefficient for the filter... 0.5 = means it is averaging the two
	// transfer functions (rotations from the gyroscope and
	// acceleration/magnetic, respectively).
	public float filterCoefficient = 0.5f;

	private float dT = 0;

	private float omegaMagnitude = 0;

	private float thetaOverTwo = 0;
	private float sinThetaOverTwo = 0;
	private float cosThetaOverTwo = 0;

	private float[] components = new float[3];

	// angular speeds from gyro
	private float[] gyroscope = new float[3];

	// rotation matrix from gyro data
	private float[] gyroMatrix = new float[9];

	// orientation angles from gyro matrix
	private float[] gyroOrientation = new float[3];

	// magnetic field vector
	private float[] magnetic = new float[3];

	// accelerometer vector
	private float[] acceleration = new float[3];

	// orientation angles from accel and magnet
	private float[] orientation = new float[3];

	// final orientation angles from sensor fusion
	private float[] fusedOrientation = new float[3];

	// accelerometer and magnetometer based rotation matrix
	private float[] rotationMatrix = new float[9];

	private float[] linearAcceleration = new float[3];

	// copy the new gyro values into the gyro array
	// convert the raw gyro data into a rotation vector
	private float[] deltaVector = new float[4];

	// convert rotation vector into rotation matrix
	private float[] deltaMatrix = new float[9];

	private long timeStamp;

	private boolean initState = false;

	/**
	 * Initialize a singleton instance.
	 *
	 */
	public ImuLaCfOrientation()
	{
		super();

		// The orientation vector for the gyroscope.
		gyroOrientation[0] = 0.0f;
		gyroOrientation[1] = 0.0f;
		gyroOrientation[2] = 0.0f;

		// Initialize gyroMatrix with identity matrix. This is important because
		// we will need to initialize this matrix... either to the orientation
		// of the device relative to earth frame or to the initial local frame.
		gyroMatrix[0] = 1.0f;
		gyroMatrix[1] = 0.0f;
		gyroMatrix[2] = 0.0f;
		gyroMatrix[3] = 0.0f;
		gyroMatrix[4] = 1.0f;
		gyroMatrix[5] = 0.0f;
		gyroMatrix[6] = 0.0f;
		gyroMatrix[7] = 0.0f;
		gyroMatrix[8] = 1.0f;

	}

	/**
	 * Get the linear acceleration of the device. This method can be called
	 * *only* after setAcceleration(), setMagnetic() and getGyroscope() have
	 * been called.
	 * 
	 * @return float[] an array containing the linear acceleration of the device
	 *         where [0] = x, [1] = y and [2] = z with respect to the Android
	 *         coordinate system.
	 */
	public float[] getLinearAcceleration()
	{

		// Fuse the gyroscope and acceleration/magnetic sensor orientations
		// together via complementary filter to produce a new, fused
		// orientation.
		calculateFusedOrientation();

		// values[0]: azimuth, rotation around the Z axis.
		// values[1]: pitch, rotation around the X axis.
		// values[2]: roll, rotation around the Y axis.

		// Find the gravity component of the X-axis
		// = g*-cos(pitch)*sin(roll);
		components[0] = (float) (SensorManager.GRAVITY_EARTH
				* -Math.cos(gyroOrientation[1]) * Math.sin(gyroOrientation[2]));

		// Find the gravity component of the Y-axis
		// = g*-sin(pitch);
		components[1] = (float) (SensorManager.GRAVITY_EARTH * -Math
				.sin(gyroOrientation[1]));

		// Find the gravity component of the Z-axis
		// = g*cos(pitch)*cos(roll);
		components[2] = (float) (SensorManager.GRAVITY_EARTH
				* Math.cos(gyroOrientation[1]) * Math.cos(gyroOrientation[2]));

		// Subtract the gravity component of the signal
		// from the input acceleration signal to get the
		// tilt compensated output.
		linearAcceleration[0] = (this.acceleration[0] - components[0]);
		linearAcceleration[1] = (this.acceleration[1] - components[1]);
		linearAcceleration[2] = (this.acceleration[2] - components[2]);

		return linearAcceleration;
	}

	/**
	 * The acceleration of the device. Presumably from Sensor.TYPE_ACCELERATION.
	 * 
	 * @param acceleration
	 *            The acceleration of the device.
	 */
	public void setAcceleration(float[] acceleration)
	{
		// Get a local copy of the raw magnetic values from the device sensor.
		System.arraycopy(acceleration, 0, this.acceleration, 0,
				acceleration.length);

		// We fuse the orientation of the magnetic and acceleration sensor based
		// on acceleration sensor updates. It could be done when the magnetic
		// sensor updates or when they both have updated if you want to spend
		// the resources to make the checks.
		calculateOrientation();
	}
	
	/**
	 * The complementary filter coefficient, a floating point value between 0-1,
	 * exclusive of 0, inclusive of 1.
	 * 
	 * @param filterCoefficient
	 */
	public void setFilterCoefficient(float filterCoefficient)
	{
		this.filterCoefficient = filterCoefficient;
	}

	/**
	 * Set the gyroscope rotation. Presumably from Sensor.TYPE_GYROSCOPE
	 * 
	 * @param gyroscope
	 *            the rotation of the device.
	 * @param timeStamp
	 *            the time the measurement was taken.
	 */
	public void setGyroscope(float[] gyroscope, long timeStamp)
	{
		// don't start until first accelerometer/magnetometer orientation has
		// been acquired
		if (!hasOrientation)
		{
			return;
		}

		// Initialization of the gyroscope based rotation matrix
		if (!initState)
		{
			gyroMatrix = matrixMultiplication(gyroMatrix, rotationMatrix);
			initState = true;
		}

		// Only integrate when we can measure a delta time, so one iteration
		// must pass to initialize the timeStamp.
		if (this.timeStamp != 0)
		{
			dT = (timeStamp - this.timeStamp) * NS2S;

			System.arraycopy(gyroscope, 0, this.gyroscope, 0, 3);
			getRotationVectorFromGyro(dT);

			// Get the rotation matrix from the gyroscope
			SensorManager.getRotationMatrixFromVector(deltaMatrix, deltaVector);

			// Apply the new rotation interval on the gyroscope based rotation
			// matrix to form a composite rotation matrix. The product of two
			// rotation matricies is a rotation matrix...
			// Multiplication of rotation matrices corresponds to composition of
			// rotations... Which in this case are the rotation matrix from the
			// fused orientation and the rotation matrix from the current
			// gyroscope
			// outputs.
			gyroMatrix = matrixMultiplication(gyroMatrix, deltaMatrix);

			// Get the gyroscope based orientation from the composite rotation
			// matrix. This orientation will be fused via complementary filter
			// with
			// the orientation from the acceleration sensor and magnetic sensor.
			SensorManager.getOrientation(gyroMatrix, gyroOrientation);
		}

		// measurement done, save current time for next interval
		this.timeStamp = timeStamp;
	}

	/**
	 * Set the magnetic field... presumably from Sensorr.TYPE_MAGNETIC_FIELD.
	 * 
	 * @param magnetic
	 *            the magnetic field
	 */
	public void setMagnetic(float[] magnetic)
	{
		// Get a local copy of the raw magnetic values from the device sensor.
		System.arraycopy(magnetic, 0, this.magnetic, 0, magnetic.length);
	}

	/**
	 * Calculates orientation angles from accelerometer and magnetometer output.
	 */
	private void calculateOrientation()
	{
		// To get the orientation vector from the acceleration and magnetic
		// sensors, we let Android do the heavy lifting. This call will
		// automatically compensate for the tilt of the compass and fail if the
		// magnitude of the acceleration is not close to 9.82m/sec^2. You could
		// perform these steps yourself, but in my opinion, this is the best way
		// to do it.
		if (SensorManager.getRotationMatrix(rotationMatrix, null, acceleration,
				magnetic))
		{
			// Now get the orientation vector from the rotation matrix.
			SensorManager.getOrientation(rotationMatrix, orientation);

			hasOrientation = true;
		}
	}

	/**
	 * Calculate the fused orientation. The gist of this algorithm was written
	 * by Paul Lawitzki.
	 * 
	 * http://www.thousand-thoughts.com/2012/03/android-sensor-fusion-tutorial/
	 */
	private void calculateFusedOrientation()
	{
		float oneMinusCoeff = (1.0f - filterCoefficient);

		/*
		 * Fix for 179 <--> -179 transition problem: Check whether one of the
		 * two orientation angles (gyro or accMag) is negative while the other
		 * one is positive. If so, add 360 (2 * math.PI) to the negative value,
		 * perform the sensor fusion, and remove the 360 from the result if it
		 * is greater than 180. This stabilizes the output in
		 * positive-to-negative-transition cases.
		 */

		// azimuth
		if (gyroOrientation[0] < -0.5 * Math.PI && orientation[0] > 0.0)
		{
			fusedOrientation[0] = (float) (filterCoefficient
					* (gyroOrientation[0] + 2.0 * Math.PI) + oneMinusCoeff
					* orientation[0]);
			fusedOrientation[0] -= (fusedOrientation[0] > Math.PI) ? 2.0 * Math.PI
					: 0;
		}
		else if (orientation[0] < -0.5 * Math.PI && gyroOrientation[0] > 0.0)
		{
			fusedOrientation[0] = (float) (filterCoefficient
					* gyroOrientation[0] + oneMinusCoeff
					* (orientation[0] + 2.0 * Math.PI));
			fusedOrientation[0] -= (fusedOrientation[0] > Math.PI) ? 2.0 * Math.PI
					: 0;
		}
		else
		{
			fusedOrientation[0] = filterCoefficient * gyroOrientation[0]
					+ oneMinusCoeff * orientation[0];
		}

		// pitch
		if (gyroOrientation[1] < -0.5 * Math.PI && orientation[1] > 0.0)
		{
			fusedOrientation[1] = (float) (filterCoefficient
					* (gyroOrientation[1] + 2.0 * Math.PI) + oneMinusCoeff
					* orientation[1]);
			fusedOrientation[1] -= (fusedOrientation[1] > Math.PI) ? 2.0 * Math.PI
					: 0;
		}
		else if (orientation[1] < -0.5 * Math.PI && gyroOrientation[1] > 0.0)
		{
			fusedOrientation[1] = (float) (filterCoefficient
					* gyroOrientation[1] + oneMinusCoeff
					* (orientation[1] + 2.0 * Math.PI));
			fusedOrientation[1] -= (fusedOrientation[1] > Math.PI) ? 2.0 * Math.PI
					: 0;
		}
		else
		{
			fusedOrientation[1] = filterCoefficient * gyroOrientation[1]
					+ oneMinusCoeff * orientation[1];
		}

		// roll
		if (gyroOrientation[2] < -0.5 * Math.PI && orientation[2] > 0.0)
		{
			fusedOrientation[2] = (float) (filterCoefficient
					* (gyroOrientation[2] + 2.0 * Math.PI) + oneMinusCoeff
					* orientation[2]);
			fusedOrientation[2] -= (fusedOrientation[2] > Math.PI) ? 2.0 * Math.PI
					: 0;
		}
		else if (orientation[2] < -0.5 * Math.PI && gyroOrientation[2] > 0.0)
		{
			fusedOrientation[2] = (float) (filterCoefficient
					* gyroOrientation[2] + oneMinusCoeff
					* (orientation[2] + 2.0 * Math.PI));
			fusedOrientation[2] -= (fusedOrientation[2] > Math.PI) ? 2.0 * Math.PI
					: 0;
		}
		else
		{
			fusedOrientation[2] = filterCoefficient * gyroOrientation[2]
					+ oneMinusCoeff * orientation[2];
		}

		// overwrite gyro matrix and orientation with fused orientation
		// to comensate gyro drift
		gyroMatrix = getRotationMatrixFromOrientation(fusedOrientation);

		System.arraycopy(fusedOrientation, 0, gyroOrientation, 0, 3);
	}

	/**
	 * Calculates a rotation vector from the gyroscope angular speed values.
	 *
	 * @param timeFactor
	 * http://developer.android
	 *      .com/reference/android/hardware/SensorEvent.html#values
	 */
	private void getRotationVectorFromGyro(float timeFactor)
	{
		// This code is taken from the Android samples/developer reference. It
		// creates a unit quaternion which is then transformed into a rotation
		// matrix before it is integrated. This is not ideal, but it works.

		// Calculate the angular speed of the sample
		omegaMagnitude = (float) Math.sqrt(Math.pow(gyroscope[0], 2)
				+ Math.pow(gyroscope[1], 2) + Math.pow(gyroscope[2], 2));

		// Normalize the rotation vector if it's big enough to get the axis
		if (omegaMagnitude > EPSILON)
		{
			gyroscope[0] /= omegaMagnitude;
			gyroscope[1] /= omegaMagnitude;
			gyroscope[2] /= omegaMagnitude;
		}

		// Integrate around this axis with the angular speed by the timestep
		// in order to get a delta rotation from this sample over the timestep
		// We will convert this axis-angle representation of the delta rotation
		// into a quaternion before turning it into the rotation matrix.
		thetaOverTwo = omegaMagnitude * timeFactor / 2.0f;
		sinThetaOverTwo = (float) Math.sin(thetaOverTwo);
		cosThetaOverTwo = (float) Math.cos(thetaOverTwo);

		deltaVector[0] = sinThetaOverTwo * gyroscope[0];
		deltaVector[1] = sinThetaOverTwo * gyroscope[1];
		deltaVector[2] = sinThetaOverTwo * gyroscope[2];
		deltaVector[3] = cosThetaOverTwo;
	}

	/**
	 * Get the rotation matrix from the current orientation. Android Sensor
	 * Manager does not provide a method to transform the orientation into a
	 * rotation matrix, only the orientation from a rotation matrix. The basic
	 * rotations can be found in Wikipedia with the caveat that the rotations
	 * are *transposed* relative to what is required for this method.
	 * 
	 * @param orientation
	 *            device orientation.
	 * @return The rotation matrix from the orientation.
	 * 
	 * http://en.wikipedia.org/wiki/Rotation_matrix
	 */
	private float[] getRotationMatrixFromOrientation(float[] orientation)
	{
		float[] xM = new float[9];
		float[] yM = new float[9];
		float[] zM = new float[9];

		float sinX = (float) Math.sin(orientation[1]);
		float cosX = (float) Math.cos(orientation[1]);
		float sinY = (float) Math.sin(orientation[2]);
		float cosY = (float) Math.cos(orientation[2]);
		float sinZ = (float) Math.sin(orientation[0]);
		float cosZ = (float) Math.cos(orientation[0]);

		// rotation about x-axis (pitch)
		xM[0] = 1.0f;
		xM[1] = 0.0f;
		xM[2] = 0.0f;
		xM[3] = 0.0f;
		xM[4] = cosX;
		xM[5] = sinX;
		xM[6] = 0.0f;
		xM[7] = -sinX;
		xM[8] = cosX;

		// rotation about y-axis (roll)
		yM[0] = cosY;
		yM[1] = 0.0f;
		yM[2] = sinY;
		yM[3] = 0.0f;
		yM[4] = 1.0f;
		yM[5] = 0.0f;
		yM[6] = -sinY;
		yM[7] = 0.0f;
		yM[8] = cosY;

		// rotation about z-axis (azimuth)
		zM[0] = cosZ;
		zM[1] = sinZ;
		zM[2] = 0.0f;
		zM[3] = -sinZ;
		zM[4] = cosZ;
		zM[5] = 0.0f;
		zM[6] = 0.0f;
		zM[7] = 0.0f;
		zM[8] = 1.0f;

		// Build the composite rotation... rotation order is y, x, z (roll,
		// pitch, azimuth)
		float[] resultMatrix = matrixMultiplication(xM, yM);
		resultMatrix = matrixMultiplication(zM, resultMatrix);
		return resultMatrix;
	}

	/**
	 * Multiply A by B.
	 * 
	 * @param A
	 * @param B
	 * @return A*B
	 */
	private float[] matrixMultiplication(float[] A, float[] B)
	{
		float[] result = new float[9];

		result[0] = A[0] * B[0] + A[1] * B[3] + A[2] * B[6];
		result[1] = A[0] * B[1] + A[1] * B[4] + A[2] * B[7];
		result[2] = A[0] * B[2] + A[1] * B[5] + A[2] * B[8];

		result[3] = A[3] * B[0] + A[4] * B[3] + A[5] * B[6];
		result[4] = A[3] * B[1] + A[4] * B[4] + A[5] * B[7];
		result[5] = A[3] * B[2] + A[4] * B[5] + A[5] * B[8];

		result[6] = A[6] * B[0] + A[7] * B[3] + A[8] * B[6];
		result[7] = A[6] * B[1] + A[7] * B[4] + A[8] * B[7];
		result[8] = A[6] * B[2] + A[7] * B[5] + A[8] * B[8];

		return result;
	}

}
