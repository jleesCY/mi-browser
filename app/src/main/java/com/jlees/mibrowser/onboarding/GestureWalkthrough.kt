// The Gesture Walkthrough (Settings › About): a hands-on gesture tour over a lightweight copy of mi's own screens (the blank tab, the Pill, the tab
// switcher, the Library, Settings and History), with the part to try lit up and a caption above. Nothing real is
// touched: no tabs, history or settings change, except the flag that it was seen. Shows by itself to anyone who hasn't
// seen it (new installs, and existing users once after updating; Settings.walkthroughSeen), and again from Settings › About.
package com.jlees.mibrowser.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ui.SWITCHER_DRAG
import com.jlees.mibrowser.tabs.pullDownToOpen
import com.jlees.mibrowser.tabs.TabCardView
import com.jlees.mibrowser.tabs.cardAspect
import com.jlees.mibrowser.tabs.switcherGradient
import com.jlees.mibrowser.data.newTab
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import com.jlees.mibrowser.Sheet
import com.jlees.mibrowser.home.MiLogo
import com.jlees.mibrowser.home.homeBackground
import com.jlees.mibrowser.tabs.SwitcherEdgeButton
import com.jlees.mibrowser.tabs.SwitcherPillButtons
import com.jlees.mibrowser.ui.common.LocalHaptics
import com.jlees.mibrowser.ui.common.LocalUi
import com.jlees.mibrowser.ui.common.pillBar
import com.jlees.mibrowser.ui.common.Sym
import com.jlees.mibrowser.ui.common.Txt
import com.jlees.mibrowser.ui.common.blockTouches
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Covers the browser until it's finished or skipped; both mark it seen and leave mi on a blank tab, ready to search. */
@Composable
fun GestureWalkthrough(vm: BrowserViewModel) {
    if (!vm.ready || vm.settings.walkthroughSeen) return
    val ui = LocalUi.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val px = density.density
    val widthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val heightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    var step by remember { mutableStateOf(Step.INTRO) }
    var stepDone by remember { mutableStateOf(false) }
    val lit = remember { mutableStateListOf<Task>() } // this step's tasks that are done
    fun advance() { if (step != Step.DONE) { step = Step.entries[step.ordinal + 1]; stepDone = false; lit.clear() } }
    fun complete() {
        if (stepDone) return
        stepDone = true; haptics?.tick()
        val at = step
        scope.launch { delay(900); if (step == at) advance() }
    }
    /** The page's row tasks (delete, edit) are done: only then does going back appear, and work. Read at call time. */
    fun rowsDone() = step.chips.filterNot { it.leaves }.all { it in lit }
    /** Ticks off [task], if it's one of this step's (a swipe can match another step's: ignored). */
    fun light(task: Task) {
        if (task !in step.chips || task in lit) return
        lit += task; haptics?.tick()
        if (step.chips.all { it in lit }) complete()
    }
    fun finish() {
        vm.updateSettings { copy(walkthroughSeen = true) }
        // back to a blank tab, ready to search: whatever opened the guide (Settings, the switcher) closes properly
        if (vm.activeView != Sheet.NONE) vm.onOverlayClosed()
        vm.closeSwitcherNow() // also puts the Pill back where it belongs
        if (vm.tabs.current?.isFresh != true) vm.addNewTab()
    }
    BackHandler { finish() }
    // opened from Settings' search, the keyboard may still be up: put it away for the tour
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    // only when one is up: hiding is a call to the system's keyboard service, which can stall while the phone is busy
    @OptIn(ExperimentalLayoutApi::class) val keyboardUp = WindowInsets.isImeVisible
    LaunchedEffect(Unit) { if (keyboardUp) { focusManager.clearFocus(); keyboard?.hide() } }

    // the copy's state
    val navDrag = remember { Animatable(0f) }       // NAVIGATE: the Pill's sideways drag (dp), for its back/forward feedback
    val hidden = remember { Animatable(0f) }        // HIDE: 0 the Pill .. 1 the recall button
    val zoom = remember { Animatable(0f) }          // 0 the blank tab .. 1 the tab switcher
    val panelP = remember { Animatable(0f) }        // the step's page: 0 closed .. 1 open
    var pillRect by remember { mutableStateOf<Rect?>(null) }
    var barRect by remember { mutableStateOf<Rect?>(null) }
    var rowRect by remember { mutableStateOf<Rect?>(null) }
    var panelPillRect by remember { mutableStateOf<Rect?>(null) }
    var pageOpened by remember { mutableStateOf(false) } // the step's page has been opened: its checklist shows from then
    // the switcher copy's tabs, oldest first; the blank tab you started on is the current one, on the right
    val guideTabs = remember { mutableStateListOf(
        newTab("https://en.wikipedia.org/wiki/Mars").copy(id = "guide_wiki", title = "Mars - Wikipedia", pinned = true),
        newTab("https://example.com").copy(id = "guide_example", title = "Example Domain"),
        newTab().copy(id = GUIDE_HOME),
    ) }
    val pager = rememberPagerState(initialPage = guideTabs.lastIndex) { guideTabs.size }
    val homeIndex = guideTabs.indexOfFirst { it.id == GUIDE_HOME }
    var homeRect by remember { mutableStateOf<Rect?>(null) } // the blank tab's card: where the zoom lands, as in the app
    var cardRect by remember { mutableStateOf<Rect?>(null) } // the centered card, for the hint
    // OPEN zooms back into the blank tab: it's brought to the middle first
    LaunchedEffect(step) { panelP.snapTo(0f); pageOpened = false; if (step == Step.OPEN && pager.currentPage != homeIndex) pager.animateScrollToPage(homeIndex) }
    LaunchedEffect(pager.currentPage) { if (step == Step.CARDS && pager.currentPage != homeIndex) light(Task.OTHER_TABS) }
    val panel = step.panel
    val panelOpen = panel != null && panelP.value > 0f

    /** OPEN: let go of the swipe down; pulled a third (or flicked) it zooms into the tab, otherwise back to the switcher. */
    suspend fun releaseOpen(pulled: Float, vy: Float) {
        val open = pulled > 0.3f || vy > 500f
        zoom.animateTo(if (open) 0f else 1f, spring(0.85f, 600f))
        if (open) complete()
    }
    /** How far a swipe on the page's own search Pill has pushed it back out (0..1). */
    fun backPull(d: Offset) = when (step.panel) { // read now: these handlers outlive the composition they were made in
        Panel.LIBRARY -> -d.x / widthPx
        Panel.SETTINGS -> d.x / widthPx
        Panel.HISTORY -> d.y / (220 * px)
        null -> 0f
    }.coerceIn(0f, 1f)

    Box(Modifier.fillMaxSize().zIndex(4000f).blockTouches()) {
        // ---- the blank tab's page (below the status bar, as in the app), zooming into its card as the switcher opens
        val topPad = with(density) { maxOf(WindowInsets.statusBars.getTop(this), WindowInsets.displayCutout.getTop(this)).toDp() }
        val bottomPad = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
        Box(Modifier.fillMaxWidth().height(topPad).graphicsLayer { alpha = 1f - zoom.value.coerceIn(0f, 1f) }.background(homeBackground(false)))
        Box(
            Modifier.fillMaxSize().padding(top = topPad).graphicsLayer {
                val z = zoom.value.coerceIn(0f, 1f) // the spring can overshoot: clamped, or the corner radius goes negative
                val r = homeRect
                if (r != null && size.width > 0f && r.width > 1f) {
                    // onto the blank tab's real card, like the app's zoom
                    val sc = lerp(1f, r.width / size.width, z).coerceAtLeast(0.05f)
                    transformOrigin = TransformOrigin(0f, 0f); scaleX = sc; scaleY = sc
                    translationX = r.left * z; translationY = (r.top - topPad.toPx()) * z
                    shape = RoundedCornerShape((28.dp.toPx() * z / sc).coerceIn(0f, size.minDimension / 2)) // reads as 28dp on screen
                } else { val sc = lerp(1f, 0.72f, z); scaleX = sc; scaleY = sc; shape = RoundedCornerShape((28 * z).dp) }
                clip = z > 0f
                alpha = if (z >= 1f && r != null) 0f else 1f // landed: the card itself (with its title) takes over
            }.background(homeBackground(false)),
            contentAlignment = Alignment.Center,
        ) { MiLogo() }

        // ---- the switcher behind the shrinking page: the app's own cards, in the same pager, laid out the same way
        if (zoom.value > 0f) Column(
            Modifier.fillMaxSize().zIndex(-1f).graphicsLayer { alpha = zoom.value.coerceIn(0f, 1f) }.background(switcherGradient(ui))
                // a swipe down anywhere (card, below it) opens the blank tab, as in the app
                .pullDownToOpen(SWITCHER_DRAG,
                    onDrag = { p -> if (step == Step.OPEN) scope.launch { zoom.snapTo(1f - p) } },
                    onRelease = { open -> if (step == Step.OPEN) scope.launch { releaseOpen(if (open) 1f else 0f, 0f) } })
                .padding(top = topPad + 16.dp, bottom = bottomPad + 12.dp),
        ) {
            Txt("${guideTabs.size} tabs", 14f, ui.cs.onSurfaceVariant, ui.semibold, Modifier.align(Alignment.CenterHorizontally))
            val aspect = cardAspect(vm)
            HorizontalPager(
                pager, Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 56.dp), pageSpacing = 12.dp,
                key = { guideTabs.getOrNull(it)?.id ?: it }, userScrollEnabled = step == Step.CARDS,
            ) { page ->
                val tab = guideTabs.getOrNull(page) ?: return@HorizontalPager
                val home = tab.id == GUIDE_HOME
                TabCardView(
                    tab, active = home, showLogo = false, aspect = aspect, error = null, hidden = home && zoom.value < 1f, logoShown = true,
                    modifier = Modifier.graphicsLayer { // cover-flow: neighbours shrink and fade, as in the app
                        val off = abs(pager.currentPage - page + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                        val sc = 1f - 0.12f * off; scaleX = sc; scaleY = sc; alpha = 1f - 0.4f * off
                    },
                    // only while it's centered and still: off to the side its on-screen bounds are cut short (even to
                    // nothing), and zooming onto that would divide by zero
                    onRect = { r ->
                        val still = page == pager.currentPage && abs(pager.currentPageOffsetFraction) < 0.01f
                        if (home && still && r.width > 1f) homeRect = r
                        if (still) cardRect = r
                    },
                    onOpen = { if (step == Step.OPEN && home) scope.launch { releaseOpen(1f, 0f) } },
                    onRename = {},
                    onClosed = { guideTabs.remove(tab); light(Task.CLOSE_TAB) },
                    // the blank tab is where the tour returns: it stays (and pinned ones can't be closed, as in the app)
                    closable = step == Step.CARDS && !home && !tab.pinned,
                )
            }
            Spacer(Modifier.height(68.dp)) // the bottom bar's place (drawn below, over everything)
        }

        // ---- the browsing Pill (or, hidden, the recall button)
        if (zoom.value < 1f) BoxWithConstraints(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 10.dp, end = 10.dp, bottom = 10.dp).height(70.dp).graphicsLayer { alpha = 1f - zoom.value.coerceIn(0f, 1f) },
            contentAlignment = Alignment.BottomCenter,
        ) {
            val h = hidden.value.coerceIn(0f, 1f) // its spring back can overshoot below 0
            val outer = RoundedCornerShape(if (ui.radius >= 20) 35.dp else lerp((ui.radius * 2).toFloat(), 16f, h).dp)
            Box(
                Modifier.width(lerp(maxWidth.value, 56f, h).dp).height(lerp(70f, 32f, h).dp)
                    .onGloballyPositioned { pillRect = it.boundsInRoot() }
                    .guideDrag(
                        onTap = {
                            when {
                                step == Step.HIDE && hidden.value >= 1f -> scope.launch { hidden.animateTo(0f, spring(0.75f, 500f)); complete() }
                            }
                        },
                        onDrag = { d ->
                            scope.launch {
                                when (step) {
                                    Step.NAVIGATE -> navDrag.snapTo(d.x / px)
                                    Step.HIDE -> if (hidden.value < 1f) hidden.snapTo((d.y / px / 110f).coerceIn(0f, 0.99f))
                                    Step.TABS -> zoom.snapTo((-d.y / px / 220f).coerceIn(0f, 1f))
                                    else -> {}
                                }
                            }
                        },
                        onRelease = { d, v ->
                            scope.launch {
                                when (step) {
                                    Step.NAVIGATE -> {
                                        if (d.x / px > 24 || (d.x / px > 8 && v.x > 500f)) light(Task.GO_BACK)
                                        if (d.x / px < -24 || (d.x / px < -8 && v.x < -500f)) light(Task.GO_FORWARD)
                                        navDrag.animateTo(0f, spring(0.8f, 500f))
                                    }
                                    Step.HIDE -> if (hidden.value < 1f) hidden.animateTo(if (d.y / px / 110f > 0.25f || v.y > 500f) 1f else 0f, tween(180))
                                    Step.TABS -> {
                                        val open = -d.y / px / 220f > 0.15f || v.y < -500f
                                        zoom.animateTo(if (open) 1f else 0f, spring(0.85f, 600f))
                                        if (open) complete()
                                    }
                                    else -> {}
                                }
                            }
                        },
                    )
                    .shadow(lerp(10f, 3f, h).dp, outer).clip(outer)
                    .background(lerp(ui.pillBar, ui.cs.secondaryContainer, h))
                    .padding(horizontal = 10.dp * (1f - h)),
                contentAlignment = Alignment.Center,
            ) {
                if (h >= 0.95f) Sym("keyboard_arrow_up", 24f, ui.cs.onSecondaryContainer, label = "Show toolbar")
                else GuidePillField(navDrag.value, Modifier.graphicsLayer { alpha = (1f - h * 3f).coerceIn(0f, 1f) })
            }
        }

        // ---- the switcher's bottom bar; kept while the zoom runs either way, so a swipe that starts on it isn't cut off
        if (zoom.value > 0f && step.ordinal > Step.TABS.ordinal) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().graphicsLayer { alpha = zoom.value.coerceIn(0f, 1f) }
                    .windowInsetsPadding(WindowInsets.navigationBars).padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SwitcherEdgeButton("library_books", {}, label = "Library")
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = CircleShape, color = ui.cs.surfaceContainerHigh, shadowElevation = 6.dp,
                    modifier = Modifier.onGloballyPositioned { barRect = it.boundsInRoot() }.guideDrag(
                        onDrag = { d ->
                            scope.launch {
                                when {
                                    step == Step.OPEN -> if (d.y > 0) zoom.snapTo(1f - (d.y / px / 220f).coerceIn(0f, 1f))
                                    step.panel == Panel.LIBRARY && d.x > 0 -> panelP.snapTo((d.x / widthPx).coerceIn(0f, 1f))
                                    step.panel == Panel.SETTINGS && d.x < 0 -> panelP.snapTo((-d.x / widthPx).coerceIn(0f, 1f))
                                    step.panel == Panel.HISTORY && d.y < 0 -> panelP.snapTo((-d.y / heightPx).coerceIn(0f, 1f))
                                }
                            }
                        },
                        onRelease = { d, v ->
                            scope.launch {
                                if (step == Step.OPEN) { releaseOpen(d.y / px / 220f, v.y); return@launch }
                                // decided from the whole swipe: a quick flick may not have reported any movement before it ended
                                val (pulled, fast) = when (step.panel) {
                                    Panel.LIBRARY -> d.x / widthPx to (v.x > 500f)
                                    Panel.SETTINGS -> -d.x / widthPx to (v.x < -500f)
                                    Panel.HISTORY -> -d.y / heightPx to (v.y < -500f)
                                    null -> return@launch
                                }
                                val open = pulled > 0.2f || fast
                                if (open) pageOpened = true
                                panelP.animateTo(if (open) 1f else 0f, spring(0.9f, 700f))
                            }
                        },
                    ),
                ) { SwitcherPillButtons({}, {}) }
                Spacer(Modifier.weight(1f))
                SwitcherEdgeButton("settings", {}, label = "Settings")
            }
        }

        // ---- the step's page, sliding in with the finger
        if (panel != null && panelOpen) GuidePage(
            panel, panelP.value,
            onRow = { rowRect = it }, onPill = { panelPillRect = it },
            onDelete = { light(if (panel == Panel.LIBRARY) Task.DELETE_BOOKMARK else Task.DELETE_ENTRY) }, onEdit = if (panel == Panel.LIBRARY) { { light(Task.EDIT_BOOKMARK) } } else null,
            pillSwipe = Modifier.guideDrag(
                onDrag = { d -> if (rowsDone()) scope.launch { panelP.snapTo(1f - backPull(d)) } },
                onRelease = { d, v ->
                    if (rowsDone()) scope.launch {
                        val fast = when (step.panel) { Panel.LIBRARY -> v.x < -500f; Panel.SETTINGS -> v.x > 500f; Panel.HISTORY -> v.y > 500f; null -> false }
                        val back = backPull(d) > 0.25f || fast
                        panelP.animateTo(if (back) 0f else 1f, spring(0.9f, 700f))
                        if (back) step.chips.firstOrNull { it.leaves }?.let(::light)
                    }
                },
            ),
        )

        // ---- dim everything but what to try (not on a page: it's all to try there)
        val spot = when {
            step.ordinal <= Step.TABS.ordinal -> pillRect
            panelOpen || step == Step.CARDS || step == Step.OPEN || step == Step.DONE -> null
            else -> barRect
        }
        val dim by animateFloatAsState(if (spot == null) 0f else 0.55f, label = "dim")
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color.Black.copy(alpha = dim))
            spot?.let { r ->
                val pad = 8.dp.toPx()
                drawRoundRect(Color.Transparent, Offset(r.left - pad, r.top - pad), Size(r.width + pad * 2, r.height + pad * 2), CornerRadius(r.height / 2 + pad), blendMode = BlendMode.Clear)
            }
        }

        // ---- the fingertip, acting out what to do next
        if (!stepDone && step != Step.DONE) {
            val rowPending = panel != null && !rowsDone()
            val hint: Pair<Rect?, Move> = when (step) {
                Step.INTRO -> null to Move.TAP // nothing to do: just read
                // closing is only pointed at on a tab that can be closed (not the blank one, nor the pinned one)
                Step.CARDS -> cardRect to when {
                    step.chips[0] !in lit -> Move.RIGHT
                    guideTabs.getOrNull(pager.currentPage).let { it != null && it.id != GUIDE_HOME && !it.pinned } -> Move.UP
                    else -> Move.SIDEWAYS
                }
                Step.NAVIGATE -> pillRect to Move.SIDEWAYS
                Step.HIDE -> pillRect to if (hidden.value >= 1f) Move.TAP else Move.DOWN
                Step.TABS -> pillRect to Move.UP
                Step.OPEN -> cardRect to Move.DOWN
                else -> when {
                    !panelOpen -> barRect to when (panel) { Panel.LIBRARY -> Move.RIGHT; Panel.SETTINGS -> Move.LEFT; else -> Move.UP }
                    rowPending -> rowRect to if (panel == Panel.LIBRARY) Move.SIDEWAYS else Move.LEFT
                    else -> panelPillRect to when (panel) { Panel.LIBRARY -> Move.LEFT; Panel.SETTINGS -> Move.RIGHT; else -> Move.DOWN }
                }
            }
            hint.first?.let { FingerHint(hint.second, it) }
        }

        // ---- the caption: what to do, progress, Skip. At the top, out of the Pill's way; while a page is open it glides down
        // to sit above that page's search Pill, so the page's title and section buttons stay in view (and on the tab step,
        // below the cards' titles)
        val captionBias by animateFloatAsState(if ((panel != null && panelP.value > 0.5f) || step == Step.DONE || step == Step.CARDS) 1f else -1f, spring(0.85f, 300f), label = "caption")
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(top = 8.dp, bottom = 92.dp)) {
        Column(
            Modifier.align(androidx.compose.ui.BiasAlignment(0f, captionBias)).padding(horizontal = 16.dp).fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(ui.cs.surfaceContainerHigh).padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Step.entries.forEach { s -> Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (s.ordinal <= step.ordinal) ui.cs.primary else ui.cs.outlineVariant)) }
                }
                if (step != Step.DONE) TextButton(onClick = ::finish, Modifier.padding(start = 8.dp)) { Txt("Skip", 15f, ui.cs.primary, ui.semibold) }
            }
            Txt(step.title, 22f, ui.cs.onSurface, ui.extrabold, Modifier.padding(top = 8.dp))
            Txt(
                when {
                    step == Step.HIDE && hidden.value >= 1f && !stepDone -> "Now tap the button to bring it back."
                    panelP.value >= 1f && step.inside.isNotEmpty() -> step.inside
                    else -> step.text
                },
                15f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(top = 4.dp), lineHeight = 22f,
            )
            // a page's checklist shows once the page has been opened; going back joins it once the row tasks are done
            val shown = when {
                step == Step.CARDS -> if (step.chips[0] in lit) step.chips else step.chips.take(1) // closing comes once you've moved
                panel == null -> step.chips
                !pageOpened -> emptyList()
                else -> step.chips.filter { rowsDone() || !it.leaves }
            }
            if (shown.isNotEmpty()) Column(Modifier.padding(top = 12.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) { shown.forEach { Chip(it.label, it in lit) } }
            if (step == Step.DONE) Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
                Button(onClick = ::finish) { Txt("Start browsing", 15f, ui.cs.onPrimary, ui.semibold) }
            } else if (step == Step.INTRO) Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.CenterEnd) {
                Button(onClick = ::advance) { Txt("Next", 15f, ui.cs.onPrimary, ui.semibold) }
            } else Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (stepDone) { Sym("check", 20f, ui.cs.primary, Modifier.padding(end = 6.dp)); Txt("Nice!", 15f, ui.cs.primary, ui.semibold) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    // skipping a step leaves the copy where that step would have
                    scope.launch {
                        when (step) { Step.HIDE -> hidden.snapTo(0f); Step.TABS -> zoom.snapTo(1f); Step.OPEN -> zoom.snapTo(0f); else -> {} }
                        advance()
                    }
                }) { Txt("Next", 15f, ui.cs.onSurfaceVariant, ui.semibold) }
            }
        }
        }
    }
}

