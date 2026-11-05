// The Pill's "Not secure" marker and its explanation.
package com.jlees.mibrowser.web

import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.logic.isNotSecure

/** The visible page's connection isn't secure (see [isNotSecure]); the pill shows a "Not secure" marker. */
val BrowserViewModel.connectionNotSecure get() = isNotSecure(activeUrl, certExceptions.hostsFor(tabs.current?.profile))

fun BrowserViewModel.explainNotSecure() {
    val certError = activeUrl?.startsWith("https://", ignoreCase = true) == true
    showAlert(
        "Connection not secure",
        if (certError) "You chose to continue to ${displayHost(activeUrl)} despite a certificate problem, so this connection may be intercepted. Don't enter passwords, payment details or other sensitive information here."
        else "${displayHost(activeUrl)} doesn't use an encrypted connection, so others on the network could see or change what you send and receive. Don't enter passwords, payment details or other sensitive information here.",
    )
}
