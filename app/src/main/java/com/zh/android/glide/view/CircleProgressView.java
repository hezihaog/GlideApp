package com.zh.android.glide.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.bumptech.glideapp.R;


/**
 * 圆环进度条
 */
public class CircleProgressView extends View {
    /**
     * 默认的当前进度，默认为0
     */
    private static final int DEFAULT_PROGRESS = 0;
    /**
     * 默认的最大值，默认为100
     */
    private static final int DEFAULT_MAX = 100;
    /**
     * 默认进度圆弧颜色
     */
    private final int DEFAULT_CIRCLE_COLOR = Color.parseColor("#0AA4A2");
    /**
     * 圆弧颜色
     */
    private final int DEFAULT_REMAIN_CIRCLE_COLOR = Color.parseColor("#EFEFF0");
    /**
     * 默认背景颜色
     */
    private final int DEFAULT_BG_COLOR = Color.parseColor("#00000000");

    /**
     * 绘制相关
     */
    private RectF mRect;
    private Paint mCirclePaint;
    /**
     * 画笔颜色
     */
    private int mCircleColor;
    private int mRemainCircleColor;
    private int mBgColor;
    /**
     * 圆弧宽度
     */
    private float mCircleBorderWidth;
    /**
     * View相关尺寸
     */
    private int mWidth;
    private int mHeight;
    /**
     * 外圆半径
     */
    private float mRadius;
    /**
     * 当前进度
     */
    private float mProgress;
    /**
     * 进度最大值
     */
    private long mMax;
    /**
     * 弧线的开始角度，默认是0，是水平的，我们要从上面开始画
     */
    private final float mStartAngle = -90f;
    /**
     * 中心点X、Y坐标
     */
    private int mCenterX;
    private int mCenterY;
    /**
     * 进度条进度
     */
    private ValueAnimator mProgressAnimator;
    private OnProgressUpdateListener mProgressUpdateListener;

    public CircleProgressView(Context context) {
        super(context);
        init(null);
    }

    public CircleProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public CircleProgressView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    /**
     * 初始化自定义属性
     */
    private void initAttributeVar(AttributeSet attrs) {
        //默认圆弧宽度
        int defaultCircleBorderWidth = dip2px(getContext(), 5f);
        if (attrs != null) {
            TypedArray array = getContext().obtainStyledAttributes(attrs, R.styleable.CircleProgressView);
            mProgress = array.getInt(R.styleable.CircleProgressView_cp_progress, DEFAULT_PROGRESS);
            mMax = array.getInt(R.styleable.CircleProgressView_cp_max, DEFAULT_MAX);
            //Xml设置的进度圆弧颜色
            mCircleColor = array.getColor(R.styleable.CircleProgressView_cp_circle_color, DEFAULT_CIRCLE_COLOR);
            //Xml设置的圆弧颜色
            mRemainCircleColor = array.getColor(R.styleable.CircleProgressView_cp_remain_circle_color, DEFAULT_REMAIN_CIRCLE_COLOR);
            //读取设置的圆弧轮廓宽度，读取dimension
            mCircleBorderWidth = array.getDimensionPixelSize(R.styleable.CircleProgressView_cp_remain_circle_border_width, defaultCircleBorderWidth);
            //背景颜色
            mBgColor = array.getColor(R.styleable.CircleProgressView_cp_bg_color, DEFAULT_BG_COLOR);
            array.recycle();
        } else {
            //没有在Xml中设置属性，使用默认属性
            mProgress = DEFAULT_PROGRESS;
            mMax = DEFAULT_MAX;
            mCircleColor = DEFAULT_CIRCLE_COLOR;
            mRemainCircleColor = DEFAULT_REMAIN_CIRCLE_COLOR;
            mCircleBorderWidth = defaultCircleBorderWidth;
        }
    }

