/* Podpis dwuetapowy z aplikacją lokalną (D10, ADR 0008): przeglądarka pobiera łańcuch certyfikatów z http://127.0.0.1:17227,
   przekazuje go do serwera (krok 1 → skrót), prosi aplikację lokalną o podpis skrótu i oddaje wartość podpisu (krok 2).
   Dokument nigdy nie trafia do aplikacji lokalnej; aplikacja lokalna nigdy nie widzi sesji eSkargi. */
(function () {
  'use strict';
  const BASE = window.ESKARGA_SIGNER_URL || 'http://127.0.0.1:17227';
  function val(id, v) { const el = document.querySelector('[id$="' + id + '"]'); if (el) { if (v !== undefined) { el.value = v; } return el.value; } return null; }
  async function status() {
    const r = await fetch(BASE + '/status', { mode: 'cors' });
    if (!r.ok) { throw new Error('Aplikacja do podpisu nie jest gotowa (' + r.status + ')'); }
    return r.json();
  }
  async function loadCertificates() {
    const r = await fetch(BASE + '/certyfikaty', { mode: 'cors' });
    if (!r.ok) { throw new Error('Nie udało się pobrać certyfikatu z karty'); }
    const j = await r.json();
    val('certyfikaty', j.lancuch.join(','));
    if (j.algorytm) { val('algorytm', j.algorytm); }
    return j;
  }
  async function signDigest() {
    const skrot = val('skrot');
    if (!skrot) { throw new Error('Brak skrótu do podpisu – wykonaj krok 1'); }
    const r = await fetch(BASE + '/podpisz', { method: 'POST', mode: 'cors', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ skrot: skrot, algorytmSkrotu: 'SHA-256' }) });
    if (!r.ok) { const e = await r.json().catch(function () { return {}; }); throw new Error(e.komunikat || ('Podpis odrzucony (' + r.status + ')')); }
    const j = await r.json();
    val('podpisWartosc', j.podpis);
    return j;
  }
  window.eskargaSign = { status: status, loadCertificates: loadCertificates, signDigest: signDigest };
  document.addEventListener('click', async function (ev) {
    const btn = ev.target.closest('[data-sign-step]');
    if (!btn) { return; }
    const step = btn.getAttribute('data-sign-step');
    const err = document.getElementById('sign-error');
    try {
      if (step === 'certyfikaty') { await status(); await loadCertificates(); }
      if (step === 'podpisz') { await signDigest(); }
      if (err) { err.textContent = ''; }
    } catch (e) {
      ev.preventDefault(); ev.stopImmediatePropagation();
      if (err) { err.textContent = e.message; }
    }
  }, true);
})();
