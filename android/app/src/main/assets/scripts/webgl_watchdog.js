// The game is alive but the canvas lost its WebGL context and didn't recover in 4s: reload.
(() => {
  if (window.__pgGlWd) return; window.__pgGlWd = 1;
  let tm;
  document.addEventListener('webglcontextlost', () => { clearTimeout(tm); tm = setTimeout(() => location.reload(), 4000); }, true);
  document.addEventListener('webglcontextrestored', () => clearTimeout(tm), true);
})();
