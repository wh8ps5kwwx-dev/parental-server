# -*- coding: utf-8 -*-
"""توليد catalog.json من القوائم الافتراضية — شغّل: python blocklists/generate_catalog.py"""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent

PKG: dict[str, str] = {
    "Granny": "com.dvloper.granny",
    "Granny Chapter Two": "com.dvloper.grannychaptertwo",
    "Granny 3": "com.dvloper.granny3",
    "Evil Nun": "com.keplerians.evilnun",
    "Evil Nun 2": "com.keplerians.evilnun2",
    "The Baby In Yellow": "com.teamterrible.thebabyinyellow",
    "Poppy Playtime": "com.mobentertainment.poppyplaytime",
    "Poppy Playtime Chapter 2": "com.mobentertainment.poppyplaytimech2",
    "Poppy Playtime Chapter 3": "com.mobentertainment.poppyplaytimech3",
    "Five Nights at Freddy's": "com.clickteam.freddys1",
    "FNAF": "com.clickteam.freddys1",
    "Hello Neighbor": "com.tinybuildgames.helloneighbor",
    "Ice Scream": "com.keplerians.icescream",
    "Ice Scream 2": "com.keplerians.icescreamtwo",
    "Ice Scream 3": "com.keplerians.icescreamthree",
    "Ice Scream 4": "com.keplerians.icescreamfour",
    "Mr Meat": "com.keplerians.mrmeat",
    "Mr Meat 2": "com.keplerians.mrmeat2",
    "Slendrina": "com.dvloper.slendrina",
    "Eyes Horror Game": "com.fear.eyes.horror.game",
    "Specimen Zero": "com.specimen.zero.horror",
    "Horrorfield": "com.te.games.horrorfield",
    "Dead by Daylight Mobile": "com.netease.dbdm",
    "Into the Dead": "com.pikpok.dr2",
    "Mental Hospital": "com.agaming.mentalhospital",
    "Siren Head": "com.dogame.sirenhead",
    "Scary Teacher 3D": "com.zakg.scaryteacher.hellgame",
    "Dark Riddle": "com.qaiboing.darkriddle",
    "Bendy and the Ink Machine": "com.jds.batim",
    "Amanda the Adventurer": "com.mangorama.amandatheadventurer",
    "OmeTV": "ome.tv",
    "Azar": "com.azarlive.android",
    "Monkey": "com.holla.monkey",
    "HOLLA": "com.holla.android",
    "LivU": "com.livechat.livu",
    "Mico": "com.mico",
    "Tumile": "com.tumile.app",
    "Yubo": "com.yubo.app",
    "Chatroulette": "com.chatroulette.android",
    "Camsurf": "com.camsurf.android",
    "Chatspin": "com.chatspin",
    "Chatrandom": "com.chatrandom",
    "Chatous": "com.chatous.chatous",
    "Wink": "com.wink.app",
    "Hoop": "com.hoop.app",
    "Emerald Chat": "com.emeraldchat",
    "Minichat": "com.minichat",
    "CooMeet": "com.coomeet",
    "Tinder": "com.tinder",
    "Bumble": "com.bumble.app",
    "Badoo": "com.badoo.mobile",
    "Tagged": "com.taggedapp",
    "Skout": "com.skout.android",
    "MeetMe": "com.myyearbook.m",
    "Jaumo": "com.jaumo",
    "OkCupid": "com.okcupid.okcupid",
    "Hinge": "co.hinge.app",
    "Grindr": "com.grindrapp.android",
    "Happn": "com.happn",
    "Muzz": "com.muzmatch.muzmatchapp",
    "Waplog": "com.waplog.social",
    "LOVOO": "net.lovoo.android",
    "Plenty of Fish": "com.pof.android",
    "Twoo": "com.twoo",
    "1xBet": "org.xbet.client1",
    "Bet365": "com.bet365Wrapper.Bet365_Application",
    "MelBet": "com.melbet.sport",
    "Parimatch": "com.parimatch",
    "22Bet": "com.twentytwobet",
    "PokerStars": "com.pyrsoftware.pokerstars.eu",
    "LeoVegas": "com.leovegas.leovegas",
    "Betway": "com.betway.sports",
    "Stake": "com.stake.stake",
    "Mostbet": "com.mostbet.android",
    "Slotomania": "com.playtika.slotomania",
    "Zynga Poker": "com.zynga.livepoker",
    "Turbo VPN": "free.vpn.unblock.proxy.turbovpn",
    "SuperVPN": "com.jrzheng.supervpn",
    "Thunder VPN": "com.fast.free.unblock.thunder.vpn",
    "Snap VPN": "com.snapvpn",
    "Secure VPN": "com.secure.vpn",
    "Psiphon": "com.psiphon3",
    "Proton VPN": "ch.protonvpn.android",
    "NordVPN": "com.nordvpn.android",
    "ExpressVPN": "com.expressvpn.vpn",
    "Surfshark VPN": "com.surfshark.vpnclient.android",
    "Hotspot Shield": "hotspotshield.android.vpn",
    "Betternet VPN": "com.betternet",
    "Hola VPN": "org.hola",
    "Windscribe VPN": "com.windscribe.vpn",
    "Mullvad VPN": "net.mullvad.mullvad",
    "Orbot": "org.torproject.android",
    "Tor Browser": "org.torproject.torbrowser",
    "Aloha Browser": "com.aloha.browser",
    "Brave Private Browser": "com.brave.browser",
    "DuckDuckGo Browser": "com.duckduckgo.mobile.android",
    "Character AI": "ai.character.app",
    "Chai AI": "com.Beauchamp.Messenger.external",
    "Replika": "ai.replika.app",
    "Telegram X": "org.thunderdog.challegram",
    "Signal Private Messenger": "org.thoughtcrime.securesms",
}

