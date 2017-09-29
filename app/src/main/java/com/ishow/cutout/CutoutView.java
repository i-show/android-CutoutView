package com.ishow.cutout;

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
    private static final int TRACK_ALPHA = 100;


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
    private Bitmap mResultBitmap;
    private Bitmap mEnlargeBgBitmap;

    private boolean mActionTrackVisible;
    private boolean mEnlargeVisible;
    private int mMode;

    private int mPhotoTop;
    private int mPhotoLeft;
    /**
     * 放大视图
     */
    private int mEnlargeSize;
    private float[] mDownPoint;
    private float[] mMovePoint;
    private float[] mUpPoint;

    private List<CutoutRecord> mCutoutRecordList;
    private CutoutRecord mCurrentRecord;

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
        mMode = Mode.CUT_OUT;

        mCutoutRecordList = new ArrayList<>();
        mDownPoint = new float[2];
        mMovePoint = new float[2];
        mUpPoint = new float[2];


        mPhotoPaint = new Paint();
        mPhotoPaint.setDither(true);
        mPhotoPaint.setAntiAlias(true);
        mPhotoPaint.setStyle(Paint.Style.FILL);

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
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mEnlargeVisible = true;
                if (mMode == Mode.CUT_OUT) {
                    onCutoutDown(event);
                } else {
                    onEraserDown(event);
                }

                break;
            case MotionEvent.ACTION_MOVE:
                mMovePoint[0] = event.getX();
                mMovePoint[1] = event.getY();

                if (mMode == Mode.CUT_OUT) {
                    onCutoutMove(event);
                } else {
                    onEraserMove(event);
                }

                break;
            case MotionEvent.ACTION_UP:
                mEnlargeVisible = false;
                if (mMode == Mode.CUT_OUT) {
                    onCutoutUp(event);
                } else {
                    onEraserUp(event);
                }

                break;
        }
        postInvalidate();
        return true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
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

        /*
         * 画仿透明的背景
         */
        canvas.drawRect(
                mPhotoLeft,
                mPhotoTop,
                mPhotoLeft + mPhotoBitmap.getWidth(),
                mPhotoTop + mPhotoBitmap.getHeight(),
                mTransparentPaint);


        if (mResultBitmap != null && !mResultBitmap.isRecycled()) {
            canvas.drawBitmap(mResultBitmap, mPhotoLeft, mPhotoTop, mPhotoPaint);
        } else {
            canvas.drawBitmap(mPhotoBitmap, mPhotoLeft, mPhotoTop, mPhotoPaint);
        }

        if (mActionTrackVisible) {
            canvas.drawPath(mCurrentPath, mActionPaint);
        }

        drawEnlarge(canvas);

    }

    private void drawEnlarge(Canvas canvas) {
        if (!mEnlargeVisible || mEnlargeBgBitmap == null) {
            return;
        }
        final int x = mEnlargeSize / 2;
        final int y = mEnlargeSize / 2;
        final float moveX = x - mMovePoint[0] + mPhotoLeft;
        final float moveY = y - mMovePoint[1] + mPhotoTop;

        canvas.drawRect(
                0,
                0,
                mEnlargeSize,
                mEnlargeSize,
                mTransparentPaint);

        int saveCount = canvas.saveLayer(0, 0, getWidth(), getHeight(), null, Canvas.ALL_SAVE_FLAG);
        mPhotoPaint.setXfermode(null);
        canvas.drawBitmap(mEnlargeBgBitmap, 0, 0, mPhotoPaint);
        mPhotoPaint.setXfermode(mEnlargePorterMode);

        if (mResultBitmap == null) {
            canvas.drawBitmap(mPhotoBitmap, moveX, moveY, mPhotoPaint);
        } else {
            canvas.drawBitmap(mResultBitmap, moveX, moveY, mPhotoPaint);
        }
        mPhotoPaint.setXfermode(null);
        canvas.restoreToCount(saveCount);

        saveCount = canvas.saveLayer(0, 0, getWidth(), getHeight(), null, Canvas.ALL_SAVE_FLAG);
        canvas.translate(x - mMovePoint[0], y - mMovePoint[1]);
        if (mActionTrackVisible) {
            canvas.drawPath(mCurrentPath, mActionPaint);
        }

        canvas.restoreToCount(saveCount);


        canvas.drawRect(0, 0, mEnlargeSize, mEnlargeSize, mEnlargePaint);
        canvas.drawCircle(mEnlargeSize / 2, mEnlargeSize / 2, 15, mEnlargePaint);

    }

    /**
     * 抠图按下操作
     */
    private void onCutoutDown(MotionEvent event) {
        mActionTrackVisible = true;
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
        mActionTrackVisible = false;
        mActionPaint.setAlpha(255);
        mCurrentPath.reset();
        mCurrentPath.moveTo(event.getX(), event.getY());
    }

    private void onEraserMove(MotionEvent event) {
        mCurrentPath.lineTo(event.getX(), event.getY());
        mResultBitmap = getEraserResultBitmap();
    }

    private void onEraserUp(MotionEvent event) {
        mCurrentRecord.addPath(mCurrentPath);
        mCurrentPath = new Path();
        notifyCanBack();
    }

    private Bitmap getEraserResultBitmap() {
        if (mResultBitmap == null || mResultBitmap.isRecycled()) {
            mResultBitmap = mPhotoBitmap;
        }
        Bitmap bitmap = Bitmap.createBitmap(mPhotoBitmap.getWidth(), mPhotoBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        mPhotoPaint.setXfermode(null);
        canvas.drawBitmap(mResultBitmap, 0, 0, mPhotoPaint);
        mPhotoPaint.setXfermode(mEraserPorterMode);
        canvas.drawBitmap(getEraserPathBitmap(), 0, 0, mPhotoPaint);
        mPhotoPaint.setXfermode(null);
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
     * 计算图片相关信息
     */
    private void computePhotoInfo() {
        final int viewWidth = getMeasuredWidth();
        final int viewHeight = getMeasuredHeight();
        if (mPhotoBitmap == null || viewHeight == 0 || viewWidth == 0) {
            return;
        }

        final int width = mPhotoBitmap.getWidth();
        final int height = mPhotoBitmap.getHeight();
        final float widthScale = ((float) viewWidth / width);
        final float heightScale = ((float) viewHeight / height);

        Matrix matrix = new Matrix();
        final float scale = Math.min(widthScale, heightScale);
        if (widthScale <= heightScale) {
            mPhotoLeft = 0;
            mPhotoTop = (int) ((viewHeight - height * scale) / 2);
        } else {
            mPhotoTop = 0;
            mPhotoLeft = (int) ((viewWidth - width * scale) / 2);
        }
        matrix.postScale(scale, scale);

        Bitmap bitmap = mPhotoBitmap;
        mPhotoBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);

    }

    public void setMode(@Mode int mode) {
        if (mMode == mode) {
            return;
        }

        mMode = mode;
        if (mode == Mode.ERASER && mCurrentRecord != null) {
            mCurrentRecord.clearPointList();
            mActionTrackVisible = false;
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

        if (mResultBitmap != null) {
            mResultBitmap.recycle();
            mResultBitmap = null;
        }

        mPhotoBitmap = BitmapFactory.decodeFile(path);
        computePhotoInfo();
        postInvalidate();
    }


    private Runnable mResultCutoutRunnable = new Runnable() {
        @Override
        public void run() {
            notifyShowLoading();
            if (mResultBitmap == null || mResultBitmap.isRecycled()) {
                mResultBitmap = mPhotoBitmap;
            }
            Bitmap bitmap = Bitmap.createBitmap(mPhotoBitmap.getWidth(), mPhotoBitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            mPhotoPaint.setXfermode(null);
            canvas.drawBitmap(mResultBitmap, 0, 0, mPhotoPaint);
            mPhotoPaint.setXfermode(mCutoutPorterMode);
            canvas.drawBitmap(getPathBitmap(), 0, 0, mPhotoPaint);
            mPhotoPaint.setXfermode(null);
            mResultBitmap = bitmap;
            mPhotoBitmap = bitmap;
            mActionTrackVisible = false;

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
        if (mResultBitmap != null) {
            mResultBitmap.recycle();
            mResultBitmap = null;
        }


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
        if (size == 1) {
            pathList.clear();
            mResultBitmap = mPhotoBitmap;
            notifyCanBack();
        } else {
            pathList.remove(pathList.size() - 1);
            Bitmap bitmap = Bitmap.createBitmap(mPhotoBitmap.getWidth(), mPhotoBitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            mPhotoPaint.setXfermode(null);
            canvas.drawBitmap(mPhotoBitmap, 0, 0, mPhotoPaint);
            mPhotoPaint.setXfermode(mEraserPorterMode);
            canvas.drawBitmap(getEraserPathBitmap(pathList), 0, 0, mPhotoPaint);
            mPhotoPaint.setXfermode(null);
            mResultBitmap = bitmap;
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
    public String saveResult() {
        return saveResult(true);
    }


    private String saveResult(boolean recycle) {
        if (mResultBitmap == null) {
            mResultBitmap = mPhotoBitmap;
        }
        File cache = generateRandomPhotoFile(getContext());
        try {
            FileOutputStream out = new FileOutputStream(cache);
            mResultBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
            Log.e(TAG, cache.getAbsolutePath());
            return cache.getAbsolutePath();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return "";
        } finally {
            if (recycle) {
                if (mResultBitmap != null) {
                    mResultBitmap.recycle();
                    mResultBitmap = null;
                }

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
