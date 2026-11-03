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

  function copySignature(btn) {
    var text = btn.getAttribute('data-sig');
    if (!text || !navigator.clipboard) return;
    navigator.clipboard.writeText(text).then(function () {
      var live = document.getElementById('e-live');
      if (live) { live.textContent = 'Skopiowano sygnaturę ' + text; }
    });
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
  });

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', restoreNav);
  } else {
    restoreNav();
  }
})();
