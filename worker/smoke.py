#!/usr/bin/env python3
"""End-to-end check of the teramera worker API against a throwaway local D1.

    cd worker && python3 smoke.py           # wipe local DB, start server, run, stop
    cd worker && python3 smoke.py --reuse   # keep a running server + its data

No login needed: the script mints HS256 access tokens for seeded users, so it
covers the real auth middleware without Google. Every assertion prints "ok -".
"""

import argparse
import base64
import hashlib
import hmac
import json
import os
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from pathlib import Path

WORKER = Path(__file__).resolve().parent
PORT = 8787
BASE = f"http://127.0.0.1:{PORT}"
SECRET = "local-dev-secret-0123456789abcdef0123456789abcdef"
DB = "teramera"

# Seeded once per run; the checks assume a clean DB, hence the wipe by default.
USERS = [("user-a", "Alice"), ("user-b", "Bob"), ("user-c", "Cara")]


# ---------- server lifecycle ----------


def env():
    return {**os.environ, "JWT_SECRET": SECRET, "WRANGLER_SEND_METRICS": "false"}


def wrangler(*args, check=True):
    return subprocess.run(
        ["npx", "wrangler", *args],
        cwd=WORKER,
        env=env(),
        capture_output=True,
        text=True,
        check=check,
    )


def d1(sql):
    result = wrangler("d1", "execute", DB, "--local", "--command", sql, check=False)
    if result.returncode != 0:
        raise RuntimeError(f"D1 command failed:\n{result.stdout}\n{result.stderr}")
    return result.stdout


def free_port():
    try:
        out = subprocess.run(
            ["lsof", "-ti", f":{PORT}"], capture_output=True, text=True
        ).stdout
    except FileNotFoundError:
        return
    for pid in out.split():
        try:
            os.kill(int(pid), signal.SIGTERM)
        except (ProcessLookupError, ValueError):
            pass
    time.sleep(1)


def reachable(timeout=1.5):
    try:
        urllib.request.urlopen(BASE + "/", timeout=timeout)
        return True
    except urllib.error.HTTPError:
        return True  # any HTTP answer means the worker is listening
    except Exception:
        return False


def reset_db():
    shutil.rmtree(WORKER / ".wrangler" / "state", ignore_errors=True)


def apply_schema():
    result = wrangler("d1", "execute", DB, "--local", "--file", "schema.sql", check=False)
    if result.returncode != 0:
        raise RuntimeError(f"schema.sql failed:\n{result.stdout}\n{result.stderr}")


def seed_users():
    values = ", ".join(
        f"('{uid}', NULL, '{uid}@example.com', '{name}', 0)" for uid, name in USERS
    )
    d1(
        "INSERT OR REPLACE INTO users (id, phone, email, name, created_at) "
        f"VALUES {values};"
    )


def start_server():
    log_path = Path(tempfile.gettempdir()) / "teramera-smoke-wrangler.log"
    log = open(log_path, "w")
    proc = subprocess.Popen(
        ["npx", "wrangler", "dev", "--port", str(PORT), "--local"],
        cwd=WORKER,
        env=env(),
        stdout=log,
        stderr=subprocess.STDOUT,
        start_new_session=True,
    )
    deadline = time.time() + 90
    while time.time() < deadline:
        if proc.poll() is not None:
            print(log_path.read_text()[-2000:])
            raise RuntimeError("wrangler dev exited before it was ready")
        if reachable():
            return proc
        time.sleep(0.5)
    print(log_path.read_text()[-2000:])
    raise RuntimeError(f"wrangler dev did not answer on {BASE} within 90s")


def stop_server(proc):
    if proc is None or proc.poll() is not None:
        return
    try:
        os.killpg(os.getpgid(proc.pid), signal.SIGTERM)
    except ProcessLookupError:
        return
    try:
        proc.wait(timeout=15)
    except subprocess.TimeoutExpired:
        os.killpg(os.getpgid(proc.pid), signal.SIGKILL)
    time.sleep(1)


# ---------- API helpers ----------


