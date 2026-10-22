import io
import json
import os
import re
import sys
import urllib.request
import zipfile

REPO = "a780358454-beep/sinoar"
TOKEN = os.environ.get("GH_TOKEN", "").strip()
HEADERS = {"User-Agent": "sinoar", "Accept": "application/vnd.github+json"}
if TOKEN:
    HEADERS["Authorization"] = f"Bearer {TOKEN}"
WANT = sys.argv[1] if len(sys.argv) > 1 else None


class SafeRedirect(urllib.request.HTTPRedirectHandler):
    """Drop the API token when GitHub redirects us to blob storage."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        new = super().redirect_request(req, fp, code, msg, headers, newurl)
        if new is not None and "api.github.com" not in newurl:
            try:
                new.remove_header("Authorization")
            except KeyError:
                pass
        return new


OPENER = urllib.request.build_opener(SafeRedirect)


def api(url):
    req = urllib.request.Request(url, headers=HEADERS)
    with OPENER.open(req, timeout=40) as r:
        return json.load(r)


runs = api(f"https://api.github.com/repos/{REPO}/actions/runs?per_page=5")
run = next(
    (r for r in runs["workflow_runs"] if WANT is None or r["head_sha"].startswith(WANT)),
    runs["workflow_runs"][0],
)
print("run", run["id"], run["head_sha"][:8], run["conclusion"], flush=True)

jobs = api(f"https://api.github.com/repos/{REPO}/actions/runs/{run['id']}/jobs")
failed = [j for j in jobs["jobs"] if j["conclusion"] == "failure"]
if not failed:
    print("no failed job", flush=True)
    raise SystemExit(0)

job = failed[0]
print("job", job["id"], job["name"], flush=True)

req = urllib.request.Request(
    f"https://api.github.com/repos/{REPO}/actions/jobs/{job['id']}/logs",
    headers=HEADERS,
)
with OPENER.open(req, timeout=300) as r:
    blob = r.read()
print("log bytes", len(blob), flush=True)

zf = None
try:
    zf = zipfile.ZipFile(io.BytesIO(blob))
except zipfile.BadZipFile:
    pass


def report(name, text):
    print("=====", name, len(text), flush=True)
    lines = text.splitlines()
    hits = [
        line
        for line in lines
        if re.search(
            r"^e: |error:|FAILURE:|What went wrong|Caused by|Unresolved reference|Could not (find|resolve)|\.kt:\d+",
            line,
        )
    ]
    for line in hits[:80]:
        print("  >", line[:300])
    print("  ---- tail ----")
    for line in lines[-25:]:
        print("  |", line[:300])


if zf is not None:
    for name in zf.namelist():
        report(name, zf.read(name).decode("utf-8", "replace"))
else:
    report("log", blob.decode("utf-8-sig", "replace"))
