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
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

public class Slider extends FrameLayout implements View.OnTouchListener, Runnable {
    private final static String LOG_TAG = Slider.class.getSimpleName();

    private final static int TYPE_HORIZONTAL = 0;
    private final static int TYPE_VERTICAL = 1;
    private final static int TYPE_PLANAR_LOCKED = 2;
    private final static int TYPE_PLANAR_UNLOCKED = 3;

    private final static int STATE_STILL = 0;
    private final static int STATE_DRAG_IN_PROGRESS = 1;

    public final static int DIR_NONE = 0;
    public final static int DIR_LEFT = 1;
    public final static int DIR_RIGHT = 2;
    public final static int DIR_UP = 4;
    public final static int DIR_DOWN = 8;

    @IntDef({TYPE_HORIZONTAL, TYPE_VERTICAL, TYPE_PLANAR_LOCKED, TYPE_PLANAR_UNLOCKED}) public @interface Type {}

    @IntDef({STATE_STILL, STATE_DRAG_IN_PROGRESS}) public @interface State {}

    @IntDef({DIR_NONE, DIR_LEFT, DIR_RIGHT, DIR_UP, DIR_DOWN}) public @interface Direction {}

    private final static int BACKGROUND_TRANSITION_DURATION = 300;

    private final int MAX_CLICK_DISTANCE;

    private @Type int type = TYPE_HORIZONTAL;

    private View slidingView;
    private final View background;

    private @Nullable OnClickListener onClickListener;
    private @Nullable OnDragListener onDragListener;
    private @Nullable OnDiscretePositionChange1DListener onDiscretePositionChange1dListener;
    private @Nullable OnRelativePositionChange1DListener onRelativePositionChange1dListener;
    private @Nullable OnDiscretePositionChange2DListener onDiscretePositionChange2dListener;
    private @Nullable OnRelativePositionChange2DListener onRelativePositionChange2dListener;

    private boolean slidingViewTouched;
    private @State int state;

    private float sliderBoundLeft;
    private float sliderBoundRight;
    private float sliderBoundTop;
    private float sliderBoundBottom;

    private final PointF sliderPosition;
    private final PointF touchDownPosition;
    private final PointF touchCurrentPosition;
    private final PointF correction;

    private int currentSegmentX;
    private int currentSegmentY;

    private TransitionDrawable backgroundTransition;

    private @DrawableRes int backgroundStillResId = android.R.color.transparent;
    private @DrawableRes int backgroundDraggedResId = R.drawable.background_dragged_default;
    private int dragActivationTime = 300;
    private float segmentRatioX = 1;
    private float segmentRatioY = 1;

    private float alphaMinX = 0.2f;
    private float alphaMaxX = 1f;

    private float alphaMinY = 0.2f;
    private float alphaMaxY = 1f;

    public interface OnClickListener {
        void onClick(final Slider slider);
    }

    public interface OnDragListener {
        void onDragStarted(final Slider slider);

        void onDragFinished(final Slider slider);
    }

    public interface OnDiscretePositionChange1DListener {
        void onPositionChanged(
                final Slider slider,
                final @Direction int direction,
                final int position
        );
    }

    public interface OnDiscretePositionChange2DListener {
        void onPositionChanged(
                final Slider slider,
                final @Direction int directionX,
                final int positionX,
                final @Direction int directionY,
                final int positionY
        );
    }

    public interface OnRelativePositionChange1DListener {
        void onPositionChanged(
                final Slider slider,
                final @Direction int direction,
                final float ratio
        );
    }

    public interface OnRelativePositionChange2DListener {
        void onPositionChanged(
                final Slider slider,
                final @Direction int directionX,
                final float ratioX,
                final @Direction int directionY,
                final float ratioY
        );
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
                ContextCompat.getDrawable(context, backgroundDraggedResId)
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

        sliderBoundTop = 0;
        sliderBoundBottom = getHeight() - slidingView.getHeight();
    }

