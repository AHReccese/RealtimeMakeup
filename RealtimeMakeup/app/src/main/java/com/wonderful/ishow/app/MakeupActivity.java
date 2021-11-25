package com.wonderful.ishow.app;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.FloatRange;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.TimingLogger;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Toast;

import com.wonderful.ishow.BuildConfig;
import com.wonderful.ishow.R;
import com.wonderful.ishow.bean.MakeupParams;
import com.wonderful.ishow.makeup.Feature;
import com.wonderful.ishow.makeup.Makeup;
import com.wonderful.ishow.makeup.MultiThreading;
import com.wonderful.ishow.util.BitmapUtils;
import com.wonderful.ishow.util.ColorUtils;
import com.wonderful.ishow.util.Compatibility;
import com.wonderful.ishow.util.MathUtils;
import com.wonderful.ishow.widget.ImageViewTouch;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;

// what is retention = done.

// todo : 1.ThreadHandling
// todo : 2.Classifiers.
// todo : first determine the timingPart -> realtime.
// todo : 80-100 ms


public class MakeupActivity extends BaseActivity implements View.OnClickListener {
    private static final String TAG = "TAG";//MakeupActivity.class.getSimpleName();

    // DO NOT change these values without updating their counterparts in venus/Makeup.h!
    private static final int REGION_EYE_BROW = 0;
    private static final int REGION_EYE_LASH = 1;
    private static final int REGION_EYE_SHADOW = 2;
    private static final int REGION_IRIS = 3;
    private static final int REGION_BLUSH = 4;
    private static final int REGION_NOSE = 5;
    private static final int REGION_LIP = 6;
    private static final int REGION_SKIN = 7;
    private static final int REGION_COUNT = 8;

    //////////////////
    // for handler
    private MakeUpActivityHandler makeUpActivityHandler;
    private static int CHECK_FACE_EXISTENCE = 1;
    private static int FACE_DETECTED = 2;
    private static int FACE_DID_NOT_DETECTED = 3;
    private static int APPLY_COSMETIC = 4;
    private static int APPLY_COSMETIC_DONE = 5;
    //////////////////

    private MultiThreading multiThreading;


    // aspect Oriented annotations.
    @IntDef({
            REGION_EYE_BROW,
            REGION_EYE_LASH,
            REGION_EYE_SHADOW,
            REGION_IRIS,
            REGION_BLUSH,
            REGION_NOSE,
            REGION_LIP,
            REGION_SKIN,
            //REGION_COUNT  // internal usage
    })
    @Retention(RetentionPolicy.SOURCE)
    private @interface Region {

    }

    private static final String EXTRA_IMAGE_PATH = "image_path";

    private ImageViewTouch iv_image;

    ///////////////
    private ProgressBar progressBar;
    ///////////////

    private SeekBar sb_weight;
    private Spinner spinner;

    private LinearLayout ll_styles;

    @Region
    private int region;

    private int region_id = 0;

    // Spinner options
    private static final int OPTION_TEXTURE = 0;
    private static final int OPTION_COLOR = 1;

    private int option = OPTION_TEXTURE;
    private int[] textures;  ///< OPTION_TEXTURE
    private int[] colors;    ///< OPTION_COLOR

    private Makeup makeup;

    // I haven't dug but it seems like the id is ascending with the same prefix.
    // So I can use first and last for indexing the IDs.
    // R.id.xxx R.drawable.xxx id begin with 0x7f
    // [start, end], with end inclusive.

    private int idStart, idStop, idSelected;  // res_selected store color for lips
    private int colorIndex = 0;
    private MakeupParams makeupParams;

    private final LinearLayout.LayoutParams LAYOUT_PARAMS;

    {
        // 120x100 tweak size if necessary.
        LAYOUT_PARAMS = new LinearLayout.LayoutParams(120, 100);

        final int margin = 5;  // pixels
        LAYOUT_PARAMS.setMargins(margin, margin, margin, margin);  // (left, top, right, bottom);
    }

    private final int[][] ROI_COLORS = new int[REGION_COUNT][];

