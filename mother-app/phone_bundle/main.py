import re
import sys
from pathlib import Path

import requests
import arabic_reshaper
from bidi.algorithm import get_display

APP_DIR = Path(__file__).resolve().parent
# سطح المكتب: common من المشروع | جوال أندرويد: guardian_api.py بجانب main.py
sys.path.insert(0, str(APP_DIR))
sys.path.insert(0, str(APP_DIR.parent))

try:
    from guardian_api import add_child, send_email_code, send_link_code, verify_email_code
except ImportError:
    from common.guardian_api import add_child, send_email_code, send_link_code, verify_email_code  # noqa: E402

from kivy.app import App
from kivy.utils import platform as kivy_platform
from kivy.uix.screenmanager import ScreenManager, Screen
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.gridlayout import GridLayout
from kivy.uix.label import Label
from kivy.uix.textinput import TextInput
from kivy.uix.button import Button
from kivy.uix.spinner import Spinner
from kivy.core.window import Window
from kivy.core.text import LabelBase

if kivy_platform not in ("android", "ios"):
    Window.size = (390, 720)

# بعد رفع السيرفر على Render حطي الرابط هنا
SERVER_URL = "https://parental-server-4mms.onrender.com"

API_KEY = "graduation-secret-key"
HEADERS = {"X-API-KEY": API_KEY}

CHILD_CODE = "CHILD-001"

_arabic_font = APP_DIR / "Arabic.ttf"
UI_FONT = "Roboto"
if _arabic_font.is_file():
    LabelBase.register(name="Arabic", fn_regular=str(_arabic_font))
    UI_FONT = "Arabic"


def ar(text):
    return get_display(arabic_reshaper.reshape(str(text)))


def valid_email(email):
    return re.match(r"^[\w\.-]+@[\w\.-]+\.\w+$", email)


def ALabel(text, **kwargs):
    kwargs.setdefault("font_name", UI_FONT)
    return Label(text=ar(text), **kwargs)


def AButton(text, **kwargs):
    kwargs.setdefault("font_name", UI_FONT)
    return Button(text=ar(text), **kwargs)


def AInput(hint_text, **kwargs):
    return TextInput(
        hint_text=ar(hint_text),
        font_name=UI_FONT,
        multiline=False,
        halign="right",
        **kwargs
    )


