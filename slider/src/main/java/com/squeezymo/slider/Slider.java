package com.squeezymo.slider;

import android.animation.Animator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Build;
import android.support.annotation.DrawableRes;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

public class Slider extends FrameLayout implements View.OnTouchListener {
    private final static int STATE_STILL = 0;
    private final static int STATE_ACTIVE = 1;

    private final static int DIR_NONE = 0;
    private final static int DIR_LEFT = 1;
    private final static int DIR_RIGHT = 2;

    @IntDef({STATE_STILL, STATE_ACTIVE}) public @interface State {}

    @IntDef({DIR_NONE, DIR_LEFT, DIR_RIGHT}) public @interface Direction {}

    private final static int BACKGROUND_TRANSITION_DURATION = 300;

    private final int MAX_CLICK_DISTANCE;
    private final Runnable activeStateChecker = new Runnable() {
        @Override
        public void run() {
            if (slidingFabTouched) {
                setState(STATE_ACTIVE);
            }
        }
    };

    private View slidingView;
    private final View background;

    private @Nullable OnClickListener onClickListener;
    private @Nullable OnPositionChangeListener onPositionChangeListener;

    private boolean slidingFabTouched;
    private @State int state;

    private float sliderBoundLeft;
    private float sliderBoundRight;

    private final PointF sliderPosition;
    private final PointF touchDownPosition;
    private final PointF touchCurrentPosition;
    private final PointF correction;

    private int currentSegmentX;

    private TransitionDrawable backgroundTransition;

    private @DrawableRes int backgroundStillResId = android.R.color.transparent;
    private @DrawableRes int backgroundActiveResId = R.drawable.background_active_default;
    private int activationTime = 300;
    private float segmentRatioX = 1;
    private float alphaMin = 0.2f;
    private float alphaMax = 1f;

    public interface OnClickListener {
        void onClick(final Slider slider);
    }

    public interface OnPositionChangeListener {
        void onActivated(final Slider slider);

        void onPositionChanged(final Slider slider, final @Direction int direction, final int position);

        void onReleased(final Slider slider);
    }

    public Slider(Context context) {
        this(context, null);
    }

    public Slider(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Slider(final Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        MAX_CLICK_DISTANCE = context.getResources().getDimensionPixelSize(R.dimen.max_click_dist);

        final ViewGroup view = (ViewGroup) LayoutInflater.from(context).inflate(R.layout.view_slider, this, true);

        background = view.findViewById(R.id.background);

        touchDownPosition = new PointF();
        touchCurrentPosition = new PointF();
        sliderPosition = new PointF();
        correction = new PointF();

        initAttributes(context, attrs);

        backgroundTransition = new TransitionDrawable(new Drawable[] {
                ContextCompat.getDrawable(context, backgroundStillResId),
                ContextCompat.getDrawable(context, backgroundActiveResId)
        });

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            //noinspection deprecation
            background.setBackgroundDrawable(backgroundTransition);
        } else {
            background.setBackground(backgroundTransition);
        }

        state = STATE_STILL;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (getChildCount() != 2) {
            throw new IllegalStateException("1 child expected; " + (getChildCount() - 1) + " found");
        }

        slidingView = getChildAt(1);
        slidingView.setOnTouchListener(this);
        ((LayoutParams) slidingView.getLayoutParams()).gravity = Gravity.CENTER;
        slidingView.bringToFront();

        sliderPosition.set(slidingView.getX(), slidingView.getY());

        sliderBoundLeft = 0;
        sliderBoundRight = getWidth() - slidingView.getWidth();

    }

    private void initAttributes(final Context context, final @Nullable AttributeSet attrs) {
        if (attrs == null) {
            return;
        }

        TypedArray array = null;

        try {
            array = context.obtainStyledAttributes(attrs, R.styleable.Slider);

            backgroundStillResId = array.getResourceId(R.styleable.Slider_sld_backgroundStill, backgroundStillResId);
            backgroundActiveResId = array.getResourceId(R.styleable.Slider_sld_backgroundActive, backgroundActiveResId);
            segmentRatioX = 1f/array.getInt(R.styleable.Slider_sld_segmentsX, 1);
            alphaMin = array.getFloat(R.styleable.Slider_sld_alphaFrom, alphaMin);
            alphaMax = array.getFloat(R.styleable.Slider_sld_alphaTo, alphaMax);
        }
        finally {
            if (array != null) {
                array.recycle();
            }
        }
    }

