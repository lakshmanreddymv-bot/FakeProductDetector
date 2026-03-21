# FakeProductDetector — ML Training Scripts

This folder contains Python scripts for building and training the on-device
fake-product detection model that powers the Android app's local inference.

---

## Prerequisites

```bash
pip install torch torchvision tensorflow pillow tqdm fiftyone
```

Python 3.9+ is recommended.

---

## Step 1 — Scaffold the dataset folders

```bash
python scripts/prepare_dataset.py
```

This creates:

```
dataset/
├── train/
│   ├── authentic/    ← real product images   (min 300)
│   └── fake/         ← counterfeit images    (min 300)
└── validation/
    ├── authentic/    ← held-out real images   (min 60)
    └── fake/         ← held-out fakes         (min 60)
```

Images are git-ignored (`dataset/.gitignore`) so large binary files are
never committed accidentally.

---

## Step 2 — Collect authentic product images

**Goal:** 300–500 images per class minimum. More is always better.

### Recommended sources (authentic)

| Source | Method |
|--------|--------|
| Official brand websites | Download product photos directly (check ToS) |
| Amazon — verified sellers | Right-click product images from "Sold by [Brand]" listings |
| [Open Images Dataset v7](https://storage.googleapis.com/openimages/web/index.html) | Filter by product-related labels; CC-BY 4.0 licence |
| [Unsplash](https://unsplash.com) / [Pexels](https://www.pexels.com) | Search "product packaging"; CC0 licence |
| Google Shopping | Limit to brand-verified listings |

### Open Images quick download

```python
import fiftyone.zoo as foz, fiftyone as fo

ds = foz.load_zoo_dataset(
    "open-images-v7",
    split="train",
    label_types=["classifications"],
    classes=["Product"],
    max_samples=600,
)
ds.export(
    export_dir="dataset/train/authentic",
    dataset_type=fo.types.ImageDirectory,
)
```

### What to photograph (if collecting manually)

- Multiple angles of genuine packaged products
- Close-ups of barcodes, holograms, and security seals
- Brand logos and label typography under good lighting
- Receipts / purchase confirmation are not needed — images only

---

## Step 3 — Collect fake / counterfeit product images

**Goal:** Match the authentic count as closely as possible (±10 %).
Class imbalance significantly hurts binary classifier accuracy.

> ⚠ **Ethical guidance:** collect only publicly visible listing images.
> Do not purchase counterfeit goods. Images are used solely for building
> a *detection* system — a defensive, consumer-protection purpose.

### Recommended sources (fake)

| Source | Notes |
|--------|-------|
| AliExpress | Search genuine brand names at unusually low prices; screenshot product photos |
| DHgate / Wish | Same approach |
| Kaggle datasets | Search "counterfeit product detection" |
| [Papers with Code](https://paperswithcode.com) | Academic fake-product image datasets |
| Consumer-protection organisations | Some publish example counterfeit images for education |

### Visual signals that indicate a fake (useful for labelling)

- Blurry or pixelated brand logo
- Misspelled text on packaging
- Incorrect font weight, spacing, or colour
- Missing holographic security seal
- Low-resolution or misaligned barcode / QR code
- Colour that deviates from the official brand palette
- Inconsistent label alignment or border widths

---

## Step 4 — Minimum dataset requirements

| Split | Per class | Notes |
|-------|-----------|-------|
| `train/` | **300** minimum | Absolute floor for transfer learning |
| `train/` | **500** recommended | Better generalisation |
| `validation/` | **60** minimum | ~20 % of train set |
| `validation/` | **100** recommended | More reliable metrics |

**Total minimum:** 720 images (300 + 300 + 60 + 60)
**Total recommended:** 1 200 images

### Image requirements

- **Format:** JPG, JPEG, PNG, or WebP
- **Minimum resolution:** 224 × 224 px (MobileNetV3 input size)
- **Recommended:** 512 × 512 px or larger (centre-crop applied at training time)
- Vary lighting conditions, backgrounds, and angles

---

## Step 5 — Verify the dataset

```bash
python scripts/prepare_dataset.py --verify
```

This prints a per-class image count with a progress bar and exits with
code `0` if all classes meet the minimum, or `1` if any fall short.

---

## Step 6 — Train the model

```bash
python scripts/train_model.py
```

The training script (to be added) will:

1. Load **MobileNetV3-Large** pre-trained on ImageNet
2. Replace the classifier head with a 2-class output (`authentic` / `fake`)
3. Apply data augmentation (random flip, colour jitter, rotation)
4. Fine-tune for 20–50 epochs with early stopping on validation loss
5. Export to **TensorFlow Lite** (`.tflite`) for on-device inference
6. Copy the `.tflite` file to `app/src/main/assets/` automatically

### Expected training time

| Hardware | Time per epoch (600 images) |
|----------|-----------------------------|
| CPU only | ~8–15 min |
| GPU (CUDA / MPS) | ~30–90 sec |

---

## Folder structure reference

```
FakeProductDetector/
├── app/
│   └── src/main/assets/
│       └── fake_product_model.tflite   ← output of train_model.py
├── dataset/
│   ├── .gitignore                      ← images are excluded from git
│   ├── train/
│   │   ├── authentic/
│   │   └── fake/
│   └── validation/
│       ├── authentic/
│       └── fake/
└── scripts/
    ├── README.md                       ← you are here
    ├── prepare_dataset.py              ← scaffold + verify
    └── train_model.py                  ← (coming soon)
```

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `fiftyone` install fails | Try `pip install fiftyone --no-deps` then install deps manually |
| Open Images download is slow | Use `max_samples=` to limit; or download directly from GCS bucket |
| Model accuracy below 80 % | Add more images, especially for the under-represented class |
| `.tflite` file too large | Use `quantize=True` in the export step (reduces size by ~4×) |