class LoginScreen(Screen):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)

        layout = BoxLayout(orientation="vertical", padding=25, spacing=15)

        title = Label(
            text="Parental Control",
            font_size=28,
            bold=True,
            size_hint_y=None,
            height=70
        )

        subtitle = ALabel(
            "تسجيل دخول ولي الأمر",
            font_size=20,
            size_hint_y=None,
            height=45
        )

        self.role = Spinner(
            text=ar("اختاري الصفة"),
            values=[ar("أم"), ar("أب"), ar("ولي أمر")],
            font_name=UI_FONT,
            size_hint_y=None,
            height=55
        )

        self.email = TextInput(
            hint_text="Gmail",
            multiline=False,
            font_size=18
        )

        self.password = TextInput(
            hint_text="Password",
            password=True,
            multiline=False,
            font_size=18
        )

        self.email_code = AInput(
            "رمز التحقق من بريدك",
            font_size=18,
            size_hint_y=None,
            height=0,
            opacity=0,
            disabled=True,
        )

        self.login_btn = AButton(
            "إرسال رمز التحقق للبريد",
            font_size=20,
            size_hint_y=None,
            height=55
        )
        self.login_btn.bind(on_press=self.login)

        self.message = ALabel("", font_size=16, color=(1, 0, 0, 1))
        self._code_sent = False

        layout.add_widget(title)
        layout.add_widget(subtitle)
        layout.add_widget(self.role)
        layout.add_widget(self.email)
        layout.add_widget(self.password)
        layout.add_widget(self.email_code)
        layout.add_widget(self.login_btn)
        layout.add_widget(self.message)

        self.add_widget(layout)

    def _validate_form(self):
        role = self.role.text
        email = self.email.text.strip()
        password = self.password.text.strip()

        if role == ar("اختاري الصفة"):
            self.message.text = ar("اختاري الصفة أولًا")
            return None
        if not valid_email(email):
            self.message.text = ar("اكتبي بريدًا صحيحًا")
            return None
        if len(password) < 6:
            self.message.text = ar("كلمة السر لازم تكون 6 أحرف على الأقل")
            return None
        return role, email, password

    def login(self, instance):
        validated = self._validate_form()
        if not validated:
            return
        role, email, password = validated
        if not self._code_sent:
            self._send_verification_code(role, email, password)
            return
        self._verify_and_enter(role, email, password)

    def _send_verification_code(self, role, email, password):
        """يرسل رمزاً للبريد الذي أدخلته الأم — أي بريد صحيح."""
        try:
            result = send_email_code(email)
            if result.get("status") != "success":
                self.message.text = ar(result.get("message", "فشل الإرسال"))
                return
            App.get_running_app().guardian = {"role": role, "email": email, "password": password}
            self._code_sent = True
            self.email_code.disabled = False
            self.email_code.opacity = 1
            self.email_code.height = 50
            self.login_btn.text = ar("تحقق والدخول")
            if result.get("dev_fallback"):
                dev_code = result.get("verification_code", "")
                self.message.text = ar("وضع تطوير — الرمز: ") + str(dev_code)
                if dev_code:
                    self.email_code.text = str(dev_code)
            else:
                self.message.text = ar("تم إرسال الرمز إلى ") + email + ar(" — أدخليه من بريدك")
        except Exception:
            self.message.text = ar("فشل الاتصال بالسيرفر")

    def _verify_and_enter(self, role, email, password):
        code = self.email_code.text.strip()
        if not code:
            self.message.text = ar("أدخلي رمز التحقق من بريدك")
            return
        try:
            result = verify_email_code(email, code)
            if result.get("status") != "success":
                self.message.text = ar(result.get("message", "رمز غير صحيح"))
                return
            app = App.get_running_app()
            app.guardian = {"role": role, "email": email, "password": password}
            app.email_verified = True
            self.message.text = ar("تم التحقق من بريدك — مرحبًا")
            self.manager.current = "home"
        except Exception:
            self.message.text = ar("فشل الاتصال بالسيرفر")


class HomeScreen(Screen):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)

        main = BoxLayout(orientation="vertical", padding=20, spacing=12)

        self.title = ALabel(
            "لوحة تحكم ولي الأمر",
            font_size=24,
            bold=True,
            size_hint_y=None,
            height=60
        )

        self.input_box = AInput(
            "اكتبي اسم التطبيق أو الموقع هنا",
            font_size=18,
            size_hint_y=None,
            height=50
        )

        grid = GridLayout(cols=2, spacing=10, size_hint_y=None)
        grid.height = 320

        buttons = [
            ("حظر تطبيق", self.block_app),
            ("السماح", self.allow),
            ("حظر موقع", self.block_site),
            ("التقارير", self.open_reports),
            ("التنبيهات", self.open_alerts),
            ("إضافة طفل", self.add_child),
        ]

        for text, func in buttons:
            btn = AButton(text, font_size=17)
            btn.bind(on_press=func)
            grid.add_widget(btn)

        self.status = ALabel(
            "جاهز",
            font_size=16,
            size_hint_y=None,
            height=45
        )

        main.add_widget(self.title)
        main.add_widget(self.input_box)
        main.add_widget(grid)
        main.add_widget(self.status)

        self.add_widget(main)

    def send_command(self, action, value=""):
        try:
            app = App.get_running_app()
            guardian = app.guardian
            child_code = getattr(app, "selected_child_code", CHILD_CODE) or CHILD_CODE

            data = {
                "action": action,
                "value": value,
                "child_code": child_code,
                "guardian_email": guardian.get("email", "")
            }

            response = requests.post(
                SERVER_URL + "/send-command",
                json=data,
                headers=HEADERS,
                timeout=10
            )

            if response.status_code == 200:
                self.status.text = ar("تم إرسال الأمر بنجاح")
            else:
                self.status.text = ar("فشل إرسال الأمر")

        except Exception:
            self.status.text = ar("فشل الاتصال بالسيرفر")

    def block_app(self, instance):
        app_name = self.input_box.text.strip()

        if not app_name:
            self.status.text = ar("اكتبي اسم التطبيق أولًا")
            return

        self.send_command("block_app", app_name)

    def allow(self, instance):
        self.send_command("allow", "")

    def block_site(self, instance):
        site = self.input_box.text.strip()

        if not site:
            self.status.text = ar("اكتبي اسم الموقع أولًا")
            return

        self.send_command("block_site", site)

    def open_reports(self, instance):
        self.manager.current = "reports"

    def open_alerts(self, instance):
        self.manager.current = "alerts"

    def add_child(self, instance):
        self.manager.current = "children"


