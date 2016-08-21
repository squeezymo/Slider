package com.squeezymo.slider_demo;

import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.github.clans.fab.FloatingActionButton;
import com.squeezymo.slider.Slider;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final FABSlider fabSlideHorizontal = new FABSlider(
                (Slider) findViewById(R.id.slider_horizontal),
                (FloatingActionButton) findViewById(R.id.sliding_fab_horizontal)
        );

        final FABSlider fabSlideVertical = new FABSlider(
                (Slider) findViewById(R.id.slider_vertical),
                (FloatingActionButton) findViewById(R.id.sliding_fab_vertical)
        );
    }

    public static class FABSlider {
        private final Slider slider;
        private final FloatingActionButton fab;

        public FABSlider(final Slider slider, final FloatingActionButton fab) {
            this.slider = slider;
            this.fab = fab;

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
                    // fab.setBackgroundColor(Color.GREEN);
                }

                @Override
                public void onDragFinished(Slider slider) {
                    android.util.Log.d("333444", "RELEASED");
                    //  fab.setBackgroundColor(Color.RED);
                }
            });
            slider.setOnDiscretePositionChange1dListener(new Slider.OnDiscretePositionChange1DListener() {
                @Override
                public void onPositionChanged(Slider slider, @Slider.Direction int direction, int position) {
                    android.util.Log.d("333444", "POSITION CHANGED: " + direction + ", " + position);
                }
            });
        }
    }
}