    private void initAttributes(final Context context, final @Nullable AttributeSet attrs) {
        if (attrs == null) {
            return;
        }

        TypedArray array = null;

        try {
            array = context.obtainStyledAttributes(attrs, R.styleable.Slider);

            //noinspection WrongConstant
            type = array.getInt(R.styleable.Slider_sld_type, type);

            if (canMoveHorizontally()) {
                segmentRatioX = 1f/array.getInt(R.styleable.Slider_sld_segmentsX, 1);
                alphaMinX = array.getFloat(R.styleable.Slider_sld_alphaFromX, alphaMinX);
                alphaMaxX = array.getFloat(R.styleable.Slider_sld_alphaToX, alphaMaxX);
            }
            else {
                composeIgnoredAttributeWarning(array, R.styleable.Slider_sld_segmentsX, "sld_segmentsX");
                composeIgnoredAttributeWarning(array, R.styleable.Slider_sld_alphaFromX, "sld_alphaFromX");
                composeIgnoredAttributeWarning(array, R.styleable.Slider_sld_alphaToX, "sld_alphaToX");
            }

            if (canMoveVertically()) {
                segmentRatioY = 1f/array.getInt(R.styleable.Slider_sld_segmentsY, 1);
                alphaMinY = array.getFloat(R.styleable.Slider_sld_alphaFromY, alphaMinX);
                alphaMaxY = array.getFloat(R.styleable.Slider_sld_alphaToY, alphaMaxX);
            }
            else {
                composeIgnoredAttributeWarning(array, R.styleable.Slider_sld_segmentsY, "sld_segmentsY");
                composeIgnoredAttributeWarning(array, R.styleable.Slider_sld_alphaFromY, "sld_alphaFromY");
                composeIgnoredAttributeWarning(array, R.styleable.Slider_sld_alphaToY, "sld_alphaToY");
            }

            backgroundStillResId = array.getResourceId(R.styleable.Slider_sld_backgroundStill, backgroundStillResId);
            backgroundDraggedResId = array.getResourceId(R.styleable.Slider_sld_backgroundDragged, backgroundDraggedResId);
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
    public OnDragListener getOnDragListener() {
        return onDragListener;
    }

    public void setOnDragListener(@Nullable OnDragListener onDragListener) {
        this.onDragListener = onDragListener;
    }

    @Nullable
    public OnDiscretePositionChange1DListener getOnDiscretePositionChange1dListener() {
        return onDiscretePositionChange1dListener;
    }

    public int getDragActivationTime() {
        return dragActivationTime;
    }

    public void setDragActivationTime(int activationTime) {
        this.dragActivationTime = activationTime;
    }

    public void setSegmentsX(int segmentsX) {
        this.segmentRatioX = 1f/segmentsX;
    }

    public void setSegmentRatioY(float segmentRatioY) {
        this.segmentRatioY = segmentRatioY;
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if (view != slidingView) {
            return false;
        }

        touchCurrentPosition.set(event.getRawX(), event.getRawY());

        switch (MotionEventCompat.getActionMasked(event)) {
            case MotionEvent.ACTION_DOWN: {
                slidingViewTouched = true;
                touchDownPosition.set(touchCurrentPosition);
                correction.set(slidingView.getX() - touchCurrentPosition.x, slidingView.getY() - touchCurrentPosition.y);

                postDelayed(this, dragActivationTime);

                break;
            }

            case MotionEvent.ACTION_MOVE: {
                // Horizontal change
                final @Direction int directionX;
                final float ratioX;

                if (canMoveHorizontally()) {
                    float newX = touchCurrentPosition.x + correction.x;

                    if (newX < sliderPosition.x) {
                        if (newX < 0) newX = sliderBoundLeft;

                        directionX = DIR_LEFT;
                        ratioX = (sliderPosition.x - newX) / sliderPosition.x;
                    } else if (newX > sliderPosition.x) {
                        if (newX > sliderBoundRight) newX = sliderBoundRight;

                        directionX = DIR_RIGHT;
                        ratioX = (sliderPosition.x - newX) / (sliderPosition.x - sliderBoundRight);
                    } else {
                        directionX = DIR_NONE;
                        ratioX = 0;
                    }

                    slidingView.setX(newX);
                }
                else {
                    directionX = DIR_NONE;
                    ratioX = 0;
                }

                // Vertical change
                final @Direction int directionY;
                final float ratioY;

                if (canMoveVertically()) {
                    float newY = touchCurrentPosition.y + correction.y;

                    if (newY < sliderPosition.y) {
                        if (newY < 0) newY = sliderBoundTop;

                        directionY = DIR_UP;
                        ratioY = (sliderPosition.y - newY) / sliderPosition.y;
                    } else if (newY > sliderPosition.y) {
                        if (newY > sliderBoundBottom) newY = sliderBoundBottom;

                        directionY = DIR_DOWN;
                        ratioY = (sliderPosition.y - newY) / (sliderPosition.y - sliderBoundBottom);
                    } else {
                        directionY = DIR_NONE;
                        ratioY = 0;
                    }

                    slidingView.setY(newY);
                }
                else {
                    directionY = DIR_NONE;
                    ratioY = 0;
                }

                // Background
                background.setAlpha(calcAlpha(ratioX, ratioY));

                // Callbacks
                final int newCurrentSegmentX = (int) Math.ceil(ratioX/segmentRatioX);
                final int newCurrentSegmentY = (int) Math.ceil(ratioY/segmentRatioY);

                if (state == STATE_DRAG_IN_PROGRESS) {
                    // discrete position change callback
                    if (onDiscretePositionChange1dListener != null) {
                        if (canMoveHorizontally() && currentSegmentX != newCurrentSegmentX) {
                            currentSegmentX = newCurrentSegmentX;
                            onDiscretePositionChange1dListener.onPositionChanged(this, directionX, currentSegmentX);
                        }

                        if (canMoveVertically() && currentSegmentY != newCurrentSegmentY) {
                            currentSegmentY = newCurrentSegmentY;
                            onDiscretePositionChange1dListener.onPositionChanged(this, directionY, currentSegmentY);
                        }
                    }
                    else if (onDiscretePositionChange2dListener != null) {
                        if (currentSegmentX != newCurrentSegmentX || currentSegmentY != newCurrentSegmentY) {
                            currentSegmentX = newCurrentSegmentX;
                            currentSegmentY = newCurrentSegmentY;

                            onDiscretePositionChange2dListener.onPositionChanged(
                                    this,
                                    directionX,
                                    currentSegmentX,
                                    directionY,
                                    currentSegmentY
                            );
                        }
                    }

                    // relative position change callback
                    if (onRelativePositionChange1dListener != null) {
                        onRelativePositionChange1dListener.onPositionChanged(this, directionX, ratioX);
                    }
                    else if (onRelativePositionChange2dListener != null) {
                        onRelativePositionChange2dListener.onPositionChanged(
                                this,
                                directionX,
                                ratioX,
                                directionY,
                                ratioY
                        );
                    }
                }

                if (hasExceededDistThreshold()) {
                    setState(STATE_DRAG_IN_PROGRESS);
                }

                break;
            }

            case MotionEvent.ACTION_UP: {
                slidingViewTouched = false;

                if (onClickListener != null && state != STATE_DRAG_IN_PROGRESS && !hasExceededDistThreshold()) {
                    onClickListener.onClick(this);
                }

                revertSlidingViewPosition(true);
            }
        }

        return true;
    }

    private boolean hasExceededDistThreshold() {
        final float horizontal = canMoveHorizontally() ? Math.abs(touchCurrentPosition.x - touchDownPosition.x) : 0;
        final float vertical = canMoveVertically() ? Math.abs(touchCurrentPosition.y - touchDownPosition.y) : 0;

        return Math.sqrt(horizontal * horizontal + vertical * vertical) >= MAX_CLICK_DISTANCE;
    }

    private float calcAlpha(final float ratioX, final float ratioY) {
        final float horizontal = canMoveHorizontally() ? alphaMinX + ratioX*(alphaMaxX - alphaMinX) : 0;
        final float vertical = canMoveVertically() ? alphaMinY + ratioY*(alphaMaxY - alphaMinY) : 0;

        return (float) Math.sqrt(horizontal*horizontal + vertical*vertical);
    }

    private void revertSlidingViewPosition(final boolean animate) {
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

    private void setState(final @State int state) {
        if (this.state != state) {
            if (state == STATE_STILL) {
                currentSegmentX = 0;
                backgroundTransition.reverseTransition(BACKGROUND_TRANSITION_DURATION);

                if (onDragListener != null) {
                    onDragListener.onDragFinished(this);
                }
            }
            else {
                background.setAlpha(alphaMinX);
                backgroundTransition.startTransition(BACKGROUND_TRANSITION_DURATION);

                if (onDragListener != null) {
                    onDragListener.onDragStarted(this);
                }
            }

            this.state = state;
        }
    }

    @Override
    public void run() {
        if (slidingViewTouched) {
            setState(STATE_DRAG_IN_PROGRESS);
        }
    }

    private boolean canMoveHorizontally() {
        return type == TYPE_HORIZONTAL;
    }

    private boolean canMoveVertically() {
        return type == TYPE_VERTICAL;
    }

    private String composeIllegalListenerExceptionMessage(final Class listenerType) {
        return "Use " + listenerType.getSimpleName() + " for this type of " + Slider.class.getSimpleName();
    }

    private void composeIgnoredAttributeWarning(final TypedArray array, final int attrIndex, final String attrName) {
        if (array.hasValue(attrIndex)) {
            Log.w(LOG_TAG, "\"" + attrName + "\" attribute ignored for this type of " + Slider.class.getSimpleName());
        }
    }

    /* --- Listeners setters and getters -- */

    public void setOnDiscretePositionChange1dListener(@Nullable OnDiscretePositionChange1DListener onDiscretePositionChange1dListener) {
        if (type != TYPE_HORIZONTAL && type != TYPE_VERTICAL) {
            throw new IllegalStateException(composeIllegalListenerExceptionMessage(OnDiscretePositionChange2DListener.class));
        }

        this.onDiscretePositionChange1dListener = onDiscretePositionChange1dListener;
    }

    @Nullable
    public OnRelativePositionChange1DListener getOnRelativePositionChange1dListener() {
        return onRelativePositionChange1dListener;
    }

    public void setOnRelativePositionChange1dListener(@Nullable OnRelativePositionChange1DListener onRelativePositionChange1dListener) {
        if (type != TYPE_HORIZONTAL && type != TYPE_VERTICAL) {
            throw new IllegalStateException(composeIllegalListenerExceptionMessage(OnRelativePositionChange2DListener.class));
        }

        this.onRelativePositionChange1dListener = onRelativePositionChange1dListener;
    }

    @Nullable
    public OnDiscretePositionChange2DListener getOnDiscretePositionChange2dListener() {
        return onDiscretePositionChange2dListener;
    }

    public void setOnDiscretePositionChange2dListener(@Nullable OnDiscretePositionChange2DListener onDiscretePositionChange2dListener) {
        if (type == TYPE_HORIZONTAL || type == TYPE_VERTICAL) {
            throw new IllegalStateException(composeIllegalListenerExceptionMessage(OnDiscretePositionChange1DListener.class));
        }

        this.onDiscretePositionChange2dListener = onDiscretePositionChange2dListener;
    }

    @Nullable
    public OnRelativePositionChange2DListener getOnRelativePositionChange2dListener() {
        return onRelativePositionChange2dListener;
    }

    public void setOnRelativePositionChange2dListener(@Nullable OnRelativePositionChange2DListener onRelativePositionChange2dListener) {
        if (type == TYPE_HORIZONTAL || type == TYPE_VERTICAL) {
            throw new IllegalStateException(composeIllegalListenerExceptionMessage(OnRelativePositionChange1DListener.class));
        }

        this.onRelativePositionChange2dListener = onRelativePositionChange2dListener;
    }
}
