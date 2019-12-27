package qiao.ocr.ocridcard;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;

import com.bumptech.glide.Glide;

import androidx.appcompat.app.AppCompatActivity;
import butterknife.BindView;
import butterknife.ButterKnife;
import qiao.ocr.ocrid.CameraActivity;
import qiao.ocr.ocrid.utils.AssestUtils;
import qiao.ocr.ocridcard.weidget.CustomRoundAngleImageView;

import static android.text.TextUtils.isEmpty;

/**
 * @author wangqiao
 */
public class MainActivity extends AppCompatActivity {
    @BindView(R.id.bt_closs)
    Button bt_closs;
    @BindView(R.id.trans_imageview)
    CustomRoundAngleImageView imageview;
    @BindView(R.id.write_et)
    EditText write_et;

    private String bitmap;
    private String IDCARD;
    private static final int REQUEST_CODE_PICK_IMAGE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        new AssestUtils(this).init();
        init();
    }

    public void init() {
        bt_closs.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, CameraActivity.class);
            startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_IMAGE) {
            if (data != null) {
                bitmap = data.getStringExtra("bitmapPath");
                IDCARD = data.getStringExtra("IDCARD");
                Log.d("=getPath===", bitmap);
                if (!isEmpty(bitmap) && !isEmpty(IDCARD)) {
                    Glide.with(getApplicationContext()).load(bitmap).into(imageview);
                    write_et.setText(IDCARD);
                }

            }
        }
    }

}
