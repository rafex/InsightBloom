#!/usr/bin/env python3
"""
cloud-display.py — Muestra una nube de palabras en la terminal.
Lee JSON de stdin, muestra barras proporcionales al score.

Uso:
  curl -s <endpoint> | python3 scripts/cloud-display.py
  curl -s <endpoint> | python3 scripts/cloud-display.py --field-word wordNormalized \
                         --field-score relevanceScore --field-count countTotal --top 20
"""
import sys
import json
import argparse

RESET   = '\033[0m'
BOLD    = '\033[1m'
GREEN   = '\033[92m'
YELLOW  = '\033[93m'
CYAN    = '\033[96m'
DIM     = '\033[2m'

def bar(score, max_score, width=28):
    if max_score <= 0:
        return '░' * width
    filled = int((score / max_score) * width)
    return '█' * filled + '░' * (width - filled)

def color_for(rank):
    if rank == 0:   return YELLOW + BOLD
    if rank < 3:    return GREEN
    if rank < 8:    return CYAN
    return DIM

def main():
    p = argparse.ArgumentParser()
    p.add_argument('--field-word',  default='wordNormalized')
    p.add_argument('--field-score', default='relevanceScore')
    p.add_argument('--field-count', default='messageCount')
    p.add_argument('--top',  type=int, default=20)
    p.add_argument('--bars', type=int, default=28)
    p.add_argument('--color', action='store_true', default=True)
    args = p.parse_args()

    try:
        data = json.load(sys.stdin)
    except json.JSONDecodeError:
        print("  (respuesta vacía o inválida)")
        return

    items = data.get('data', [])
    if not items:
        print("  (sin datos aún)")
        return

    items.sort(key=lambda x: float(x.get(args.field_score, 0) or 0), reverse=True)
    max_score = max((float(x.get(args.field_score, 0) or 0) for x in items), default=0)

    print(f"  {'Palabra':<22} {'Relevancia':<{args.bars+2}} {'Score':>8}  {'N':>5}")
    print(f"  {'─'*22} {'─'*(args.bars+2)} {'─'*8}  {'─'*5}")

    for i, item in enumerate(items[:args.top]):
        word  = str(item.get(args.field_word, '?'))[:21]
        score = float(item.get(args.field_score, 0) or 0)
        count = int(item.get(args.field_count, 0) or 0)
        b     = bar(score, max_score, args.bars)
        col   = color_for(i) if args.color else ''
        rst   = RESET if args.color else ''
        print(f"  {col}{word:<22}{rst} {col}{b}{rst} {score:>8.3f}  {count:>5}")

if __name__ == '__main__':
    main()