    private final void initRegionColorArray(Resources res) {
        ROI_COLORS[REGION_EYE_BROW] = ColorUtils.obtainColorArray(res, R.array.eye_brow_colors);
        ROI_COLORS[REGION_EYE_LASH] = ColorUtils.obtainColorArray(res, R.array.eye_lash_colors);
        ROI_COLORS[REGION_EYE_SHADOW] = ColorUtils.obtainColorArray(res, R.array.eye_shadow_colors);
        ROI_COLORS[REGION_IRIS] = ColorUtils.obtainColorArray(res, R.array.iris_colors);
        ROI_COLORS[REGION_BLUSH] = ColorUtils.obtainColorArray(res, R.array.blush_colors);
        ROI_COLORS[REGION_LIP] = ColorUtils.obtainColorArray(res, R.array.lip_colors);

        // todo
        //ROI_COLORS[REGION.SKIN      ] = N/A;
    }

    private void selectTexture(@Region int region, int index) {
        switch (region) {
            case REGION_EYE_SHADOW:
                // eye shadow use 3 layers for color blending
                index = R.drawable.eye_shadow_001 + index * 3;
                textures = new int[]{index, index + 1, index + 2};
                break;
            case REGION_IRIS:
                // iris use 2 layers for color blending
                index = R.drawable.iris_000 + index * 2;
                textures = new int[]{index, index + 1};
                break;
            case REGION_LIP:
//			indices = null;  // dispensable, leave it alone.
                break;
            case REGION_EYE_LASH:
                textures = dodgeArrayAllocation(textures, R.drawable.eye_lash_00 + index);
                break;
            case REGION_EYE_BROW:
                textures = dodgeArrayAllocation(textures, R.drawable.eye_brow_mask_00 + index);
                break;
            case REGION_BLUSH:
                textures = dodgeArrayAllocation(textures, index);
                break;
            default:
                throw new UnsupportedOperationException("not implemented yet");
        }
    }

    private static int[] dodgeArrayAllocation(int[] array, int value) {
        if (array != null && array.length == 1)
            array[0] = value;
        else
            array = new int[]{value};

        return array;
    }

