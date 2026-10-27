# Image Vectorizer — Android Batch Image-to-Vector App

Aplikasi Android modern untuk mengubah gambar raster (JPG, PNG, WebP) menjadi vektor SVG (*Scalable Vector Graphics*) secara *offline* di perangkat dengan kemampuan pemrosesan batch dan pencegahan kurva berbelit (*anti-mbulet*).

---

## Fitur Utama

1. **Pemrosesan Batch Cepat & Aman**:
   * Pilih puluhan gambar sekaligus menggunakan Android Photo Picker modern.
   * Dibatasi maksimal 2 pemrosesan paralel (Concurrency Semaphore) untuk mencegah panas berlebih dan crash memori (Out-Of-Memory).
   * **Adaptive Downscaler**: Otomatis menurunkan resolusi gambar kamera raksasa (misal 48 MP) ke batas aman (maks 1280px) tanpa mengurangi kualitas vektor.

2. **5 Mode Vektorisasi Khusus (Anti Garis Mbulet)**:
   * **Logo & Clipart**: Mengurangi noise kompresi JPEG, menghaluskan kurva Bezier, dan meratakan bidang warna.
   * **Line Art & Sketsa**: Menggunakan binarisasi Otsu adaptif dan otomatis membuang latar putih. **Mencegah garis ganda berlubang (*double-outline*)** pada gambar pensil/pena.
   * **Foto / Ilustrasi Warna**: Multi-layer clustering (sampai 24 warna) untuk menangkap nuansa dan gradasi warna kaya.
   * **Pixel Art / Retro**: Tracing siku-siku 90 derajat tanpa kurva lengkung agar sprite game retro tetap kotak dan tajam.
   * **Siluet & Monokrom**: Binarisasi 1-bit dengan pembersihan bintik kotoran agresif untuk sablon, stensil, dan cap/stempel.

3. **Interactive Before/After Split Comparison Slider**:
   * Geser garis pemisah secara interaktif untuk membandingkan piksel raster asli vs kurva vektor hasil tracing secara langsung.
   * Ganti-ganti mode secara instan untuk melihat mode mana yang paling pas untuk gambar tertentu.

4. **Ekspor & Scoped Storage**:
   * Simpan file `.svg` langsung ke folder `Downloads/ImageVectorizer` publik perangkat menggunakan Android MediaStore API (kompatibel Android 10 sampai Android 14+).

---

## Struktur Kode

```
android-vectorizer/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/vectorizer/app/
│   │   │   ├── MainActivity.kt               # Entry point & Navigasi
│   │   │   ├── core/
│   │   │   │   ├── model/
│   │   │   │   │   ├── ImageData.kt          # Buffer RGBA platform-agnostic
│   │   │   │   │   ├── VectorPreset.kt       # 5 mode preset
│   │   │   │   │   ├── VectorizerOptions.kt  # Konfigurasi parameter
│   │   │   │   │   └── VectorResult.kt       # Data hasil tracing & statistik
│   │   │   │   ├── preprocess/
│   │   │   │   │   └── ImagePreprocessor.kt  # Downscaler OOM, Blur, Otsu Binarize
│   │   │   │   ├── engine/
│   │   │   │   │   └── ImageTracerEngine.kt  # Engine tracing SVG & Bezier curves
│   │   │   │   └── adapter/
│   │   │   │       └── AndroidBitmapAdapter.kt # Bridge Bitmap/Uri & MediaStore
│   │   │   ├── domain/
│   │   │   │   ├── BatchItem.kt              # Model antrean item
│   │   │   │   └── BatchQueueManager.kt      # Kontroler antrean batch (Coroutines)
│   │   │   └── ui/
│   │   │       ├── theme/                    # Warna & Material 3 Dark Theme
│   │   │       ├── components/
│   │   │       │   └── BeforeAfterSlider.kt  # Slider perbandingan interaktif
│   │   │       └── screens/
│   │   │           ├── HomeScreen.kt         # Picker, pilihan mode, antrean batch
│   │   │           └── CompareScreen.kt      # Halaman detail perbandingan & switcher
│   │   └── res/
│   │       └── values/strings.xml, themes.xml
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```
