package com.wonderful.ishow.camera;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.wonderful.ishow.R;
import com.wonderful.ishow.makeup.MultiThreading;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;

import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;

public class CameraActivity extends Activity {

    private static String TAG = CameraActivity.class.getSimpleName();

    private Camera mCamera;
    private CameraPreview mPreview;
    private ImageView imageView;

    private MultiThreading multiThreading;

    private int smoother = 0;

    private PrivateHandler privateHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera_activity);

        privateHandler = new PrivateHandler(this);
        multiThreading = new MultiThreading(10);
        imageView = findViewById(R.id.processed_image);

        if (!checkCameraHardware(this)) {
            return;
        }

        // Create an instance of Camera
        mCamera = getCameraInstance();
        if (mCamera == null) {
            Log.v(TAG, "Null");
        }


        // Create our Preview view and set it as the content of our activity.
        mPreview = new CameraPreview(this, mCamera);
        final FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
        preview.addView(mPreview);

        // Add a listener to the Capture button
        Button captureButton = (Button) findViewById(R.id.button_capture);
        captureButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // get an image from the camera
                        mCamera.takePicture(null, null, mPicture);
                    }
                }
        );

        mCamera.setPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(final byte[] data, final Camera camera) {

                smoother++;
                if (!(smoother % 5 == 0)) {
                    return;
                }

                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {

                        /*
                        int pic_size = mCamera.getParameters().getPreviewSize().height * mCamera.getParameters().getPreviewSize().width;
                        byte[] pic = new byte[pic_size];

                        for (int i = 0; i < pic_size; i++) {
                            pic[i] = data[i];
                        }
                        */

                        Camera.Parameters parameters = camera.getParameters();
                        int width = parameters.getPreviewSize().width;
                        int height = parameters.getPreviewSize().height;
                        ByteArrayOutputStream outstr = new ByteArrayOutputStream();
                        Rect rect = new Rect(0, 0, width, height);
                        YuvImage yuvimage = new YuvImage(data, ImageFormat.NV21, width, height, null);
                        yuvimage.compressToJpeg(rect, 100, outstr);
                        Bitmap bmp = BitmapFactory.decodeByteArray(outstr.toByteArray(), 0, outstr.size());

                        Matrix matrix = new Matrix();
                        matrix.postRotate(90);

                        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bmp, width, height, true);
                        Bitmap rotatedBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix, true);
                        Bitmap finalBitmap = toGrayscale(Bitmap.createScaledBitmap(rotatedBitmap, width, height, true));

                        /*
                        int pic_size = mCamera.getParameters().getPreviewSize().height * mCamera.getParameters().getPreviewSize().width;
                        byte[] pic = new byte[pic_size];

                        for (int i = 0; i < pic_size; i++) {
                            pic[i] = data[i];
                        }


                        Bitmap picframe = BitmapFactory.decodeByteArray(data, 0, data.length);
                        */

                        Message message = new Message();
                        message.obj = finalBitmap;
                        message.what = 1;
                        privateHandler.sendMessage(message);

                    }
                });

                multiThreading.exec(thread);

                /*
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inMutable = true;
                Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                */

            }
        });
    }

    public Bitmap toGrayscale(Bitmap bmpOriginal) {
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();

        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, 0, 0, paint);
        return bmpGrayscale;
    }


    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.v(TAG, CameraActivity.this.saveAndGetUri(data).toString());
            camera.startPreview();
        }
    };


    public Uri saveAndGetUri(Bitmap processedFrame) {

        File pictureFile = MediaUtil.getOutputMediaFile(MEDIA_TYPE_IMAGE);
        if (pictureFile == null) {
            Log.d(TAG, "Error creating media file, check storage permissions");
            return null;
        }

        try {
            FileOutputStream fos = new FileOutputStream(pictureFile);
            fos.write(getByte(processedFrame));
            fos.close();
            Log.d(TAG, "Captured " + pictureFile.getPath());
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.d(TAG, "Error accessing file: " + e.getMessage());
        }

        return Uri.fromFile(pictureFile);

    }

    public Uri saveAndGetUri(byte[] byteArray) {

        File pictureFile = MediaUtil.getOutputMediaFile(MEDIA_TYPE_IMAGE);
        if (pictureFile == null) {
            Log.d(TAG, "Error creating media file, check storage permissions");
            return null;
        }

        try {
            FileOutputStream fos = new FileOutputStream(pictureFile);
            fos.write(byteArray);
            fos.close();
            Log.d(TAG, "Captured " + pictureFile.getPath());
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.d(TAG, "Error accessing file: " + e.getMessage());
        }

        return Uri.fromFile(pictureFile);
    }

    private byte[] getByte(Bitmap bmp) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] byteArray = stream.toByteArray();
        //bmp.recycle();
        return byteArray;
    }


    /**
     * A safe way to get an instance of the Camera object.
     */
    public static Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        } catch (Exception e) {
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }


    /**
     * Check if this device has a camera
     */
    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }


    private static class PrivateHandler extends Handler {

        WeakReference<CameraActivity> cameraActivityWeakReference;

        public PrivateHandler(CameraActivity cameraActivity) {
            this.cameraActivityWeakReference = new WeakReference<>(cameraActivity);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            Bitmap bitmap = (Bitmap) msg.obj;
            Log.v(TAG, cameraActivityWeakReference.get().saveAndGetUri(bitmap).toString());
            cameraActivityWeakReference.get().imageView.setImageBitmap(bitmap);
        }
    }

}