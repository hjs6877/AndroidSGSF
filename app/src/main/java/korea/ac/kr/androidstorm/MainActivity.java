package korea.ac.kr.androidstorm;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static String mFileName = null;

    private MediaRecorder mRecorder = null;


    private MediaPlayer mPlayer = null;

    private Button btnStartRecord = null;
    private Button btnStopRecord = null;

    private Button btnPlay = null;

    boolean mStartRecording = true;
    boolean mStartPlaying = true;

    TextView txtResultDb;

    private static double mEMA = 0.0;
    private static final double EMA_FILTER = 0.6;


    RecordThread recordThread;
    LocationThread locationThread;

    Socket clientSocket;
    PrintWriter writer;

    private static final String ADDRESS = "192.168.0.2";
    private static final int PORT = 5000;

    private Map<String, Double> locationMap;

    public MainActivity() {
        mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
        mFileName += "/audiorecordtest.3gp";

        locationMap = new HashMap<String, Double>();
    }

    final Runnable updater = new Runnable(){

        public void run(){
            updateDb();
        }
    };
    final Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);

        Log.d("Create", "call onCreate()");
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.d("Resume", "call onResume()");
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mRecorder != null) {
            stopRecording();
        }

        if(recordThread != null){
            recordThread.interrupt();
            recordThread = null;
        }
