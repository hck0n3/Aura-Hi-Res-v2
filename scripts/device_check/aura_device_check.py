#!/usr/bin/env python3
"""Aura Hi-Res v2 — on-device check over adb (run it on the owner's computer, phone on USB).

Usage:
    python aura_device_check.py            # all automatic checks
    python aura_device_check.py --offline  # also the airplane-mode check (asks you to start a song first)

Needs Python 3.8+ and `adb` on PATH (Android platform-tools; on Windows: winget install Google.PlatformTools),
USB debugging on, and the phone authorized. It never taps a button that changes an account: it only opens
screens, reads what is on them and takes screenshots. Everything lands in a new folder aura_check_<date>/ with a
report (informe.md) to paste back to Claude.
"""

import argparse
import datetime
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "iad1tya.aura.music"
OUT = None
RESULTS = []  # (name, status, detail)
SERIAL = None


# ── adb ───────────────────────────────────────────────────────────────────────────────────────────

def adb(*args, check=False, binary=False, timeout=60):
    cmd = ["adb"] + (["-s", SERIAL] if SERIAL else []) + list(args)
    try:
        p = subprocess.run(cmd, capture_output=True, timeout=timeout)
    except FileNotFoundError:
        sys.exit("No encuentro «adb». Instala Android platform-tools (winget install Google.PlatformTools) y vuelve a abrir la terminal.")
    except subprocess.TimeoutExpired:
        return b"" if binary else ""
    if check and p.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} falló: {p.stderr.decode(errors='replace').strip()}")
    return p.stdout if binary else p.stdout.decode(errors="replace")


def shell(cmd, timeout=60):
    return adb("shell", cmd, timeout=timeout)


def screenshot(name):
    data = adb("exec-out", "screencap", "-p", binary=True, timeout=30)
    if data:
        path = os.path.join(OUT, f"{len(os.listdir(OUT)):02d}-{name}.png")
        with open(path, "wb") as f:
            f.write(data)
        return os.path.basename(path)
    return None


def record(name, status, detail=""):
    RESULTS.append((name, status, detail))
    icon = {"BIEN": "✅", "MAL": "❌", "AVISO": "⚠️", "SALTADA": "⏭️"}.get(status, "•")
    print(f"{icon} {name}: {status} {('— ' + detail) if detail else ''}")


# ── UI tree ───────────────────────────────────────────────────────────────────────────────────────

BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


class Node:
    def __init__(self, el, parent):
        self.el = el
        self.parent = parent
        self.text = el.get("text", "") or ""
        self.desc = el.get("content-desc", "") or ""
        self.selected = el.get("selected") == "true"
        self.clickable = el.get("clickable") == "true"
        m = BOUNDS.match(el.get("bounds", "") or "")
        self.bounds = tuple(int(x) for x in m.groups()) if m else (0, 0, 0, 0)
        self.children = []

    @property
    def center(self):
        l, t, r, b = self.bounds
        return (l + r) // 2, (t + b) // 2

    def walk(self):
        yield self
        for c in self.children:
            yield from c.walk()

    def label(self):
        return self.text or self.desc


def parse_ui(xml_text):
    """Parses a `uiautomator dump` file into a Node tree (also used by the unit test)."""
    xml_text = xml_text[xml_text.find("<"):] if "<" in xml_text else ""
    if not xml_text:
        return None
    try:
        root_el = ET.fromstring(xml_text)
    except ET.ParseError:
        return None

    def build(el, parent):
        n = Node(el, parent)
        for child in el:
            n.children.append(build(child, n))
        return n

    return build(root_el, None)


DUMP_COUNT = [0]


def dump(save_as=None):
    shell("uiautomator dump /sdcard/aura_ui.xml", timeout=30)
    xml_text = shell("cat /sdcard/aura_ui.xml", timeout=30)
    if save_as and OUT:
        DUMP_COUNT[0] += 1
        with open(os.path.join(OUT, f"ui-{DUMP_COUNT[0]:02d}-{save_as}.xml"), "w", encoding="utf-8") as f:
            f.write(xml_text)
    return parse_ui(xml_text)


def all_text(root):
    return [n.label() for n in root.walk() if n.label()] if root else []


