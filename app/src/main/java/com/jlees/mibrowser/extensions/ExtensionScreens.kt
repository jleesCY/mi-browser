// The extension screens over the app: the install dialog (with its rating), an extension's popup or options page in a
// sheet, and the "Add to mi." bar on a store page. Also the pieces the Settings pages share: the rating badge and an
// extension's icon.
package com.jlees.mibrowser.extensions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ui.ModalFrame
import com.jlees.mibrowser.ui.common.*

/** "Might have problems" is amber, whatever the accent: a warning should never look like the accent's "Works". */
@Composable
fun fitAmber(): Color = if (LocalUi.current.isDark) Color(0xFFF2B84B) else Color(0xFF9A5B00)

/** A rating's colour (the badge, the Library list). */
@Composable
internal fun fitColor(fit: Fit): Color = when (fit) { Fit.WORKS -> LocalUi.current.cs.primary; Fit.MAYBE -> fitAmber(); Fit.NO -> LocalUi.current.cs.error }

/** What a rating says, its symbol and its colour. */
@Composable
private fun fitLook(fit: Fit): Triple<String, String, Color> =
    Triple(fit.label, when (fit) { Fit.WORKS -> "check"; Fit.MAYBE -> "warning"; Fit.NO -> "block" }, fitColor(fit))

