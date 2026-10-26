package com.cleantv.wifilauncher

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private var wifiServer: WifiWebServer? = null
    private lateinit var prefs: LauncherPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = LauncherPrefs(this)
        webView = WebView(this)
        setContentView(webView)

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false

        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(TvBridge(), "AndroidTVBridge")

        // Inicia o servidor HTTP na porta 8080 para controle via navegador no Wi-Fi
        val ip = getLocalWifiIp()
        wifiServer = WifiWebServer(this, prefs, 8080) { updatedStateJson ->
            runOnUiThread {
                webView.evaluateJavascript("window.onRemoteStateUpdated && window.onRemoteStateUpdated($updatedStateJson);", null)
            }
        }
        wifiServer?.startServer()

        // Carrega a interface limpa da Launcher
        webView.loadUrl("file:///android_asset/web/index.html")
        Toast.makeText(this, "CleanTV ativa! Painel Wi-Fi: http://$ip:8080", Toast.LENGTH_LONG).show()
    }

    @Suppress("DEPRECATION")
    private fun getLocalWifiIp(): String {
        return try {
            val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
        } catch (e: Exception) {
            "192.168.1.50"
        }
    }

    inner class TvBridge {
        @JavascriptInterface
        fun launchApp(packageName: String) {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
                ?: packageManager.getLeanbackLaunchIntentForPackage(packageName)
            if (intent != null) {
                startActivity(intent)
            } else {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "App não encontrado: $packageName", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun openUrlOnTv(url: String) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Não foi possível abrir o link: $url", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun getInstalledTvAppsJson(): String {
            val pm = packageManager
            val appsArray = JSONArray()
            val packages = pm.getInstalledApplications(0)
            for (appInfo in packages) {
                val launchIntent = pm.getLeanbackLaunchIntentForPackage(appInfo.packageName)
                    ?: pm.getLaunchIntentForPackage(appInfo.packageName)
                if (launchIntent != null && appInfo.packageName != packageName) {
                    val obj = JSONObject()
                    obj.put("name", pm.getApplicationLabel(appInfo).toString())
                    obj.put("pkg", appInfo.packageName)
                    appsArray.put(obj)
                }
            }
            return appsArray.toString()
        }
    }

    override fun onDestroy() {
        wifiServer?.stopServer()
        super.onDestroy()
    }
}
