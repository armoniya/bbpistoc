"""BBPI + Stoch RSI PAPER-TRADE botu (gerçek emir YOK, sanal bakiye).

Üç motor yan yana çalışır ve aynı kuralı uygular:
  - 15m motoru (backtest.py ile birebir aynı zaman dilimi) : 1000$ sanal bakiye
  - 4h motoru (backtestte kanıtlanan)   : 1000$ sanal bakiye
  - 1h motoru (karşılaştırma için)      : 1000$ sanal bakiye

KURAL (sadece long, STOP-LOSS YOK):
  Giriş: BBPI <= 35 iken Stoch %K, %D'yi yukarı keser (K[önceki] < 40)
         -> sinyal barı kapanınca piyasa fiyatından al (tüm bakiye)
         (btrend şartı YOK: btrend P&F teyidi 3 kutuluk hareket bekleyip geç
         onay verdiği için tam dip barında her zaman -1 kalıyor ve gerçek dip
         girişini kaçırıyordu; backtest.py ile aynı düzeltme buraya da uygulandı.)
  Çıkış: TP YOK (kâr tavanı kaldırıldı, kâr sınırsız koşabilir).
         Fiyat >= giriş fiyatı + komisyon (%0.2, breakeven, yani komisyon
         üstü kâr) VE BBPI >= 65 iken VE Stoch RSI %K, %D'yi aşağı keserse
         (K[önceki] >= D[önceki] ve K[şimdi] < D[şimdi]) bar kapanışında
         piyasa fiyatından sat.
         Komisyon üstü kâr yoksa hiçbir koşulda kapanış yapılmaz; pozisyon
         TUTULMAYA DEVAM EDER (zararına satılmaz).
  Komisyon: %0.1/yön (sanal bakiyeden düşülür)

Durum data/paper_state_<tf>.json dosyasında tutulur; bot yeniden
başlatıldığında kaldığı yerden devam eder. İşlemler data/paper_trades_<tf>.csv'ye yazılır.

Süreç ayrıca 0.0.0.0:8000 üzerinde küçük bir HTTP sunucusu açar (proje klasörünü
sunar); aynı ağdaki herhangi bir cihazdan (telefon dahil) tarayıcıyla
http://<bu-cihazin-ip'si>:8000/trading-dashboard.html adresine gidip canlı
grafiği izleyebilirsin. Ayrı bir "python -m http.server" çalıştırmana gerek yok.

Kullanım:
  bpi_env/bin/python papertrade.py          # sürekli çalışır (Ctrl+C ile durdur)
  bpi_env/bin/python papertrade.py --once   # tek kontrol turu (test için)
"""
import csv
import functools
import http.server
import json
import os
import socketserver
import sys
import threading
import time
from datetime import datetime, timezone, timedelta

import numpy as np
import pandas as pd
import requests
from binance.client import Client

from bbpi import BBPI_ExactMatch

TARGET_COIN = 'SOLUSDT'
DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'data')
DASHBOARD_PATH = os.path.join(DATA_DIR, 'dashboard.json')
DASHBOARD_PORT = 8000

# Render'daki "vitrin" servisine push (disaridan ESP32/telefon erisimi icin).
# Bos birakilirsa push denenmez, sadece yerel dashboard.json yazilir.
RELAY_PUSH_URL = os.environ.get('RELAY_PUSH_URL', '')   # ör: https://dashboard.senin-domainin.com/push
RELAY_API_KEY = os.environ.get('RELAY_API_KEY', '')
DASHBOARD_CANDLES = 60   # grafikte gösterilecek mum sayısı

FEE = 0.001
START_BALANCE = 1000.0
POLL_SEC = 8

BULL_ZONE = 35.0
K_MAX = 40.0
EXIT_ZONE = 65.0

TF_MS = {'15m': 900_000, '1h': 3_600_000, '4h': 14_400_000}
# Pine script'teki timeframe.period bazli percentage tablosu (15->0.10, 60->0.26, 240->0.66)
PCT_BY_TF = {'15m': 0.10, '1h': 0.26, '4h': 0.66}
BOOTSTRAP_DAYS = {'15m': 125, '1h': 500, '4h': 2000}   # ilk kurulumda geriye kac gun cekilecek
TR_TZ = timezone(timedelta(hours=3))


def now_tr() -> str:
    return datetime.now(TR_TZ).strftime('%Y-%m-%d %H:%M:%S')


def log(msg: str) -> None:
    line = f"[{now_tr()}] {msg}"
    print(line, flush=True)
    with open(os.path.join(DATA_DIR, 'paper_log.txt'), 'a') as f:
        f.write(line + '\n')


