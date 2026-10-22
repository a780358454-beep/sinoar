import json
import urllib.request

REPO = "a780358454-beep/sinoar"
HEADERS = {"User-Agent": "sinoar", "Accept": "application/vnd.github+json"}


def get(url):
    req = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=20) as r:
        return r.status, json.load(r)


try:
    status, repo = get(f"https://api.github.com/repos/{REPO}")
    print("repo:", status, "private=", repo.get("private"), "default_branch=", repo.get("default_branch"))
except Exception as e:
    print("repo:", repr(e))

try:
    status, runs = get(f"https://api.github.com/repos/{REPO}/actions/runs?per_page=5")
    print("runs:", status, "total=", runs.get("total_count"))
    for run in runs.get("workflow_runs", []):
        print(run["id"], run["status"], run["conclusion"], run["head_sha"][:8], run["created_at"], run["name"])
except Exception as e:
    print("runs:", repr(e))
