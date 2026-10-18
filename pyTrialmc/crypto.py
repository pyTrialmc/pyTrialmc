import hashlib
import os

MAGIC = b"PYTM"
VERSION = 1
SALT_LENGTH = 16
CHECKSUM_LENGTH = 32
HEADER_LENGTH = len(MAGIC) + 1 + SALT_LENGTH + CHECKSUM_LENGTH
KEY = hashlib.sha256(b"pyTrial mod package v1").digest()


def _keystream(salt, length):
    stream = bytearray()
    counter = 0
    while len(stream) < length:
        stream += hashlib.sha256(KEY + salt + counter.to_bytes(4, "little")).digest()
        counter += 1
    return bytes(stream[:length])


def _xor(data, stream):
    return bytes(left ^ right for left, right in zip(data, stream))


def encrypt(data):
    salt = os.urandom(SALT_LENGTH)
    body = _xor(data, _keystream(salt, len(data)))
    return MAGIC + bytes([VERSION]) + salt + hashlib.sha256(data).digest() + body


def decrypt(data):
    if len(data) <= HEADER_LENGTH or data[: len(MAGIC)] != MAGIC:
        raise ValueError("not a pyTrial package")
    if data[len(MAGIC)] != VERSION:
        raise ValueError("unsupported package version " + str(data[len(MAGIC)]))
    start = len(MAGIC) + 1
    salt = data[start : start + SALT_LENGTH]
    checksum = data[start + SALT_LENGTH : HEADER_LENGTH]
    plain = _xor(data[HEADER_LENGTH:], _keystream(salt, len(data) - HEADER_LENGTH))
    if hashlib.sha256(plain).digest() != checksum:
        raise ValueError("package checksum mismatch")
    return plain
