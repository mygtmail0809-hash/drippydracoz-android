package com.drippydracoz.scanner;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.graphics.Color;
import android.view.Gravity;

import java.net.Inet4Address;
import java.net.InetAddress;

public class MainActivity extends Activity {

    private TextView results;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(28, 40, 28, 28);
        layout.setBackgroundColor(Color.rgb(10, 10, 18));

        TextView title = new TextView(this);
        title.setText("DRIPPYDRACOZ");
        title.setTextColor(Color.rgb(0, 255, 170));
        title.setTextSize(27);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("ANDROID NETWORK SCANNER");
        subtitle.setTextColor(Color.WHITE);
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        layout.addView(subtitle);

        Button refresh = new Button(this);
        refresh.setText("REFRESH NETWORK INFO");
        layout.addView(refresh);

        results = new TextView(this);
        results.setTextColor(Color.WHITE);
        results.setTextSize(15);
        results.setPadding(5, 25, 5, 5);
        layout.addView(results);

        setContentView(layout);

        refresh.setOnClickListener(v -> scanNetwork());
        scanNetwork();
    }

    private void scanNetwork() {
        StringBuilder output = new StringBuilder();

        ConnectivityManager cm =
            (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        Network network = cm.getActiveNetwork();

        if (network == null) {
            results.setText("No active network connection detected.");
            return;
        }

        NetworkCapabilities caps =
            cm.getNetworkCapabilities(network);

        LinkProperties links =
            cm.getLinkProperties(network);

        output.append("DEVICE NETWORK INFORMATION\n\n");

        if (caps != null) {
            output.append("Wi-Fi: ")
                .append(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    ? "Connected" : "Not detected")
                .append("\n");

            output.append("Mobile data: ")
                .append(caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    ? "Connected" : "Not detected")
                .append("\n");

            output.append("Internet available: ")
                .append(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    ? "Yes" : "Not confirmed")
                .append("\n\n");
        }

        if (links != null) {
            output.append("IP ADDRESSES\n");

            for (android.net.LinkAddress address : links.getLinkAddresses()) {
                InetAddress ip = address.getAddress();

                if (ip instanceof Inet4Address) {
                    output.append("IPv4: ")
                        .append(ip.getHostAddress())
                        .append("\n");
                }
            }

            output.append("\nDNS SERVERS\n");

            for (InetAddress dns : links.getDnsServers()) {
                output.append(dns.getHostAddress()).append("\n");
            }

            output.append("\nGATEWAY\n");

            for (RouteInfo route : links.getRoutes()) {
                if (route.isDefault() && route.getGateway() != null) {
                    output.append(route.getGateway().getHostAddress())
                        .append("\n");
                }
            }
        }

        output.append("\nOnly information exposed by Android is displayed.");
        results.setText(output.toString());
    }
}
