#!/usr/bin/env python3
"""Builds the in-app "Novedades" changelog.json from RELEASE_INFO.md, or checks that it would show something.

Only `## ` titles and `- ` / `* ` bullets reach the app (prose does not). A bullet wrapped onto indented
continuation lines is ONE item: before 2026-10-06 only its first line was kept, so every wrapped bullet
reached the phone cut mid-sentence ("…junto al género es un artista, la").

Usage:
  release_changelog.py RELEASE_INFO.md OUT.json   # write changelog.json (release workflow)
  release_changelog.py --check RELEASE_INFO.md    # fail if the app would show no bullets (CI gate)
"""
import json
import sys


def parse(text):
    sections = []
    cur = None
    in_item = False
    for line in text.splitlines():
        s = line.strip()
        if s.startswith('## '):
            cur = {'title': s[3:].strip(), 'items': []}
            sections.append(cur)
            in_item = False
        elif s.startswith('# '):
            in_item = False
        elif s.startswith('- ') or s.startswith('* '):
            if cur is None:
                cur = {'title': '', 'items': []}
                sections.append(cur)
            cur['items'].append(s[2:].strip())
            in_item = True
        elif s and in_item and line[:1] in (' ', '\t'):
            cur['items'][-1] += ' ' + s
        else:
            in_item = False
    return sections


def main(argv):
    if len(argv) == 3 and argv[1] == '--check':
        sections = parse(open(argv[2], encoding='utf-8').read())
        bullets = sum(len(s['items']) for s in sections)
        empty = [s['title'] for s in sections if not s['items']]
        for title in empty:
            print(f"::warning::RELEASE_INFO.md section '{title}' has no bullets: the app will show its title only")
        if bullets == 0:
            print('::error::RELEASE_INFO.md has no "- " bullets: the in-app Novedades screen would be empty')
            return 1
        print(f'changelog check: {len(sections)} sections, {bullets} bullets')
        return 0
    if len(argv) == 3:
        sections = parse(open(argv[1], encoding='utf-8').read())
        out = {'description': '', 'image': '', 'warning': '', 'changelog': sections}
        with open(argv[2], 'w', encoding='utf-8') as f:
            f.write(json.dumps(out, ensure_ascii=False, indent=2))
        print('changelog.json sections:', len(sections))
        return 0
    print(__doc__)
    return 2


if __name__ == '__main__':
    sys.exit(main(sys.argv))
