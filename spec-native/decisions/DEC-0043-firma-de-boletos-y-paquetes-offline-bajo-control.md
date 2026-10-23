+++
doctype = "decision"
id = "DEC-0043"
title = "Firma de boletos y paquetes offline bajo control del organizador"
status = "accepted"
created_at = "2026-10-23"
owners = ["product owner"]
related_specs = ["SPEC-TICKET-ORG-SIGN-0001"]
related_tasks = []
related_architecture = []
supersedes = []
tags = ["tickets", "offline", "cryptography", "security"]
+++

# DEC-0043 - Firma de boletos y paquetes offline bajo control del organizador

## Contexto

El check-in offline usa actualmente una clave privada global de la plataforma para firmar paquetes; los boletos cuentan con QR rotativo firmado por el dispositivo vinculado, pero no tienen un sello verificable del organizador.

## Decisión

Cada evento tendrá un par Ed25519/COSE_Sign1 controlado por su organizador. La clave privada se genera, conserva y usa localmente en el cliente firmante; InsightBloom almacena solo claves públicas, firmas de boleto y paquetes firmados. El backend no podrá firmar por fallback. El boleto se firma al emitirse y el organizador firma además el snapshot offline de vigencia/revocación y vínculos de dispositivo. El check-in conserva la prueba P-256 del dispositivo y el comportamiento conectado atómico.

## Consecuencias

La emisión de boletos protegidos requiere que el firmante del organizador esté disponible online hasta vaciar pendientes. Perder la privada impide emitir y actualizar paquetes, por lo que el dueño necesita un respaldo cifrado bajo su control. Lectores offline verifican con la clave pública/evento preconfiada y el paquete vigente; no pueden conocer revocaciones posteriores ni evitar duplicados entre lectores aislados. El WASM no se considera una frontera de seguridad: la operación de firma usará Web Crypto con una clave no exportable.