def b64(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def token(sub):
    header = b64(json.dumps({"alg": "HS256", "typ": "JWT"}).encode())
    payload = b64(
        json.dumps(
            {
                "sub": sub,
                "typ": "access",
                "iat": int(time.time()),
                "exp": int(time.time()) + 3600,
            }
        ).encode()
    )
    sig = b64(hmac.new(SECRET.encode(), f"{header}.{payload}".encode(), hashlib.sha256).digest())
    return f"{header}.{payload}.{sig}"


A, B = token("user-a"), token("user-b")


def call(method, path, body=None, tok=A, expect=200):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        BASE + path,
        data=data,
        method=method,
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {tok}"},
    )
    try:
        with urllib.request.urlopen(req) as r:
            status, raw = r.status, r.read()
    except urllib.error.HTTPError as e:
        status, raw = e.code, e.read()
    parsed = json.loads(raw) if raw else None
    assert status == expect, f"{method} {path} -> {status} {parsed} (expected {expect})"
    return parsed


def page(path):
    """GET a non-JSON (HTML) route and return (status, body)."""
    try:
        with urllib.request.urlopen(BASE + path) as r:
            return r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def check(label, cond, detail=""):
    if not cond:
        raise AssertionError(f"{label} failed {detail}")
    print("ok -", label)


# ---------- the checks ----------


