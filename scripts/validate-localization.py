"""Check Sable Life localization sources and optionally the packaged copies."""

import argparse
import json
import re
from collections import Counter
from pathlib import Path
from zipfile import BadZipFile, ZipFile


RESOURCES = Path(__file__).resolve().parents[1] / "src/main/resources"
LANGUAGES = ("en_us", "zh_cn", "ru_ru")
ASSETS = "assets/sablelife/"
MOJIBAKE = re.compile(
    r"[\u0080-\u009f\ufeff\ufffd]|\u00c2[\u00a0-\u00bf]|\u00c3[\u00a0-\u00bf]"
    r"|\u00e2\u20ac|\u00ef[\u00bb-\u00bd]"
)


def unique_object(pairs):
    entries = {}
    for key, value in pairs:
        if key in entries:
            raise ValueError(f"Duplicate translation key: {key}")
        entries[key] = value
    return entries


def read_language(path):
    entries = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_object)
    if not isinstance(entries, dict):
        raise ValueError("Expected an object of translation keys and strings")
    return entries


def format_arguments(text):
    arguments = Counter()
    implicit = 0
    for match in re.finditer(r"%%|%(?:(\d+)\$)?([sdf])", text):
        if match.group() == "%%":
            arguments[(0, "%")] += 1
        else:
            if match.group(1):
                index = int(match.group(1))
            else:
                implicit += 1
                index = implicit
            arguments[(index, match.group(2))] += 1
    return arguments


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, help="Check the exact JAR being delivered")
    args = parser.parse_args()

    errors = []
    locales = {}
    language_paths = {lang: f"{ASSETS}lang/{lang}.json" for lang in LANGUAGES}
    for lang, name in language_paths.items():
        try:
            entries = read_language(RESOURCES / name)
        except (OSError, ValueError) as error:
            errors.append(f"{name}: {error}")
            continue
        locales[lang] = entries
        for key, value in entries.items():
            if not isinstance(value, str):
                errors.append(f"{lang}: {key}: expected a string")
                continue
            match = MOJIBAKE.search(value)
            if match:
                errors.append(f"{lang}: {key}: possible mojibake {ascii(match.group())}")

    if len(locales) == len(LANGUAGES):
        en = locales["en_us"]
        for lang in LANGUAGES[1:]:
            translated = locales[lang]
            for key in sorted(en.keys() - translated.keys()):
                errors.append(f"{lang}: missing {key}")
            for key in sorted(translated.keys() - en.keys()):
                errors.append(f"{lang}: extra {key}")
            for key in sorted(en.keys() & translated.keys()):
                if isinstance(en[key], str) and isinstance(translated[key], str) and format_arguments(en[key]) != format_arguments(translated[key]):
                    errors.append(f"{lang}: {key}: format arguments differ from English")

    if args.jar:
        try:
            with ZipFile(args.jar) as archive:
                for name in (*language_paths.values(), f"{ASSETS}manual/catalog.json"):
                    if archive.namelist().count(name) != 1:
                        errors.append(f"{args.jar.name}: expected exactly one copy of {name}")
                    elif archive.read(name) != (RESOURCES / name).read_bytes():
                        errors.append(f"{args.jar.name}: {name} differs from source")
        except (OSError, BadZipFile) as error:
            errors.append(f"{args.jar.name}: {error}")

    if errors:
        parser.exit(1, "\n".join(errors) + "\n")
    print(f"Localization validated: {len(locales['en_us'])} keys in {', '.join(LANGUAGES)}.")
    if args.jar:
        print(f"{args.jar.name}: packaged localization and manual catalog match source.")


if __name__ == "__main__":
    main()
