package com.generated.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class PlayerActivity extends Activity {

    private FrameLayout flPlayerContainer;
    private FrameLayout flFullscreenContainer;
    private WebView webViewPlayer;
    private TextView tvPlayerTitle;
    private TextView tvPlayerChannel;
    private TextView tvPlayerDate;
    private TextView tvPlayerDescription;
    private TextView tvServerBadge;
    private ImageButton btnPlayerBack;

    private Button btnDownloadVideo;
    private Button btnDownloadAudio;
    private Button btnDirectStream;
    private Button btnSwitchServer;
    private ImageButton btnExternalOpen;
    private ProgressBar pbDownloadLoading;
    private ProgressBar pbPlayerLoading;

    private String currentVideoId = "";
    private String currentVideoTitle = "";
    private int playerMode = 0; // 0 = YouTube NoCookie Responsive, 1 = Piped / Invidious Embed, 2 = HTML5 Direct Stream

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        initViews();
        setupWebView();

        Intent intent = getIntent();
        if (intent != null) {
            currentVideoId = intent.getStringExtra("VIDEO_ID");
            currentVideoTitle = intent.getStringExtra("VIDEO_TITLE");
            String channel = intent.getStringExtra("CHANNEL_TITLE");
            String date = intent.getStringExtra("PUBLISHED_DATE");
            String description = intent.getStringExtra("DESCRIPTION");

            tvPlayerTitle.setText(currentVideoTitle != null ? currentVideoTitle : "");
            tvPlayerChannel.setText(channel != null ? channel : "");
            tvPlayerDate.setText(date != null && !date.isEmpty() ? "פורסם ב: " + date : "");
            tvPlayerDescription.setText(description != null && !description.isEmpty() ? description : "אין תיאור נוסף עבור סרטון זה.");

            if (currentVideoId != null && !currentVideoId.isEmpty()) {
                loadPlayerCurrentMode();
            }
        }

        setupListeners();
    }

    private void initViews() {
        flPlayerContainer = findViewById(R.id.flPlayerContainer);
        flFullscreenContainer = findViewById(R.id.flFullscreenContainer);
        webViewPlayer = findViewById(R.id.webViewPlayer);
        tvPlayerTitle = findViewById(R.id.tvPlayerTitle);
        tvPlayerChannel = findViewById(R.id.tvPlayerChannel);
        tvPlayerDate = findViewById(R.id.tvPlayerDate);
        tvPlayerDescription = findViewById(R.id.tvPlayerDescription);
        tvServerBadge = findViewById(R.id.tvServerBadge);
        btnPlayerBack = findViewById(R.id.btnPlayerBack);

        btnDownloadVideo = findViewById(R.id.btnDownloadVideo);
        btnDownloadAudio = findViewById(R.id.btnDownloadAudio);
        btnDirectStream = findViewById(R.id.btnDirectStream);
        btnSwitchServer = findViewById(R.id.btnSwitchServer);
        btnExternalOpen = findViewById(R.id.btnExternalOpen);
        pbDownloadLoading = findViewById(R.id.pbDownloadLoading);
        pbPlayerLoading = findViewById(R.id.pbPlayerLoading);
    }

    private void setupWebView() {
        WebSettings ws = webViewPlayer.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setJavaScriptCanOpenWindowsAutomatically(true);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        // Standard modern browser user agent without restrictive WebView signature
        ws.setUserAgentString("Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");

        webViewPlayer.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress >= 80) {
                    pbPlayerLoading.setVisibility(View.GONE);
                }
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                flFullscreenContainer.addView(view);
                flFullscreenContainer.setVisibility(View.VISIBLE);
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                flFullscreenContainer.removeView(customView);
                flFullscreenContainer.setVisibility(View.GONE);
                customView = null;
                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                    customViewCallback = null;
                }
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            }
        });

        webViewPlayer.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                pbPlayerLoading.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pbPlayerLoading.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                // If official fails to load, prompt fallback
            }
        });
    }

    private void setupListeners() {
        btnPlayerBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (customView != null) {
                    webViewPlayer.getWebChromeClient().onHideCustomView();
                } else {
                    finish();
                }
            }
        });

        btnSwitchServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playerMode = (playerMode + 1) % 2;
                String modeName = playerMode == 1 ? "שרת פרטי ללא פרסומות" : "נגן רשמי מהיר";
                Toast.makeText(PlayerActivity.this, "הוחלף ל: " + modeName, Toast.LENGTH_SHORT).show();
                loadPlayerCurrentMode();
            }
        });

        btnDirectStream.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadDirectHtml5Stream();
            }
        });

        btnExternalOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openInExternalApp();
            }
        });

        btnDownloadVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDownloadQualityDialog(false);
            }
        });

        btnDownloadAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDownloadQualityDialog(true);
            }
        });
    }

    private void loadPlayerCurrentMode() {
        pbPlayerLoading.setVisibility(View.VISIBLE);

        if (playerMode == 0) {
            tvServerBadge.setText(R.string.server_official);
            String embedHtml = "<!DOCTYPE html>" +
                    "<html lang=\"he\" dir=\"rtl\"><head>" +
                    "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">" +
                    "<style>" +
                    "* { margin: 0; padding: 0; box-sizing: border-box; background: #000; }" +
                    "html, body { width: 100%; height: 100%; overflow: hidden; background-color: #000000; display: flex; align-items: center; justify-content: center; }" +
                    "iframe { width: 100%; height: 100%; border: none; }" +
                    "</style></head><body>" +
                    "<iframe src=\"https://www.youtube-nocookie.com/embed/" + currentVideoId + "?autoplay=1&playsinline=1&rel=0&modestbranding=1&enablejsapi=1\" " +
                    "allow=\"accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share; fullscreen\" " +
                    "allowfullscreen></iframe>" +
                    "</body></html>";

            webViewPlayer.loadDataWithBaseURL("https://www.youtube-nocookie.com", embedHtml, "text/html", "UTF-8", null);

        } else if (playerMode == 1) {
            tvServerBadge.setText(R.string.server_invidious);
            String pipedUrl = "https://piped.video/embed/" + currentVideoId + "?autoplay=1";
            String embedHtml = "<!DOCTYPE html>" +
                    "<html><head>" +
                    "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">" +
                    "<style>" +
                    "* { margin: 0; padding: 0; box-sizing: border-box; background: #000; }" +
                    "html, body { width: 100%; height: 100%; overflow: hidden; background-color: #000000; }" +
                    "iframe { width: 100%; height: 100%; border: none; }" +
                    "</style></head><body>" +
                    "<iframe src=\"" + pipedUrl + "\" allowfullscreen allow=\"autoplay; fullscreen\"></iframe>" +
                    "</body></html>";

            webViewPlayer.loadDataWithBaseURL("https://piped.video", embedHtml, "text/html", "UTF-8", null);
        }
    }

    private void loadDirectHtml5Stream() {
        pbPlayerLoading.setVisibility(View.VISIBLE);
        Toast.makeText(this, "טוען הזרמה ישירה ללא פרסומות...", Toast.LENGTH_SHORT).show();

        DownloadHelper.getStreamUrl(currentVideoId, "720p", false, new DownloadHelper.StreamCallback() {
            @Override
            public void onSuccess(final String streamUrl, String extension, String mimeType) {
                pbPlayerLoading.setVisibility(View.GONE);
                playerMode = 2;
                tvServerBadge.setText(R.string.server_direct);

                String html = "<!DOCTYPE html><html><head>" +
                        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">" +
                        "<style>" +
                        "* { margin:0; padding:0; background:#000; }" +
                        "html, body { width:100%; height:100%; overflow:hidden; display:flex; align-items:center; justify-content:center; }" +
                        "video { width:100%; height:100%; object-fit:contain; }" +
                        "</style></head><body>" +
                        "<video controls autoplay playsinline src=\"" + streamUrl + "\"></video>" +
                        "</body></html>";

                webViewPlayer.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
                Toast.makeText(PlayerActivity.this, "הסרטון מוזרם בהצלחה!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                pbPlayerLoading.setVisibility(View.GONE);
                Toast.makeText(PlayerActivity.this, "הנגן הרשמי זמין לצפייה.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openInExternalApp() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + currentVideoId));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "לא נמצאה אפליקציה מתאימה לפתיחה", Toast.LENGTH_SHORT).show();
        }
    }

    private void showDownloadQualityDialog(final boolean isAudioOnly) {
        if (isAudioOnly) {
            startDownloadProcess("AUDIO", true);
            return;
        }

        final String[] qualities = new String[]{"איכות גבוהה (720p HD)", "איכות רגילה (480p / 360p)", "איכות מקסימלית (1080p)"};
        final String[] qualityValues = new String[]{"720p", "360p", "1080p"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.select_download_quality)
                .setItems(qualities, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startDownloadProcess(qualityValues[which], false);
                    }
                })
                .show();
    }

    private void startDownloadProcess(String qualityLabel, final boolean isAudioOnly) {
        pbDownloadLoading.setVisibility(View.VISIBLE);
        Toast.makeText(this, "מכין קישור מהיר להורדה...", Toast.LENGTH_SHORT).show();

        DownloadHelper.getStreamUrl(currentVideoId, qualityLabel, isAudioOnly, new DownloadHelper.StreamCallback() {
            @Override
            public void onSuccess(String streamUrl, String extension, String mimeType) {
                pbDownloadLoading.setVisibility(View.GONE);
                DownloadHelper.startDownload(PlayerActivity.this, currentVideoTitle, streamUrl, extension, mimeType, isAudioOnly);
            }

            @Override
            public void onError(String error) {
                pbDownloadLoading.setVisibility(View.GONE);
                DownloadHelper.downloadViaWebPortal(PlayerActivity.this, currentVideoId, isAudioOnly);
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            webViewPlayer.getWebChromeClient().onHideCustomView();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webViewPlayer != null) {
            webViewPlayer.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webViewPlayer != null) {
            webViewPlayer.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        if (webViewPlayer != null) {
            webViewPlayer.stopLoading();
            webViewPlayer.loadUrl("about:blank");
            webViewPlayer.destroy();
        }
        super.onDestroy();
    }
}
