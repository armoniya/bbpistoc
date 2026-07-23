"""BBPI + Stoch RSI 15 DAKİKALIK TEK SEFERLİK BACKTEST (canlı/paper-trade DEĞİL).

1h ve 4h motorlarını beklemek yerine, geçmiş 15m veriyi bir kerede çekip
aynı stratejiyi (papertrade.py ile birebir aynı kural) baştan sona
simüle eder ve özet sonucu ekrana basar. Durum dosyası tutmaz, sürekli
çalışmaz; her çalıştırmada sıfırdan hesaplar.

KURAL (sadece long, STOP-LOSS YOK):
  Giriş: BBPI <= 35 iken Stoch %K, %D'yi yukarı keser (K[önceki] < 40)
         -> sinyal barı kapanınca piyasa fiyatından al (tüm bakiye).
         (btrend şartı YOK: btrend P&F teyidi 3 kutuluk hareket bekleyip geç
         onay verdiği için tam dip barında (ör. BBPI hâlâ 4 iken) her zaman
         -1 kalıyor ve gerçek dip girişini kaçırıyordu; bu yüzden kaldırıldı.)
  Çıkış: TP YOK (kâr tavanı kaldırıldı, kâr sınırsız koşabilir).
         Fiyat >= breakeven (giriş + %0.2 komisyon, yani komisyon üstü kâr)
         VE BBPI >= 65 iken VE Stoch RSI %K, %D'yi aşağı keserse
         (K[önceki] >= D[önceki] ve K[şimdi] < D[şimdi]) bar kapanışında sat.
         Komisyon üstü kâr yoksa hiçbir koşulda kapanış yapılmaz; pozisyon
         zararına kapatılmaz, tutulmaya devam edilir.
  Komisyon: %0.1/yön
  --inject N: her ayın 1'inde N$ ek sermaye eklenir. Pozisyon açıksa bu para
           o anki fiyattan pozisyona eklenir (ortalama maliyet düşürülür,
           breakeven yeni ortalamaya göre yeniden hesaplanır); pozisyon
           yoksa nakit bakiyeye eklenip bir sonraki sinyalde yatırılır.

Kullanım:
  bpi_env/bin/python backtest.py              # varsayılan 180 gün geçmiş
  bpi_env/bin/python backtest.py --days 365   # farklı geçmiş uzunluğu
  bpi_env/bin/python backtest.py --days 164 --inject 1000   # + aylık 1000$ ekleme
"""
import argparse

import numpy as np
import pandas as pd
from binance.client import Client

from bbpi import BBPI_ExactMatch

TARGET_COIN = 'SOLUSDT'
TF = '15m'
PCT = 0.10   # gerçek TradingView göstergesiyle karşılaştırılıp doğrulandı (Auto, 15m -> 0.10)
FEE = 0.001
START_BALANCE = 1000.0
BULL_ZONE = 35.0
K_MAX = 40.0
EXIT_ZONE = 65.0


def fetch_data(client: Client, calc: BBPI_ExactMatch, days_back: int) -> pd.DataFrame:
    start_str = f"{days_back} days ago UTC"
    symbols = list(calc.coins.keys())

    print(f"SOLUSDT (OHLC) çekiliyor...")
    target_klines = client.get_historical_klines(TARGET_COIN, TF, start_str)
    target_times = pd.Index([int(k[0]) for k in target_klines])
    df = pd.DataFrame(index=target_times)
    df['open'] = [float(k[1]) for k in target_klines]
    df['high'] = [float(k[2]) for k in target_klines]
    df['low'] = [float(k[3]) for k in target_klines]
    df[TARGET_COIN] = [float(k[4]) for k in target_klines]
    df = df[~df.index.duplicated(keep='last')]

    for i, symbol in enumerate(symbols, 1):
        print(f"\r[{i}/{len(symbols)}] {symbol} çekiliyor...", end='', flush=True)
        klines = client.get_historical_klines(symbol, TF, start_str)
        s = pd.Series([float(k[4]) for k in klines], index=[int(k[0]) for k in klines])
        s = s[~s.index.duplicated(keep='last')]
        df[symbol] = s.reindex(target_times).ffill().fillna(0.0)
    print(f"\nToplam {len(df)} bar ({TF}) çekildi.")
    return df