class _QuietHandler(http.server.SimpleHTTPRequestHandler):
    """Her fetch isteğini (1.5sn'de bir) log'a/konsola basmasin diye erisim
    loglarini bastiran SimpleHTTPRequestHandler."""
    def log_message(self, format: str, *args) -> None:
        pass


def start_dashboard_server(port: int = DASHBOARD_PORT) -> None:
    """Proje klasorunu 0.0.0.0:port'ta sunan bir arka plan thread'i baslatir.
    trading-dashboard.html ve data/dashboard.json ayni agdaki her cihazdan
    (telefon dahil) bu port uzerinden erisilebilir olur."""
    root = os.path.dirname(os.path.abspath(__file__))
    handler = functools.partial(_QuietHandler, directory=root)
    httpd = socketserver.ThreadingTCPServer(('0.0.0.0', port), handler)
    httpd.daemon_threads = True
    threading.Thread(target=httpd.serve_forever, daemon=True).start()
    log(f"Dashboard sunucusu basladi: http://0.0.0.0:{port}/trading-dashboard.html")


def bootstrap_closes(client: Client, calc: BBPI_ExactMatch, tf: str) -> pd.DataFrame:
    """closes_<tf>.parquet ilk kez olusturuluyorsa gostergenin ihtiyac duydugu
    derin gecmisi (tum coinler icin kapanis fiyati) Binance'ten ceker.
    Sadece kurulumda BIR KEZ calisir; sonrasinda sync_closes() artan guncelleme yapar."""
    days_back = BOOTSTRAP_DAYS[tf]
    start_str = f"{days_back} days ago UTC"
    log(f"[{tf}] closes_{tf}.parquet bulunamadi, ilk kurulum icin ~{days_back} "
        f"gunluk gecmis cekiliyor (birkac dakika surebilir)...")

    target_klines = client.get_historical_klines(TARGET_COIN, tf, start_str)
    target_times = pd.Index([int(k[0]) for k in target_klines])
    df = pd.DataFrame(index=target_times)
    df[TARGET_COIN] = [float(k[4]) for k in target_klines]
    df = df[~df.index.duplicated(keep='last')]

    symbols = [s for s in calc.coins if s != TARGET_COIN]
    for i, symbol in enumerate(symbols, 1):
        klines = client.get_historical_klines(symbol, tf, start_str)
        s = pd.Series([float(k[4]) for k in klines], index=[int(k[0]) for k in klines])
        s = s[~s.index.duplicated(keep='last')]
        df[symbol] = s.reindex(target_times).ffill().fillna(0.0)
        log(f"[{tf}] bootstrap {i}/{len(symbols)} {symbol} tamamlandi")

    return df


def fetch_ohlc(client: Client, symbol: str, tf: str, limit: int) -> list:
    """Grafik için OHLC mumları (indikatör hesabına dahil değil, sadece görsel)."""
    klines = client.get_klines(symbol=symbol, interval=tf, limit=limit)
    return [{'t': int(k[0]), 'o': float(k[1]), 'h': float(k[2]),
              'l': float(k[3]), 'c': float(k[4])} for k in klines]


def _series_tail(arr, n: int):
    if arr is None:
        return []
    return [None if np.isnan(v) else round(float(v), 2) for v in arr[-n:]]


def write_dashboard(engines: list, client: Client) -> None:
    """data/dashboard.json'a canlı fiyat + indikatör + pozisyon anlık görüntüsünü yazar.
    trading-dashboard.html bu dosyayı okuyup grafiği çizer."""
    payload = {'symbol': TARGET_COIN, 'updated': now_tr(), 'engines': {}}
    for eng in engines:
        try:
            candles = fetch_ohlc(client, TARGET_COIN, eng.tf, DASHBOARD_CANDLES)
        except Exception as exc:
            log(f"[{eng.tf}] dashboard mumları çekilemedi ({exc})")
            candles = []

        pos = eng.state['position']
        position = None
        if pos is not None and candles:
            last_price = candles[-1]['c']
            upnl = (pos['qty'] * last_price * (1 - FEE) / pos['cost'] - 1) * 100.0
            position = {
                'entry_price': pos['entry_price'],
                'entry_time': pos['entry_time'],
                'breakeven': pos['breakeven'],
                'upnl_pct': upnl,
            }

        payload['engines'][eng.tf] = {
            'balance': eng.state['balance'],
            'position': position,
            'bpi_series': _series_tail(eng.last_bpi, DASHBOARD_CANDLES),
            'k_series': _series_tail(eng.last_k, DASHBOARD_CANDLES),
            'd_series': _series_tail(eng.last_d, DASHBOARD_CANDLES),
            'candles': candles,
        }

    tmp_path = DASHBOARD_PATH + '.tmp'
    with open(tmp_path, 'w') as f:
        json.dump(payload, f)
    os.replace(tmp_path, DASHBOARD_PATH)

    if RELAY_PUSH_URL:
        try:
            requests.post(RELAY_PUSH_URL, json=payload,
                          headers={'X-API-Key': RELAY_API_KEY}, timeout=5)
        except Exception as exc:
            log(f"Render'a push basarisiz ({exc})")


