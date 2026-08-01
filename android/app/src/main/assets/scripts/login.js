// Fills the login form and submits once both fields are correct. The captcha is always solved
// by the human; this only clicks "Entrar" when the turnstile token is present and the button is
// enabled. Ported from the Electron app's loginPanel(), with a fallback field-detection path
// (type=password / positional) for sites that don't set autocomplete=username/current-password.
(async () => {
  const setVal = (el, val) => {
    const st = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
    st.call(el, val);
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  };
  const findBtn = () => {
    const buttons = [...document.querySelectorAll('button')];
    return buttons.find(x => x.type === 'submit' || /auth-imgbtn/.test(x.className))
      || buttons.find(x => !x.disabled && /entrar|login|sign\s*in|log\s*in/i.test((x.textContent || '').trim()));
  };
  const findFields = () => {
    const inputs = [...document.querySelectorAll('input')];
    // Preferred: standard autocomplete hints (this is what poke.idleworld.online uses).
    let u = inputs.find(i => i.autocomplete === 'username');
    let p = inputs.find(i => i.autocomplete === 'current-password');
    // Fallback: type=password is a reliable, autocomplete-independent signal most sites still set.
    if (!p) p = inputs.find(i => i.type === 'password');
    if (!u && p) {
      const idx = inputs.indexOf(p);
      // The email/username field is normally the nearest text-ish input right before the
      // password field in DOM order.
      u = inputs.slice(0, idx).reverse().find(i => i.type === 'email' || i.type === 'text' || !i.type);
    }
    if (!u) u = inputs.find(i => i.type === 'email');
    return { u, p };
  };

  for (let t = 0; t < 40; t++) {
    const { u, p } = findFields();
    const b = findBtn();
    if (u && p && b) {
      const EMAIL = __EMAIL_JSON__, SENHA = __SENHA_JSON__;
      const preenche = () => {
        const { u: uu, p: pp } = findFields();
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
