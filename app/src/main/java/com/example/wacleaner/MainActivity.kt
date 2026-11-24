package com.example.wacleaner

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Environment
import android.provider.ContactsContract
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
    val phone: String,
    val accounts: String,
    val starred: Boolean
)

class ScanResult(
    val items: List<ContactItem> = emptyList(),
    val total: Int = 0,
    val waLinked: Int = 0,
    val error: String? = null
)

class DeleteReport(
    val deleted: List<ContactItem>,
    val failed: List<Pair<ContactItem, String>>,
    val cancelled: Boolean
)

class BackupResult(
    val fileName: String,
    val saved: List<ContactItem>
)

class MainActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val items = mutableListOf<ContactItem>()

    @Volatile
    private var cancelled = false
    private var busy = false
    private var suspicious = false
    private var lastTotal = 0

    private lateinit var status: TextView
    private lateinit var list: ListView
    private lateinit var scanButton: Button
    private lateinit var selectAllButton: Button
    private lateinit var deleteButton: Button
    private lateinit var cancelButton: Button

    private val waAccountTypes = arrayOf("com.whatsapp", "com.whatsapp.w4b")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    override fun onDestroy() {
        cancelled = true
        executor.shutdown()
        super.onDestroy()
    }

    // ---------- UI ----------

    private fun buildUi() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        status = TextView(this).apply {
            text = "Tekan \"Pindai kontak\" untuk mencari kontak yang tidak terhubung WhatsApp. " +
                    "Pemindaian tidak mengubah apa pun."
            textSize = 15f
        }
        root.addView(status)

        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
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
        row1.addView(scanButton, lp)
        row1.addView(selectAllButton, lp)
        row1.addView(deleteButton, lp)
        root.addView(row1)

        cancelButton = Button(this).apply {
            text = "Batalkan penghapusan"
            visibility = View.GONE
            setOnClickListener {
                cancelled = true
                status.text = "Membatalkan setelah kelompok yang sedang berjalan selesai..."
            }
        }
        root.addView(cancelButton)

        list = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_MULTIPLE
            setOnItemClickListener { _, _, _, _ -> updateDeleteButton() }
        }
        root.addView(
            list,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val infoButton = Button(this).apply {
            text = "Info keamanan"
            setOnClickListener { showSecurityInfo() }
        }
        root.addView(infoButton)

        // Anti-tapjacking: abaikan sentuhan jika ada jendela aplikasi lain yang menimpa layar.
        root.filterTouchesWhenObscured = true

        setContentView(root)
    }

    /** Menampilkan fakta keamanan yang dibaca langsung dari aplikasi yang sedang berjalan. */
    @Suppress("DEPRECATION")
    private fun showSecurityInfo() {
        val message = try {
            val info = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES
            )
            val perms = info.requestedPermissions?.toList() ?: emptyList()
            val hasInternet = perms.contains(Manifest.permission.INTERNET)
            val signer = info.signingInfo?.apkContentsSigners?.firstOrNull()
            val fingerprint = if (signer == null) {
                "tidak diketahui"
            } else {
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(signer.toByteArray())
                    .joinToString(":") { b -> "%02X".format(b) }
            }
            val debuggable =
                (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

            buildString {
                append("Izin yang diminta aplikasi ini:\n")
                if (perms.isEmpty()) append("• (tidak ada)\n")
                perms.forEach { append("• ${it.substringAfterLast('.')}\n") }
                append("\nAkses internet: ${if (hasInternet) "ADA" else "tidak ada"}")
                append("\nMode debug: ${if (debuggable) "AKTIF" else "mati"}")
                append("\nLibrary pihak ketiga: tidak ada")
                append("\nPemrosesan: seluruhnya di HP ini; kontakmu tidak dikirim ke mana pun.")
                append("\n\nSHA-256 sertifikat penandatangan:\n$fingerprint")
                append("\n\nBandingkan dengan nilai di halaman rilis GitHub. Jika berbeda, ")
                append("aplikasi ini bukan dari rilis resmi: jangan dipakai.")
            }
        } catch (e: Exception) {
            "Gagal membaca info keamanan: ${e.message}"
        }

        AlertDialog.Builder(this)
            .setTitle("Info keamanan")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
            .window?.decorView?.filterTouchesWhenObscured = true
    }

    private fun ui(block: () -> Unit) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) block()
        }
    }

    private fun setBusy(value: Boolean, deleting: Boolean = false) {
        busy = value
        scanButton.isEnabled = !value
        selectAllButton.isEnabled = !value && items.isNotEmpty()
        list.isEnabled = !value
        cancelButton.visibility = if (value && deleting) View.VISIBLE else View.GONE
        if (value) {
            deleteButton.isEnabled = false
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            updateDeleteButton()
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ---------- Izin ----------

    private fun ensurePermissionsThenScan() {
        if (busy) return
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

    // ---------- Pemindaian (hanya membaca) ----------

    private fun scan() {
        if (busy) return
        setBusy(true)
        status.text = "Memindai..."
        executor.execute {
            val result = try {
                doScan()
            } catch (e: Exception) {
                ScanResult(error = "Gagal memindai: ${e.message}")
            }
            ui {
                if (result.error != null) {
                    showItems(emptyList())
                    setBusy(false)
                    status.text = result.error
                } else {
                    suspicious = result.total > 20 && result.items.size * 10 > result.total * 9
                    lastTotal = result.total
                    showItems(result.items)
                    setBusy(false)
                    status.text = buildString {
                        append("Dari ${result.total} kontak: ${result.waLinked} terhubung WhatsApp, ")
                        append("${result.items.size} tidak terhubung.")
                        if (suspicious) {
                            append(
                                "\nPerhatian: lebih dari 90% kontak terdeteksi tidak terhubung. " +
                                        "Itu wajar jika memang kebanyakan kontakmu bukan pengguna WhatsApp. " +
                                        "Jika tidak, pastikan sinkronisasi kontak WhatsApp sudah selesai " +
                                        "sebelum menghapus. Centang yang ingin dihapus."
                            )
                        } else if (result.items.isNotEmpty()) {
                            append(" Centang yang ingin dihapus.")
                        }
                    }
                }
            }
        }
    }

    private fun accountLabel(type: String?, name: String?): String {
        val t = type ?: ""
        return when {
            t.isEmpty() -> "Lokal (HP)"
            t == "com.google" -> if (name.isNullOrEmpty()) "Google" else "Google ($name)"
            t in waAccountTypes -> "WhatsApp"
            t.contains("sim", ignoreCase = true) -> "SIM"
            else -> t.substringAfterLast('.')
        }
    }

    private fun doScan(): ScanResult {
        // 1. Semua raw contact: tandai kontak yang punya entri WhatsApp, dan catat akunnya.
        val waContactIds = HashSet<Long>()
        val accounts = HashMap<Long, MutableSet<String>>()
        contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(
                ContactsContract.RawContacts.CONTACT_ID,
                ContactsContract.RawContacts.ACCOUNT_TYPE,
                ContactsContract.RawContacts.ACCOUNT_NAME
            ),
            "${ContactsContract.RawContacts.DELETED} = 0",
            null,
            null
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val type = c.getString(1)
                val accName = c.getString(2)
                if (type != null && type in waAccountTypes) waContactIds.add(id)
                accounts.getOrPut(id) { linkedSetOf() }.add(accountLabel(type, accName))
            }
        }

        // Pengaman: jika tidak ada satu pun kontak WhatsApp, kemungkinan WhatsApp belum
        // terpasang atau sinkronisasi kontaknya mati. Jangan tampilkan semua kontak sebagai
        // "tidak terhubung".
        if (waContactIds.isEmpty()) {
            return ScanResult(
                error = "Tidak ada kontak WhatsApp yang terdeteksi. Pastikan WhatsApp terpasang, " +
                        "sudah login, dan diberi izin kontak. Pemindaian dibatalkan demi keamanan."
            )
        }

        // 2. Satu nomor telepon per kontak untuk ditampilkan.
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
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.STARRED
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
                        phone = phones[id] ?: "(tanpa nomor)",
                        accounts = accounts[id]?.joinToString(", ") ?: "?",
                        starred = c.getInt(3) == 1
                    )
                )
            }
        }
        return ScanResult(items = found, total = total, waLinked = total - found.size)
    }

    private fun showItems(newItems: List<ContactItem>) {
        items.clear()
        items.addAll(newItems)
        val labels = items.map {
            (if (it.starred) "★ " else "") + "${it.name}\n${it.phone}  •  ${it.accounts}"
        }
        list.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_multiple_choice,
            labels
        )
        selectAllButton.text = "Pilih semua"
        selectAllButton.isEnabled = items.isNotEmpty() && !busy
        updateDeleteButton()
    }

    private fun toggleSelectAll() {
        val selectAll = selectAllButton.text == "Pilih semua"
        for (i in items.indices) {
            // Kontak berbintang (favorit) tidak ikut dipilih otomatis.
            list.setItemChecked(i, selectAll && !items[i].starred)
        }
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
        deleteButton.isEnabled = n > 0 && !busy
        deleteButton.text = if (n > 0) "Hapus ($n)" else "Hapus"
    }

    // ---------- Hapus (pelan-pelan, dengan backup) ----------

    private fun batteryOk(): Boolean {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return bm.isCharging || level < 0 || level >= MIN_BATTERY_PERCENT
    }

    private fun confirmDelete() {
        if (busy) return
        val selected = selectedItems()
        if (selected.isEmpty()) return

        if (!batteryOk()) {
            status.text = "Baterai di bawah $MIN_BATTERY_PERCENT% dan tidak sedang diisi. " +
                    "Isi daya dulu, lalu coba lagi."
            return
        }

        val round = selected
        val starred = round.count { it.starred }
        val message = buildString {
            append("Kontak yang akan dihapus: ${round.size}.\n\n")
            append(round.take(5).joinToString("\n") { "• ${it.name}" })
            if (round.size > 5) append("\n• ...dan ${round.size - 5} lainnya")
            if (starred > 0) {
                append("\n\nPerhatian: $starred kontak berbintang (favorit) ikut terpilih.")
            }
            append(
                "\n\nBackup .vcf disimpan ke folder Download lebih dulu. Penghapusan " +
                        "berjalan pelan-pelan dan bisa dibatalkan."
            )
        }

        AlertDialog.Builder(this)
            .setTitle("Hapus ${round.size} kontak?")
            .setMessage(message)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Backup dan hapus") { _, _ -> confirmLarge(round) }
            .show()
            .window?.decorView?.filterTouchesWhenObscured = true
    }

    /** Konfirmasi kedua untuk penghapusan besar, atau saat hampir semua kontak ikut terhapus. */
    private fun confirmLarge(round: List<ContactItem>) {
        if (round.size < LARGE_DELETE_THRESHOLD && !suspicious) {
            startDelete(round)
            return
        }
        val percent = if (lastTotal > 0) round.size * 100 / lastTotal else 0
        AlertDialog.Builder(this)
            .setTitle("Yakin menghapus ${round.size} kontak?")
            .setMessage(
                "Ini ${round.size} dari $lastTotal kontak di HP-mu (sekitar $percent%).\n\n" +
                        "Backup .vcf dibuat dulu. Untuk jumlah sebesar ini prosesnya bisa " +
                        "memakan beberapa menit: biarkan layar menyala dan HP diisi daya. " +
                        "Kamu bisa membatalkan kapan saja."
            )
            .setNegativeButton("Batal", null)
            .setPositiveButton("Ya, hapus ${round.size}") { _, _ -> startDelete(round) }
            .show()
            .window?.decorView?.filterTouchesWhenObscured = true
    }

    private fun startDelete(round: List<ContactItem>) {
        if (busy) return
        cancelled = false
        setBusy(true, deleting = true)
        status.text = "Menyimpan backup..."
        executor.execute {
            val backup = try {
                backupToDownloads(round) { done, total ->
                    ui { status.text = "Menyimpan backup... $done dari $total" }
                }
            } catch (e: Exception) {
                null
            }

            if (backup == null || backup.saved.isEmpty()) {
                ui {
                    setBusy(false)
                    status.text = "Backup gagal, penghapusan dibatalkan. Tidak ada kontak yang dihapus."
                }
                return@execute
            }

            if (cancelled) {
                ui {
                    setBusy(false)
                    status.text = "Dibatalkan. Tidak ada kontak yang dihapus."
                }
                return@execute
            }

            // Hanya kontak yang berhasil masuk backup yang boleh dihapus.
            val skipped = round.size - backup.saved.size
            val report = deleteInBatches(backup.saved) { done, total ->
                ui { status.text = "Menghapus $done dari $total... (jangan tutup aplikasi)" }
            }
            ui { finishDelete(report, backup.fileName, skipped) }
        }
    }

    private fun contactUri(id: Long): Uri =
        ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id)

    /**
     * Menghapus per kelompok kecil dengan jeda, supaya sistem kontak dan sinkronisasi
     * tidak kewalahan. Jika satu kelompok ditolak, kontaknya dicoba satu per satu agar
     * penyebabnya jelas.
     */
    private fun deleteInBatches(
        round: List<ContactItem>,
        onProgress: (Int, Int) -> Unit
    ): DeleteReport {
        val deleted = mutableListOf<ContactItem>()
        val failed = mutableListOf<Pair<ContactItem, String>>()
        var processed = 0
        var sinceCooldown = 0

        for (chunk in round.chunked(BATCH_SIZE)) {
            if (cancelled) break

            val ops = ArrayList<ContentProviderOperation>()
            for (item in chunk) {
                ops.add(ContentProviderOperation.newDelete(contactUri(item.id)).build())
            }

            var batchOk = false
            try {
                val results = contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                for (i in chunk.indices) {
                    val count = results.getOrNull(i)?.count ?: 0
                    if (count > 0) {
                        deleted.add(chunk[i])
                    } else {
                        failed.add(chunk[i] to "ditolak sistem (akun mungkin hanya-baca)")
                    }
                }
                batchOk = true
            } catch (e: Exception) {
                // Satu kelompok gagal: coba satu per satu di bawah.
            }

            if (!batchOk) {
                for (item in chunk) {
                    if (cancelled) break
                    try {
                        val n = contentResolver.delete(contactUri(item.id), null, null)
                        if (n > 0) {
                            deleted.add(item)
                        } else {
                            failed.add(item to "ditolak sistem (akun mungkin hanya-baca)")
                        }
                    } catch (e: Exception) {
                        failed.add(item to (e.message ?: e.javaClass.simpleName))
                    }
                    Thread.sleep(INDIVIDUAL_PAUSE_MS)
                }
            }

            processed += chunk.size
            onProgress(processed, round.size)
            Thread.sleep(BATCH_PAUSE_MS)

            sinceCooldown += chunk.size
            if (sinceCooldown >= COOLDOWN_EVERY) {
                sinceCooldown = 0
                // Istirahat singkat supaya sinkronisasi kontak sempat menyusul.
                var waited = 0L
                while (waited < COOLDOWN_MS && !cancelled) {
                    Thread.sleep(500)
                    waited += 500
                }
            }
        }
        return DeleteReport(deleted, failed, cancelled)
    }

    private fun finishDelete(report: DeleteReport, backupName: String, skipped: Int) {
        val deletedIds = report.deleted.map { it.id }.toSet()
        // Perbarui daftar dari memori; tidak memindai ulang agar tidak membebani sistem.
        val remaining = items.filter { it.id !in deletedIds }
        showItems(remaining)
        setBusy(false)

        status.text = buildString {
            append("Selesai: ${report.deleted.size} kontak dihapus, ${report.failed.size} gagal")
            if (skipped > 0) append(", $skipped dilewati karena gagal masuk backup")
            if (report.cancelled) append(" (dibatalkan)")
            append(".\nBackup: Download/$backupName")
            append("\n(Isinya data kontak tanpa enkripsi; hapus file ini setelah yakin semuanya aman.)")
            append("\nTekan \"Pindai kontak\" untuk menyegarkan daftar.")
            append("\nDi WhatsApp, buka menu titik tiga lalu \"Segarkan\" agar daftar kontak di WhatsApp ikut diperbarui.")
        }

        if (report.failed.isNotEmpty()) {
            val detail = report.failed.take(15).joinToString("\n\n") { (item, reason) ->
                "${item.name}\nAkun: ${item.accounts}\nAlasan: $reason"
            }
            val more = if (report.failed.size > 15) "\n\n...dan ${report.failed.size - 15} lainnya." else ""
            AlertDialog.Builder(this)
                .setTitle("${report.failed.size} kontak tidak bisa dihapus")
                .setMessage(detail + more)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    /** Menyimpan kontak yang akan dihapus ke satu file .vcf di folder Download. Mengembalikan nama file, atau null jika gagal. */
    private fun backupToDownloads(
        selected: List<ContactItem>,
        onProgress: (Int, Int) -> Unit
    ): BackupResult? {
        val fileName = "backup_kontak_${System.currentTimeMillis()}.vcf"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/x-vcard")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val target = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return null

        val saved = mutableListOf<ContactItem>()
        val stream = contentResolver.openOutputStream(target)
        if (stream == null) {
            contentResolver.delete(target, null, null)
            return null
        }
        stream.use { out ->
            for ((index, item) in selected.withIndex()) {
                if (cancelled) break
                try {
                    val vcardUri = Uri.withAppendedPath(
                        ContactsContract.Contacts.CONTENT_VCARD_URI,
                        item.lookupKey
                    )
                    val input = contentResolver.openInputStream(vcardUri)
                    if (input != null) {
                        input.use { it.copyTo(out) }
                        out.write("\r\n".toByteArray())
                        saved.add(item)
                    }
                } catch (e: Exception) {
                    // Kontak ini tidak masuk backup, jadi tidak akan dihapus.
                }
                if ((index + 1) % 25 == 0) onProgress(index + 1, selected.size)
            }
        }

        if (saved.isEmpty()) {
            contentResolver.delete(target, null, null)
            return null
        }
        return BackupResult(fileName, saved)
    }

    companion object {
        private const val REQ_PERMS = 100
        private const val LARGE_DELETE_THRESHOLD = 200
        private const val COOLDOWN_EVERY = 200
        private const val COOLDOWN_MS = 3000L
        private const val BATCH_SIZE = 20
        private const val BATCH_PAUSE_MS = 600L
        private const val INDIVIDUAL_PAUSE_MS = 200L
        private const val MIN_BATTERY_PERCENT = 20
    }
}