class PaperEngine:
    def __init__(self, tf: str, client: Client):
        self.tf = tf
        self.client = client
        self.calc = BBPI_ExactMatch(target_coin=TARGET_COIN, timeframe=tf, percentage=PCT_BY_TF[tf])
        self.closes_path = os.path.join(DATA_DIR, f'closes_{tf}.parquet')
        self.state_path = os.path.join(DATA_DIR, f'paper_state_{tf}.json')
        self.trades_path = os.path.join(DATA_DIR, f'paper_trades_{tf}.csv')
        if not os.path.exists(self.closes_path):
            self.closes_df = bootstrap_closes(client, self.calc, tf)
            self.closes_df.to_parquet(self.closes_path)
        else:
            self.closes_df = pd.read_parquet(self.closes_path)
        self.state = self._load_state()
        self._ensure_trades_csv()
        self.last_bpi = self.last_k = self.last_d = None   # dashboard için önbellek

    # ---------- durum ----------
    def _load_state(self) -> dict:
        if os.path.exists(self.state_path):
            with open(self.state_path) as f:
                return json.load(f)
        return {'balance': START_BALANCE, 'position': None, 'last_signal_bar': 0}

    def _save_state(self) -> None:
        with open(self.state_path, 'w') as f:
            json.dump(self.state, f, indent=2)

    def _ensure_trades_csv(self) -> None:
        if not os.path.exists(self.trades_path):
            with open(self.trades_path, 'w', newline='') as f:
                csv.writer(f).writerow(
                    ['giris_zamani', 'cikis_zamani', 'giris', 'cikis',
                     'neden', 'net_pnl_pct', 'bakiye'])

    def _record_trade(self, exit_price: float, reason: str) -> None:
        pos = self.state['position']
        proceeds = pos['qty'] * exit_price * (1 - FEE)
        pnl_pct = (proceeds / pos['cost'] - 1) * 100.0
        self.state['balance'] = proceeds
        with open(self.trades_path, 'a', newline='') as f:
            csv.writer(f).writerow(
                [pos['entry_time'], now_tr(), f"{pos['entry_price']:.4f}",
                 f"{exit_price:.4f}", reason, f"{pnl_pct:+.2f}",
                 f"{proceeds:.2f}"])
        log(f"[{self.tf}] SATIŞ ({reason}) @ {exit_price:.2f} | net {pnl_pct:+.2f}% "
            f"| yeni bakiye {proceeds:.2f}$")
        self.state['position'] = None
        self._save_state()

    # ---------- veri ----------
    def sync_closes(self) -> bool:
        """Eksik KAPANMIŞ barları tüm semboller için tamamlar.
        Yeni bar eklendiyse True döner."""
        tf_ms = TF_MS[self.tf]
        now_ms = int(time.time() * 1000)
        last_open = int(self.closes_df.index[-1])
        first_missing = last_open + tf_ms
        if first_missing + tf_ms > now_ms:   # yeni kapanmış bar yok
            return False

        new_times = None
        new_cols = {}
        for symbol in self.closes_df.columns:
            try:
                klines = self.client.get_historical_klines(
                    symbol, self.tf, start_str=first_missing)
                closed = [k for k in klines if int(k[0]) + tf_ms <= now_ms]
                s = pd.Series([float(k[4]) for k in closed],
                              index=[int(k[0]) for k in closed])
                s = s[~s.index.duplicated(keep='last')]
            except Exception as exc:
                log(f"[{self.tf}] {symbol} çekilemedi ({exc}); son değer taşınacak")
                s = pd.Series(dtype=float)
            if symbol == TARGET_COIN:
                if s.empty:
                    return False
                new_times = s.index
            new_cols[symbol] = s

        add = pd.DataFrame(index=new_times)
        for symbol, s in new_cols.items():
            add[symbol] = s.reindex(new_times)
        add = add.ffill()
        # sembol hiç veri vermediyse önceki kapanışı taşı
        add = add.fillna(self.closes_df.iloc[-1])
        self.closes_df = pd.concat([self.closes_df, add])
        self.closes_df.to_parquet(self.closes_path)
        return True

    def indicators(self):
        n = len(self.closes_df)
        bull_sum = np.zeros(n)
        for ticker, tick in self.calc.coins.items():
            _, _, buyon = self.calc._calculate_pnf(
                self.closes_df[ticker].values, tick, is_bpi_layer=False)
            bull_sum += buyon
        bullrate = 100.0 * bull_sum / len(self.calc.coins)
        bpi, btrend, _ = self.calc._calculate_pnf(bullrate, tick=2.0, is_bpi_layer=True)
        k, d = self.calc.calculate_stoch_rsi_tv(self.closes_df[TARGET_COIN])
        return bpi, btrend, k.to_numpy(), d.to_numpy()

    # ---------- karar ----------
    def on_new_bar(self, price: float) -> None:
        bpi, btrend, k, d = self.indicators()
        self.last_bpi, self.last_k, self.last_d = bpi, k, d
        i = len(self.closes_df) - 1          # son KAPANMIŞ bar
        bar_ms = int(self.closes_df.index[-1])
        bar_str = datetime.fromtimestamp(bar_ms / 1000, TR_TZ).strftime('%d.%m %H:%M')
        log(f"[{self.tf}] bar {bar_str} | BBPI {bpi[i]:.0f} yön {int(btrend[i]):+d} "
            f"| K {k[i]:.1f} D {d[i]:.1f} | SOL {price:.2f}")

        pos = self.state['position']
        if pos is not None:
            if (price >= pos['breakeven'] and bpi[i] >= EXIT_ZONE
                    and not np.isnan(k[i - 1]) and not np.isnan(d[i - 1])
                    and k[i - 1] >= d[i - 1] and k[i] < d[i]):
                self._record_trade(price, 'exit_cross')
            return

        if self.state.get('last_signal_bar') == bar_ms:
            return                            # bu bar zaten işlendi
        if np.isnan(k[i]) or np.isnan(d[i]) or np.isnan(k[i - 1]) or np.isnan(d[i - 1]):
            return
        if (bpi[i] <= BULL_ZONE
                and k[i - 1] <= d[i - 1] and k[i] > d[i] and k[i - 1] < K_MAX):
            balance = self.state['balance']
            qty = balance / (price * (1 + FEE))
            self.state['position'] = {
                'entry_time': now_tr(), 'entry_price': price,
                'qty': qty, 'cost': balance,
                # breakeven: satista net gelirin maliyeti karsilamasi icin
                # gereken minimum fiyat (iki yonlu %0.1 komisyon dahil, ~%0.2 uzeri)
                'breakeven': price * (1 + FEE) / (1 - FEE),
            }
            self.state['last_signal_bar'] = bar_ms
            self._save_state()
            log(f"[{self.tf}] *** ALIŞ @ {price:.2f} | {balance:.2f}$ | "
                f"breakeven {self.state['position']['breakeven']:.2f} ***")

    def status(self, price: float) -> str:
        pos = self.state['position']
        if pos is None:
            return f"{self.tf}: nakit {self.state['balance']:.2f}$"
        upnl = (pos['qty'] * price * (1 - FEE) / pos['cost'] - 1) * 100.0
        return (f"{self.tf}: POZİSYONDA giriş {pos['entry_price']:.2f} "
                f"({upnl:+.2f}%) breakeven {pos['breakeven']:.2f}")