class ChildrenScreen(Screen):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)

        layout = BoxLayout(orientation="vertical", padding=20, spacing=12)

        title = ALabel(
            "إضافة وربط طفل",
            font_size=24,
            bold=True,
            size_hint_y=None,
            height=60
        )

        self.child_name = AInput(
            "اسم الطفل",
            font_size=18,
            size_hint_y=None,
            height=50
        )

        self.child_age = AInput(
            "العمر من 5 إلى 13",
            font_size=18,
            size_hint_y=None,
            height=50
        )

        self.device = AInput(
            "نوع الجهاز مثل Samsung A32",
            font_size=18,
            size_hint_y=None,
            height=50
        )

        self.android_version = AInput(
            "إصدار النظام مثل Android 13",
            font_size=18,
            size_hint_y=None,
            height=50
        )

        self.child_code_input = AInput(
            "كود الطفل مثل CHILD-001",
            font_size=18,
            size_hint_y=None,
            height=50
        )
        self.child_code_input.text = CHILD_CODE

        self.device_verify_input = AInput(
            "رمز الربط من بريدك (6 أرقام)",
            font_size=18,
            size_hint_y=None,
            height=50
        )

        send_link = AButton(
            "إرسال رمز الربط للبريد",
            size_hint_y=None,
            height=55
        )
        send_link.bind(on_press=self.send_link_code)

        save = AButton(
            "حفظ وربط الطفل",
            size_hint_y=None,
            height=55
        )
        save.bind(on_press=self.save_child)

        self.msg = ALabel("", font_size=16)
        self._link_code_sent = False

        back = AButton(
            "رجوع",
            size_hint_y=None,
            height=55
        )
        back.bind(on_press=lambda x: setattr(self.manager, "current", "home"))

        layout.add_widget(title)
        layout.add_widget(self.child_name)
        layout.add_widget(self.child_age)
        layout.add_widget(self.device)
        layout.add_widget(self.android_version)
        layout.add_widget(self.child_code_input)
        layout.add_widget(send_link)
        layout.add_widget(self.device_verify_input)
        layout.add_widget(save)
        layout.add_widget(self.msg)
        layout.add_widget(back)

        self.add_widget(layout)

    def send_link_code(self, instance):
        child_code = self.child_code_input.text.strip()
        guardian = App.get_running_app().guardian
        email = guardian.get("email", "").strip()
        if not child_code:
            self.msg.text = ar("أدخلي كود الطفل من جواله أولًا")
            return
        if not email:
            self.msg.text = ar("سجّلي دخولك من الشاشة الأولى")
            return
        try:
            result = send_link_code(email, child_code)
            if result.get("status") != "success":
                self.msg.text = ar(result.get("message", "فشل الإرسال"))
                return
            self._link_code_sent = True
            if result.get("dev_fallback"):
                dev_code = result.get("verification_code", "")
                self.msg.text = ar("وضع تطوير — الرمز: ") + str(dev_code)
                if dev_code:
                    self.device_verify_input.text = str(dev_code)
            else:
                self.msg.text = ar("تم إرسال رمز الربط — تحققي من بريدك")
        except Exception:
            self.msg.text = ar("فشل الاتصال بالسيرفر")

    def save_child(self, instance):
        name = self.child_name.text.strip()
        age = self.child_age.text.strip()
        device = self.device.text.strip()
        android_version = self.android_version.text.strip()
        child_code = self.child_code_input.text.strip()
        device_verify_code = self.device_verify_input.text.strip()

        if not name or not age.isdigit() or not child_code or not device_verify_code:
            self.msg.text = ar("أدخلي كل البيانات بما فيها رمز الربط من البريد")
            return

        age = int(age)

        if age < 5 or age > 13:
            self.msg.text = ar("العمر لازم يكون من 5 إلى 13")
            return

        try:
            guardian = App.get_running_app().guardian

            app = App.get_running_app()
            if not getattr(app, "email_verified", False):
                self.msg.text = ar("يجب التحقق من بريدك أولًا من شاشة الدخول")
                return

            data = {
                "name": name,
                "age": age,
                "device": device,
                "android_version": android_version,
                "child_code": child_code,
                "device_verify_code": device_verify_code,
                "guardian_email": guardian.get("email", ""),
                "guardian_role": guardian.get("role", "")
            }

            result = add_child(data)
            if result.get("status") == "success":
                global CHILD_CODE
                CHILD_CODE = child_code
                app.selected_child_code = child_code
                self.msg.text = ar("تم ربط الطفل بنجاح")
            else:
                self.msg.text = ar(result.get("message", "فشل الربط — تحققي من الرموز"))

        except Exception:
            self.msg.text = ar("فشل الاتصال بالسيرفر")


