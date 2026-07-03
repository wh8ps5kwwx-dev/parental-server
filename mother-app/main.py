"""حماية الأطفال — تطبيق الأم (Pydroid 3). نفس تدفق تطبيق Android: بريد → طفل → ربط → تحكم."""
from __future__ import annotations

import re
import sys
from pathlib import Path

_APP_DIR = Path(__file__).resolve().parent
_PROJECT_ROOT = _APP_DIR.parent
if (_PROJECT_ROOT / "common").is_dir() and str(_PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(_PROJECT_ROOT))

import arabic_reshaper
from bidi.algorithm import get_display
from kivy.app import App
from kivy.core.text import LabelBase
from kivy.core.window import Window
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.gridlayout import GridLayout
from kivy.uix.label import Label
from kivy.uix.screenmanager import Screen, ScreenManager
from kivy.uix.scrollview import ScrollView
from kivy.uix.spinner import Spinner
from kivy.uix.textinput import TextInput

try:
    from common import guardian_api
except ImportError:
    if str(_APP_DIR) not in sys.path:
        sys.path.insert(0, str(_APP_DIR))
    import guardian_api

Window.size = (390, 720)
Window.clearcolor = (0.97, 0.97, 1, 1)

LabelBase.register(name="Arabic", fn_regular="Arabic.ttf")

# ألوان MY Rana (بنفسجي)
UI_PRIMARY = (0.43, 0.16, 0.85, 1)
UI_BG = (0.97, 0.97, 1, 1)

ROLE_CHOOSE = "choose_role"
ROLES = ("أم", "أب", "ولي أمر")


def ar(text: str) -> str:
    return get_display(arabic_reshaper.reshape(str(text)))


def valid_email(email: str) -> bool:
    return bool(re.match(r"^[\w.\-+]+@[\w.\-]+\.[A-Za-z]{2,}$", email.strip()))


def normalize_child_code(raw: str) -> str:
    text = raw.strip().upper().replace(" ", "")
    if not text:
        return ""
    if text.startswith("CHILD-"):
        return text
    return f"CHILD-{text}"


def api_ok(body: dict) -> bool:
    return body.get("status") == "success"


def api_msg(body: dict, default: str = "حدث خطأ") -> str:
    return str(body.get("message") or default)


def fill_code_field(field: TextInput, body: dict) -> None:
    if body.get("dev_fallback") and body.get("verification_code"):
        field.text = str(body["verification_code"])


class ALabel(Label):
    def __init__(self, text: str = "", **kwargs):
        super().__init__(text=ar(text), font_name="Arabic", **kwargs)


class AButton(Button):
    def __init__(self, text: str = "", **kwargs):
        kwargs.setdefault("background_color", UI_PRIMARY)
        kwargs.setdefault("color", (1, 1, 1, 1))
        super().__init__(text=ar(text), font_name="Arabic", **kwargs)


class AInput(TextInput):
    def __init__(self, hint_text: str = "", **kwargs):
        super().__init__(
            hint_text=ar(hint_text),
            font_name="Arabic",
            multiline=False,
            halign="right",
            **kwargs,
        )


def screen_box() -> BoxLayout:
    return BoxLayout(orientation="vertical", padding=20, spacing=12)


