#!/usr/bin/env python3
"""Generate Mosquitto passwd line (PBKDF2-SHA512, $7$101$)."""
import base64
import hashlib
import os
import sys

password = sys.argv[1] if len(sys.argv) > 1 else "changeme"
username = sys.argv[2] if len(sys.argv) > 2 else "mindbody"
salt = os.urandom(12)
dk = hashlib.pbkdf2_hmac("sha512", password.encode(), salt, 101)
salt_b64 = base64.b64encode(salt).decode("ascii").rstrip("=")
hash_b64 = base64.b64encode(dk).decode("ascii").rstrip("=")
print(f"{username}:$7$101${salt_b64}${hash_b64}")