class ReportsScreen(Screen):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)

        layout = BoxLayout(orientation="vertical", padding=20, spacing=10)

        title = ALabel(
            "تقارير نشاط الطفل",
            font_size=24,
            bold=True,
            size_hint_y=None,
            height=60
        )

        self.reports = ALabel(
            "اضغطي تحميل التقارير",
            font_size=16
        )

        load_btn = AButton(
            "تحميل التقارير",
            size_hint_y=None,
            height=55
        )
        load_btn.bind(on_press=self.load_reports)

        back_btn = AButton(
            "رجوع",
            size_hint_y=None,
            height=55
        )
        back_btn.bind(on_press=lambda x: setattr(self.manager, "current", "home"))

        layout.add_widget(title)
        layout.add_widget(self.reports)
        layout.add_widget(load_btn)
        layout.add_widget(back_btn)

        self.add_widget(layout)

    def load_reports(self, instance):
        try:
            data = requests.get(
                SERVER_URL + "/reports",
                params={"child_code": CHILD_CODE},
                headers=HEADERS,
                timeout=10
            ).json()

            if not data:
                self.reports.text = ar("لا توجد تقارير بعد")
                return

            text = ""
            for r in data[:8]:
                text += f"{ar('الحدث')}: {r.get('event')}\n"
                text += f"{ar('القيمة')}: {r.get('value')}\n"
                text += f"{ar('الوقت')}: {r.get('time')}\n"
                text += "-------------------\n"

            self.reports.text = text

        except Exception:
            self.reports.text = ar("فشل تحميل التقارير")


class AlertsScreen(Screen):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)

        layout = BoxLayout(orientation="vertical", padding=20, spacing=10)

        title = ALabel(
            "التنبيهات",
            font_size=24,
            bold=True,
            size_hint_y=None,
            height=60
        )

        self.alerts = ALabel(
            "لا توجد تنبيهات حالياً",
            font_size=16
        )

        refresh = AButton(
            "تحديث التنبيهات",
            size_hint_y=None,
            height=55
        )
        refresh.bind(on_press=self.load_alerts)

        back = AButton(
            "رجوع",
            size_hint_y=None,
            height=55
        )
        back.bind(on_press=lambda x: setattr(self.manager, "current", "home"))

        layout.add_widget(title)
        layout.add_widget(self.alerts)
        layout.add_widget(refresh)
        layout.add_widget(back)

        self.add_widget(layout)

    def load_alerts(self, instance):
        try:
            data = requests.get(
                SERVER_URL + "/alerts",
                params={"child_code": CHILD_CODE},
                headers=HEADERS,
                timeout=10
            ).json()

            if not data:
                self.alerts.text = ar("لا توجد تنبيهات")
                return

            text = ""
            for a in data[:8]:
                text += f"{ar('تنبيه')}: {a.get('message')}\n"
                text += f"{ar('الوقت')}: {a.get('time')}\n"
                text += "-------------------\n"

            self.alerts.text = text

        except Exception:
            self.alerts.text = ar("فشل تحميل التنبيهات")


class MotherApp(App):
    guardian = {}
    email_verified = False
    selected_child_code = CHILD_CODE

    def build(self):
        sm = ScreenManager()
        sm.add_widget(LoginScreen(name="login"))
        sm.add_widget(HomeScreen(name="home"))
        sm.add_widget(ReportsScreen(name="reports"))
        sm.add_widget(AlertsScreen(name="alerts"))
        sm.add_widget(ChildrenScreen(name="children"))
        return sm


MotherApp().run()