def find(root, text=None, desc_contains=None, exact=True):
    if root is None:
        return None
    for n in root.walk():
        if text is not None:
            if (n.text == text) if exact else (text.lower() in n.text.lower()):
                return n
        if desc_contains is not None and desc_contains.lower() in n.desc.lower():
            return n
    return None


def clickable_ancestor(n):
    while n is not None and not n.clickable:
        n = n.parent
    return n


def tap(x, y):
    shell(f"input tap {x} {y}")
    time.sleep(1.2)


def tap_node(n):
    target = clickable_ancestor(n) or n
    tap(*target.center)


def screen_size():
    m = re.search(r"(\d+)x(\d+)", shell("wm size"))
    return (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)


def swipe_up():
    w, h = screen_size()
    shell(f"input swipe {w // 2} {h * 75 // 100} {w // 2} {h * 35 // 100} 300")
    time.sleep(1.0)


def find_scrolling(text, exact=True, tries=8):
    for _ in range(tries):
        root = dump()
        n = find(root, text=text, exact=exact)
        if n:
            return n, root
        swipe_up()
    return None, dump()


def back():
    shell("input keyevent 4")
    time.sleep(1.2)


def app_alive():
    return shell(f"pidof {PKG}").strip() != ""


def launch(cold=True):
    if cold:
        shell(f"am force-stop {PKG}")
        time.sleep(1)
    out = shell(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1")
    time.sleep(6)
    return out


# ── checks ────────────────────────────────────────────────────────────────────────────────────────

TABS = ["Inicio", "Buscar", "Biblioteca", "Novedades"]


def check_device():
    devices = [l for l in adb("devices").splitlines()[1:] if l.strip()]
    if not devices:
        sys.exit("No hay ningún celular conectado. Conecta el cable, activa la depuración USB y acepta el aviso del celular.")
    if any("unauthorized" in d for d in devices):
        sys.exit("El celular dice «unauthorized»: desbloquéalo y acepta el aviso «¿Permitir depuración USB?».")
    info = shell(f"dumpsys package {PKG}")
    m = re.search(r"versionName=(\S+)", info)
    if not m:
        sys.exit(f"No encuentro Aura ({PKG}) instalada en el celular.")
    model = shell("getprop ro.product.model").strip()
    sdk = shell("getprop ro.build.version.release").strip()
    record("Celular y versión", "BIEN", f"{model}, Android {sdk}, Aura {m.group(1)}, pantalla {layout_name()}")


def check_cold_start():
    shell("logcat -c")
    launch(cold=True)
    root = dump()
    shot = screenshot("inicio")
    if not app_alive():
        record("Arranque en frío", "MAL", "la app no quedó abierta")
        return
    terms = find(root, text="Aceptar y continuar")
    if terms:
        record("Arranque en frío", "AVISO", f"salió la pantalla de Términos (normal la primera vez tras actualizar). Acéptala a mano y vuelve a correr el script. Captura {shot}")
        return
    record("Arranque en frío", "BIEN", f"captura {shot}")


def tab_cell(root, label):
    """The bar/rail cell of a top-level tab: its label as text, or as content-desc on an icon-only rail."""
    if root is None:
        return None
    for n in root.walk():
        if n.text == label or n.desc == label or n.desc.startswith(label + ","):
            return clickable_ancestor(n) or n
    return None


def tab_selected(root, label):
    for n in root.walk() if root else []:
        if n.selected and any(c.text == label or c.desc == label or c.desc.startswith(label + ",") for c in n.walk()):
            return True
    return False


def layout_name():
    w, h = screen_size()
    return f"{w}x{h} ({'pantalla grande/abierto' if min(w, h) >= 1600 else 'teléfono'})"


def check_tabs():
    for label in TABS:
        root = dump()
        cell = tab_cell(root, label)
        if not cell:
            dump(save_as=f"pestana-{label}-no-encontrada")
            shot = screenshot(f"pestana-{label}-no-encontrada")
            record(f"Pestaña {label}", "MAL", f"no encuentro la pestaña ({layout_name()}) · captura {shot}")
            continue
        tap(*cell.center)
        time.sleep(1.5)
        root = dump()
        ok = tab_selected(root, label)
        shot = screenshot(f"pestana-{label}")
        record(f"Pestaña {label}", "BIEN" if ok else "MAL", ("" if ok else "no quedó seleccionada · ") + f"captura {shot}")
    # Edge taps (beta5 fix): 6 px inside each edge of each cell must still select that tab.
    bad = []
    tested = 0
    for label in TABS:
        for side in ("izq", "der"):
            root = dump()
            cell = tab_cell(root, label)
            if not cell:
                continue
            tested += 1
            l, t, r, b = cell.bounds
            x = l + 6 if side == "izq" else r - 6
            tap(x, (t + b) // 2)
            time.sleep(1.0)
            if not tab_selected(dump(), label):
                bad.append(f"{label} borde {side}")
    if tested == 0:
        record("Toques en el borde de las pestañas", "SALTADA", "no encontré las pestañas, así que no se probó")
    else:
        record("Toques en el borde de las pestañas", "BIEN" if not bad else "MAL",
               ", ".join(bad) if bad else f"los {tested} bordes probados abren su pestaña")


def open_settings():
    root = dump()
    cell = tab_cell(root, "Inicio")
    if cell:
        tap(*cell.center)
    root = dump()
    cog = find(root, desc_contains="Cuenta")
    if not cog:
        return False
    tap_node(cog)
    entry, _ = find_scrolling("Ajustes", tries=6)
    if not entry:
        return False
    tap_node(entry)
    time.sleep(1.5)
    return find(dump(), text="Apariencia", exact=False) is not None


def check_about():
    if not open_settings():
        record("Ajustes ▸ Acerca de", "MAL", "no pude abrir Ajustes")
        return
    entry, _ = find_scrolling("Acerca de", tries=10)
    if not entry:
        record("Ajustes ▸ Acerca de", "MAL", "no encuentro «Acerca de»")
        return
    tap_node(entry)
    texts = []
    for _ in range(8):
        texts += all_text(dump())
        swipe_up()
    joined = " ".join(texts)
    with open(os.path.join(OUT, "acerca-de-texto.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(dict.fromkeys(texts)))
    bad = [w for w in ("Qobuz", "Sin pérdida", "Saavn") if w.lower() in joined.lower()]
    devices_ok = "3 equipos" in joined
    shot = screenshot("acerca-de")
    if bad:
        record("Ajustes ▸ Acerca de", "MAL", f"todavía menciona {', '.join(bad)} · captura {shot}")
    else:
        record("Ajustes ▸ Acerca de", "BIEN", ("explica «hasta 3 equipos»" if devices_ok else "no encontré «3 equipos»") + f" · captura {shot}")
    back()


def check_settings_screen(path, final_check, name):
    """Opens Ajustes ▸ path[0] ▸ path[1]…, screenshots it and runs final_check(root) -> (status, detail)."""
    if not open_settings():
        dump(save_as="no-abre-ajustes")
        shot = screenshot("no-abre-ajustes")
        record(name, "MAL", f"no pude abrir Ajustes ({layout_name()}) · captura {shot}")
        return
    for step in path:
        entry, _ = find_scrolling(step, tries=10)
        if not entry:
            dump(save_as=f"no-encuentro-{step}")
            shot = screenshot(f"no-encuentro-{step}")
            record(name, "MAL", f"no encuentro «{step}» ({layout_name()}) · captura {shot}")
            for _ in path:
                back()
            return
        tap_node(entry)
        time.sleep(2.5)
    root = dump()
    shot = screenshot(name.replace(" ", "-").replace("▸", "").lower()[:40])
    status, detail = final_check(root)
    record(name, status, f"{detail} · captura {shot}")
    for _ in path:
        back()


def hidden_check(root):
    return ("BIEN", "se abre") if find(root, text="Ocultos", exact=False) else ("MAL", "no se ve la pantalla Ocultos")


def subs_check(root):
    time.sleep(8)  # the list loads from YouTube
    root = dump()
    texts = all_text(root)
    joined = " ".join(texts)
    if "incompleta" in joined.lower():
        return "AVISO", "dice «Lista incompleta» (YouTube no dejó leerla entera)"
    nums = [int(x) for x in re.findall(r"\b(\d{2,5})\b", joined)]
    if not texts:
        return "MAL", "la pantalla está vacía"
    return "BIEN", f"se abre y lista suscripciones (números visibles: {', '.join(map(str, nums[:5])) or 'ninguno'}). No se quitó nada"


def check_offline():
    input("\nPrueba de modo avión: pon una canción a sonar en Aura (de internet), deja visible el mini reproductor y pulsa Enter aquí... ")
    if not is_playing():
        record("Modo avión: aviso «Sin conexión»", "SALTADA", "no detecté música sonando antes de activar el modo avión")
        return
    shell("cmd connectivity airplane-mode enable")
    time.sleep(20)
    root = dump(save_as="modo-avion")
    seen = any("Sin conexión" in t for t in all_text(root))
    shot = screenshot("modo-avion")
    playing = is_playing()
    record("Modo avión: aviso «Sin conexión»", "BIEN" if seen else "MAL", f"{'se ve' if seen else 'no se ve'} el aviso · ¿suena? {'sí' if playing else 'no'} · captura {shot}")
    shell("cmd connectivity airplane-mode disable")
    time.sleep(25)
    root = dump()
    still = any("Sin conexión" in t for t in all_text(root))
    shot = screenshot("vuelve-la-red")
    record("Al volver la red se quita el aviso", "BIEN" if not still else "MAL", f"captura {shot}")


def is_playing():
    out = shell("dumpsys media_session")
    return bool(re.search(r"state=(3|PlaybackState\.STATE_PLAYING|PLAYING)", out))


def check_crashes():
    log = adb("logcat", "-d", "-b", "crash", timeout=60)
    main = adb("logcat", "-d", timeout=120)
    with open(os.path.join(OUT, "logcat.txt"), "w", encoding="utf-8") as f:
        f.write(main)
    crashes = [l for l in log.splitlines() if PKG in l]
    anrs = [l for l in main.splitlines() if "ANR in " + PKG in l]
    if crashes or anrs:
        record("Cierres y bloqueos (ANR)", "MAL", f"{len(crashes)} líneas de cierre, {len(anrs)} ANR (ver logcat.txt)")
    else:
        record("Cierres y bloqueos (ANR)", "BIEN", "ninguno durante la prueba")


def write_report():
    path = os.path.join(OUT, "informe.md")
    with open(path, "w", encoding="utf-8") as f:
        f.write(f"# Informe de pruebas de Aura en el celular — {datetime.datetime.now():%Y-%m-%d %H:%M}\n\n")
        for name, status, detail in RESULTS:
            f.write(f"- **{name}:** {status}{(' — ' + detail) if detail else ''}\n")
        f.write("\nCapturas y logcat.txt en esta misma carpeta.\n")
    return path


def main():
    global OUT, SERIAL
    ap = argparse.ArgumentParser(description="Pruebas de Aura Hi-Res v2 en el celular por adb")
    ap.add_argument("--offline", action="store_true", help="incluir la prueba de modo avión (interactiva)")
    ap.add_argument("--serial", help="número de serie del celular si hay varios conectados")
    args = ap.parse_args()
    SERIAL = args.serial
    OUT = f"aura_check_{datetime.datetime.now():%Y%m%d_%H%M%S}"
    os.makedirs(OUT, exist_ok=True)
    print(f"Guardando todo en {os.path.abspath(OUT)}\n")

    check_device()
    check_cold_start()
    if any(s == "AVISO" and n == "Arranque en frío" for n, s, _ in RESULTS):
        print("\nAcepta los Términos en el celular y vuelve a ejecutar el script.")
    else:
        steps = [
            check_tabs,
            check_about,
            lambda: check_settings_screen(["Contenido", "Ocultos"], hidden_check, "Ajustes ▸ Contenido ▸ Ocultos"),
            lambda: check_settings_screen(["Cuentas", "Revisar suscripciones"], subs_check, "Ajustes ▸ Cuentas ▸ Revisar suscripciones"),
        ]
        if args.offline:
            steps.append(check_offline)
        for step in steps:
            try:
                step()
            except Exception as e:  # one failing check must not stop the others
                record(getattr(step, "__name__", "prueba"), "MAL", f"el script falló: {e}")
            if not app_alive():
                record("App abierta", "MAL", "la app se cerró durante la prueba anterior")
                launch(cold=False)
    check_crashes()
    report = write_report()
    print(f"\nListo. Pega en Claude el contenido de:\n  {os.path.abspath(report)}")


if __name__ == "__main__":
    main()
