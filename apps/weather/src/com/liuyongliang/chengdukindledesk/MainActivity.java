package com.liuyongliang.chengdukindledesk;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebChromeClient;
import android.webkit.WebViewClient;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public class MainActivity extends Activity {
    private WebView webView;
    private final Handler handler = new Handler();
    private final Runnable refreshWeather = new Runnable() {
        @Override public void run() {
            fetchWeather();
            handler.postDelayed(this, 30 * 60 * 1000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        webView.setBackgroundColor(0xffffffff);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        // Weather is fetched natively because this older WebView lacks a current root CA.
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                Log.i("KindleDeskJS", message.message() + " @" + message.lineNumber());
                return true;
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (url.startsWith("file:///android_asset/")) {
                    handler.removeCallbacks(refreshWeather);
                    refreshWeather.run();
                }
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !url.startsWith("https://") && !url.startsWith("http://")
                    && !url.startsWith("file:///android_asset/");
            }
        });
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void fetchWeather() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject location;
                    boolean cachedLocation = false;
                    try {
                        location = fetchIpLocation();
                        double latitude = location.getDouble("lat"), longitude = location.getDouble("lon");
                        if (Double.isNaN(latitude) || Double.isInfinite(latitude) || latitude < -90 || latitude > 90 || Double.isNaN(longitude) || Double.isInfinite(longitude) || longitude < -180 || longitude > 180) throw new Exception("Invalid IP coordinates");
                        getSharedPreferences("location", 0).edit().putString("last", location.toString()).apply();
                        Log.i("KindleDeskWeather", "IP city: " + location.optString("city"));
                    } catch (Exception locationError) {
                        String previous = getSharedPreferences("location", 0).getString("last", null);
                        if (previous == null) throw locationError;
                        location = new JSONObject(previous);
                        cachedLocation = true;
                        Log.w("KindleDeskWeather", "Using last IP location: " + locationError);
                    }
                    String url = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code&hourly=temperature_2m&past_days=1&forecast_days=2&timezone=auto",
                        location.getDouble("lat"), location.getDouble("lon"));
                    JSONObject weather = new JSONObject(fetchText(url));
                    String city = location.optString("city");
                    if (city.length() == 0) city = location.optString("regionName", "当前位置");
                    weather.put("locationName", city);
                    weather.put("locationStale", cachedLocation);
                    final String script = "window.applyNativeWeather(" + JSONObject.quote(weather.toString()) + ")";
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (webView != null) webView.evaluateJavascript(script, null);
                        }
                    });
                } catch (Exception error) {
                    Log.e("KindleDeskWeather", "Native request failed: " + error);
                    notifyWeatherFailure();
                }
            }
        }).start();
    }

    private JSONObject fetchIpLocation() throws Exception {
        try {
            JSONObject source = new JSONObject(fetchText("https://ipwho.is/?fields=success,country_code,region,city,latitude,longitude,timezone&lang=zh-CN"));
            if (!source.optBoolean("success")) throw new Exception("ipwho.is did not return a location");
            JSONObject location = new JSONObject();
            location.put("lat", source.getDouble("latitude"));
            location.put("lon", source.getDouble("longitude"));
            location.put("city", source.optString("city"));
            location.put("regionName", source.optString("region"));
            return location;
        } catch (Exception primaryError) {
            Log.w("KindleDeskWeather", "Primary IP location failed: " + primaryError);
            JSONObject source = new JSONObject(fetchText("http://ip-api.com/json/?fields=status,message,regionName,city,lat,lon&lang=zh-CN"));
            if (!"success".equals(source.optString("status"))) throw new Exception("Backup IP location failed: " + source.optString("message"));
            return source;
        }
    }

    private String fetchText(String address) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            if (connection instanceof HttpsURLConnection && "api.open-meteo.com".equals(new URL(address).getHost())) {
                InputStream certificateInput = getAssets().open("isrgrootx1.pem");
                Certificate root = CertificateFactory.getInstance("X.509").generateCertificate(certificateInput);
                certificateInput.close();
                KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
                store.load(null, null);
                store.setCertificateEntry("isrg-root-x1", root);
                TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trust.init(store);
                SSLContext tls = SSLContext.getInstance("TLS");
                tls.init(null, trust.getTrustManagers(), null);
                ((HttpsURLConnection) connection).setSSLSocketFactory(tls.getSocketFactory());
            }
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            int code = connection.getResponseCode();
            Log.i("KindleDeskWeather", new URL(address).getHost() + " HTTP " + code);
            if (code != 200) throw new Exception("HTTP " + code);
            InputStream input = connection.getInputStream();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            input.close();
            return bytes.toString("UTF-8");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void notifyWeatherFailure() {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                if (webView != null) webView.evaluateJavascript("window.nativeWeatherFailed()", null);
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(refreshWeather);
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