class LoginScreen(Screen):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        layout = screen_box()

        layout.add_widget(
            Label(
                text="حماية الأطفال",
                font_size=28,
                bold=True,
                color=UI_PRIMARY,
                size_hint_y=None,
                height=60,
            )
        )
        layout.add_widget(ALabel("تسجيل ولي الأمر", font_size=20, size_hint_y=None, height=40))

        self.role = Spinner(
            text=ar(ROLE_CHOOSE),
            values=[ar(r) for r in ROLES],
            font_name="Arabic",
            size_hint_y=None,
            height=50,
        )
        self.email = TextInput(
            hint_text="Email",
            multiline=False,
            font_size=18,
            size_hint_y=None,
            height=45,
        )
        send_btn = AButton("إرسال رمز التحقق", size_hint_y=None, height=55)
        send_btn.bind(on_press=self.send_code)
        self.message = ALabel("", font_size=15, color=(1, 0.2, 0.2, 1), size_hint_y=None, height=80)

        layout.add_widget(self.role)
        layout.add_widget(self.email)
        layout.add_widget(send_btn)
        layout.add_widget(self.message)
        self.add_widget(layout)

    def _role_value(self) -> str:
        text = self.role.text
        for role in ROLES:
            if text == ar(role):
                return role
        return ""

    def send_code(self, *_):
        app = App.get_running_app()
        role = self._role_value()
        email = self.email.text.strip()

        if not role:
            self.message.text = ar("اختاري الصفة أولاً")
            return
        if not valid_email(email):
            self.message.text = ar("أدخلي بريداً صحيحاً")
            return

        app.email = email
        app.role = role
        self.message.text = ar("جاري الإرسال...")

        try:
            body = guardian_api.send_email_code(email)
        except Exception:
            self.message.text = ar("فشل الاتصال بالسيرفر")
            return

        if api_ok(body):
            verify = self.manager.get_screen("verify")
            verify.email_label.text = ar(f"البريد: {email}")
            verify.code_input.text = ""
            fill_code_field(verify.code_input, body)
            self.message.text = ar(api_msg(body, "تم إرسال الرمز — تحققي من Gmail"))
            self.manager.current = "verify"
        else:
            self.message.text = ar(api_msg(body))


class VerifyScreen(Screen):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        layout = screen_box()

        layout.add_widget(ALabel("تحقق من البريد", font_size=22, size_hint_y=None, height=50))
        self.email_label = ALabel("", font_size=16, size_hint_y=None, height=35)
        self.code_input = AInput("رمز التحقق من Gmail", size_hint_y=None, height=45)
        verify_btn = AButton("تأكيد الرمز", size_hint_y=None, height=55)
        verify_btn.bind(on_press=self.verify)
        back_btn = AButton("رجوع", size_hint_y=None, height=45)
        back_btn.bind(on_press=lambda *_: setattr(self.manager, "current", "login"))
        self.message = ALabel("", font_size=15, color=(1, 0.2, 0.2, 1), size_hint_y=None, height=70)

        layout.add_widget(self.email_label)
        layout.add_widget(self.code_input)
        layout.add_widget(verify_btn)
        layout.add_widget(back_btn)
        layout.add_widget(self.message)
        self.add_widget(layout)

    def verify(self, *_):
        app = App.get_running_app()
        code = self.code_input.text.strip()
        if not code:
            self.message.text = ar("أدخلي رمز التحقق")
            return

        self.message.text = ar("جاري التحقق...")
        try:
            body = guardian_api.verify_email_code(app.email, code)
        except Exception:
            self.message.text = ar("فشل الاتصال بالسيرفر")
            return

        if api_ok(body):
            app.email_verified = True
            self.message.text = ar(api_msg(body, "تم التحقق"))
            self.manager.current = "add_child"
        else:
            self.message.text = ar(api_msg(body))


class AddChildScreen(Screen):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        layout = screen_box()

        layout.add_widget(ALabel("إضافة طفل", font_size=22, size_hint_y=None, height=50))
        layout.add_widget(
            ALabel("الخطوة 1: اسم الطفل والعمر", font_size=16, size_hint_y=None, height=35)
        )
        self.name_input = AInput("اسم الطفل", size_hint_y=None, height=45)
        self.age_input = AInput("العمر من 5 إلى 13", size_hint_y=None, height=45)
        cont_btn = AButton("متابعة إلى ربط الجهاز", size_hint_y=None, height=55)
        cont_btn.bind(on_press=self.continue_to_link)
        self.message = ALabel("", font_size=15, color=(1, 0.2, 0.2, 1), size_hint_y=None, height=60)

        layout.add_widget(self.name_input)
        layout.add_widget(self.age_input)
        layout.add_widget(cont_btn)
        layout.add_widget(self.message)
        self.add_widget(layout)

    def continue_to_link(self, *_):
        app = App.get_running_app()
        name = self.name_input.text.strip()
        age_text = self.age_input.text.strip()

        if not name:
            self.message.text = ar("أدخلي اسم الطفل")
            return
        if not age_text.isdigit():
            self.message.text = ar("أدخلي العمر رقماً")
            return

        age = int(age_text)
        if age < 5 or age > 13:
            self.message.text = ar("العمر من 5 إلى 13")
            return

        app.child_name = name
        app.child_age = age
        app.child_code = ""
        app.device_verify_code = ""
        app.device_name = ""
        app.android_version = ""
        app.linked = False

        link = self.manager.get_screen("link")
        link.child_code_input.text = ""
        link.verify_input.text = ""
        link.message.text = ar("الصقي كود الطفل من جواله (CHILD-...)")
        self.manager.current = "link"