/** "Tested in mi": tested by hand (VerifiedExtensions), shown in place of the rating worked out from its code. */
@Composable
fun VerifiedBadge(modifier: Modifier = Modifier, small: Boolean = false) {
    val ui = LocalUi.current
    val color = ui.cs.primary
    Row(modifier.clip(CircleShape).background(color.copy(alpha = 0.16f)).padding(horizontal = if (small) 8.dp else 10.dp, vertical = if (small) 2.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Sym("check", if (small) 13f else 16f, color, Modifier.padding(end = if (small) 4.dp else 6.dp))
        Txt("Tested in mi", if (small) 11f else 13f, color, ui.bold, maxLines = 1)
    }
}

/** For a verified extension: a warning when this phone's WebView is older than it suits (VerifiedExtensions). */
@Composable
fun WebViewWarning(v: VerifiedExtension?) {
    v ?: return
    val ui = LocalUi.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val major = remember { webViewMajor(ctx) }
    val text = webViewWarning(v, major) ?: return
    val bad = webViewFit(v, major) == WebViewFit.WONT_WORK
    Txt(text, 13f, if (bad) ui.cs.error else fitAmber(), ui.semibold, Modifier.padding(top = 8.dp), lineHeight = 18f)
}

/** The rating as a small pill: its symbol and words, in its colour. */
@Composable
fun FitBadge(fit: Fit, modifier: Modifier = Modifier, small: Boolean = false) {
    val ui = LocalUi.current
    val (label, icon, color) = fitLook(fit)
    Row(modifier.clip(CircleShape).background(color.copy(alpha = 0.16f)).padding(horizontal = if (small) 8.dp else 10.dp, vertical = if (small) 2.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Sym(icon, if (small) 13f else 16f, color, Modifier.padding(end = if (small) 4.dp else 6.dp))
        Txt(label, if (small) 11f else 13f, color, ui.bold, maxLines = 1)
    }
}

/**
 * The reasons behind a rating, apart by how serious they are: what won't work (red), then what might have problems
 * (amber), each under its own heading; for Works, that everything it uses is supported.
 */
@Composable
fun FitReasons(fit: Fit, reasons: List<String>, serious: List<String> = emptyList()) {
    val ui = LocalUi.current
    if (fit == Fit.WORKS) { Txt("Everything this extension uses works in mi.", 14f, ui.cs.onSurfaceVariant, ui.regular, lineHeight = 20f); return }
    // older saved ratings have no split: all of a Not recommended's reasons count as serious then
    val bad = if (fit == Fit.NO && serious.isEmpty()) reasons else serious
    val minor = reasons - bad.toSet()
    @Composable fun Group(title: String, icon: String, color: Color, items: List<String>, top: Boolean) {
        if (items.isEmpty()) return
        Row(Modifier.padding(top = if (top) 0.dp else 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Sym(icon, 16f, color, Modifier.padding(end = 6.dp))
            Txt(title, 14f, color, ui.bold)
        }
        items.forEach { r ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Txt("•", 14f, color, ui.bold, Modifier.width(16.dp))
                Txt("$r.", 14f, ui.cs.onSurfaceVariant, ui.regular, Modifier.weight(1f), lineHeight = 20f)
            }
        }
    }
    Group("Won't Work", "block", ui.cs.error, bad, top = true)
    Group("Might Have Problems", "warning", fitAmber(), minor, top = bad.isEmpty())
}

/** An installed extension's icon (from its own files), else the extension symbol. */
@Composable
fun ExtensionIcon(id: String, size: Float, path: String? = null) {
    val ui = LocalUi.current
    val engine = ExtensionFlow.engine
    // the first that can be shown: the one asked for, the button's current one, then every icon the manifest lists
    // (an SVG can't be, so a PNG of another size stands in); the button's state is read so the icon appears as soon as
    // the extension is added
    val m = engine?.infos?.get(id)?.manifest
    val candidates = listOfNotNull(path, engine?.actions?.get(id)?.icon, m?.icon(48)) +
        (m?.icons.orEmpty() + m?.actionIcons.orEmpty()).entries.sortedBy { kotlin.math.abs(it.key - 48) }.map { it.value }
    val file = candidates.firstNotNullOfOrNull { c -> engine?.dirOf(id)?.resolve(c.trimStart('/'))?.takeIf { f -> f.isFile && f.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp", "gif") } }
    NetImage(file?.path, Modifier.size(size.dp)) { Sym("extension", size * 0.8f, ui.cs.onSurfaceVariant) }
}

/** What an extension can reach, in plain words, from its host permissions. */
fun reachOf(m: Manifest): String? = when {
    m.hostPermissions.any { it == "<all_urls>" || it.matches(Regex("""^(\*|https?)://\*/.*""")) } -> "Can read and change every site you visit"
    m.hostPermissions.isNotEmpty() || m.contentScripts.isNotEmpty() -> {
        val sites = (m.hostPermissions + m.contentScripts.flatMap { it.matches })
            .mapNotNull { Regex("""://(?:\*\.)?([^/*]+)""").find(it)?.groupValues?.get(1) }.distinct()
        if (sites.isEmpty() || m.contentScripts.any { cs -> cs.matches.any { it == "<all_urls>" || it.contains("://*/") } }) "Can read and change every site you visit"
        else "Can read and change ${sites.take(3).joinToString(", ")}${if (sites.size > 3) " and ${sites.size - 3} more" else ""}"
    }
    else -> null
}

/**
 * The install dialog. It opens as soon as an extension is chosen, with what the store says about it (name, summary,
 * icon) and the download's progress where the rating will be; once the package is read, its rating, reasons and reach
 * fill in and Add comes on. Cancel works throughout (a download stops).
 */
@Composable
fun ExtensionInstallDialog(vm: BrowserViewModel) {
    val ui = LocalUi.current
    val fetching = ExtensionFlow.fetching
    val pending = ExtensionFlow.pending
    val listing = ExtensionFlow.listing
    val open = fetching != null || pending != null
    // kept while the dialog fades out, so its words don't vanish first
    var lastPending by remember { mutableStateOf(pending) }
    var lastListing by remember { mutableStateOf(listing) }
    if (pending != null) lastPending = pending
    if (listing != null) lastListing = listing
    if (!open && lastPending == null && lastListing == null) return
    val p = if (open) pending else lastPending
    val l = if (open) listing else lastListing
    val name = p?.info?.name ?: l?.name ?: fetching ?: "extension"
    val verified = verifiedFor(storeSource(p?.source)) != null
    // a verified extension was tested in mi: no "Add Anyway" for it, whatever its code scan says
    val fit = p?.info?.compatibility?.fit?.let { if (verified) Fit.WORKS else it }
    ModalFrame(open, onDismiss = { vm.cancelExtension() }) {
        DialogCard(if (p?.replacing != null) "Update $name?" else "Add $name?", actions = {
            // as in the app's other sheets (Follow price): Cancel outlined, the action filled, side by side; Add waits for the check
            androidx.compose.material3.OutlinedButton({ vm.cancelExtension() }, Modifier.weight(1f).height(48.dp).padding(bottom = 4.dp)) { Txt("Cancel", 15f, ui.cs.primary, ui.bold) }
            val label = if (fit == Fit.NO) "Add Anyway" else if (p?.replacing != null) "Update" else "Add"
            androidx.compose.material3.Button({
                // Not recommended: a second, plain question first: they're taking it on knowing the risks
                if (fit == Fit.NO) vm.showAlert("Add $name Anyway?",
                    "mi can't do some of what this extension needs, so it may not work properly, and mi can't make it safe. Add it only if you accept those risks.",
                    listOf(com.jlees.mibrowser.AlertButton("Cancel", com.jlees.mibrowser.ButtonStyle.CANCEL),
                        com.jlees.mibrowser.AlertButton("Add Anyway", com.jlees.mibrowser.ButtonStyle.DESTRUCTIVE) { vm.confirmExtension() }))
                else vm.confirmExtension()
            }, Modifier.weight(1f).height(48.dp).padding(bottom = 4.dp), enabled = p != null,
                colors = if (fit == Fit.NO) androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = ui.cs.error, contentColor = ui.cs.onError)
                    else androidx.compose.material3.ButtonDefaults.buttonColors()) {
                Txt(label, 15f, if (p == null) ui.cs.onSurface.copy(alpha = 0.38f) else if (fit == Fit.NO) ui.cs.onError else ui.cs.onPrimary, ui.bold, maxLines = 1)
            }
        }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(ui.cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                    val iconFile = p?.info?.manifest?.icon(48)?.let { p.info.dir.resolve(it.trimStart('/')) }?.takeIf { it.isFile }
                    NetImage(iconFile?.path ?: p?.iconUrl ?: l?.iconUrl, Modifier.size(36.dp)) { Sym("extension", 28f, ui.cs.onSurfaceVariant) }
                }
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    if (fit != null) { if (verified) VerifiedBadge() else FitBadge(fit) }
                    if (fit != null) WebViewWarning(verifiedFor(storeSource(p?.source)))
                    else {
                        // still on its way: how far the download is, then the check
                        val pr = ExtensionFlow.progress
                        if (ExtensionFlow.checking || pr == null) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 6.dp))
                        else androidx.compose.material3.LinearProgressIndicator(progress = { pr }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                        Txt(if (ExtensionFlow.checking) "Checking how well it works in mi…" else if (pr != null) "Downloading… ${(pr * 100).toInt()}%" else "Downloading…",
                            12f, ui.cs.onSurfaceVariant, ui.regular)
                    }
                    (p?.info?.manifest?.versionName ?: l?.version)?.let { Txt("Version $it", 13f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(top = 6.dp)) }
                }
            }
            val description = p?.info?.description?.takeIf { it.isNotBlank() } ?: l?.summary
            if (!description.isNullOrBlank()) Txt(description, 14f, ui.cs.onSurface, ui.regular, Modifier.padding(top = 14.dp), lineHeight = 20f)
            if (p != null) {
                Spacer(Modifier.height(14.dp))
                FitReasons(p.info.compatibility.fit, p.info.compatibility.reasons, p.info.compatibility.serious)
                reachOf(p.info.manifest)?.let { reach ->
                    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Sym("shield", 18f, ui.cs.onSurfaceVariant, Modifier.padding(end = 10.dp))
                        Txt(reach, 14f, ui.cs.onSurfaceVariant, ui.regular, Modifier.weight(1f), lineHeight = 20f)
                    }
                }
            }
        }
    }
}

