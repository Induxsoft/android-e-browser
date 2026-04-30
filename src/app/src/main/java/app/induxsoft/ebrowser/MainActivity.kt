package app.induxsoft.ebrowser

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dantsu.escposprinter.EscPosCharsetEncoding
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.EscPosPrinterSize
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.dantsu.escposprinter.connection.tcp.TcpConnection
import com.dantsu.escposprinter.connection.usb.UsbConnection
import com.dantsu.escposprinter.connection.usb.UsbPrintersConnections
import com.dantsu.escposprinter.textparser.PrinterTextParserImg


class MainActivity : AppCompatActivity() {
    private var uploadMessage: ValueCallback<Uri>? = null
    private var uploadMessageAboveL: ValueCallback<Array<Uri>>? = null
    val PERMISSION_BLUETOOTH = 1
    val PERMISSION_BLUETOOTH_ADMIN = 2
    val PERMISSION_BLUETOOTH_CONNECT = 3
    val PERMISSION_BLUETOOTH_SCAN = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val wb : WebView = findViewById(R.id.myWebView)
        wb.loadUrl("file:///android_asset/browser.html")

        wb.addJavascriptInterface(dsEscPrn(this,wb), "dsEscPrn")

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH), PERMISSION_BLUETOOTH);
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_ADMIN), PERMISSION_BLUETOOTH_ADMIN);
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), PERMISSION_BLUETOOTH_CONNECT);
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_SCAN), PERMISSION_BLUETOOTH_SCAN);
        }

        wb.settings.javaScriptEnabled = true
        wb.settings.domStorageEnabled=true
        wb.settings.allowContentAccess=true
        wb.settings.allowFileAccess=true

        wb.settings.allowFileAccessFromFileURLs=false
        wb.settings.allowUniversalAccessFromFileURLs=false
        wb.settings.blockNetworkImage=false
        wb.settings.blockNetworkLoads=false
        wb.settings.databaseEnabled=true
        wb.settings.defaultTextEncodingName="UTF-8"
        wb.settings.displayZoomControls=true
        wb.settings.javaScriptCanOpenWindowsAutomatically=false
        wb.settings.lightTouchEnabled=false
        wb.settings.loadsImagesAutomatically=true
        wb.settings.mediaPlaybackRequiresUserGesture=true
        wb.settings.saveFormData=false
        wb.settings.savePassword=false
        wb.settings.useWideViewPort=true
        wb.settings.setSupportMultipleWindows(false)
        wb.settings.setSupportZoom(true)

        wb.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        wb.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        // Evitar el redirecionamiento a una pestaña del navegador dentro de webview
        wb.webViewClient=(object : WebViewClient()
        {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                view?.loadUrl(request?.url.toString())
                return true
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                view?.loadUrl(url ?: "")
                return true
            }
        })

        // Poder abrir el file selector nativo de android dentro de webview
        wb.webChromeClient=(object : WebChromeClient()
        {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: WebChromeClient.FileChooserParams
            ): Boolean {
                uploadMessageAboveL = filePathCallback
                openImageChooserActivity()
                return true
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean
            {
                //Log.d("WebView", consoleMessage?.message() ?: "")
                return true
            }
        })
        if (Build.VERSION.SDK_INT > 9) {
            val gfgPolicy = ThreadPolicy.Builder().permitAll().build()
            //StrictMode.setThreadPolicy(gfgPolicy)
        }
    }
    private fun openImageChooserActivity() {
        val i = Intent(Intent.ACTION_GET_CONTENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "image/*"
        startActivityForResult(Intent.createChooser(i, "Image Chooser"), FILE_CHOOSER_RESULT_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            if (null == uploadMessage && null == uploadMessageAboveL) return
            val result = if (data == null || resultCode != Activity.RESULT_OK) null else data.data
            if (uploadMessageAboveL != null) {
                onActivityResultAboveL(requestCode, resultCode, data)
            } else if (uploadMessage != null) {
                uploadMessage!!.onReceiveValue(result)
                uploadMessage = null
            }
        }
    }
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun onActivityResultAboveL(requestCode: Int, resultCode: Int, intent: Intent?) {
        if (requestCode != FILE_CHOOSER_RESULT_CODE || uploadMessageAboveL == null)
            return
        var results: Array<Uri>? = null
        if (resultCode == Activity.RESULT_OK) {
            if (intent != null) {
                val dataString = intent.dataString
                val clipData = intent.clipData
                if (clipData != null) {
                    results = Array(clipData.itemCount){
                            i -> clipData.getItemAt(i).uri
                    }
                }
                if (dataString != null)
                    results = arrayOf(Uri.parse(dataString))
            }
        }
        uploadMessageAboveL!!.onReceiveValue(results)
        uploadMessageAboveL = null
    }

    companion object {
        private val FILE_CHOOSER_RESULT_CODE = 10000
    }

    // Evita salir de la app con el control de retroceder del dispositivo, quedando como navegación
    override fun onBackPressed() {
        val wb : WebView = findViewById(R.id.myWebView)
        if(wb!= null && wb.canGoBack())
            wb.goBack();// if there is previous page open it
        else
            super.onBackPressed();//if there is no previous page, close app
    }


}

