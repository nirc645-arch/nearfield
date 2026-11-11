package com.nearfield.setup;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.nfc.tech.Ndef;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class MainActivity extends Activity implements NfcAdapter.ReaderCallback {
    private NfcAdapter nfc;
    private EditText ssid, password;
    private TextView status, heading, note, ssidLabel, passwordLabel;
    private Button write, clear, englishButton, hebrewButton;
    private LinearLayout root;
    private boolean hebrew;
    private String statusEnglish = "Ready when you are.";
    private volatile byte[] wsc;
    private volatile boolean clearingTag;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        nfc = NfcAdapter.getDefaultAdapter(this);
        getWindow().setStatusBarColor(Color.rgb(18, 18, 18));
        getWindow().setNavigationBarColor(Color.rgb(18, 18, 18));

        hebrew = getPreferences(MODE_PRIVATE).getBoolean("hebrew", false);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(32));
        root.setBackgroundColor(0xff121212);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        TextView logo = text("nearfield.", 21, 0xfff7f7f5);
        logo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(logo, new LinearLayout.LayoutParams(0, -2, 1));
        englishButton = languageButton("EN");
        hebrewButton = languageButton("עבר");
        header.addView(englishButton);
        header.addView(hebrewButton);
        root.addView(header);
        englishButton.setOnClickListener(v -> setLanguage(false));
        hebrewButton.setOnClickListener(v -> setLanguage(true));

        heading = text("Set up your Wi-Fi stand.", 36, 0xfff7f7f5);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setLetterSpacing(-0.04f);
        root.addView(heading, spacing(42));

        note = text("Add your network details, then tap your phone against the stand.", 15, 0xffa7a7a7);
        note.setLineSpacing(dp(3), 1f);
        root.addView(note, spacing(12));

        ssidLabel = label("Wi-Fi name");
        root.addView(ssidLabel, spacing(34));
        ssid = input("Enter network name");
        root.addView(ssid, inputSpacing());

        passwordLabel = label("Password");
        root.addView(passwordLabel, spacing(20));
        password = input("Enter password");
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setTypeface(Typeface.DEFAULT);
        root.addView(password, inputSpacing());

        write = button("Write to NFC stand", true);
        root.addView(write, spacing(28));
        clear = button("Clear NFC tag", false);
        root.addView(clear, spacing(10));

        status = text("Ready when you are.", 13, 0xffaaaaaa);
        status.setLineSpacing(dp(2), 1f);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        root.addView(status, spacing(20));

        write.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            prepare();
        });
        clear.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(t("Clear NFC tag?"))
                .setMessage(t("This removes the current contents of the tag."))
                .setNegativeButton(t("Cancel"), null)
                .setPositiveButton(t("Clear tag"), (dialog, which) -> prepareClear())
                .show());

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.addView(root);
        FrameLayout screen = new FrameLayout(this);
        screen.addView(scrollView);
        setContentView(screen);
        setLanguage(hebrew);
        if (state == null && android.animation.ValueAnimator.areAnimatorsEnabled()) {
            showIntro(screen, scrollView);
        }
    }

    private void showIntro(FrameLayout screen, View content) {
        FrameLayout intro = new FrameLayout(this);
        intro.setBackgroundColor(0xff121212);
        intro.setClickable(true);
        intro.setContentDescription("Nearfield");
        intro.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        content.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        LinearLayout mark = new LinearLayout(this);
        mark.setOrientation(LinearLayout.VERTICAL);
        mark.setGravity(android.view.Gravity.CENTER);

        IntroMark icon = new IntroMark();
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        mark.addView(icon, new LinearLayout.LayoutParams(dp(190), dp(190)));

        TextView name = text("nearfield.", 22, 0xfff7f7f5);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-2, -2);
        nameParams.topMargin = dp(8);
        mark.addView(name, nameParams);

        FrameLayout.LayoutParams center = new FrameLayout.LayoutParams(-2, -2, android.view.Gravity.CENTER);
        intro.addView(mark, center);
        screen.addView(intro, new FrameLayout.LayoutParams(-1, -1));

        mark.setAlpha(0f);
        mark.setScaleX(0.88f);
        mark.setScaleY(0.88f);
        mark.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(260)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(() -> intro.animate().alpha(0f)
                        .setStartDelay(1100)
                        .setDuration(320)
                        .withEndAction(() -> {
                            screen.removeView(intro);
                            content.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
                        }).start())
                .start();
    }

    private class IntroMark extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path letter = new android.graphics.Path();
        private final android.graphics.Path[] waves = new android.graphics.Path[3];
        private final android.graphics.Path visible = new android.graphics.Path();
        private final android.graphics.PathMeasure measure = new android.graphics.PathMeasure();
        private final android.animation.ValueAnimator animator;
        private final android.graphics.Shader silver = new android.graphics.LinearGradient(
                30, 65, 150, 185, 0xffffffff, 0xffbfc2bf,
                android.graphics.Shader.TileMode.CLAMP);
        private final android.graphics.Shader green = new android.graphics.LinearGradient(
                155, 55, 220, 175, 0xffc1ff94, 0xff65d823,
                android.graphics.Shader.TileMode.CLAMP);
        private float progress;

        IntroMark() {
            super(MainActivity.this);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(17f);
            paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);

            letter.moveTo(37, 170);
            letter.lineTo(68, 74);
            letter.lineTo(142, 165);
            letter.quadTo(151, 174, 155, 156);
            letter.lineTo(161, 114);

            waves[0] = new android.graphics.Path();
            waves[0].moveTo(162, 92);
            waves[0].quadTo(185, 112, 169, 137);
            waves[1] = new android.graphics.Path();
            waves[1].moveTo(179, 72);
            waves[1].quadTo(220, 111, 187, 154);
            waves[2] = new android.graphics.Path();
            waves[2].moveTo(195, 52);
            waves[2].quadTo(253, 111, 204, 174);

            animator = android.animation.ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(1200);
            animator.setInterpolator(new android.view.animation.LinearInterpolator());
            animator.addUpdateListener(animation -> {
                progress = (float) animation.getAnimatedValue();
                invalidate();
            });
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            animator.start();
        }

        @Override protected void onDetachedFromWindow() {
            animator.cancel();
            super.onDetachedFromWindow();
        }

        @Override protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            float size = Math.min(getWidth(), getHeight());
            canvas.save();
            canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
            canvas.scale(size / 250f, size / 250f);
            drawSegment(canvas, letter, Math.min(1f, progress / 0.55f), false);
            for (int i = 0; i < waves.length; i++) {
                float start = 0.48f + i * 0.12f;
                float waveProgress = Math.max(0f, Math.min(1f, (progress - start) / 0.25f));
                drawSegment(canvas, waves[i], waveProgress, true);
            }
            canvas.restore();
        }

        private void drawSegment(android.graphics.Canvas canvas, android.graphics.Path path,
                                 float fraction, boolean signal) {
            if (fraction <= 0f) return;
            float eased = fraction * fraction * (3f - 2f * fraction);
            measure.setPath(path, false);
            visible.reset();
            measure.getSegment(0f, measure.getLength() * eased, visible, true);
            paint.setShader(signal ? green : silver);
            if (signal) paint.setShadowLayer(5f, 0f, 1f, 0x8867d534);
            else paint.setShadowLayer(2f, 0f, 1f, 0x557f857f);
            canvas.drawPath(visible, paint);
            paint.clearShadowLayer();
        }
    }

    private Button languageButton(String language) {
        Button view = new Button(this);
        view.setText(language);
        view.setAllCaps(false);
        view.setTextSize(12);
        view.setMinWidth(dp(44));
        view.setMinHeight(dp(44));
        view.setPadding(dp(8), 0, dp(8), 0);
        view.setBackgroundColor(Color.TRANSPARENT);
        return view;
    }

    private void setLanguage(boolean useHebrew) {
        hebrew = useHebrew;
        getPreferences(MODE_PRIVATE).edit().putBoolean("hebrew", hebrew).apply();
        root.setLayoutDirection(hebrew ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        heading.setText(t("Set up your Wi-Fi stand."));
        heading.setLetterSpacing(hebrew ? 0 : -0.04f);
        note.setText(t("Add your network details, then tap your phone against the stand."));
        ssidLabel.setText(t("Wi-Fi name"));
        passwordLabel.setText(t("Password"));
        ssid.setHint(t("Enter network name"));
        password.setHint(t("Enter password"));
        write.setText(t("Write to NFC stand"));
        clear.setText(t("Clear NFC tag"));
        status.setText(t(statusEnglish));
        englishButton.setTextColor(hebrew ? 0xff888888 : 0xfff7f7f5);
        hebrewButton.setTextColor(hebrew ? 0xfff7f7f5 : 0xff888888);
        englishButton.setContentDescription(hebrew ? "אנגלית" : "English");
        hebrewButton.setContentDescription(hebrew ? "עברית" : "Hebrew");
    }

    private void setStatus(String value) {
        statusEnglish = value;
        status.setText(t(value));
    }

    private String t(String value) {
        if (!hebrew) return value;
        switch (value) {
            case "Set up your Wi-Fi stand.": return "הגדירו את מעמד ה-Wi-Fi שלכם.";
            case "Add your network details, then tap your phone against the stand.": return "הזינו את פרטי הרשת והצמידו את הטלפון למעמד.";
            case "Wi-Fi name": return "שם רשת ה-Wi-Fi";
            case "Password": return "סיסמה";
            case "Enter network name": return "הזינו את שם הרשת";
            case "Enter password": return "הזינו סיסמה";
            case "Write to NFC stand": return "כתיבה למעמד ה-NFC";
            case "Clear NFC tag": return "מחיקת תג ה-NFC";
            case "Ready when you are.": return "מוכנים להתחיל.";
            case "Clear NFC tag?": return "למחוק את תג ה-NFC?";
            case "This removes the current contents of the tag.": return "הפעולה תמחק את התוכן הנוכחי של התג.";
            case "Cancel": return "ביטול";
            case "Clear tag": return "מחיקת התג";
            case "Enter the Wi-Fi name": return "הזינו את שם רשת ה-Wi-Fi";
            case "Turn on NFC in phone settings.": return "יש להפעיל NFC בהגדרות הטלפון.";
            case "Ready. Hold your phone against the stand.": return "מוכן. הצמידו את הטלפון למעמד.";
            case "Could not prepare the Wi-Fi details.": return "לא הצלחנו להכין את פרטי הרשת.";
            case "Ready to clear. Hold your phone against the tag.": return "מוכן למחיקה. הצמידו את הטלפון לתג.";
            case "This tag does not have a writable NDEF record.": return "לא ניתן לכתוב לתג הזה.";
            case "This NFC tag is locked.": return "תג ה-NFC הזה נעול.";
            case "This tag does not have enough space.": return "אין מספיק מקום בתג.";
            case "Tag cleared. You can write new details now.": return "התג נמחק. אפשר לכתוב פרטים חדשים.";
            case "Done. Your stand is ready for customers.": return "ההגדרה הושלמה. המעמד מוכן ללקוחות.";
            case "Could not write this tag. Try another NFC tag.": return "לא הצלחנו לכתוב לתג. נסו תג NFC אחר.";
            case "Saved to tag": return "נשמר בתג";
            default: return value;
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private TextView label(String value) {
        TextView view = text(value, 13, 0xffeeeeec);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable background(int color, int border) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(10));
        if (border != 0) drawable.setStroke(dp(1), border);
        return drawable;
    }

    private EditText input(String hint) {
        EditText view = new EditText(this);
        view.setSingleLine(true);
        view.setHint(hint);
        view.setTextSize(16);
        view.setTextColor(0xfff7f7f5);
        view.setHintTextColor(0xff777777);
        view.setPadding(dp(16), 0, dp(16), 0);
        view.setMinHeight(dp(54));
        view.setBackground(background(0xff1c1c1c, 0xff3a3a3a));
        return view;
    }

    private Button button(String title, boolean primary) {
        Button view = new Button(this);
        view.setText(title);
        view.setAllCaps(false);
        view.setTextSize(14);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setMinHeight(dp(56));
        view.setPadding(dp(18), 0, dp(18), 0);
        view.setTextColor(primary ? 0xff111111 : 0xffeeeeec);

        GradientDrawable surface = background(primary ? 0xfff4f4f1 : 0xff1b1b1b,
                primary ? 0 : 0xff454545);
        int rippleColor = primary ? 0x3383c95e : 0x33ffffff;
        view.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(rippleColor), surface, null));
        view.setElevation(dp(primary ? 2 : 0));
        return view;
    }

    private LinearLayout.LayoutParams spacing(int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        return params;
    }

    private LinearLayout.LayoutParams inputSpacing() {
        return spacing(8);
    }

    private void prepare() {
        String name = ssid.getText().toString(), key = password.getText().toString();
        if (name.isEmpty()) { ssid.setError(t("Enter the Wi-Fi name")); return; }
        if (nfc == null || !nfc.isEnabled()) { setStatus("Turn on NFC in phone settings."); return; }
        try {
            clearingTag = false;
            wsc = makeCredential(name, key);
            nfc.enableReaderMode(this, this, NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B, null);
            setStatus("Ready. Hold your phone against the stand.");
        } catch (Exception e) { setStatus("Could not prepare the Wi-Fi details."); }
    }

    private void prepareClear() {
        if (nfc == null || !nfc.isEnabled()) { setStatus("Turn on NFC in phone settings."); return; }
        wsc = null;
        clearingTag = true;
        nfc.enableReaderMode(this, this, NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B, null);
        setStatus("Ready to clear. Hold your phone against the tag.");
    }

    @Override public void onTagDiscovered(Tag tag) {
        boolean clear = clearingTag;
        byte[] credential = wsc;
        if (!clear && credential == null) return;
        Ndef ndef = Ndef.get(tag);
        if (ndef == null) { show("This tag does not have a writable NDEF record."); return; }
        try {
            ndef.connect();
            NdefRecord record = clear
                    ? new NdefRecord(NdefRecord.TNF_EMPTY, new byte[0], new byte[0], new byte[0])
                    : new NdefRecord(NdefRecord.TNF_MIME_MEDIA, "application/vnd.wfa.wsc".getBytes("US-ASCII"), new byte[0], credential);
            NdefMessage message = new NdefMessage(new NdefRecord[]{record});
            if (!ndef.isWritable()) { show("This NFC tag is locked."); return; }
            if (message.toByteArray().length > ndef.getMaxSize()) { show("This tag does not have enough space."); return; }
            ndef.writeNdefMessage(message);
            wsc = null; clearingTag = false; nfc.disableReaderMode(this);
            show(clear ? "Tag cleared. You can write new details now." : "Done. Your stand is ready for customers.");
            if (!clear) runOnUiThread(this::showWriteSuccess);
        } catch (Exception e) { show("Could not write this tag. Try another NFC tag."); }
        finally { try { ndef.close(); } catch (Exception ignored) {} }
    }

    private void showWriteSuccess() {
        if (isFinishing() || isDestroyed()) return;

        Dialog dialog = new Dialog(this);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(android.view.Gravity.CENTER);
        card.setPadding(dp(40), dp(26), dp(40), dp(28));
        card.setBackground(background(0xff232323, 0xff454545));

        TextView check = text("✓", 54, 0xffb4ff78);
        check.setGravity(android.view.Gravity.CENTER);
        card.addView(check);

        TextView message = text(t("Saved to tag"), 16, 0xfff7f7f5);
        message.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        message.setGravity(android.view.Gravity.CENTER);
        card.addView(message, spacing(8));

        dialog.setContentView(card);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();

        card.setAlpha(0f);
        card.setScaleX(0.85f);
        card.setScaleY(0.85f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start();
        card.postDelayed(() -> {
            if (dialog.isShowing()) dialog.dismiss();
        }, 1800);
    }

    private void show(String text) { runOnUiThread(() -> setStatus(text)); }
    @Override protected void onPause() { super.onPause(); if (nfc != null) nfc.disableReaderMode(this); }

    private static byte[] makeCredential(String name, String password) throws IOException {
        ByteArrayOutputStream inner = new ByteArrayOutputStream();
        tlv(inner, 0x1026, new byte[]{1});
        tlv(inner, 0x1045, name.getBytes("UTF-8"));
        tlv(inner, 0x1003, new byte[]{0, 0x20});
        tlv(inner, 0x100f, new byte[]{0, 0x08});
        tlv(inner, 0x1020, new byte[]{-1, -1, -1, -1, -1, -1});
        if (!password.isEmpty()) tlv(inner, 0x1027, password.getBytes("UTF-8"));
        ByteArrayOutputStream outer = new ByteArrayOutputStream();
        tlv(outer, 0x100e, inner.toByteArray());
        return outer.toByteArray();
    }

    private static void tlv(ByteArrayOutputStream out, int type, byte[] value) {
        out.write((type >> 8) & 255); out.write(type & 255);
        out.write((value.length >> 8) & 255); out.write(value.length & 255);
        out.write(value, 0, value.length);
    }
}