def run_checks():
    # ---- group + multi-payer expense ----
    group = call("POST", "/groups", {"name": "Goa Trip"})
    gid = group["id"]
    call("POST", f"/groups/{gid}/members", {"userId": "user-b"})

    created = call(
        "POST",
        "/expenses",
        {
            "groupId": gid,
            "title": "Dinner",
            "amountMinor": 10000,
            "splitType": "EQUAL",
            "participantIds": ["user-a", "user-b"],
            "payments": [
                {"userId": "user-a", "amountMinor": 6000},
                {"userId": "user-b", "amountMinor": 4000},
            ],
        },
    )
    detail = call("GET", f"/groups/{gid}/detail")
    check("multi-payer expense appears once", len(detail["expenses"]) == 1, detail["expenses"])
    exp = detail["expenses"][0]
    check("logical total is 10000", exp["amountMinor"] == 10000, exp)
    check("payer count is 2", exp["payerCount"] == 2, exp)
    check("share is half", exp["myShareMinor"] == 5000, exp)
    check("participants are 2", exp["participantCount"] == 2, exp)
    check("worst case count present", detail["worstCasePayments"] == 1, detail["worstCasePayments"])
    nets = {b["userId"]: b["netMinor"] for b in detail["balances"]}
    check("nets are balanced", nets == {"user-a": 1000, "user-b": -1000}, detail["balances"])
    check("one simplified transfer", len(detail["simplifiedDebts"]) == 1, detail["simplifiedDebts"])

    groups = call("GET", "/groups")
    check("group summary total", groups[0]["totalSpentMinor"] == 10000, groups)
    check("group summary net", groups[0]["netForMeMinor"] == 1000, groups)

    balances = call("GET", "/balances")
    check(
        "friend balance 1000",
        {b["userId"]: b["netMinor"] for b in balances} == {"user-b": 1000},
        balances,
    )

    # ---- edit total: payer amounts + shares must both rescale ----
    call("PATCH", f"/expenses/{created['id']}", {"title": "Dinner (bigger)", "amountMinor": 20000})
    detail = call("GET", f"/groups/{gid}/detail")
    exp = detail["expenses"][0]
    check("edited total", exp["amountMinor"] == 20000, exp)
    check("edited myShare", exp["myShareMinor"] == 10000, exp)
    nets = {b["userId"]: b["netMinor"] for b in detail["balances"]}
    check("edited nets", nets == {"user-a": 2000, "user-b": -2000}, nets)
    check("edited title", exp["title"] == "Dinner (bigger)", exp)

    # ---- exact split ----
    exact = call(
        "POST",
        "/expenses",
        {
            "groupId": gid,
            "title": "Cab",
            "amountMinor": 10000,
            "splitType": "EXACT",
            "participants": {"user-a": 7000, "user-b": 3000},
            "paidByUserId": "user-a",
        },
    )
    detail = call("GET", f"/groups/{gid}/detail")
    check("two logical expenses", len(detail["expenses"]) == 2, detail["expenses"])
    nets = {b["userId"]: b["netMinor"] for b in detail["balances"]}
    check("exact split nets", nets == {"user-a": 5000, "user-b": -5000}, nets)
    check(
        "one transfer covers both",
        len(detail["simplifiedDebts"]) == 1 and detail["simplifiedDebts"][0]["amountMinor"] == 5000,
        detail["simplifiedDebts"],
    )

    # ---- editing only the title of an EXACT expense must not change shares ----
    call("PATCH", f"/expenses/{exact['id']}", {"title": "Cab ride"})
    detail = call("GET", f"/groups/{gid}/detail")
    nets = {b["userId"]: b["netMinor"] for b in detail["balances"]}
    check("exact split preserved on title edit", nets == {"user-a": 5000, "user-b": -5000}, nets)

    # ---- editing the total of an EXACT expense keeps the 70/30 proportion ----
    call("PATCH", f"/expenses/{exact['id']}", {"amountMinor": 20000})
    detail = call("GET", f"/groups/{gid}/detail")
    nets = {b["userId"]: b["netMinor"] for b in detail["balances"]}
    check("exact proportions scale with total", nets == {"user-a": 8000, "user-b": -8000}, nets)
    check(
        "double-size exact expense",
        [e for e in detail["expenses"] if e["id"] == exact["id"]][0]["amountMinor"] == 20000,
        detail["expenses"],
    )

    # ---- activity: no duplicate rows, correct myShare ----
    activity = call("GET", "/activity")
    exp_events = [e for e in activity if e["type"] == "expense"]
    check("two expense events", len(exp_events) == 2, exp_events)
    big = [e for e in exp_events if e["title"] == "Dinner (bigger)"][0]
    check("activity myShare across payers", big["myShareMinor"] == 10000, big)
    check("activity group name", big["groupName"] == "Goa Trip", big)
    check("activity participants", big["participantCount"] == 2, big)
    check("no settlements yet", [e for e in activity if e["type"] == "settlement"] == [], activity)

    # ---- settle the whole group debt ----
    call(
        "POST",
        "/settlements",
        {
            "groupId": gid,
            "payerUserId": "user-b",
            "paidToUserId": "user-a",
            "amountMinor": 8000,
            "method": "UPI",
        },
    )
    detail = call("GET", f"/groups/{gid}/detail")
    nets = {b["userId"]: b["netMinor"] for b in detail["balances"]}
    check("group settled to zero", all(v == 0 for v in nets.values()), nets)
    check("no transfers after settle", detail["simplifiedDebts"] == [], detail["simplifiedDebts"])
    check("worst case present after settle", detail["worstCasePayments"] == 0, detail["worstCasePayments"])

    activity = call("GET", "/activity")
    check("settlement in activity", len([e for e in activity if e["type"] == "settlement"]) == 1, activity)

    # ---- group-less expense ----
    call(
        "POST",
        "/expenses",
        {
            "groupId": None,
            "title": "Coffee",
            "amountMinor": 3000,
            "splitType": "EQUAL",
            "participantIds": ["user-a", "user-b"],
            "paidByUserId": "user-a",
        },
    )
    balances = call("GET", "/balances")
    check(
        "group-less expense hits friend balance",
        {b["userId"]: b["netMinor"] for b in balances} == {"user-b": 1500},
        balances,
    )

    # ---- friend lookup by email (what the add-expense picker calls) ----
    found = call("GET", "/users/find?email=user-c@example.com")
    check("find user by email", found["id"] == "user-c", found)
    # someone you're square with is still a valid split partner
    call(
        "POST",
        "/expenses",
        {
            "groupId": None,
            "title": "Museum",
            "amountMinor": 2000,
            "splitType": "EQUAL",
            "participantIds": ["user-a", "user-c"],
            "paidByUserId": "user-a",
        },
    )
    balances = {b["userId"]: b["netMinor"] for b in call("GET", "/balances")}
    check("split with a zero-balance friend works", balances.get("user-c") == 1000, balances)
    call("GET", "/users/find?email=nobody@example.com", expect=404)
    call("GET", "/users/find?email=not-an-email", expect=400)

    # ---- validation ----
    call(
        "POST",
        "/expenses",
        {
            "groupId": gid,
            "title": "Bad",
            "amountMinor": 100.5,
            "splitType": "EQUAL",
            "participantIds": ["user-a", "user-b"],
            "paidByUserId": "user-a",
        },
        expect=400,
    )
    call(
        "POST",
        "/expenses",
        {
            "groupId": gid,
            "title": "Outsider",
            "amountMinor": 1000,
            "splitType": "EQUAL",
            "participantIds": ["user-a", "user-c"],
            "paidByUserId": "user-a",
        },
        expect=403,
    )
    call(
        "POST",
        "/expenses",
        {
            "groupId": gid,
            "title": "Unbalanced payers",
            "amountMinor": 1000,
            "splitType": "EQUAL",
            "participantIds": ["user-a", "user-b"],
            "payments": [{"userId": "user-a", "amountMinor": 400}],
        },
        expect=400,
    )
    call("POST", "/settlements", {"paidToUserId": "nobody", "amountMinor": 100, "method": "UPI"}, expect=400)
    call("POST", "/settlements", {"paidToUserId": "user-a", "amountMinor": 100, "method": "UPI"}, expect=400)

    # ---- percent + shares splits in a second group ----
    g2 = call("POST", "/groups", {"name": "Splits"})["id"]
    call("POST", f"/groups/{g2}/members", {"userId": "user-b"})
    call(
        "POST",
        "/expenses",
        {
            "groupId": g2,
            "title": "Percent",
            "amountMinor": 4000,
            "splitType": "PERCENT",
            "participants": {"user-a": 25, "user-b": 75},
            "paidByUserId": "user-a",
        },
    )
    call(
        "POST",
        "/expenses",
        {
            "groupId": g2,
            "title": "Shares",
            "amountMinor": 3000,
            "splitType": "SHARES",
            "participants": {"user-a": 1, "user-b": 2},
            "paidByUserId": "user-a",
        },
    )
    detail2 = call("GET", f"/groups/{g2}/detail")
    nets = {b["userId"]: b["netMinor"] for b in detail2["balances"]}
    check("percent + shares nets", nets == {"user-a": 5000, "user-b": -5000}, nets)
    check("percent and shares both stored", len(detail2["expenses"]) == 2, detail2["expenses"])
    exact_event = [e for e in call("GET", "/activity") if e.get("title") == "Cab ride"][0]
    check("activity shows edited exact total", exact_event["amountMinor"] == 20000, exact_event)

    # ---- delete removes the whole logical expense ----
    call("DELETE", f"/expenses/{exact['id']}")
    detail = call("GET", f"/groups/{gid}/detail")
    check("delete removed both sibling rows", len(detail["expenses"]) == 1, detail["expenses"])

    # ---- manage group: rename + delete (creator only) ----
    renamed = call("PATCH", f"/groups/{g2}", {"name": "Splits renamed"})
    check("group rename returns new name", renamed["name"] == "Splits renamed", renamed)
    renamed_detail = call("GET", f"/groups/{g2}/detail")
    check("renamed group reflected in detail", renamed_detail["name"] == "Splits renamed", renamed_detail["name"])
    call("PATCH", f"/groups/{g2}", {"name": "Not allowed"}, tok=B, expect=403)
    check("non-owner cannot rename group", True)
    call("DELETE", f"/groups/{g2}", tok=B, expect=403)
    check("non-owner cannot delete group", True)
    call("DELETE", f"/groups/{g2}")
    call("GET", f"/groups/{g2}/detail", expect=404)
    remaining = call("GET", "/groups")
    check("deleted group disappears from group list", all(g["id"] != g2 for g in remaining), remaining)

    # ---- app version link is scheme-correct locally ----
    version = call("GET", "/app/version")
    check("version endpoint reachable without auth", version["versionCode"] >= 1, version)
    check("apk url matches request origin", version["apkUrl"] == f"{BASE}/teramera.apk", version)

    # ---- public landing page (the download site) ----
    status, landing = page("/")
    check("root serves the download page", status == 200 and "<title>teramera" in landing, landing[:120])
    check("landing page links the apk", "/teramera.apk" in landing)
    check("landing page shows the shipped version", version["versionName"] in landing)
    health = call("GET", "/health", tok="")
    check("health probe stays public json", health["status"] == "ok", health)


def main():
    parser = argparse.ArgumentParser(description="Run the worker API smoke test.")
    parser.add_argument(
        "--reuse",
        action="store_true",
        help="use an already-running server and keep its data (checks may fail on dirty state)",
    )
    args = parser.parse_args()

    proc = None
    try:
        if args.reuse:
            if not reachable():
                sys.exit(f"No server on {BASE} — start `npx wrangler dev` or drop --reuse.")
            print(f"using the server already on {BASE}")
        else:
            print("resetting local D1 …")
            free_port()
            reset_db()
            apply_schema()
            print("starting wrangler dev …")
            proc = start_server()

        seed_users()
        print("running checks\n")
        run_checks()
        print("\nALL BACKEND CHECKS PASSED")
    finally:
        stop_server(proc)


if __name__ == "__main__":
    main()