/** An extension's popup or options page, in a sheet from the bottom. */
@Composable
fun ExtensionPageSheet() {
    val ui = LocalUi.current
    val page = ExtensionFlow.page
    var shown by remember { mutableStateOf(page) }
    if (page != null) shown = page
    val p = shown ?: return
    val engine = ExtensionFlow.engine ?: return
    val close = { ExtensionFlow.page = null }
    ModalFrame(page != null, onDismiss = close, dismissOnScrim = true) {
        val maxH = LocalConfiguration.current.screenHeightDp.dp * (if (p.kind == ExtensionEngine.Kind.OPTIONS) 0.85f else 0.7f)
        Column(
            Modifier.align(Alignment.BottomCenter).widthIn(max = COMFORTABLE_WIDTH.dp).fillMaxWidth().padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(28.dp)).background(ui.cs.surfaceContainerHigh).blockTouches(),
        ) {
            ExtensionSheetHeader(p.ext, engine.infos[p.ext]?.name ?: "Extension", null, close)
            // the page itself: an extension popup sizes to its content, so it gets the room up to the sheet's height
            key(p) {
                AndroidView(
                    factory = { ctx -> engine.pages.openPage(ctx, p, close) ?: android.view.View(ctx) },
                    onRelease = { v -> (v as? android.webkit.WebView)?.let { engine.pages.closePage(it) } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = maxH).height(maxH).padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
                )
            }
        }
    }
}