class LinkScreen(Screen):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        layout = screen_box()

        layout.add_widget(ALabel("ربط جهاز الطفل", font_size=22, size_hint_y=None, height=50))
        layout.add_widget(
            ALabel("الخطوة 2: كود الطفل + رمز الربط", font_size=16, size_hint_y=None, height=35)
        )
        self.child_code_input = AInput("كود الطفل CHILD-...", size_hint_y=None, height=45)
        send_link_btn = AButton("إرسال رمز الربط للبريد", size_hint_y=None, height=50)
        send_link_btn.bind(on_press=self.send_link_code)
        self.verify_input = AInput("رمز الربط من Gmail", size_hint_y=None, height=45)
        verify_btn = AButton("تحقق من رمز الربط", size_hint_y=None, height=50)
        verify_btn.bind(on_press=self.verify_device)
        link_btn = AButton("ربط الطفل وحفظ", size_hint_y=None, height=55)
        link_btn.bind(on_press=self.link_child)
        self.message = ALabel("", font_size=14, size_hint_y=None, height=100)

        layout.add_widget(self.child_code_input)
        layout.add_widget(send_link_btn)
        layout.add_widget(self.verify_input)
        layout.add_widget(verify_btn)
        layout.add_widget(link_btn)
        layout.add_widget(self.message)
        self.add_widget(layout)

    def _child_code(self) -> str:
        code = normalize_child_code(self.child_code_input.text)
        if code:
            self.child_code_input.text = code
        return code

    def send_link_code(self, *_):
        app = App.get_running_app()
        child_code = self._child_code()
        if not child_code:
            self.message.text = ar("أدخلي كود الطفل من جواله")
            return

        self.message.text = ar("جاري إرسال رمز الربط...")
        try:
            body = guardian_api.send_link_code(app.email, child_code)
        except Exception:
            self.message.text = ar("فشل الاتصال بالسيرفر")
            return

        if api_ok(body):
            fill_code_field(self.verify_input, body)
            self.message.text = ar(api_msg(body, "تم إرسال رمز الربط — تحققي من Gmail"))
        else:
            self.message.text = ar(api_msg(body))

    def verify_device(self, *_):
        app = App.get_running_app()
        child_code = self._child_code()
        code = self.verify_input.text.strip()
        if not child_code or not code:
            self.message.text = ar("أدخلي كود الطفل ورمز الربط")
            return

        self.message.text = ar("جاري التحقق...")
        try:
            body = guardian_api.verify_child_device_code(child_code, code)
        except Exception:
            self.message.text = ar("فشل الاتصال بالسيرفر")
            return

        if api_ok(body):
            app.child_code = child_code
            app.device_verify_code = code
            app.device_name = str(body.get("device_name") or "")
            app.android_version = str(body.get("android_version") or "")
            info = app.device_name or "Android"
            self.message.text = ar(f"تم التحقق — الجهاز: {info}. اضغطي «ربط الطفل وحفظ»")
        else:
            self.message.text = ar(api_msg(body))

    def link_child(self, *_):
        app = App.get_running_app()
        child_code = self._child_code()
        verify = self.verify_input.text.strip()

        if not child_code or not verify:
            self.message.text = ar("أدخلي كود الطفل ورمز الربط")
            return

        child_name = (app.child_name or "").strip() or "طفل"
        payload = {
            "name": child_name,
            "child_name": child_name,
            "age": app.child_age or 10,
            "child_email": app.email,
            "device": app.device_name or "Android",
            "android_version": app.android_version or "Android",
            "child_code": child_code,
            "device_verify_code": verify,
            "verification_code": verify,
            "otp": verify,
            "guardian_email": app.email,
            "parent_email": app.email,
            "email": app.email,
            "guardian_role": app.role,
        }

        self.message.text = ar("جاري الربط...")
        try:
            body = guardian_api.add_child(payload)
        except Exception:
            self.message.text = ar("فشل الاتصال بالسيرفر")
            return

        if api_ok(body):
            app.child_code = child_code
            app.device_verify_code = verify
            app.linked = True
            try:
                guardian_api.apply_default_blocklist(child_code)
            except Exception:
                pass
            control = self.manager.get_screen("control")
            control.refresh_header()
            self.message.text = ar(api_msg(body, "تم ربط الطفل بنجاح"))
            self.manager.current = "control"
        else:
            self.message.text = ar(api_msg(body))


