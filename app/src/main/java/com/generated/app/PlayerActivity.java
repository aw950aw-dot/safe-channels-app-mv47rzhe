package com.generated.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class PlayerActivity extends Activity {

    private WebView webViewPlayer;
    private TextView tvPlayerTitle;
    private TextView tvPlayerChannel;
    private TextView tvPlayerDate;
    private TextView tvPlayerDescription;
    private ImageButton btnPlayerBack;

    private Button btnDownloadVideo;
    private Button btnDownloadAudio;
    private Button btnWatchQuality;
    private Button btnSwitchServer;
    private ProgressBar pbDownloadLoading;

    private String currentVideoId = "";
    private String currentVideoTitle = "";
    private String selectedWatchQuality = "720p";
    private boolean useInvidiousFallback = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        initViews();

        Intent intent = getIntent();
        if (intent != null) {
            currentVideoId = intent.getStringExtra("VIDEO_ID");
            currentVideoTitle = intent.getStringExtra("VIDEO_TITLE");
            String channel = intent.getStringExtra("CHANNEL_TITLE");
            String date = intent.getStringExtra("PUBLISHED_DATE");
            String description = intent.getStringExtra("DESCRIPTION");

            tvPlayerTitle.setText(currentVideoTitle != null ? currentVideoTitle : "");
            tvPlayerChannel.setText(channel != null ? channel : "");
            tvPlayerDate.setText(date != null ? "פורסם ב: " + date : "");
            tvPlayerDescription.setText(description != null && !description.isEmpty() ? description : "אין תיאור זמין עבור סרטון זה.");

            if (currentVideoId != null && !currentVideoId.isEmpty()) {
                loadPlayer(currentVideoId);
            }
        }

        setupListeners();
    }

    private void initViews() {
        webViewPlayer = findViewById(R.id.webViewPlayer);
        tvPlayerTitle = findViewById(R.id.tvPlayerTitle);
        tvPlayerChannel = findViewById(R.id.tvPlayerChannel);
        tvPlayerDate = findViewById(R.id.tvPlayerDate);
        tvPlayerDescription = findViewById(R.id.tvPlayerDescription);
        btnPlayerBack = findViewById(R.id.btnPlayerBack);

        btnDownloadVideo = findViewById(R.id.btnDownloadVideo);
        btnDownloadAudio = findViewById(R.id.btnDownloadAudio);
        btnWatchQuality = findViewById(R.id.btnWatchQuality);
        btnSwitchServer = findViewById(R.id.btnSwitchServer);
        pbDownloadLoading = findViewById(R.id.pbDownloadLoading);
    }

    private void setupListeners() {
        btnPlayerBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        btnWatchQuality.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showWatchQualityDialog();
            }
        });

        btnSwitchServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                useInvidiousFallback = !useInvidiousFallback;
                Toast.makeText(PlayerActivity.this, useInvidiousFallback ? "עובר לשרת נגן חלופי" : "עובר לנגן יוטיוב רגיל", Toast.LENGTH_SHORT).show();
                if (currentVideoId != null && !currentVideoId.isEmpty()) {
                    loadPlayer(currentVideoId);
                }
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

    private void loadPlayer(String videoId) {
        WebSettings webSettings = webViewPlayer.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setAllowFileAccess(true);
        webSettings.setUserAgentString("Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

        webViewPlayer.setWebChromeClient(new WebChromeClient());
        webViewPlayer.setWebViewClient(new WebViewClient());

        String embedUrl;
        if (useInvidiousFallback) {
            embedUrl = "https://inv.nadeko.net/embed/" + videoId + "?autoplay=1&quality=" + selectedWatchQuality;
        } else {
            embedUrl = "https://www.youtube-nocookie.com/embed/" + videoId + "?autoplay=1&rel=0&modestbranding=1&enablejsapi=1&origin=https://www.youtube.com";
        }

        String embedHtml = "<!DOCTYPE html><html><head>" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">" +
                "<style>body, html { margin:0; padding:0; width:100%; height:100%; background-color:#000000; overflow:hidden; }" +
                "iframe { width:100%; height:100%; border:none; }</style></head><body>" +
                "<iframe id=\"player\" src=\"" + embedUrl + "\" " +
                "allow=\"accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture\" allowfullscreen></iframe>" +
                "</body></html>";

        webViewPlayer.loadDataWithBaseURL("https://www.youtube.com", embedHtml, "text/html", "UTF-8", null);
    }

    private void showWatchQualityDialog() {
        final String[] qualities = new String[]{"1080p", "720p", "480p", "360p", "240p", "אוטימטי (Auto)"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.select_watch_quality)
                .setItems(qualities, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        selectedWatchQuality = qualities[which].contains("1080") ? "1080p" :
                                qualities[which].contains("720") ? "720p" :
                                qualities[which].contains("480") ? "480p" :
                                qualities[which].contains("360") ? "360p" : "auto";
                        Toast.makeText(PlayerActivity.this, "איכות הצפייה הוגדרה ל: " + qualities[which], Toast.LENGTH_SHORT).show();
                        if (currentVideoId != null && !currentVideoId.isEmpty()) {
                            loadPlayer(currentVideoId);
                        }
                    }
                })
                .show();
    }

    private void showDownloadQualityDialog(final boolean isAudioOnly) {
        if (isAudioOnly) {
            startDownloadProcess("AUDIO", true);
            return;
        }

        final String[] qualities = new String[]{"1080p (איכות גבוהה ביותר)", "720p (איכות HD)", "480p (איכות רגילה)", "360p (חיסכון בנתונים)"};
        final String[] qualityValues = new String[]{"1080p", "720p", "480p", "360p"};

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
        Toast.makeText(this, "מכין את קישור ההורדה...", Toast.LENGTH_SHORT).show();

        DownloadHelper.getStreamUrl(currentVideoId, qualityLabel, isAudioOnly, new DownloadHelper.StreamCallback() {
            @Override
            public void onSuccess(String streamUrl, String extension, String mimeType) {
                pbDownloadLoading.setVisibility(View.GONE);
                DownloadHelper.startDownload(PlayerActivity.this, currentVideoTitle, streamUrl, extension, mimeType, isAudioOnly);
            }

            @Override
            public void onError(String error) {
                pbDownloadLoading.setVisibility(View.GONE);
                Toast.makeText(PlayerActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
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
            webViewPlayer.destroy();
        }
        super.onDestroy();
    }
}
