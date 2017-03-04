package com.example.baddriverreporter;


import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import ru.yandex.speechkit.Error;
import ru.yandex.speechkit.PhraseSpotter;
import ru.yandex.speechkit.PhraseSpotterListener;
import ru.yandex.speechkit.PhraseSpotterModel;
import ru.yandex.speechkit.SpeechKit;

import static android.Manifest.permission.RECORD_AUDIO;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;


/**
 * A simple {@link Fragment} subclass.
 */
public class PhraseSpotterFragment extends Fragment implements PhraseSpotterListener,SurfaceHolder.Callback,View.OnClickListener {

    public SurfaceView mPreview;//<uses-feature android:name="android.hardware.camera" />
    public Button btnStart;
    public Button btnStop;
    public Button btnReport;
    public Button btnEmergency;
    public SurfaceHolder mHolder;
    public Camera mCamera;
    public String mVideoFilename;
    public MediaRecorder mMediaRecorder;
    public boolean isEmergency=false;
    static int n=0;

    private static final String API_KEY_FOR_TESTS_ONLY = "069b6659-984b-4c5f-880e-aaedcfd84102";

    private static final int REQUEST_PERMISSION_CODE = 1;

    private TextView currentStatus;
    public PhraseSpotterFragment() {
        // Required empty public constructor
    }

