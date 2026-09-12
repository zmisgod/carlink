package com.carlink.vehicle;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** A compact, native Canvas TBT surface suitable for a 1024x524 cluster region. */
public final class NavigationClusterView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private NavigationInfo info;
    private String outputLabel = "本地预览";

    public NavigationClusterView(Context context) {
        super(context);
        init();
    }

    public NavigationClusterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setContentDescription("导航仪表盘预览");
    }

    public void setNavigationInfo(NavigationInfo info) {
        if (info != null) {
            this.info = info;
            invalidate();
        }
    }

    public void setOutputLabel(String label) {
        outputLabel = label == null || label.trim().isEmpty() ? "本地预览" : label;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (info == null) return;
        float density = getResources().getDisplayMetrics().density;
        float w = getWidth();
        float h = getHeight();
        float pad = 16 * density;
        float header = 35 * density;
        float gap = 10 * density;
        float contentTop = header + gap;

        canvas.drawColor(Color.rgb(7, 15, 22));
        text(canvas, "导航 / 转向指引", pad, 22 * density, 11 * density,
                Color.rgb(124, 160, 184), true, Paint.Align.LEFT);
        text(canvas, outputLabel, w - pad, 22 * density, 10 * density,
                outputLabel.contains("副屏") ? Color.rgb(78, 209, 161) : Color.rgb(241, 174, 83),
                true, Paint.Align.RIGHT);
        paint.setColor(Color.rgb(33, 55, 71));
        paint.setStrokeWidth(Math.max(1, density));
        canvas.drawLine(pad, header, w - pad, header, paint);

        float bodyHeight = Math.max(1, h - contentTop - 54 * density);
        float leftWidth = Math.min(170 * density, w * .30f);
        float rightWidth = Math.min(152 * density, w * .26f);
        float centerLeft = pad + leftWidth + gap;
        float centerRight = w - pad - rightWidth - gap;
        float centerWidth = Math.max(1, centerRight - centerLeft);

        // Maneuver tile.
        rect.set(pad, contentTop, pad + leftWidth, contentTop + bodyHeight);
        panel(canvas, rect, Color.rgb(12, 29, 41));
        drawManeuver(canvas, rect.centerX(), contentTop + bodyHeight * .42f,
                Math.min(leftWidth, bodyHeight) * .42f, info.maneuver);
        text(canvas, info.maneuver.label(), rect.centerX(), contentTop + bodyHeight - 18 * density,
                13 * density, Color.rgb(184, 207, 220), true, Paint.Align.CENTER);

        // Road and distance tile.
        rect.set(centerLeft, contentTop, centerRight, contentTop + bodyHeight);
        panel(canvas, rect, Color.rgb(13, 25, 35));
        text(canvas, "距下个动作", centerLeft + 14 * density, contentTop + 24 * density,
                11 * density, Color.rgb(123, 157, 181), false, Paint.Align.LEFT);
        text(canvas, fit(info.distanceLabel(), centerWidth - 28 * density, 33 * density, true),
                centerLeft + 14 * density, contentTop + 66 * density, 33 * density,
                Color.WHITE, true, Paint.Align.LEFT);
        text(canvas, fit(info.nextRoadName, centerWidth - 28 * density, 17 * density, true),
                centerLeft + 14 * density, contentTop + 99 * density, 17 * density,
                Color.rgb(94, 211, 169), true, Paint.Align.LEFT);
        text(canvas, "当前 " + fit(info.currentRoadName, centerWidth - 70 * density,
                        12 * density, false),
                centerLeft + 14 * density, contentTop + 123 * density, 12 * density,
                Color.rgb(160, 182, 195), false, Paint.Align.LEFT);
        text(canvas, info.trafficLight ? "前方信号灯" : (info.rerouting ? "重新规划路线" : "按车道行驶"),
                centerLeft + 14 * density, contentTop + bodyHeight - 18 * density, 11 * density,
                info.rerouting ? Color.rgb(241, 174, 83) : Color.rgb(123, 157, 181),
                false, Paint.Align.LEFT);
        drawLaneGuidance(canvas, centerLeft + 14 * density, contentTop + bodyHeight - 51 * density,
                centerWidth - 28 * density, 20 * density);

        // Speed and route summary tile.
        rect.set(w - pad - rightWidth, contentTop, w - pad, contentTop + bodyHeight);
        panel(canvas, rect, Color.rgb(12, 29, 41));
        text(canvas, "当前速度", rect.centerX(), contentTop + 23 * density, 11 * density,
                Color.rgb(123, 157, 181), false, Paint.Align.CENTER);
        text(canvas, (info.currentSpeedKmh >= 0 ? String.valueOf(info.currentSpeedKmh) : "—"), rect.centerX(), contentTop + 64 * density,
                31 * density, Color.WHITE, true, Paint.Align.CENTER);
        text(canvas, "km/h", rect.centerX(), contentTop + 82 * density, 11 * density,
                Color.rgb(160, 182, 195), false, Paint.Align.CENTER);
        paint.setColor(info.currentSpeedKmh > info.speedLimitKmh && info.speedLimitKmh > 0
                ? Color.rgb(235, 100, 91) : Color.rgb(94, 211, 169));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * density);
        canvas.drawCircle(rect.centerX(), contentTop + 119 * density, 22 * density, paint);
        paint.setStyle(Paint.Style.FILL);
        text(canvas, info.speedLimitKmh > 0 ? String.valueOf(info.speedLimitKmh) : "-",
                rect.centerX(), contentTop + 124 * density, 15 * density, paint.getColor(), true,
                Paint.Align.CENTER);
        text(canvas, info.routeRemainingLabel() + " · " + info.routeRemainingMinutes + " 分钟",
                rect.centerX(), contentTop + bodyHeight - 36 * density, 11 * density,
                Color.rgb(184, 207, 220), false, Paint.Align.CENTER);
        text(canvas, "预计 " + info.etaMinutes + " 分钟后到达",
                rect.centerX(), contentTop + bodyHeight - 17 * density, 10 * density,
                Color.rgb(123, 157, 181), false, Paint.Align.CENTER);

        text(canvas, "目的地  " + fit(info.destination, Math.max(1, w - 2 * pad - 80 * density),
                        11 * density, false), pad, h - 14 * density, 11 * density,
                Color.rgb(145, 169, 186), false, Paint.Align.LEFT);
        text(canvas, info.maneuver == NavigationInfo.Maneuver.ARRIVED ? "已到达" : "导航中",
                w - pad, h - 14 * density, 11 * density,
                info.maneuver == NavigationInfo.Maneuver.ARRIVED
                        ? Color.rgb(94, 211, 169) : Color.rgb(145, 169, 186),
                true, Paint.Align.RIGHT);
    }

    private void drawManeuver(Canvas canvas, float cx, float cy, float size,
                              NavigationInfo.Maneuver maneuver) {
        paint.setColor(Color.rgb(94, 211, 169));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(3, size * .09f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        path.reset();
        float half = size * .42f;
        switch (maneuver) {
            case LEFT:
                path.moveTo(cx + half, cy + half * .62f);
                path.lineTo(cx - half * .32f, cy + half * .62f);
                path.lineTo(cx - half * .32f, cy - half * .56f);
                path.moveTo(cx - half * .72f, cy - half * .18f);
                path.lineTo(cx - half * .32f, cy - half * .62f);
                path.lineTo(cx + half * .08f, cy - half * .18f);
                break;
            case RIGHT:
                path.moveTo(cx - half, cy + half * .62f);
                path.lineTo(cx + half * .32f, cy + half * .62f);
                path.lineTo(cx + half * .32f, cy - half * .56f);
                path.moveTo(cx + half * .72f, cy - half * .18f);
                path.lineTo(cx + half * .32f, cy - half * .62f);
                path.lineTo(cx - half * .08f, cy - half * .18f);
                break;
            case U_TURN:
                rect.set(cx - half * .65f, cy - half * .55f, cx + half * .65f, cy + half * .66f);
                canvas.drawArc(rect, 180, -180, false, paint);
                path.moveTo(cx - half * .65f, cy + half * .05f);
                path.lineTo(cx - half * .65f, cy + half * .62f);
                path.lineTo(cx - half * .22f, cy + half * .32f);
                break;
            case ROUNDABOUT:
                canvas.drawCircle(cx, cy, half * .66f, paint);
                path.moveTo(cx, cy - half * .66f);
                path.lineTo(cx + half * .36f, cy - half * .98f);
                path.moveTo(cx, cy - half * .66f);
                path.lineTo(cx - half * .36f, cy - half * .98f);
                break;
            case ARRIVED:
                path.moveTo(cx - half * .75f, cy);
                path.lineTo(cx - half * .18f, cy + half * .52f);
                path.lineTo(cx + half * .82f, cy - half * .62f);
                break;
            case STRAIGHT:
            default:
                path.moveTo(cx, cy + half * .82f);
                path.lineTo(cx, cy - half * .76f);
                path.moveTo(cx, cy - half * .76f);
                path.lineTo(cx - half * .42f, cy - half * .25f);
                path.moveTo(cx, cy - half * .76f);
                path.lineTo(cx + half * .42f, cy - half * .25f);
                break;
        }
        canvas.drawPath(path, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawLaneGuidance(Canvas canvas, float x, float y, float width, float height) {
        if (info.laneCount <= 0) return;
        float laneWidth = Math.max(8 * getResources().getDisplayMetrics().density,
                width / info.laneCount);
        for (int i = 0; i < info.laneCount; i++) {
            float left = x + i * laneWidth + 2;
            float right = x + (i + 1) * laneWidth - 2;
            paint.setColor(i + 1 == info.recommendedLane
                    ? Color.rgb(94, 211, 169) : Color.rgb(46, 72, 88));
            rect.set(left, y, right, y + height);
            canvas.drawRoundRect(rect, 3, 3, paint);
            paint.setColor(i + 1 == info.recommendedLane
                    ? Color.rgb(7, 25, 29) : Color.rgb(139, 162, 174));
            text(canvas, i + 1 == info.recommendedLane ? "↑" : "·",
                    (left + right) / 2, y + height - 4, 12 * getResources().getDisplayMetrics().density,
                    paint.getColor(), true, Paint.Align.CENTER);
        }
    }

    private void panel(Canvas canvas, RectF bounds, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawRoundRect(bounds, 10 * getResources().getDisplayMetrics().density,
                10 * getResources().getDisplayMetrics().density, paint);
    }

    private void text(Canvas canvas, String value, float x, float y, float size, int color,
                      boolean bold, Paint.Align align) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTextAlign(align);
        paint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,
                bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
        canvas.drawText(value == null ? "" : value, x, y, paint);
    }

    private String fit(String value, float maxWidth, float size, boolean bold) {
        String normalized = value == null ? "" : value;
        paint.setTextSize(size);
        paint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,
                bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
        if (paint.measureText(normalized) <= maxWidth) return normalized;
        String suffix = "…";
        int end = normalized.length();
        while (end > 1 && paint.measureText(normalized.substring(0, end - 1) + suffix) > maxWidth) {
            end--;
        }
        return normalized.substring(0, Math.max(1, end - 1)) + suffix;
    }
}
