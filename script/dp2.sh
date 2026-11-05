#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

if [[ ! -f "$PROJECT_DIR/requirements.txt" ]]; then
    printf 'Error: requirements.txt was not found in %s.\n' "$PROJECT_DIR" >&2
    exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
    printf 'Error: python3 is required. Install Python 3.8 or newer and retry.\n' >&2
    exit 1
fi

if command -v apt-get >/dev/null 2>&1; then
    system_packages=(python3-venv python3-dev python3-tk build-essential portaudio19-dev libsdl2-dev libsdl2-image-dev libsdl2-mixer-dev libsdl2-ttf-dev espeak)
    if [[ "$EUID" -eq 0 ]]; then
        apt-get update
        apt-get install -y "${system_packages[@]}"
    elif command -v sudo >/dev/null 2>&1; then
        sudo apt-get update
        sudo apt-get install -y "${system_packages[@]}"
    else
        printf 'Error: install system packages with root or sudo first: %s\n' "${system_packages[*]}" >&2
        exit 1
    fi
else
    printf 'Note: apt-get was not found; ensure your system has Python venv and PortAudio development packages installed.\n'
fi

python3 -m venv .venv
.venv/bin/python -m pip install --upgrade pip
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python -m pip check

printf '\nDependencies installed. Next:\n'
printf '  source .venv/bin/activate\n'
printf '  cp .env.example .env  # then add your GROQ_API_KEY\n'
printf '  python main.py\n'