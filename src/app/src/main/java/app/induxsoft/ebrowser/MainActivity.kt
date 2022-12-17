package app.induxsoft.ebrowser

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val wb : WebView = findViewById(R.id.myWebView)
        wb.loadUrl("file:///android_asset/browser.html")

        wb.settings.javaScriptEnabled = true

        wb.webViewClient=(object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return false
            }
        })
    }

    override fun onBackPressed() {
        val wb : WebView = findViewById(R.id.myWebView)
        if(wb!= null && wb.canGoBack())
            wb.goBack();// if there is previous page open it
        else
            super.onBackPressed();//if there is no previous page, close app
    }
}