import json
import urllib.request

REPO = "a780358454-beep/sinoar"
HEADERS = {"User-Agent": "sinoar", "Accept": "application/vnd.github+json"}
RUN_ID = 39749083212

url = f"https://api.github.com/repos/{REPO}/actions/runs/{RUN_ID}/jobs"
req = urllib.request.Request(url, headers=HEADERS)
with urllib.request.urlopen(req, timeout=30) as r:
    data = json.load(r)

for job in data.get("jobs", []):
    print(f"JOB {job['id']} {job['name']} -> {job['status']}/{job['conclusion']}")
    for step in job.get("steps", []):
        print(f"   {step['number']:>2}. {step['name']:<40} {step['status']}/{step['conclusion']}")
    for key in ("html_url",):
        print("   ", job.get(key))
