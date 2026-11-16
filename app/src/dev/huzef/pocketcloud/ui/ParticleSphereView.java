package dev.huzef.pocketcloud.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class ParticleSphereView extends View {

    private static final int POINT_COUNT = 540;
    private final float[] px = new float[POINT_COUNT];
    private final float[] py = new float[POINT_COUNT];
    private final float[] pz = new float[POINT_COUNT];
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float rotationAngle = 0f;
    private ValueAnimator animator;

    public ParticleSphereView(Context context) {
        super(context);
        initFibonacciSphere();
        startRotation();
    }

    private void initFibonacciSphere() {
        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < POINT_COUNT; i++) {
            double y = 1.0 - (i / (double) (POINT_COUNT - 1)) * 2.0;
            double radiusAtY = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double theta = goldenAngle * i;
            px[i] = (float) (Math.cos(theta) * radiusAtY);
            py[i] = (float) y;
            pz[i] = (float) (Math.sin(theta) * radiusAtY);
        }
    }

    private void startRotation() {
        animator = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2.0));
        animator.setDuration(18000L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                rotationAngle = (Float) animation.getAnimatedValue();
                invalidate();
            }
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float sphereRadius = Math.min(w, h) * 0.38f;

        // Warm bottom-rim ambient aura matching reference artwork
        RadialGradient rimAura = new RadialGradient(
                cx, cy + sphereRadius * 0.55f, sphereRadius * 1.15f,
                new int[]{Color.parseColor("#44EA580C"), Color.parseColor("#14F97316"), Color.TRANSPARENT},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP
        );
        glowPaint.setShader(rimAura);
        canvas.drawCircle(cx, cy + sphereRadius * 0.25f, sphereRadius * 1.15f, glowPaint);

        float cosA = (float) Math.cos(rotationAngle);
        float sinA = (float) Math.sin(rotationAngle);
        float tilt = 0.35f;
        float cosT = (float) Math.cos(tilt);
        float sinT = (float) Math.sin(tilt);

        for (int i = 0; i < POINT_COUNT; i++) {
            // Rotate around Y
            float x1 = px[i] * cosA - pz[i] * sinA;
            float z1 = px[i] * sinA + pz[i] * cosA;
            // Slight X tilt
            float y2 = py[i] * cosT - z1 * sinT;
            float z2 = py[i] * sinT + z1 * cosT;

            float depth = (z2 + 1.0f) * 0.5f; // 0 (back) .. 1 (front)
            float bottomBoost = Math.max(0f, y2); // brighter along bottom curve
            float intensity = Math.min(1f, depth * 0.65f + bottomBoost * 0.65f);

            int alpha = (int) (35 + intensity * 215);
            int r = (int) (180 + intensity * 75);
            int g = (int) (65 + intensity * 65);
            int b = (int) (20 + intensity * 25);

            dotPaint.setColor(Color.argb(alpha, r, g, b));
            float dotSize = 1.4f + intensity * 2.2f;

            float screenX = cx + x1 * sphereRadius;
            float screenY = cy + y2 * sphereRadius;
            canvas.drawCircle(screenX, screenY, dotSize, dotPaint);
        }
    }
}
