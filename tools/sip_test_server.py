#!/usr/bin/env python3
"""Minimal UDP SIP registrar + echo INVITE for local lab testing.

Default account: 1001 / secret @ 127.0.0.1
Never use this on a public interface with real credentials.
"""
from __future__ import annotations

import hashlib
import random
import socket
import threading
import time

HOST = "0.0.0.0"
PORT = 5060
REALM = "127.0.0.1"
USERS = {"1001": "secret", "1002": "secret"}
NONCES: dict[str, float] = {}


def md5(s: str) -> str:
    return hashlib.md5(s.encode()).hexdigest()


def parse_sip(data: bytes):
    text = data.decode("utf-8", "replace")
    lines = text.split("\r\n")
    start = lines[0]
    headers = {}
    i = 1
    while i < len(lines) and lines[i]:
        if ":" in lines[i]:
            k, v = lines[i].split(":", 1)
            headers[k.strip()] = v.strip()
        i += 1
    body = "\r\n".join(lines[i + 1 :]) if i < len(lines) else ""
    return start, headers, body


def header(headers, name):
    for k, v in headers.items():
        if k.lower() == name.lower():
            return v
    return None


def make_nonce() -> str:
    n = md5(str(time.time()) + str(random.random()))
    NONCES[n] = time.time()
    return n


def check_auth(headers, method: str, uri: str) -> bool:
    auth = header(headers, "Authorization") or header(headers, "Proxy-Authorization")
    if not auth or not auth.startswith("Digest "):
        return False
    parts = {}
    for item in auth[len("Digest ") :].split(","):
        item = item.strip()
        if "=" in item:
            k, v = item.split("=", 1)
            parts[k.strip()] = v.strip().strip('"')
    user = parts.get("username")
    if user not in USERS:
        return False
    realm = parts.get("realm", REALM)
    nonce = parts.get("nonce", "")
    if nonce not in NONCES:
        return False
    ha1 = md5(f"{user}:{realm}:{USERS[user]}")
    ha2 = md5(f"{method}:{parts.get('uri', uri)}")
    expected = md5(f"{ha1}:{nonce}:{ha2}")
    return expected == parts.get("response")


def respond(sock, addr, start_line: str, headers: dict, body: str = ""):
    lines = [start_line]
    for k, v in headers.items():
        lines.append(f"{k}: {v}")
    lines.append(f"Content-Length: {len(body)}")
    lines.append("")
    lines.append(body)
    sock.sendto("\r\n".join(lines).encode(), addr)


def handle(sock, data, addr):
    start, headers, body = parse_sip(data)
    print(f"<< {addr} {start}")
    via = header(headers, "Via") or ""
    frm = header(headers, "From") or ""
    to = header(headers, "To") or ""
    call_id = header(headers, "Call-ID") or ""
    cseq = header(headers, "CSeq") or ""
    base = {"Via": via, "From": frm, "To": to, "Call-ID": call_id, "CSeq": cseq}

    if start.startswith("REGISTER"):
        if not check_auth(headers, "REGISTER", start.split()[1]):
            nonce = make_nonce()
            h = dict(base)
            h["WWW-Authenticate"] = f'Digest realm="{REALM}", nonce="{nonce}", algorithm=MD5'
            respond(sock, addr, "SIP/2.0 401 Unauthorized", h)
            return
        h = dict(base)
        h["Contact"] = header(headers, "Contact") or ""
        h["Expires"] = header(headers, "Expires") or "3600"
        to_tag = f";tag={md5(call_id)[:8]}"
        if "tag=" not in h["To"]:
            h["To"] = h["To"] + to_tag
        respond(sock, addr, "SIP/2.0 200 OK", h)
        print(f">> REGISTERED {frm}")
        return

    if start.startswith("INVITE"):
        if not check_auth(headers, "INVITE", start.split()[1]):
            nonce = make_nonce()
            h = dict(base)
            h["WWW-Authenticate"] = f'Digest realm="{REALM}", nonce="{nonce}", algorithm=MD5'
            respond(sock, addr, "SIP/2.0 401 Unauthorized", h)
            return
        respond(sock, addr, "SIP/2.0 100 Trying", dict(base))
        to_tag = f";tag=echo{md5(call_id)[:6]}"
        base180 = dict(base)
        base180["To"] = (to if "tag=" in to else to + to_tag)
        respond(sock, addr, "SIP/2.0 180 Ringing", base180)
        sdp = body or ""
        h200 = dict(base180)
        h200["Content-Type"] = "application/sdp"
        h200["Contact"] = f"<sip:echo@{HOST}:{PORT}>"
        respond(sock, addr, "SIP/2.0 200 OK", h200, sdp)
        print(f">> INVITE answered (echo SDP) {frm}")
        return

    if start.startswith("ACK") or start.startswith("BYE") or start.startswith("OPTIONS"):
        respond(sock, addr, "SIP/2.0 200 OK", dict(base))
        return

    respond(sock, addr, "SIP/2.0 501 Not Implemented", dict(base))


def main():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind((HOST, PORT))
    print(f"SIP test server on udp://{HOST}:{PORT} accounts={list(USERS)}")
    while True:
        data, addr = sock.recvfrom(65535)
        threading.Thread(target=handle, args=(sock, data, addr), daemon=True).start()


if __name__ == "__main__":
    main()
