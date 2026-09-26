#!/bin/sh
# Fase 7 (2026-08): bloqueo real de egress de un sandbox, aplicado por el initContainer del Pod
# (ver KubernetesPodClient.buildInitContainer) -- corre UNA sola vez, con CAP_NET_ADMIN, ANTES de
# que arranque el contenedor principal. El netns del Pod es compartido entre initContainer y
# contenedor principal, y persiste para toda la vida del Pod aunque el contenedor principal
# reinicie -- el alumno nunca tiene CAP_NET_ADMIN, así que no puede deshacer estas reglas.
#
# Deliberadamente simple y ESTATICO: no conoce dominios ni el toggle "internet habilitado" de la
# conferencia -- esa lógica dinámica (whitelist/blacklist, TTL corto) sigue viviendo enteramente
# en insightbloom-egress-proxy/ResolveEgressPolicyUseCase. La única pregunta que responde este
# script es "¿este destino es interno al cluster, o es internet real?" -- lo interno se permite
# siempre (DNS, el proxy de egress, la API de insightbloom-users, y cualquier otro Pod/Service del
# cluster -- la restricción de "solo el mismo evento" para Pod-a-Pod la aplica la NetworkPolicy de
# ingress del Pod RECEPTOR, no este script). La salida web TCP/UDP externa va por el proxy; la
# unica excepcion directa es ICMP echo-request a IPs publicas para diagnostico.
#
# Antes de esto: sin ninguna NetworkPolicy de Egress seleccionando el Pod, Kubernetes permitía
# TODO el tráfico saliente por defecto -- el toggle "Permitir acceso a internet" y las listas
# blanca/negra de dominios no tenían ningún efecto real (bug reportado 2026-08-12).

set -eu

CLUSTER_CIDR="${SANDBOX_CLUSTER_CIDR:-10.0.0.0/8}"
DNS_SERVERS="$(awk '/^nameserver/ { print $2 }' /etc/resolv.conf 2>/dev/null | tr '\n' ' ')"
NFT_BIN="${NFT_BIN:-$(command -v nft || true)}"
if [ -z "$NFT_BIN" ]; then
    for candidate in /sbin/nft /usr/sbin/nft; do
        if [ -x "$candidate" ]; then
            NFT_BIN="$candidate"
            break
        fi
    done
fi
if [ -z "$NFT_BIN" ] || [ ! -x "$NFT_BIN" ]; then
    echo "lockdown-egress: nft executable not found" >&2
    exit 1
fi
nft() { "$NFT_BIN" "$@"; }

nft add table inet sandboxfw
nft add chain inet sandboxfw output '{ type filter hook output priority 0; policy drop; }'
nft add rule inet sandboxfw output oif lo accept
nft add rule inet sandboxfw output ct state established,related accept
for ip in $DNS_SERVERS; do
    nft add rule inet sandboxfw output ip daddr "$ip" udp dport 53 accept
    nft add rule inet sandboxfw output ip daddr "$ip" tcp dport 53 accept
done
nft add rule inet sandboxfw output ip daddr "$CLUSTER_CIDR" accept
# Kubernetes NetworkPolicy has no portable ICMP protocol selector. A companion policy permits
# L3 egress only for sandbox Pods; this firewall remains the protocol-level guard and allows only
# IPv4 echo requests to globally routable destinations. TCP/UDP direct egress still goes only to
# cluster services (including the controlled HTTP proxy) or DNS.
nft add rule inet sandboxfw output ip protocol icmp icmp type echo-request \
    ip daddr != { 0.0.0.0/8, 10.0.0.0/8, 100.64.0.0/10, 127.0.0.0/8, \
                  169.254.0.0/16, 172.16.0.0/12, 192.0.0.0/24, \
                  192.0.2.0/24, 192.88.99.0/24, 192.168.0.0/16, \
                  198.18.0.0/15, 198.51.100.0/24, 203.0.113.0/24, \
                  224.0.0.0/4, 240.0.0.0/4 } accept

echo "lockdown-egress: reglas aplicadas (cluster_cidr=$CLUSTER_CIDR, dns=$DNS_SERVERS)" >&2
nft list ruleset >&2