class ControlScreen(Screen):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        main = screen_box()

        self.header = ALabel("لوحة التحكم", font_size=22, size_hint_y=None, height=55)
        self.target_input = AInput("اسم التطبيق أو الموقع", size_hint_y=None, height=45)
        self.status = ALabel("جاهز", font_size=15, size_hint_y=None, height=45)

        grid = GridLayout(cols=2, spacing=8, size_hint_y=None)
        grid.height = 400
        actions = [
            ("حظر تطبيق", lambda *_: self.cmd("block_app")),
            ("تجميد تطبيق", lambda *_: self.cmd("freeze_app")),
            ("حظر موقع", lambda *_: self.cmd("block_site")),
            ("السماح", lambda *_: self.cmd("allow", "")),
            ("قائمة حظر كاملة", self.apply_blocklist),
            ("التقارير", lambda *_: setattr(self.manager, "current", "reports")),
            ("التنبيهات", lambda *_: setattr(self.manager, "current", "alerts")),
            ("استخدام أسبوعي", lambda *_: setattr(self.manager, "current", "usage")),
            ("رسالة للطفل", self.send_message),
            ("إضافة طفل آخر", self.add_another),
        ]
        for label, handler in actions:
            btn = AButton(label, font_size=16)
            btn.bind(on_press=handler)
            grid.add_widget(btn)

        main.add_widget(self.header)
        main.add_widget(self.target_input)
        main.add_widget(grid)
        main.add_widget(self.status)
        self.add_widget(main)

    def refresh_header(self):
        app = App.get_running_app()
        if app.linked and app.child_code:
            self.header.text = ar(f"الطفل: {app.child_name} — {app.child_code}")
        else:
            self.header.text = ar("لوحة التحكم")

    def _need_linked(self) -> bool:
        app = App.get_running_app()
        if not app.linked or not app.child_code:
            self.status.text = ar("اربطي الطفل أولاً من شاشة الربط")
            return False
        return True

    def cmd(self, action: str, value: str | None = None):
        if not self._need_linked():
            return
        app = App.get_running_app()
        val = value if value is not None else self.target_input.text.strip()
        if action != "allow" and not val:
            self.status.text = ar("اكتبي اسم التطبيق أو الموقع")
            return

        self.status.text = ar("جاري الإرسال...")
        try:
            body = guardian_api.send_command(action, val, app.child_code, app.email)
        except Exception:
            self.status.text = ar("فشل الاتصال بالسيرفر")
            return

        if api_ok(body):
            self.status.text = ar(api_msg(body, "تم إرسال الأمر"))
        else:
            self.status.text = ar(api_msg(body))

    def send_message(self, *_):
        if not self._need_linked():
            return
        app = App.get_running_app()
        text = self.target_input.text.strip()
        if not text:
            self.status.text = ar("اكتبي الرسالة في الحقل أعلاه")
            return
        self.status.text = ar("جاري الإرسال...")
        try:
            body = guardian_api.send_guardian_message(app.child_code, app.role, text)
        except Exception:
            self.status.text = ar("فشل الاتصال بالسيرفر")
            return
        if api_ok(body):
            self.status.text = ar(api_msg(body, "تم إرسال الرسالة"))
        else:
            self.status.text = ar(api_msg(body))

    def apply_blocklist(self, *_):
        if not self._need_linked():
            return
        app = App.get_running_app()
        self.status.text = ar("جاري تطبيق القائمة...")
        try:
            body = guardian_api.apply_default_blocklist(app.child_code)
        except Exception:
            self.status.text = ar("فشل الاتصال بالسيرفر")
            return
        if api_ok(body):
            self.status.text = ar(api_msg(body, "تم تطبيق قائمة الحظر"))
        else:
            self.status.text = ar(api_msg(body))

    def add_another(self, *_):
        app = App.get_running_app()
        app.child_name = ""
        app.child_age = 0
        app.child_code = ""
        app.device_verify_code = ""
        app.device_name = ""
        app.android_version = ""
        app.linked = False
        add = self.manager.get_screen("add_child")
        add.name_input.text = ""
        add.age_input.text = ""
        add.message.text = ""
        self.manager.current = "add_child"


