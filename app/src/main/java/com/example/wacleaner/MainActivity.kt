package com.example.wacleaner

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.ContactsContract
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.util.concurrent.Executors

data class ContactItem(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val phone: String
)

class MainActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val items = mutableListOf<ContactItem>()

    private lateinit var status: TextView
    private lateinit var list: ListView
    private lateinit var scanButton: Button
    private lateinit var selectAllButton: Button
    private lateinit var deleteButton: Button

    private val waAccountTypes = arrayOf("com.whatsapp", "com.whatsapp.w4b")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        status = TextView(this).apply {
            text = "Tekan \"Pindai kontak\" untuk mencari kontak yang tidak terhubung WhatsApp."
            textSize = 15f
        }
        root.addView(status)

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        scanButton = Button(this).apply {
            text = "Pindai kontak"
            setOnClickListener { ensurePermissionsThenScan() }
        }
        selectAllButton = Button(this).apply {
            text = "Pilih semua"
            isEnabled = false
            setOnClickListener { toggleSelectAll() }
        }
        deleteButton = Button(this).apply {
            text = "Hapus"
            isEnabled = false
            setOnClickListener { confirmDelete() }
        }
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        buttons.addView(scanButton, lp)
        buttons.addView(selectAllButton, lp)
        buttons.addView(deleteButton, lp)
        root.addView(buttons)

        list = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_MULTIPLE
            setOnItemClickListener { _, _, _, _ -> updateDeleteButton() }
        }
        root.addView(
            list,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
    }

    // ---------- Izin ----------

    private fun ensurePermissionsThenScan() {
        val needed = arrayOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

        if (needed.isEmpty()) {
            scan()
        } else {
            requestPermissions(needed.toTypedArray(), REQ_PERMS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                scan()
            } else {
                status.text = "Izin kontak (baca dan tulis) diperlukan agar aplikasi bisa bekerja."
            }
        }
    }

    // ---------- Pemindaian ----------

    private fun scan() {
        status.text = "Memindai..."
        scanButton.isEnabled = false
        executor.execute {
            val result = try {
                doScan()
            } catch (e: Exception) {
                ScanResult(error = "Gagal memindai: ${e.message}")
            }
            runOnUiThread {
                scanButton.isEnabled = true
                if (result.error != null) {
                    status.text = result.error
                    showItems(emptyList())
                } else {
                    showItems(result.items)
                    status.text = "Ditemukan ${result.items.size} kontak tanpa WhatsApp " +
                            "dari ${result.total} kontak. Centang yang ingin dihapus."
                }
            }
        }
    }

    private class ScanResult(
        val items: List<ContactItem> = emptyList(),
        val total: Int = 0,
        val error: String? = null
    )

    private fun doScan(): ScanResult {
        // 1. Kumpulkan ID kontak yang punya entri WhatsApp.
        val waContactIds = HashSet<Long>()
        val placeholders = waAccountTypes.joinToString(",") { "?" }
        contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.CONTACT_ID),
            "${ContactsContract.RawContacts.ACCOUNT_TYPE} IN ($placeholders) " +
                    "AND ${ContactsContract.RawContacts.DELETED} = 0",
            waAccountTypes,
            null
        )?.use { c ->
            while (c.moveToNext()) waContactIds.add(c.getLong(0))
        }

        // Pengaman: jika tidak ada satu pun kontak WhatsApp, kemungkinan WhatsApp belum
        // terpasang atau sinkronisasi kontaknya mati. Jangan tampilkan semua kontak sebagai
        // "tidak terhubung" karena itu bisa menyebabkan semua kontak terhapus.
        if (waContactIds.isEmpty()) {
            return ScanResult(
                error = "Tidak ada kontak WhatsApp yang terdeteksi. Pastikan WhatsApp terpasang, " +
                        "sudah login, dan diberi izin kontak. Pemindaian dibatalkan demi keamanan."
            )
        }

        // 2. Ambil satu nomor telepon per kontak untuk ditampilkan.
        val phones = HashMap<Long, String>()
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                if (!phones.containsKey(id)) phones[id] = c.getString(1) ?: ""
            }
        }

        // 3. Semua kontak, lalu saring yang tidak ada di WhatsApp.
        val found = mutableListOf<ContactItem>()
        var total = 0
        contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
            ),
            null, null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                total++
                val id = c.getLong(0)
                if (id in waContactIds) continue
                val lookup = c.getString(1) ?: continue
                found.add(
                    ContactItem(
                        id = id,
                        lookupKey = lookup,
                        name = c.getString(2) ?: "(tanpa nama)",
                        phone = phones[id] ?: "(tanpa nomor)"
                    )
                )
            }
        }
        return ScanResult(items = found, total = total)
    }

    private fun showItems(newItems: List<ContactItem>) {
        items.clear()
        items.addAll(newItems)
        val labels = items.map { "${it.name}\n${it.phone}" }
        list.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_multiple_choice,
            labels
        )
        selectAllButton.isEnabled = items.isNotEmpty()
        selectAllButton.text = "Pilih semua"
        updateDeleteButton()
    }

    private fun toggleSelectAll() {
        val selectAll = selectAllButton.text == "Pilih semua"
        for (i in 0 until list.count) list.setItemChecked(i, selectAll)
        selectAllButton.text = if (selectAll) "Batal pilih" else "Pilih semua"
        updateDeleteButton()
    }

    private fun selectedItems(): List<ContactItem> {
        val checked = list.checkedItemPositions
        val out = mutableListOf<ContactItem>()
        for (i in 0 until checked.size()) {
            if (checked.valueAt(i)) {
                val pos = checked.keyAt(i)
                if (pos in items.indices) out.add(items[pos])
            }
        }
        return out
    }

    private fun updateDeleteButton() {
        val n = selectedItems().size
        deleteButton.isEnabled = n > 0
        deleteButton.text = if (n > 0) "Hapus ($n)" else "Hapus"
    }

    // ---------- Backup dan penghapusan ----------

    private fun confirmDelete() {
        val selected = selectedItems()
        if (selected.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Hapus ${selected.size} kontak?")
            .setMessage(
                "Aplikasi akan menyimpan backup (.vcf) ke folder Download terlebih dahulu, " +
                        "lalu menghapus kontak yang dipilih dari ponsel."
            )
            .setNegativeButton("Batal", null)
            .setPositiveButton("Backup dan hapus") { _, _ -> backupAndDelete(selected) }
            .show()
    }

    private fun backupAndDelete(selected: List<ContactItem>) {
        scanButton.isEnabled = false
        deleteButton.isEnabled = false
        status.text = "Menyimpan backup..."
        executor.execute {
            val backupName = try {
                backupToDownloads(selected)
            } catch (e: Exception) {
                null
            }

            if (backupName == null) {
                runOnUiThread {
                    scanButton.isEnabled = true
                    updateDeleteButton()
                    status.text = "Backup gagal, penghapusan dibatalkan. Tidak ada kontak yang dihapus."
                }
                return@execute
            }

            var deleted = 0
            var failed = 0
            for (item in selected) {
                try {
                    val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, item.id)
                    if (contentResolver.delete(uri, null, null) > 0) deleted++ else failed++
                } catch (e: Exception) {
                    failed++
                }
            }

            runOnUiThread {
                scanButton.isEnabled = true
                status.text = "Selesai: $deleted kontak dihapus, $failed gagal.\n" +
                        "Backup: Download/$backupName"
                scan()
            }
        }
    }

    /** Menyimpan kontak terpilih ke satu file .vcf di folder Download. Mengembalikan nama file, atau null jika gagal. */
    private fun backupToDownloads(selected: List<ContactItem>): String? {
        val fileName = "backup_kontak_${System.currentTimeMillis()}.vcf"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/x-vcard")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val target = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return null

        var written = 0
        contentResolver.openOutputStream(target)?.use { out ->
            for (item in selected) {
                val vcardUri = Uri.withAppendedPath(
                    ContactsContract.Contacts.CONTENT_VCARD_URI,
                    item.lookupKey
                )
                contentResolver.openInputStream(vcardUri)?.use { input ->
                    input.copyTo(out)
                    out.write("\r\n".toByteArray())
                    written++
                }
            }
        } ?: return null

        // Backup harus mencakup semua kontak yang akan dihapus; jika ada yang gagal, batalkan.
        if (written != selected.size) {
            contentResolver.delete(target, null, null)
            return null
        }
        return fileName
    }

    companion object {
        private const val REQ_PERMS = 100
    }
}
