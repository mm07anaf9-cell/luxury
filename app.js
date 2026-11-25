// Luxury Motors - front-end showcase. Demo only: nothing is sent, stored or saved.
(function () {
  var pages = [["index.html","Home"],["login.html","Login"],["catalog_page.html","Catalog"],["services.html","Services"],["contact.html","Contact"]];
  var cur = location.pathname.split("/").pop() || "index.html";
  var nav = document.getElementById("nav");
  if (nav) {
    nav.innerHTML = '<a class="luxurybutton" href="index.html">Luxury Motors</a><ul>' +
      pages.map(function (p) { return '<li><a href="' + p[0] + '"' + (p[0] === cur ? ' class="active"' : '') + '>' + p[1] + '</a></li>'; }).join("") + '</ul>';
  }
  // Theme: follows the system setting until the visitor picks one. Only the theme name is remembered.
  var root = document.documentElement, KEY = "lm-theme";
  function savedTheme() { try { return localStorage.getItem(KEY); } catch (e) { return null; } }
  function applyTheme(t) { root.setAttribute("data-theme", t); var b = document.getElementById("theme-toggle"); if (b) { b.setAttribute("aria-pressed", t === "dark"); b.textContent = t === "dark" ? "Light mode" : "Night mode"; } }
  var qs = /[?&]theme=(dark|light)/.exec(location.search);
  var start = (qs && qs[1]) || savedTheme() || (window.matchMedia && matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
  if (nav) {
    var tb = document.createElement("button");
    tb.id = "theme-toggle"; tb.type = "button"; tb.className = "theme-toggle";
    tb.addEventListener("click", function () {
      var t = root.getAttribute("data-theme") === "dark" ? "light" : "dark";
      applyTheme(t); try { localStorage.setItem(KEY, t); } catch (e) {}
    });
    nav.appendChild(tb);
  }
  applyTheme(start);

  // Scroll reveal
  var reduce = window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches;
  window.LM_reveal = function (scope) {
    var els = (scope || document).querySelectorAll(".reveal:not(.in)");
    if (reduce || !("IntersectionObserver" in window)) { els.forEach(function (e) { e.classList.add("in"); }); return; }
    var io = new IntersectionObserver(function (en) {
      en.forEach(function (x) { if (x.isIntersecting) { x.target.classList.add("in"); io.unobserve(x.target); } });
    }, { threshold: 0.12 });
    els.forEach(function (e) { io.observe(e); });
  };

  window.LM = {
    cars: [
      {id:1, make:"Porsche", model:"911 Carrera", year:2023, price:135000, body:"Coupe", hp:379, note:"Rear-engine icon with everyday usability."},
      {id:2, make:"Mercedes-Benz", model:"S 500", year:2022, price:118000, body:"Sedan", hp:429, note:"Flagship comfort and a quiet cabin."},
      {id:3, make:"BMW", model:"X5 xDrive40i", year:2023, price:72000, body:"SUV", hp:335, note:"Family-size SUV with sport handling."},
      {id:4, make:"Audi", model:"RS e-tron GT", year:2023, price:147000, body:"Sedan", hp:637, note:"Electric grand tourer."},
      {id:5, make:"Range Rover", model:"Sport", year:2022, price:96000, body:"SUV", hp:395, note:"Commanding view, off-road capable."},
      {id:6, make:"Bentley", model:"Continental GT", year:2021, price:205000, body:"Coupe", hp:542, note:"Hand-finished long-distance coupe."}
    ],

    art: function (c) {
      return '<img src="img/car' + c.id + '.jpg" alt="' + c.year + ' ' + c.make + ' ' + c.model + ' (photo of a similar car)" loading="lazy">';
    },
    money: function (n) { return "$" + n.toLocaleString("en-US"); },
    msg: function (el, text, ok) { el.textContent = text; el.className = "msg " + (ok ? "ok" : "bad"); }
  };
  var email = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
  function demoNote(form) { return "Demo only: nothing was sent or saved."; }

  var signup = document.getElementById("signup-form");
  if (signup) signup.addEventListener("submit", function (e) {
    e.preventDefault();
    var m = document.getElementById("form-msg"), v = function (id) { return document.getElementById(id).value; };
    if (v("full-name").trim().length < 2) return LM.msg(m, "Enter your full name.", false);
    if (!email.test(v("email"))) return LM.msg(m, "Enter a valid email.", false);
    if (v("password").length < 8) return LM.msg(m, "Password must be at least 8 characters.", false);
    if (v("password") !== v("confirm-password")) return LM.msg(m, "Passwords do not match.", false);
    if (!document.getElementById("terms").checked) return LM.msg(m, "Please accept the terms.", false);
    signup.reset();
    LM.msg(m, "Form looks good. " + demoNote(), true);
  });

  var login = document.getElementById("login-form");
  if (login) login.addEventListener("submit", function (e) {
    e.preventDefault();
    var m = document.getElementById("form-msg");
    if (!email.test(document.getElementById("email").value)) return LM.msg(m, "Enter a valid email.", false);
    if (document.getElementById("password").value.length < 8) return LM.msg(m, "Password must be at least 8 characters.", false);
    login.reset();
    LM.msg(m, "Form looks good. " + demoNote() + " No real accounts exist on this showcase.", true);
  });

  var contact = document.getElementById("contact-form");
  if (contact) contact.addEventListener("submit", function (e) {
    e.preventDefault();
    var m = document.getElementById("form-msg");
    if (document.getElementById("name").value.trim().length < 2) return LM.msg(m, "Enter your name.", false);
    if (!email.test(document.getElementById("email").value)) return LM.msg(m, "Enter a valid email.", false);
    if (document.getElementById("message").value.trim().length < 5) return LM.msg(m, "Write a short message.", false);
    contact.reset();
    LM.msg(m, "Looks good. " + demoNote(), true);
  });

  document.querySelectorAll(".page > h1, .page > p, .svc > div, .page form, .main .text, .main form").forEach(function (e, i) { e.classList.add("reveal"); e.style.setProperty("--i", i % 6); });
  LM_reveal();
  var grid = document.getElementById("grid");
  if (grid) {
    var make = document.getElementById("f-make"), body = document.getElementById("f-body"), max = document.getElementById("f-max"), sort = document.getElementById("f-sort");
    var makes = Array.from(new Set(LM.cars.map(function (c) { return c.make; }))).sort();
    makes.forEach(function (m) { make.add(new Option(m, m)); });
    var dlg = document.getElementById("detail");
    function render() {
      var list = LM.cars.filter(function (c) {
        return (!make.value || c.make === make.value) && (!body.value || c.body === body.value) && (!max.value || c.price <= +max.value);
      });
      list.sort(function (a, b) { return sort.value === "high" ? b.price - a.price : a.price - b.price; });
      document.getElementById("count").textContent = list.length + " of " + LM.cars.length + " sample listings";
      grid.innerHTML = "";
      if (!list.length) { grid.innerHTML = '<p class="empty">No cars match these filters.</p>'; return; }
      list.forEach(function (c, i) {
        var d = document.createElement("div"); d.className = "card reveal"; d.style.setProperty("--i", i % 6);
        d.innerHTML = '<div class="thumb">' + LM.art(c) + '<a class="tag" href="credits.html">Photo credit</a></div><h3></h3><p class="meta"></p><p class="price"></p><button class="btn small" type="button">View details</button>';
        d.querySelector("h3").textContent = c.make + " " + c.model;
        d.querySelector(".meta").textContent = c.year + " · " + c.body + " · " + c.hp + " hp";
        d.querySelector(".price").textContent = LM.money(c.price);
        d.querySelector("button").addEventListener("click", function () {
          document.getElementById("d-art").innerHTML = LM.art(c);
          document.getElementById("d-title").textContent = c.year + " " + c.make + " " + c.model;
          document.getElementById("d-body").textContent = c.note + " " + c.body + ", " + c.hp + " hp. Price: " + LM.money(c.price) + ". Sample listing for this showcase.";
          dlg.className = "modal open"; document.getElementById("d-close").focus();
        });
        grid.appendChild(d);
      });
      LM_reveal(grid);
    }
    [make, body, max, sort].forEach(function (el) { el.addEventListener("change", render); });
    document.getElementById("d-close").addEventListener("click", function () { dlg.className = "modal"; });
    dlg.addEventListener("click", function (e) { if (e.target === dlg) dlg.className = "modal"; });
    document.addEventListener("keydown", function (e) { if (e.key === "Escape") dlg.className = "modal"; });
    render();
  }
})();
