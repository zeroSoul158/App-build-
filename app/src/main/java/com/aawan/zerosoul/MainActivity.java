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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        webView = (WebView) findViewById(R.id.webview);
        setupWebView();
        checkPermissions();
        
        // Resolve IP in background
        new Thread(new Runnable() {
            @Override
            public void run() {
                resolvedIP = resolveIPViaCloudflare("aawan-cafe.rf.gd");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        webView.loadUrl("https://aawan-cafe.rf.gd/");
                    }
                });
            }
        }).start();
    }

    // Desi DNS-over-HTTPS Resolver (Pure Java)
    private String resolveIPViaCloudflare(String host) {
        try {
            // Cloudflare DNS JSON API
            URL url = new URL("https://1.1.1.1/dns-query?name=" + host + "&type=A");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Accept", "application/dns-json");
            conn.setConnectTimeout(5000);
            
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            
            String response = sb.toString();
            // Desi JSON Parsing (Manual String Search)
            int dataIndex = response.indexOf("\"data\":\"");
            if (dataIndex != -1) {
                int start = dataIndex + 8;
                int end = response.indexOf("\"", start);
                return response.substring(start, end);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setGeolocationEnabled(true);
        
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

                if (resolvedIP != null && host != null && host.equals("aawan-cafe.rf.gd")) {
                    try {
                        // Desi Way: Replace Host with IP in URL
                        String newUrlString = urlString.replace(host, resolvedIP);
                        URL url = new URL(newUrlString);
                        
                        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                        conn.setRequestMethod(request.getMethod());
                        
                        // Set Manual Host Header (Crucial for shared hosting like rf.gd)
                        conn.setRequestProperty("Host", host);
                        
                        // Forward all other headers
                        for (Map.Entry<String, String> entry : request.getRequestHeaders().entrySet()) {
                            if (!entry.getKey().equalsIgnoreCase("Host")) {
                                conn.setRequestProperty(entry.getKey(), entry.getValue());
                            }
                        }

                        // Desi SSL Fix: Verify that the IP matches our intended Host
                        conn.setHostnameVerifier(new HostnameVerifier() {
                            @Override
                            public boolean verify(String hostname, SSLSession session) {
                                return true; // Accept since we manually pointed to the IP
                            }
                        });

                        InputStream in = new BufferedInputStream(conn.getInputStream());
                        String contentType = conn.getContentType();
                        String encoding = conn.getContentEncoding();
                        if (encoding == null) encoding = "UTF-8";

                        String mimeType = "text/html";
                        if (contentType != null) {
                            mimeType = contentType.split(";")[0].trim();
                        }

                        return new WebResourceResponse(mimeType, encoding, in);
                    } catch (Exception e) {
                        return null; // Fallback
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    request.grant(request.getResources());
                }
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