def run_backtest(df: pd.DataFrame, calc: BBPI_ExactMatch, inject_amount: float = 0.0):
    n = len(df)
    bull_sum = np.zeros(n)
    for ticker, tick in calc.coins.items():
        _, _, buyon = calc._calculate_pnf(df[ticker].values, tick, is_bpi_layer=False)
        bull_sum += buyon
    bullrate = 100.0 * bull_sum / len(calc.coins)
    bpi, btrend, _ = calc._calculate_pnf(bullrate, tick=2.0, is_bpi_layer=True)
    k, d = calc.calculate_stoch_rsi_tv(df[TARGET_COIN])
    k, d = k.to_numpy(), d.to_numpy()

    close = df[TARGET_COIN].to_numpy()
    times = df.index.to_numpy()

    balance = START_BALANCE
    position = None
    trades = []
    equity = [balance]
    injections = []
    last_inject_month = None
    total_injected = 0.0

    for i in range(1, n):
        if inject_amount > 0:
            ts = pd.Timestamp(times[i], unit='ms', tz='UTC')
            month_key = (ts.year, ts.month)
            if ts.day == 1 and month_key != last_inject_month:
                last_inject_month = month_key
                total_injected += inject_amount
                injections.append((ts, inject_amount))
                if position is not None:
                    add_qty = inject_amount / (close[i] * (1 + FEE))
                    position['qty'] += add_qty
                    position['cost'] += inject_amount
                    position['entry_price'] = position['cost'] / (position['qty'] * (1 + FEE))
                    position['breakeven'] = position['cost'] / (position['qty'] * (1 - FEE))
                else:
                    balance += inject_amount

        if position is not None:
            reason = None
            exit_price = None
            if (close[i] >= position['breakeven'] and bpi[i] >= EXIT_ZONE
                    and not np.isnan(k[i - 1]) and not np.isnan(d[i - 1])
                    and k[i - 1] >= d[i - 1] and k[i] < d[i]):
                reason, exit_price = 'exit_cross', close[i]

            if reason:
                proceeds = position['qty'] * exit_price * (1 - FEE)
                pnl_pct = (proceeds / position['cost'] - 1) * 100.0
                balance = proceeds
                trades.append({
                    'giris_zamani': position['entry_time'],
                    'cikis_zamani': pd.Timestamp(times[i], unit='ms', tz='UTC'),
                    'giris': position['entry_price'],
                    'cikis': exit_price,
                    'neden': reason,
                    'net_pnl_pct': pnl_pct,
                    'bakiye': balance,
                })
                position = None
                equity.append(balance)
            else:
                equity.append(position['qty'] * close[i] * (1 - FEE))  # mark-to-market
            continue

        equity.append(balance)
        if np.isnan(k[i]) or np.isnan(d[i]) or np.isnan(k[i - 1]) or np.isnan(d[i - 1]):
            continue
        if (bpi[i] <= BULL_ZONE
                and k[i - 1] <= d[i - 1] and k[i] > d[i] and k[i - 1] < K_MAX):
            price = close[i]
            qty = balance / (price * (1 + FEE))
            position = {
                'entry_time': pd.Timestamp(times[i], unit='ms', tz='UTC'),
                'entry_price': price,
                'realized_balance_at_entry': balance,
                'capital_base_at_entry': START_BALANCE + total_injected,
                'qty': qty,
                'cost': balance,
                'breakeven': price * (1 + FEE) / (1 - FEE),
            }

    return trades, balance, np.array(equity), position, close[-1] if n > 0 else None, total_injected, injections


