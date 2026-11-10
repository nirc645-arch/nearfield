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
    private TextView status;
    private volatile byte[] wsc;
    private volatile boolean clearingTag;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        nfc = NfcAdapter.getDefaultAdapter(this);
        getWindow().setStatusBarColor(Color.rgb(18, 18, 18));
        getWindow().setNavigationBarColor(Color.rgb(18, 18, 18));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(32));
        root.setBackgroundColor(0xff121212);

        TextView logo = text("nearfield.", 21, 0xfff7f7f5);
        logo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(logo);

        TextView heading = text("Set up your Wi-Fi stand.", 36, 0xfff7f7f5);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setLetterSpacing(-0.04f);
        root.addView(heading, spacing(42));

        TextView note = text("Add your network details, then tap your phone against the stand.", 15, 0xffa7a7a7);
        note.setLineSpacing(dp(3), 1f);
        root.addView(note, spacing(12));

        root.addView(label("Wi-Fi name"), spacing(34));
        ssid = input("Enter network name");
        root.addView(ssid, inputSpacing());

        root.addView(label("Password"), spacing(20));
        password = input("Enter password");
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setTypeface(Typeface.DEFAULT);
        root.addView(password, inputSpacing());

        Button write = button("Write to NFC stand", true);
        root.addView(write, spacing(28));
        Button clear = button("Clear NFC tag", false);
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
                .setTitle("Clear NFC tag?")
                .setMessage("This removes the current contents of the tag.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear tag", (dialog, which) -> prepareClear())
                .show());

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.addView(root);
        setContentView(scrollView);
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
        view.setMinHeight(dp(54));
        view.setTextColor(primary ? 0xff111111 : 0xffeeeeec);
        view.setBackground(background(primary ? 0xfff4f4f1 : 0xff121212,
                primary ? 0 : 0xff454545));
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
        if (name.isEmpty()) { ssid.setError("Enter the Wi-Fi name"); return; }
        if (nfc == null || !nfc.isEnabled()) { status.setText("Turn on NFC in phone settings."); return; }
        try {
            clearingTag = false;
            wsc = makeCredential(name, key);
            nfc.enableReaderMode(this, this, NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B, null);
            status.setText("Ready. Hold your phone against the stand.");
        } catch (Exception e) { status.setText("Could not prepare the Wi-Fi details."); }
    }

    private void prepareClear() {
        if (nfc == null || !nfc.isEnabled()) { status.setText("Turn on NFC in phone settings."); return; }
        wsc = null;
        clearingTag = true;
        nfc.enableReaderMode(this, this, NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B, null);
        status.setText("Ready to clear. Hold your phone against the tag.");
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

        TextView message = text("Saved to tag", 16, 0xfff7f7f5);
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

    private void show(String text) { runOnUiThread(() -> status.setText(text)); }
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