class dsEscPrn(private val mContext: Context,private val webView: WebView){

    var printer : com.dantsu.escposprinter.EscPosPrinter? = null

    private val ACTION_USB_PERMISSION = "app.induxsoft.ebrowser.USB_PERMISSION"
    var usbReady = false
    val usbDevice = null

    private var charsetEncoding: String ="windows-1252"
    private var charsetId: Int=16
    //========== CONEXIONES
    @JavascriptInterface
    public fun setCharsetEncoding(charsetencoding: String)
    {
        charsetEncoding=charsetencoding
    }
    @JavascriptInterface
    public fun setCharsetId(_charsetid:Int)
    {
        charsetId=_charsetid
    }
    @JavascriptInterface
    public fun openPrinterTCP(address: String, port: Int, timeout: Int, prnDpi:Int, prnWidth:Float, prnCharPerLine:Int){
        Thread {
            try {
                printer?.disconnectPrinter()

                printer = EscPosPrinter(
                    TcpConnection(address, port, timeout),
                    prnDpi,
                    prnWidth,
                    prnCharPerLine,
                    EscPosCharsetEncoding(charsetEncoding, charsetId)
                )

                (mContext as Activity).runOnUiThread {
                    Toast.makeText(mContext, "Conectado correctamente", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                (mContext as Activity).runOnUiThread {
                    Toast.makeText(mContext, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
                e.printStackTrace()
            }
        }.start()
    }

    @JavascriptInterface
    fun openPrinterUSB(prnDpi:Int, prnWidth:Float, prnCharPerLine:Int) {
        Toast.makeText(mContext,"1",Toast.LENGTH_SHORT).show()
        val usbConnection = UsbPrintersConnections.selectFirstConnected(mContext)
        val usbManager = mContext.getSystemService(Context.USB_SERVICE) as UsbManager?
        Toast.makeText(mContext,"2",Toast.LENGTH_SHORT).show()
        if (usbConnection != null && usbManager != null) {
            Toast.makeText(mContext,"3",Toast.LENGTH_SHORT).show()
            val permissionIntent = PendingIntent.getBroadcast(
                mContext,
                0,
                Intent(ACTION_USB_PERMISSION),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            )
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            mContext.registerReceiver(this.usbReceiver, filter)
            usbManager.requestPermission(usbConnection.device, permissionIntent)
            Toast.makeText(mContext,"4",Toast.LENGTH_SHORT).show()
            if(usbReady) {
                Toast.makeText(mContext,"5",Toast.LENGTH_SHORT).show()
                (mContext as Activity).runOnUiThread{
                    printer?.disconnectPrinter()
                    Thread {
                        try {
                            printer = EscPosPrinter(UsbConnection(usbManager,usbDevice), prnDpi, prnWidth, prnCharPerLine)
                        }
                        catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }.start()
                }
            }
        }
    }

    @JavascriptInterface
    fun openPrinterBluetooth(prnDpi:Int, prnWidth:Float, prnCharPerLine:Int){
        (mContext as Activity).runOnUiThread{
            printer?.disconnectPrinter()
            Thread {
                try {
                    printer = EscPosPrinter(BluetoothPrintersConnections.selectFirstPaired(), prnDpi, prnWidth, prnCharPerLine)
                }
                catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        }
    }

    //========== FUNCIONES
    @JavascriptInterface
    public fun printFormattedText(text: String){
        if (printer == null) {
            (mContext as Activity).runOnUiThread {
                Toast.makeText(mContext, "Impresora no conectada", Toast.LENGTH_SHORT).show()
            }
            return
        }
        Thread {
            try {
                printer?.printFormattedText(text)
            } catch (e: Exception) {
                (mContext as Activity).runOnUiThread {
                    Toast.makeText(mContext, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
                e.printStackTrace()
            }
        }.start()
    }

    @JavascriptInterface
    public fun printFormattedTextAndCut(text: String){
        if (printer == null) {
            (mContext as Activity).runOnUiThread {
                Toast.makeText(mContext, "Impresora no conectada", Toast.LENGTH_SHORT).show()
            }
            return
        }
        Thread {
            try {
                printer?.printFormattedTextAndCut(text)
            } catch (e: Exception) {
                (mContext as Activity).runOnUiThread {
                    Toast.makeText(mContext, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
                e.printStackTrace()
            }
        }.start()
    }

    @JavascriptInterface
    public fun printFormattedTextAndOpenCashBox(text: String, feedPaper: Float){
        if (printer == null) {
            Toast.makeText(mContext, "Impresora no conectada", Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            try {
                printer?.printFormattedTextAndOpenCashBox(text, feedPaper)
            } catch (e: Exception) {
                (mContext as Activity).runOnUiThread {
                    Toast.makeText(mContext, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
                e.printStackTrace()
            }
        }.start()
    }
    fun getBitmapFromBase64(base64: String): Bitmap {
        try {
            // 🔹 limpiar posibles prefijos
            val cleanBase64 = base64
                .substringAfter("base64,", base64)
                .replace("\n", "")
                .replace("\r", "")

            // 🔹 decodificar (usar NO_WRAP 👈 clave)
            val decodedBytes = android.util.Base64.decode(cleanBase64, android.util.Base64.NO_WRAP)

            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

            return bitmap ?: throw Exception("BitmapFactory devolvió null")

        } catch (e: Exception) {
            throw Exception("Error al decodificar base64: ${e.message}")
        }
    }
    fun getBitmapFromUrl(url: String): Bitmap {
        val connection = java.net.URL(url).openConnection()
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.doInput = true
        connection.connect()

        val input = connection.getInputStream()
        val bitmap = BitmapFactory.decodeStream(input)

        input.close()

        return bitmap ?: throw Exception("No se pudo decodificar la imagen")
    }
    @JavascriptInterface
    public fun printImage(base64OrUrl: String,callbackName: String,maxWidth: Int = 384,height: Int = 0)
    {
        if (printer == null) {
            Toast.makeText(mContext, "Impresora no conectada", Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            try
            {
                // 🔹 1. Obtener bitmap
                val bitmap: Bitmap = if (base64OrUrl.startsWith("http")) {
                    getBitmapFromUrl(base64OrUrl)
                } else {
                    getBitmapFromBase64(base64OrUrl)
                }

                if (bitmap.width == 0 || bitmap.height == 0) {
                    throw Exception("Imagen inválida")
                }

                // 🔹 2. Calcular alto
                val finalHeight = if (height <= 0) {
                    (bitmap.height * maxWidth) / bitmap.width
                } else {
                    height
                }

                // 🔹 3. Redimensionar
                val resized = Bitmap.createScaledBitmap(
                    bitmap,
                    maxWidth,
                    finalHeight,
                    true
                )


                if (resized.width == 0 || resized.height == 0) {
                    throw Exception("Bitmap inválido")
                }
                // 🔹 4. Convertir a HEX
                val hex = PrinterTextParserImg.bitmapToHexadecimalString(printer, resized)

                // 🔹 5. Ejecutar callback en UI thread
                (mContext as Activity).runOnUiThread{
                    Toast.makeText(mContext, " invocar funcion", Toast.LENGTH_LONG).show()
                    val safeHex = hex.replace("'", "\\'")
                    webView.evaluateJavascript("""
                        if (typeof $callbackName === 'function') {
                            $callbackName('$safeHex');
                        } else {
                            console.error('Callback no existe: $callbackName');
                        }
                    """.trimIndent(), null)

                    Toast.makeText(mContext, " paso funcion", Toast.LENGTH_LONG).show()
                }

                // 🔹 opcional: liberar memoria
                if (resized != bitmap) {
                    bitmap.recycle()
                }

            } catch (e: Exception) {
                (mContext as Activity).runOnUiThread {
                    Toast.makeText(mContext, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
                e.printStackTrace()
            }
        }.start()
    }
    private val usbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized(this) {
                    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager?
                    val usbDevice = intent.getParcelableExtra<Parcelable>(UsbManager.EXTRA_DEVICE) as UsbDevice?
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (usbManager != null && usbDevice != null) {
                            usbReady = true
                        }
                    }
                }
            }
        }
    }
}