import math
import time
import numpy as np
import pandas as pd
from binance.client import Client
import time

class BBPI_ExactMatch:
    def __init__(self, target_coin='SOLUSDT', timeframe='4h', percentage=0.66):
        self.target_coin = target_coin
        self.timeframe = timeframe
        self.percentage = percentage
        self.overbought, self.oversold, self.reversal = 70.0, 30.0, 3
        
        # Binance'te halen aktif işlem gören 31 coin.
        # Delist edilenler çıkarıldı: EOS, XMR, OMG, REP, WAVES, BTS, NANO
        self.coins = {
            'BTCUSDT': 0.01, 'XRPUSDT': 0.00001, 'BCHUSDT': 0.01, 'LTCUSDT': 0.01,
            'BNBUSDT': 0.00001, 'XTZUSDT': 0.00001, 'ETHUSDT': 0.01,
            'XLMUSDT': 0.00001, 'ADAUSDT': 0.00001, 'TRXUSDT': 0.00001,
            'DASHUSDT': 0.01, 'ETCUSDT': 0.00001, 'NEOUSDT': 0.001,
            'ATOMUSDT': 0.001, 'IOTAUSDT': 0.00001, 'ALGOUSDT': 0.00001, 'ONTUSDT': 0.00001,
            'FTTUSDT': 0.001, 'DOGEUSDT': 0.0000001, 'BATUSDT': 0.00001, 'ZECUSDT': 0.01,
            'VETUSDT': 0.000001, 'SCUSDT': 0.000001, 'ZILUSDT': 0.00001, 'QTUMUSDT': 0.001,
            'THETAUSDT': 0.00001, 'LSKUSDT': 0.00001, 'HOTUSDT': 0.0000001,
            'HBARUSDT': 0.00001, 'ICXUSDT': 0.00001, 'RVNUSDT': 0.00001
        }
        self.client = Client()
        self.closes = None          # sembol -> kapanış dizisi (bellekte tutulan tarihsel veri)
        self.last_bar_time = None   # son barın açılış zamanı (ms)

    def fetch_deep_data(self, days_back=750):
        """TradingView'in max_bars_back derinliğine ulaşmak için ~4500 bar geçmiş veri çeker.
        Sadece başlangıçta BİR KEZ çağrılır; sonraki güncellemeler refresh_latest() ile yapılır."""
        closes = {}
        start_str = f"{days_back} days ago UTC"
        
        print(f"[{time.strftime('%H:%M:%S')}] TradingView senkronizasyonu için ~2.5 yıllık veri çekiliyor...")
        
        # Önce hedef coini çekip referans zaman eksenini belirliyoruz
        target_klines = self.client.get_historical_klines(self.target_coin, self.timeframe, start_str)
        target_times = pd.Index([int(k[0]) for k in target_klines])
        target_len = len(target_times)

        all_symbols = list(set(list(self.coins.keys()) + [self.target_coin]))
        for idx, symbol in enumerate(all_symbols, 1):
            try:
                print(f"\rVeri çekiliyor: [{idx}/{len(all_symbols)}] {symbol}...", end="", flush=True)
                if symbol == self.target_coin:
                    klines = target_klines
                else:
                    # get_historical_klines arka planda sayfalama yaparak 1000 bar limitini aşar
                    klines = self.client.get_historical_klines(symbol, self.timeframe, start_str)

                # TradingView security() gibi ZAMAN DAMGASINA göre hizala:
                # eksik barlar son kapanışla doldurulur (gaps_off), listeleme öncesi 0
                series = pd.Series([float(k[4]) for k in klines], index=[int(k[0]) for k in klines])
                series = series[~series.index.duplicated(keep='last')]
                aligned = series.reindex(target_times).ffill().fillna(0.0)
                closes[symbol] = aligned.to_numpy(dtype=float, copy=True)
            except Exception:
                # Binance'ten silinmiş coinler (FTT, NANO vb.) TradingView gibi 0 ile doldurulur
                closes[symbol] = np.zeros(target_len)
                
        print("\nVeri çekimi tamamlandı. PnF algoritması çalıştırılıyor...")
        self.closes = closes
        self.last_bar_time = int(target_klines[-1][0])

    def refresh_latest(self):
        """Her döngüde tam geçmiş yerine sembol başına SADECE son barı (1 kline) çeker.
        Yeni bar açıldıysa diziye ekler, açılmadıysa son barın kapanışını günceller."""
        target_kline = self.client.get_klines(symbol=self.target_coin, interval=self.timeframe, limit=1)
        current_open = int(target_kline[0][0])
        is_new_bar = current_open > self.last_bar_time

        all_symbols = list(set(list(self.coins.keys()) + [self.target_coin]))
        for symbol in all_symbols:
            try:
                kline = self.client.get_klines(symbol=symbol, interval=self.timeframe, limit=1)
                close = float(kline[0][4])
            except Exception:
                close = 0.0
            if is_new_bar:
                self.closes[symbol] = np.append(self.closes[symbol], close)
            else:
                self.closes[symbol][-1] = close

        if is_new_bar:
            self.last_bar_time = current_open

    def _calculate_pnf(self, closes, tick, is_bpi_layer=False):
        n = len(closes)
        icloseprice, trend, buyon = np.zeros(n), np.zeros(n, dtype=int), np.zeros(n, dtype=int)
        # ext0 = son trend değişimindeki kolon ucu (Pine: valuewhen(chigh, chigh, 0))
        # ext1 = ondan bir önceki, yani AYNI yönlü önceki kolonun ucu (Pine: valuewhen(chigh, chigh, 1))
        # Double top/bottom kırılımı ext1'e karşı test edilir!
        curr_trend, beginprice, box, ext0, ext1, curr_buyon = 0, 0.0, 0.0, np.nan, np.nan, 0
        decimals = max(0, -int(math.floor(math.log10(tick)))) if tick < 1 else 0
        
        for i in range(n):
            cp = closes[i]
            if np.isnan(cp) or cp == 0:
                icloseprice[i] = np.nan if i == 0 else icloseprice[i-1]
                trend[i], buyon[i] = curr_trend, curr_buyon
                continue
                
            # Pine round() 0.5'i her zaman yukarı yuvarlar; Python round() ise çifte yuvarlar (banker's)
            factor = 10 ** decimals
            pboxsize = 2.0 if is_bpi_layer else max(math.floor(cp * self.percentage / 100.0 * factor + 0.5) / factor, tick)
            if i == 0 or box == 0.0 or np.isnan(box):
                box = pboxsize
                beginprice = math.floor(cp / 2.0) * 2.0 if is_bpi_layer else math.floor(cp / box) * box
            
            prev_iclose = icloseprice[i-1] if i > 0 else beginprice
            prev_trend = curr_trend
            
            if curr_trend == 0:
                if abs(beginprice - cp) >= box * self.reversal:
                    numcell = math.floor(abs(beginprice - cp) / box)
                    icloseprice[i], curr_trend = (beginprice - numcell * box, -1) if beginprice > cp else (beginprice + numcell * box, 1)
                    beginprice = icloseprice[i]
                else: icloseprice[i] = prev_iclose
            elif curr_trend == -1:
                nok = True
                if beginprice > cp and abs(beginprice - cp) >= box:
                    numcell = math.floor(abs(beginprice - cp) / box)
                    icloseprice[i] = beginprice - numcell * box
                    beginprice, nok = icloseprice[i], False
                else: icloseprice[i] = prev_iclose
                if nok and beginprice < cp and abs(beginprice - cp) >= box * self.reversal:
                    numcell = math.floor(abs(beginprice - cp) / box)
                    icloseprice[i], curr_trend = beginprice + numcell * box, 1
                    beginprice = icloseprice[i]
            elif curr_trend == 1:
                nok = True
                if beginprice < cp and abs(beginprice - cp) >= box:
                    numcell = math.floor(abs(beginprice - cp) / box)
                    icloseprice[i] = beginprice + numcell * box
                    beginprice, nok = icloseprice[i], False
                else: icloseprice[i] = prev_iclose
                if nok and beginprice > cp and abs(beginprice - cp) >= box * self.reversal:
                    numcell = math.floor(abs(beginprice - cp) / box)
                    icloseprice[i], curr_trend = beginprice - numcell * box, -1
                    beginprice = icloseprice[i]
                    
            trend[i] = curr_trend
            if i > 0 and icloseprice[i] != icloseprice[i-1]: box = pboxsize
            if i > 0 and curr_trend != prev_trend and prev_trend != 0:
                ext1, ext0 = ext0, icloseprice[i-1]

            dtb = (curr_trend == 1) and (icloseprice[i] > prev_iclose) and not np.isnan(ext1) and (icloseprice[i] > ext1) and (prev_iclose <= ext1)
            dbb = (curr_trend == -1) and (icloseprice[i] < prev_iclose) and not np.isnan(ext1) and (icloseprice[i] < ext1) and (prev_iclose >= ext1)
            
            if dtb: curr_buyon = 1
            elif dbb: curr_buyon = 0
            buyon[i] = curr_buyon
            
        return icloseprice, trend, buyon

    def calculate_stoch_rsi_tv(self, series, rsi_len=14, stoch_len=14, k_len=3, d_len=3):
        """TradingView Wilder RMA uyumlu Stokastik RSI."""
        delta = series.diff()
        up, down = delta.clip(lower=0), -1 * delta.clip(upper=0)
        
        rma_up = up.ewm(alpha=1/rsi_len, min_periods=rsi_len, adjust=False).mean()
        rma_down = down.ewm(alpha=1/rsi_len, min_periods=rsi_len, adjust=False).mean()
        
        rsi = 100 - (100 / (1 + (rma_up / rma_down)))
        stoch = 100 * (rsi - rsi.rolling(stoch_len).min()) / (rsi.rolling(stoch_len).max() - rsi.rolling(stoch_len).min())
        
        k = stoch.rolling(k_len).mean()
        d = k.rolling(d_len).mean()
        return k, d

    def run(self):
        # İlk çalıştırmada tam geçmişi çek, sonraki döngülerde sadece son barı güncelle
        if self.closes is None:
            self.fetch_deep_data(days_back=2000)
        else:
            self.refresh_latest()

        df_closes = pd.DataFrame(self.closes)
        n = len(df_closes)
        
        bullbo_sum = np.zeros(n)
        for ticker, tick in self.coins.items():
            _, _, buyon = self._calculate_pnf(df_closes[ticker].values, tick, is_bpi_layer=False)
            bullbo_sum += buyon
            
        # Bölen, listedeki güncel aktif coin sayısı
        bullrate = 100.0 * bullbo_sum / len(self.coins)
        bpi, btrend, _ = self._calculate_pnf(bullrate, tick=2.0, is_bpi_layer=True)
        
        k, d = self.calculate_stoch_rsi_tv(df_closes[self.target_coin])
        
        print("-" * 50)
        print(f"Boğa sinyali       : {int(bullbo_sum[-1])}/{len(self.coins)} coin (ham oran: {bullrate[-1]:.2f})")
        print(f"BBPI Değeri        : {bpi[-1]:.2f}")
        print(f"BBPI Yönü          : {'Yükseliş (Turuncu/Yeşil)' if btrend[-1] == 1 else 'Düşüş (Kırmızı/Mavi)'}")
        print("-" * 50)
        print(f"{self.target_coin} Stoch RSI : %K: {k.iloc[-1]:.2f} | %D: {d.iloc[-1]:.2f}")
        print("-" * 50)

if __name__ == "__main__":
    app = BBPI_ExactMatch(target_coin='SOLUSDT', timeframe='4h', percentage=0.66)
    while True:
        app.run()
        time.sleep(5)
