// Feedback on GitHub: the addresses of mi's issue forms (.github/ISSUE_TEMPLATE), with the fields mi already knows filled
// in (its version, the phone's WebView version), so reports say what they were made on. Each form tags its issues
// (bug, enhancement, extension-support). Nothing is sent until the person submits the form on GitHub themselves.
package com.jlees.mibrowser.logic

import java.net.URLEncoder

object Feedback {
    const val REPO = "https://github.com/jleescy/mi-browser"

    fun bugReport(version: String) = issueForm("bug_report.yml", "version" to version)
    fun featureRequest(version: String) = issueForm("feature_request.yml", "version" to version)
    /** [extension]: its name, when asked from its own page. */
    fun extensionSupport(version: String, webView: String?, extension: String? = null) =
        issueForm("extension_support.yml", "version" to version, "webview" to webView, "extension" to extension)

    /** A new issue from form [template], with [fields] (form field id → value) filled in; blank ones left out. */
    fun issueForm(template: String, vararg fields: Pair<String, String?>): String =
        "$REPO/issues/new?template=$template" + fields.filter { !it.second.isNullOrBlank() }
            .joinToString("") { (k, v) -> "&$k=" + URLEncoder.encode(v, "UTF-8").replace("+", "%20") }
}
