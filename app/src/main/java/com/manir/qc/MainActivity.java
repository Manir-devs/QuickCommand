package com.manir.qc;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.*;

public class MainActivity extends AppCompatActivity {

    EditText inputCommand;
    Button executeBtn;
    SharedPreferences prefs;

    private RecyclerView chatRecyclerView;
    private MessageAdapter messageAdapter;
    private List<Message> messageList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inputCommand = findViewById(R.id.inputCommand);
        executeBtn = findViewById(R.id.executeBtn);
        prefs = getSharedPreferences("cmd_prefs", MODE_PRIVATE);

        chatRecyclerView = findViewById(R.id.chatRecyclerView);
        messageList = new ArrayList<>();
        messageAdapter = new MessageAdapter(messageList);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatRecyclerView.setAdapter(messageAdapter);

        // Add a welcome message from the app
        addAppMessage("Hi! I'm your Quick Command assistant. How can I help you today?");

        executeBtn.setOnClickListener(v -> {
            String cmd = inputCommand.getText().toString().trim();
            if (!cmd.isEmpty()) {
                addUserMessage(cmd);
                inputCommand.setText(""); // Clear input after sending
                handleCommand(cmd.replaceAll("\\s+", " ").toLowerCase());
            }
        });

        // Request necessary runtime permissions
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.CAMERA // For flashlight
        }, 1);
    }

    private void addUserMessage(String message) {
        messageList.add(new Message(message, Message.TYPE_USER));
        messageAdapter.notifyItemInserted(messageList.size() - 1);
        chatRecyclerView.scrollToPosition(messageList.size() - 1); // Scroll to the latest message
    }

    private void addAppMessage(String message) {
        messageList.add(new Message(message, Message.TYPE_APP));
        messageAdapter.notifyItemInserted(messageList.size() - 1);
        chatRecyclerView.scrollToPosition(messageList.size() - 1); // Scroll to the latest message
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
            addAppMessage("Unknown command: \"" + cmd + "\"");
        }
    }

    void callByName(String name) {
        addAppMessage("Searching for contact: " + name + "...");
        String number = null;
        ContentResolver cr = getContentResolver();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            addAppMessage("READ_CONTACTS permission not granted. Cannot access contacts.");
            return;
        }

        Cursor c = cr.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
        if (c != null) {
            try {
                while (c.moveToNext()) {
                    String contactId = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
                    String displayName = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));

                    if (displayName != null && levenshteinDistance(displayName.toLowerCase(), name.toLowerCase()) <= 2) {
                        int hasNumber = c.getInt(c.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER));
                        if (hasNumber > 0) {
                            Cursor p = cr.query(
                                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                    null,
                                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                                    new String[]{contactId},
                                    null);
                            if (p != null) {
                                try {
                                    if (p.moveToFirst()) {
                                        number = p.getString(p.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
                                    }
                                } finally {
                                    p.close();
                                }
                            }
                        }
                        break;
                    }
                }
            } finally {
                c.close();
            }
        }

        if (number == null) {
            addAppMessage("Contact not found for: " + name);
            return;
        }

        Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number));
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            startActivity(intent);
            addAppMessage("Calling " + name + " (" + number + ")");
            prefs.edit().putInt("last_sim_slot", 0).apply();
        } else {
            addAppMessage("Permission to call not granted. Please enable CALL_PHONE permission.");
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
                        addAppMessage("Wi-Fi " + (state ? "Enabled" : "Disabled"));
                    } else {
                        addAppMessage("Wi-Fi service not available.");
                    }
                } catch (Exception e) {
                    addAppMessage("Wi-Fi toggle failed: " + e.getMessage());
                }
                break;

            case "bluetooth":
                BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();
                if (bluetooth != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            if (state) {
                                if (!bluetooth.isEnabled()) bluetooth.enable();
                            } else {
                                if (bluetooth.isEnabled()) bluetooth.disable();
                            }
                            addAppMessage("Bluetooth " + (state ? "Enabled" : "Disabled"));
                        } else {
                            addAppMessage("Bluetooth Connect permission not granted. Please grant it in settings.");
                        }
                    } else {
                        if (state) {
                            if (!bluetooth.isEnabled()) bluetooth.enable();
                        } else {
                            if (bluetooth.isEnabled()) bluetooth.disable();
                        }
                        addAppMessage("Bluetooth " + (state ? "Enabled" : "Disabled"));
                    }
                } else {
                    addAppMessage("Bluetooth is not available on this device.");
                }
                break;

            case "flashlight":
                try {
                    CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                    if (cm != null) {
                        String[] cameraIds = cm.getCameraIdList();
                        if (cameraIds.length > 0) {
                            cm.setTorchMode(cameraIds[0], state);
                            addAppMessage("Flashlight " + (state ? "On" : "Off"));
                        } else {
                            addAppMessage("No camera found with flashlight capability.");
                        }
                    } else {
                        addAppMessage("Camera service not available.");
                    }
                } catch (Exception e) {
                    addAppMessage("Flashlight error: " + e.getMessage());
                }
                break;

            case "location":
                try {
                    startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    addAppMessage("Opening location settings.");
                } catch (ActivityNotFoundException e) {
                    addAppMessage("Could not open location settings.");
                }
                break;

            default:
                addAppMessage("Feature '" + feature + "' needs root access or is unsupported.");
        }
    }

    void openAppByName(String name) {
        addAppMessage("Searching for app: " + name + "...");
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> apps = pm.queryIntentActivities(intent, 0);

        ResolveInfo bestMatch = null;
        int minDistance = Integer.MAX_VALUE;

        for (ResolveInfo app : apps) {
            String label = app.loadLabel(pm).toString().toLowerCase();
            int distance = levenshteinDistance(label, name.toLowerCase());

            if (label.equals(name.toLowerCase())) {
                minDistance = 0;
                bestMatch = app;
                break;
            } else if (label.contains(name.toLowerCase())) {
                if (distance < minDistance) {
                    minDistance = distance;
                    bestMatch = app;
                }
            } else if (distance < minDistance) {
                minDistance = distance;
                bestMatch = app;
            }
        }

        if (bestMatch != null && minDistance <= 3) {
            Intent launchIntent = pm.getLaunchIntentForPackage(bestMatch.activityInfo.packageName);
            if (launchIntent != null) {
                startActivity(launchIntent);
                addAppMessage("Opening " + bestMatch.loadLabel(pm).toString());
                return;
            }
        }

        addAppMessage("App not found or no good match for: " + name);
    }

    // Levenshtein Distance (Edit Distance) implementation
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                            dp[i - 1][j - 1] + (s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1));
                }
            }
        }
        return dp[s1.length()][s2.length()];
    }

    // The original toast method is no longer used for displaying command results
    // It's still here but not called for the main chat interaction.
    void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}