//        if (mPlayer != null) {
//            mPlayer.release();
//            mPlayer = null;
//        }
    }


    public void onClickStartRecord(View view){
        switch(view.getId()){
            case R.id.btnStartRecord:
                btnStartRecord = (Button) findViewById(R.id.btnStartRecord);
                btnStopRecord = (Button) findViewById(R.id.btnStopRecord);
                /**
                 * 1. 녹음. 시작.
                 * 2. 녹음과 동시에 데시벨을 측정해서 서버로 전송.
                 */
                startRecording();
                if (recordThread == null) {
                    recordThread = new RecordThread();

                    recordThread.start();
                    Log.d("Noise", "start recordThread in onClickStartRecord()");
                }

                if(locationThread == null){
                    locationThread = new LocationThread();
                    locationThread.start();
                    Log.d("Noise", "also start locationThread in onClickStartRecord()");
                }
                btnStartRecord.setEnabled(false);
                btnStopRecord.setEnabled(true);
        }

    }

    public void onClickStopRecord(View view){
        switch(view.getId()){
            case R.id.btnStopRecord:
                btnStopRecord = (Button) findViewById(R.id.btnStopRecord);
                txtResultDb = (TextView) findViewById(R.id.txtResultDb);
                /**
                 * 1. 녹음. 중지.
                 * 2. 녹음을 중지하고, RecordThread 종료.
                 */
                stopRecording();
                if(recordThread.isAlive()){
                    recordThread.interrupt();
                    recordThread = null;
                    Log.d("Noise", "interrupt recordThread in onClickStopRecord()");
                }

                btnStopRecord.setEnabled(false);
                btnStartRecord.setEnabled(true);

        }

    }



    private void startRecording() {
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mRecorder.setOutputFile(mFileName);


        try {
            mRecorder.prepare();

        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "prepare() failed");
        }

        mRecorder.start();
    }

    private void stopRecording() {
        mRecorder.stop();
        mRecorder.release();
        mRecorder = null;

        try {
            if(clientSocket != null){
                clientSocket.close();
            }

            Log.i(TAG, "clientSocket is closed..");
            Toast.makeText(getApplicationContext(), "clientSocket is closed..", Toast.LENGTH_SHORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * 데이터 전송
     */
    public void updateDb(){
        txtResultDb = (TextView) findViewById(R.id.txtResultDb);
        double dB = getAmplitudeEMA();
        double latitude = locationMap.get("latitude") == null ? 0.00 : locationMap.get("latitude") ;
        double longitude = locationMap.get("longitude") == null ? 0.00 : locationMap.get("longitude");

        if(recordThread == null){
            txtResultDb.setText("Here is result..");
        }else{
            txtResultDb.setText(Double.toString(dB) + " dB");
            if(clientSocket != null){
                writer.println(Double.toString(dB) + "," + Double.toString(latitude) + "," + Double.toString(longitude));
                writer.flush();
            }

        }

    }

    public double soundDb(double ampl){
        return  20 * Math.log10(getAmplitudeEMA() / ampl);
    }

    public double getAmplitude() {
        if (mRecorder != null)
            return  (mRecorder.getMaxAmplitude());
        else
            return 0;

    }
    public double getAmplitudeEMA() {
        double amp =  getAmplitude();
        mEMA = EMA_FILTER * amp + (1.0 - EMA_FILTER) * mEMA;
        return mEMA;
    }

    class RecordThread extends Thread {
        public void run(){
            try {
                Log.i(TAG, "clientSocket is trying to connect..");
                clientSocket = new Socket(ADDRESS, PORT);

                if(clientSocket != null){
                    writer = new PrintWriter(clientSocket.getOutputStream());

                    Log.i(TAG, "clientSocket is connected..");
                    Log.i(TAG, "RecordThread is running..");

                    while (recordThread != null) {
                        try {
                            Thread.sleep(500);

                        } catch (InterruptedException e) { };
                        mHandler.post(updater);
                    }
                }else{
                    Log.i(TAG, "clientSocket is not connected..");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }


        }
    }

    class LocationThread extends Thread {
        private LocationManager locationManager;
        private String locationProviderNetwork = LocationManager.NETWORK_PROVIDER;
        private String locationProviderGps = LocationManager.GPS_PROVIDER;

        public void run(){
            startLocationService();
        }

        private void startLocationService() {
            locationManager = (LocationManager) getSystemService(getApplicationContext().LOCATION_SERVICE);

            GPSListener gpsListener = new GPSListener();

            try{
                locationManager.requestLocationUpdates(locationProviderNetwork, 1000, 0, gpsListener);
            }catch (SecurityException ex){
                ex.printStackTrace();
            }

        }

        class GPSListener implements LocationListener {

            public void onLocationChanged(Location location) {

                //capture location data sent by current provider
                Double latitude = location.getLatitude();
                Double longitude = location.getLongitude();

                locationMap.put("latitude", latitude);
                locationMap.put("longitude", longitude);

                String msg = "Latitude : "+ latitude + "\nLongitude:"+ longitude;
//                txtViewLocationInfoOne.setText(msg);

                Log.i("GPSLocationService", msg);

            }

            public void onProviderDisabled(String provider) {
                Toast.makeText(getApplicationContext(), "onProviderDisabled", Toast.LENGTH_LONG).show();
            }

            public void onProviderEnabled(String provider) {
                Toast.makeText(getApplicationContext(), "onProviderEnabled", Toast.LENGTH_LONG).show();
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
                Toast.makeText(getApplicationContext(), "onStatusChanged", Toast.LENGTH_LONG).show();
            }

        }
    }
    /**
    public void onClickPlay(View view){
        switch(view.getId()){
            case R.id.btnPlay:
                btnPlay = (Button) findViewById(R.id.btnPlay);
                onPlay(mStartPlaying);
                if (mStartPlaying) {
                    btnPlay.setText("Stop playing");
                } else {
                    btnPlay.setText("Start playing");
                }
                mStartPlaying = !mStartPlaying;
        }

    }

    private void onPlay(boolean start) {
        if (start) {
            startPlaying();
        } else {
            stopPlaying();
        }
    }

    private void startPlaying() {
        mPlayer = new MediaPlayer();
        try {
            mPlayer.setDataSource(mFileName);
            mPlayer.prepare();
            mPlayer.start();
        } catch (IOException e) {
            Log.e(TAG, "prepare() failed");
        }
    }

    private void stopPlaying() {
        mPlayer.release();
        mPlayer = null;
    }
    */

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
