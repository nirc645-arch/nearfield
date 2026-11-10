package com.nearfield.setup;

import android.app.Activity;
import android.app.AlertDialog;
import android.nfc.tech.Ndef;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.graphics.Typeface;
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 48, 28, 24);
        root.setBackgroundColor(0xfffafafa);
        TextView logo = new TextView(this); logo.setText("nearfield."); logo.setTextSize(21); logo.setTypeface(null, Typeface.BOLD); root.addView(logo);
        TextView title = new TextView(this); title.setText("Put your Wi-Fi\non the stand."); title.setTextSize(34); title.setTypeface(null, Typeface.BOLD); title.setPadding(0, 40, 0, 8); root.addView(title);
        TextView note = new TextView(this); note.setText("Enter your network details, then hold the phone against the NFC tag."); note.setTextSize(14); note.setTextColor(0xff666666); root.addView(note);
        ssid = new EditText(this); ssid.setSingleLine(true); ssid.setHint("Wi-Fi name (SSID)"); root.addView(ssid, fieldParams());
        password = new EditText(this); password.setSingleLine(true); password.setHint("Wi-Fi password"); password.setInputType(129); root.addView(password, fieldParams());
        Button button = new Button(this); button.setText("Write to NFC stand"); root.addView(button, fieldParams());
        Button clearButton = new Button(this); clearButton.setText("Clear NFC tag"); root.addView(clearButton, fieldParams());
        status = new TextView(this); status.setText("Network details are used to write the tag."); status.setTextColor(0xff777777); status.setPadding(0, 18, 0, 0); root.addView(status);
        button.setOnClickListener(v -> prepare());
        clearButton.setOnClickListener(v -> new AlertDialog.Builder(this)
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

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = 16; return p;
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
        } catch (Exception e) { show("Could not write this tag. Try another NFC tag."); }
        finally { try { ndef.close(); } catch (Exception ignored) {} }
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
