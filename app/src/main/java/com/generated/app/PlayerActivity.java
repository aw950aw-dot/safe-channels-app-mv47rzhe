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
import android.webkit.WebChromeClient;
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

    private FrameLayout flFullscreenContainer;
    private WebView webViewPlayer;
    private TextView tvPlayerTitle;
    private TextView tvPlayerChannel;
    private TextView tvPlayerDate;
    private TextView tvPlayerDescription;
    private ImageButton btnPlayerBack;

    private Button btnDownloadVideo;
    private Button btnDownloadAudio;
    private ImageButton btnExternalOpen;
    private ProgressBar pbDownloadLoading;
    private ProgressBar pbPlayerLoading;

    private String currentVideoId = "";
    private String currentVideoTitle = "";

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
                loadYouTubePlayer();
            }
        }

        setupListeners();
    }

    private void initViews() {
        flFullscreenContainer = findViewById(R.id.flFullscreenContainer);
        webViewPlayer = findViewById(R.id.webViewPlayer);
        tvPlayerTitle = findViewById(R.id.tvPlayerTitle);
        tvPlayerChannel = findViewById(R.id.tvPlayerChannel);
        tvPlayerDate = findViewById(R.id.tvPlayerDate);
        tvPlayerDescription = findViewById(R.id.tvPlayerDescription);
        btnPlayerBack = findViewById(R.id.btnPlayerBack);

        btnDownloadVideo = findViewById(R.id.btnDownloadVideo);
        btnDownloadAudio = findViewById(R.id.btnDownloadAudio);
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

        btnExternalOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openInExternalApp();
            }
        });

        btnDownloadVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDownloadVideoQualityDialog();
            }
        });

        btnDownloadAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDownloadAudioQualityDialog();
            }
        });
    }

    private void loadYouTubePlayer() {
        pbPlayerLoading.setVisibility(View.VISIBLE);

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
    }

    private void openInExternalApp() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + currentVideoId));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "לא נמצאה אפליקציה מתאימה לפתיחה", Toast.LENGTH_SHORT).show();
        }
    }

    private void showDownloadVideoQualityDialog() {
        final String[] qualities = new String[]{
                getString(R.string.video_quality_1080),
                getString(R.string.video_quality_720),
                getString(R.string.video_quality_360)
        };
        final String[] qualityValues = new String[]{"1080p", "720p", "360p"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.select_download_quality)
                .setItems(qualities, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startDirectInAppDownload(qualityValues[which], false);
                    }
                })
                .show();
    }

    private void showDownloadAudioQualityDialog() {
        final String[] audioQualities = new String[]{
                getString(R.string.audio_quality_high),
                getString(R.string.audio_quality_med),
                getString(R.string.audio_quality_low)
        };
        final String[] audioQualityValues = new String[]{"160k", "128k", "64k"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.select_audio_quality)
                .setItems(audioQualities, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startDirectInAppDownload(audioQualityValues[which], true);
                    }
                })
                .show();
    }

    private void startDirectInAppDownload(String qualityKey, final boolean isAudioOnly) {
        pbDownloadLoading.setVisibility(View.VISIBLE);
        Toast.makeText(this, isAudioOnly ? "מכין קובץ שמע להורדה ישירה..." : "מכין סרטון להורדה ישירה...", Toast.LENGTH_SHORT).show();

        DownloadHelper.getStreamUrl(currentVideoId, qualityKey, isAudioOnly, new DownloadHelper.StreamCallback() {
            @Override
            public void onSuccess(String streamUrl, String extension, String mimeType) {
                pbDownloadLoading.setVisibility(View.GONE);
                DownloadHelper.startDownload(PlayerActivity.this, currentVideoTitle, streamUrl, extension, mimeType, isAudioOnly);
            }

            @Override
            public void onError(String error) {
                pbDownloadLoading.setVisibility(View.GONE);
                Toast.makeText(PlayerActivity.this, R.string.download_error, Toast.LENGTH_LONG).show();
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