def print_report(trades, final_balance, equity, open_position, last_price,
                  total_injected=0.0, injections=None):
    n_trades = len(trades)
    total_capital = START_BALANCE + total_injected
    print("\n" + "=" * 60)
    print(f"BACKTEST SONUCU — {TARGET_COIN} {TF}")
    print("=" * 60)

    if total_injected > 0:
        print(f"Aylık ekleme        : {len(injections)} kez x ~{injections[0][1]:.0f}$ "
              f"= toplam {total_injected:.2f}$ ek sermaye "
              f"(başlangıç {START_BALANCE:.2f}$ + ekleme = toplam yatırılan {total_capital:.2f}$)")

    if open_position is not None:
        mtm_value = open_position['qty'] * last_price * (1 - FEE)
        unrealized_pct = (mtm_value / open_position['cost'] - 1) * 100.0
        print(f"*** AÇIK POZİSYON *** giriş {open_position['entry_time']:%d.%m.%Y %H:%M} "
              f"@ ort. maliyet {open_position['entry_price']:.3f} | güncel {last_price:.3f} "
              f"| mark-to-market {mtm_value:.2f}$ ({unrealized_pct:+.2f}%) — breakeven'e "
              f"ulaşmadığı için hâlâ açık.")
        realized_net_profit = open_position['realized_balance_at_entry'] - open_position['capital_base_at_entry']
    else:
        realized_net_profit = final_balance - total_capital
    realized_pct = (realized_net_profit / total_capital * 100.0) if total_capital > 0 else 0.0
    print(f"REALİZE EDİLEN NET KÂR : {realized_net_profit:+.2f}$  "
          f"(tüm sermayeye {total_capital:.2f}$'a göre {realized_pct:+.2f}%)")

    if n_trades == 0:
        print("Kapanan işlem yok.")
        return

    wins = [t for t in trades if t['net_pnl_pct'] > 0]
    losses = [t for t in trades if t['net_pnl_pct'] <= 0]
    win_rate = 100.0 * len(wins) / n_trades
    total_return = (equity[-1] / total_capital - 1) * 100.0
    avg_pnl = sum(t['net_pnl_pct'] for t in trades) / n_trades

    peak = np.maximum.accumulate(equity)
    dd = (equity - peak) / peak * 100.0
    max_dd = dd.min()

    print(f"İşlem sayısı       : {n_trades}  (kazanan {len(wins)} / kaybeden {len(losses)})")
    print(f"Kazanma oranı       : {win_rate:.1f}%")
    print(f"Ortalama işlem PnL  : {avg_pnl:+.2f}%")
    print(f"Toplam getiri (yatırılan {total_capital:.2f}$'a göre): {total_return:+.2f}%  "
          f"({total_capital:.2f}$ -> {equity[-1]:.2f}$)")
    print(f"Maks düşüş (DD, nominal bakiye üzerinden) : {max_dd:.2f}%")
    print("-" * 60)
    print("Son 10 işlem:")
    for t in trades:
        print(f"  {t['giris_zamani']:%d.%m %H:%M} -> {t['cikis_zamani']:%d.%m %H:%M} "
              f"| {t['giris']:.3f} -> {t['cikis']:.3f} | {t['neden']:10s} "
              f"| {t['net_pnl_pct']:+.2f}% | bakiye {t['bakiye']:.2f}$")
    print("=" * 60)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--days', type=int, default=30, help='Geçmiş kaç gün (varsayılan 180)')
    parser.add_argument('--inject', type=float, default=0.0,
                         help='Her ayın 1inde eklenecek sermaye ($, varsayılan 0 = kapalı)')
    args = parser.parse_args()

    client = Client()
    calc = BBPI_ExactMatch(target_coin=TARGET_COIN, timeframe=TF, percentage=PCT)

    df = fetch_data(client, calc, args.days)
    trades, final_balance, equity, open_position, last_price, total_injected, injections = \
        run_backtest(df, calc, inject_amount=args.inject)
    print_report(trades, final_balance, equity, open_position, last_price,
                  total_injected, injections)

    if trades:
        out_path = 'data/backtest_15m_trades.csv'
        pd.DataFrame(trades).to_csv(out_path, index=False)
        print(f"\nİşlem detayları kaydedildi: {out_path}")


if __name__ == '__main__':
    main()
