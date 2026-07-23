# Raspberry Pi kurulumu

## 1. Dosyaları kopyala

Bu `rasp` klasörünü Pi'ye kopyalayın (örnek: `/home/pi/bbpi`):

```bash
scp -r rasp/ pi@<PI_IP>:/home/pi/bbpi
```

## 2. Ortamı kur (Pi üzerinde)

```bash
cd /home/pi/bbpi
python3 -m venv venv
venv/bin/pip install -r requirements.txt
```

## 3. Test çalıştırması

```bash
venv/bin/python papertrade.py --once
```

`15m: nakit ...$ | 4h: nakit 1000.00$ | 1h: nakit 1000.00$` benzeri bir satır
görmelisiniz. Bot ilk turda parquet'teki son bardan bugüne kadarki eksik
barları Binance'ten tamamlar; kopyalama ile kurulum arasında zaman geçtiyse
ilk tur birkaç dakika sürebilir, normaldir.

`data/closes_15m.parquet` repoda zaten varsa (Windows'ta bir kere bootstrap
edildiyse ve `scp -r` ile kopyalandıysa) bu adım hızlı geçer. Yoksa bot ilk
turda 31 coin için ~125 günlük 15 dakikalık geçmişi kendi çeker; bu ~5-10
dakika sürer, sadece ilk kurulumda olur.

## 3b. Dashboard'u telefon/başka bir cihazdan izleme

`papertrade.py` artık ayrı bir sunucuya gerek kalmadan kendi içinde
`0.0.0.0:8000` üzerinde bir web sunucusu açıyor. Pi'nin yerel ağ IP'sini
öğrenip (`hostname -I`) aynı WiFi'deki herhangi bir cihazdan tarayıcıyla:

```
http://<pi-ip>:8000/trading-dashboard.html
```

adresine gidince canlı grafiği görürsünüz. Ev ağının dışından (mobil veri
üzerinden) erişmek isterseniz Tailscale gibi bir overlay ağ kurmanız gerekir
— port'u doğrudan internete yönlendirmeyin, kimlik doğrulaması yoktur.

## 4. Açılışta otomatik başlat (systemd)

`bbpi-paper.service` içindeki `User`, `WorkingDirectory` ve `ExecStart`
yollarını kendi kullanıcı adınıza göre düzeltin (varsayılan: `pi` /
`/home/pi/bbpi`), sonra:

```bash
sudo cp bbpi-paper.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now bbpi-paper
```

Kontrol:

```bash
systemctl status bbpi-paper          # servis durumu
tail -f /home/pi/bbpi/data/paper_log.txt   # bot günlüğü
```

## Dosyalar

| Dosya | Görev |
|---|---|
| `papertrade.py` | Bot (15m + 4h + 1h motor, 1000'er $ sanal bakiye, gerçek emir YOK) + gömülü dashboard sunucusu (:8000) |
| `bbpi.py` | BBPI göstergesi (31 coin PnF) ve Stoch RSI hesabı |
| `trading-dashboard.html` | Canlı fiyat/BBPI/Stoch RSI grafiği (tarayıcıdan izlenir) |
| `data/closes_15m.parquet` | 15m kapanış arşivi (bot kendisi günceller) |
| `data/closes_4h.parquet` | 4h kapanış arşivi (bot kendisi günceller) |
| `data/closes_1h.parquet` | 1h kapanış arşivi (bot kendisi günceller) |
| `data/dashboard.json` | Dashboard'un okuduğu canlı anlık görüntü (bot her ~8sn günceller) |
| `data/paper_state_*.json` | Sanal bakiye + açık pozisyon (bot oluşturur) |
| `data/paper_trades_*.csv` | İşlem geçmişi (bot oluşturur) |
| `data/paper_log.txt` | Günlük (bot oluşturur) |

## 5. Dışarıdan erişim (Render "vitrin" servisi)

Bot evdeki Linux cihazında kalır (state/parquet orada tutulur); Render sadece
evden gelen son anlık görüntüyü bellekte tutup dışarıya (ESP32, telefon)
sunan hafif bir "vitrin". Kod `render-relay/` klasöründe.

**Render tarafı (bir kere):**
1. Bu repoyu GitHub'a push'la (Render git'ten deploy eder).
2. Render'da "New Web Service" → repo'yu seç → **Root Directory**: `render-relay`
3. Build command: `pip install -r requirements.txt`
4. Start command: `gunicorn app:app --bind 0.0.0.0:$PORT`
5. Environment → `RELAY_API_KEY` değişkenini ekle (güçlü rastgele bir değer,
   ör. `openssl rand -hex 32` ile üretilebilir — bunu ev tarafındaki .env
   ile BİREBİR aynı yap).
6. Settings → Custom Domain'e kendi domain'ini ekle, Render'ın verdiği
   CNAME/A kaydını domain sağlayıcında tanımla (Render otomatik HTTPS sertifikası çıkarır).

**Ev tarafı (Pi/Linux cihazı):** `/home/pi/bbpi/.env` dosyası oluştur (bu
dosya asla git'e eklenmez):
```
RELAY_PUSH_URL=https://senin-domainin.com/push
RELAY_API_KEY=<Render'a girdiğin AYNI değer>
```
Sonra `sudo systemctl restart bbpi-paper`. Log'da push hatası görünmüyorsa
çalışıyordur; `https://senin-domainin.com/trading-dashboard.html` adresini
WiFi'den ÇIKIP mobil veriyle açarak dışarıdan gerçekten erişilebildiğini
doğrula.

**ESP32:** `GET https://senin-domainin.com/data/dashboard.json` — düz JSON
döner, API anahtarı gerekmez (okuma ucu bilerek açık bırakıldı, içerik
hassas değil). `/push` ucu ise `X-API-Key` header'ı olmadan 401 döner.

**Neden Render'ın ücretsiz katmanının uyku sorunu olmuyor:** ev sunucusu
her ~8 saniyede bir kendiliğinden `/push` isteği attığı için Render servisi
sürekli "istek alıyor" sayılır, 15 dakikalık boşta-uyuma limitine hiç girmez.

## Notlar

- Bot durup yeniden başlarsa kaldığı yerden devam eder; aradaki barları
  Binance'ten tamamlar. Elektrik kesintisi sorun değildir.
- API anahtarı GEREKMEZ — sadece halka açık fiyat verisi okunur.
- Saat dilimi: günlükteki saatler Türkiye saatidir (UTC+3); Pi'nin saati
  yanlışsa `sudo timedatectl set-timezone Europe/Istanbul` yapın ve NTP'nin
  açık olduğundan emin olun (`timedatectl` çıktısında "NTP service: active").
- Pi Zero/1 gibi çok zayıf modellerde 1h motorunun gösterge hesabı bar başına
  ~10-30 sn sürebilir; Pi 3 ve üzeri rahat kaldırır.
