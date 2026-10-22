/**
 * Dezente Scroll-Animationen: Einblenden per IntersectionObserver und ein
 * leichter Parallax-Effekt im Hero. Läuft nur, wenn <html> die Klasse
 * `motion` trägt (siehe Inline-Skript im Layout) – bei
 * „prefers-reduced-motion: reduce“ passiert hier nichts.
 */
const root = document.documentElement;
const reduced = window.matchMedia('(prefers-reduced-motion: reduce)');

function initReveal() {
  const targets = document.querySelectorAll<HTMLElement>('[data-reveal]:not(.is-visible), [data-draw]:not(.is-visible)');
  if (!targets.length) return;

  // Pfadlängen für die Linienzeichnung vorbereiten
  document.querySelectorAll<SVGPathElement>('[data-draw] path').forEach((p) => {
    p.style.setProperty('--len', String(Math.ceil(p.getTotalLength())));
  });

  const io = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue;
        entry.target.classList.add('is-visible');
        io.unobserve(entry.target);
      }
    },
    // Etwas vor dem eigentlichen Sichtbarwerden starten, damit nichts „nachlädt“
    { rootMargin: '0px 0px -8% 0px', threshold: 0.08 },
  );
  targets.forEach((el) => io.observe(el));

  // Sprung per Anker (z. B. Speisekarten-Navigation): alles bis zum Ziel sofort zeigen,
  // damit beim Lesen nichts mehr einblendet.
  window.addEventListener('hashchange', revealUpToHash);
  revealUpToHash();
}

function revealUpToHash() {
  const id = decodeURIComponent(location.hash.slice(1));
  const target = id && document.getElementById(id);
  if (!target) return;
  target.querySelectorAll('[data-reveal]').forEach((el) => el.classList.add('is-visible'));
  target.closest('[data-reveal]')?.classList.add('is-visible');
}

function initParallax() {
  const layers = [...document.querySelectorAll<HTMLElement>('[data-parallax]')];
  if (!layers.length) return;
  let ticking = false;
  // Nur rechnen, solange der Hero im Bild ist
  const hero = layers[0].closest('section');
  let limit = Infinity;
  const measure = () => (limit = hero ? hero.offsetTop + hero.offsetHeight : Infinity);
  measure();
  window.addEventListener('resize', measure, { passive: true });

  const update = () => {
    ticking = false;
    if (!root.classList.contains('motion')) return;
    const y = Math.min(window.scrollY, limit);
    for (const el of layers) {
      const speed = Number(el.dataset.parallax) || 0.15;
      el.style.transform = `translate3d(0, ${(y * speed).toFixed(1)}px, 0)`;
    }
  };
  window.addEventListener(
    'scroll',
    () => {
      if (!ticking) {
        ticking = true;
        requestAnimationFrame(update);
      }
    },
    { passive: true },
  );
  update();
}

function disable() {
  root.classList.remove('motion');
  document.querySelectorAll<HTMLElement>('[data-parallax]').forEach((el) => (el.style.transform = ''));
}

if (root.classList.contains('motion')) {
  initReveal();
  initParallax();
  root.classList.add('motion-ready');
  // Wird die Einstellung während des Besuchs geändert, sofort reagieren.
  reduced.addEventListener('change', (e) => e.matches && disable());
}
