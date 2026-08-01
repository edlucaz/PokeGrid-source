// Hides/shows the game's icon dock (<nav class="game-dock">) to reclaim screen space.
(() => {
  window.__dockHide = __HIDE__;
  const set = () => { const d = document.querySelector('nav.game-dock'); if (d) d.style.display = window.__dockHide ? 'none' : ''; };
  if (!window.__dockObs) { window.__dockObs = new MutationObserver(set); window.__dockObs.observe(document.documentElement, { childList: true, subtree: true }); }
  set();
})();
