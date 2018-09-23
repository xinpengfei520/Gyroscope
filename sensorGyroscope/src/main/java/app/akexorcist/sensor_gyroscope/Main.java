package app.akexorcist.sensor_gyroscope;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.widget.TextView;
import android.widget.Toast;

public class Main extends Activity {

    public static final int LOCK = 1;
    public static final int UNLOCK = 2;
    public static final int DOUBLELOCK = 3;
    private int currentState = -1;

    TextView textX, textY, textZ;
    SensorManager sensorManager;
    Sensor sensor;
    private float x, y, z;
    private Vibrator mVibrator;
    private boolean isUp = true; // 手机是否正面朝上

    private Handler handler = new Handler() {
        public void handleMessage(Message msg) {

            if ((Math.abs(x - 9.8) < 1.0) && (Math.abs(y) < 1.0) && (Math.abs(z) < 1.0) && isUp) {
                currentState = UNLOCK;
            }
//            if ((Math.abs(x) < 1.0) && (Math.abs(y) < 1.0) && (Math.abs(z - 9.8) < 1.0) && !isUp) {
//                currentState = DOUBLELOCK;
//            }
//            if ((Math.abs(x) < 1.0) && (Math.abs(y) < 1.0) && (Math.abs(z - 9.8) < 1.0 && isUp)) {
//                currentState = LOCK;
//            }
            switch (currentState) {
                case LOCK:
                    Toast.makeText(Main.this, "已上锁", Toast.LENGTH_SHORT).show();
                    mVibrator.vibrate(new long[]{1000, 1000, 1000, 1000}, -1);
                    break;
                case UNLOCK:
                    Toast.makeText(Main.this, "已开锁", Toast.LENGTH_SHORT).show();
                    mVibrator.vibrate(new long[]{1000, 1000, 1000, 1000}, -1);
                    break;
                case DOUBLELOCK:
                    Toast.makeText(Main.this, "已反锁", Toast.LENGTH_SHORT).show();
                    mVibrator.vibrate(new long[]{1000, 1000, 1000, 1000}, -1);
                    break;
            }

        }
    };

    public SensorEventListener gyroListener = new SensorEventListener() {

        /**
         * 当传感器事件(数据)发生改变的时候回调
         * @param event
         */
        @Override
        public void onSensorChanged(SensorEvent event) {
            x = event.values[0];
            y = event.values[1];
            z = event.values[2];

            textX.setText("X : " + String.format("%.2f", x) + " m/s^2");
            textY.setText("Y : " + String.format("%.2f", y) + " m/s^2");
            textZ.setText("Z : " + String.format("%.2f", z) + " m/s^2");

            // 判断是否已经做好开锁准备
            if ((Math.abs(x) < 1.0) && (Math.abs(y) < 1.0) && (Math.abs(z - 9.8) < 1.0)) {
                handler.sendEmptyMessageDelayed(1, 1000);
                isUp = z > 0 ? true : false;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
//		sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        mVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        textX = (TextView) findViewById(R.id.textX);
        textY = (TextView) findViewById(R.id.textY);
        textZ = (TextView) findViewById(R.id.textZ);
    }

    public void onResume() {
        super.onResume();
        // 此处可设置传感器的延迟高低
        sensorManager.registerListener(gyroListener, sensor,
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void onStop() {
        super.onStop();
        sensorManager.unregisterListener(gyroListener);
    }

}