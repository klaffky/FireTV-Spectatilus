// Header: Hintergrund nach dem Scrollen + Mobilmenü
const header = document.querySelector<HTMLElement>('.site-header');

if (header?.dataset.overlay) {
  const onScroll = () => (header.dataset.scrolled = String(window.scrollY > 24));
  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();
}

const toggle = document.querySelector<HTMLButtonElement>('.nav-toggle');
const panel = document.getElementById('mobile-nav');

if (toggle && panel) {
  const setOpen = (open: boolean) => {
    toggle.setAttribute('aria-expanded', String(open));
    toggle.setAttribute('aria-label', (open ? toggle.dataset.labelClose : toggle.dataset.labelOpen) ?? '');
    panel.hidden = !open;
  };
  toggle.addEventListener('click', () => setOpen(toggle.getAttribute('aria-expanded') !== 'true'));
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && toggle.getAttribute('aria-expanded') === 'true') {
      setOpen(false);
      toggle.focus();
    }
  });
  panel.addEventListener('click', (e) => {
    if ((e.target as HTMLElement).closest('a')) setOpen(false);
  });
  window.matchMedia('(min-width: 48rem)').addEventListener('change', (e) => e.matches && setOpen(false));
}