    // This listener controls variable textures[].
    private final View.OnClickListener lsn_texture = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            int index = (Integer) v.getTag();
            selectTexture(region, index);
        }
    };

    private void selectColor(@Region int region, int index) {
        final int[] COLORS = ROI_COLORS[region];
        int color = COLORS[index];

        boolean single = true;
        switch (region) {

            case REGION_LIP:
                // TODO experiment, modify alpha or store alpha value in XML file.
                color = (color & 0x00FFFFFF) | (Color.alpha(color) >> 2 << 24);
                //Log.i(TAG, "use color: " + ColorUtils.colorToString(color));
                break;

            case REGION_BLUSH:
                // TODO experiment
                //color = (color & 0x00FFFFFF) | (((int)(amount * 128)) << 24);
                color = (color & 0x00FFFFFF) | (0x80 << 24);
                break;

            case REGION_EYE_SHADOW:
                single = false;  // currently only REGION_EYE_SHADOW use multiple colors.
                colors = new int[]{color, COLORS[index + 1], COLORS[index + 2]};
                break;

            default:
                break;
        }

        if (single)
            colors = dodgeArrayAllocation(colors, color);
    }

    // This listener controls variable colors[].
    private final View.OnClickListener lsn_color = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            int index = (Integer) v.getTag();
            selectColor(region, index);

            // todo what?
            if (sb_weight != null)
                randomProgress(sb_weight);
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.header, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.back:
                onBackPressed();
                return true;
            case R.id.save:
                SettingsActivity.saveImage(this, makeup.getIntermediateImage(), null);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /* package */
    static void start(Context context, String path) {
        Intent intent = new Intent(context, MakeupActivity.class);
        intent.putExtra(EXTRA_IMAGE_PATH, path);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.makeup_activity);

        initView();
        initData();
        initListener();

    }

    private void initView() {

        iv_image = (ImageViewTouch) findViewById(R.id.image);

        progressBar = findViewById(R.id.progress_bar);
        //progressBar.setBackgroundColor(Color.RED);

        sb_weight = (SeekBar) findViewById(R.id.weight);
        ll_styles = (LinearLayout) findViewById(R.id.styles);
        spinner = (Spinner) findViewById(R.id.spinner);

        iv_image.setScrollEnabled(true);

        // only enable this CheckBox in debug mode
        CheckBox debugMark = (CheckBox) findViewById(R.id.mark);
        debugMark.setVisibility(BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
        debugMark.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            Bitmap mark = makeup.markFeaturePoints();
            iv_image.setImageBitmap(isChecked ? mark : makeup.getIntermediateImage());
        });

    }

    private void initData() {

        ////////////////
        makeUpActivityHandler = new MakeUpActivityHandler(this);
        multiThreading = new MultiThreading(10);
        ////////////////

        initRegionColorArray(this.getResources());
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.cosmetic_option, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        //name = String.format("%s/example/example%d.jpg", App.WORKING_DIRECTORY, (int) (Math.random() * 8) + 1);
        Intent intent = getIntent();
        String name = intent.getStringExtra(EXTRA_IMAGE_PATH);
        if (name == null || name.isEmpty()) {
            finish();
            return;
        }

        Log.v(TAG, "image name: " + name);
        Bitmap image = BitmapUtils.decodeFile(name, BitmapUtils.OPTION_RGBA8888); // ?!!

        ImageData imageData = new ImageData(image, name);

        Message message = new Message();
        message.what = CHECK_FACE_EXISTENCE;
        message.obj = imageData;
        makeUpActivityHandler.sendMessage(message);

    }

    private static class MakeUpActivityHandler extends Handler {
        private WeakReference<MakeupActivity> makeupActivityWeakReference;

        public MakeUpActivityHandler(MakeupActivity makeupActivity) {
            this.makeupActivityWeakReference = new WeakReference<>(makeupActivity);
        }

        @Override
        public void handleMessage(Message msg) {

            super.handleMessage(msg);
            int what = msg.what;
            MakeupActivity makeupActivity = makeupActivityWeakReference.get();
            if (what == CHECK_FACE_EXISTENCE) {

                final ImageData imageData = (ImageData) msg.obj;
                makeupActivity.multiThreading.exec(() -> {

                    Bitmap image = imageData.getBitmap();
                    long startTime = System.currentTimeMillis();
                    PointF[] points = Feature.detectFace(makeupActivity, image, imageData.getName());
                    long difference = System.currentTimeMillis() - startTime;
                    // faceDetected.
                    if (points.length > 0) {
                        Log.v(TAG, "face detected");

            /*    try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            */
                        // detected face points
                        DetectionData detectionData = new DetectionData(points, image);
                        Message message = new Message();
                        message.what = FACE_DETECTED;
                        message.obj = detectionData;
                        sendMessage(message);

                    } else {
                        Log.v(TAG, "face did not detected");
                        sendEmptyMessage(FACE_DID_NOT_DETECTED);
                    }


                });

            } else if (what == FACE_DETECTED) {

                DetectionData detectionData = (DetectionData) msg.obj;

                makeupActivity.makeup = new Makeup(detectionData.getImage(), detectionData.getPoints());
                makeupActivity.iv_image.setImageBitmap(detectionData.getImage());
                makeupActivity.progressBar.setVisibility(View.GONE);
                // the state of first time in: Region.LIP & color index 0
                makeupActivity.findViewById(R.id.lip).performClick();

            } else if (what == FACE_DID_NOT_DETECTED) {

                // Toasting nothing detected.
                Toast.makeText(makeupActivity, R.string.no_face_detected, Toast.LENGTH_LONG).show();
                // makeupActivity.finish();

            } else if (what == APPLY_COSMETIC) {

                UpdateCosmeticConfig config = (UpdateCosmeticConfig) msg.obj;

                // todo : 1.ThreadPool Executor. -> Done.
                //      : 2.Thread -> : java -> cpp.
                //      : 3.Thread -> : cpp -> ThreadPool.

                makeupActivity.multiThreading.exec(() -> {

                    long startTime = System.currentTimeMillis();

                    makeupActivity.applyCosmetic(
                            makeupActivity,
                            makeupActivity.makeup,
                            config.getRegion(),
                            config.getTextures(),
                            config.getColors(),
                            config.getAmount());

                    long difference = System.currentTimeMillis() - startTime;
                    Log.v("Time", "ApplyCosmetic: " + difference);

                    Message message = new Message();
                    message.what = APPLY_COSMETIC_DONE;
                    message.obj = config.getTimingLogger();
                    sendMessage(message);


                });


            } else if (what == APPLY_COSMETIC_DONE) {

                TimingLogger logger = (TimingLogger) msg.obj;

                logger.addSplit("applyCosmestic");
                logger.dumpToLog();

                makeupActivity.iv_image.setImageBitmap(makeupActivity.makeup.getIntermediateImage());
                makeupActivity.progressBar.setVisibility(View.GONE);

            }
        }
    }

    private static class ImageData {

        private Bitmap bitmap;
        private String name;

        public ImageData(Bitmap bitmap, String name) {
            this.bitmap = bitmap;
            this.name = name;
        }


        public Bitmap getBitmap() {
            return bitmap;
        }

        public String getName() {
            return name;
        }

        public void setBitmap(Bitmap bitmap) {
            this.bitmap = bitmap;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    private static class DetectionData {
        private PointF[] points;
        private Bitmap image;

        public DetectionData(PointF[] pointFS, Bitmap image) {
            this.points = pointFS;
            this.image = image;
        }

        public Bitmap getImage() {
            return image;
        }

        public PointF[] getPoints() {
            return points;
        }

        public void setImage(Bitmap image) {
            this.image = image;
        }

        public void setPoints(PointF[] points) {
            this.points = points;
        }

    }

    private static class UpdateCosmeticConfig {

        private TimingLogger timingLogger;

        @Region
        private int region;

        private int[] textures;
        private int[] colors;
        private float amount;

        public UpdateCosmeticConfig(@Region int region, int[] textures, int[] colors, float amount, TimingLogger timingLogger) {
            this.region = region;
            this.textures = textures;
            this.colors = colors;
            this.amount = amount;
            this.timingLogger = timingLogger;
        }


        public int getRegion() {
            return region;
        }

        public void setRegion(int region) {
            this.region = region;
        }

        public TimingLogger getTimingLogger() {
            return timingLogger;
        }

        public int[] getTextures() {
            return textures;
        }

        public float getAmount() {
            return amount;
        }

        public void setTextures(int[] textures) {
            this.textures = textures;
        }

        public int[] getColors() {
            return colors;
        }

        public void setColors(int[] colors) {
            this.colors = colors;
        }

        public void setAmount(float amount) {
            this.amount = amount;
        }

        public void setTimingLogger(TimingLogger timingLogger) {
            this.timingLogger = timingLogger;
        }
    }


    private void initListener() {

        findViewById(R.id.reset).setOnClickListener(this);
        findViewById(R.id.blush).setOnClickListener(this);
        findViewById(R.id.eye_lash).setOnClickListener(this);
        findViewById(R.id.eye_shadow).setOnClickListener(this);
        findViewById(R.id.eye_brow).setOnClickListener(this);
        findViewById(R.id.iris).setOnClickListener(this);
        findViewById(R.id.lip).setOnClickListener(this);

        sb_weight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override

            /*
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float amount = (float) progress / seekBar.getMax();

                TimingLogger timings = new TimingLogger(TAG, "makeup");

                applyCosmetic(MakeupActivity.this, makeup, region, textures, colors, amount);

                timings.addSplit("applyCosmestic");
                timings.dumpToLog();

                iv_image.setImageBitmap(makeup.getIntermediateImage());
            }
            */

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

                // cause view to crash.
                //progressBar.setVisibility(View.VISIBLE);

                float amount = (float) seekBar.getProgress() / seekBar.getMax();
                TimingLogger timings = new TimingLogger(TAG, "makeup");

                UpdateCosmeticConfig config = new UpdateCosmeticConfig(region, textures, colors, amount, timings);

                Message message = new Message();
                message.what = APPLY_COSMETIC;
                message.obj = config;
                makeUpActivityHandler.sendMessage(message);

            }
        });

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Lip has only one parameter--color, so do nothing for lip.
                if (option == position || region == REGION_LIP)
                    return;

                option = position;
                updateCosmeticContent(region, option);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // do nothing
            }
        });

        iv_image.setOnTouchListener((View v, MotionEvent event) -> {
            final int action = event.getAction();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    iv_image.setImageBitmap(makeup.getIntermediateImage());
                    break;

                case MotionEvent.ACTION_UP:
                    iv_image.setImageBitmap(makeup.getInputImage());
                    iv_image.performClick();
                    break;
            }

            return true;
        });

    }

    private static void randomProgress(final SeekBar slider) {
        int max = slider.getMax();
        final float low = 0.25F, high = 0.75F;
        int progress = MathUtils.random((int) (max * low), (int) (max * high));
        slider.setProgress(progress);
    }

    private void updateCosmeticContent(@Region int region, int option) {
        ll_styles.removeAllViews();

        if (region == REGION_LIP) {
            Log.v(TAG, "Update Cosmetic content");
            // Special case, Region.LIP has only one option.
            int[] COLORS = ROI_COLORS[REGION_LIP];
//			for(int i = 0; i < COLORS.length; ++i)
            for (int i = idStart; i <= idStop; ++i) {
                final int color = COLORS[i];
                GradientDrawable shape = new GradientDrawable();
                shape.setCornerRadius(12);
                shape.setColor(color);

                final ImageView iv_color = new ImageView(this);
                iv_color.setLayoutParams(LAYOUT_PARAMS);
                iv_color.setImageDrawable(shape);
                iv_color.setTag((Integer) (i));
                ll_styles.addView(iv_color);
                iv_color.setOnClickListener(lsn_color);
            }

        } else if (option == OPTION_TEXTURE) {
            final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

            for (int i = idStart; i <= idStop; ++i) {
                final ImageView style = new ImageView(this);
                style.setImageResource(i);
                style.setScaleType(ImageView.ScaleType.CENTER_CROP);
                style.setTag((Integer) (i - idStart));
                ll_styles.addView(style, params);
                style.setOnClickListener(lsn_texture);
            }

            // default color parameter
            if (colors == null)
                selectColor(region, 0);

        } else if (option == OPTION_COLOR) {

            final Drawable circle = Compatibility.getDrawable(this, R.drawable.circle);
            final int[] COLORS = ROI_COLORS[region];

            for (int i = 0; i < COLORS.length; ++i) {
                Drawable drawable = circle.mutate().getConstantState().newDrawable();
                drawable.setColorFilter(COLORS[i], PorterDuff.Mode.SRC_ATOP);

                final ImageView iv_color = new ImageView(MakeupActivity.this);
                iv_color.setLayoutParams(LAYOUT_PARAMS);
                iv_color.setImageDrawable(drawable);
                iv_color.setTag((Integer) (i));
                ll_styles.addView(iv_color);
                iv_color.setOnClickListener(lsn_color);
            }

            // default texture parameter
            if (textures == null)
                selectTexture(region, 0);
        }
    }

    /**
     * All the makeup stuff in one go, with corresponding parameters.
     *
     * @param context
     * @param makeup   #Makeup
     * @param region   #Region
     * @param textures <ul>
     *                 <li>For {@link #REGION_EYE_BROW} single drawable resource of the cosmetics.</li>
     *                 <li>For {@link #REGION_EYE_LASH} ditto.</li>
     *                 <li>For {@link #REGION_EYE_SHADOW} multiple drawable resources of the cosmetics.
     *                 Note that they are masks loaded with Bitmap.Config.ALPHA_8 parameter.
     *                 </li>
     *                 <li>For {@link #REGION_IRIS} multiple drawable resources of the cosmetics.</li>
     *                 <li>For {@link #REGION_BLUSH}, it's BlushShape index.
     *                 <li>For {@link #REGION_NOSE} </li>
     *                 <li>For {@link #REGION_LIP}, unused, passing <code>null</code> value is OK.</li>
     *                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                <ul>
     * @param colors   Color of the cosmetics. {@link #REGION_EYE_SHADOW} use multiple colors probably,
     *                 since they enhance the face's beauty.
     * @param amount   Blending amount in range [0, 1], 0 being no effect, 1 being fully applied.
     * @see {android.graphics.Bitmap.Config#ALPHA_8}
     */
    public void applyCosmetic(Context context, @NonNull Makeup makeup, @Region int region,
                              int[] textures, @NonNull int[] colors,
                              @FloatRange(from = 0.0D, to = 1.0D) float amount) {

        switch (region) {
            case REGION_LIP:
                Log.v(TAG, "Apply cosmetic lip");
                makeup.applyLip(colors[0], amount);
                break;

            case REGION_BLUSH:
                makeup.applyBlush(textures[0], colors[0], amount);
                break;

            case REGION_EYE_BROW: {
                Bitmap eye_brow = BitmapFactory.decodeResource(context.getResources(), textures[0], BitmapUtils.OPTION_A8);
                makeup.applyBrow(eye_brow, colors[0], amount);
            }
            break;

            case REGION_IRIS: {
                Bitmap iris = BitmapFactory.decodeResource(context.getResources(), textures[0]);
                makeup.applyIris(iris, amount);
            }
            break;

            case REGION_EYE_LASH: {
                Bitmap eye_lash = BitmapFactory.decodeResource(context.getResources(), textures[0], BitmapUtils.OPTION_A8);
                makeup.applyEyeLash(eye_lash, colors[0], amount);
            }
            break;

            case REGION_EYE_SHADOW: {
                final int length = textures.length;
                Bitmap[] mask = new Bitmap[length];
                for (int i = 0; i < length; ++i)
                    mask[i] = BitmapFactory.decodeResource(context.getResources(), textures[i], BitmapUtils.OPTION_A8);

                makeup.applyEyeShadow(mask, colors, amount);
            }
            break;

            default:
                throw new UnsupportedOperationException("not implemented yet");
        }
    }

    @Override
    public void onClick(View view) {
        @Region int lastRegion = region;
        switch (view.getId()) {

            case R.id.reset:
                iv_image.setImageBitmap(makeup.getOutputImage());
                sb_weight.setProgress(0);
                return;

            case R.id.blush:
                region = REGION_BLUSH;
                region_id = R.id.blush;
                idStart = R.drawable.blusher01;
                idStop = R.drawable.blusher01 + Makeup.BLUSH_SHAPE_COUNT - 1;
                break;

            case R.id.eye_lash:
                region = REGION_EYE_LASH;
                region_id = R.id.eye_lash;
                idStart = R.drawable.thumb_eye_lash_00;
                idStop = R.drawable.thumb_eye_lash_09;
                break;

            case R.id.eye_shadow:
                region = REGION_EYE_SHADOW;
                region_id = R.id.eye_shadow;
                idStart = R.drawable.thumb_eye_shadow_00;
                idStop = R.drawable.thumb_eye_shadow_09;
                break;

            case R.id.eye_brow:
                region = REGION_EYE_BROW;
                region_id = R.id.eye_brow;
                idStart = R.drawable.thumb_eye_brow_00;
                idStop = R.drawable.thumb_eye_brow_27;
                break;

            case R.id.iris:
                region = REGION_IRIS;
                region_id = R.id.iris;
                idStart = R.drawable.thumb_iris_00;
                idStop = R.drawable.thumb_iris_08;
                break;

            case R.id.lip:
                region = REGION_LIP;
                region_id = R.id.lip;
                idStart = 0;
                idStop = ROI_COLORS[REGION_LIP].length - 1;
                break;

            default:
                region_id = 0;
                return;
        }

        if (lastRegion != region)
            updateCosmeticContent(region, option);
    }

}