APPS = """Maryam Game
Mariam Game
لعبة مريم
Granny
Granny Chapter Two
Granny 3
Evil Nun
Evil Nun 2
The Baby In Yellow
Poppy Playtime
Poppy Playtime Chapter 2
Poppy Playtime Chapter 3
Five Nights at Freddy's
FNAF
Hello Neighbor
Ice Scream
Ice Scream 2
Ice Scream 3
Ice Scream 4
Mr Meat
Mr Meat 2
Slendrina
Slendrina The Cellar
Eyes Horror Game
Specimen Zero
Horrorfield
Dead by Daylight Mobile
Into the Dead
Mental Hospital
Siren Head
Scary Teacher 3D
Dark Riddle
Bendy and the Ink Machine
Amanda the Adventurer
OmeTV
Azar
Monkey
HOLLA
LivU
Mico
Tumile
Yubo
Chatroulette
Camsurf
Chatspin
Chatrandom
Chatous
Wink
Hoop
Emerald Chat
Minichat
CooMeet
Omegle Alternative
Random Chat
Stranger Chat
Tinder
Bumble
Badoo
Tagged
Skout
MeetMe
Jaumo
OkCupid
Hinge
Grindr
Happn
Muzz
Waplog
LOVOO
Plenty of Fish
Twoo
1xBet
Bet365
MelBet
Parimatch
22Bet
888 Casino
PokerStars
LeoVegas
Betway
Stake
BC.Game
Mostbet
Casino Games
Slotomania
Zynga Poker
Turbo VPN
SuperVPN
VPN Proxy Master
Thunder VPN
Snap VPN
Secure VPN
X-VPN
Psiphon
Proton VPN
NordVPN
ExpressVPN
Surfshark VPN
Hotspot Shield
Betternet VPN
Hola VPN
Windscribe VPN
Mullvad VPN
Orbot
Tor Browser
Onion Browser
Aloha Browser
Brave Private Browser
DuckDuckGo Browser
Character AI
Chai AI
Replika
Anima AI
Talkie AI
CrushOn AI
Janitor AI
SpicyChat AI
Poly AI
AI Girlfriend
AI Boyfriend
Anonymous Chat
Whisper
Tellonym
NGL
Sarahah
ASKfm
Session Messenger
Signal Private Messenger
Telegram X""".strip().splitlines()

