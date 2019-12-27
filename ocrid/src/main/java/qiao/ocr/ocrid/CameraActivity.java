package qiao.ocr.ocrid;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableOnSubscribe;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import qiao.ocr.ocrid.utils.CheckUtils;
import qiao.ocr.ocrid.view.CameraManager;


public class CameraActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private SurfaceView sfv;
    private CameraManager cameraManager;
    private static final String TAG = "CameraActivity";
    private boolean hasSurface;
    static final String TESSBASE_PATH = Environment.getExternalStorageDirectory()+ "/";
    //识别语言英文
    static final String DEFAULT_LANGUAGE = "eng";
    private ImageView iv_result, lightButton;
    private MediaPlayer mediaPlayer;
    private boolean playBeep;
    private static final float BEEP_VOLUME = 0.10f;
    private boolean vibrate;
    private static final long VIBRATE_DURATION = 200L;
    private static final int REQUEST_CODE_PICK_IMAGE = 100;
    private boolean isok = true;
    /**
     * 身份证，银行卡，等裁剪用的遮罩
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }
        setContentView(R.layout.activity_camera);
        try {
            initView();
        } catch (IOException e) {
            e.printStackTrace();
        }

        playBeep = true;
        AudioManager audioService = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioService.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
            playBeep = false;
        }
        initBeepSound();
        vibrate = true;

    }

    private void initView() throws IOException {
        sfv = findViewById(R.id.sfv);
        SurfaceHolder surfaceHolder = sfv.getHolder();
        iv_result =  findViewById(R.id.iv_result);
        lightButton = findViewById(R.id.light_button);
        lightButton.setOnClickListener(lightButtonOnClickListener);
        if (hasSurface) {
            // activity在paused时但不会stopped,因此surface仍旧存在；
            // surfaceCreated()不会调用，因此在这里初始化camera
            initCamera(surfaceHolder);
        } else {
            // 重置callback，等待surfaceCreated()来初始化camera
            surfaceHolder.addCallback(this);
        }

        findViewById(R.id.close_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }

    boolean isLight = false;
    private View.OnClickListener lightButtonOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (!isLight) {
                isLight = true;
                cameraManager.openLight();
                lightButton.setImageResource(R.drawable.bd_ocr_light_on);
            } else {
                isLight = false;
                cameraManager.offLight();
                lightButton.setImageResource(R.drawable.bd_ocr_light_off);
            }
        }
    };


    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraManager.stopPreview();
        cameraManager.closeDriver();
        SurfaceHolder surfaceHolder = sfv.getHolder();
        surfaceHolder.removeCallback(this);
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            initCamera(holder);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initCamera(SurfaceHolder holder) throws IOException {
        cameraManager = new CameraManager();
        if (holder == null) {
            throw new IllegalStateException("No SurfaceHolder provided");
        }
        if (cameraManager.isOpen()) {
            return;
        }
        try {
            // 打开Camera硬件设备
            cameraManager.openDriver(holder, this);
            // 创建一个handler来打开预览，并抛出一个运行时异常
            cameraManager.startPreview(this);
        } catch (Exception ioe) {
            Log.d("zk", ioe.toString());

        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;
    }

    @SuppressLint("CheckResult")
    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        camera.addCallbackBuffer(data);
        ByteArrayOutputStream baos;
        byte[] rawImage;
        Bitmap bitmap;
        Camera.Size previewSize = camera.getParameters().getPreviewSize();//获取尺寸,格式转换的时候要用到
        BitmapFactory.Options newOpts = new BitmapFactory.Options();
        newOpts.inJustDecodeBounds = true;
        YuvImage yuvimage = new YuvImage(
                data,
                ImageFormat.NV21,
                previewSize.width,
                previewSize.height,
                null);
        baos = new ByteArrayOutputStream();
        yuvimage.compressToJpeg(new Rect(0, 0, previewSize.width, previewSize.height), 100, baos);// 80--JPG图片的质量[0-100],100最高
        rawImage = baos.toByteArray();
        //将rawImage转换成bitmap
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        bitmap = BitmapFactory.decodeByteArray(rawImage, 0, rawImage.length, options);
        if (bitmap == null) {
            return;
        } else {
            int height = bitmap.getHeight();
            int width = bitmap.getWidth();
            final Bitmap bitmap1 = Bitmap.createBitmap(bitmap, width / 2 - dip2px(150), height / 2 - dip2px(92), dip2px(308), dip2px(190));
            int x, y, w, h;
            x = (int) (bitmap1.getWidth() * 0.340);
            y = (int) (bitmap1.getHeight() * 0.800);
            w = (int) (bitmap1.getWidth() * 0.6 + 0.5f);
            h = (int) (bitmap1.getHeight() * 0.12 + 0.5f);
            final Bitmap bit_hm = Bitmap.createBitmap(bitmap1, x, y, w, h);
            if (bit_hm != null) {

//                iv_result.setImageBitmap(bit_hm);

            Observable.create((ObservableOnSubscribe<String>) subscribe -> {
                    subscribe.onNext(localre(bit_hm));
                }).subscribeOn(Schedulers.io()) //事件发送，即图片加载在IO线程
                        .observeOn(Schedulers.single())  //事件处理，显示在UI线程
                        .subscribe(new Observer<String>() {

                            @Override
                            public void onError(Throwable e) {
                            }

                            @Override
                            public void onComplete() {
                            }

                            @Override
                            public void onSubscribe(Disposable d) {
                            }

                            @Override
                            public void onNext(String localre) {
                                Log.d("-------->",localre);
                                if (localre.length() == 18) {
                                    if (CheckUtils.isIDNumber(localre)) {
                                        saveTuPian(bitmap1, localre);
                                    }
                                }
                            }
                        });
            }
        }
    }


    @SuppressLint("MissingPermission")
    public void playBeepSoundAndVibrate() {
        if (playBeep && mediaPlayer != null) {
            mediaPlayer.start();
        }
        if (vibrate) {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vibrator.vibrate(VIBRATE_DURATION);
        }
    }

    private void initBeepSound() {
        if (playBeep && mediaPlayer == null) {
            // The volume on STREAM_SYSTEM is not adjustable, and users found it
            // too loud,
            // so we now play on the music stream.
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            mediaPlayer.setOnCompletionListener(beepListener);

            AssetFileDescriptor file = getResources().openRawResourceFd(R.raw.beep);
            try {
                mediaPlayer.setDataSource(file.getFileDescriptor(),
                        file.getStartOffset(), file.getLength());
                file.close();
                mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
                mediaPlayer.prepare();
            } catch (IOException e) {
                mediaPlayer = null;
            }
        }
    }

    /**
     * When the beep has finished playing, rewind to queue up another one.
     */
    private final MediaPlayer.OnCompletionListener beepListener = mediaPlayer -> mediaPlayer.seekTo(0);


    private String localre(Bitmap bm) {
        String content = "";
        bm = bm.copy(Bitmap.Config.ARGB_8888, true);
        TessBaseAPI baseApi = new TessBaseAPI();
        baseApi.init(TESSBASE_PATH, DEFAULT_LANGUAGE);
        //设置识别模式
        baseApi.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_LINE);
        //设置要识别的图片
        baseApi.setImage(bm);
        baseApi.setVariable("tessedit_char_whitelist", "0123456789Xx");
        Log.e(TAG, "localre: " + baseApi.getUTF8Text());
        content = baseApi.getUTF8Text();
        baseApi.clear();
        baseApi.end();
        return content;
    }


    private void saveTuPian(Bitmap bitmap, String idcard) {// 保存图片

        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/youge/IMG/OCR-";
        File dirF = new File(path);
        if (!dirF.exists()) {
            dirF.mkdirs();
        }
        File file = new File(path + System.currentTimeMillis() + ".jpg");

        try {
            FileOutputStream outputStream = new FileOutputStream(file);
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);

            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, bufferedOutputStream);// 图片格式、品质（0-100），输出流
            // 30：
            // 图片压缩比例
            bufferedOutputStream.flush();
            bufferedOutputStream.close();
            if (bitmap != null) {
                try {
                    playBeepSoundAndVibrate();
                    Intent intent = new Intent();
                    intent.putExtra("IDCARD", idcard);
                    intent.putExtra("bitmapPath", file.getPath());
                    Log.d("=getPath===", file.getPath());
                    setResult(REQUEST_CODE_PICK_IMAGE, intent);
                    finish();
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } // 上传照片
            }
        } catch (Exception e) {
            Toast.makeText(this, "请从新识别！", Toast.LENGTH_SHORT).show();
        }

    }


    public int dip2px(int dp) {
        float density = this.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5);
    }


}