class ListScreen(Screen):
    """شاشة مشتركة للتقارير والتنبيهات والاستخدام."""

    def __init__(self, title: str, loader, **kwargs):
        super().__init__(**kwargs)
        self._loader = loader
        layout = screen_box()

        layout.add_widget(ALabel(title, font_size=22, size_hint_y=None, height=50))
        scroll = ScrollView(size_hint=(1, 1))
        self.content = ALabel("اضغطي تحميل", font_size=14, text_size=(340, None), halign="right", valign="top")
        self.content.bind(size=self._resize_label)
        scroll.add_widget(self.content)
        load_btn = AButton("تحميل", size_hint_y=None, height=50)
        load_btn.bind(on_press=self.load)
        back_btn = AButton("رجوع", size_hint_y=None, height=45)
        back_btn.bind(on_press=lambda *_: setattr(self.manager, "current", "control"))

        layout.add_widget(scroll)
        layout.add_widget(load_btn)
        layout.add_widget(back_btn)
        self.add_widget(layout)

    @staticmethod
    def _resize_label(instance, _size):
        instance.text_size = (instance.width, None)

    def load(self, *_):
        app = App.get_running_app()
        if not app.linked or not app.child_code:
            self.content.text = ar("اربطي الطفل أولاً")
            return
        self.content.text = ar("جاري التحميل...")
        try:
            self.content.text = self._loader(app)
        except Exception:
            self.content.text = ar("فشل التحميل")


def load_reports(app) -> str:
    rows = guardian_api.fetch_reports(app.child_code)
    if not rows:
        return ar("لا توجد تقارير بعد")
    lines = []
    for row in rows[:12]:
        lines.append(f"{row.get('event', '')}: {row.get('value', '')}")
        lines.append(str(row.get("time", "")))
        lines.append("---")
    return ar("\n".join(lines))


def load_alerts(app) -> str:
    rows = guardian_api.fetch_alerts(app.child_code)
    if not rows:
        return ar("لا توجد تنبيهات")
    lines = []
    for row in rows[:12]:
        lines.append(str(row.get("message", "")))
        lines.append(str(row.get("time", "")))
        lines.append("---")
    return ar("\n".join(lines))


def load_usage(app) -> str:
    rows = guardian_api.fetch_weekly_usage(app.child_code)
    if not rows:
        return ar("لا بيانات استخدام بعد")
    lines = []
    for row in rows[:15]:
        pkg = row.get("package") or row.get("app") or "?"
        mins = row.get("minutes") or row.get("total_minutes") or 0
        lines.append(f"{pkg}: {mins} د")
    return ar("\n".join(lines))


class MotherApp(App):
    email: str = ""
    role: str = ""
    email_verified: bool = False
    child_name: str = ""
    child_age: int = 0
    child_code: str = ""
    device_verify_code: str = ""
    device_name: str = ""
    android_version: str = ""
    linked: bool = False

    def build(self):
        sm = ScreenManager()
        sm.add_widget(LoginScreen(name="login"))
        sm.add_widget(VerifyScreen(name="verify"))
        sm.add_widget(AddChildScreen(name="add_child"))
        sm.add_widget(LinkScreen(name="link"))
        sm.add_widget(ControlScreen(name="control"))
        sm.add_widget(
            ListScreen("تقارير الطفل", load_reports, name="reports")
        )
        sm.add_widget(
            ListScreen("التنبيهات", load_alerts, name="alerts")
        )
        sm.add_widget(
            ListScreen("استخدام أسبوعي", load_usage, name="usage")
        )
        return sm


if __name__ == "__main__":
    MotherApp().run()
