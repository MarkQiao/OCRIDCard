/*
 * Copyright (C) 2017 Baidu, Inc. All Rights Reserved.
 */
package qiao.ocr.ocrid.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.io.File;

import androidx.annotation.IntDef;
import androidx.annotation.RequiresApi;
import androidx.core.content.res.ResourcesCompat;
import qiao.ocr.ocrid.R;

@SuppressWarnings("unused")
public class MaskView extends View {

    public static final int MASK_TYPE_NONE = 0;
    public static final int MASK_TYPE_ID_CARD_FRONT = 1;
    public static final int MASK_TYPE_ID_CARD_BACK = 2;
    public static final int MASK_TYPE_BANK_CARD = 11;
    private int BOX_LAYOUT_WIDTH = 308;
    private int BOX_LAYOUT_HEIGHT = 190;
    private int lineAnimColor = Color.GREEN;

    @IntDef({MASK_TYPE_NONE, MASK_TYPE_ID_CARD_FRONT, MASK_TYPE_ID_CARD_BACK, MASK_TYPE_BANK_CARD})
    @interface MaskType {
    }

    public void setLineColor(int lineColor) {
        this.lineColor = lineColor;
    }

    public void setMaskColor(int maskColor) {
        this.maskColor = maskColor;
    }

    private int lineColor = Color.WHITE;
    private int maskType = MASK_TYPE_ID_CARD_FRONT;
    private int maskColor = Color.argb(100, 0, 0, 0);

    private Paint eraser = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Rect frame = new Rect();

    private Drawable locatorDrawable;

    public Rect getFrameRect() {
        if (maskType == MASK_TYPE_NONE) {
            return new Rect(0, 0, getWidth(), getHeight());
        } else {
            return new Rect(frame);
        }
    }

    public Rect getFrameRectExtend() {
        Rect rc = new Rect(frame);
        int widthExtend = (int) ((frame.right - frame.left) * 0.02f);
        int heightExtend = (int) ((frame.bottom - frame.top) * 0.02f);
        rc.left -= widthExtend;
        rc.right += widthExtend;
        rc.top -= heightExtend;
        rc.bottom += heightExtend;
        return rc;
    }

    public void setMaskType(@MaskType int maskType) {
        this.maskType = maskType;
        switch (maskType) {
            case MASK_TYPE_ID_CARD_FRONT:
                locatorDrawable = ResourcesCompat.getDrawable(getResources(),
                        R.drawable.bd_ocr_id_card_locator_front, null);
                break;
            case MASK_TYPE_ID_CARD_BACK:
                locatorDrawable = ResourcesCompat.getDrawable(getResources(),
                        R.drawable.bd_ocr_id_card_locator_back, null);
                break;
            case MASK_TYPE_BANK_CARD:
                break;
            case MASK_TYPE_NONE:
            default:
                break;
        }
        invalidate();
    }


    private String getScanMessage(int status) {
        String message;
        switch (status) {
            case 0:
                message = "";
                break;
            case 2:
                message = "身份证模糊，请重新尝试";
                break;
            case 3:
                message = "身份证反光，请重新尝试";
                break;
            case 4:
                message = "请将身份证前后反转再进行识别";
                break;
            case 5:
                message = "请拿稳镜头和身份证";
                break;
            case 6:
                message = "请将镜头靠近身份证";
                break;
            case 7:
                message = "请确保身份证完整";
                break;
            case 1:
            default:
                message = "请将身份证置于取景框内";
        }


        return message;
    }


    public int getMaskType() {
        return maskType;
    }

    //    public void setOrientation(@CameraView.Orientation int orientation) {
    //    }

    public MaskView(Context context) {
        super(context);
        init();
    }

