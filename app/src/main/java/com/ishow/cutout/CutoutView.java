package com.ishow.cutout;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Environment;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.ishow.common.utils.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


/**
 * Created by yuhaiyang on 2017/9/25.
 * 抠图
 */
public class CutoutView extends View {

    private static final String TAG = "CutoutView";

    /**
     * Donw和up之间的有效距离
     * 单位px
     */
    private static final int EFFECTIVE_DISTANCE = 200;
    /**
     * 移动的有效距离
     */
    private static final int EFFECTIVE_MOVE_DISTANCE = 500;
    /**
     * 抠图画笔的透明度
     */
    private static final int TRACK_ALPHA = 100;

    /**
     * 最大放大倍数
     */
    private static final float MAX_ZOOM_SCALE = 2;
    /**
     * 缩放的最小倍数
     */
    private static final float MIN_ZOOM_SCALE = 0.3f;

    /**
     * 动作路径
     */
    private Path mCurrentPath;
    /**
     * 用来计算动作路径
     */
    private PathMeasure mPathMeasure;

    private Paint mPhotoPaint;
    private Paint mActionPaint;
    private Paint mEnlargePaint;
    private Paint mTransparentPaint;

    private PorterDuffXfermode mCutoutPorterMode;
    private PorterDuffXfermode mEnlargePorterMode;
    private PorterDuffXfermode mEraserPorterMode;
    private Bitmap mPhotoBitmap;
    private Bitmap mEnlargeBgBitmap;

    private boolean isActionTrackVisible;
    private boolean isEnlargeVisible;
    /**
     * 是否手势操作过
     */
    private boolean isGestured;
    private int mMode;

    private int mPhotoTop;
    private int mPhotoLeft;
    private int mPhotoWidth;
    private int mPhotoHeight;
    private int mViewWidth;
    private int mViewHeight;
    /**
     * 放大视图
     */
    private int mEnlargeSize;
    /**
     * 多点触控处理
     */
    private int mTouchPoint;
    private float[] mDownPoint;
    private float[] mMovePoint;
    private float[] mUpPoint;
    private float[] mTouchTwoPointCenter;
    private float[] mDownPointOne;
    private float[] mDownPointTwo;
    /**
     * 缩放的比例
     */
    private float mZoomScale;
    private float mZoomLastScale;
    /**
     * 上一次2点之间的距离
     */
    private double mLastTwoPointDistance;
    /**
     * 移动的距离
     */
    private float mTranslateX;
    private float mTranslateY;
    private float mTranslateLastX;
    private float mTranslateLastY;

    private List<CutoutRecord> mCutoutRecordList;
    private CutoutRecord mCurrentRecord;
    private ValueAnimator mZoomAnimator;
    private ValueAnimator mTranslateXAnimator;
    private ValueAnimator mTranslateYAnimator;

    private Matrix mMatrix;
    private RectF mPhotoRectF;

    public CutoutView(Context context) {
        super(context);
        init();
    }

    public CutoutView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CutoutView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        mMatrix = new Matrix();
        mPhotoRectF = new RectF();

        mMode = Mode.CUT_OUT;

        mCutoutRecordList = new ArrayList<>();
        mDownPoint = new float[2];
        mMovePoint = new float[2];
        mUpPoint = new float[2];
        mTouchTwoPointCenter = new float[2];
        mDownPointOne = new float[2];
        mDownPointTwo = new float[2];

        mZoomScale = 1.0f;
        mZoomLastScale = 1.0f;
        mTranslateX = 0f;
        mTranslateY = 0f;

        mPhotoPaint = new Paint();
        mPhotoPaint.setDither(true);
        mPhotoPaint.setAntiAlias(true);
        mPhotoPaint.setStyle(Paint.Style.FILL);

        mZoomAnimator = ValueAnimator.ofFloat();
        mZoomAnimator.setDuration(500);
        mZoomAnimator.addUpdateListener(mZoomAniListener);


