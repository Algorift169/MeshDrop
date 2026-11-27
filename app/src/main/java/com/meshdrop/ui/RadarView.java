package com.meshdrop.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.meshdrop.R;
import com.meshdrop.data.model.DiscoveredDevice;

import java.util.ArrayList;
import java.util.List;

public class RadarView extends View {
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF radarBounds = new RectF();
    private final int accentColor;
    private final int ringColor;
    private final int faintColor;
    private final List<DiscoveredDevice> discoveredDevices = new ArrayList<>();

    public RadarView(Context context) {
        this(context, null);
    }

    public RadarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        accentColor = context.getColor(R.color.accent);
        ringColor = context.getColor(R.color.radar_ring);
        faintColor = context.getColor(R.color.radar_faint);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setContentDescription(context.getString(R.string.radar_content_description));

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(1));
        ringPaint.setColor(ringColor);

        centerPaint.setStyle(Paint.Style.FILL);
        centerPaint.setColor(accentColor);
        centerPaint.setShadowLayer(dp(12), 0, 0, accentColor);

        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setColor(faintColor);

        sweepPaint.setStyle(Paint.Style.STROKE);
        sweepPaint.setStrokeWidth(dp(2));
        sweepPaint.setStrokeCap(Paint.Cap.ROUND);
        sweepPaint.setColor(accentColor);
        sweepPaint.setAlpha(100);

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(accentColor);

        labelPaint.setColor(accentColor);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(dp(10));
        labelPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float radius = Math.max(0, Math.min(getWidth(), getHeight()) * 0.43f);
        if (radius <= 0) {
            return;
        }

        for (int ring = 1; ring <= 4; ring++) {
            float ringRadius = radius * ring / 4f;
            canvas.drawCircle(centerX, centerY, ringRadius, ringPaint);
        }

        radarBounds.set(centerX - radius, centerY - radius,
                centerX + radius, centerY + radius);
        canvas.drawArc(radarBounds, 215, 72, false, sweepPaint);
        canvas.drawCircle(centerX, centerY, dp(22), glowPaint);
        canvas.drawCircle(centerX, centerY, dp(9), centerPaint);
        canvas.drawCircle(centerX, centerY, dp(3), dotPaint);
        canvas.drawText(getResources().getString(R.string.this_device),
                centerX, centerY + dp(40), labelPaint);

        int count = discoveredDevices.size();
        for (int index = 0; index < count; index++) {
            DiscoveredDevice device = discoveredDevices.get(index);
            int hash = endpointHash(device.getEndpointId());
            double angle = (2.0 * Math.PI * index / Math.max(1, count))
                    + (hash / 1000.0);
            float ringFraction = 0.52f + ((Math.abs(hash) % 4) * 0.10f);
            float markerX = centerX + (float) (Math.cos(angle) * radius * ringFraction);
            float markerY = centerY + (float) (Math.sin(angle) * radius * ringFraction);

            canvas.drawCircle(markerX, markerY, dp(7), glowPaint);
            canvas.drawCircle(markerX, markerY, dp(4), dotPaint);

            String name = device.getDeviceName();
            if (name.length() > 18) {
                name = name.substring(0, 17) + "...";
            }
            canvas.drawText(name, markerX, markerY - dp(10), labelPaint);
        }
    }

    public void setDiscoveredDevices(@Nullable List<DiscoveredDevice> devices) {
        discoveredDevices.clear();
        if (devices != null) {
            discoveredDevices.addAll(devices);
        }
        invalidate();
    }

    private int endpointHash(@Nullable String endpointId) {
        if (endpointId == null || endpointId.isEmpty()) {
            return 0;
        }
        int hash = 0;
        for (int index = 0; index < endpointId.length(); index++) {
            hash = 31 * hash + endpointId.charAt(index);
        }
        return hash;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
