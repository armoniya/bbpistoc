"""Render'da calisan hafif "vitrin" servisi (bot BURADA calismiyor).

Evdeki papertrade.py her dongude (~8sn) buraya POST /push ile son anlik
goruntuyu (dashboard.json ile ayni format) gonderir; bu servis onu sadece
bellekte tutar ve GET /data/dashboard.json ile geri sunar. Render'in disk'i
kalici olmadigi icin (servis uyuyup uyanabilir) veri hic diske yazilmaz -
zaten ev sunucusu birkac saniyede bir yeniden gonderdigi icin bellek her
uyanista aninda tekrar dolar.

/push GUCLU bir API anahtariyla korunur (RELAY_API_KEY ortam degiskeni) ki
disaridan kimse sahte veri enjekte edemesin. GET uclari (dashboard.json,
trading-dashboard.html) bilerek acik birakildi: icerik hassas degil (gercek
para yok, sadece herkese acik piyasa fiyati + sanal bakiye).

Calistirma (Render Start Command):
  gunicorn app:app --bind 0.0.0.0:$PORT
"""
import os
import time
from functools import wraps

from flask import Flask, abort, jsonify, request, send_from_directory

app = Flask(__name__)

API_KEY = os.environ.get('RELAY_API_KEY', '')
_state = {'payload': None, 'received_at': 0.0}


def require_key(fn):
    @wraps(fn)
    def wrapper(*args, **kwargs):
        if not API_KEY or request.headers.get('X-API-Key') != API_KEY:
            abort(401)
        return fn(*args, **kwargs)
    return wrapper


@app.post('/push')
@require_key
def push():
    data = request.get_json(force=True, silent=True)
    if data is None:
        abort(400)
    _state['payload'] = data
    _state['received_at'] = time.time()
    return {'ok': True}


@app.get('/data/dashboard.json')
def dashboard_json():
    if _state['payload'] is None:
        return jsonify({'error': 'henuz ev sunucusundan veri gelmedi'}), 503
    return jsonify(_state['payload'])


@app.get('/health')
def health():
    age = None if not _state['received_at'] else round(time.time() - _state['received_at'], 1)
    return {'ok': True, 'last_push_age_sec': age}


@app.get('/trading-dashboard.html')
def dashboard_html():
    return send_from_directory(os.path.dirname(os.path.abspath(__file__)), 'trading-dashboard.html')


@app.get('/')
def root():
    return dashboard_html()
