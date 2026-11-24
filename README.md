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
