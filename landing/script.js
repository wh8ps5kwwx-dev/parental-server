(function () {
  "use strict";

  var toggle = document.querySelector(".nav-toggle");
  var links = document.querySelector(".nav-links");
  if (toggle && links) {
    toggle.addEventListener("click", function () {
      var open = links.classList.toggle("open");
      toggle.setAttribute("aria-expanded", open ? "true" : "false");
    });
    links.querySelectorAll("a").forEach(function (a) {
      a.addEventListener("click", function () {
        links.classList.remove("open");
        toggle.setAttribute("aria-expanded", "false");
      });
    });
  }

  document.querySelectorAll(".faq-item").forEach(function (item) {
    var btn = item.querySelector(".faq-q");
    if (!btn) return;
    btn.addEventListener("click", function () {
      var wasOpen = item.classList.contains("open");
      document.querySelectorAll(".faq-item.open").forEach(function (el) {
        el.classList.remove("open");
        var q = el.querySelector(".faq-q");
        if (q) q.setAttribute("aria-expanded", "false");
      });
      if (!wasOpen) {
        item.classList.add("open");
        btn.setAttribute("aria-expanded", "true");
      }
    });
  });

  var reveals = document.querySelectorAll(".reveal");
  if ("IntersectionObserver" in window) {
    var io = new IntersectionObserver(
      function (entries) {
        entries.forEach(function (entry) {
          if (entry.isIntersecting) {
            entry.target.classList.add("visible");
            io.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.12, rootMargin: "0px 0px -40px 0px" }
    );
    reveals.forEach(function (el) {
      io.observe(el);
    });
  } else {
    reveals.forEach(function (el) {
      el.classList.add("visible");
    });
  }

  var form = document.getElementById("contact-form");
  if (!form) return;

  function setError(id, message) {
    var el = document.getElementById(id + "-error");
    if (el) el.textContent = message || "";
  }

  function validEmail(value) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(value);
  }

  form.addEventListener("submit", function (event) {
    event.preventDefault();
    var name = (form.elements.namedItem("name") || {}).value || "";
    var email = (form.elements.namedItem("email") || {}).value || "";
    var subject = (form.elements.namedItem("subject") || {}).value || "";
    var message = (form.elements.namedItem("message") || {}).value || "";
    var ok = true;

    name = name.trim();
    email = email.trim();
    subject = subject.trim();
    message = message.trim();

    setError("name", "");
    setError("email", "");
    setError("subject", "");
    setError("message", "");

    if (name.length < 2) {
      setError("name", "Please enter your name (at least 2 characters).");
      ok = false;
    }
    if (!validEmail(email)) {
      setError("email", "Please enter a valid email address.");
      ok = false;
    }
    if (subject.length < 3) {
      setError("subject", "Please enter a subject.");
      ok = false;
    }
    if (message.length < 10) {
      setError("message", "Please write a message of at least 10 characters.");
      ok = false;
    }

    var success = document.getElementById("form-success");
    if (!ok) {
      if (success) success.classList.remove("show");
      return;
    }

    form.reset();
    if (success) {
      success.classList.add("show");
      success.focus();
    }
  });
})();
