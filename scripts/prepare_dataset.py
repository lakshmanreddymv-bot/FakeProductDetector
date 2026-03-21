"""
prepare_dataset.py
==================
Scaffolds the dataset folder structure required to train a fake-product
detector using transfer learning (MobileNetV3 / EfficientNet).

Usage
-----
    python scripts/prepare_dataset.py [--verify]

    --verify   Check existing dataset and print per-class counts.

What this script does
---------------------
1. Creates the required directory tree under dataset/.
2. Writes a .gitkeep in every leaf folder so the structure is preserved
   in git without committing real images.
3. Prints step-by-step instructions telling you exactly which images to
   collect and where to put them.
4. Optionally verifies an existing dataset and warns if any class is below
   the 300-image minimum.
"""

import argparse
import os
import sys
from pathlib import Path

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

DATASET_ROOT = Path(__file__).parent.parent / "dataset"

SPLITS = ["train", "validation"]
CLASSES = ["authentic", "fake"]

# Recommended minimums for reliable transfer-learning fine-tuning
MIN_TRAIN_PER_CLASS = 300
MIN_VAL_PER_CLASS = 60          # ~20 % of train set
RECOMMENDED_TRAIN = 500
RECOMMENDED_VAL = 100

IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}

# ---------------------------------------------------------------------------
# Folder scaffold
# ---------------------------------------------------------------------------

STRUCTURE = {
    split: {cls: DATASET_ROOT / split / cls for cls in CLASSES}
    for split in SPLITS
}


def create_structure() -> None:
    print("\n📁  Creating dataset folder structure …\n")
    for split, classes in STRUCTURE.items():
        for cls, path in classes.items():
            path.mkdir(parents=True, exist_ok=True)
            gitkeep = path / ".gitkeep"
            gitkeep.touch(exist_ok=True)
            print(f"   ✓  {path.relative_to(DATASET_ROOT.parent)}")

    # Write a top-level .gitignore inside dataset/ so images are never
    # accidentally committed (they can be large binary files).
    gitignore = DATASET_ROOT / ".gitignore"
    if not gitignore.exists():
        gitignore.write_text(
            "# Ignore all images — only keep the folder skeleton\n"
            "*.jpg\n*.jpeg\n*.png\n*.webp\n*.bmp\n*.gif\n",
            encoding="utf-8",
        )
        print(f"\n   ✓  {gitignore.relative_to(DATASET_ROOT.parent)}  (prevents accidental image commits)")


# ---------------------------------------------------------------------------
# Image-count verification
# ---------------------------------------------------------------------------

def count_images(directory: Path) -> int:
    if not directory.exists():
        return 0
    return sum(
        1 for f in directory.iterdir()
        if f.suffix.lower() in IMAGE_EXTENSIONS
    )


