// Closes the promo popup (<div class="promo-overlay"> with <button class="promo-close">) the
// moment it appears, so it doesn't block the panel unattended.
(() => {
  if (window.__promoKiller) return; window.__promoKiller = true;
  const kill = () => {
    const b = document.querySelector('button.promo-close');
    if (b) b.click();
    else { const o = document.querySelector('.promo-overlay'); if (o) o.remove(); }
  };
  new MutationObserver(kill).observe(document.documentElement, { childList: true, subtree: true });
  kill();
})();
