# WA Contact Cleaner

Aplikasi Android (Kotlin) untuk menemukan kontak yang tidak terhubung dengan WhatsApp,
lalu menghapusnya setelah kamu menyetujui.

## Cara kerja

WhatsApp membuat entri kontak khusus (account type `com.whatsapp`, atau `com.whatsapp.w4b`
untuk WhatsApp Business) untuk setiap kontak yang terdaftar di WhatsApp. Aplikasi ini membaca
daftar kontak, membuang yang punya entri tersebut, dan menampilkan sisanya.

Semua proses berlangsung di ponselmu. Aplikasi tidak login ke WhatsApp, tidak memakai internet,
dan tidak memakai library tidak resmi, jadi tidak ada risiko akun WhatsApp diblokir.

## Cara membuka dan menjalankan

1. Buka folder `WaCleaner` di Android Studio (versi terbaru) lalu tunggu Gradle sync selesai.
2. Colok ponsel Android (aktifkan USB debugging) lalu tekan Run, atau pilih
   Build > Build APK(s) untuk memasang APK secara manual.
3. Di aplikasi: tekan **Pindai kontak**, beri izin kontak, centang kontak yang ingin dihapus,
   lalu tekan **Hapus**.

## Pengaman

- Penghapusan berjalan per kelompok kecil (20 kontak) dengan jeda, maksimal 100 kontak per
  putaran, supaya sistem kontak dan sinkronisasi tidak kewalahan. Bisa dibatalkan kapan saja.
- Layar dijaga tetap menyala selama penghapusan, dan penghapusan ditolak jika baterai di bawah 20%
  dan tidak sedang diisi.
- Kontak berbintang (favorit) ditandai ★ dan tidak ikut dipilih oleh "Pilih semua".
- Jika hampir semua kontak terdeteksi tidak terhubung (kemungkinan sinkronisasi WhatsApp belum
  selesai), "Pilih semua" dimatikan dan muncul peringatan.
- Setiap kontak menampilkan akunnya (Google, Lokal, SIM, dst.). Kontak yang gagal dihapus
  dilaporkan lengkap dengan akun dan alasannya.

- Sebelum menghapus, aplikasi menyimpan backup `.vcf` ke folder **Download**. Jika backup gagal,
  tidak ada kontak yang dihapus.
- Hanya kontak yang kamu centang yang dihapus. Tidak ada penghapusan otomatis.
- Jika tidak ada satu pun kontak WhatsApp yang terdeteksi (WhatsApp belum terpasang, belum login,
  atau sinkronisasi kontak mati), pemindaian dibatalkan agar semua kontak tidak ikut terdaftar
  sebagai "tidak terhubung".
- Kontak yang digabung dengan kontak WhatsApp (aggregated contact) dianggap terhubung dan tidak
  ditampilkan.

## Keamanan dan verifikasi

- **Tanpa internet.** Aplikasi hanya meminta izin baca dan tulis kontak. Setiap build di GitHub
  diperiksa otomatis: jika ada izin lain di APK (termasuk internet), build gagal dan tidak
  dirilis. Build juga gagal jika APK masih berstatus debug.
- **Tanpa library pihak ketiga.** Tidak ada blok `dependencies` di `app/build.gradle.kts`,
  sehingga tidak ada kode luar yang bisa membawa celah.
- **Build rilis**, bukan debug, dengan R8 (pengecilan kode) menyala dan `allowBackup` mati.
- **Anti-tapjacking.** Layar utama dan dialog konfirmasi mengabaikan sentuhan jika ada
  aplikasi lain yang menimpa layar.
- **Menu "Info keamanan"** di aplikasi membaca langsung izin, status debug, dan SHA-256
  sertifikat penandatangan. Bandingkan dengan nilai di catatan rilis GitHub; jika berbeda,
  jangan pakai APK itu.
- **Unduh hanya dari halaman Releases repository ini.** Jangan pasang salinan APK dari sumber lain.
- Pipeline build memakai tanda tangan dari secret `KEYSTORE_B64`, `KEYSTORE_PASSWORD`,
  `KEY_PASSWORD`, `KEY_ALIAS` jika diisi pemilik repo (sertifikat tetap di setiap versi). Jika
  belum diisi, dibuat kunci sementara per build; tiap versi baru harus mencopot yang lama dulu.
  Kunci hanya ada selama langkah build dan dihapus sesudahnya.

## Batasan

- Kontak di akun yang bersifat read-only (misalnya sebagian kontak SIM) mungkin gagal dihapus.
- Nomor telepon rumah, kantor, atau orang yang memang tidak memakai WhatsApp akan muncul di daftar.
  Periksa daftar sebelum mencentang.
- Untuk memulihkan kontak, buka file `.vcf` di folder Download lewat aplikasi Kontak
  (Impor / Import).
- minSdk 29 (Android 10).
