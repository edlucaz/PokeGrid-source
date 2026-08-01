// Scrolls the submit button into view so the captcha and "Entrar" are reachable on small
// screens. Runs at most twice: the 2nd pass covers the layout shift once the captcha loads.
(() => {
  if (window.__loginScroll) return; window.__loginScroll = true;
  const btn = () => [...document.querySelectorAll('button')].find(x => x.type === 'submit' || /auth-imgbtn/.test(x.className));
  let feitos = 0, ciclos = 0;
  const t = setInterval(() => {
    ciclos++;
    const b = btn();
    if (b) { b.scrollIntoView({ block: 'center' }); feitos++; }
    if (feitos >= 2 || ciclos > 16) clearInterval(t);
  }, 500);
})();
