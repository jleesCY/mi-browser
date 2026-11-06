// QR codes: scanning with the camera (or from an image) and generating one for the current page. Uses ZXing.
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.jlees.mibrowser.qr


import com.jlees.mibrowser.search.searchEngine

import com.jlees.mibrowser.ButtonStyle

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.ui.draw.clip
import com.jlees.mibrowser.ui.common.DialogCard
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.jlees.mibrowser.AlertButton
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.logic.inputToUrl
import com.jlees.mibrowser.ui.ModalFrame
import com.jlees.mibrowser.ui.common.blockTouches
import com.jlees.mibrowser.ui.common.*
import com.jlees.mibrowser.ui.needsLegacyWritePermission
import com.jlees.mibrowser.ui.saveBitmapToGallery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

private val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)

private fun decode(source: LuminanceSource): String? = try {
    QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
} catch (_: Exception) {
    try { QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source.invert())), hints).text } catch (_: Exception) { null }
}

/** Decode a QR from an image (gallery pick). */
fun decodeImage(ctx: Context, uri: Uri): String? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    var sample = 1
    while (opts.outWidth / sample > 2000 || opts.outHeight / sample > 2000) sample *= 2
    val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
    val px = IntArray(bmp.width * bmp.height)
    bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    return decode(RGBLuminanceSource(bmp.width, bmp.height, px))
}

fun qrBitmap(text: String, sizePx: Int, quietPx: Int): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, mapOf(EncodeHintType.MARGIN to 0))
    val total = sizePx + quietPx * 2
    val bmp = Bitmap.createBitmap(total, total, Bitmap.Config.ARGB_8888)
    bmp.eraseColor(android.graphics.Color.WHITE)
    for (x in 0 until matrix.width) for (y in 0 until matrix.height) if (matrix[x, y]) bmp.setPixel(x + quietPx, y + quietPx, android.graphics.Color.BLACK)
    return bmp
}

/** A scanned code is opened like typed text: an address loads, anything else is searched (shortcuts work too). */
private fun onScan(vm: BrowserViewModel, data: String) {
    vm.qrScannerVisible = false
    vm.inputUrl = data
    val text = data.trim()
    val s = vm.settings
    val url = inputToUrl(text, s.httpsOnly, vm.searchEngine, vm.bangs) ?: return
    vm.navigateTab(url, text)
}

@Composable
fun QrScanner(vm: BrowserViewModel) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var processing by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        processing = true
        scope.launch {
            val result = withContext(Dispatchers.Default) { runCatching { decodeImage(ctx, uri) }.getOrNull() }
            processing = false
            if (result != null) { scanned = true; onScan(vm, result) }
            else vm.showAlert("No QR Code Found", "Could not detect a QR code in this image.")
        }
    }
    LaunchedEffect(vm.qrScannerVisible) {
        if (vm.qrScannerVisible) { scanned = false; processing = false; if (!granted) permLauncher.launch(Manifest.permission.CAMERA) }
    }
    val close = { vm.qrScannerVisible = false }

    AnimatedVisibility(vm.qrScannerVisible, enter = slideInVertically { it }, exit = slideOutVertically { it }, modifier = Modifier.zIndex(900f)) {
        BackHandler(onBack = close)
        if (!granted) {
            Column(Modifier.fillMaxSize().background(ui.cs.surface).blockTouches().padding(20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Txt("We need your permission to show the camera", 16f, ui.cs.onSurface, ui.regular, Modifier.padding(bottom = 20.dp), align = TextAlign.Center)
                Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Sym("qr_code_scanner", 20f, ui.cs.onPrimary, Modifier.padding(end = 8.dp)); Txt("Grant Permission", 16f, ui.cs.onPrimary, ui.bold)
                }
                FilledTonalButton(onClick = close, modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) { Txt("Close", 16f, ui.cs.onSecondaryContainer, ui.bold) }
            }
            return@AnimatedVisibility
        }
        Box(Modifier.fillMaxSize().background(Color.Black).blockTouches()) {
            CameraPreview { text -> if (!scanned && !processing) { scanned = true; onScan(vm, text) } }
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = close, modifier = Modifier.size(56.dp)) { Sym("close", 28f, ui.cs.onSecondaryContainer, label = "Close") }
                Txt("Scan QR Code", 22f, Color.White, ui.bold, Modifier.padding(start = 16.dp).weight(1f))
                if (processing) LoadingIndicator(Modifier.size(56.dp))
                else FilledTonalIconButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.size(56.dp),
                ) { Sym("image", 28f, ui.cs.onSecondaryContainer) }
            }
            Box(Modifier.align(Alignment.Center).size(250.dp).border(4.dp, ui.cs.primaryContainer, RoundedCornerShape(36.dp)))
            Txt("Align the QR code within the frame", 16f, Color.White, FontWeight.W600, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 60.dp))
        }
    }
}

