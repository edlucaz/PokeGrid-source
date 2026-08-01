// Throttles requestAnimationFrame: an idle game doesn't need 60fps, so this caps the game loop's
// paint rate to save battery/CPU. fps=0 disables throttling; fps=1 is "cards mode" (ultra light).
(() => {
  if (!window.__eco) {
    window.__eco = { native: window.requestAnimationFrame.bind(window), fps: 0, last: 0 };
    window.requestAnimationFrame = (cb) => {
      const s = window.__eco;
      if (!s.fps) return s.native(cb);
      const wait = Math.max(0, (1000 / s.fps) - (performance.now() - s.last));
      return setTimeout(() => { s.last = performance.now(); s.native(cb); }, wait);
    };
  }
  window.__eco.fps = __FPS__;
})();
