package com.jlees.mibrowser.library

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.data.Favorite
import com.jlees.mibrowser.ui.ModalFrame
import com.jlees.mibrowser.ui.common.DialogAction
import com.jlees.mibrowser.ui.common.DialogCard
import com.jlees.mibrowser.ui.common.LabeledField

/**
 * Editing a favorite (hold one in the favorites bar and let go): its name and address, Save, or Delete. Deleting lives
 * here on purpose, so it's never one slip of a drag away.
 */
@Composable
fun FavoriteDialog(store: BookmarksStore, favorite: Favorite?, onClose: () -> Unit) {
    var title by remember(favorite) { mutableStateOf(favorite?.title.orEmpty()) }
    var url by remember(favorite) { mutableStateOf(favorite?.url.orEmpty()) }
    // a typed address without a scheme gets https://; it needs a host to be saved
    val address = url.trim().let { if (it.isEmpty() || "://" in it) it else "https://$it" }
    val valid = runCatching { !Uri.parse(address).host.isNullOrBlank() }.getOrDefault(false)
    ModalFrame(favorite != null, onClose) {
        DialogCard("Edit Favorite", actions = {
            DialogAction("Delete", destructive = true) { favorite?.let { store.removeFavorite(it.id) }; onClose() }
            DialogAction("Cancel", onClick = onClose)
            DialogAction("Save") { if (valid && favorite != null) { store.updateFavorite(favorite.id, title.trim(), address); onClose() } }
        }) {
            LabeledField("Name", title, { title = it }, Modifier.padding(bottom = 15.dp))
            LabeledField("Address", url, { url = it }, uri = true)
        }
    }
}
