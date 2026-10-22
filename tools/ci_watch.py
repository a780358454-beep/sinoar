import io
import json
import os
import sys
import time
import urllib.request
import zipfile

REPO = "a780358454-beep/sinoar"
HEADERS = {"User-Agent": "sinoar", "Accept": "application/vnd.github+json"}
OUT = os.path.join(os.path.dirname(__file__), "ci-logs")
WANT = sys.argv[1] if len(sys.argv) > 1 else None


def api(url):
    req = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def latest_run():
    data = api(f"https://api.github.com/repos/{REPO}/actions/runs?per_page=1")
    runs = data.get("workflow_runs", [])
    return runs[0] if runs else None


run = None
for _ in range(30):
    run = latest_run()
    if run and (WANT is None or run["head_sha"].startswith(WANT)):
        break
    time.sleep(10)

if not run:
    print("no run found")
    raise SystemExit(1)

print("run", run["id"], run["head_sha"][:8], run["status"], run["conclusion"], flush=True)

for _ in range(90):
    run = api(f"https://api.github.com/repos/{REPO}/actions/runs/{run['id']}")
    print(time.strftime("%H:%M:%S"), run["status"], run["conclusion"], flush=True)
    if run["status"] == "completed":
        break
    time.sleep(20)

if run["status"] != "completed":
    print("still running; giving up polling")
    raise SystemExit(0)

print("CONCLUSION:", run["conclusion"])

if run["conclusion"] == "failure":
    os.makedirs(OUT, exist_ok=True)
    url = f"https://api.github.com/repos/{REPO}/actions/runs/{run['id']}/logs"
    req = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=180) as r:
        blob = r.read()
    print("log zip bytes:", len(blob))
    with zipfile.ZipFile(io.BytesIO(blob)) as zf:
        zf.extractall(OUT)
        for name in zf.namelist():
            print("log file:", name)
