from __future__ import annotations

from kivy.app import App
from kivy.lang import Builder
from kivy.properties import StringProperty
from kivy.uix.boxlayout import BoxLayout

from policy_repository import PolicyRepository

KV = """
<MyRoot>:
    orientation: "vertical"
    padding: dp(12)
    spacing: dp(8)

    Label:
        text: "MYRana — لوحة الوالدين (Kivy)"
        size_hint_y: None
        height: dp(32)

    TextInput:
        id: host_input
        hint_text: "موقع لحظره (مثال: bad.example)"
        multiline: False
        size_hint_y: None
        height: dp(40)

    TextInput:
        id: pkg_input
        hint_text: "حزمة تطبيق (مثال: com.bad.app)"
        multiline: False
        size_hint_y: None
        height: dp(40)

    BoxLayout:
        size_hint_y: None
        height: dp(44)
        spacing: dp(8)
        Button:
            text: "إضافة موقع"
            on_release: root.add_host()
        Button:
            text: "إضافة حزمة"
            on_release: root.add_pkg()

    Button:
        text: "مزامنة مع الخادم"
        size_hint_y: None
        height: dp(48)
        on_release: root.sync_server()

    ScrollView:
        Label:
            text: root.status_text
            size_hint_y: None
            text_size: self.width, None
            height: self.texture_size[1]
            valign: "top"
"""


class MyRoot(BoxLayout):
    status_text = StringProperty("")

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._repo = PolicyRepository()
        self.refresh_view()

    def refresh_view(self, headline: str = "") -> None:
        lines: list[str] = []
        if headline:
            lines.append(headline)
            lines.append("")
        import db_store

        conn = self._repo.connection
        lines.append("المواقع المحلية:")
        for row in db_store.list_hosts(conn):
            flag = "*" if row["pending"] else ""
            lines.append(f"  • {row['host']}{flag}")
        lines.append("")
        lines.append("التطبيقات المحلية:")
        for row in db_store.list_packages(conn):
            flag = "*" if row["pending"] else ""
            lines.append(f"  • {row['package']}{flag}")
        lines.append("")
        lines.append("* تعني أن السجل بانتظار الرفع للخادم.")
        self.status_text = "\n".join(lines)

    def add_host(self) -> None:
        self._repo.add_blocked_site(self.ids.host_input.text)
        self.ids.host_input.text = ""
        self.refresh_view()

    def add_pkg(self) -> None:
        self._repo.add_blocked_package(self.ids.pkg_input.text)
        self.ids.pkg_input.text = ""
        self.refresh_view()

    def sync_server(self) -> None:
        try:
            msg = self._repo.sync_with_server()
            self.refresh_view(headline=msg)
        except Exception as exc:  # noqa: BLE001
            self.refresh_view(headline=f"خطأ في المزامنة: {exc}")


class ParentConsoleApp(App):
    def build(self):
        Builder.load_string(KV)
        return MyRoot()


if __name__ == "__main__":
    ParentConsoleApp().run()
