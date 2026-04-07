#!/usr/bin/env python3
"""
Hytale Prefab Optimizer
=======================

Removes blocks that are completely surrounded (on all 6 axis-aligned sides) by
fully-opaque cube blocks. Such blocks are never visible to players, so deleting
them shrinks the prefab without changing what is rendered.

Safety rules
------------
1. Only blocks that are themselves fully-opaque cubes are eligible for removal.
   Markers, props, plants, decoration, fluids, etc. always stay (they may be
   load-bearing for gameplay even when hidden).
2. A neighbor only counts as "occluding" if it is itself a fully-opaque cube.
   Non-full blocks (stairs, half blocks, fences, walls, plants, ...) and any
   block flagged ``Opacity: Transparent`` (e.g. ``UnstableRifts_Room_Config``,
   ``UnstableRifts_Props_Crate_Level1``) are treated as see-through, exactly
   like empty space.
3. Block classification is driven by the mod's own item definitions when
   available. We scan ``Server/Item/Items/**/*.json`` for ``BlockType`` entries
   and consider a block full only when ``DrawType == "Cube"`` AND
   ``Opacity != "Transparent"``. For vanilla Hytale blocks (which we don't have
   definitions for) we fall back to a conservative name-based heuristic.

Usage
-----
    python tools/optimize_prefabs.py <folder> -o <out>            # one folder
    python tools/optimize_prefabs.py <folder> -o <out> -r         # recurse
    python tools/optimize_prefabs.py <folder> -r --dry-run        # report only
    python tools/optimize_prefabs.py <folder> -o <out> --item-root <p>

The script never overwrites the input prefabs. Optimized files are written
under ``--output``, mirroring each prefab's path relative to the input folder.
Only files that actually had blocks removed are written, so the output folder
contains exactly what would change.

By default the script discovers item definitions next to the prefabs (it walks
upward from each prefab looking for a ``Server/Item/Items`` directory). You can
also pass one or more ``--item-root`` paths.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Set, Tuple


# ---------------------------------------------------------------------------
# Block classification
# ---------------------------------------------------------------------------

# Six face neighbor offsets (x, y, z).
NEIGHBOR_OFFSETS: Tuple[Tuple[int, int, int], ...] = (
    (1, 0, 0), (-1, 0, 0),
    (0, 1, 0), (0, -1, 0),
    (0, 0, 1), (0, 0, -1),
)

# Names that are always treated as non-full / see-through, regardless of any
# item definition. These are markers / editor helpers from vanilla Hytale and
# from the UnstableRifts mod.
ALWAYS_NON_FULL_EXACT: Set[str] = {
    "Empty",
    "Editor_Anchor",
    "Barrier",
    "Prefab_Spawner_Block",
    # UnstableRifts mod markers (see dungeon/MarkerType.java)
    "UnstableRifts_Mob_Spawn_Point",
    "UnstableRifts_Mob_Spawner",
    "UnstableRifts_Portal",
    "UnstableRifts_Portal_Exit",
    "UnstableRifts_Door",
    "UnstableRifts_Room_Config",
    "UnstableRifts_Activation_Zone",
    "UnstableRifts_Key_Spawner",
    "UnstableRifts_Water",
    "UnstableRifts_Tar",
    "UnstableRifts_Poison",
    "UnstableRifts_Lava",
    "UnstableRifts_Slime",
    "UnstableRifts_Red_Slime",
    "UnstableRifts_Shop_Keeper",
    "UnstableRifts_Shop_Item",
}

# Substrings that, when present in a vanilla block name, mean the block is
# *not* a full opaque cube. The check is intentionally conservative: when in
# doubt, we keep the block.
NON_FULL_SUBSTRINGS: Tuple[str, ...] = (
    "_Stairs",
    "_Half",
    "_Wall",
    "_Beam",
    "_Fence",
    "_Roof",
    "_Door",
    "_Trapdoor",
    "_Window",
    "_Stalactite",
    "_Branch",
    "_Roots",
    "_Flap",
    "_Ladder",
    "_Sign",
    "_Bars",
    "_Chain",
    "_Rope",
    "_Pile",
    "_Vertical",
    "_Pillar",
    "_Bench",
    "_Stool",
    "_Table",
    "_Shelf",
    "_Chest",
    "_Pot",
    "_Candle",
    "_Lantern",
    "_Torch",
    "_Brazier",
    "_Bed",
    "_Wardrobe",
    "_Statue",
    "_Plush",
    "_Plank_Half",
    "_Crate",
)

# Whole-name prefixes whose blocks are never full occluders.
NON_FULL_PREFIXES: Tuple[str, ...] = (
    "*",                  # state-definition variants (e.g. *Foo_Stairs_Corner_Left)
    "Plant_",             # plants are mostly transparent / non-cube
    "Deco_",              # decoration props
    "Furniture_",         # furniture
    "Container_",         # buckets, etc.
    "Vehicle_",           # boats, carts, ...
    "Potion_",            # bottles
    "Rubble_",            # piles of rubble
    "UnstableRifts_",     # any UnstableRifts custom block (markers/props)
)

# Plant/crop blocks that ARE actually full cubes despite the Plant_ prefix.
# Listed explicitly so the prefix rule doesn't reject them.
PLANT_FULL_BLOCK_EXACT: Set[str] = {
    "Plant_Moss_Block_Green",
    "Plant_Moss_Block_Green_Dark",
    "Plant_Crop_Mushroom_Block_Brown",
    "Plant_Crop_Mushroom_Block_Green",
}


def _vanilla_is_full_block(name: str) -> bool:
    """Heuristic full-block check for blocks with no item definition."""
    if not name:
        return False
    if name in ALWAYS_NON_FULL_EXACT:
        return False
    if name in PLANT_FULL_BLOCK_EXACT:
        return True
    for prefix in NON_FULL_PREFIXES:
        if name.startswith(prefix):
            return False
    for needle in NON_FULL_SUBSTRINGS:
        if needle in name:
            return False
    # Wood trunks: only the *_Trunk_Full variants are full cubes.
    if "_Trunk" in name and not name.endswith("_Trunk_Full"):
        return False
    return True


class BlockClassifier:
    """Decides whether a block name corresponds to a fully-opaque cube.

    The classifier consults item definitions discovered on disk first, then
    falls back to the name-based heuristic above.
    """

    def __init__(self) -> None:
        # name -> True (full cube) / False (not full)
        self._defs: Dict[str, bool] = {}
        # cache of resolved decisions (definition + heuristic combined)
        self._cache: Dict[str, bool] = {}
        self._scanned_roots: Set[Path] = set()

    # -- Item definition loading -------------------------------------------------

    def load_item_root(self, root: Path) -> int:
        """Scan ``root`` recursively for item JSONs that contain a BlockType.

        Returns the number of new block definitions registered.
        """
        root = root.resolve()
        if root in self._scanned_roots or not root.exists():
            return 0
        self._scanned_roots.add(root)

        added = 0
        for path in root.rglob("*.json"):
            try:
                with path.open("r", encoding="utf-8") as fh:
                    data = json.load(fh)
            except (OSError, json.JSONDecodeError):
                continue
            if not isinstance(data, dict):
                continue
            block_type = data.get("BlockType")
            if not isinstance(block_type, dict):
                continue

            # The in-game block id is the file stem (e.g.
            # ``UnstableRifts_Props_Crate_Level1.json`` -> the same name).
            block_name = path.stem
            draw_type = block_type.get("DrawType")
            opacity = block_type.get("Opacity")
            is_full = (draw_type == "Cube") and (opacity != "Transparent")
            # Don't override an earlier definition with a contradictory one.
            if block_name not in self._defs:
                self._defs[block_name] = is_full
                self._cache.pop(block_name, None)
                added += 1
        return added

    def discover_item_roots_from_prefab(self, prefab_path: Path) -> None:
        """Walk upward from a prefab and load any ``Item/Items`` directory."""
        for parent in prefab_path.resolve().parents:
            candidate = parent / "Server" / "Item" / "Items"
            if candidate.is_dir():
                self.load_item_root(candidate)
                # Don't break — there could be both ``src`` and ``target`` trees.

    # -- Classification ----------------------------------------------------------

    def is_full_block(self, name: Optional[str]) -> bool:
        if not name:
            return False
        cached = self._cache.get(name)
        if cached is not None:
            return cached

        # Names that should always be treated as see-through win, even if a
        # definition exists with DrawType=Cube (markers like Room_Config).
        if name in ALWAYS_NON_FULL_EXACT:
            self._cache[name] = False
            return False

        if name in self._defs:
            decision = self._defs[name]
        else:
            decision = _vanilla_is_full_block(name)
        self._cache[name] = decision
        return decision

    def known_definitions(self) -> int:
        return len(self._defs)


# ---------------------------------------------------------------------------
# Prefab optimization
# ---------------------------------------------------------------------------

def _block_key(block: dict) -> Tuple[int, int, int]:
    return (int(block["x"]), int(block["y"]), int(block["z"]))


def optimize_prefab_blocks(
    blocks: List[dict],
    classifier: BlockClassifier,
) -> Tuple[List[dict], int]:
    """Return ``(new_blocks, removed_count)``.

    A block is removed when:
      * it is itself a fully-opaque cube, AND
      * each of its 6 axis-aligned neighbors exists in the prefab AND is also
        a fully-opaque cube.
    """
    # Build a position -> block-name index in a single pass.
    position_to_name: Dict[Tuple[int, int, int], str] = {}
    for block in blocks:
        if not isinstance(block, dict):
            continue
        if "x" not in block or "y" not in block or "z" not in block:
            continue
        position_to_name[_block_key(block)] = block.get("name", "")

    kept: List[dict] = []
    removed = 0
    for block in blocks:
        if not isinstance(block, dict) or "x" not in block:
            kept.append(block)
            continue

        name = block.get("name", "")
        if not classifier.is_full_block(name):
            kept.append(block)
            continue

        x, y, z = _block_key(block)
        surrounded = True
        for dx, dy, dz in NEIGHBOR_OFFSETS:
            neighbor_name = position_to_name.get((x + dx, y + dy, z + dz))
            if neighbor_name is None or not classifier.is_full_block(neighbor_name):
                surrounded = False
                break

        if surrounded:
            removed += 1
        else:
            kept.append(block)

    return kept, removed


def optimize_prefab_file(
    path: Path,
    classifier: BlockClassifier,
    *,
    output_path: Optional[Path] = None,
) -> Tuple[int, int]:
    """Optimize a single ``.prefab.json`` file.

    The input file is **never** modified. When ``output_path`` is provided and
    at least one block was removed, the optimized prefab is written there;
    parent directories are created as needed.

    Returns ``(original_block_count, removed_count)``.
    """
    classifier.discover_item_roots_from_prefab(path)

    with path.open("r", encoding="utf-8") as fh:
        data = json.load(fh)

    blocks = data.get("blocks")
    if not isinstance(blocks, list):
        return (0, 0)

    original_count = len(blocks)
    new_blocks, removed = optimize_prefab_blocks(blocks, classifier)

    if removed and output_path is not None:
        data["blocks"] = new_blocks
        output_path.parent.mkdir(parents=True, exist_ok=True)
        with output_path.open("w", encoding="utf-8") as fh:
            json.dump(data, fh, indent=2)
            fh.write("\n")

    return (original_count, removed)


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

PREFAB_FILENAME_RE = re.compile(r".*\.prefab\.json$", re.IGNORECASE)


def iter_prefab_files(folder: Path, recursive: bool) -> Iterable[Path]:
    if not folder.exists():
        return
    if recursive:
        for path in folder.rglob("*.prefab.json"):
            if path.is_file():
                yield path
    else:
        for path in folder.iterdir():
            if path.is_file() and PREFAB_FILENAME_RE.match(path.name):
                yield path


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(
        description="Remove fully-occluded blocks from Hytale .prefab.json files.",
    )
    parser.add_argument(
        "folder",
        type=Path,
        help="Folder containing .prefab.json files to optimize.",
    )
    parser.add_argument(
        "-o", "--output",
        type=Path,
        default=None,
        help=(
            "Destination folder for optimized prefabs. The input directory "
            "structure is mirrored under this folder, and only files that "
            "actually had blocks removed are written. The original prefabs "
            "are never modified. Required unless --dry-run is set."
        ),
    )
    parser.add_argument(
        "-r", "--recursive",
        action="store_true",
        help="Recurse into subdirectories.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Report what would be removed without writing any file.",
    )
    parser.add_argument(
        "--item-root",
        type=Path,
        action="append",
        default=[],
        help=(
            "Extra Server/Item/Items directory to load block definitions from. "
            "Can be passed multiple times. Auto-discovery from each prefab's "
            "parents always runs as well."
        ),
    )
    args = parser.parse_args(argv)

    if not args.folder.exists():
        print(f"error: folder not found: {args.folder}", file=sys.stderr)
        return 2

    if args.output is None and not args.dry_run:
        print(
            "error: --output is required (or pass --dry-run to only report).",
            file=sys.stderr,
        )
        return 2

    folder = args.folder.resolve()
    output_root: Optional[Path] = args.output.resolve() if args.output else None
    if output_root is not None:
        try:
            if output_root == folder or folder in output_root.parents:
                print(
                    "error: --output must not point inside the input folder.",
                    file=sys.stderr,
                )
                return 2
        except OSError:
            pass

    classifier = BlockClassifier()
    for root in args.item_root:
        added = classifier.load_item_root(root)
        print(f"Loaded {added} block definitions from {root}")

    files = sorted(iter_prefab_files(folder, args.recursive))
    if not files:
        print(f"No .prefab.json files found in {folder}"
              f"{' (recursive)' if args.recursive else ''}.")
        return 0

    total_blocks = 0
    total_removed = 0
    modified_files = 0

    for path in files:
        try:
            relative = path.relative_to(folder)
        except ValueError:
            relative = Path(path.name)

        out_path: Optional[Path] = None
        if output_root is not None and not args.dry_run:
            out_path = output_root / relative

        try:
            original, removed = optimize_prefab_file(
                path,
                classifier,
                output_path=out_path,
            )
        except (OSError, json.JSONDecodeError) as exc:
            print(f"  ! {path}: {exc}", file=sys.stderr)
            continue

        total_blocks += original
        total_removed += removed
        if removed:
            modified_files += 1
            pct = (removed / original * 100.0) if original else 0.0
            if args.dry_run:
                verb = "would remove"
            else:
                verb = "wrote, removed"
            print(f"  - {relative}: {verb} {removed}/{original} blocks ({pct:.1f}%)")

    print()
    print(f"Item definitions known: {classifier.known_definitions()}")
    print(f"Files scanned:          {len(files)}")
    suffix = ""
    if args.dry_run:
        suffix = " (dry-run)"
    elif output_root is not None:
        suffix = f" -> {output_root}"
    print(f"Files written:          {modified_files}{suffix}")
    if total_blocks:
        pct = total_removed / total_blocks * 100.0
        print(f"Blocks removed:         {total_removed}/{total_blocks} ({pct:.1f}%)")
    else:
        print("Blocks removed:         0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