    public MaskView(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MaskView);
        BOX_LAYOUT_HEIGHT = a.getInt(R.styleable.MaskView_boxHeight, 0);
        BOX_LAYOUT_WIDTH = a.getInt(R.styleable.MaskView_boxWidth, 0);
        lineAnimColor = a.getColor(R.styleable.MaskView_lineAnimColor, Color.parseColor("#34B04C"));
        init();
    }

    public MaskView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        locatorDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.bd_ocr_id_card_locator_front, null);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            frame.left = dip2px((px2dip(w) - BOX_LAYOUT_WIDTH) / 2);
            frame.top = dip2px((px2dip(h) - BOX_LAYOUT_HEIGHT) / 2);
            frame.right = frame.left + dip2px(BOX_LAYOUT_WIDTH);
            frame.bottom = frame.top + dip2px(BOX_LAYOUT_HEIGHT);
            startDelay(0, frame.top + 10, frame.bottom - 10);
        }
    }

    public int dip2px(int dp) {
        float density = this.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5);
    }

    /**
     * 根据手机的分辨率从 px(像素) 的单位 转成为 dp
     */
    public int px2dip(float pxValue) {
        final float scale = getResources().getDisplayMetrics().density;
        return (int) (pxValue / scale + 0.5f);
    }

    int scrollY = 0;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = frame.width();
        int height = frame.height();
        int left = frame.left;
        int top = frame.top;
        int right = frame.right;
        int bottom = frame.bottom;
        canvas.drawColor(maskColor);
        fillRectRound(left, top, right, bottom, 30, 30, false);  //方框
        canvas.drawPath(path, pen);
        canvas.drawPath(path, eraser);

        drawLineAnim(canvas, left, scrollY, right, scrollY);

        locatorDrawable.setBounds(
                (int) (left + 601f / 1006 * width),
                (int) (top + (110f / 632) * height),
                (int) (left + (963f / 1006) * width),
                (int) (top + (476f / 632) * height));

        if (locatorDrawable != null) {
            locatorDrawable.draw(canvas);
        }
    }

    Paint mPaint = new Paint();

    //绘制扫描动画
    private void drawLineAnim(Canvas canvas, float left, float top, float right, float bottom) {
        mPaint.setColor(lineAnimColor);
        mPaint.setStrokeWidth(5);
        canvas.drawLine(left, top, right, bottom, mPaint);
    }

    public void startDelay(long startDelay, float top, float bottom) {
        ValueAnimator animator = ValueAnimator.ofFloat(top, bottom);
        animator.setDuration(750);
        animator.setStartDelay(startDelay);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            scrollY = Double.valueOf(animation.getAnimatedValue().toString()).intValue();
            invalidate();
        });
        animator.start();
    }


    private Path path = new Path();

    private Path fillRectRound(float left, float top, float right, float bottom, float rx, float ry, boolean
            conformToOriginalPost) {
        path.reset();
        if (rx < 0) {
            rx = 0;
        }
        if (ry < 0) {
            ry = 0;
        }
        float width = right - left;
        float height = bottom - top;
        if (rx > width / 2) {
            rx = width / 2;
        }
        if (ry > height / 2) {
            ry = height / 2;
        }
        float widthMinusCorners = (width - (2 * rx));
        float heightMinusCorners = (height - (2 * ry));

        path.moveTo(right, top + ry);
        path.rQuadTo(0, -ry, -rx, -ry);
        path.rLineTo(-widthMinusCorners, 0);
        path.rQuadTo(-rx, 0, -rx, ry);
        path.rLineTo(0, heightMinusCorners);

        if (conformToOriginalPost) {
            path.rLineTo(0, ry);
            path.rLineTo(width, 0);
            path.rLineTo(0, -ry);
        } else {
            path.rQuadTo(0, ry, rx, ry);
            path.rLineTo(widthMinusCorners, 0);
            path.rQuadTo(rx, 0, rx, -ry);
        }

        path.rLineTo(0, -heightMinusCorners);
        path.close();
        return path;
    }

    {
        // 硬件加速不支持，图层混合。
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        pen.setColor(Color.WHITE);
        pen.setStyle(Paint.Style.STROKE);
        pen.setStrokeWidth(6);

        eraser.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    private void capture(File file) {

    }
}
