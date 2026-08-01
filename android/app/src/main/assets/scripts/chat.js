// Hides/shows the game's chat box in every panel. Finds the message field by placeholder (pt/en)
// or, language-independently, a text field in the bottom half that isn't the login field.
(() => {
  const achaChat = () => {
    const marc = document.querySelector('[data-pgchat]');
    if (marc) return marc;
    let inp = [...document.querySelectorAll('input, textarea')].find(i => /Falar em|Talk|Message|Chat/i.test(i.placeholder || ''));
    if (!inp) inp = [...document.querySelectorAll('input[type=text], input:not([type]), textarea')]
      .find(i => i.autocomplete !== 'username' && !/password/.test(i.autocomplete || '')
        && i.getBoundingClientRect().width > 0 && i.getBoundingClientRect().top > innerHeight * 0.5);
    if (!inp) return null;
    let el = inp;
    for (let k = 0; k < 12 && el; k++) {
      const cs = getComputedStyle(el), r = el.getBoundingClientRect();
      const posic = cs.position === 'absolute' || cs.position === 'fixed';
      if (posic && r.width >= 150 && r.width <= 820 && r.height >= 110 && r.height <= 720) return el;
      el = el.parentElement;
    }
    return null;
  };
  const esconder = __HIDE__;
  const aplica = () => { const c = achaChat(); if (c) { c.dataset.pgchat = '1'; c.style.display = esconder ? 'none' : ''; } };
  if (window.__chatObs) { window.__chatObs.disconnect(); window.__chatObs = null; }
  aplica();
  if (esconder) { window.__chatObs = new MutationObserver(aplica); window.__chatObs.observe(document.documentElement, { childList: true, subtree: true }); }
})();
