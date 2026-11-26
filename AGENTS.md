# Repository Guidelines & Behavioral Invariants: erlkim-net

## 1. MikroTik RouterOS v7 REST API Conventions
- **PATCH Payloads**: Jangan pernah menyertakan key dengan nilai string kosong (`""`) untuk field opsional pada request PATCH. Hanya sertakan field yang benar-benar diubah.
- **Command Endpoints**: Endpoint eksekusi aksi (seperti `/ip/dhcp-server/lease/make-static`) mewajibkan body `{"numbers": "<internal-id>"}`, bukan `.id`.
- **Hotspot User Profiles**:
  - `session-timeout` tidak boleh dikirim sebagai `""`; gunakan `"0s"` atau hilangkan key tersebut jika unlimited.
  - `shared-users`: Jika unlimited/default, gunakan `"1"` atau omit.
- **Wi-Fi Wave2 / WiFi v7 Interfaces**:
  - Untuk channel frequency otomatis pada `/rest/interface/wifi`, gunakan string kosong `""` pada `channel.frequency` (legacy `/interface/wireless` menggunakan `"auto"`).
- **FastTrack Invariant**:
  - Jangan mengaktifkan FastTrack IPv4 (`/ip settings set ipv4-fasttrack-active=yes` atau FastTrack firewall dummy rules) saat router menjalankan MikroTik Hotspot dengan rate-limit Simple Queue, karena FastTrack mem-bypass packet queue counters.
- **Log Clearing**:
  - Pembersihan log memori router dilakukan melalui `POST /system/logging/action/clear` dengan payload `{"numbers": "memory"}` (atau fallback toggle `memory-lines=1`).

## 2. Android Codebase & Build Invariants
- **Target APK Size Budget**:
  - Pertahankan ukuran rilis APK di bawah 3.0 MB.
  - Pertahankan `resConfigs "en", "id"` di `app/build.gradle.kts`.
  - Hindari penambahan library dependensi besar jika fungsionalitas dapat ditangani dengan library bawaan atau utility mandiri.
- **ProGuard / R8 Rules**:
  - Jangan gunakan wildcard terbuka pada library besar (misal: hindari `-keep class com.google.zxing.** { *; }`; cukup keep modul spesifik yang digunakan seperti `com.google.zxing.qrcode.**`).
  - Selalu pastikan model data DTO REST API teranotasi `@Keep` atau `@Serializable` agar tidak ter-obfuscate oleh R8.

## 3. Execution & Sandbox Guardrails
- **LAN Router Access (`10.10.10.1`)**: Perintah PowerShell / cURL yang melakukan query ke router LAN lokal harus dijalankan dengan `BypassSandbox: true` karena sandbox default tidak memiliki akses network.
- **GitHub Push**: Perintah `git push` harus dijalankan dengan `BypassSandbox: true` untuk resolusi DNS dan koneksi remote GitHub.