def verify_dataset() -> bool:
    print("\n🔍  Verifying existing dataset …\n")
    all_ok = True

    minimums = {
        "train":      MIN_TRAIN_PER_CLASS,
        "validation": MIN_VAL_PER_CLASS,
    }

    for split, classes in STRUCTURE.items():
        for cls, path in classes.items():
            count = count_images(path)
            minimum = minimums[split]
            status = "✓" if count >= minimum else "✗"
            bar_len = min(count // 10, 50)
            bar = "█" * bar_len + "░" * (50 - bar_len)
            print(
                f"   {status}  {split:>10}/{cls:<10}  "
                f"{count:>4} images  [{bar}]  (min {minimum})"
            )
            if count < minimum:
                all_ok = False

    return all_ok


# ---------------------------------------------------------------------------
# Developer instructions
# ---------------------------------------------------------------------------

INSTRUCTIONS = """
╔══════════════════════════════════════════════════════════════════════════════╗
║           FakeProductDetector — Dataset Collection Instructions            ║
╚══════════════════════════════════════════════════════════════════════════════╝

The folder structure below has been created for you:

    dataset/
    ├── train/
    │   ├── authentic/    ← put real product images here   (min {min_train})
    │   └── fake/         ← put counterfeit images here    (min {min_train})
    └── validation/
        ├── authentic/    ← real products, held-out set    (min {min_val})
        └── fake/         ← counterfeits, held-out set     (min {min_val})

All images must be in JPG, JPEG, PNG, or WebP format.
Images are git-ignored (see dataset/.gitignore).

──────────────────────────────────────────────────────────────────────────────
STEP 1 — Collecting AUTHENTIC product images
──────────────────────────────────────────────────────────────────────────────
Goal: {min_train}–{rec_train} images per category (medicine, electronics,
      luxury goods, food) from verifiable official sources.

  Sources (in order of reliability):
  ┌─────────────────────────────────────────────────────────────────────────┐
  │ Source                    │ How to collect                              │
  ├─────────────────────────────────────────────────────────────────────────┤
  │ Brand official websites   │ Right-click → Save image (check ToS first) │
  │ Amazon "Sold by [Brand]"  │ Product photos from verified seller pages   │
  │ Open Images Dataset v7    │ https://storage.googleapis.com/openimages   │
  │                           │ Filter by label: "Product" + brand names   │
  │ Google Shopping           │ Limit to brand-verified listings            │
  │ Unsplash / Pexels         │ Search "product packaging" (CC0 licence)   │
  └─────────────────────────────────────────────────────────────────────────┘

  Quick Open Images download (requires `fiftyoneETA` or direct GCS access):

      pip install fiftyone
      python - <<'EOF'
      import fiftyone.zoo as foz
      ds = foz.load_zoo_dataset("open-images-v7", split="train",
                                label_types=["classifications"],
                                classes=["Product"])
      ds.export("dataset/train/authentic", dataset_type=fo.types.ImageDirectory)
      EOF

  Tips:
  • Vary lighting, angles, and backgrounds — diversity prevents overfitting.
  • Include close-ups of barcodes, labels, holograms, and serial numbers.
  • Aim for multiple product categories if your app is category-agnostic.

──────────────────────────────────────────────────────────────────────────────
STEP 2 — Collecting FAKE / COUNTERFEIT product images
──────────────────────────────────────────────────────────────────────────────
Goal: {min_train}–{rec_train} images (match the authentic count as closely
      as possible — class imbalance hurts accuracy significantly).

  ⚠  Ethical note: collect publicly visible listing images only. Do not
     purchase or promote counterfeit goods. Images are for research/defence
     purposes under fair-use provisions in most jurisdictions.

  Sources:
  ┌─────────────────────────────────────────────────────────────────────────┐
  │ Source                    │ Notes                                       │
  ├─────────────────────────────────────────────────────────────────────────┤
  │ AliExpress listings       │ Search known brand names at suspiciously    │
  │                           │ low prices; screenshot product photos       │
  │ DHgate / Wish             │ Similar approach to AliExpress              │
  │ Academic datasets         │ "Counterfeit Product Detection" on          │
  │                           │ Kaggle, Papers with Code                    │
  │ Consumer-protection orgs  │ Some publish example fake-product images    │
  │ INTA / USPTO resources    │ Anti-counterfeiting image libraries         │
  └─────────────────────────────────────────────────────────────────────────┘

  Visual signals to look for (label these images as "fake"):
  • Blurry or pixelated brand logos
  • Misspelled text on packaging
  • Incorrect font weight or spacing
  • Missing holograms or security seals
  • Low-resolution barcode / QR code
  • Colour deviation from official brand palette

──────────────────────────────────────────────────────────────────────────────
STEP 3 — Minimum dataset requirements
──────────────────────────────────────────────────────────────────────────────

  ┌────────────────┬──────────────┬──────────────────────────────────────┐
  │ Split          │ Per class    │ Notes                                │
  ├────────────────┼──────────────┼──────────────────────────────────────┤
  │ train/         │ {min_train}+ images │ Absolute minimum for transfer learning  │
  │ train/         │ {rec_train}+ images │ Recommended for production accuracy     │
  │ validation/    │ {min_val}+  images │ ~20 % of train set                     │
  └────────────────┴──────────────┴──────────────────────────────────────┘

  Total minimum:  {total_min} images  ({min_train} authentic + {min_train} fake + {min_val} + {min_val})
  Total recommended:  {total_rec} images

  Image requirements:
  • Format: JPG, JPEG, PNG, or WebP
  • Minimum resolution: 224 × 224 px  (MobileNetV3 input size)
  • Recommended: 512 × 512 px or larger (centre-crop is applied at runtime)
  • Balanced classes: ±10 % difference between authentic and fake counts

──────────────────────────────────────────────────────────────────────────────
STEP 4 — Verify your dataset
──────────────────────────────────────────────────────────────────────────────

  Run this script again with --verify to check image counts:

      python scripts/prepare_dataset.py --verify

──────────────────────────────────────────────────────────────────────────────
STEP 5 — Train the model
──────────────────────────────────────────────────────────────────────────────

  Once the dataset passes verification, run the training script:

      python scripts/train_model.py

  That script will:
  1. Load MobileNetV3-Large pre-trained on ImageNet (via torchvision or
     TensorFlow Hub).
  2. Replace the classifier head with a 2-class output (authentic / fake).
  3. Fine-tune for 20–50 epochs with early stopping.
  4. Export the model to TensorFlow Lite (.tflite) for on-device inference.
  5. Copy the .tflite file to app/src/main/assets/ ready for deployment.

  Dependencies:
      pip install torch torchvision tensorflow pillow tqdm fiftyone

──────────────────────────────────────────────────────────────────────────────
""".format(
    min_train=MIN_TRAIN_PER_CLASS,
    rec_train=RECOMMENDED_TRAIN,
    min_val=MIN_VAL_PER_CLASS,
    total_min=(MIN_TRAIN_PER_CLASS + MIN_VAL_PER_CLASS) * 2,
    total_rec=(RECOMMENDED_TRAIN + RECOMMENDED_VAL) * 2,
)


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Scaffold and verify the FakeProductDetector training dataset."
    )
    parser.add_argument(
        "--verify",
        action="store_true",
        help="Check image counts in an existing dataset and report missing images.",
    )
    args = parser.parse_args()

    if args.verify:
        ok = verify_dataset()
        if ok:
            print("\n✅  Dataset looks good — all classes meet minimum requirements.\n")
            print("    Next step: python scripts/train_model.py\n")
        else:
            print(
                "\n⚠   Some classes are below the minimum threshold.\n"
                "    See the instructions above for collection guidance.\n"
                "    Re-run without --verify to reprint full instructions.\n"
            )
        sys.exit(0 if ok else 1)

    # Default: scaffold + print instructions
    create_structure()
    print(INSTRUCTIONS)
    print(
        "📌  Tip: once images are added, run  "
        "python scripts/prepare_dataset.py --verify  to check counts.\n"
    )


if __name__ == "__main__":
    main()