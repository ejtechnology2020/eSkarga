/* Widżet PDF eSkarga (T-034, T-035). pdf.js ładowany jako moduł z własnej domeny (webjar), worker lokalny, isEvalSupported=false.
   Strony renderowane leniwie (IntersectionObserver) – dokument 200 stron otwiera się bez pobierania całości (HTTP Range).
   Skróty (brief §6): J/K strona, +/- zoom, / szukaj, Esc zamyka wyszukiwanie. Nakładka zaznaczeń z data-highlights (JSON).
   Kilka dokumentów jednego pisma: chipy .pdfv-doc[data-src] przełączają dokument w tym samym widżecie. Dopasowanie do panelu ≤ 100 %. */
(function () {
  'use strict';
  const SCALE_STEP = 0.15;

  async function loadLib(url) {
    if (window.__eskargaPdfjs) { return window.__eskargaPdfjs; }
    window.__eskargaPdfjs = import(url);
    return window.__eskargaPdfjs;
  }

  async function mount(root) {
    if (root.dataset.mounted) { return; }
    root.dataset.mounted = '1';
    const status = root.querySelector('.pdfv-status');
    const pagesEl = root.querySelector('.pdfv-pages');
    const scroll = root.querySelector('.pdfv-scroll');
    const pageFrom = parseInt(root.dataset.pageFrom || '1', 10) || 1;
    let highlights = [];
    try { highlights = JSON.parse(root.dataset.highlights || '[]'); } catch (e) { highlights = []; }
    let scale = 1.0;
    let pdf = null;
    let current = 1;
    let pdfjs;
    let base = null;        // viewport pierwszej strony w skali 1.33 (do dopasowania)
    let autoFit = true;     // dopasowanie do szerokości panelu, dopóki użytkownik nie zmieni powiększenia ręcznie
    let holders = [];
    let rendered = new Map();
    let io = null;
    let generation = 0;     // numer otwartego dokumentu – render strony poprzedniego dokumentu jest ignorowany

    try {
      pdfjs = await loadLib(root.dataset.lib);
      pdfjs.GlobalWorkerOptions.workerSrc = root.dataset.worker;
    } catch (e) {
      status.textContent = 'Nie udało się otworzyć dokumentu. ' + (e && e.message ? e.message : '');
      return;
    }

    // Dopasowanie automatyczne nigdy nie przekracza 100 % (skala rzeczywista); większe powiększenie tylko ręcznie (+, Dopasuj wraca do auto).
    function fitScale() {
      if (!base) { return 1.0; }
      const w = scroll.clientWidth - 2 * (parseFloat(getComputedStyle(scroll).paddingLeft) || 0) - 2;
      return w > 100 ? Math.max(0.5, Math.min(1.0, w / base.width)) : 1.0;
    }

    async function render(i, force) {
      const gen = generation;
      const holder = holders[i - 1];
      if (!holder || (!force && rendered.get(i) === scale)) { return; }
      rendered.set(i, scale);
      const page = await pdf.getPage(i);
      if (gen !== generation) { return; }
      const viewport = page.getViewport({ scale: scale * 1.33 });
      holder.innerHTML = '';
      const canvas = document.createElement('canvas');
      canvas.width = Math.floor(viewport.width);
      canvas.height = Math.floor(viewport.height);
      holder.style.minHeight = canvas.height + 'px';
      holder.appendChild(canvas);
      await page.render({ canvasContext: canvas.getContext('2d'), viewport: viewport }).promise;
      if (gen !== generation) { return; }
      const textLayer = document.createElement('div');
      textLayer.className = 'pdfv-text';
      textLayer.style.width = canvas.width + 'px';
      textLayer.style.height = canvas.height + 'px';
      holder.appendChild(textLayer);
      try {
        const textContent = await page.getTextContent();
        const tl = new pdfjs.TextLayer({ textContentSource: textContent, container: textLayer, viewport: viewport });
        await tl.render();
      } catch (e) { /* bez warstwy tekstowej (skan bez OCR) */ }
      drawHighlights(holder, i, viewport);
    }

    function drawHighlights(holder, pageNo, viewport) {
      highlights.filter(function (h) { return h.page === pageNo; }).forEach(function (h) {
        const box = document.createElement('div');
        box.className = 'pdfv-hl';
        box.title = h.comment || 'zaznaczenie zespołu sędziego';
        const r = viewport.convertToViewportRectangle([h.x, h.y, h.x + h.w, h.y + h.h]);
        box.style.left = Math.min(r[0], r[2]) + 'px';
        box.style.top = Math.min(r[1], r[3]) + 'px';
        box.style.width = Math.abs(r[2] - r[0]) + 'px';
        box.style.height = Math.abs(r[3] - r[1]) + 'px';
        holder.appendChild(box);
      });
    }

    function setCurrent(i) {
      current = i;
      root.querySelector('.pdfv-num').value = String(i);
      root.querySelector('.pdfv-karta').textContent = String(pageFrom + i - 1);
    }
    function goTo(i) {
      if (!pdf) { return; }
      i = Math.max(1, Math.min(pdf.numPages, i));
      holders[i - 1].scrollIntoView({ block: 'start' });
      setCurrent(i);
    }
    function setScale(s) {
      scale = Math.max(0.5, Math.min(3, s));
      root.querySelector('.pdfv-zoom').textContent = Math.round(scale * 100) + ' %';
      holders.forEach(function (h, idx) { if (rendered.has(idx + 1)) { render(idx + 1, true); } });
    }
    async function search(q) {
      const found = root.querySelector('.pdfv-found');
      if (!q || !pdf) { found.textContent = ''; return; }
      let hits = 0; let first = 0;
      for (let i = 1; i <= pdf.numPages; i++) {
        const page = await pdf.getPage(i);
        const tc = await page.getTextContent();
        const text = tc.items.map(function (it) { return it.str; }).join(' ').toLowerCase();
        const n = text.split(q.toLowerCase()).length - 1;
        if (n > 0) { hits += n; if (!first) { first = i; } }
      }
      found.textContent = hits ? hits + ' trafień, pierwsze na stronie ' + first : 'brak trafień';
      if (first) { goTo(first); }
    }

    /** Otwiera dokument spod adresu src (pierwszy albo wybrany przełącznikiem); poprzedni dokument jest zamykany. */
    async function open(src, downloadUrl) {
      generation++;
      if (io) { io.disconnect(); io = null; }
      if (pdf) { try { pdf.destroy(); } catch (e) { /* ignorowane */ } pdf = null; }
      pagesEl.innerHTML = '';
      holders = [];
      rendered = new Map();
      root.dataset.src = src;
      const dl = root.querySelector('.pdfv-download');
      if (dl) { dl.href = downloadUrl || src; }
      root.querySelector('.pdfv-count').textContent = '…';
      root.querySelector('.pdfv-found').textContent = '';
      status.textContent = 'Ładowanie dokumentu…';
      try {
        pdf = await pdfjs.getDocument({ url: src, isEvalSupported: false, disableAutoFetch: true, disableStream: false, rangeChunkSize: 65536 }).promise;
        base = (await pdf.getPage(1)).getViewport({ scale: 1.33 });
      } catch (e) {
        status.textContent = 'Nie udało się otworzyć dokumentu. ' + (e && e.message ? e.message : '');
        return;
      }
      status.textContent = '';
      root.querySelector('.pdfv-count').textContent = String(pdf.numPages);
      if (autoFit) { scale = fitScale(); }
      root.querySelector('.pdfv-zoom').textContent = Math.round(scale * 100) + ' %';
      for (let i = 1; i <= pdf.numPages; i++) {
        const holder = document.createElement('div');
        holder.className = 'pdfv-pg';
        holder.dataset.page = String(i);
        holder.setAttribute('aria-label', 'Karta ' + (pageFrom + i - 1) + ', strona ' + i);
        holder.style.minHeight = Math.floor(base.height * scale) + 'px';
        pagesEl.appendChild(holder);
        holders.push(holder);
      }
      io = new IntersectionObserver(function (entries) {
        entries.forEach(function (en) {
          if (en.isIntersecting) {
            const i = parseInt(en.target.dataset.page, 10);
            render(i, false);
            if (en.intersectionRatio > 0.4) { setCurrent(i); }
          }
        });
      }, { root: scroll, threshold: [0.1, 0.5] });
      holders.forEach(function (h) { io.observe(h); });
      goTo(1);
    }

    // przełącznik dokumentów pisma (kilka skanów przy jednym wpływie): chipy z data-src; aktywny ten, którego adres jest otwarty
    const docs = root.querySelectorAll('.pdfv-doc[data-src]');
    function markActive(src) {
      docs.forEach(function (d) {
        const on = d.dataset.src === src;
        d.classList.toggle('on', on);
        d.setAttribute('aria-selected', on ? 'true' : 'false');
      });
    }
    docs.forEach(function (d) {
      d.addEventListener('click', function () {
        if (d.dataset.src === root.dataset.src) { return; }
        markActive(d.dataset.src);
        open(d.dataset.src, d.dataset.download);
      });
    });
    markActive(root.dataset.src);

    if (window.ResizeObserver) {
      let ostatnia = scroll.clientWidth;
      new ResizeObserver(function () {
        if (autoFit && pdf && Math.abs(scroll.clientWidth - ostatnia) > 8) { ostatnia = scroll.clientWidth; setScale(fitScale()); }
      }).observe(scroll);
    }

    root.addEventListener('click', function (ev) {
      const btn = ev.target.closest('[data-act]');
      if (!btn) { return; }
      switch (btn.dataset.act) {
        case 'zoom-in': autoFit = false; setScale(scale + SCALE_STEP); break;
        case 'zoom-out': autoFit = false; setScale(scale - SCALE_STEP); break;
        case 'fit': autoFit = true; setScale(fitScale()); break;
        case 'prev': goTo(current - 1); break;
        case 'next': goTo(current + 1); break;
        case 'print': window.open(root.dataset.src, '_blank', 'noopener'); break;
        default: break;
      }
    });
    root.querySelector('.pdfv-num').addEventListener('change', function (ev) { goTo(parseInt(ev.target.value, 10) || 1); });
    root.querySelector('.pdfv-q').addEventListener('keydown', function (ev) {
      if (ev.key === 'Enter') { search(ev.target.value.trim()); }
      if (ev.key === 'Escape') { ev.target.value = ''; search(''); scroll.focus(); }
    });
    scroll.addEventListener('keydown', function (ev) {
      if (ev.target.tagName === 'INPUT') { return; }
      if (ev.key === 'j' || ev.key === 'J') { goTo(current + 1); ev.preventDefault(); }
      if (ev.key === 'k' || ev.key === 'K') { goTo(current - 1); ev.preventDefault(); }
      if (ev.key === '+' || ev.key === '=') { autoFit = false; setScale(scale + SCALE_STEP); ev.preventDefault(); }
      if (ev.key === '-') { autoFit = false; setScale(scale - SCALE_STEP); ev.preventDefault(); }
      if (ev.key === '/') { root.querySelector('.pdfv-q').focus(); ev.preventDefault(); }
    });
    if (root.dataset.canHighlight === 'true') {
      pagesEl.addEventListener('dblclick', function (ev) {
        const holder = ev.target.closest('.pdfv-pg');
        if (!holder) { return; }
        const rect = holder.getBoundingClientRect();
        const detail = { page: parseInt(holder.dataset.page, 10), xPx: ev.clientX - rect.left, yPx: ev.clientY - rect.top, scale: scale * 1.33 };
        root.dispatchEvent(new CustomEvent('eskarga:highlight', { detail: detail, bubbles: true }));
      });
    }
    await open(root.dataset.src, root.dataset.download);
  }

  function mountAll() { document.querySelectorAll('.pdfv').forEach(mount); }
  if (document.readyState === 'loading') { document.addEventListener('DOMContentLoaded', mountAll); } else { mountAll(); }
  // Widżet pojawia się też w treści podmienianej AJAX-em PrimeFaces (kolejka S-06 po wyborze pisma, dziennik S-04, akta S-14):
  // PrimeFaces wysyła pfAjaxComplete zdarzeniem jQuery (nie natywnym DOM), więc nasłuch przez jQuery, a niezależnie od niego
  // MutationObserver montuje każdy nowy .pdfv wstawiony do dokumentu.
  if (window.jQuery) { window.jQuery(document).on('pfAjaxComplete', mountAll); }
  if (window.MutationObserver && document.body) {
    new MutationObserver(function (muts) {
      for (let m = 0; m < muts.length; m++) {
        const added = muts[m].addedNodes;
        for (let n = 0; n < added.length; n++) {
          const node = added[n];
          if (node.nodeType === 1 && (node.matches('.pdfv') || node.querySelector('.pdfv'))) { mountAll(); return; }
        }
      }
    }).observe(document.body, { childList: true, subtree: true });
  }
})();
