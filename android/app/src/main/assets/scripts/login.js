// Fills the login form and submits once both fields are correct. The captcha is always solved
// by the human; this only clicks "Entrar" when the turnstile token is present and the button is
// enabled. Ported from the Electron app's loginPanel().
(async () => {
  const setVal = (el, val) => {
    const st = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
    st.call(el, val);
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  };
  const findBtn = () => [...document.querySelectorAll('button')].find(x => x.type === 'submit' || /auth-imgbtn/.test(x.className));
  for (let t = 0; t < 40; t++) {
    const inputs = [...document.querySelectorAll('input')];
    const u = inputs.find(i => i.autocomplete === 'username');
    const p = inputs.find(i => i.autocomplete === 'current-password');
    const b = findBtn();
    if (u && p && b) {
      const EMAIL = __EMAIL_JSON__, SENHA = __SENHA_JSON__;
      const preenche = () => {
        const ii = [...document.querySelectorAll('input')];
        const uu = ii.find(i => i.autocomplete === 'username');
        const pp = ii.find(i => i.autocomplete === 'current-password');
        if (uu && uu.value !== EMAIL) setVal(uu, EMAIL);
        if (pp && pp.value !== SENHA) setVal(pp, SENHA);
        return !!(uu && pp && uu.value === EMAIL && pp.value === SENHA);
      };
      preenche();
      if (!window.__loginWatch) {
        window.__loginWatch = true;
        let ciclos = 0;
        const w = setInterval(() => {
          const bb = findBtn();
          if (!bb || ++ciclos > 1200) { clearInterval(w); window.__loginWatch = false; return; }
          const ok = preenche();
          const tk = document.querySelector('input[name=cf-turnstile-response]');
          if (ok && (!tk || tk.value) && !bb.disabled) { clearInterval(w); window.__loginWatch = false; bb.click(); }
        }, 500);
      }
      return 'preenchido';
    }
    await new Promise(r => setTimeout(r, 300));
  }
  return 'sem-form';
})();
