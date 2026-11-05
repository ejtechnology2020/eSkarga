/* eSkarga – skróty klawiszowe i drobne zachowania szkieletu (bez eval, zgodne z CSP).
   Skróty nie działają podczas pisania w polach tekstowych i mają odpowiedniki w menu. */
(function () {
  'use strict';

  function isTyping(el) {
    if (!el) return false;
    var tag = el.tagName;
    return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || el.isContentEditable;
  }

  function toggleNav() {
    var wrap = document.querySelector('.e-navwrap');
    var nav = document.querySelector('.e-nav');
    if (!wrap || !nav) return;
    var mini = wrap.classList.toggle('mini');
    nav.classList.toggle('mini', mini);
    try { window.sessionStorage.setItem('eskarga.nav.mini', mini ? '1' : '0'); } catch (e) { /* brak storage */ }
  }

  function restoreNav() {
    var mini = null;
    try { mini = window.sessionStorage.getItem('eskarga.nav.mini'); } catch (e) { /* brak storage */ }
    if (mini === '1') {
      var wrap = document.querySelector('.e-navwrap');
      var nav = document.querySelector('.e-nav');
      if (wrap && nav) { wrap.classList.add('mini'); nav.classList.add('mini'); }
    }
  }

  function focusQuickSearch() {
    var qs = document.getElementById('qs-sig');
    if (qs) { qs.focus(); qs.select(); }
  }

  function announce(text) {
    var live = document.getElementById('e-live');
    if (!live) {
      live = document.createElement('div');
      live.id = 'e-live'; live.className = 'sr'; live.setAttribute('aria-live', 'polite');
      document.body.appendChild(live);
    }
    live.textContent = text;
  }

  /* Kopiowanie do schowka: navigator.clipboard (bezpieczny kontekst) albo tymczasowe pole + execCommand. */
  function copyText(text, done) {
    if (!text) return;
    if (navigator.clipboard && window.isSecureContext) {
      navigator.clipboard.writeText(text).then(function () { announce(done); }, function () { copyLegacy(text, done); });
    } else {
      copyLegacy(text, done);
    }
  }

  function copyLegacy(text, done) {
    var ta = document.createElement('textarea');
    ta.value = text; ta.setAttribute('readonly', ''); ta.style.position = 'fixed'; ta.style.left = '-9999px';
    document.body.appendChild(ta); ta.select();
    var ok = false;
    try { ok = document.execCommand('copy'); } catch (e) { ok = false; }
    document.body.removeChild(ta);
    announce(ok ? done : 'Nie udało się skopiować – zaznacz i skopiuj tekst ręcznie');
  }

  function copySignature(btn) {
    var text = btn.getAttribute('data-sig');
    copyText(text, 'Skopiowano sygnaturę ' + text);
  }

  /* data-action="copy" data-text="…": np. kod błędu dla administratora na stronie 500 (bez skryptów inline – CSP). */
  function copyGeneric(btn) {
    var text = btn.getAttribute('data-text');
    copyText(text, 'Skopiowano: ' + text);
    var label = btn.getAttribute('data-done');
    if (label) { var prev = btn.textContent; btn.textContent = label; setTimeout(function () { btn.textContent = prev; }, 2000); }
  }

  document.addEventListener('keydown', function (ev) {
    if (ev.ctrlKey || ev.metaKey || ev.altKey) return;
    if (isTyping(ev.target)) return;
    if (ev.key === '/') { ev.preventDefault(); focusQuickSearch(); }
    else if (ev.key === '[') { ev.preventDefault(); toggleNav(); }
    else if (ev.key === '?') {
      var help = document.getElementById('skroty');
      if (help) { help.hidden = !help.hidden; }
    }
  });

  document.addEventListener('click', function (ev) {
    var el = ev.target.closest ? ev.target.closest('[data-action]') : null;
    if (!el) return;
    var action = el.getAttribute('data-action');
    if (action === 'toggle-nav') { ev.preventDefault(); toggleNav(); }
    else if (action === 'copy-sig') { ev.preventDefault(); copySignature(el); }
    else if (action === 'copy') { ev.preventDefault(); copyGeneric(el); }
    else if (action === 'reload') { ev.preventDefault(); window.location.reload(); }
  });

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', restoreNav);
  } else {
    restoreNav();
  }
})();
