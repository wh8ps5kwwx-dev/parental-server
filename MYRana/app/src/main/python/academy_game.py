# ==============================
# أكاديمية العباقرة — محرك Python داخل APK (Chaquopy)
# ==============================

import json
import os
import random

SAVE_NAME = "child_game_progress.json"

MATH_QUESTIONS = [
    {"q": "كم ناتج 5 + 3؟", "options": ["8", "6", "10"], "answer": "8"},
    {"q": "كم ناتج 9 - 4؟", "options": ["5", "3", "7"], "answer": "5"},
    {"q": "كم ناتج 3 × 2؟", "options": ["6", "5", "8"], "answer": "6"},
    {"q": "أي عدد أكبر؟", "options": ["12", "9", "6"], "answer": "12"},
]

SCIENCE_QUESTIONS = [
    {"q": "ما الكوكب الذي نعيش عليه؟", "options": ["الأرض", "المريخ", "زحل"], "answer": "الأرض"},
    {"q": "ما العضو الذي يضخ الدم؟", "options": ["القلب", "العين", "الأذن"], "answer": "القلب"},
    {"q": "ما رمز الماء؟", "options": ["H2O", "O2", "CO2"], "answer": "H2O"},
    {"q": "النبات يحتاج إلى؟", "options": ["ماء وضوء", "حديد فقط", "رمل فقط"], "answer": "ماء وضوء"},
]

LOGIC_QUESTIONS = [
    {"q": "شيء نراه في الليل ولا نلمسه؟", "options": ["القمر", "الكتاب", "القلم"], "answer": "القمر"},
    {"q": "له أسنان ولا يعض؟", "options": ["المشط", "الكلب", "السمكة"], "answer": "المشط"},
    {"q": "كلما أخذت منه كبر؟", "options": ["الحفرة", "الكأس", "الكتاب"], "answer": "الحفرة"},
    {"q": "ما الشيء الذي يمشي بلا رجلين؟", "options": ["الوقت", "الكرسي", "الحجر"], "answer": "الوقت"},
]

BUILDINGS = [
    ("بيت المعرفة", 10),
    ("مدرسة العلوم", 20),
    ("حديقة الذكاء", 30),
    ("مركز الفضاء", 40),
]


def _default_progress():
    return {
        "coins": 0,
        "xp": 0,
        "level": 1,
        "stars": 0,
        "correct": 0,
        "buildings": [],
    }


def _path(storage_dir):
    return os.path.join(storage_dir, SAVE_NAME)


def load_progress_from_storage(storage_dir):
    try:
        with open(_path(storage_dir), "r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception:
        data = _default_progress()
    return json.dumps(data, ensure_ascii=False)


def save_progress_to_storage(storage_dir, progress_json):
    with open(_path(storage_dir), "w", encoding="utf-8") as f:
        f.write(progress_json)
    return "ok"


def stats_line(progress_json):
    p = json.loads(progress_json)
    return (
        f"🏆 المستوى: {p['level']}\n"
        f"⭐ النجوم: {p['stars']}   🪙 العملات: {p['coins']}   ⚡ XP: {p['xp']}"
    )


def random_question(category):
    pools = {
        "math": MATH_QUESTIONS,
        "science": SCIENCE_QUESTIONS,
        "logic": LOGIC_QUESTIONS,
    }
    item = random.choice(pools.get(category, MATH_QUESTIONS))
    options = item["options"][:]
    random.shuffle(options)
    return json.dumps(
        {"q": item["q"], "options": options, "answer": item["answer"]},
        ensure_ascii=False,
    )


def grade_answer(storage_dir, progress_json, category, selected, correct_answer):
    p = json.loads(progress_json)
    leveled = False
    title = {"math": "رياضيات", "science": "علوم", "logic": "ألغاز"}.get(category, category)

    if selected == correct_answer:
        p["coins"] += 5
        p["xp"] += 10
        p["stars"] += 1
        p["correct"] += 1
        if p["xp"] >= p["level"] * 50:
            p["level"] += 1
            leveled = True
            message = f"رائع! وصلتِ للمستوى {p['level']} 🎉"
        else:
            message = "إجابة صحيحة ✅ +5 عملات +10 XP +نجمة"
        event = "academy_correct"
        event_value = f"{title} - إجابة صحيحة"
    else:
        message = f"إجابة خاطئة ❌ الصحيح: {correct_answer}"
        event = "academy_wrong"
        event_value = f"{title} - إجابة خاطئة"

    out = json.dumps(p, ensure_ascii=False)
    save_progress_to_storage(storage_dir, out)
    return json.dumps(
        {
            "progress_json": out,
            "message": message,
            "leveled": leveled,
            "level": p["level"],
            "event": event,
            "event_value": event_value,
        },
        ensure_ascii=False,
    )


def city_info(progress_json):
    p = json.loads(progress_json)
    city = "المدينة فارغة الآن 🏜️" if not p["buildings"] else " ".join(p["buildings"])
    return json.dumps(
        {
            "stars": p["stars"],
            "city_text": city,
            "buildings": p["buildings"],
        },
        ensure_ascii=False,
    )


def city_build(storage_dir, progress_json, name, cost):
    p = json.loads(progress_json)
    if name in p["buildings"]:
        return json.dumps({"ok": False, "message": "هذا المبنى موجود بالفعل ✅"}, ensure_ascii=False)
    if p["stars"] < cost:
        return json.dumps({"ok": False, "message": "النجوم غير كافية ⭐"}, ensure_ascii=False)
    p["stars"] -= cost
    p["buildings"].append(name)
    out = json.dumps(p, ensure_ascii=False)
    save_progress_to_storage(storage_dir, out)
    return json.dumps(
        {
            "ok": True,
            "message": f"تم بناء {name} 🎉",
            "progress_json": out,
            "event_value": f"بنى الطفل: {name}",
        },
        ensure_ascii=False,
    )


def rewards_text(progress_json):
    p = json.loads(progress_json)
    medal = "🥉 مبتدئ"
    if p["level"] >= 3:
        medal = "🥈 مستكشف"
    if p["level"] >= 5:
        medal = "🥇 عالم صغير"
    if p["level"] >= 8:
        medal = "💎 عبقري"
    return (
        f"🏆 المستوى: {p['level']}\n"
        f"🪙 العملات: {p['coins']}\n"
        f"⚡ XP: {p['xp']}\n"
        f"⭐ النجوم: {p['stars']}\n"
        f"✅ الإجابات الصحيحة: {p['correct']}\n\n"
        f"وسامك الحالي: {medal}\n\n"
        "استمري في التعلم لبناء مدينة المعرفة!"
    )


def list_buildings():
    return json.dumps(
        [{"name": n, "cost": c} for n, c in BUILDINGS],
        ensure_ascii=False,
    )
