[app]
title = MYRana Mother
package.name = myrana_mother
package.domain = org.myrana
source.dir = .
source.include_exts = py,png,jpg,kv,atlas,ttf
source.include_patterns = guardian_api.py,main.py,Arabic.ttf
version = 1.0.0
requirements = python3,kivy,requests,arabic-reshaper,python-bidi,openssl
orientation = portrait
fullscreen = 0

[android]
android.permissions = INTERNET
android.api = 31
android.minapi = 24
android.ndk = 25b
android.accept_sdk_license = True
android.archs = arm64-v8a, armeabi-v7a

[buildozer]
log_level = 2
warn_on_root = 1