SITES = """maryamgame.com
granny-game.com
poppyplaytime.com
mobentertainment.com
fivenightsatfreddys.com
fnafworld.com
helloneighbor.com
bendyandtheinkmachine.com
deadbydaylight.com
horrorfield.com
scaryteacher3d.com
poki.com
crazygames.com
silvergames.com
ome.tv
chatroulette.com
monkey.app
holla.world
azar.live
livu.me
yubo.live
camsurf.com
chatspin.com
chatrandom.com
chatous.com
emeraldchat.com
minichat.com
coomeet.com
shagle.com
omegle.com
omegleweb.com
tinder.com
bumble.com
badoo.com
tagged.com
skout.com
meetme.com
jaumo.com
okcupid.com
hinge.co
grindr.com
happn.com
muzz.com
waplog.com
lovoo.com
pof.com
1xbet.com
bet365.com
melbet.com
parimatch.com
22bet.com
888casino.com
pokerstars.com
leovegas.com
betway.com
stake.com
bc.game
mostbet.com
casino.org
slotomania.com
zyngapoker.com
nordvpn.com
expressvpn.com
surfshark.com
protonvpn.com
psiphon.ca
hotspotshield.com
betternet.co
hola.org
windscribe.com
mullvad.net
torproject.org
orbot.app
onionbrowser.com
hidemyass.com
proxysite.com
hide.me
kproxy.com
croxyproxy.com
4everproxy.com
vpnbook.com
character.ai
chai-research.com
replika.com
anima-ai.com
talkie-ai.com
crushon.ai
janitorai.com
spicychat.ai
poly.ai
tellonym.me
ngl.link
ask.fm
whisper.sh
session.org
signal.org""".strip().splitlines()

KEYWORDS = """لعبة مريم
مريم المرعبة
تحدي مريم
Maryam game
Mariam game
Granny horror
Evil Nun
Poppy Playtime
Huggy Wuggy
Kissy Missy
Five Nights at Freddy's
FNAF jumpscare
Siren Head
Slendrina
Eyes horror
scary ritual
creepy ritual
summoning demon
real ghost
ghost challenge
horror prank
scary clown
dark web horror
Blue Whale Challenge
Momo Challenge
تحدي الحوت الأزرق
تحدي مومو
self harm challenge
knife challenge
choking challenge
fire challenge
dangerous challenge
roof jumping
train challenge
car surfing
blackout challenge
bloody fight
violent fight
street fight
knife fight
gun fight
school fight
قتل
دم
تعذيب
مشاجرة دموية
سلاح
سكين
مسدس
عنف شديد
مشاهد دموية
drug challenge
smoking challenge
vape tricks
weed
cannabis
alcohol challenge
drinking challenge
مخدرات
حشيش
تدخين
فيب
كحول
سكران
dating chat
stranger video chat
random video chat
girls chat
boys chat
adult chat
meet strangers
تعارف بنات
تعارف شباب
دردشة بنات
دردشة عشوائية
فيديو شات
شات غرباء
casino tricks
betting tips
1xbet tricks
slot machine
poker online
ربح مراهنات
مراهنات
قمار
كازينو
بوكر""".strip().splitlines()


def main() -> None:
    packages: set[str] = set()
    labels: list[str] = []
    for name in APPS:
        name = name.strip()
        if not name:
            continue
        if name in PKG:
            packages.add(PKG[name].lower())
        elif "." in name and " " not in name:
            packages.add(name.lower())
        else:
            labels.append(name)
    for pkg in PKG.values():
        if pkg and "." in pkg:
            packages.add(pkg.lower())

    sites = sorted(
        {
            s.strip().lower().replace("https://", "").split("/")[0]
            for s in SITES
            if s.strip()
        }
    )
    keywords = list(dict.fromkeys(k.strip() for k in KEYWORDS if k.strip()))

    catalog = {
        "version": 1,
        "description": "قائمة الحظر الافتراضية — تنبيه الأم عند كل محاولة فتح",
        "packages": sorted(packages),
        "app_labels": sorted(set(labels), key=str.lower),
        "sites": sites,
        "video_keywords": keywords,
    }
    (ROOT / "catalog.json").write_text(
        json.dumps(catalog, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    (ROOT / "app_package_map.json").write_text(
        json.dumps(PKG, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(
        f"OK packages={len(packages)} sites={len(sites)} keywords={len(keywords)} labels={len(labels)}"
    )


if __name__ == "__main__":
    main()