/** An extension sheet's top: its icon, [title] (and [subtitle]), and a close button. */
@Composable
internal fun ExtensionSheetHeader(ext: String, title: String, subtitle: String?, close: () -> Unit) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        ExtensionIcon(ext, 22f)
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Txt(title, 16f, ui.cs.onSurface, ui.bold, maxLines = 1)
            subtitle?.let { Txt(it, 13f, ui.cs.onSurfaceVariant, ui.regular, maxLines = 1, pan = false) }
        }
        androidx.compose.material3.IconButton(onClick = close) { Sym("close", 22f, ui.cs.onSurfaceVariant, label = "Close") }
    }
}

/** On an extension's store page: "Add to mi." over the page, as the store's own button can't install into mi. */
@Composable
fun ExtensionOfferBar(vm: BrowserViewModel, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    val offer = ExtensionFlow.offer
    var shown by remember { mutableStateOf(offer) }
    if (offer != null) shown = offer
    AnimatedVisibility(offer != null, modifier, enter = slideInVertically(Motion.enter()) { it }, exit = slideOutVertically(Motion.exit()) { it } + fadeOut(Motion.exit())) {
        val o = shown ?: return@AnimatedVisibility
        Row(
            Modifier.widthIn(max = PILL_MAX_WIDTH.dp).fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(28.dp))
                .background(ui.cs.inverseSurface).padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Sym("extension", 20f, ui.cs.inverseOnSurface, Modifier.padding(end = 10.dp))
            Txt("Add ${o.name} to mi.", 15f, ui.cs.inverseOnSurface, ui.semibold, Modifier.weight(1f), maxLines = 1)
            TextButton(onClick = { ExtensionFlow.dismissed += o.pageUrl; ExtensionFlow.offer = null }) {
                Text("Not Now", color = ui.cs.inverseOnSurface.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = { vm.fetchExtension(o.source, o.name) }) {
                Text("Add", color = ui.cs.inversePrimary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Keeps the "Add to mi." bar in step with the page showing: offered on an extension's Firefox Add-ons page, unless it's installed. */
fun BrowserViewModel.updateExtensionOffer() {
    val t = tabs.current
    val source = if (t == null || t.incognito || t.loading) null else storeSource(t.url) as? InstallSource.Amo
    if (source == null || t == null || t.url in ExtensionFlow.dismissed) { ExtensionFlow.offer = null; return }
    val name = storeTitleName(t.title).ifBlank { "this extension" }
    // already installed from this page: nothing to offer
    if (extensions.list.any { e -> e.source?.contains("/addon/${source.slug}/") == true }) { ExtensionFlow.offer = null; return }
    ExtensionFlow.offer = StoreOffer(name, source, t.url!!)
}