@Composable
private fun CameraPreview(onResult: (String) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val latest by rememberUpdatedState(onResult)
    DisposableEffect(Unit) { onDispose { executor.shutdown(); ProcessCameraProvider.getInstance(ctx).get().unbindAll() } }
    AndroidView(factory = { c ->
        val view = PreviewView(c)
        val providerFuture = ProcessCameraProvider.getInstance(c)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(executor) { img ->
                val plane = img.planes[0]
                val buf = plane.buffer
                val bytes = ByteArray(buf.remaining()).also { buf.get(it) }
                val source = PlanarYUVLuminanceSource(bytes, plane.rowStride, img.height, 0, 0, img.width, img.height, false)
                val text = decode(source)
                img.close()
                if (text != null) view.post { latest(text) }
            }
            provider.unbindAll()
            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(c))
        view
    }, modifier = Modifier.fillMaxSize())
}

@Composable
fun QrGenerator(vm: BrowserViewModel) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val url = vm.activeUrl ?: ""
    val bmp = remember(url) { if (url.isNotEmpty()) runCatching { qrBitmap(url, 600, 30) }.getOrNull() else null }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) vm.showAlert("Permission Required", "Photo access has been denied. Please enable it in your device settings to save QR codes.", listOf(
            AlertButton("Cancel", ButtonStyle.CANCEL),
            AlertButton("Open Settings") { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))) },
        ))
    }
    ModalFrame(vm.qrGeneratorVisible, { vm.qrGeneratorVisible = false }) {
        DialogCard(null) {
          Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("QR Code", 24f, ui.cs.onSurface, ui.regular, Modifier.weight(1f), scaled = false)
                IconButton(onClick = { vm.qrGeneratorVisible = false }) { Sym("close", 24f, ui.cs.onSurfaceVariant, label = "Close") }
            }
            bmp?.let { Image(it.asImageBitmap(), null, Modifier.padding(bottom = 16.dp).size(220.dp).clip(RoundedCornerShape(20.dp)).background(Color.White)) }
            Txt(url, 12f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(bottom = 20.dp), maxLines = 2, align = TextAlign.Center)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.weight(1f).height(48.dp).clip(CircleShape).background(ui.cs.primary).touchable {
                        val b = bmp ?: return@touchable
                        if (needsLegacyWritePermission(ctx)) { permLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE); return@touchable }
                        try { saveBitmapToGallery(ctx, b, "qrcode_${System.currentTimeMillis()}.png"); vm.showAlert("Success", "QR Code saved to gallery!") }
                        catch (e: Exception) { vm.showAlert("Error", "Failed to save QR code: ${e.message ?: "Unknown error"}") }
                    },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) { Sym("download", 20f, ui.cs.onPrimary, Modifier.padding(end = 8.dp)); Txt("Save", 14f, ui.cs.onPrimary, ui.bold, scaled = false) }
                Row(
                    Modifier.weight(1f).height(48.dp).clip(CircleShape).background(ui.cs.secondaryContainer).touchable {
                        val b = bmp ?: return@touchable
                        try {
                            val f = File(ctx.cacheDir, "qrcode_share_${System.currentTimeMillis()}.png")
                            f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
                            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
                        } catch (_: Exception) { vm.showAlert("Error", "Failed to share QR code.") }
                    },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) { Sym("share", 20f, ui.cs.onSecondaryContainer, Modifier.padding(end = 8.dp)); Txt("Share", 14f, ui.cs.onSecondaryContainer, ui.bold, scaled = false) }
            }
          }
        }
    }
}

