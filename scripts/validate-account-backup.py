#!/usr/bin/env python3
"""Independent Python reader of public, synthetic JVM fixtures; never accepts personal files."""
import base64
import hashlib
import json
import struct
from pathlib import Path
from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

root = Path('app/build/auth-backup-validation')
magic = b'NOCTUA-ACCOUNT-BACKUP-1\n'
owner = '11111111-1111-4111-8111-111111111111'
for name, password in [('synthetic.noctua', 'Senha-backup-ficticia-2026'),
                       ('unicode-password.noctua', 'Senha-fictícia-ç-2026')]:
    data = (root / name).read_bytes()
    assert data.startswith(magic)
    length = struct.unpack('>I', data[len(magic):len(magic) + 4])[0]
    end = len(magic) + 4 + length
    h = json.loads(data[len(magic) + 4:end])
    assert h['owner'] == owner and h['version'] == 1
    assert h['format'] == 'noctua-account-backup' and h['iterations'] == 600_000
    key = hashlib.pbkdf2_hmac('sha256', password.encode('utf-8'), base64.b64decode(h['salt']), h['iterations'], 32)
    aes = AESGCM(key)
    assert aes.decrypt(base64.b64decode(h['iv']), data[end:], data[:end]) == (root / 'expected.txt').read_bytes()
    # Owner/header alteration is authenticated, including when ciphertext is unchanged.
    changed = data[:end].replace(owner.encode(), b'22222222-2222-4222-8222-222222222222')
    try:
        aes.decrypt(base64.b64decode(h['iv']), data[end:], changed)
        raise AssertionError('Authenticated header alteration was accepted')
    except InvalidTag:
        pass
print('Backup sintético: leitor independente decifrou payload UTF-8, senhas ASCII/Unicode e rejeitou adulteração de titularidade.')
