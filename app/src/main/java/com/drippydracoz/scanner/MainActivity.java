package com.drippydracoz.scanner;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.TrafficStats;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.widget.*;
import android.graphics.Color;
import android.view.Gravity;
import android.text.InputType;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class MainActivity extends Activity {

    private TextView results, status;
    private EditText serverInput, codeInput;
    private Button pairButton;

    private final ExecutorService executor =
        Executors.newSingleThreadExecutor();

    private static final String ALIAS = "DrippyCompanionKey";
    private static final String PREFS = "drippy_secure";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(28, 35, 28, 28);
        layout.setBackgroundColor(Color.rgb(10, 10, 18));

        TextView title = new TextView(this);
        title.setText("DRIPPYDRACOZ");
        title.setTextColor(Color.rgb(0, 255, 170));
        title.setTextSize(27);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("ANDROID COMPANION");
        subtitle.setTextColor(Color.WHITE);
        subtitle.setGravity(Gravity.CENTER);
        layout.addView(subtitle);

        addLabel(layout, "WEBSITE HTTPS URL");

        serverInput = new EditText(this);
        serverInput.setSingleLine(true);
        serverInput.setHint("https://your-website.example");
        serverInput.setTextColor(Color.WHITE);
        serverInput.setHintTextColor(Color.GRAY);
        layout.addView(serverInput);

        addLabel(layout, "SIX-DIGIT PAIRING CODE");

        codeInput = new EditText(this);
        codeInput.setSingleLine(true);
        codeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        codeInput.setTextColor(Color.WHITE);
        codeInput.setHint("000000");
        codeInput.setHintTextColor(Color.GRAY);
        layout.addView(codeInput);

        pairButton = new Button(this);
        pairButton.setText("PAIR DEVICE");
        layout.addView(pairButton);

        status = new TextView(this);
        status.setTextColor(Color.rgb(0, 255, 170));
        status.setPadding(5, 15, 5, 15);
        layout.addView(status);

        Button refresh = new Button(this);
        refresh.setText("REFRESH & SYNC MY INFO");
        layout.addView(refresh);

        results = new TextView(this);
        results.setTextColor(Color.WHITE);
        results.setTextSize(15);
        results.setPadding(5, 20, 5, 10);
        layout.addView(results);

        scroll.addView(layout);
        setContentView(scroll);

        pairButton.setOnClickListener(v -> pairDevice());
        refresh.setOnClickListener(v -> { scanNetwork(); reportDeviceInfo(); });

        scanNetwork();
    }

    private void addLabel(LinearLayout layout, String value) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextColor(Color.rgb(0, 255, 170));
        label.setPadding(0, 18, 0, 4);
        layout.addView(label);
    }

    private void pairDevice() {
        String base = serverInput.getText().toString().trim();
        String code = codeInput.getText().toString().trim();

        if (!base.startsWith("https://")) {
            status.setText("HTTPS website URL required.");
            return;
        }

        if (!code.matches("\\d{6}")) {
            status.setText("Enter a six-digit pairing code.");
            return;
        }

        pairButton.setEnabled(false);
        status.setText("Connecting securely...");

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(
                    base.replaceAll("/+$", "") + "/api/companion/pair"
                );

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setDoOutput(true);
                connection.setRequestProperty(
                    "Content-Type", "application/json"
                );

                JSONObject request = new JSONObject();
                request.put("code", code);
                request.put("device_name", Build.MODEL);

                try (OutputStream out = connection.getOutputStream()) {
                    out.write(request.toString().getBytes(
                        StandardCharsets.UTF_8
                    ));
                }

                int responseCode = connection.getResponseCode();

                InputStream stream = responseCode >= 200 &&
                    responseCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();

                StringBuilder response = new StringBuilder();

                if (stream != null) {
                    try (BufferedReader reader =
                        new BufferedReader(new InputStreamReader(stream))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                    }
                }

                JSONObject data = new JSONObject(response.toString());

                if (responseCode < 200 || responseCode >= 300) {
                    throw new Exception(
                        data.optString("error", "Pairing failed.")
                    );
                }

                String token = data.getString("token");
                saveEncryptedToken(token);
                saveServerBase(base);

                runOnUiThread(() -> {
                    status.setText("PAIRED SUCCESSFULLY — SENDING FIRST REPORT...");
                    pairButton.setEnabled(true);
                    codeInput.setText("");
                    scanNetwork();
                    reportDeviceInfo();
                });

            } catch (Exception e) {
                String error = e.getMessage();

                runOnUiThread(() -> {
                    status.setText("PAIRING ERROR: " + error);
                    pairButton.setEnabled(true);
                });

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void saveEncryptedToken(String token) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        SecretKey secretKey;

        if (keyStore.containsAlias(ALIAS)) {
            secretKey = (SecretKey) keyStore.getKey(ALIAS, null);
        } else {
            KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            );

            generator.init(new KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT |
                KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .setRandomizedEncryptionRequired(true)
                .build());

            secretKey = generator.generateKey();
        }

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        byte[] encrypted = cipher.doFinal(
            token.getBytes(StandardCharsets.UTF_8)
        );

        SharedPreferences prefs =
            getSharedPreferences(PREFS, MODE_PRIVATE);

        prefs.edit()
            .putString("token", Base64.encodeToString(
                encrypted, Base64.NO_WRAP
            ))
            .putString("iv", Base64.encodeToString(
                cipher.getIV(), Base64.NO_WRAP
            ))
            .apply();
    }

    private void scanNetwork() {
        StringBuilder output = new StringBuilder();

        ConnectivityManager cm =
            (ConnectivityManager) getSystemService(
                Context.CONNECTIVITY_SERVICE
            );

        Network network = cm.getActiveNetwork();

        output.append("PHONE NETWORK INFORMATION\n\n");
        output.append("Device: ").append(Build.MODEL).append("\n");
        output.append("Android: ").append(Build.VERSION.RELEASE).append("\n\n");

        if (network == null) {
            output.append("No active network detected.");
            results.setText(output.toString());
            return;
        }

        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        LinkProperties links = cm.getLinkProperties(network);

        if (caps != null) {
            output.append("Wi-Fi: ")
                .append(caps.hasTransport(
                    NetworkCapabilities.TRANSPORT_WIFI
                ) ? "Connected" : "Not detected").append("\n");

            output.append("Mobile data: ")
                .append(caps.hasTransport(
                    NetworkCapabilities.TRANSPORT_CELLULAR
                ) ? "Connected" : "Not detected").append("\n");

            output.append("Internet available: ")
                .append(caps.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET
                ) ? "Yes" : "Not confirmed").append("\n\n");
        }

        if (links != null) {
            output.append("IP ADDRESSES\n");

            links.getLinkAddresses().forEach(address -> {
                output.append(address.getAddress().getHostAddress())
                    .append("\n");
            });

            output.append("\nDNS SERVERS\n");

            links.getDnsServers().forEach(dns ->
                output.append(dns.getHostAddress()).append("\n")
            );

            output.append("\nGATEWAY\n");

            for (RouteInfo route : links.getRoutes()) {
                if (route.getDestination().getPrefixLength() == 0 &&
                    route.getGateway() != null) {
                    output.append(route.getGateway().getHostAddress())
                        .append("\n");
                }
            }
        }

        output.append("\nHotspot client discovery: Not yet verified.");
        results.setText(output.toString());
    }


    private void saveServerBase(String base) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putString("server_base", base.replaceAll("/+$", ""))
            .apply();
    }

    private String loadDecryptedToken() throws Exception {
        SharedPreferences prefs =
            getSharedPreferences(PREFS, MODE_PRIVATE);

        String encryptedText = prefs.getString("token", null);
        String ivText = prefs.getString("iv", null);

        if (encryptedText == null || ivText == null) {
            throw new Exception("This phone is not paired.");
        }

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        SecretKey secretKey =
            (SecretKey) keyStore.getKey(ALIAS, null);

        if (secretKey == null) {
            throw new Exception("Secure pairing key is unavailable. Pair again.");
        }

        byte[] encrypted = Base64.decode(encryptedText, Base64.NO_WRAP);
        byte[] iv = Base64.decode(ivText, Base64.NO_WRAP);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey,
            new GCMParameterSpec(128, iv)
        );

        return new String(
            cipher.doFinal(encrypted),
            StandardCharsets.UTF_8
        );
    }

    private JSONObject buildDeviceReport() throws Exception {
        JSONObject report = new JSONObject();
        JSONArray ips = new JSONArray();
        JSONArray dns = new JSONArray();

        ConnectivityManager cm =
            (ConnectivityManager) getSystemService(
                Context.CONNECTIVITY_SERVICE
            );

        Network network = cm.getActiveNetwork();
        String networkType = "Offline";
        String gateway = "";

        if (network != null) {
            NetworkCapabilities caps =
                cm.getNetworkCapabilities(network);

            if (caps != null) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    networkType = "Wi-Fi";
                } else if (caps.hasTransport(
                    NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    networkType = "Mobile data";
                } else if (caps.hasTransport(
                    NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    networkType = "Ethernet";
                } else if (caps.hasTransport(
                    NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                    networkType = "Bluetooth";
                } else {
                    networkType = "Other";
                }
            }

            LinkProperties links = cm.getLinkProperties(network);

            if (links != null) {
                for (android.net.LinkAddress address :
                    links.getLinkAddresses()) {
                    ips.put(address.getAddress().getHostAddress());
                }

                for (InetAddress address : links.getDnsServers()) {
                    dns.put(address.getHostAddress());
                }

                for (RouteInfo route : links.getRoutes()) {
                    if (route.getDestination().getPrefixLength() == 0 &&
                        route.getGateway() != null) {
                        gateway = route.getGateway().getHostAddress();
                        break;
                    }
                }
            }
        }

        int uid = android.os.Process.myUid();
        long tx = TrafficStats.getUidTxBytes(uid);
        long rx = TrafficStats.getUidRxBytes(uid);

        report.put("network_type", networkType);
        report.put("ip_addresses", ips);
        report.put("dns_servers", dns);
        report.put("gateway", gateway);
        report.put("android_version", Build.VERSION.RELEASE);
        report.put("tx_bytes", tx);
        report.put("rx_bytes", rx);

        return report;
    }

    private void reportDeviceInfo() {
        SharedPreferences prefs =
            getSharedPreferences(PREFS, MODE_PRIVATE);

        String base = prefs.getString("server_base", "");

        if (!base.startsWith("https://")) {
            status.setText("Pair this phone before sending reports.");
            return;
        }

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String token = loadDecryptedToken();
                JSONObject report = buildDeviceReport();

                URL url = new URL(
                    base.replaceAll("/+$", "") +
                    "/api/companion/report"
                );

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setDoOutput(true);
                connection.setRequestProperty(
                    "Content-Type", "application/json"
                );
                connection.setRequestProperty(
                    "Authorization", "Bearer " + token
                );

                try (OutputStream out = connection.getOutputStream()) {
                    out.write(report.toString().getBytes(
                        StandardCharsets.UTF_8
                    ));
                }

                int responseCode = connection.getResponseCode();

                InputStream stream = responseCode >= 200 &&
                    responseCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();

                StringBuilder response = new StringBuilder();

                if (stream != null) {
                    try (BufferedReader reader =
                        new BufferedReader(new InputStreamReader(stream))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                    }
                }

                if (responseCode < 200 || responseCode >= 300) {
                    JSONObject error = new JSONObject(response.toString());
                    throw new Exception(
                        error.optString("error", "Report was rejected.")
                    );
                }

                runOnUiThread(() ->
                    status.setText("REPORT SENT — NETWORK INFO UPDATED")
                );

            } catch (Exception e) {
                String message = e.getMessage();

                runOnUiThread(() ->
                    status.setText("REPORT ERROR: " + message)
                );

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }
}