    public static PhraseSpotterFragment newInstance() {
        return new PhraseSpotterFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SpeechKit.getInstance().configure(getContext(), API_KEY_FOR_TESTS_ONLY);
        // Specify the PhraseSpotter model (check the asset folder for the used one).
        PhraseSpotterModel model = new PhraseSpotterModel("phrase-spotter/commands");
        // Don't forget to load the model.
        Error loadResult = model.load();
        if (loadResult.getCode() != Error.ERROR_OK) {
            updateCurrentStatus("Error occurred during model loading: " + loadResult.getString());
        } else {
            // Set the listener.
            PhraseSpotter.setListener(this);
            // Set the model.
            Error setModelResult = PhraseSpotter.setModel(model);
            handleError(setModelResult);
        }



    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_start: {
                bool=true;
                second=0;
                a=0;
                b=0;
                handler.postDelayed(task, 1000);
                break;
            }
            case R.id.btn_stop: {
                stopRecord();
                bool=false;

                break;
            }
            case R.id.btn_report: {
                stopRecord();
                bool=false;
                isEmergency=false;
                new GetDataFromServer().execute();
                bool=true;
                second=0;
                a=0;
                b=0;
                handler.postDelayed(task, 1000);
                break;
            }

            case R.id.btn_emergency: {
                stopRecord();
                bool=false;
                isEmergency=true;
                new GetDataFromServer().execute();
                bool=true;
                second=0;
                a=0;
                b=0;
                handler.postDelayed(task, 1000);
                break;
            }
        }
    }


    private class GetDataFromServer extends AsyncTask<String, Integer, String> {

        @Override
        protected void onPreExecute(){}

        @Override
        protected String doInBackground(String... params) {

            //this is the JSON for the metadata information
            JSONObject metadata = new JSONObject();
            try {

                metadata.put("duration", "32");
                metadata.put("framesPerSecond", "6");
                if(isEmergency){
                    metadata.put("isImmediateHazard", "1");
                }
                else {
                    metadata.put("isImmediateHazard", "0");
                }
                metadata.put("locationRecorded", "California");
                metadata.put("sizeInMB", "10");
                metadata.put("speedInMPH", "90");
                metadata.put("timeOfRecording", "11/22/2016 23:11:03");
                metadata.put("phoneNumber", "13102617363");

            } catch (JSONException e)
            {
                e.printStackTrace();
            }


            HttpURLConnection conn = null;
            DataOutputStream dos = null;
            String lineEnd = "\r\n";
            String twoHyphens = "--";
            String boundary = "*****";
            int bytesRead, bytesAvailable, bufferSize;
            byte[] buffer;
            int maxBufferSize = 1 * 1024 * 1024;
            Log.d("Rajat, uploading:", mVideoFilename);
            File sourceFile = new File(mVideoFilename);

            if (!sourceFile.isFile()) {

                Log.e("uploadFile", "Source File not exist :" + mVideoFilename);
                return "0";

            }
            else
            {
                try {

                    // open a URL connection to the Servlet
                    FileInputStream fileInputStream = new FileInputStream(sourceFile);
                    URL url = new URL("http://45.55.3.71:9001/uploadVideo");

                    // Open a HTTP  connection to  the URL
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setDoInput(true); // Allow Inputs
                    conn.setDoOutput(true); // Allow Outputs
                    conn.setUseCaches(false); // Don't use a Cached Copy
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Connection", "Keep-Alive");
                    conn.setRequestProperty("ENCTYPE", "multipart/form-data");
                    conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                    dos = new DataOutputStream(conn.getOutputStream());

                    dos.writeBytes(twoHyphens + boundary + lineEnd);
                    dos.writeBytes("Content-Disposition: form-data; name=\"filefield\"; filename=\"" + mVideoFilename + "\"" + lineEnd);
                    dos.writeBytes("Content-Type: video/mp4" + lineEnd);
                    dos.writeBytes(lineEnd);

                    // create a buffer of  maximum size
                    bytesAvailable = fileInputStream.available();

                    bufferSize = Math.min(bytesAvailable, maxBufferSize);
                    buffer = new byte[bufferSize];

                    // read file and write it into form...
                    bytesRead = fileInputStream.read(buffer, 0, bufferSize);

                    while (bytesRead > 0) {

                        dos.write(buffer, 0, bufferSize);
                        bytesAvailable = fileInputStream.available();
                        bufferSize = Math.min(bytesAvailable, maxBufferSize);
                        bytesRead = fileInputStream.read(buffer, 0, bufferSize);

                    }

                    // send multipart form data necessary after file data...
                    dos.writeBytes(lineEnd);
                    dos.writeBytes(twoHyphens + boundary + lineEnd);
                    dos.writeBytes("Content-Disposition: form-data; name=\"metadata\"" + lineEnd);
                    dos.writeBytes("Content-Type: application/json" + lineEnd);
                    dos.writeBytes(lineEnd);
                    dos.writeBytes(String.valueOf(metadata));
                    dos.writeBytes(lineEnd);
                    dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);   //EoF request payload... IMPORTANT
                    dos.flush();
                    dos.close();

                    // Responses from the server (code and message)
                    int serverResponseCode = conn.getResponseCode();
                    String serverResponseMessage = conn.getResponseMessage();

                    Log.i("uploadFile", "HTTP Response is : "
                            + serverResponseMessage + "and " + serverResponseCode);

                    if(serverResponseCode == 200){
                        return "1";
                    }

                    //close the streams //
                    fileInputStream.close();

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                return "1";

            } // End else block
        }

        @Override
        protected void onProgressUpdate(Integer... values){}

        @Override
        protected void onPostExecute(String result) {

            try {

                Log.d("done","uploaded to mongo");

                CharSequence text = "Uploaded successfully" + mVideoFilename;
                int duration = Toast.LENGTH_SHORT;

                Toast toast = Toast.makeText(getActivity(), text, duration);
                toast.show();

            }
            catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_phrase_spotter, container, false);
    }


    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        currentStatus = (TextView) view.findViewById(R.id.current_state);
        mPreview = (SurfaceView)view.findViewById(R.id.preview);
        btnStart = (Button)view.findViewById(R.id.btn_start);
        btnStop = (Button)view.findViewById(R.id.btn_stop);
        btnReport = (Button)view.findViewById(R.id.btn_report);
        btnEmergency = (Button)view.findViewById(R.id.btn_emergency);
        mHolder = mPreview.getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        btnStart.setOnClickListener(this);
        btnStop.setOnClickListener(this);
        btnReport.setOnClickListener(this);
        btnEmergency.setOnClickListener(this);
    }




    @Override
    public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
        // TODO Auto-generated method stub
        Camera.Parameters parameters = mCamera.getParameters();
        mCamera.setParameters(parameters);
        mCamera.stopPreview();
        mCamera.startPreview();

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // TODO Auto-generated method stub
        mCamera = Camera.open();
        mCamera.setDisplayOrientation(90);///fangxiang
        try {
            mCamera.setPreviewDisplay(holder);
        } catch (IOException exception) {
            mCamera.release();
            mCamera = null;
        }

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // TODO Auto-generated method stub
		/*mCamera.stopPreview();
		mCamera.release();
		mCamera = null;*/


    }

    public Toast toast;

    public void showMsg(String arg) {
        if (toast == null) {
            toast = Toast.makeText(getActivity(), arg, Toast.LENGTH_SHORT);
        } else {
            toast.cancel();
            toast.setText(arg);
        }
        toast.show();
    }

    @SuppressLint("SdCardPath")
    public boolean prepareRecord(){
        mMediaRecorder = new MediaRecorder();
        mCamera.unlock();

        mMediaRecorder.setCamera(mCamera);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
//        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
//        mMediaRecorder.setVideoSize(176,144); //176,144
//        mMediaRecorder.setVideoFrameRate(15);
//        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
//        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);


//        mMediaRecorder.setMaxDuration(100000);
          mMediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));
    //mMediaRecorder.setVideoSize(800,480);
          mMediaRecorder.setVideoFrameRate(5);
        //aa=filePath;
        mMediaRecorder.setPreviewDisplay(mPreview.getHolder().getSurface());
        mVideoFilename="/sdcard/CLIP" +n + ".mp4";
        n=(n+1)%6;
        mMediaRecorder.setOutputFile(mVideoFilename);
        //mMediaRecorder.setPreviewDisplay(mPreview.getHolder().getSurface());
        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
    public void stopRecord(){
        if (mMediaRecorder != null) {
            mMediaRecorder.stop();
            releaseMediaRecorder();

        }
        showMsg("OK");
    }

    private void releaseMediaRecorder() {
        // TODO Auto-generated method stub
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    int second=0;
    boolean bool=true;
    int a=0;
    int b=0;
    public Handler handler = new Handler();
    public Runnable task = new Runnable() {

        public void run() {
            if (bool) {
                //handler.postDelayed(this, 1000);
                second=(second+1)%16;

                if(a==0&&b==0){
                    if(prepareRecord()){
                        mMediaRecorder.start();
                    }
                    a=1;
                }
                if(second==0){
                    a=0;
                    b=1;
                    stopRecord();
                    if(prepareRecord()){
                        mMediaRecorder.start();
                    }
                }
                handler.postDelayed(this, 1000);
            }
        }
    };





    @Override
    public void onStart() {
        super.onStart();
        // Don't forget to call start.
        startPhraseSpotter();
    }

    @Override
    public void onStop() {
        super.onStop();
        Error stopResult = PhraseSpotter.stop();
        handleError(stopResult);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode != REQUEST_PERMISSION_CODE) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length == 1 && grantResults[0] == PERMISSION_GRANTED) {
            startPhraseSpotter();
        } else {
            updateCurrentStatus("Record audio permission was not granted");
        }
    }

    private void startPhraseSpotter() {
        final Context context = getContext();
        if (context == null) {
            return;
        }

        if (ContextCompat.checkSelfPermission(context, RECORD_AUDIO) != PERMISSION_GRANTED) {
            requestPermissions(new String[]{RECORD_AUDIO}, REQUEST_PERMISSION_CODE);
        } else {
            Error startResult = PhraseSpotter.start();
            handleError(startResult);
        }
    }

    private void handleError(Error error) {
        if (error.getCode() != Error.ERROR_OK) {
            updateCurrentStatus("Error occurred: " + error.getString());
        }
    }

    @Override
    public void onPhraseSpotted(String s, int i) {


//        if(i==6) {
//            bool=true;
//            second=0;
//            a=0;
//            b=0;
//            handler.postDelayed(task, 1000);
//            showMsg("Started Recording Video");
//        }
//        if(i==7){
//            stopRecord();
//            bool=false;
//            showMsg("Stopped Recording Video");
//        }

        switch (i) {

            case 5: {

                stopRecord();
                bool=false;
                isEmergency=true;
                new GetDataFromServer().execute();
                bool=true;
                second=0;
                a=0;
                b=0;
                handler.postDelayed(task, 1000);
                break;
            }

            case 6: {

                stopRecord();
                bool=false;
                isEmergency=false;
                new GetDataFromServer().execute();
                bool=true;
                second=0;
                a=0;
                b=0;
                handler.postDelayed(task, 1000);
                break;
            }
            case 7: {
                bool=true;
                second=0;
                a=0;
                b=0;
                handler.postDelayed(task, 1000);

                break;
            }
            case 8: {
                stopRecord();
                bool=false;
                break;
            }


        }

    }

    @Override
    public void onPhraseSpotterStarted() {
        updateCurrentStatus("PhraseSpotter started");
    }

    @Override
    public void onPhraseSpotterStopped() {
        updateCurrentStatus("PhraseSpotter stopped");
    }

    @Override
    public void onPhraseSpotterError(Error error) {
        handleError(error);
    }

    private void updateCurrentStatus(String text) {

    }

}