def main() -> None:
    once = '--once' in sys.argv
    start_dashboard_server()
    client = Client()
    engines = [PaperEngine('15m', client), PaperEngine('4h', client), PaperEngine('1h', client)]
    log("Paper-trade başladı. Motorlar: 15m (backtest ile birebir) + 4h + 1h "
        "(karşılaştırma). Gerçek emir gönderilmez.")

    # Acilista, yeni bar kapanmasini beklemeden mevcut son kapanmis barin
    # gosterge degerlerini hemen logla.
    start_price = float(client.get_symbol_ticker(symbol=TARGET_COIN)['price'])
    for eng in engines:
        eng.sync_closes()
        eng.on_new_bar(start_price)
    write_dashboard(engines, client)

    last_status = 0.0
    while True:
        try:
            price = float(client.get_symbol_ticker(symbol=TARGET_COIN)['price'])
            for eng in engines:
                if eng.sync_closes():
                    eng.on_new_bar(price)
            write_dashboard(engines, client)
            if time.time() - last_status > 1800 or once:
                log(' | '.join(e.status(price) for e in engines))
                last_status = time.time()
        except KeyboardInterrupt:
            log("Durduruldu.")
            return
        except Exception as exc:
            log(f"HATA: {exc} (60s sonra tekrar)")
            time.sleep(60)
            continue
        if once:
            return
        time.sleep(POLL_SEC)


if __name__ == '__main__':
    main()
