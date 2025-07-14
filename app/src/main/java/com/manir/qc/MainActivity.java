package com.manir.qc;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.*;

public class MainActivity extends AppCompatActivity {

    EditText inputCommand;
    Button executeBtn;
    SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        inputCommand = findViewById(R.id.inputCommand);
        executeBtn = findViewById(R.id.executeBtn);
        prefs = getSharedPreferences("cmd_prefs", MODE_PRIVATE);

        executeBtn.setOnClickListener(v -> {
            String cmd = inputCommand.getText().toString().trim().replaceAll("\\s+", " ").toLowerCase();
            handleCommand(cmd);
        });

        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.BLUETOOTH_CONNECT
        }, 1);
    }

    void handleCommand(String cmd) {
        if (cmd.startsWith("call ")) {
            String name = cmd.substring(5).trim();
            callByName(name);
        } else if (cmd.startsWith("turn on ")) {
            toggleFeature(cmd.substring(8).trim(), true);
        } else if (cmd.startsWith("turn off ")) {
            toggleFeature(cmd.substring(9).trim(), false);
        } else if (cmd.startsWith("open ")) {
            openAppByName(cmd.substring(5).trim());
        } else {
            toast("Unknown command: " + cmd);
        }
    }

    void callByName(String name) {
        String number = null;
        ContentResolver cr = getContentResolver();
        Cursor c = cr.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
        if (c != null) {
            while (c.moveToNext()) {
                String contactId = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
                String displayName = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));
                if (displayName != null && displayName.equalsIgnoreCase(name)) {
                    int hasNumber = c.getInt(c.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER));
                    if (hasNumber > 0) {
                        Cursor p = cr.query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                null,
                                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                                new String[]{contactId},
                                null);
                        if (p != null && p.moveToFirst()) {
                            number = p.getString(p.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
                            p.close();
                        }
                    }
                    break;
                }
            }
            c.close();
        }

        if (number == null) {
            toast("Contact not found: " + name);
            return;
        }

        Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number));
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            startActivity(intent);
            prefs.edit().putInt("last_sim_slot", 0).apply();
        }
    }

    void toggleFeature(String feature, boolean state) {
        feature = feature.toLowerCase();
        switch (feature) {
            case "wifi":
                try {
                    WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    if (wifi != null) {
                        wifi.setWifiEnabled(state);
                        toast("Wi-Fi " + (state ? "Enabled" : "Disabled"));
                    }
                } catch (Exception e) {
                    toast("Wi-Fi toggle failed");
                }
                break;

            case "bluetooth":
                BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();
                if (bluetooth != null) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        if (state) bluetooth.enable();
                        else bluetooth.disable();
                        toast("Bluetooth " + (state ? "Enabled" : "Disabled"));
                    } else {
                        toast("Bluetooth permission not granted");
                    }
                }
                break;

            case "flashlight":
                try {
                    CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
                    String camId = cm.getCameraIdList()[0];
                    cm.setTorchMode(camId, state);
                    toast("Flashlight " + (state ? "On" : "Off"));
                } catch (Exception e) {
                    toast("Flashlight error");
                }
                break;

            case "location":
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                break;

            default:
                toast("Feature needs root or unsupported: " + feature);
        }
    }

    void openAppByName(String name) {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(intent, 0);

        ResolveInfo bestMatch = null;
        int bestScore = 0;

        for (ResolveInfo app : apps) {
            String label = app.loadLabel(pm).toString().toLowerCase();
            int score = matchScore(label, name.toLowerCase());
            if (score > bestScore) {
                bestScore = score;
                bestMatch = app;
            }
        }

        if (bestMatch != null) {
            Intent launchIntent = pm.getLaunchIntentForPackage(bestMatch.activityInfo.packageName);
            if (launchIntent != null) {
                startActivity(launchIntent);
                return;
            }
        }

        toast("App not found: " + name);
    }

    int matchScore(String a, String b) {
        if (a.equals(b)) return 100;
        if (a.contains(b)) return 80;
        int score = 0;
        for (char c : b.toCharArray()) {
            if (a.indexOf(c) != -1) score++;
        }
        return score;
    }

    void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
