import time
import urllib.request

URLS = [
    "https://github.com",
    "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip",
    "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse",
]

for url in URLS:
    try:
        req = urllib.request.Request(url, headers={"Range": "bytes=0-2000000",
                                                  "User-Agent": "Mozilla/5.0"})
        start = time.time()
        with urllib.request.urlopen(req, timeout=20) as resp:
            data = resp.read(2_000_000)
        elapsed = max(time.time() - start, 0.001)
        print(f"{url[:70]:70} OK {len(data)/1024:8.0f} KB in {elapsed:5.1f}s = {len(data)/1024/elapsed:7.0f} KB/s")
    except Exception as exc:
        print(f"{url[:70]:70} FAIL {exc}")
