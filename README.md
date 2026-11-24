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

## Batasan

- Kontak di akun yang bersifat read-only (misalnya sebagian kontak SIM) mungkin gagal dihapus.
- Nomor telepon rumah, kantor, atau orang yang memang tidak memakai WhatsApp akan muncul di daftar.
  Periksa daftar sebelum mencentang.
- Untuk memulihkan kontak, buka file `.vcf` di folder Download lewat aplikasi Kontak
  (Impor / Import).
- minSdk 29 (Android 10).
