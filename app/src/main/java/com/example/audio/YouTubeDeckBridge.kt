package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.model.DeckId

interface YouTubeDeckListener {
    fun onReady(deckId: DeckId)
    fun onStateChanged(deckId: DeckId, stateCode: Int)
    fun onProgress(deckId: DeckId, currentSeconds: Double, durationSeconds: Double)
    fun onError(deckId: DeckId, errorCode: Int)
}

/**
 * Wraps an embedded YouTube IFrame Player inside a WebView for a single DJ Deck,
 * configured with explicit app package origin, third-party cookies, and referrer headers.
 */
class YouTubeDeckBridge(
    context: Context,
    private val deckId: DeckId,
    private val listener: YouTubeDeckListener
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val appOrigin = "https://${context.packageName}"
    private var isApiReady = false
    private val pendingCommands = mutableListOf<String>()

    @SuppressLint("SetJavaScriptEnabled")
    val webView: WebView = WebView(context.applicationContext).apply {
        setBackgroundColor(android.graphics.Color.BLACK)
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = userAgentString.replace("; wv", "")
        }
        webChromeClient = WebChromeClient()
        webViewClient = WebViewClient()
        addJavascriptInterface(JsBridge(), "AndroidDeck")
        loadDataWithBaseURL(
            appOrigin,
            buildPlayerHtml(appOrigin),
            "text/html",
            "UTF-8",
            null
        )
    }

    fun loadVideo(videoId: String, autoPlay: Boolean, volumePercent: Int, playbackRate: Float) {
        val safeId = videoId.replace(Regex("[^A-Za-z0-9_-]"), "")
        val vol = volumePercent.coerceIn(0, 100)
        val cmd = if (autoPlay) {
            "window.deckLoadAndPlay('$safeId', $vol, $playbackRate);"
        } else {
            "window.deckCue('$safeId', $vol, $playbackRate);"
        }
        mainHandler.post {
            if (!isApiReady) {
                pendingCommands.clear()
                pendingCommands.add(cmd)
            } else {
                webView.evaluateJavascript(cmd, null)
            }
        }
    }

    fun play() = runJs("window.deckPlay();")
    fun pause() = runJs("window.deckPause();")
    fun stop() = runJs("window.deckStop();")
    fun seekTo(seconds: Int) = runJs("window.deckSeek($seconds);")
    fun setVolume(volumePercent: Int) = runJs("window.deckSetVolume(${volumePercent.coerceIn(0, 100)});")
    fun setPlaybackRate(rate: Float) = runJs("window.deckSetRate($rate);")

    fun destroy() {
        mainHandler.post {
            pendingCommands.clear()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    }

    private fun runJs(script: String) {
        mainHandler.post {
            if (!isApiReady) {
                pendingCommands.add(script)
            } else {
                webView.evaluateJavascript(script, null)
            }
        }
    }

    inner class JsBridge {
        @JavascriptInterface
        fun onPlayerReady() {
            mainHandler.post {
                isApiReady = true
                listener.onReady(deckId)
                if (pendingCommands.isNotEmpty()) {
                    val toRun = pendingCommands.toList()
                    pendingCommands.clear()
                    toRun.forEach { cmd ->
                        webView.evaluateJavascript(cmd, null)
                    }
                }
            }
        }

        @JavascriptInterface
        fun onPlayerStateChange(state: Int) {
            mainHandler.post { listener.onStateChanged(deckId, state) }
        }

        @JavascriptInterface
        fun onPlayerProgress(currentSec: Double, durationSec: Double) {
            mainHandler.post { listener.onProgress(deckId, currentSec, durationSec) }
        }

        @JavascriptInterface
        fun onPlayerError(errorCode: Int) {
            mainHandler.post { listener.onError(deckId, errorCode) }
        }
    }

    private fun buildPlayerHtml(originUrl: String): String = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <meta name="referrer" content="origin">
          <style>
            html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #090B10; overflow: hidden; }
            #player { width: 100%; height: 100%; }
          </style>
        </head>
        <body>
          <div id="player"></div>
          <script src="https://www.youtube.com/iframe_api"></script>
          <script>
            var player = null;
            var ready = false;
            var pendingAction = null;
            var progressTimer = null;

            function onYouTubeIframeAPIReady() {
              player = new YT.Player('player', {
                width: '100%',
                height: '100%',
                playerVars: {
                  autoplay: 0,
                  controls: 0,
                  disablekb: 1,
                  enablejsapi: 1,
                  fs: 0,
                  modestbranding: 1,
                  playsinline: 1,
                  rel: 0,
                  origin: '$originUrl',
                  widget_referrer: '$originUrl'
                },
                events: {
                  onReady: function(e) {
                    try {
                      var iframe = player.getIframe();
                      if (iframe) {
                        iframe.setAttribute('referrerpolicy', 'strict-origin-when-cross-origin');
                        iframe.setAttribute('allow', 'accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture');
                      }
                    } catch (err) {}
                    ready = true;
                    AndroidDeck.onPlayerReady();
                    if (pendingAction) {
                      pendingAction();
                      pendingAction = null;
                    }
                    if (!progressTimer) {
                      progressTimer = setInterval(function() {
                        if (player && typeof player.getCurrentTime === 'function') {
                          var cur = player.getCurrentTime() || 0;
                          var dur = player.getDuration() || 0;
                          AndroidDeck.onPlayerProgress(cur, dur);
                        }
                      }, 250);
                    }
                  },
                  onStateChange: function(e) {
                    AndroidDeck.onPlayerStateChange(e.data);
                  },
                  onError: function(e) {
                    AndroidDeck.onPlayerError(e.data);
                  }
                }
              });
            }

            window.deckLoadAndPlay = function(videoId, vol, rate) {
              var fn = function() {
                player.setVolume(vol);
                player.loadVideoById({ videoId: videoId, startSeconds: 0 });
                player.setPlaybackRate(rate);
                player.playVideo();
              };
              if (ready) fn(); else pendingAction = fn;
            };

            window.deckCue = function(videoId, vol, rate) {
              var fn = function() {
                player.setVolume(vol);
                player.cueVideoById({ videoId: videoId, startSeconds: 0 });
                player.setPlaybackRate(rate);
              };
              if (ready) fn(); else pendingAction = fn;
            };

            window.deckPlay = function() {
              if (ready && player) player.playVideo();
            };

            window.deckPause = function() {
              if (ready && player) player.pauseVideo();
            };

            window.deckStop = function() {
              if (ready && player) player.stopVideo();
            };

            window.deckSeek = function(sec) {
              if (ready && player) player.seekTo(sec, true);
            };

            window.deckSetVolume = function(vol) {
              if (ready && player) player.setVolume(vol);
            };

            window.deckSetRate = function(rate) {
              if (ready && player) player.setPlaybackRate(rate);
            };
          </script>
        </body>
        </html>
    """.trimIndent()
}
