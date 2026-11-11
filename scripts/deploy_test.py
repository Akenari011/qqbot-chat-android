#!/usr/bin/env python3
"""Deploy an APK to the instabox Android box, install it on emulator-5554 and
capture a screenshot + UI hierarchy. All work happens on the server-side box.

API reference (verified 2026-11-11):
  PUT  /v1/machines/{id}/files?path=...          upload raw bytes (max 5 GiB)
  GET  /v1/machines/{id}/files/content?path=...  download raw bytes
  POST /v1/machines/{id}/exec                    run a shell command
  PUT  /v1/machines/{id}/activity-leases/{lid}   keep-alive lease

Usage: python3 scripts/deploy_test.py path/to/app-debug.apk [package] [activity]
"""
import hashlib
import json
import os
import sys
import urllib.parse
import urllib.request

KEY = os.environ.get("INSTABOX_KEY", "ibk_dwiku67nrx2ex4wisogqsv276kcr6jb556b5duyaco23j26gey5a")
MID = os.environ.get("INSTABOX_MACHINE", "m_agq4n6zhqjd6uyxyv4hlecu4f4")
BASE = "https://api.instabox.sh/v1"

APK = sys.argv[1] if len(sys.argv) > 1 else None
PKG = sys.argv[2] if len(sys.argv) > 2 else "org.example.qqbotchat"
ACT = sys.argv[3] if len(sys.argv) > 3 else ".MainActivity"


def call(method, path, body=None, raw=None, timeout=300, extra=None):
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    headers = {"X-API-Key": KEY}
    if body is not None or raw is not None:
        headers["Content-Type"] = "application/octet-stream" if raw is not None else "application/json"
    if extra:
        headers.update(extra)
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        payload = r.read()
        ctype = r.headers.get("Content-Type", "")
        return payload, ctype, r.status


def exec_cmd(cmd, timeout=180):
    payload, _, _ = call("POST", f"/machines/{MID}/exec",
                         body={"cmd": cmd, "timeoutSeconds": timeout}, timeout=timeout + 30)
    d = json.loads(payload)
    return d.get("exitCode"), d.get("stdout") or "", d.get("stderr") or ""


def upload(local, remote):
    data = open(local, "rb").read()
    q = urllib.parse.urlencode({"path": remote, "parents": "true", "mode": "0644"})
    payload, _, status = call("PUT", f"/machines/{MID}/files?{q}", raw=data, timeout=600)
    print("  upload status", status, "sha256", hashlib.sha256(data).hexdigest()[:16])
    return json.loads(payload)


def download(remote, local):
    q = urllib.parse.urlencode({"path": remote})
    payload, _, _ = call("GET", f"/machines/{MID}/files/content?{q}", timeout=300)
    open(local, "wb").write(payload)
    return len(payload)


def lease_keepalive():
    try:
        call("PUT", f"/machines/{MID}/activity-leases/build", body={"ttlSeconds": 3600})
    except Exception as e:
        print("  lease warn:", str(e)[:120])


def main():
    if not APK or not os.path.exists(APK):
        print("usage: deploy_test.py <apk> [package] [activity]")
        return 1
    print("[0/7] keep-alive lease")
    lease_keepalive()

    print(f"[1/7] upload {APK} ({os.path.getsize(APK)} bytes)")
    print(" ", upload(APK, "/data/home/qqbot.apk"))

    print("[2/7] verify on box")
    code, out, err = exec_cmd("ls -l /data/home/qqbot.apk; file /data/home/qqbot.apk 2>/dev/null | head -1")
    print(out.strip()[:300])

    print("[3/7] adb install")
    code, out, err = exec_cmd("adb install -r /data/home/qqbot.apk 2>&1", timeout=300)
    print(out.strip()[-700:])
    if "Success" not in out:
        print("install failed", err[:300])
        return 1

    print("[4/7] resolve & launch")
    code, out, err = exec_cmd(f"adb shell cmd package resolve-activity --brief {PKG} | tail -1")
    resolved = out.strip().splitlines()[-1] if out.strip() else ""
    print("  resolved:", resolved)
    exec_cmd(f"adb shell am force-stop {PKG}")
    code, out, err = exec_cmd(f"adb shell am start -n {PKG}/{ACT} 2>&1")
    print(out.strip()[-400:])
    exec_cmd("sleep 5")

    print("[5/7] UI hierarchy")
    code, out, err = exec_cmd("adb exec-out uiautomator dump /dev/fd/1 2>/dev/null | head -c 4000",
                              timeout=120)
    print(out.strip()[:1500])

    print("[6/7] screenshot")
    exec_cmd("adb shell screencap -p /sdcard/qqbot_screen.png")
    try:
        n = download("/sdcard/qqbot_screen.png", os.path.splitext(APK)[0] + "-screen.png")
        print("  screenshot bytes", n)
    except Exception as e:
        print("  screenshot download failed:", str(e)[:200])
        code, out, err = exec_cmd("adb shell ls -l /sdcard/qqbot_screen.png 2>&1")
        print(out.strip()[:300])

    print("[7/7] logcat errors (last 25)")
    code, out, err = exec_cmd("adb logcat -d -t 60 *:E 2>/dev/null | tail -25")
    print(out.strip()[:1200])
    print("DONE")
    return 0


if __name__ == "__main__":
    sys.exit(main())