        mTranslateXAnimator = ValueAnimator.ofFloat();
        mTranslateXAnimator.setDuration(500);
        mTranslateXAnimator.addUpdateListener(mTranslateXAniListener);

        mTranslateYAnimator = ValueAnimator.ofFloat();
        mTranslateYAnimator.setDuration(500);
        mTranslateYAnimator.addUpdateListener(mTranslateYAniListener);

        // 透明背景
        Bitmap transparentBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.imitate_transparent_bg_piece);
        BitmapShader transparentShader = new BitmapShader(transparentBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        mTransparentPaint = new Paint();
        mTransparentPaint.setDither(true);
        mTransparentPaint.setAntiAlias(true);
        mTransparentPaint.setShader(transparentShader);

        mCurrentPath = new Path();

        mActionPaint = new Paint();
        mActionPaint.setDither(true);
        mActionPaint.setAntiAlias(true);
        mActionPaint.setStyle(Paint.Style.STROKE);
        mActionPaint.setColor(Color.RED);
        mActionPaint.setStrokeWidth(30);
        mActionPaint.setStrokeCap(Paint.Cap.ROUND);

        mEnlargePaint = new Paint();
        mEnlargePaint.setDither(true);
        mEnlargePaint.setAntiAlias(true);
        mEnlargePaint.setStyle(Paint.Style.STROKE);
        mEnlargePaint.setColor(Color.WHITE);
        mEnlargePaint.setStrokeWidth(5);
        mEnlargePaint.setStrokeCap(Paint.Cap.ROUND);

        mPathMeasure = new PathMeasure(mCurrentPath, false);
        mCutoutPorterMode = new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
        mEnlargePorterMode = new PorterDuffXfermode(PorterDuff.Mode.SRC_IN);
        mEraserPorterMode = new PorterDuffXfermode(PorterDuff.Mode.DST_OUT);
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                mTouchPoint = 1;
                isEnlargeVisible = true;
                isGestured = false;

                if (mMode == Mode.CUT_OUT) {
                    onCutoutDown(event);
                } else {
                    onEraserDown(event);
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                mLastTwoPointDistance = getTouchPointDistance(event);
                mTouchPoint += 1;
                isEnlargeVisible = false;
                isGestured = true;

                mDownPointOne[0] = event.getX(0);
                mDownPointOne[1] = event.getY(0);
                mDownPointTwo[0] = event.getX(1);
                mDownPointTwo[1] = event.getY(1);
                break;
            case MotionEvent.ACTION_MOVE:
                mMovePoint[0] = event.getX();
                mMovePoint[1] = event.getY();

                if (mTouchPoint >= 2) {
                    onGestureMove(event);
                } else if (!isGestured) { // 手势操作后不能进行其他操作
                    if (mMode == Mode.CUT_OUT) {
                        onCutoutMove(event);
                    } else {
                        onEraserMove(event);
                    }
                }

                break;
            case MotionEvent.ACTION_POINTER_UP:
                mTouchPoint -= 1;
                if (mTouchPoint <= 1) {
                    onGestureUp(event);
                }
                break;

            case MotionEvent.ACTION_UP:
                mTouchPoint = 0;
                isEnlargeVisible = false;
                // 手势操作后不能进行其他操作
                if (!isGestured) {
                    if (mMode == Mode.CUT_OUT) {
                        onCutoutUp(event);
                    } else {
                        onEraserUp(event);
                    }
                }
                break;

        }
        postInvalidate();
        return true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);


        mViewWidth = w;
        mViewHeight = h;

        mEnlargeSize = w / 4;
        createEnlargeBgBitmap();
        computePhotoInfo();
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mPhotoBitmap == null || mPhotoBitmap.isRecycled()) {
            return;
        }
        canvas.save();

        mMatrix.setTranslate(mTranslateX, mTranslateY);
        mMatrix.preScale(mZoomScale, mZoomScale, mTouchTwoPointCenter[0], mTouchTwoPointCenter[1]);
        canvas.setMatrix(mMatrix);

        /*
         * 画仿透明的背景
         */
        canvas.drawRect(
                mPhotoLeft,
                mPhotoTop,
                mPhotoLeft + mPhotoBitmap.getWidth(),
                mPhotoTop + mPhotoBitmap.getHeight(),
                mTransparentPaint);

        canvas.drawBitmap(mPhotoBitmap, mPhotoLeft, mPhotoTop, mPhotoPaint);

        if (isActionTrackVisible) {
            canvas.drawPath(mCurrentPath, mActionPaint);
        }
        canvas.restore();

        drawEnlarge(canvas);

    }

    private void drawEnlarge(Canvas canvas) {
        if (!isEnlargeVisible || mEnlargeBgBitmap == null) {
            return;
        }
        final int left;
        final float effect = mEnlargeSize * 1.2f;
        if (mMovePoint[0] < effect && mMovePoint[1] < effect) {
            left = getMeasuredWidth() - mEnlargeSize;
        } else {
            left = 0;
        }
        final int x = left + mEnlargeSize / 2;
        final int y = mEnlargeSize / 2;
        final float moveX = x - mMovePoint[0] + mPhotoLeft;
        final float moveY = y - mMovePoint[1] + mPhotoTop;

        canvas.drawRect(
                left,
                0,
                left + mEnlargeSize,
                mEnlargeSize,
                mTransparentPaint);


        Bitmap bitmap = getEnlargeResultBitmap();
        int saveCount = canvas.saveLayer(0, 0, getWidth(), getHeight(), null, Canvas.ALL_SAVE_FLAG);
        mPhotoPaint.setXfermode(null);
        canvas.drawBitmap(mEnlargeBgBitmap, left, 0, mPhotoPaint);
        mPhotoPaint.setXfermode(mEnlargePorterMode);
        canvas.drawBitmap(bitmap, moveX, moveY, mPhotoPaint);
        mPhotoPaint.setXfermode(null);
        canvas.restoreToCount(saveCount);

        canvas.drawRect(left, 0, left + mEnlargeSize, mEnlargeSize, mEnlargePaint);
        canvas.drawCircle(x, y, 15, mEnlargePaint);

        recycleBitmap(bitmap);

    }

    /**
     * 抠图按下操作
     */
    private void onCutoutDown(MotionEvent event) {
        isActionTrackVisible = true;
        mActionPaint.setAlpha(TRACK_ALPHA);
        // 如果之前是closed 就重置
        if (mPathMeasure.getLength() == 0 || mPathMeasure.isClosed()) {
            mDownPoint[0] = event.getX();
            mDownPoint[1] = event.getY();

            mCurrentPath.reset();
            mCurrentPath.moveTo(event.getX(), event.getY());
        } else {
            mCurrentPath.lineTo(event.getX(), event.getY());
        }
        mPathMeasure.setPath(mCurrentPath, false);
    }

    private void onCutoutMove(MotionEvent event) {
        mCurrentPath.lineTo(event.getX(), event.getY());
    }

    private void onCutoutUp(MotionEvent event) {
        mUpPoint[0] = event.getX();
        mUpPoint[1] = event.getY();
        mPathMeasure.setPath(mCurrentPath, false);
        final float actionLength = mPathMeasure.getLength();
        final double distance = getPointDistance(mDownPoint, mUpPoint);
        if (actionLength > EFFECTIVE_MOVE_DISTANCE && distance < EFFECTIVE_DISTANCE) {
            mCurrentPath.close();
            mPathMeasure.setPath(mCurrentPath, false);
            mCurrentRecord.clearPointList();
            new Thread(mResultCutoutRunnable).start();

        } else {
            mPathMeasure.setPath(mCurrentPath, false);
            mPathMeasure.setPath(mCurrentPath, false);
            mCurrentRecord.addCutoutTrack(mPathMeasure.getLength());
            notifyCanBack();
        }
    }

    /**
     * 橡皮擦按下操作
     */
    private void onEraserDown(MotionEvent event) {
        isActionTrackVisible = false;
        mActionPaint.setAlpha(255);
        mCurrentPath.reset();
        mCurrentPath.moveTo(event.getX(), event.getY());
    }

    private void onEraserMove(MotionEvent event) {
        mCurrentPath.lineTo(event.getX(), event.getY());
        mPhotoBitmap = getEraserResultBitmap();
    }

    @SuppressWarnings("UnusedParameters")
    private void onEraserUp(MotionEvent event) {
        mCurrentRecord.addPath(mCurrentPath);
        mCurrentPath = new Path();
        notifyCanBack();
    }

    private void onGestureMove(MotionEvent event) {
        double nowDistance = getTouchPointDistance(event);
        mTouchTwoPointCenter[0] = event.getX(0) + (event.getX(1) - event.getX(0)) / 2;
        mTouchTwoPointCenter[1] = event.getY(0) + (event.getY(1) - event.getY(0)) / 2;

        mTranslateX = mTranslateLastX + Math.min(event.getX(0) - mDownPointOne[0], event.getX(1) - mDownPointTwo[0]);
        mTranslateY = mTranslateLastY + Math.min(event.getY(0) - mDownPointOne[1], event.getY(1) - mDownPointTwo[1]);

        final float scale = (float) (nowDistance / mLastTwoPointDistance);
        mZoomScale = mZoomLastScale + (scale - 1);
        mZoomScale = Math.max(mZoomScale, MIN_ZOOM_SCALE);
    }


    @SuppressWarnings("UnusedParameters")
    private void onGestureUp(MotionEvent event) {
        isGestured = true;
        mZoomLastScale = mZoomScale;
        mTranslateLastX = mTranslateX;
        mTranslateLastY = mTranslateY;

        if (mZoomScale > 1) {
            onGestureZoomin(event);
        } else {
            onGestureZoomOut(event);
        }
    }


    /**
     * 放大
     */
    @SuppressWarnings("UnusedParameters")
    private void onGestureZoomin(MotionEvent event) {
        final float scale;
        if (mZoomScale > MAX_ZOOM_SCALE) {
            scale = MAX_ZOOM_SCALE;
            mZoomAnimator.setFloatValues(mZoomLastScale, MAX_ZOOM_SCALE);
            mZoomAnimator.start();
        } else {
            scale = mZoomScale;
        }

        resetPhotoRectF();
        Matrix matrix = new Matrix();
        matrix.setTranslate(mTranslateX, mTranslateY);
        matrix.preScale(scale, scale, mTouchTwoPointCenter[0], mTouchTwoPointCenter[1]);
        matrix.mapRect(mPhotoRectF);

        final float photoWidth = mPhotoRectF.width();
        final float photoHeight = mPhotoRectF.height();
        if (photoWidth > mViewWidth) {
            // 左移
            if (mPhotoRectF.right < mViewWidth) {
                final float result = mTranslateX + (mViewWidth - mPhotoRectF.right);
                mTranslateXAnimator.setFloatValues(mTranslateX, result);
                mTranslateXAnimator.start();
            } else if (mPhotoRectF.left > 0) {
                final float result = mTranslateX - mPhotoRectF.left;
                mTranslateXAnimator.setFloatValues(mTranslateX, result);
                mTranslateXAnimator.start();
            }
        } else {
                /*
                 * 1. (mViewWidth - viewWidth) / 2      单侧移动的了多少距离
                 * 2. (mPhotoRectF.left - (mViewWidth - viewWidth) / 2    需要移动的距离
                 * 3. mTranslateX - (mPhotoRectF.left - (mViewWidth - viewWidth) / 2) 最终的距离
                 */
            final float result = mTranslateX - (mPhotoRectF.left - (mViewWidth - photoWidth) / 2);
            Log.i(TAG, "onGestureUp: result = " + result);
            mTranslateXAnimator.setFloatValues(mTranslateX, result);
            mTranslateXAnimator.start();
        }

        if (photoHeight > mViewHeight) {
            // 上移
            if (mPhotoRectF.top > 0) {
                final float result = mTranslateY - mPhotoRectF.top;
                mTranslateYAnimator.setFloatValues(mTranslateY, result);
                mTranslateYAnimator.start();
            } else if (mPhotoRectF.bottom < mViewHeight) {
                final float result = mTranslateY + (mViewHeight - mPhotoRectF.bottom);
                mTranslateYAnimator.setFloatValues(mTranslateY, result);
                mTranslateYAnimator.start();
            }
        } else {
                /*
                 * 同X
                 */
            final float result = mTranslateY - (mPhotoRectF.top - (mViewHeight - photoHeight) / 2);
            mTranslateYAnimator.setFloatValues(mTranslateY, result);
            mTranslateYAnimator.start();
        }

    }

    /**
     * 缩小
     */
    @SuppressWarnings("UnusedParameters")
    private void onGestureZoomOut(MotionEvent event) {
        mZoomAnimator.setFloatValues(mZoomLastScale, 1);
        mZoomAnimator.start();
        mTranslateXAnimator.setFloatValues(mTranslateX, 0);
        mTranslateXAnimator.start();
        mTranslateYAnimator.setFloatValues(mTranslateY, 0);
        mTranslateYAnimator.start();
    }

    private Bitmap getEraserResultBitmap() {

        Bitmap bitmap = Bitmap.createBitmap(mPhotoBitmap.getWidth(), mPhotoBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        mPhotoPaint.setXfermode(null);
        canvas.drawBitmap(mPhotoBitmap, 0, 0, mPhotoPaint);
        mPhotoPaint.setXfermode(mEraserPorterMode);
        canvas.drawBitmap(getEraserPathBitmap(), 0, 0, mPhotoPaint);
        mPhotoPaint.setXfermode(null);
        return bitmap;
    }

    private Bitmap getEnlargeResultBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(mPhotoBitmap.getWidth(), mPhotoBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawBitmap(mPhotoBitmap, 0, 0, mPhotoPaint);
        if (mMode == Mode.CUT_OUT) {
            int saveCount = canvas.saveLayer(0, 0, mPhotoBitmap.getWidth(), mPhotoBitmap.getHeight(), null, Canvas.ALL_SAVE_FLAG);
            canvas.translate(-mPhotoLeft, -mPhotoTop);
            canvas.drawPath(mCurrentPath, mActionPaint);
            canvas.restoreToCount(saveCount);
        }
        return bitmap;
    }


    /**
     * 获取路径Bitmap
     */
    private Bitmap getPathBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(mPhotoBitmap.getWidth(), mPhotoBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setMaskFilter(new BlurMaskFilter(100, BlurMaskFilter.Blur.NORMAL));
        canvas.translate(-mPhotoLeft, -mPhotoTop);
        canvas.drawPath(mCurrentPath, paint);
        return bitmap;
    }


    private Bitmap getEraserPathBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(mPhotoBitmap.getWidth(), mPhotoBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.translate(-mPhotoLeft, -mPhotoTop);
        canvas.drawPath(mCurrentPath, mActionPaint);
        return bitmap;
    }


    private Bitmap getEraserPathBitmap(List<Path> pathList) {
        Bitmap bitmap = Bitmap.createBitmap(mPhotoBitmap.getWidth(), mPhotoBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.translate(-mPhotoLeft, -mPhotoTop);
        for (Path path : pathList) {
            canvas.drawPath(path, mActionPaint);
        }
        return bitmap;
    }


    private void createEnlargeBgBitmap() {
        if (mEnlargeBgBitmap != null) {
            mEnlargeBgBitmap.recycle();
            mEnlargeBgBitmap = null;
        }
        Bitmap bitmap = Bitmap.createBitmap(mEnlargeSize, mEnlargeSize, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        canvas.drawRect(
                0,
                0,
                mEnlargeSize,
                mEnlargeSize,
                paint);

        mEnlargeBgBitmap = bitmap;
    }

    /**
     * 获取2点距离
     */
    private double getPointDistance(float[] down, float[] up) {
        final double x = Math.pow(up[0] - down[0], 2);
        final double y = Math.pow(up[1] - down[1], 2);
        return Math.sqrt(x + y);
    }

    /**
     * 获取 距离
     */
    private double getTouchPointDistance(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return Math.sqrt(x * x + y * y);
    }

    /**
     * 计算图片相关信息
     */
    private void computePhotoInfo() {
        // 当是第二张的时候不需要处理
        if (mCutoutRecordList.size() > 1) {
            return;
        }

        if (mPhotoBitmap == null || mViewHeight == 0 || mViewWidth == 0) {
            return;
        }

        final int width = mPhotoBitmap.getWidth();
        final int height = mPhotoBitmap.getHeight();
        final float widthScale = ((float) mViewWidth / width);
        final float heightScale = ((float) mViewHeight / height);

        Matrix matrix = new Matrix();
        final float scale = Math.min(widthScale, heightScale);
        if (widthScale <= heightScale) {
            mPhotoLeft = 0;
            mPhotoTop = (int) ((mViewHeight - height * scale) / 2);
        } else {
            mPhotoLeft = (int) ((mViewWidth - width * scale) / 2);
            mPhotoTop = 0;
        }
        matrix.postScale(scale, scale);

        Bitmap bitmap = mPhotoBitmap;
        mPhotoBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
        mPhotoWidth = mPhotoBitmap.getWidth();
        mPhotoHeight = mPhotoBitmap.getHeight();
    }

    public void setMode(@Mode int mode) {
        if (mMode == mode) {
            return;
        }

        mMode = mode;
        if (mode == Mode.ERASER && mCurrentRecord != null) {
            mCurrentRecord.clearPointList();
            isActionTrackVisible = false;
            notifyCanBack();
            postInvalidate();
        } else if (mode == Mode.CUT_OUT) {
            mCurrentPath = new Path();
            mPathMeasure.setPath(mCurrentPath, false);
        }
    }


    public void setPhoto(String path) {
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            Toast.makeText(getContext(), R.string.file_not_exist, Toast.LENGTH_SHORT).show();
            return;
        }
        CutoutRecord record = new CutoutRecord();
        record.setImagePath(path);

        mCutoutRecordList.clear();
        mCutoutRecordList.add(record);
        mCurrentRecord = record;


        mPhotoBitmap = BitmapFactory.decodeFile(path);
        computePhotoInfo();
        postInvalidate();
    }


    private Runnable mResultCutoutRunnable = new Runnable() {
        @Override
        public void run() {
            notifyShowLoading();

            Bitmap bitmap = Bitmap.createBitmap(mPhotoBitmap.getWidth(), mPhotoBitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Bitmap pathBitmap = getPathBitmap();

            Canvas canvas = new Canvas(bitmap);
            mPhotoPaint.setXfermode(null);
            canvas.drawBitmap(mPhotoBitmap, 0, 0, mPhotoPaint);
            mPhotoPaint.setXfermode(mCutoutPorterMode);
            canvas.drawBitmap(pathBitmap, 0, 0, mPhotoPaint);
            mPhotoPaint.setXfermode(null);

            recycleBitmap(pathBitmap);
            recycleBitmap(mPhotoBitmap);
            mPhotoBitmap = bitmap;

            isActionTrackVisible = false;

            String path = saveResult(false);
            CutoutRecord record = new CutoutRecord();
            record.setImagePath(path);
            mCutoutRecordList.add(record);
            mCurrentRecord = record;

            notifyCanBack();
            notifyDismissLoading();
            postInvalidate();
        }
    };


    /**
     * 回退
     * 备注：
     * 回退分3个步骤分别为
     * 1. 回退抠图时候的路径
     * 2. 回退擦除时候的路径
     * 3. 回退抠图
     */
    public void back() {
        if (mCurrentRecord == null) {
            Log.i(TAG, "back: mCurrentRecord is null");
            return;
        }
        List<Float> cutoutTrackList = mCurrentRecord.getCutoutTrackList();
        List<Path> pathList = mCurrentRecord.getPathList();
        if (!cutoutTrackList.isEmpty()) {
            backCutoutAction(cutoutTrackList);
        } else if (!pathList.isEmpty()) {
            backPath(pathList);
        } else if (!mCutoutRecordList.isEmpty()) {
            backRecord();
        }
    }


    private void backRecord() {
        if (mCutoutRecordList.size() <= 1) {
            return;
        }
        final int size = mCutoutRecordList.size();
        int last = size - 1;
        mCutoutRecordList.remove(last);
        mCurrentRecord = mCutoutRecordList.get(last - 1);
        mPhotoBitmap = BitmapFactory.decodeFile(mCurrentRecord.getImagePath());

        if (size == 2) {
            computePhotoInfo();
            notifyCanBack();
        }
        List<Path> pathList = mCurrentRecord.getPathList();
        if (!pathList.isEmpty()) {
            // 方法默认是回退一个的， 但是现在回退到record 的时候不需要回退这么多
            mActionPaint.setAlpha(255);
            pathList.add(new Path());
            backPath(pathList);
        }

        // 擦除的path 给画上去
        postInvalidate();
    }

    private void backPath(List<Path> pathList) {
        final int size = pathList.size();
        recycleBitmap(mPhotoBitmap);
        mPhotoBitmap = BitmapFactory.decodeFile(mCurrentRecord.getImagePath());
        computePhotoInfo();
        if (size == 1) {
            pathList.clear();
            notifyCanBack();
        } else {
            pathList.remove(pathList.size() - 1);
            Bitmap pathBitmap = getEraserPathBitmap(pathList);
            Bitmap bitmap = Bitmap.createBitmap(mPhotoBitmap.getWidth(), mPhotoBitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            mPhotoPaint.setXfermode(null);
            canvas.drawBitmap(mPhotoBitmap, 0, 0, mPhotoPaint);
            mPhotoPaint.setXfermode(mEraserPorterMode);
            canvas.drawBitmap(pathBitmap, 0, 0, mPhotoPaint);
            mPhotoPaint.setXfermode(null);

            recycleBitmap(mPhotoBitmap);
            recycleBitmap(pathBitmap);
            mPhotoBitmap = bitmap;
        }

        postInvalidate();
    }

    private void backCutoutAction(List<Float> cutoutTrackList) {
        final int size = cutoutTrackList.size();
        if (size == 1) {
            cutoutTrackList.clear();
            mCurrentPath.reset();
            mPathMeasure.setPath(mCurrentPath, false);
            notifyCanBack();
        } else {
            Path path = new Path();
            path.moveTo(mDownPoint[0], mDownPoint[1]);
            float length = cutoutTrackList.get(size - 2);
            mPathMeasure.getSegment(0, length, path, false);
            mCurrentPath = path;
            mPathMeasure.setPath(mCurrentPath, false);
            cutoutTrackList.remove(size - 1);
        }
        postInvalidate();
    }


    /**
     * 保存图片
     */
    @SuppressWarnings("unused")
    public String saveResult() {
        return saveResult(true);
    }


    private String saveResult(boolean recycle) {

        File cache = generateRandomPhotoFile(getContext());
        try {
            FileOutputStream out = new FileOutputStream(cache);
            mPhotoBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
            Log.e(TAG, cache.getAbsolutePath());
            return cache.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        } finally {
            if (recycle) {

                if (mPhotoBitmap != null) {
                    mPhotoBitmap.recycle();
                    mPhotoBitmap = null;
                }
            }
        }
    }

    private OnCutoutListener mCutoutListener;

    public void setOnCutoutListener(OnCutoutListener listener) {
        mCutoutListener = listener;
    }

    private void notifyCanBack() {
        if (mCutoutListener == null) {
            return;
        }
        post(new Runnable() {
            @Override
            public void run() {
                if (mCutoutRecordList.size() > 1 || (mCurrentRecord != null && mCurrentRecord.hasRecord())) {
                    mCutoutListener.canBack(1);
                } else {
                    mCutoutListener.canBack(0);
                }
            }
        });

    }

    private void notifyShowLoading() {
        if (mCutoutListener == null) {
            return;
        }
        post(new Runnable() {
            @Override
            public void run() {
                mCutoutListener.showLoading();

            }
        });
    }

    private void notifyDismissLoading() {
        if (mCutoutListener == null) {
            return;
        }
        post(new Runnable() {
            @Override
            public void run() {
                mCutoutListener.dismissLoading();
            }
        });
    }


    /**
     * 生成图片名称
     */
    public static File generateRandomPhotoFile(Context context) {
        return new File(generateRandomPhotoName(context));
    }

    /**
     * 生成随机的名字
     */
    public static String generateRandomPhotoName(Context context) {
        File cacheFolder = context.getExternalCacheDir();
        if (null == cacheFolder) {
            File target = Environment.getExternalStorageDirectory();
            cacheFolder = new File(target + File.separator + "Pictures");
        }

        Log.d(TAG, "cacheFolder path = " + cacheFolder.getAbsolutePath());
        if (!cacheFolder.exists()) {
            try {
                boolean result = cacheFolder.mkdir();
                Log.d(TAG, " result: " + (result ? "succeeded" : "failed"));
            } catch (Exception e) {
                Log.e(TAG, "generateUri failed: " + e.toString());
            }
        }
        String name = StringUtils.plusString(UUID.randomUUID().toString().toUpperCase(), ".png");
        return StringUtils.plusString(cacheFolder.getAbsolutePath(), File.separator, name);
    }


    private void recycleBitmap(Bitmap bitmap) {
        if (bitmap != null) {
            bitmap.recycle();
        }

    }

    private void resetPhotoRectF() {
        mPhotoRectF.left = mPhotoLeft;
        mPhotoRectF.top = mPhotoTop;
        mPhotoRectF.right = mPhotoRectF.left + mPhotoWidth;
        mPhotoRectF.bottom = mPhotoRectF.top + mPhotoHeight;
    }

    private ValueAnimator.AnimatorUpdateListener mZoomAniListener = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            mZoomScale = (float) animation.getAnimatedValue();
            mZoomLastScale = mZoomScale;
            postInvalidate();
        }
    };

    private ValueAnimator.AnimatorUpdateListener mTranslateXAniListener = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            mTranslateX = (float) animation.getAnimatedValue();
            mTranslateLastX = mTranslateX;
            postInvalidate();
        }
    };

    private ValueAnimator.AnimatorUpdateListener mTranslateYAniListener = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            mTranslateY = (float) animation.getAnimatedValue();
            mTranslateLastY = mTranslateY;
            postInvalidate();
        }
    };

    /**
     * 定义图片是单选还是多选
     */
    @SuppressWarnings("WeakerAccess")
    @IntDef({Mode.CUT_OUT, Mode.ERASER})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Mode {
        /**
         * 单选
         */
        int CUT_OUT = 1;
        /**
         * 多选
         */
        int ERASER = 2;
    }


    @SuppressWarnings("WeakerAccess")
    public interface OnCutoutListener {

        void canBack(int count);

        void showLoading();

        void dismissLoading();
    }

}
