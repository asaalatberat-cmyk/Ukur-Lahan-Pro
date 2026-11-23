# Ukur Lahan Pro – Aplikasi Android untuk Pekerjaan Tanah

Aplikasi Android (Kotlin + Jetpack Compose) yang menjadikan HP alat ukur lapangan:
waterpass, sipat datar, "total station" sederhana, dan pengukur luas lahan.

## Fitur

| Menu | Fungsi |
|---|---|
| 🎯 **Sipat Datar Kamera** | HP di tripod jadi waterpass. Kamera + benang silang + indikator datar (±0,05°). Bidik rambu di titik acuan → **KUNCI HI** (tinggi garis bidik). Di titik lain langsung muncul elevasi dan status **GALI / TIMBUN (cm)**. Ada **bacaan target rambu** untuk meratakan lahan, pindah alat (titik ikat), koreksi sudut otomatis, zoom, ekspor CSV. |
| 🫧 **Waterpass Digital** | Nivo gelembung 2 sumbu (HP rebah) atau tabung (HP berdiri di sisi). **Kunci titik** acuan → di titik lain tampil selisihnya. Mode jidar: beda tinggi ujung dalam mm. Bunyi bip saat datar. Kalibrasi putar 180°. |
| 🔭 **Total Station Kamera** | Azimut (kompas) + sudut vertikal + jarak (otomatis dari sudut tunduk atau dari meteran) → koordinat X/Y/Z tiap titik, peta, luas & keliling, tinggi objek. Ekspor CSV. |
| 🚶 **Luas Lahan Berjalan** | Berjalan menyusuri batas dengan kamera + GPS. Tandai sudut (rata-rata 5 detik) atau rekam otomatis tiap N meter. Luas m²/are/ha, keliling, panjang sisi, peta. Ekspor **CSV & KML (Google Earth)**. |
| ⛏️ **Galian & Timbunan** | Metode grid: volume gali/timbun, **elevasi seimbang** (gali = timbun), faktor kembang, ritase truk. Bisa mengambil elevasi langsung dari data Sipat Datar. |
| 📐 **Kemiringan** | Konversi %, derajat, 1:n; elevasi per stasiun untuk kemiringan rencana. |
| ⚙️ **Kalibrasi** | **Uji dua patok (two-peg test)** untuk koreksi garis bidik kamera, reset koreksi. |

Semua data tersimpan otomatis di HP dan bisa diekspor lewat tombol **Bagikan** (WhatsApp, Drive, Email, dll).

## Cara membuat APK

### Opsi A – Tanpa install apa pun (GitHub, gratis)
1. Buat repository baru di GitHub, upload seluruh isi folder ini.
2. Buka tab **Actions** → workflow **Build APK** jalan otomatis (±5–8 menit).
3. Setelah selesai (centang hijau), unduh artifact **UkurLahanPro-apk** → ekstrak → `app-debug.apk`.
4. Kirim APK ke HP, buka, izinkan "Instal dari sumber tidak dikenal".

### Opsi B – Android Studio
1. Install Android Studio (versi terbaru).
2. **File → Open** → pilih folder `UkurLahanPro`. Tunggu Gradle sync selesai.
3. Sambungkan HP (USB debugging aktif) → tekan ▶ **Run**, atau
   **Build → Build APK(s)** → file ada di `app/build/outputs/apk/debug/`.

Persyaratan: Android 8.0 (API 26) ke atas. Sensor akselerometer wajib; kompas, GPS, kamera dianjurkan.

## Cara kerja (ringkas)

**Meratakan lahan**
1. Pasang HP di tripod di tengah lahan. Lakukan Uji Dua Patok sekali.
2. Rambu di titik acuan (BM) → atur HP sampai indikator hijau → baca rambu → isi BS → **KUNCI HI**.
3. Isi elevasi rencana (atau ukur beberapa titik lalu pakai rata-rata).
4. Pindah rambu ke titik lain: pekerja gali/timbun sampai rambu terbaca **angka target**.

Rumus: `HI = elevasi acuan + BS`, `elevasi titik = HI − FS`, `koreksi = bacaan − jarak × tan(sudut)`.

## Akurasi yang realistis
- Sensor kemiringan HP setelah kalibrasi: ±0,05–0,1° → ±1–2 cm pada jarak 10–20 m. Jaga jarak bidik ≤ 20 m dan jarak BS ≈ FS.
- Jarak otomatis dari sudut tunduk: baik untuk ≤ 15 m di tanah relatif datar.
- GPS HP: ±3–5 m di tempat terbuka → cocok untuk lahan > 500 m².
- Untuk pekerjaan legal (sertifikat, batas resmi) tetap gunakan surveyor berlisensi.

## Struktur kode
```
app/src/main/java/com/ukurlahan/pro/
├─ MainActivity.kt          navigasi & tema
├─ sensor/Orientation.kt    sensor gravitasi + kompas, dirata-rata (anti getar)
├─ data/Store.kt            penyimpanan, ekspor/bagikan, kalibrasi
├─ data/Models.kt           model data + rumus luas (shoelace) & proyeksi GPS
└─ ui/                      layar-layar aplikasi
```
