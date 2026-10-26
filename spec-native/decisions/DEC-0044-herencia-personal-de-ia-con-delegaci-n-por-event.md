+++
doctype = "decision"
id = "DEC-0044"
title = "Herencia personal de IA con delegación por evento"
status = "accepted"
created_at = "2026-10-26"
owners = []
related_specs = ["SPEC-LPI"]
related_tasks = []
related_architecture = []
supersedes = ["DEC-0041"]
tags = []
+++

# DEC-0044 - Herencia personal de IA con delegación por evento

## Contexto

La resolución exclusiva por evento obliga a repetir credenciales. SPEC-LPI autoriza herencia dinámica y moderadores explícitamente delegados.

## Decisión

Cada herramienta selecciona INHERIT_OWNER, EVENT, MEMBER_PROFILE o DISABLED; fallback del vínculo inválido al dueño, nunca por fallo del proveedor. Credenciales personales cifradas aisladas del legado, sin fuente global; resolver autorización vigente en users por solicitud del gateway.

## Consecuencias

Sustituye el requisito event-only de llm-credential-isolation; requiere consulta interna fail-closed, auditoría y advertencias de consumo del titular.
