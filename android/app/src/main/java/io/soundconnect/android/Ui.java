package io.soundconnect.android;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.util.function.IntConsumer;

/** Sound Connect reference geometry and resource tokens, using API-28-safe native views. */
final class Ui {
    final Context context;
    Ui(Context context) { this.context = context; }
    int dim(int id) { return context.getResources().getDimensionPixelSize(id); }
    int color(int id) { return context.getColor(id); }
    LinearLayout column() { LinearLayout v = new LinearLayout(context); v.setOrientation(LinearLayout.VERTICAL); return v; }
    LinearLayout horizontal() { LinearLayout v = new LinearLayout(context); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    LinearLayout.LayoutParams params() { return new LinearLayout.LayoutParams(-1, -2); }
    void padding(View v, int resource) { int n = dim(resource); v.setPadding(n, n, n, n); }
    TextView text(String value, int size) {
        TextView v = new TextView(context); v.setText(value); v.setTextColor(color(R.color.text));
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimension(size));
        v.setFontFeatureSettings("kern"); return v;
    }
    TextView centered(String value, int size) { TextView v = text(value, size); v.setGravity(Gravity.CENTER); return v; }
    GradientDrawable shape(int background, boolean outline) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color(background)); d.setCornerRadius(dim(R.dimen.radius));
        if (outline) d.setStroke(dim(R.dimen.stroke), color(R.color.outline)); return d;
    }
    void clickable(View v, int background, boolean outline, Runnable action) {
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(color(R.color.ripple)), shape(background, outline), null));
        v.setFocusable(true); v.setOnClickListener(w -> action.run());
    }
    TextView button(String value, boolean primary, Runnable action) {
        TextView v = centered(value, R.dimen.body); v.setMinHeight(dim(R.dimen.touch_target)); padding(v, R.dimen.gap);
        v.setTextColor(color(primary ? R.color.on_accent : R.color.accent));
        clickable(v, primary ? R.color.accent : R.color.card, !primary, action); return v;
    }
    LinearLayout card(LinearLayout parent) {
        LinearLayout v = column(); padding(v, R.dimen.gutter); v.setBackground(shape(R.color.card, false));
        LinearLayout.LayoutParams p = params(); p.bottomMargin = dim(R.dimen.gap); parent.addView(v, p); return v;
    }
    TextView body(LinearLayout parent, String message) {
        TextView v = text(message, R.dimen.caption); v.setTextColor(color(R.color.secondary));
        v.setLineSpacing(dim(R.dimen.gap) / 2f, 1); parent.addView(v, params()); return v;
    }
    void space(LinearLayout parent, int resource) { parent.addView(new View(context), new LinearLayout.LayoutParams(1, dim(resource))); }
    LinearLayout row(LinearLayout parent, String title, String summary, Runnable action) {
        LinearLayout row = horizontal(); row.setMinimumHeight(dim(R.dimen.toolbar));
        LinearLayout labels = column(); TextView t = text(title, R.dimen.body); labels.addView(t);
        if (summary != null && !summary.isEmpty()) body(labels, summary);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        if (action != null) {
            row.addView(new Glyph(context, "chevron", false), new LinearLayout.LayoutParams(dim(R.dimen.icon), dim(R.dimen.icon)));
            clickable(row, R.color.card, false, action);
        }
        parent.addView(row, params()); return row;
    }
    void toggle(LinearLayout parent, String title, String subtitle, boolean value, IntConsumer changed) {
        LinearLayout row = horizontal(); row.setMinimumHeight(dim(R.dimen.toolbar));
        LinearLayout labels = column(); labels.addView(text(title, R.dimen.body));
        if (subtitle != null) body(labels, subtitle);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        Switch toggle = new Switch(context); toggle.setContentDescription(title); toggle.setChecked(value);
        toggle.setMinimumHeight(dim(R.dimen.touch_target)); row.addView(toggle);
        toggle.setOnCheckedChangeListener((v, checked) -> changed.accept(checked ? 1 : 0)); parent.addView(row, params());
    }
    View icon(String name, String description, Runnable action) {
        FrameLayout frame = new FrameLayout(context); frame.setContentDescription(description);
        Glyph glyph = new Glyph(context, name, false);
        frame.addView(glyph, new FrameLayout.LayoutParams(dim(R.dimen.icon), dim(R.dimen.icon), Gravity.CENTER));
        frame.setMinimumWidth(dim(R.dimen.touch_target)); frame.setMinimumHeight(dim(R.dimen.touch_target));
        frame.setBackground(new RippleDrawable(ColorStateList.valueOf(color(R.color.ripple)), null, shape(R.color.card,false)));
        frame.setFocusable(true);frame.setOnClickListener(v->action.run());return frame;
    }
    void choices(LinearLayout parent, int selected, IntConsumer action, boolean cancel, boolean ambient) {
        LinearLayout row = horizontal(); String[] titles = {"ノイズキャン\nセリング", "外音取り込み", "オフ"};
        String[] icons = {"noise", "ambient", "off"}; int[] modes = {1, 2, 0};
        for (int i = 0; i < titles.length; i++) {
            final int mode = modes[i];
            LinearLayout item = column(); item.setGravity(Gravity.CENTER);
            FrameLayout circle = new FrameLayout(context); boolean chosen = mode == selected;
            GradientDrawable circleShape=shape(chosen ? R.color.accent : R.color.card, !chosen); circleShape.setShape(GradientDrawable.OVAL); circle.setBackground(circleShape);
            circle.addView(new Glyph(context, icons[i], chosen).tone(R.color.text), new FrameLayout.LayoutParams(dim(R.dimen.icon), dim(R.dimen.icon), Gravity.CENTER));
            item.addView(circle, new LinearLayout.LayoutParams(dim(R.dimen.touch_target), dim(R.dimen.touch_target)));
            TextView caption = centered(titles[i], R.dimen.small); LinearLayout.LayoutParams cp = params(); cp.topMargin = dim(R.dimen.gap); item.addView(caption, cp);
            item.setSelected(chosen); item.setContentDescription(titles[i].replace('\n', ' ') + (chosen ? "、選択中" : ""));
            boolean supported = mode == 0 || mode == 1 && cancel || mode == 2 && ambient;
            item.setEnabled(supported); item.setAlpha(supported ? 1 : 0.35f);
            clickable(item, R.color.card, false, () -> { if (supported) action.accept(mode); });
            row.addView(item, new LinearLayout.LayoutParams(0, dim(R.dimen.choice), 1));
        }
        parent.addView(row, params());
    }

    static final class Glyph extends View {
        private final String kind; private final boolean inverse; private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG); private int tone;
        Glyph(Context c, String kind, boolean inverse) { super(c); this.kind = kind; this.inverse = inverse; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        Glyph tone(int resource) { tone=resource;return this; }
        private void line(Canvas c, float x, float y, float xx, float yy) { c.drawLine(x,y,xx,yy,pen); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); canvas.save(); canvas.scale(getWidth()/24f, getHeight()/24f);
            boolean neutral=kind.equals("power")||kind.equals("more")||kind.equals("back")||kind.equals("chevron");
            pen.setColor(getContext().getColor(inverse ? R.color.on_accent : tone!=0 ? tone : neutral ? R.color.text : R.color.accent)); pen.setStrokeWidth(1.8f);
            pen.setStyle(Paint.Style.STROKE); pen.setStrokeCap(Paint.Cap.ROUND); pen.setStrokeJoin(Paint.Join.ROUND);
            switch (kind) {
                case "headphones": canvas.drawArc(4,3,20,21,180,180,false,pen); canvas.drawRoundRect(3,12,7,21,2,2,pen); canvas.drawRoundRect(17,12,21,21,2,2,pen); break;
                case "noise": case "ambient":
                    canvas.drawCircle(12,9,3,pen); canvas.drawArc(6,13,18,24,190,160,false,pen);
                    canvas.drawArc(2,1,22,23,205,130,false,pen);
                    if (kind.equals("ambient")) { line(canvas,1,9,3,9); line(canvas,21,9,23,9); line(canvas,5,2,6,4); line(canvas,18,4,19,2); }
                    break;
                case "off": canvas.drawCircle(12,12,7,pen); break;
                case "power": canvas.drawArc(4,4,20,21,-50,280,false,pen); line(canvas,12,2,12,11); break;
                case "back": line(canvas,15,4,7,12); line(canvas,7,12,15,20); break;
                case "chevron": line(canvas,9,5,16,12); line(canvas,16,12,9,19); break;
                case "more": pen.setStyle(Paint.Style.FILL); for(int y=5;y<21;y+=7) canvas.drawCircle(12,y,1.3f,pen); break;
                case "menu": for(int y=6;y<20;y+=6) line(canvas,4,y,20,y); break;
                case "settings":
                    canvas.drawCircle(12,12,6,pen);canvas.drawCircle(12,12,2,pen);
                    for(int i=0;i<8;i++){double angle=i*Math.PI/4;line(canvas,12+(float)Math.cos(angle)*6,12+(float)Math.sin(angle)*6,12+(float)Math.cos(angle)*9,12+(float)Math.sin(angle)*9);}break;
                case "discover": canvas.drawCircle(6,14,4,pen); canvas.drawCircle(18,14,4,pen); line(canvas,10,13,14,13); line(canvas,3,11,6,5); line(canvas,21,11,18,5); break;
                case "walk": canvas.drawCircle(13,4,1.7f,pen); line(canvas,12,8,10,14); line(canvas,12,9,16,12); line(canvas,11,11,7,13); line(canvas,10,14,15,17); line(canvas,15,17,17,22); line(canvas,10,14,8,21); break;
                case "play": case "next": case "previous":
                    pen.setStyle(Paint.Style.FILL); Path triangle = new Path(); boolean previous = kind.equals("previous");
                    triangle.moveTo(previous?18:6,4); triangle.lineTo(previous?5:19,12); triangle.lineTo(previous?18:6,20); triangle.close(); canvas.drawPath(triangle,pen);
                    if(!kind.equals("play")) line(canvas,previous?4:20,4,previous?4:20,20); break;
                case "pause": pen.setStrokeWidth(4); line(canvas,8,5,8,19); line(canvas,16,5,16,19); break;
                case "volume": Path speaker = new Path(); speaker.moveTo(3,9); speaker.lineTo(7,9); speaker.lineTo(12,4); speaker.lineTo(12,20); speaker.lineTo(7,15); speaker.lineTo(3,15); speaker.close(); canvas.drawPath(speaker,pen); canvas.drawArc(10,6,21,18,-65,130,false,pen); break;
                default: canvas.drawCircle(12,12,8,pen); line(canvas,12,7,12,13); canvas.drawPoint(12,17,pen);
            }
            canvas.restore();
        }
    }

    static final class EqualizerChart extends View {
        private final int[] bands; private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
        EqualizerChart(Context c, int[] bands) { super(c); this.bands = bands.clone(); setContentDescription("イコライザーの周波数特性"); }
        @Override protected void onDraw(Canvas c) {
            float pad=getResources().getDimension(R.dimen.gutter), top=pad, bottom=getHeight()-pad*1.5f, width=getWidth()-pad*2;
            pen.setStrokeWidth(getResources().getDimension(R.dimen.stroke)); pen.setColor(getContext().getColor(R.color.outline));
            for(int i=0;i<3;i++) c.drawLine(pad,top+(bottom-top)*i/2f,getWidth()-pad,top+(bottom-top)*i/2f,pen);
            pen.setTextSize(getResources().getDimension(R.dimen.small)); pen.setTextAlign(Paint.Align.CENTER);
            String[] labels={"400", "1k", "2.5k", "6.3k", "16k"};
            for(int i=0;i<bands.length;i++) {
                float x=pad+width*(i+0.5f)/Math.max(1,bands.length), y=(top+bottom)/2-bands[i]*(bottom-top)/20f;
                pen.setColor(getContext().getColor(R.color.accent)); pen.setStrokeWidth(getResources().getDimension(R.dimen.stroke)*3);
                c.drawLine(x-pad/2,y,x+pad/2,y,pen);
                if(getHeight()>getResources().getDimension(R.dimen.chart_preview)) {
                    pen.setColor(getContext().getColor(R.color.secondary)); c.drawText(bands.length==5?labels[i]:String.valueOf(i+1),x,getHeight()-pad/4,pen);
                }
            }
        }
    }
}