    private void init(AttributeSet attrs) {
        initAttributeVar(attrs);
        //外圆画笔
        mCirclePaint = new Paint();
        mCirclePaint.setColor(mCircleColor);
        mCirclePaint.setStrokeWidth(mCircleBorderWidth);
        mCirclePaint.setStyle(Paint.Style.STROKE);
        //设置笔触为圆角
        mCirclePaint.setStrokeCap(Paint.Cap.ROUND);
        mCirclePaint.setAntiAlias(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        //控件的总宽高
        mWidth = w;
        mHeight = h;
        //取出padding值
        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();
        int paddingTop = getPaddingTop();
        int paddingBottom = getPaddingBottom();
        //绘制范围
        mRect = new RectF();
        mRect.left = (float) paddingLeft;
        mRect.top = (float) paddingTop;
        mRect.right = (float) mWidth - paddingRight;
        mRect.bottom = (float) mHeight - paddingBottom;
        //计算直径和半径
        float diameter = (Math.min(mWidth, mHeight)) - paddingLeft - paddingRight;
        mRadius = (float) ((diameter / 2) * 0.98);
        //计算圆心的坐标
        mCenterX = mWidth / 2;
        mCenterY = mHeight / 2;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(measureSpec(widthMeasureSpec), measureSpec(heightMeasureSpec));
    }

    private int measureSpec(int measureSpec) {
        int result;
        int mode = MeasureSpec.getMode(measureSpec);
        int size = MeasureSpec.getSize(measureSpec);
        //默认大小
        int defaultSize = dip2px(getContext(), 55f);
        //指定宽高则直接返回
        if (mode == MeasureSpec.EXACTLY) {
            result = size;
        } else if (mode == MeasureSpec.AT_MOST) {
            //wrap_content的情况
            result = Math.min(defaultSize, size);
        } else {
            //未指定，则使用默认的大小
            result = defaultSize;
        }
        return result;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.scale(0.93f, 0.93f, mCenterX, mCenterY);
        drawBg(canvas);
        float curProgress = getProgress();
        //画圆弧
        drawCircle(canvas, curProgress);
    }

    /**
     * 画背景
     */
    private void drawBg(Canvas canvas) {
        canvas.drawColor(mBgColor);
    }

    /**
     * 画圆弧
     */
    private void drawCircle(Canvas canvas, float curProgress) {
        mCirclePaint.setColor(mRemainCircleColor);
        canvas.drawCircle(mCenterX, mCenterY, mRadius, mCirclePaint);
        //绘制当前进度的弧线
        mCirclePaint.setColor(mCircleColor);
        float angle = 360 * (curProgress / getMax());
        canvas.drawArc(mRect, mStartAngle, angle, false, mCirclePaint);
    }

    /**
     * 指定时间，开始进度
     *
     * @param targetProgress 目标进度
     * @param duration       执行时间
     * @param isReverse      是否反转，true则是从最大值过渡到目标值，false则是从最小值过渡到目标值
     */
    public void startProgressByTime(int targetProgress, long duration, boolean isReverse) {
        if (mProgressAnimator == null) {
            long startValue;
            long endValue;
            if (isReverse) {
                //从最大值过渡到目标值
                startValue = mMax;
            } else {
                //从最小值过渡到目标值
                startValue = 0;
            }
            endValue = targetProgress;
            mProgressAnimator = ValueAnimator.ofFloat(startValue, endValue);
        }
        mProgressAnimator.setInterpolator(new LinearInterpolator());
        mProgressAnimator.setDuration(duration);
        mProgressAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                Float cValue = (Float) animation.getAnimatedValue();
                setProgress(cValue);
                if (mProgressUpdateListener != null) {
                    mProgressUpdateListener.onProgressUpdate(cValue.longValue());
                }
            }
        });
        mProgressAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                if (mProgressUpdateListener != null) {
                    mProgressUpdateListener.onStart();
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (mProgressUpdateListener != null) {
                    mProgressUpdateListener.onEnd();
                }
            }
        });
        mProgressAnimator.start();
    }

    /**
     * 进度更新监听
     */
    public interface OnProgressUpdateListener {
        void onStart();

        /**
         * 进度更新
         *
         * @param curProgress 当前进度
         */
        void onProgressUpdate(long curProgress);

        void onEnd();
    }

    public static class OnProgressUpdateAdapter implements OnProgressUpdateListener {
        @Override
        public void onStart() {
        }

        @Override
        public void onProgressUpdate(long curProgress) {
        }

        @Override
        public void onEnd() {
        }
    }

    public void setOnProgressUpdateListener(OnProgressUpdateListener progressUpdateListener) {
        mProgressUpdateListener = progressUpdateListener;
    }

    public float getProgress() {
        return mProgress;
    }

    public void setProgress(float mProgress) {
        this.mProgress = mProgress;
        postInvalidate();
    }

    public float getMax() {
        return mMax;
    }

    public void setMax(long max) {
        this.mMax = max;
        postInvalidate();
    }

    /**
     * 设置进度圆弧的颜色
     */
    public void setCircleColor(int color) {
        this.mCircleColor = color;
        invalidate();
    }

    /**
     * 设置总进度圆弧的颜色
     */
    public void setRemainCircleColor(int color) {
        this.mRemainCircleColor = color;
        invalidate();
    }

    public static int dip2px(Context context, float dipValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dipValue * scale + 0.5f);
    }

    public static int px2dp(Context context, float pxValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (pxValue / scale + 0.5f);
    }

    private int sp2px(Context context, float spVal) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                spVal, context.getResources().getDisplayMetrics());
    }
}