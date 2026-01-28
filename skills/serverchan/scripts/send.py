#!/usr/bin/env python3
"""Server酱³ 推送脚本"""
import os
import sys
import urllib.request
import urllib.parse
import json

def send(title: str, desp: str = "") -> dict:
    key = os.environ.get("FT07_KEY")
    if not key:
        return {"error": "FT07_KEY 环境变量未设置"}

    user_id = key[4:].split('t')[0]
    url = f"https://{user_id}.push.ft07.com/send/{key}.send"
    data = urllib.parse.urlencode({"title": title, "desp": desp}).encode()

    try:
        req = urllib.request.Request(url, data=data)
        req.add_header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36 Edg/144.0.0.0")
        with urllib.request.urlopen(req, timeout=10) as resp:
            return json.loads(resp.read().decode())
    except Exception as e:
        return {"error": str(e)}

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("用法: send.py <title> [desp]")
        sys.exit(1)

    title = sys.argv[1]
    desp = sys.argv[2] if len(sys.argv) > 2 else ""
    result = send(title, desp)
    print(json.dumps(result, ensure_ascii=False, indent=2))
