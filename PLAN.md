# notnote — Proje Planı

> Durum: **Faz 1–4 tamamlandı** (sürüm 1.3, `apk/notnote.apk`) · Son güncelleme: 2026-11-16
>
> 1.1 ile gelenler: Tiny Notes tarzı not listesi widget'ı, sesle yazma (🎤), başka uygulamalardan
> "Paylaş → notnote", uzun basma menüsü (üste sabitle / kopyala / paylaş / sil), menüden arama,
> bildirim panelinde "Yeni not" kutucuğu.
>
> Yeniden derlemek için: `gradlew.bat assembleRelease` (JAVA_HOME = `D:\Android Studio\jbr`).
> Emülatörde test edildi: notlar, kaydırma, @ önerileri, alarm, hatırlatıcı, SMS, @aç, izinler ekranı.
> **Gerçek telefonda henüz test edilmedi:** WhatsApp tam otomatik gönderim (emülatörde WhatsApp yok), rehberden kişi seçme, widget.
> Hedef cihaz: **Samsung Galaxy S25** (One UI)

## 1. Özet

**notnote**, Android için, **kişisel kullanım** amaçlı (Play Store'a çıkmayacak, APK olarak telefona yüklenecek) çok sade bir not uygulaması.
Arayüz "Tiny Notes" tarzında olacak: tek liste, altta "Aklına geleni yaz…" kutusu.

Farkı şu: yazdığın satır `@` ile başlarsa not olarak kalmaz, **komut olarak çalışır**:

```
@whatsapp Ahmet: yarın 5'te geliyorum   → Ahmet'e WhatsApp mesajı
@sms Annem: eve geçtim                   → SMS gönderir
@ara Refika                              → Refika'yı arar
@alarm 07:30                             → alarm kurar
@hatırlat 18:00 ilacı iç                 → saatinde bildirim gösterir
@spotify lofi                            → Spotify'da arar / çalar
@youtube kedi videoları                  → YouTube'da arar
@harita Kadıköy                          → yol tarifi açar
@aç instagram                            → uygulamayı açar
```

Kodun tamamını Claude yazacak. Kullanıcının programlama bilmesi gerekmiyor.

## 2. Arayüz (ekran görüntüsündeki gibi)

- **Koyu tema**, sade yazı tipi, notlar arasında ince çizgiler
- **Üstte:** solda menü butonu, ortada "notnote" başlığı, sağda yapılacaklar ikonu (sadece görüntü, bir işlevi yok)
- **Menü → İzinler ekranı:** her izin için bir satır (✓ verildi / ✗ verilmedi) ve dokununca ilgili Android ayarına giden kısayol:
  - Rehber (kişi bulma)
  - SMS gönderme
  - Telefon araması
  - Bildirimler (`@hatırlat` için)
  - Tam zamanlı alarm (`@hatırlat` / zamanlanmış mesaj için)
  - **Erişilebilirlik servisi** (WhatsApp tam otomatik gönderim için)
  - Pil optimizasyonundan muafiyet (arka planda öldürülmemek için)
- **Ortada:** not listesi (en yeni üstte)
- **Altta:** yuvarlak köşeli yazı kutusu ve gönder (↑) butonu
- **Etkileşimler:**
  - Nota dokun → düzenle
  - Sola kaydır → sil (geri al seçeneğiyle)
  - ~~Sağa kaydır → tamamlandı~~ (kaldırıldı, 2026-11-04)
- **`@` yazınca:** kutunun üstünde bir öneri şeridi açılır (WhatsApp, SMS, Ara, Alarm…). Bir uygulama seçilince kişi önerileri gelir. Yani komutları ezberlemek gerekmez.
- Çalışan komutlar listede küçük bir uygulama ikonuyla ve "✓ gönderildi" ya da "✗ hata" etiketiyle görünür. Böylece geçmiş kaybolmaz.

## 3. WhatsApp'a mesaj gönderme: **tam otomatik** ✅ (karar verildi)

Akış:
1. `@whatsapp Ahmet: mesaj` yazılır, ↑'ye basılır
2. Rehberden Ahmet'in numarası bulunur. Aynı isimde birden fazla kişi varsa uygulama hangisi olduğunu sorar
3. WhatsApp, Ahmet'in sohbetinde mesaj yazılı halde açılır
4. notnote'un **Erişilebilirlik servisi** WhatsApp'taki Gönder tuşunu bulup basar
5. notnote'a otomatik geri dönülür ve not "✓ gönderildi" olarak işaretlenir

Güvenlik önlemleri:
- Erişilebilirlik servisi **sadece WhatsApp ekranını** dinler, sadece notnote'un başlattığı gönderimde ve sadece birkaç saniyeliğine devreye girer
- Gönder tuşu birkaç saniye içinde bulunamazsa hiçbir şey yapılmaz, not "✗ gönderilemedi" olarak işaretlenir ve mesaj WhatsApp'ta yazılı halde bekler
- Erişilebilirlik izni kapalıysa uygulama otomatik olarak yarı otomatik yönteme düşer: mesaj yazılı açılır, Gönder'e sen basarsın

Bilinen sınırlar: telefon kilitliyken çalışmaz (zamanlanmış mesajlarda sorun olabilir, Faz 4'te ele alınacak). WhatsApp büyük bir arayüz değişikliği yaparsa Gönder tuşunu bulma kısmının güncellenmesi gerekebilir.

SMS için ek bir şeye gerek yok: Android doğrudan ve tamamen otomatik gönderebiliyor.

## 4. Teknik seçimler

| Konu | Seçim | Neden |
|---|---|---|
| Dil | Kotlin | Android'in resmi dili, telefon kontrolü için gereken her API'ye doğrudan erişim |
| Arayüz | Jetpack Compose (Material 3) | Modern, sade arayüzleri hızlı yapmayı sağlıyor |
| Veritabanı | Room (SQLite) | Notlar telefonda, çevrimdışı saklanır |
| Komut motoru | Kendi yazacağımız ayrıştırıcı + "Action" sınıfları | Her `@komut` ayrı bir modül, yenisini eklemek kolay |
| Kişiler | Android Rehber API'si | İsimden telefon numarası bulmak için |
| Zamanlama | AlarmManager / WorkManager | `@hatırlat` ve zamanlanmış mesajlar için |
| Sürüm takibi | Git | Her adım kaydedilir, bir şey bozulursa geri dönülebilir |

### Komut motoru tasarımı
```
Yazılan satır → Ayrıştırıcı → Command(app="whatsapp", target="Ahmet", text="...")
                                   ↓
                         Action (WhatsAppAction, SmsAction, AlarmAction ...)
                                   ↓
                         Sonuç → listede not olarak kaydedilir (✓ / ✗)
```
Yeni bir uygulama eklemek = tek bir yeni Action dosyası.

## 5. Aşamalar

| Faz | İçerik | Bitince elinde ne olacak |
|---|---|---|
| **0 – Kurulum** | Bilgisayara Android Studio kurulumu, telefonda USB/Kablosuz hata ayıklama, boş uygulamayı telefona yükleme | Telefonda açılan "merhaba" uygulaması |
| **1 – Not uygulaması** | Görüntüdeki arayüz, not ekleme/düzenleme/silme, kaydırma hareketleri, tamamlandı işareti | Günlük kullanılabilir not uygulaması |
| **2 – @ altyapısı + WhatsApp** | Öneri şeridi, kişi seçimi, Menü → İzinler ekranı, `@whatsapp` (**tam otomatik**), `@sms`, `@ara` | İlk "notlar iş yapıyor" deneyimi |
| **3 – Diğer komutlar** | `@alarm`, `@hatırlat`, `@aç`, `@spotify`, `@youtube`, `@harita`, `@telegram` | Asistan gibi kullanım |
| **4 – Ekstralar** | Zamanlanmış mesaj (`@whatsapp 21:00 Ahmet: ...`), ana ekran widget'ı, yedek alma/geri yükleme | Güçlü sürüm |
| **5 – İleride (fikir)** | Sesle yazma (Wispr Flow benzeri), doğal dil ("Ahmet'e yarın geleceğimi yaz") için AI desteği | — |

## 6. Kullanıcının yapması gereken (tek seferlik)

**Bilgisayarda:**
1. Android Studio'yu kurmak (adım adım anlatılacak)

**Galaxy S25'te:**
1. **Otomatik Engelleyici'yi kapatmak:** Ayarlar → Güvenlik ve gizlilik → Otomatik Engelleyici → Kapalı. Açıkken Samsung, Play Store dışından gelen uygulamaları ve USB komutlarını engelliyor.
2. **Geliştirici seçeneklerini açmak:** Ayarlar → Telefon hakkında → Yazılım bilgileri → "Yapım numarası"na 7 kez dokunmak
3. **USB hata ayıklama**'yı açmak: Ayarlar → Geliştirici seçenekleri → USB hata ayıklama. Alternatif olarak kablosuz hata ayıklama da kullanılabilir.
4. **Kısıtlanmış ayarlara izin vermek:** Play Store dışından yüklenen uygulamalarda Android, Erişilebilirlik iznini engelliyor. Ayarlar → Uygulamalar → notnote → sağ üstteki ⋮ → "Kısıtlanmış ayarlara izin ver". Bundan sonra Erişilebilirlik açılabiliyor.
5. **Pil:** Ayarlar → Pil → Arka plan kullanım sınırları → "Hiç uyku moduna geçmeyen uygulamalar" listesine notnote'u eklemek. Bu, hatırlatıcıların kaçmaması için gerekli.
6. Uygulama içinde **Menü → İzinler** ekranındaki izinleri tek tek açmak

Kod yazma, derleme ve telefona yükleme kısmını Claude yapacak.

## 7. Riskler ve notlar

- **Samsung pil tasarrufu** arka plandaki hatırlatıcıları durdurabilir. Bu yüzden notnote "Hiç uyku moduna geçmeyen uygulamalar" listesine eklenmeli (bkz. bölüm 6).
- **Tam otomatik WhatsApp** resmi bir yöntem değil. WhatsApp güncellemelerinde ayar gerekebilir.
- **Yanlış kişiye mesaj riski:** aynı isimde birden fazla kişi varsa uygulama sorar. İstenirse göndermeden önce kısa bir onay gösterilir.
- Tüm veriler **sadece telefonda** kalır. İnternete hiçbir şey gönderilmez (AI özelliği eklenene kadar).

## 8. Kararlar

- [x] WhatsApp → **tam otomatik** (Erişilebilirlik ile; izin yoksa yarı otomatiğe düşer)
- [x] Sağ üst ikon → **işlevsiz**, sadece görüntü
- [x] Sol menü → **İzinler** ekranı
- [x] Uygulama adı → **notnote**
- [x] Telefon → **Samsung Galaxy S25**
- [ ] `@` komut listesi: şimdilik bölüm 1'deki liste geçerli, istenirse değiştirilir
