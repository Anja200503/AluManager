'use strict';

const pageTitles = {
  home:       'Accueil',
  dashboard:  'Tableau de bord',
  profil:     'Profil',
  messages:   'Commande',
  favoris:    'Ajoute commande',
  parametres: 'Paramètres',
};

/* ════════════════════════════════════════════════════════════
   NAVIGATION AVEC HISTORIQUE NAVIGATEUR
   Fonctionnement :
   • navigateTo(id)  → affiche la page, pousse un état history
   • popstate        → ferme les calques ouverts OU navigue en arrière
   • headerGoBack()  → identique au bouton physique Retour
   • _restoreFromHash() → rappelé par firebase-auth après connexion
                          pour restaurer la bonne page depuis l'URL
════════════════════════════════════════════════════════════ */

let _curPage = 'home';
let _popMode = false;   // true pendant un popstate → évite le double pushState

/* ── Naviguer vers une page ──────────────────────────────── */
function navigateTo(pageId) {
  document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
  const target = document.getElementById('page-' + pageId);
  if (target) target.classList.add('active');

  document.getElementById('page-title').textContent = pageTitles[pageId] || '';
  closeAccountPanel();
  _curPage = pageId;

  // Met à jour le bouton retour du header
  _updateBackBtn(pageId);

  // Pousse dans l'historique sauf si on répond déjà à un popstate
  if (!_popMode) {
    history.pushState({ page: pageId }, '', '#' + pageId);
  }
}

/* ── Bouton retour header (visible/invisible) ────────────── */
function _updateBackBtn(pageId) {
  const btn = document.getElementById('headerBackBtn');
  if (!btn) return;
  if (pageId === 'home') {
    btn.classList.remove('visible');
  } else {
    btn.classList.add('visible');
  }
}

/* ── Bouton ← dans le header ────────────────────────────── */
function headerGoBack() {
  history.back(); // déclenche popstate → notre handler gère tout
}

/* ── Restauration de la page depuis le hash URL ─────────────
   Appelée par firebase-auth.js juste après l'affichage de l'app-shell.
   Permet de revenir sur la bonne page après un retour depuis
   commande.html / zavatra.html (ou tout autre lien externe).
──────────────────────────────────────────────────────────── */
window._restoreFromHash = function () {
  const hash = location.hash.slice(1);
  if (hash && pageTitles[hash]) {
    if (hash !== 'home') {
      _popMode = true;
      navigateTo(hash);
      _popMode = false;
    }
    // Remplace l'état avec les métadonnées correctes
    history.replaceState({ page: hash || 'home' }, '', '#' + (hash || 'home'));
  } else {
    history.replaceState({ page: 'home' }, '', '#home');
  }
};

/* ── Ferme le premier calque ouvert ──────────────────────── */
function _closeLayers() {
  // 1. Panneau compte
  const panel = document.getElementById('acct-panel');
  if (panel && panel.classList.contains('open')) {
    closeAccountPanel();
    return true;
  }
  // 2. Modaux commande (du plus récent au plus ancien)
  for (const id of ['cmdSuccessOverlay', 'cmdClientOverlay', 'cmdModalOverlay', 'cmdLoadingOverlay']) {
    const el = document.getElementById(id);
    if (el && el.classList.contains('active')) {
      el.classList.remove('active');
      return true;
    }
  }
  return false;
}

/* ── Gestion du bouton Retour (hardware + header) ───────────
   Ordre de priorité :
   1. Ferme les calques ouverts → reste sur la page
   2. Navigue vers la page précédente si l'état est valide
   3. Depuis l'accueil sans état app → laisse le navigateur quitter
──────────────────────────────────────────────────────────── */
window.addEventListener('popstate', function (e) {

  /* Priorité 1 : fermer les calques ouverts */
  if (_closeLayers()) {
    history.pushState({ page: _curPage }, '', '#' + _curPage);
    return;
  }

  const state = e.state;
  const page  = state && state.page && pageTitles[state.page] ? state.page : null;

  /* Priorité 2 : naviguer vers la page précédente */
  if (page) {
    _popMode = true;
    navigateTo(page);
    _popMode = false;
    return;
  }

  /* Priorité 3 : on a dépassé l'historique app */
  if (_curPage !== 'home') {
    // Sécurité : ramène à l'accueil, re-pousse pour intercepter le suivant
    _popMode = true;
    navigateTo('home');
    _popMode = false;
    history.pushState({ page: 'home' }, '', '#home');
  }
  // Si déjà sur home → laisse le navigateur fermer l'app (comportement natif)
});

/* ── Initialisation de l'historique ─────────────────────────
   Ancre l'état initial ; _restoreFromHash() sera rappelé
   par firebase-auth après confirmation de la session.
──────────────────────────────────────────────────────────── */
history.replaceState({ page: 'home' }, '', location.hash || '#home');


/* ════════════════════════════════════════════════════════════
   PANNEAU COMPTE
════════════════════════════════════════════════════════════ */
function toggleAccountPanel() {
  const panel   = document.getElementById('acct-panel');
  const overlay = document.getElementById('acct-overlay');
  const isOpen  = panel.classList.toggle('open');
  overlay.classList.toggle('active', isOpen);
}

function closeAccountPanel() {
  const panel   = document.getElementById('acct-panel');
  const overlay = document.getElementById('acct-overlay');
  if (panel)   panel.classList.remove('open');
  if (overlay) overlay.classList.remove('active');
}

/* Escape : ferme calques ou panneau compte */
document.addEventListener('keydown', e => {
  if (e.key === 'Escape') {
    if (!_closeLayers()) closeAccountPanel();
  }
});


/* ════════════════════════════════════════════════════════════
   DATE BANNIÈRE ACCUEIL
════════════════════════════════════════════════════════════ */
(function () {
  const el = document.getElementById('mgr-date');
  if (!el) return;
  const now    = new Date();
  const days   = ['Dimanche', 'Lundi', 'Mardi', 'Mercredi', 'Jeudi', 'Vendredi', 'Samedi'];
  const months = ['Jan', 'Fév', 'Mar', 'Avr', 'Mai', 'Juin', 'Juil', 'Aoû', 'Sep', 'Oct', 'Nov', 'Déc'];
  el.innerHTML = days[now.getDay()] + '<br>' +
    now.getDate() + ' ' + months[now.getMonth()] + ' ' + now.getFullYear();
})();
