package com.jlees.mibrowser

import com.jlees.mibrowser.library.BookmarkEntry.Folder
import com.jlees.mibrowser.library.BookmarkEntry.Link
import com.jlees.mibrowser.library.addBookmark
import com.jlees.mibrowser.library.childFolders
import com.jlees.mibrowser.library.descendantsAndSelf
import com.jlees.mibrowser.library.folderContents
import com.jlees.mibrowser.library.folderTrail
import com.jlees.mibrowser.library.countLinks
import com.jlees.mibrowser.library.withoutEmbeddedIcons
import com.jlees.mibrowser.library.importBookmarks
import com.jlees.mibrowser.library.isMiExport
import com.jlees.mibrowser.library.parseBookmarkFile
import com.jlees.mibrowser.library.writeBookmarkFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading other browsers' bookmark files, writing mi's, and merging an import into existing bookmarks. */
class BookmarkFileTest {
    private val chrome = """
        <!DOCTYPE NETSCAPE-Bookmark-file-1>
        <!-- This is an automatically generated file. -->
        <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
        <TITLE>Bookmarks</TITLE>
        <H1>Bookmarks</H1>
        <DL><p>
            <DT><H3 ADD_DATE="1700000000" PERSONAL_TOOLBAR_FOLDER="true">Bookmarks bar</H3>
            <DL><p>
                <DT><A HREF="https://github.com/" ADD_DATE="1700000001" ICON="data:image/png;base64,AAA">GitHub</A>
                <DT><H3>News</H3>
                <DL><p>
                    <DT><A HREF="https://news.ycombinator.com/">Hacker News</A>
                </DL><p>
            </DL><p>
            <DT><A HREF="https://example.com/">Example</A>
        </DL><p>
    """.trimIndent()

    private val firefox = """
        <!DOCTYPE NETSCAPE-Bookmark-file-1>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Bookmarks</title>
        <h1>Bookmarks Menu</h1>
        <dl><p>
            <dt><a href="place:type=6&amp;sort=14&amp;maxResults=10">Recent Tags</a>
            <dt><a href="javascript:alert(1)">Bookmarklet</a>
            <dt><h3>Empty</h3>
            <dl><p>
            </dl><p>
            <dt><h3 PERSONAL_TOOLBAR_FOLDER="true">Bookmarks Toolbar</h3>
            <dl><p>
                <dt><a href="https://www.mozilla.org/en-US/?a=1&amp;b=2">Tom &amp; Jerry&#39;s &lt;page&gt;</a>
            </dl><p>
        </dl>
    """.trimIndent()

    @Test fun readsChrome() {
        val e = parseBookmarkFile(chrome)
        assertEquals(listOf(
            Folder("Bookmarks bar", listOf(Link("GitHub", "https://github.com/"), Folder("News", listOf(Link("Hacker News", "https://news.ycombinator.com/"))))),
            Link("Example", "https://example.com/"),
        ), e)
        assertFalse(isMiExport(chrome))
    }

    @Test fun readsFirefoxSkippingWhatCantOpen() {
        // place: queries, bookmarklets and folders left empty are dropped; entities are decoded
        assertEquals(listOf(Folder("Bookmarks Toolbar", listOf(Link("Tom & Jerry's <page>", "https://www.mozilla.org/en-US/?a=1&b=2")))), parseBookmarkFile(firefox))
    }

    @Test fun miExportRoundTrips() {
        var nodes = addBookmark(emptyList(), "A & B", "https://a.com/?x=1&y=\"2\"", null)
        nodes = com.jlees.mibrowser.library.addFolder(nodes, "Work", null)
        nodes = addBookmark(nodes, "Docs", "https://docs.com/", nodes.last().id)
        val html = writeBookmarkFile(nodes)
        assertTrue(isMiExport(html))
        assertEquals(listOf(Link("A & B", "https://a.com/?x=1&y=\"2\""), Folder("Work", listOf(Link("Docs", "https://docs.com/")))), parseBookmarkFile(html))
    }

    @Test fun importSkipsKnownPagesAndMergesFolders() {
        val existing = addBookmark(emptyList(), "GitHub", "https://github.com", null)
        val (all, added, skipped) = importBookmarks(existing, parseBookmarkFile(chrome), null)
        assertEquals(2, added); assertEquals(1, skipped) // github.com/ is the same page as github.com
        assertEquals(listOf("Bookmarks bar"), all.filter { it.isFolder && it.parentId == null }.map { it.title })
        // importing the same file again adds nothing and leaves no empty folders behind
        val (again, added2, _) = importBookmarks(all, parseBookmarkFile(chrome), null)
        assertEquals(0, added2); assertEquals(all.size, again.size)
    }

    @Test fun embeddedIconsAreDroppedAndLinksCounted() {
        val slim = withoutEmbeddedIcons(chrome)
        assertFalse(slim.contains("base64"))
        assertEquals(parseBookmarkFile(chrome), parseBookmarkFile(slim)) // nothing mi uses is lost
        assertEquals(3, countLinks(parseBookmarkFile(chrome)))
    }

    @Test fun folderBrowsing() {
        var all = com.jlees.mibrowser.library.addFolder(emptyList(), "Work", null)
        val work = all.last().id
        all = com.jlees.mibrowser.library.addFolder(all, "Projects", work)
        val projects = all.last().id
        all = com.jlees.mibrowser.library.addFolder(all, "Archive", work)
        all = addBookmark(all, "Docs", "https://docs.com", work)
        assertEquals(listOf("Work"), childFolders(all, null).map { it.title })
        assertEquals(listOf("Projects", "Archive"), childFolders(all, work).map { it.title })
        assertEquals(listOf("Work", "Projects"), folderTrail(all, projects).map { it.title })
        assertEquals(emptyList<String>(), folderTrail(all, null).map { it.title })
        assertEquals(2 to 1, folderContents(all, work))
        // moving Work: it can't go inside itself or its subfolders
        assertEquals(emptyList<String>(), childFolders(all, null, descendantsAndSelf(all, work)).map { it.title })
    }
}
