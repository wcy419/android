package com.example.baddriverreporter.fragments;

/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentCompat;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.example.baddriverreporter.LocationServiceCall;
import com.example.baddriverreporter.PocketSphinxActivity;
import com.example.baddriverreporter.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import static com.example.baddriverreporter.R.id.logo_only;
import static com.example.baddriverreporter.R.id.video;


public class Camera2VideoFragment extends Fragment
        implements View.OnClickListener, FragmentCompat.OnRequestPermissionsResultCallback {

    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();
    Context mContext;

    private static final String TAG = "Camera2VideoFragment";
    private static final int REQUEST_VIDEO_PERMISSIONS = 1;
    private static final int REQUEST_PHONE_PERMISSIONS = 9;
    private static final String FRAGMENT_DIALOG = "dialog";

    private static final String[] VIDEO_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };
    private static final String[] PHONE_PERMISSIONS = {
//            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE
    };

    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }
    //declarations for video buffers
    private int clipDuration = 0;
    private boolean mediaRecorderSetUp = false;
    private int clipNumber;
    private int a = 0, b =0;
    private boolean bool = true;
    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView mTextureView;

    /**
     * Button to record video
     */
    private Button mButtonVideo;

    /**
     * A refernce to the opened {@link android.hardware.camera2.CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * A reference to the current {@link android.hardware.camera2.CameraCaptureSession} for
     * preview.
     */
    private CameraCaptureSession mPreviewSession;

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    //for beep sound notifications on START and STOP recording
    private ToneGenerator toneGen1;

    //required to get locations for file-level metadata
    private LocationServiceCall locationServiceCallObject;

    //required to extract phone number
    private String phoneNumber;

    private TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture,
                                              int width, int height) {
            Log.d(TAG, "texture available to open camera");
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture,
                                                int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }

    };

    /**
     * The {@link android.util.Size} of camera preview.
     */
    private Size mPreviewSize;

    /**
     * The {@link android.util.Size} of video recording.
     */
    private Size mVideoSize;

    /**
     * MediaRecorder
     */
    private MediaRecorder mMediaRecorder;

    /**
     * Whether the app is recording video now
     */
    private boolean mIsRecordingVideo = true;

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    //Accelerometer variables
    SensorManager sManager;
    Sensor accelerometerSensor;
    SensorEventListener acceleromererListener;

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its status.
     */
    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            startPreview();
            mCameraOpenCloseLock.release();
            if (null != mTextureView) {
                configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
            }
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }

    };
    private Integer mSensorOrientation;
    private String mNextVideoAbsolutePath;
    private CaptureRequest.Builder mPreviewBuilder;
    private Surface mRecorderSurface;
    private String mLongitude;
    private String mLatitude;


    public static Camera2VideoFragment newInstance() {
        return new Camera2VideoFragment();
    }

    public void setUpGoogleApiClient(Context context){
        mContext = context;
        locationServiceCallObject = new LocationServiceCall(mContext);

    }

    /**
     * In this sample, we choose a video size with 3x4 aspect ratio. Also, we don't use sizes
     * larger than 1080p, since MediaRecorder cannot handle such a high-resolution video.
     *
     * @param choices The list of available sizes
     * @return The video size
     */
    private static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        Log.e(TAG, "Couldn't find any suitable video size");
        return choices[choices.length - 1];
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<Size>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        try{
            //added by Jingyi 2.22.2017: for Acclerometer sensor
            sManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
            accelerometerSensor = sManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
            acceleromererListener = new SensorEventListener() {
                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {
                }

                @Override
                public void onSensorChanged(SensorEvent event) {

                    if(bool){ //it needs to be true in BDR because auto-upload should be based on recording status
                        float x = event.values[0];
                        float y = event.values[1];
                        float z = event.values[2];

                        double threshold = Math.sqrt(Math.pow(x,2)+Math.pow(y,2)+Math.pow(z,2));

                    /* threshold for collision is about 5g ＝ 49 m/s^2 */
                        if (threshold > 49

                     /* threshold for slamming on brakes
                     From some research, acceleration for this event is said to be small,
                     we should consider this small value but eliminate a false positive
                    with a little higher threshold to trigger wrongly —
                    phone dropped down from someone’s hand with an acceleration about 2.5g,
                     so we need to test for an accuracy range for slamming on brakes. */

                                || (threshold > 12 && threshold < 15 && z > 0)) {
                            stopRecordingVideo();
//                        new GetDataFromServer().execute();
                        }

                    }
                }

            };
            sManager.registerListener(acceleromererListener, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return inflater.inflate(R.layout.fragment_camera2_video, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        mTextureView = (AutoFitTextureView) view.findViewById(R.id.texture);
        mButtonVideo = (Button) view.findViewById(video);
        mButtonVideo.setOnClickListener(this);
        toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        Intent i = new Intent(getActivity(), PocketSphinxActivity.class);
        startActivity(i);
    }

    @Override
    public void onResume() {
        super.onResume();
        locationServiceCallObject.connect();
        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            Log.d(TAG, "resume to open camera");
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        locationServiceCallObject.disconnect();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case video: {
                if (mIsRecordingVideo && mediaRecorderSetUp) { // stop recording video
                    stopRecordingVideo();
                    mIsRecordingVideo = false;
                    mediaRecorderSetUp = false;
                    bool = false;
                    Log.d("RECORDING", "STOPPED");
                } else { // start recoding video
                    clipNumber = 1;
                    clipDuration = 0;
                    a = 0;
                    b = 0;
                    mIsRecordingVideo = true;
                    bool = true;
                    mButtonVideo.setText(R.string.stop);
                    toneGen1.startTone(ToneGenerator.TONE_CDMA_PIP, 150);
                    Toast.makeText(getActivity(), "RECORDING...", Toast.LENGTH_LONG).show();
                    handler.postDelayed(task,1000);
                    Log.d("RECORDING","STARTED");
                    locationServiceCallObject.getLocationUpdates();
                    updateVariables();

                }
                break;
//            case video: {
//                if (mIsRecordingVideo) {
//                    stopRecordingVideo();
//                    Log.d("RECORDING", "STOPPED");
//                } else {
//                    startRecordingVideo();
//                    Log.d("RECORDING","STARTED");
//                }
//                break;
            }
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets whether you should show UI with rationale for requesting permissions.
     *
     * @param permissions The permissions your app wants to request.
     * @return Whether you can show permission rationale UI.
     */
    private boolean shouldShowRequestPermissionRationale(String[] permissions) {
        for (String permission : permissions) {
            if (FragmentCompat.shouldShowRequestPermissionRationale(this, permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Requests permissions needed for recording video.
     */
    private void requestVideoPermissions() {
        if (shouldShowRequestPermissionRationale(VIDEO_PERMISSIONS)) {
            new ConfirmationDialog().show(getActivity().getFragmentManager(), FRAGMENT_DIALOG);
        } else {
            FragmentCompat.requestPermissions(this, VIDEO_PERMISSIONS, REQUEST_VIDEO_PERMISSIONS);
        }
    }

    //permission request for phone number
    public void requestPhoneNumber(){
        if ((ContextCompat.checkSelfPermission(getActivity(),
                Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) || (ContextCompat.checkSelfPermission(getActivity(),
                Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED)){

                ActivityCompat.requestPermissions(getActivity(),
                        PHONE_PERMISSIONS,
                        REQUEST_PHONE_PERMISSIONS);
                Log.d("Phone", "requesting");
            }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult");
        if (requestCode == REQUEST_VIDEO_PERMISSIONS) {
            Log.d("Permissions",permissions.toString());
            if (grantResults.length == VIDEO_PERMISSIONS.length) {
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        ErrorDialog.newInstance(getString(R.string.permission_request))
                                .show(getActivity().getFragmentManager(), FRAGMENT_DIALOG);
                        break;
                    }
                }
            } else {
                ErrorDialog.newInstance(getString(R.string.permission_request))
                        .show(getActivity().getFragmentManager(), FRAGMENT_DIALOG);
            }
        }
        else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private boolean hasPermissionsGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(getActivity(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Tries to open a {@link CameraDevice}. The result is listened by `mStateCallback`.
     */
    private void openCamera(int width, int height) {
        if (!hasPermissionsGranted(VIDEO_PERMISSIONS)) {
            requestVideoPermissions();
            return;
        }
        if (!hasPermissionsGranted(PHONE_PERMISSIONS)) {
            requestPhoneNumber();
            return;
        }
        final Activity activity = getActivity();
        if (null == activity || activity.isFinishing()) {
            return;
        }
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            Log.d(TAG, "tryAcquire");
            if (!mCameraOpenCloseLock.tryAcquire(25000, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            String cameraId = manager.getCameraIdList()[0];

            // Choose the sizes for camera preview and video recording
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
            mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    width, height, mVideoSize);

            int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }
            configureTransform(width, height);
            mMediaRecorder = new MediaRecorder();
            manager.openCamera(cameraId, mStateCallback, null);

        } catch (CameraAccessException e) {
            Toast.makeText(activity, "Cannot access the camera.", Toast.LENGTH_SHORT).show();
            activity.finish();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(getActivity().getFragmentManager(), FRAGMENT_DIALOG);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.");
        }
        catch (SecurityException e){
            Log.e("Security Exception",e.toString());
        }
    }

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            closePreviewSession();
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mMediaRecorder) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.");
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Start the camera preview.
     */
    private void startPreview() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            closePreviewSession();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            Surface previewSurface = new Surface(texture);
            mPreviewBuilder.addTarget(previewSurface);

            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface), new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    mPreviewSession = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    Activity activity = getActivity();
                    if (null != activity) {
                        Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
                    }
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Update the camera preview. {@link #startPreview()} needs to be called in advance.
     */
    private void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        try {
            setUpCaptureRequestBuilder(mPreviewBuilder);
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should not to be called until the camera preview size is determined in
     * openCamera, or until the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    private void setUpMediaRecorder() throws IOException {
        final Activity activity = getActivity();
        if (null == activity) {
            return;
        }
        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
//        if (mNextVideoAbsolutePath == null || mNextVideoAbsolutePath.isEmpty()) {
//            mNextVideoAbsolutePath = getVideoFilePath(getActivity());
//        }
        mMediaRecorder.setOutputFile(mNextVideoAbsolutePath);
        mMediaRecorder.setVideoEncodingBitRate(10000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        switch (mSensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                break;
        }
        if (mNextVideoAbsolutePath == null || mNextVideoAbsolutePath.isEmpty()) {
            mNextVideoAbsolutePath = getVideoFilePath(getActivity(), clipNumber);
        }
        else{
            mNextVideoAbsolutePath = getVideoFilePath(getActivity(), clipNumber);
        }
        File file = new File(mNextVideoAbsolutePath);
        if(file.exists())
            file.delete();
        mMediaRecorder.setOutputFile(mNextVideoAbsolutePath);
        clipNumber = (clipNumber + 1) % 4;
        if(clipNumber == 0)
            clipNumber = 1;
        try {
            mMediaRecorder.prepare();

        }
        catch (IllegalStateException e){
            Log.e("Illegal State " , e.toString());
            mediaRecorderSetUp = false;
        }
        catch (IOException e){
            Log.e("IO EXCEPTION " , e.toString());
            mediaRecorderSetUp = false;
        }
        mediaRecorderSetUp = true;
    }

    private String getVideoFilePath(Context context, int clipNumber) {
        return context.getExternalFilesDir(null).getAbsolutePath() + "/CLIP" + clipNumber + ".mp4";
    }

    private void startRecordingVideo() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            closePreviewSession();
            setUpMediaRecorder();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();

            // Set up Surface for the camera preview
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);

            // Set up Surface for the MediaRecorder
            mRecorderSurface = mMediaRecorder.getSurface();
            surfaces.add(mRecorderSurface);
            mPreviewBuilder.addTarget(mRecorderSurface);

            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mPreviewSession = cameraCaptureSession;
                    updatePreview();
//                    getActivity().runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            // UI
//                            mButtonVideo.setText(R.string.stop);
//                            mIsRecordingVideo = true;
//
//                            // Start recording
//                            mMediaRecorder.start();
//                            toneGen1.startTone(ToneGenerator.TONE_CDMA_PIP, 150);
//                            Toast.makeText(getActivity(), "RECORDING...", Toast.LENGTH_LONG).show();
//                        }
//                    });
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Activity activity = getActivity();
                    if (null != activity) {
                        Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
                    }
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession.close();
            mPreviewSession = null;
        }
    }
    private void releaseMediaRecorder() {
        // TODO Auto-generated method stub
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();
            mMediaRecorder.release();
            // mMediaRecorder = null;
        }
    }
    private void pauseRecordingVideo(){
        mIsRecordingVideo = false;
        System.out.println(mMediaRecorder);
        if(mMediaRecorder!=null) {
            try {
                mMediaRecorder.stop();
            }catch (RuntimeException e){
                Log.e("Runtime",e.toString());
            }
            releaseMediaRecorder();
            Activity activity = getActivity();
            if (null != activity) {
                Toast.makeText(activity, "Video saved: " + mNextVideoAbsolutePath,
                        Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Video saved: " + mNextVideoAbsolutePath);
            }
        }
    }
    private String mVideoFilename;
    private void stopRecordingVideo() {
        // UI
        mIsRecordingVideo = false;
        mButtonVideo.setText(R.string.upload);

        try {
            mPreviewSession.stopRepeating();
            mPreviewSession.abortCaptures();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        // Stop recording
        try {
            mMediaRecorder.stop();
        }catch (RuntimeException e){
            Log.e("Runtime",e.toString());
        }
        releaseMediaRecorder();
        toneGen1.startTone(ToneGenerator.TONE_CDMA_PIP, 150);

        //Uploading to server
        mVideoFilename = mNextVideoAbsolutePath;
        new UploadToServer(getActivity(), R.raw.cert2).execute();
        mNextVideoAbsolutePath = null;
        startPreview();

    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }

    }

    public static class ConfirmationDialog extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.permission_request)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            FragmentCompat.requestPermissions(parent, VIDEO_PERMISSIONS,
                                    REQUEST_VIDEO_PERMISSIONS);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    parent.getActivity().finish();
                                }
                            })
                    .create();
        }

    }
    private Handler handler= new Handler();
    private Runnable task = new Runnable() {
        @Override
        public void run() {
            // UI
            // mButtonVideo.setText(R.string.stop);
            // UI
//            mIsRecordingVideo = true;
//
            // Start recording
//            mMediaRecorder.start();

            if(bool) {
                if (!mIsRecordingVideo)
                    mIsRecordingVideo = true;
//                video buffer
                clipDuration = (clipDuration + 1) % 31;

                System.out.println("Run " + clipDuration);

                if (clipDuration == 0) { // && clipNumber > 1
                    a = 0;
                    b = 1;
//                stopRecordingVideo();
                    pauseRecordingVideo();
                    if (mediaRecorderSetUp) {
                        System.out.println("clip number " + clipNumber);
                        startRecordingVideo();
                        mMediaRecorder.start();
                    }
                }
                if (a == 0 && b == 0) { // When user clicks Record button
//
                    if (!mediaRecorderSetUp) {
                        //System.out.println("a&b=0");
                        startRecordingVideo();
                        mMediaRecorder.start();
                    }
                    a = 1;
                }
                handler.postDelayed(this, 1000);

            }
        }
    };

    public void updateVariables(){
        mLatitude = locationServiceCallObject.getLatitude();
        mLongitude = locationServiceCallObject.getLongitude();
    }

    private class UploadToServer extends AsyncTask<String, Integer, String> {

        Context context;
        int cert;

        public UploadToServer(Context context, int cert)
        {
            this.context = context;
            this.cert = cert;
        }

        @Override
        protected void onPreExecute(){}

        @Override
        protected String doInBackground(String... params) {
            return httpsUploadData(context, cert);
        }

        public String httpsUploadData(Context context, int cert)
        {
            TelephonyManager manager =(TelephonyManager)getContext().getSystemService(Context.TELEPHONY_SERVICE);
            phoneNumber = manager.getLine1Number();
            Log.d("PHONE #",phoneNumber);

            //this is the JSON for the metadata information
            JSONObject metadata = new JSONObject();

            try {
                String format = "MM/dd/yyyy HH:mm:ss";
                final SimpleDateFormat sdf = new SimpleDateFormat(format);
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                String utcTime = sdf.format(new Date());

                metadata.put("duration", "32");
                metadata.put("framesPerSecond", "6");
                metadata.put("isImmediateHazard", "0");
                metadata.put("locationRecorded",mLatitude+","+mLongitude);
                metadata.put("sizeInMB", "10");
                metadata.put("speedInMPH", "90");
                metadata.put("timeOfRecording", utcTime);
                metadata.put("phoneNumber", phoneNumber);

                locationServiceCallObject.removeLocationUpdate();
            } catch (JSONException e)
            {
                e.printStackTrace();
            }


            try {

                // build key store with ca certificate
                KeyStore keyStore = buildKeyStore(context, cert);

                // Create a TrustManager that trusts the CAs in our KeyStore
                String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
                tmf.init(keyStore);

                // Create an SSLContext that uses our TrustManager
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, tmf.getTrustManagers(), null);

                URL url = new URL("https://tatameme.com:9001/uploadVideo");
                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                conn.setSSLSocketFactory(sslContext.getSocketFactory());

                DataOutputStream dos = null;
                String lineEnd = "\r\n";
                String twoHyphens = "--";
                String boundary = "*****";
                int bytesRead, bytesAvailable, bufferSize;
                byte[] buffer;
                int maxBufferSize = 1 * 1024 * 1024;
                File sourceFile = new File(mVideoFilename);

                if (!sourceFile.isFile()) {

                    Log.e("uploadFile", "Source File not exist :" + mVideoFilename);
                    return "0";

                } else {
                    // open a URL connection to the Servlet
                    FileInputStream fileInputStream = new FileInputStream(sourceFile);

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

                    //close the streams
                    fileInputStream.close();
                    if (serverResponseCode == 200) {
                        return "1";
                    } else {
                        return "0";
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            return "0";
        }

        private KeyStore buildKeyStore(Context context, int certRawResId) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
            // init a default key store
            String keyStoreType = KeyStore.getDefaultType();
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(null, null);

            // read and add certificate authority
            Certificate cert = readCert(context, certRawResId);
            keyStore.setCertificateEntry("ca", cert);

            return keyStore;
        }

        private Certificate readCert(Context context, int certResourceId) throws CertificateException, IOException {

            // read certificate resource
            InputStream caInput = context.getResources().openRawResource(certResourceId);

            Certificate ca;
            try {
                // generate a certificate
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                ca = cf.generateCertificate(caInput);
            } finally {
                caInput.close();
            }

            return ca;
        }

        @Override
        protected void onProgressUpdate(Integer... values){}

        @Override
        protected void onPostExecute(String result) {

            try {

                mButtonVideo.setText(R.string.record);
                if (result=="1"){

                    Log.d("SUCCESS","uploaded to mongo");
                    Toast.makeText(getActivity(), "UPLOAD SUCCESSFUL", Toast.LENGTH_LONG).show();
                }
                else{
                    Toast.makeText(getActivity(), "UPLOAD FAILED. TRY AGAIN...", Toast.LENGTH_LONG).show();
                }

            }
            catch (Exception e) {
                e.printStackTrace();
            }

        }
    }
}