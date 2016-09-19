package com.squeezymo.slider_demo;

import android.graphics.Color;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.squeezymo.slider.Slider;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final FABSlider fabSlider = new FABSlider(
                (Slider) findViewById(R.id.slider),
                (FloatingActionButton) findViewById(R.id.sliding_fab)
        );
    }

    public static class FABSlider {
        private final Slider slider;
        private final FloatingActionButton fab;

        public FABSlider(final Slider slider, final FloatingActionButton fab) {
            this.slider = slider;
            this.fab = fab;

            fab.setBackgroundColor(Color.RED);

            slider.setOnClickListener(new Slider.OnClickListener() {
                @Override
                public void onClick(Slider slider) {
                    android.util.Log.d("333444", "CLICKED");
                }
            });
            slider.setOnDragListener(new Slider.OnDragListener() {
                @Override
                public void onDragStarted(Slider slider) {
                    android.util.Log.d("333444", "ACTIVATED");
                    fab.setBackgroundColor(Color.GREEN);
                }

                @Override
                public void onDragFinished(Slider slider) {
                    android.util.Log.d("333444", "RELEASED");
                    fab.setBackgroundColor(Color.RED);
                }
            });
            slider.setOnDiscretePositionChangeListener(new Slider.OnDiscretePositionChangeListener() {
                @Override
                public void onPositionChanged(final Slider slider, final @Slider.Direction int direction, final int position) {
                    android.util.Log.d("333444", "POSITION CHANGED: " + direction + ", segment " + position);
                }
            });
        }
    }
}
