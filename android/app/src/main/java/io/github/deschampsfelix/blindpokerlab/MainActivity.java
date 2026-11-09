package io.github.deschampsfelix.blindpokerlab;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.window.OnBackInvokedDispatcher;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Affiche l'appli web Blind Poker Lab, embarquée dans l'APK (aucune connexion Internet nécessaire).
 * Les fichiers sont servis depuis https://appassets.androidplatform.net/assets/www/ pour que
 * l'enregistrement local (localStorage) fonctionne comme dans un navigateur.
 */
public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    private static final String START_URL = "https://appassets.androidplatform.net/assets/www/index.html";
    private static final int FILE_CHOOSER = 42;

    private WebView webView;
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private ValueCallback<Uri[]> pendingFiles;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(5, 2, 8));
        setContentView(webView);
        hideSystemBars();

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setTextZoom(100);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("appassets.androidplatform.net".equals(uri.getHost())) return false;
                // Tout autre lien s'ouvre dans le navigateur du téléphone.
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (ActivityNotFoundException ignored) {
                }
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (pendingFiles != null) pendingFiles.onReceiveValue(null);
                pendingFiles = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER);
                } catch (ActivityNotFoundException e) {
                    pendingFiles = null;
                    return false;
                }
                return true;
            }
        });

        webView.addJavascriptInterface(new Bridge(), "PokerLabAndroid");
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::askToQuit);
        }
        tts = new TextToSpeech(this, this);

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(START_URL);
        }
    }

    // ---------- Synthèse vocale ----------

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) return;
        ttsReady = true;
        runOnUiThread(() -> webView.evaluateJavascript(
                "window.__pokerLabVoicesReady && window.__pokerLabVoicesReady()", null));
    }

    private class Bridge {
        @JavascriptInterface
        public void speak(String text, String lang, float rate, float pitch) {
            if (!ttsReady || text == null) return;
            Locale locale = Locale.forLanguageTag(lang == null || lang.isEmpty() ? "fr-FR" : lang);
            if (tts.isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE) {
                locale = lang != null && lang.toLowerCase(Locale.ROOT).startsWith("en") ? Locale.US : Locale.FRANCE;
            }
            tts.setLanguage(locale);
            tts.setSpeechRate(Math.max(0.3f, Math.min(rate, 3f)));
            tts.setPitch(Math.max(0.3f, Math.min(pitch, 2f)));
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pokerlab");
        }

        @JavascriptInterface
        public void stop() {
            if (ttsReady) tts.stop();
        }

        @JavascriptInterface
        public String voices() {
            JSONArray list = new JSONArray();
            if (!ttsReady) return list.toString();
            String[][] wanted = {{"fr-FR", "Français"}, {"en-US", "English"}};
            for (String[] w : wanted) {
                if (tts.isLanguageAvailable(Locale.forLanguageTag(w[0])) >= TextToSpeech.LANG_AVAILABLE) {
                    try {
                        JSONObject v = new JSONObject();
                        v.put("name", w[1]);
                        v.put("lang", w[0]);
                        v.put("default", list.length() == 0);
                        v.put("localService", true);
                        v.put("voiceURI", w[0]);
                        list.put(v);
                    } catch (Exception ignored) {
                    }
                }
            }
            return list.toString();
        }
    }

    // ---------- Photos des joueurs (sélecteur de fichier) ----------

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER || pendingFiles == null) return;
        pendingFiles.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
        pendingFiles = null;
    }

    // ---------- Plein écran ----------

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    // ---------- Bouton retour : on demande avant de quitter, pour ne pas couper un tournoi ----------

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        askToQuit();
    }

    private void askToQuit() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.quit_question)
                .setPositiveButton(R.string.quit_yes, (d, w) -> finish())
                .setNegativeButton(R.string.quit_no, null)
                .show();
    }

    // ---------- Cycle de vie ----------

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        webView.destroy();
        super.onDestroy();
    }
}
