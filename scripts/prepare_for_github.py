#!/usr/bin/env python3
"""
scripts/prepare_for_github.py
----------------------------------------------------------------------
اسکریپت مستقلی که قبل از هر بار push کردن این پروژه (اپ اندروید) به
گیت‌هاب اجرا می‌کنید. کاری که انجام می‌دهد:

  1. بررسی می‌کند .gitignore وجود دارد و پرونده‌های حساس (.env، *.jks،
     local.properties و ...) را پوشش می‌دهد.
  2. تمام فایل‌هایی که قرار است commit شوند (یعنی توسط .gitignore کنار
     گذاشته نشده‌اند) را برای الگوهای رمز/کلید واقعی (JWT، sbp_...،
     cfut_...، و مقادیر مشکوک دیگر) اسکن می‌کند.
  3. اگر چیزی مشکوک پیدا شود، متوقف می‌شود و چیزی commit/stage نمی‌کند.
  4. اگر همه‌چیز تمیز بود، ریپازیتوری گیت را (در صورت نیاز) می‌سازد،
     فایل‌ها را stage می‌کند، و توضیح می‌دهد که خودتان باید remote را
     اضافه کرده و push کنید — این اسکریپت خودش push نمی‌کند.

اجرا:
  python3 scripts/prepare_for_github.py
----------------------------------------------------------------------
"""

import re
import subprocess
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent

REQUIRED_GITIGNORE_PATTERNS = [".env", "*.jks", "local.properties"]

# الگوهای رمز/کلید واقعی که هرگز نباید توی هیچ فایل commit‌شده‌ای باشند.
SECRET_PATTERNS = [
    ("Supabase JWT (anon/service key)", re.compile(r"eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}")),
    ("Supabase personal access token", re.compile(r"sbp_[a-f0-9]{20,}")),
    ("Cloudflare API token", re.compile(r"cfut_[A-Za-z0-9]{20,}")),
    ("رمز عبور دیتابیس/کلید در قالب KEY=long-random-string", re.compile(
        r"(?i)(DRIVER_API_KEY|STORE_PASSWORD|KEY_PASSWORD|DB_PASSWORD|SECRET|API_KEY)\s*[=:]\s*[\"']?[A-Za-z0-9_\-]{16,}[\"']?"
    )),
]

# این فایل‌ها هرگز نباید commit بشن، صرف‌نظر از محتواشون.
FORBIDDEN_FILENAMES = {".env", "my-upload-key.jks", "debug.keystore"}
FORBIDDEN_SUFFIXES = {".jks", ".keystore", ".p12"}


def run(cmd: list[str]) -> str:
    result = subprocess.run(cmd, cwd=PROJECT_ROOT, capture_output=True, text=True)
    return result.stdout.strip()


def ensure_git_repo() -> None:
    if not (PROJECT_ROOT / ".git").exists():
        print("📦 ریپازیتوری گیت پیدا نشد — می‌سازم (git init)...")
        subprocess.run(["git", "init"], cwd=PROJECT_ROOT, check=True)


def check_gitignore() -> list[str]:
    problems = []
    gitignore_path = PROJECT_ROOT / ".gitignore"
    if not gitignore_path.exists():
        return ["فایل .gitignore وجود ندارد."]
    content = gitignore_path.read_text(encoding="utf-8")
    for pattern in REQUIRED_GITIGNORE_PATTERNS:
        if pattern not in content:
            problems.append(f".gitignore الگوی «{pattern}» را پوشش نمی‌دهد.")
    return problems


def list_would_be_tracked_files() -> list[Path]:
    """فایل‌هایی که با git add . واقعاً stage می‌شوند (یعنی .gitignore
    کنارشون نگذاشته). از خود گیت برای تشخیص دقیق استفاده می‌کنیم تا
    قوانین پیچیده‌ی gitignore (negation و غیره) درست رعایت بشه."""
    output = run(["git", "status", "--porcelain", "--ignored=no", "-uall"])
    files = []
    for line in output.splitlines():
        if not line.strip():
            continue
        # فرمت: "XY path" یا "XY path -> newpath" برای rename
        path_part = line[3:].split(" -> ")[-1].strip().strip('"')
        candidate = PROJECT_ROOT / path_part
        if candidate.is_file():
            files.append(candidate)
    return files


SKIP_GENERIC_HEURISTIC_FOR = re.compile(r"\.(example|sample|template)$", re.IGNORECASE)


def scan_files_for_secrets(files: list[Path]) -> list[str]:
    problems = []
    for f in files:
        rel = f.relative_to(PROJECT_ROOT)

        if f.name in FORBIDDEN_FILENAMES or f.suffix in FORBIDDEN_SUFFIXES:
            problems.append(f"فایل حساس در حال commit شدن است: {rel}")
            continue

        try:
            text = f.read_text(encoding="utf-8", errors="ignore")
        except Exception:
            continue

        is_example_file = bool(SKIP_GENERIC_HEURISTIC_FOR.search(f.name))

        for label, pattern in SECRET_PATTERNS:
            # فایل‌های .example/.sample/.template ذاتاً قرار است الگوی
            # KEY=placeholder داشته باشند — فقط امضاهای قطعی رمز واقعی
            # (JWT، sbp_...، cfut_...) را در این‌ها چک می‌کنیم، نه حدس
            # عمومی KEY=رشته‌ی-طولانی که روی placeholderها هم مچ می‌شود.
            if is_example_file and label.startswith("رمز عبور"):
                continue
            if pattern.search(text):
                problems.append(f"احتمال {label} در فایل: {rel}")
    return problems


def main() -> int:
    print("=" * 60)
    print("بررسی امنیتی پیش از انتقال به گیت‌هاب")
    print("=" * 60)

    ensure_git_repo()

    gitignore_problems = check_gitignore()
    if gitignore_problems:
        print("\n❌ مشکلات .gitignore:")
        for p in gitignore_problems:
            print(f"  - {p}")
        print("\nمتوقف شدم — لطفاً این‌ها را اصلاح کنید و دوباره اجرا کنید.")
        return 1

    candidate_files = list_would_be_tracked_files()
    secret_problems = scan_files_for_secrets(candidate_files)

    if secret_problems:
        print(f"\n❌ {len(secret_problems)} مورد مشکوک پیدا شد — چیزی stage نشد:")
        for p in secret_problems:
            print(f"  - {p}")
        print("\nاین فایل‌ها را اصلاح کنید (یا به .gitignore اضافه کنید) و دوباره اجرا کنید.")
        return 1

    print(f"\n✅ هیچ رمز/کلید مشکوکی توی {len(candidate_files)} فایلی که قرار بود stage بشه پیدا نشد.")

    subprocess.run(["git", "add", "."], cwd=PROJECT_ROOT, check=True)
    status = run(["git", "status", "--short"])
    print("\n📋 فایل‌های آماده‌ی commit:")
    print(status if status else "  (چیز جدیدی برای commit نیست)")

    print("\n" + "=" * 60)
    print("همه‌چیز آماده‌ست. مراحل بعدی (خودتان انجام بدید):")
    print("=" * 60)
    print('  git commit -m "..."')
    print("  git remote add origin <آدرس ریپازیتوری گیت‌هاب شما>")
    print("  git push -u origin main")
    print("\nیادتان باشد قبل از push، این ۵ secret را هم توی")
    print("GitHub -> Settings -> Secrets and variables -> Actions اضافه کنید:")
    print("  SUPABASE_URL, DRIVER_API_KEY, KEYSTORE_BASE64, STORE_PASSWORD, KEY_PASSWORD")
    return 0


if __name__ == "__main__":
    sys.exit(main())
