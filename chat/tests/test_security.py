"""Regresiones para las protecciones criptográficas del chat."""

from crypto import hash_password, verify_password


def test_passwords_use_scrypt_and_round_trip():
    encoded = hash_password("correct horse battery staple")

    assert encoded.startswith("$scrypt$")
    valid, needs_rehash = verify_password("correct horse battery staple", encoded)
    assert (valid, needs_rehash) == (True, False)


def test_wrong_password_is_rejected_without_rehash():
    encoded = hash_password("secret")

    assert verify_password("wrong", encoded) == (False, False)


def test_legacy_fernet_password_is_accepted_for_one_time_migration():
    from crypto import encrypt

    valid, needs_rehash = verify_password("legacy", encrypt("legacy"))

    assert (valid, needs_rehash) == (True, True)
