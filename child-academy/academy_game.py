# ==============================
# لعبة أكاديمية العباقرة — المصدر الأصلي (Python / Kivy)
# نُسخت منطقها إلى Android: MYRana/ui/academy/
# ==============================

import json
import random

SAVE_FILE = "child_game_progress.json"


def load_progress():
    try:
        with open(SAVE_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {
            "coins": 0,
            "xp": 0,
            "level": 1,
            "stars": 0,
            "correct": 0,
            "buildings": [],
        }


def save_progress(data):
    try:
        with open(SAVE_FILE, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
    except Exception:
        pass

# الشاشات الكاملة (AcademyMenuScreen, ChallengeScreen, ...) — انظر مستودع المشروع
# على Android يُشغَّل نفس المحتوى عبر AcademyMenuActivity + WorkManager للمراقبة.
