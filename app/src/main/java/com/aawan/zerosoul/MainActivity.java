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

import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> mFilePathCallback;
    private final static int FILECHOOSER_RESULTCODE = 1;
    private static final int REQUEST_PERMISSIONS = 2;
    private OkHttpClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        webView = (WebView) findViewById(R.id.webview);
        
        setupOkHttp();
        setupWebView();
        checkPermissions();
        
        webView.loadUrl("https://aawan-cafe.rf.gd/");
    }

    private void setupOkHttp() {
        // Proper OpenDNS Integration using OkHttp
        client = new OkHttpClient.Builder()
            .dns(new Dns() {
                @Override
                public List<InetAddress> lookup(String hostname) throws UnknownHostException {
                    if (hostname.contains("rf.gd")) {
                        try {
                            // Resolve using OpenDNS IP
                            return Arrays.asList(InetAddress.getAllByName("208.67.222.222"));
                        } catch (Exception e) {
                            return Dns.SYSTEM.lookup(hostname);
                        }
                    }
                    return Dns.SYSTEM.lookup(hostname);
                }
            })
            .build();
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setGeolocationEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }
        
        CookieManager.getInstance().setAcceptCookie(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("http")) {
                    try {
                        Request.Builder builder = new Request.Builder().url(url);
                        // Forward headers
                        for (String key : request.getRequestHeaders().keySet()) {
                            builder.addHeader(key, request.getRequestHeaders().get(key));
                        }
                        
                        Response response = client.newCall(builder.build()).execute();
                        
                        String contentType = "";
                        String encoding = "UTF-8";
                        if (response.body().contentType() != null) {
                            contentType = response.body().contentType().type() + "/" + response.body().contentType().subtype();
                        }

                        return new WebResourceResponse(contentType, encoding, response.body().byteStream());
                    } catch (Exception e) {
                        return null;
                    }
                }
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("http")) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    return true;
                } catch (Exception e) {
                    return false;
                }
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
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                mFilePathCallback = filePathCallback;
                Intent intent = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    intent = fileChooserParams.createIntent();
                }
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
            String[] permissions = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION
            };
            List<String> needed = new ArrayList<String>();
            for (String p : permissions) {
                if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) needed.add(p);
            }
            if (!needed.isEmpty()) requestPermissions(needed.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILECHOOSER_RESULTCODE && mFilePathCallback != null) {
            Uri[] res = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getDataString() != null) res = new Uri[]{Uri.parse(data.getDataString())};
            }
            mFilePathCallback.onReceiveValue(res);
            mFilePathCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
