from flask import Flask, request, jsonify
from datetime import datetime
import sqlite3

app = Flask(__name__)

DB = "db.sqlite3"
API_KEY = "graduation-secret-key"

def now():
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")

def db():
    conn = sqlite3.connect(DB)
    conn.row_factory = sqlite3.Row
    return conn

def init():
    conn = db()
    cur = conn.cursor()

    cur.execute("""
    CREATE TABLE IF NOT EXISTS children (
        id INTEGER PRIMARY KEY,
        name TEXT,
        age INTEGER,
        child_code TEXT
    )
    """)

    cur.execute("""
    CREATE TABLE IF NOT EXISTS commands (
        id INTEGER PRIMARY KEY,
        action TEXT,
        value TEXT,
        child_code TEXT,
        executed INTEGER,
        time TEXT
    )
    """)

    cur.execute("""
    CREATE TABLE IF NOT EXISTS reports (
        id INTEGER PRIMARY KEY,
        event TEXT,
        value TEXT,
        child_code TEXT,
        time TEXT
    )
    """)

    cur.execute("""
    CREATE TABLE IF NOT EXISTS alerts (
        id INTEGER PRIMARY KEY,
        message TEXT,
        child_code TEXT,
        time TEXT
    )
    """)

    conn.commit()
    conn.close()

init()

@app.before_request
def auth():
    if request.path == "/":
        return
    if request.headers.get("X-API-KEY") != API_KEY:
        return jsonify({"error": "Unauthorized"}), 401

@app.route("/")
def home():
    return jsonify({"status": "running"})

@app.route("/add-child", methods=["POST"])
def add_child():
    data = request.get_json()

    conn = db()
    cur = conn.cursor()

    cur.execute("INSERT INTO children (name, age, child_code) VALUES (?, ?, ?)", (
        data.get("name"),
        data.get("age"),
        data.get("child_code")
    ))

    cur.execute("INSERT INTO reports (event, value, child_code, time) VALUES (?, ?, ?, ?)", (
        "child_added",
        data.get("name"),
        data.get("child_code"),
        now()
    ))

    conn.commit()
    conn.close()

    return jsonify({"status": "added"})

@app.route("/send-command", methods=["POST"])
def send():
    data = request.get_json()

    conn = db()
    cur = conn.cursor()

    cur.execute("""
    INSERT INTO commands (action, value, child_code, executed, time)
    VALUES (?, ?, ?, ?, ?)
    """, (
        data.get("action"),
        data.get("value"),
        data.get("child_code"),
        0,
        now()
    ))

    cur.execute("INSERT INTO reports (event, value, child_code, time) VALUES (?, ?, ?, ?)", (
        "command_sent",
        data.get("action"),
        data.get("child_code"),
        now()
    ))

    conn.commit()
    conn.close()

    return jsonify({"status": "sent"})

@app.route("/get-command")
def get():
    code = request.args.get("child_code")

    conn = db()
    cur = conn.cursor()

    cur.execute("""
    SELECT * FROM commands
    WHERE child_code=? AND executed=0
    ORDER BY id DESC LIMIT 1
    """, (code,))

    cmd = cur.fetchone()

    if not cmd:
        return jsonify({"action": "none"})

    cur.execute("UPDATE commands SET executed=1 WHERE id=?", (cmd["id"],))
    conn.commit()
    conn.close()

    return jsonify(dict(cmd))

@app.route("/reports")
def reports():
    code = request.args.get("child_code")

    conn = db()
    cur = conn.cursor()

    cur.execute("SELECT * FROM reports WHERE child_code=? ORDER BY id DESC", (code,))
    rows = cur.fetchall()

    conn.close()

    return jsonify([dict(r) for r in rows])

@app.route("/alerts")
def alerts():
    code = request.args.get("child_code")

    conn = db()
    cur = conn.cursor()

    cur.execute("SELECT * FROM alerts WHERE child_code=? ORDER BY id DESC", (code,))
    rows = cur.fetchall()

    conn.close()

    return jsonify([dict(r) for r in rows])

@app.route("/add-alert", methods=["POST"])
def add_alert():
    data = request.get_json()

    conn = db()
    cur = conn.cursor()

    cur.execute("INSERT INTO alerts (message, child_code, time) VALUES (?, ?, ?)", (
        data.get("message"),
        data.get("child_code"),
        now()
    ))

    conn.commit()
    conn.close()

    return jsonify({"status": "ok"})

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)