    @Nullable
    public OnClickListener getOnClickListener() {
        return onClickListener;
    }

    public void setOnClickListener(@Nullable OnClickListener onClickListener) {
        this.onClickListener = onClickListener;
    }

    @Nullable
    public OnPositionChangeListener getOnPositionChangeListener() {
        return onPositionChangeListener;
    }

    public void setOnPositionChangeListener(@Nullable OnPositionChangeListener onPositionChangeListener) {
        this.onPositionChangeListener = onPositionChangeListener;
    }

    public int getActivationTime() {
        return activationTime;
    }

    public void setActivationTime(int activationTime) {
        this.activationTime = activationTime;
    }

    public void setSegmentsX(int segmentsX) {
        this.segmentRatioX = 1f/segmentsX;
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if (view != slidingView) {
            return false;
        }

        touchCurrentPosition.set(event.getRawX(), event.getY());

        switch (MotionEventCompat.getActionMasked(event)) {
            case MotionEvent.ACTION_DOWN: {
                slidingFabTouched = true;
                touchDownPosition.set(touchCurrentPosition);
                correction.set(slidingView.getX() - touchCurrentPosition.x, slidingView.getY() - touchCurrentPosition.y);

                postDelayed(activeStateChecker, activationTime);

                break;
            }

            case MotionEvent.ACTION_MOVE: {
                final @Direction int direction;
                final float ratioX;
                float newX = touchCurrentPosition.x + correction.x;

                if (newX < sliderPosition.x) {
                    if (newX < 0) newX = sliderBoundLeft;

                    direction = DIR_LEFT;
                    ratioX = (sliderPosition.x - newX) / sliderPosition.x;
                }
                else if (newX > sliderPosition.x) {
                    if (newX > sliderBoundRight) newX = sliderBoundRight;

                    direction = DIR_RIGHT;
                    ratioX = (sliderPosition.x - newX) / (sliderPosition.x - sliderBoundRight);
                }
                else {
                    direction = DIR_NONE;
                    ratioX = 0;
                }

                background.setAlpha(alphaMin + ratioX*(alphaMax - alphaMin));

                final int newCurrentSegmentX = (int) Math.ceil(ratioX/segmentRatioX);

                if (currentSegmentX != newCurrentSegmentX && state == STATE_ACTIVE && onPositionChangeListener != null) {
                    currentSegmentX = newCurrentSegmentX;
                    onPositionChangeListener.onPositionChanged(this, direction, currentSegmentX);
                }

                slidingView.setX(newX);
                //slidingView.requestLayout();

                if (hasExceededDistThreshold()) {
                    setState(STATE_ACTIVE);
                }

                break;
            }

            case MotionEvent.ACTION_UP: {
                slidingFabTouched = false;

                if (onClickListener != null && state != STATE_ACTIVE && !hasExceededDistThreshold()) {
                    onClickListener.onClick(this);
                }

                revertSlidingFabPosition(true);
            }
        }

        return true;
    }

    private boolean hasExceededDistThreshold() {
        if (Math.abs(touchCurrentPosition.x - touchDownPosition.x) >= MAX_CLICK_DISTANCE) {
            return true;
        }

        return false;
    }

    private void revertSlidingFabPosition(final boolean animate) {
        slidingView
                .animate()
                .x(sliderPosition.x)
                .y(sliderPosition.y)
                .setDuration(animate ? 200 : 0)
                .setInterpolator(new DecelerateInterpolator())
                .setListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animator) {}

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        setState(STATE_STILL);
                    }

                    @Override
                    public void onAnimationCancel(Animator animator) {}

                    @Override
                    public void onAnimationRepeat(Animator animator) {}
                })
                .start();

    }

    private synchronized void setState(final @State int state) {
        if (this.state != state) {
            if (state == STATE_STILL) {
                currentSegmentX = 0;
                backgroundTransition.reverseTransition(BACKGROUND_TRANSITION_DURATION);

                if (onPositionChangeListener != null) {
                    onPositionChangeListener.onReleased(this);
                }
            }
            else {
                background.setAlpha(alphaMin);
                backgroundTransition.startTransition(BACKGROUND_TRANSITION_DURATION);

                if (onPositionChangeListener != null) {
                    onPositionChangeListener.onActivated(this);
                }
            }

            this.state = state;
        }
    }
}
