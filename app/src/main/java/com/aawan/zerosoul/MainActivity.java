package com.aawan.zerosoul;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.PermissionRequest;
import android.webkit.GeolocationPermissions;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> mFilePathCallback;
    private final static int FILECHOOSER_RESULTCODE = 1;
    private static final int REQUEST_PERMISSIONS = 2;
    private String resolvedIP = null;
    private String userAgent = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        webView = (WebView) findViewById(R.id.webview);
        userAgent = webView.getSettings().getUserAgentString();
        
        setupWebView();
        checkPermissions();
        
        startDnsBypass();
    }

    private void startDnsBypass() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Try Cloudflare DoH (JSON)
                resolvedIP = resolveIPViaCloudflare("aawan-cafe.rf.gd");
                
                // Fallback to direct resolution if DoH fails
                if (resolvedIP == null) {
                    try {
                        resolvedIP = java.net.InetAddress.getByName("aawan-cafe.rf.gd").getHostAddress();
                    } catch (Exception e) {}
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (resolvedIP != null) {
                            Toast.makeText(MainActivity.this, "DNS Resolved: " + resolvedIP, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "DNS Failed, using normal load", Toast.LENGTH_SHORT).show();
                        }
                        webView.loadUrl("https://aawan-cafe.rf.gd/");
                    }
                });
            }
        }).start();
    }

    private String resolveIPViaCloudflare(String host) {
        try {
            URL url = new URL("https://1.1.1.1/dns-query?name=" + host + "&type=A");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Accept", "application/dns-json");
            conn.setConnectTimeout(5000);
            
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            String response = sb.toString();
            
            int dataIndex = response.indexOf("\"data\":\"");
            if (dataIndex != -1) {
                int start = dataIndex + 8;
                int end = response.indexOf("\"", start);
                return response.substring(start, end);
            }
        } catch (Exception e) {}
        return null;
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setGeolocationEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }
        CookieManager.getInstance().setAcceptCookie(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String urlString = request.getUrl().toString();
                String host = request.getUrl().getHost();

                // Intercept main requests for our domain
                if (resolvedIP != null && host != null && host.equals("aawan-cafe.rf.gd")) {
                    try {
                        String newUrlString = urlString.replace(host, resolvedIP);
                        URL url = new URL(newUrlString);
                        
                        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                        conn.setRequestMethod(request.getMethod());
                        conn.setRequestProperty("Host", host);
                        conn.setRequestProperty("User-Agent", userAgent);
                        
                        // Sync Cookies
                        String cookies = CookieManager.getInstance().getCookie(urlString);
                        if (cookies != null) {
                            conn.setRequestProperty("Cookie", cookies);
                        }

                        // Forward headers
                        for (Map.Entry<String, String> entry : request.getRequestHeaders().entrySet()) {
                            if (!entry.getKey().equalsIgnoreCase("Host") && !entry.getKey().equalsIgnoreCase("User-Agent")) {
                                conn.setRequestProperty(entry.getKey(), entry.getValue());
                            }
                        }

                        // SSL Bypass
                        conn.setHostnameVerifier(new HostnameVerifier() {
                            @Override
                            public boolean verify(String hostname, SSLSession session) {
                                return true;
                            }
                        });

                        InputStream in = new BufferedInputStream(conn.getInputStream());
                        String contentType = conn.getContentType();
                        String encoding = conn.getContentEncoding();
                        if (encoding == null) encoding = "UTF-8";
                        String mimeType = (contentType != null) ? contentType.split(";")[0].trim() : "text/html";

                        return new WebResourceResponse(mimeType, encoding, in);
                    } catch (Exception e) {
                        return null;
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) request.grant(request.getResources());
            }
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                mFilePathCallback = filePathCallback;
                Intent intent = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, FILECHOOSER_RESULTCODE);
                } catch (Exception e) {
                    mFilePathCallback = null;
                    return false;
                }
                return true;
            }
        });
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION};
            List<String> needed = new ArrayList<>();
            for (String p : permissions) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) needed.add(p);
            if (!needed.isEmpty()) requestPermissions(needed.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILECHOOSER_RESULTCODE && mFilePathCallback != null) {
            Uri[] res = null;
            if (resultCode == RESULT_OK && data != null) if (data.getDataString() != null) res = new Uri[]{Uri.parse(data.getDataString())};
            mFilePathCallback.onReceiveValue(res);
            mFilePathCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}
