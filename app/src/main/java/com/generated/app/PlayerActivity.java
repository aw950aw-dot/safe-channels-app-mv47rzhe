package com.generated.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.TextView;

public class PlayerActivity extends Activity {

    private WebView webViewPlayer;
    private TextView tvPlayerTitle;
    private TextView tvPlayerChannel;
    private TextView tvPlayerDate;
    private TextView tvPlayerDescription;
    private ImageButton btnPlayerBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        initViews();

        Intent intent = getIntent();
        if (intent != null) {
            String videoId = intent.getStringExtra("VIDEO_ID");
            String title = intent.getStringExtra("VIDEO_TITLE");
            String channel = intent.getStringExtra("CHANNEL_TITLE");
            String date = intent.getStringExtra("PUBLISHED_DATE");
            String description = intent.getStringExtra("DESCRIPTION");

            tvPlayerTitle.setText(title != null ? title : "");
            tvPlayerChannel.setText(channel != null ? channel : "");
            tvPlayerDate.setText(date != null ? "פורסם ב: " + date : "");
            tvPlayerDescription.setText(description != null && !description.isEmpty() ? description : "אין תיאור זמין עבור סרטון זה.");

            if (videoId != null && !videoId.isEmpty()) {
                setupWebView(videoId);
            }
        }

        btnPlayerBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void initViews() {
        webViewPlayer = findViewById(R.id.webViewPlayer);
        tvPlayerTitle = findViewById(R.id.tvPlayerTitle);
        tvPlayerChannel = findViewById(R.id.tvPlayerChannel);
        tvPlayerDate = findViewById(R.id.tvPlayerDate);
        tvPlayerDescription = findViewById(R.id.tvPlayerDescription);
        btnPlayerBack = findViewById(R.id.btnPlayerBack);
    }

    private void setupWebView(String videoId) {
        WebSettings webSettings = webViewPlayer.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);

        webViewPlayer.setWebChromeClient(new WebChromeClient());
        webViewPlayer.setWebViewClient(new WebViewClient());

        String embedHtml = "<!DOCTYPE html><html><head><style>" +
                "body, html { margin:0; padding:0; width:100%; height:100%; background-color:#000000; overflow:hidden; }" +
                "iframe { width:100%; height:100%; border:none; }" +
                "</style></head><body>" +
                "<iframe src=\"https://www.youtube.com/embed/" + videoId + "?autoplay=1&rel=0&modestbranding=1\" " +
                "allow=\"accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture\" allowfullscreen></iframe>" +
                "</body></html>";

        webViewPlayer.loadDataWithBaseURL("https://www.youtube.com", embedHtml, "text/html", "UTF-8", null);
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
