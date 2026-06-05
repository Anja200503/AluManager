'use strict';

/* ─── URL API InfinityFree ───────────────────────────────────
   Remplace par ton vrai domaine InfinityFree
   Ex: https://monsite.infinityfreeapp.com/save_commande.php
─────────────────────────────────────────────────────────── */
const CMD_API_URL     = 'https://alu.xo.je/save_commande.php';
const CMD_GET_URL     = 'https://alu.xo.je/get_commandes.php';
const CMD_DELETE_URL  = 'https://alu.xo.je/delete_commande.php';
const CMD_DETAIL_URL  = 'https://alu.xo.je/commande.html';
const CMD_ZAVATRA_URL = 'https://alu.xo.je/zavatra.html';

/* ─── AUTO-CHARGEMENT liste commandes ─────────────────────── */
(function() {
  const _orig = window.navigateTo;
  window.navigateTo = function(pageId) {
    _orig && _orig(pageId);
    if (pageId === 'messages') lstCharger();
    // Bouton ⚙️ prix : visible uniquement sur page "ajoute commande"
    const btn = document.getElementById('headerPrixBtn');
    if (btn) btn.style.display = pageId === 'favoris' ? 'flex' : 'none';
  };
})();

/* ════════════════════════════════════════════════════════════
   LISTE COMMANDES
════════════════════════════════════════════════════════════ */
let lstCommandes = [];

async function lstCharger() {
  const wrap = cq('lstWrap');
  wrap.innerHTML = `<div class="lst-loading"><div class="lst-spinner"></div>Chargement...</div>`;
  try {
    const res  = await fetch(CMD_GET_URL + '?t=' + Date.now());
    const data = await res.json();
    if (!data.success) throw new Error(data.error || 'Erreur serveur');
    lstCommandes = data.commandes || [];
    lstAfficherStats(data.stats || {});
    lstFiltrer();
  } catch(e) {
    wrap.innerHTML = `<div class="lst-empty"><div class="lst-empty-icon">⚠️</div>
      <h3>Erreur de chargement</h3><p>${e.message}</p></div>`;
  }
}

function lstAfficherStats(s) {
  cset('lstStatNb',  s.nb   || 0);
  cset('lstStatCA',  lstFmt(parseFloat(s.ca||0)) + ' Ar');
  cset('lstStatAv',  lstFmt(parseFloat(s.av||0)) + ' Ar');
  cset('lstStatRe',  lstFmt(parseFloat(s.re||0)) + ' Ar');
}

function lstFiltrer() {
  const q = (cq('lstSearch')?.value || '').toLowerCase().trim();
  const list = q ? lstCommandes.filter(c =>
    (c.client_nom||'').toLowerCase().includes(q) ||
    (c.reference ||'').toLowerCase().includes(q) ||
    (c.client_lieu||'').toLowerCase().includes(q)
  ) : lstCommandes;
  lstRendre(list);
}

function lstRendre(list) {
  const wrap = cq('lstWrap');
  if (!list.length) {
    wrap.innerHTML = `<div class="lst-empty">
      <div class="lst-empty-icon">📋</div>
      <h3>Aucune commande</h3>
      <p>Créez votre première commande dans l'onglet "Nouvelle commande"</p>
    </div>`; return;
  }
  wrap.innerHTML = list.map(c => lstCardHTML(c)).join('');
}

function lstCardHTML(c) {
  const total   = lstFmt(parseFloat(c.total_ar||0));
  const avance  = parseFloat(c.avance_ar||0);
  const reste   = parseFloat(c.reste_ar||0);
  const dc      = lstFmtDate(c.date_commande);
  const dl      = lstFmtDate(c.date_livraison);
  const np      = c.nb_produits || (c.produits||[]).length || 0;
  const badgeReste = reste <= 0
    ? `<span class="lst-meta-badge green">✅ Soldé</span>`
    : `<span class="lst-meta-badge orange">⏳ Reste: ${lstFmt(reste)} Ar</span>`;
  return `
  <div class="lst-card" id="lstCard_${c.id}">
    <div class="lst-card-header">
      <span class="lst-card-ref">🔖 ${esc(c.reference)}</span>
      <span class="lst-card-status">${esc(c.statut||'confirmée')}</span>
    </div>
    <div class="lst-card-body">
      <div class="lst-card-client">👤 ${esc(c.client_nom)}</div>
      <div class="lst-card-lieu">📍 ${esc(c.client_lieu)}</div>
      <div class="lst-card-meta">
        <span class="lst-meta-badge">📅 ${dc}</span>
        <span class="lst-meta-badge">🚚 ${dl}</span>
        <span class="lst-meta-badge">📦 ${np} produit(s)</span>
        <span class="lst-meta-badge">💰 ${total} Ar</span>
        ${badgeReste}
      </div>
    </div>
    <div class="lst-card-footer">
      <button class="lst-btn lst-btn-del"
        onclick="lstSupprimer(${c.id},'${esc(c.reference)}')">🗑️</button>
      <button class="lst-btn lst-btn-zavatra"
        onclick="lstOuvrirZavatra(${c.id})">🔧 Zavatra</button>
      <button class="lst-btn lst-btn-detail"
        onclick="lstOuvrirDetail(${c.id})">📄 Détail</button>
    </div>
  </div>`;
}

function lstOuvrirDetail(id) {
  window.location.href = CMD_DETAIL_URL + '?id=' + id;
}
function lstOuvrirZavatra(id) {
  window.location.href = CMD_ZAVATRA_URL + '?id=' + id;
}


let _lstPendingDel = null;

function lstSupprimer(id, ref) {
  _lstPendingDel = { id, ref };
  const refEl = cq('lstConfirmRef');
  if (refEl) refEl.textContent = ref;
  const btn = cq('lstConfirmDelBtn');
  if (btn) { btn.onclick = lstConfirmDel; }
  cq('lstConfirmOverlay').classList.add('active');
}

function lstConfirmClose() {
  cq('lstConfirmOverlay').classList.remove('active');
  _lstPendingDel = null;
}

async function lstConfirmDel() {
  if (!_lstPendingDel) return;
  const { id, ref } = _lstPendingDel;
  lstConfirmClose();
  try {
    const res  = await fetch(CMD_DELETE_URL, {
      method: 'POST', headers: {'Content-Type':'application/json'},
      body: JSON.stringify({id})
    });
    const data = await res.json();
    if (!data.success) throw new Error(data.error);
    lstCommandes = lstCommandes.filter(c => parseInt(c.id) !== parseInt(id));
    const card = cq('lstCard_' + id);
    if (card) { card.style.cssText='transition:all .25s;opacity:0;transform:scale(0.95)'; setTimeout(()=>lstFiltrer(),260); }
    cmdToast('success', 'Commande supprimée');
  } catch(e) { cmdToast('error', 'Erreur: ' + e.message); }
}

function lstFmt(v) { return Math.round(v).toLocaleString('fr-MG'); }
function lstFmtDate(d) {
  if (!d) return '—';
  const [y,m,j] = (d+'').split('-');
  return `${j}/${m}/${y}`;
}
function esc(s) { return (s||'').toString().replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'); }

/* ─── SCHEMAS ─────────────────────────────────────────────── */
const SCHEMAS = {
  fenetre:     { cols:['h','l','qty','pu','tot'], minW:230 },
  lavarangana: { cols:['l','qty','tot'],          minW:150 },
  porte:       { cols:['h','l','qty','pu','tot'], minW:230 },
  rideau:      { cols:['h','l','qty','pu','tot'], minW:230 },
  vitrine:     { cols:['h','l','p','qty','pu','tot'], minW:280 },
};
const COL_LABELS = {
  h:'H cm', l:'L cm', p:'P cm',
  qty:'Qté', pu:'Prix U.', tot:'Total',
};

/* ─── CATALOGUE ───────────────────────────────────────────── */
const PRIX_DEFAUTS = {
  fenetre: {
    coulissante_promotion: 70000,
    coulissante_deuxieme:  85000,
    coulissante_premier:  100000,
    ouvrante:   90000,
    projetant:  95000,
    naco:       80000,
    fixe:       65000,
  },
  lavarangana: 45000,
  porte:       120000,
  rideau:      95000,
  vitrine:     150000,
};
// Surplus vitrages coulissante (défaut = 0)
const SURP_KEYS   = ['antelio_4mm', 'antelio_5mm', 'clair_5mm'];
const SURP_DEFAUT = { antelio_4mm: 0, antelio_5mm: 0, clair_5mm: 0 };

const CATALOGUE = {
  fenetre: {
    id:'fenetre', label:'Fenêtre', colorClass:'fenetre', basePrice:85000,
    basePrices: {
      coulissante_promotion: 70000,
      coulissante_deuxieme:  85000,
      coulissante_premier:  100000,
      ouvrante:   90000,
      projetant:  95000,
      naco:       80000,
      fixe:       65000,
    },
    surcharges: { antelio_4mm: 0, antelio_5mm: 0, clair_5mm: 0 },
    minSurface: { coulissante_promotion: false, coulissante_deuxieme: false, coulissante_premier: false }
  },
  lavarangana: { id:'lavarangana', label:'Lavarangana Inox',  colorClass:'lavarangana', basePrice:45000  },
  porte:       { id:'porte',       label:'Porte',             colorClass:'porte',       basePrice:120000 },
  rideau:      { id:'rideau',      label:'Rideau Métallique', colorClass:'rideau',      basePrice:95000  },
  vitrine:     { id:'vitrine',     label:'Vitrine Alu',       colorClass:'vitrine',     basePrice:150000 },
};

/* ─── HELPER PRIX FENÊTRE ─────────────────────────────────── */
function cmdGetPrixFenetre(prod) {
  const raw  = prod.configRaw || {};
  const type = raw.typeFenetre || '';
  const bp   = CATALOGUE.fenetre.basePrices;
  const surp = CATALOGUE.fenetre.surcharges;

  if (type !== 'coulissante') return bp[type] || bp.coulissante_deuxieme;

  const sous  = raw.sousType || 'deuxieme';
  let price   = bp['coulissante_' + sous] || bp.coulissante_deuxieme;

  // Surplus selon vitre + épaisseur
  const vitre = raw.typeVitre || '';
  const ep    = raw.epaisseur || '';
  if      (vitre === 'antelio' && ep === '4mm') price += surp.antelio_4mm || 0;
  else if (vitre === 'antelio' && ep === '5mm') price += surp.antelio_5mm || 0;
  else if (vitre === 'clair'   && ep === '5mm') price += surp.clair_5mm   || 0;

  return price;
}

/* ─── SURFACE EFFECTIVE (min 1m² coulissante) ─────────────── */
function cmdGetEffectiveArea(prod, dim) {
  if (!dim.h || !dim.l) return 0;
  const area = (dim.h / 100) * (dim.l / 100);
  if (prod.productId === 'fenetre') {
    const raw = prod.configRaw || {};
    if (raw.typeFenetre === 'coulissante') {
      const sous = raw.sousType || 'deuxieme';
      if (CATALOGUE.fenetre.minSurface['coulissante_' + sous] && area < 1) return 1;
    }
  }
  return area;
}

/* ─── TOGGLE MIN 1m² COULISSANTE ──────────────────────────── */
function cmdToggleMinSurf(key, checked) {
  CATALOGUE.fenetre.minSurface[key] = checked;
  try {
    const saved = JSON.parse(localStorage.getItem('cmdPrices') || '{}');
    saved['minsurf_' + key] = checked;
    localStorage.setItem('cmdPrices', JSON.stringify(saved));
  } catch(e) {}
  cmdRenderCommande();
  cmdUpdateStats();
}

/* ─── PARAMÈTRES PRIX ─────────────────────────────────────── */
function cmdLoadPrix() {
  try {
    const saved = JSON.parse(localStorage.getItem('cmdPrices') || '{}');
    // Fenêtre — prix de base par type
    Object.keys(PRIX_DEFAUTS.fenetre).forEach(key => {
      const k = 'fenetre_' + key;
      if (saved[k] > 0) CATALOGUE.fenetre.basePrices[key] = saved[k];
    });
    // Fenêtre — surplus vitrage
    SURP_KEYS.forEach(key => {
      const k = 'fenetre_surp_' + key;
      if (saved[k] >= 0) CATALOGUE.fenetre.surcharges[key] = saved[k];
    });
    // Fenêtre — minimum 1m² coulissante
    ['coulissante_promotion','coulissante_deuxieme','coulissante_premier'].forEach(key => {
      if (saved['minsurf_' + key] !== undefined)
        CATALOGUE.fenetre.minSurface[key] = !!saved['minsurf_' + key];
    });
    CATALOGUE.fenetre.basePrice = CATALOGUE.fenetre.basePrices.coulissante_deuxieme;
    // Autres produits
    ['lavarangana','porte','rideau','vitrine'].forEach(id => {
      if (saved[id] > 0) CATALOGUE[id].basePrice = saved[id];
    });
  } catch(e) {}
}

function cmdOpenPrix() {
  // Prix de base fenêtre
  Object.keys(PRIX_DEFAUTS.fenetre).forEach(key => {
    const inp = cq('prix_fenetre_' + key);
    if (inp) inp.value = CATALOGUE.fenetre.basePrices[key];
  });
  // Surplus vitrage coulissante
  SURP_KEYS.forEach(key => {
    const inp = cq('prix_fenetre_surp_' + key);
    if (inp) inp.value = CATALOGUE.fenetre.surcharges[key];
  });
  // État switches min 1m²
  ['coulissante_promotion','coulissante_deuxieme','coulissante_premier'].forEach(key => {
    const chk = cq('minSurf_' + key);
    if (chk) chk.checked = !!CATALOGUE.fenetre.minSurface[key];
  });
  // Autres produits
  ['lavarangana','porte','rideau','vitrine'].forEach(id => {
    const inp = cq('prix_' + id);
    if (inp) inp.value = CATALOGUE[id].basePrice;
  });
  cq('cmdPrixOverlay').classList.add('active');
}

function cmdClosePrix() {
  cq('cmdPrixOverlay').classList.remove('active');
}

function cmdSavePrix() {
  const saved = {};
  let changed = false;
  // Fenêtre — prix de base
  Object.keys(PRIX_DEFAUTS.fenetre).forEach(key => {
    const inp = cq('prix_fenetre_' + key);
    const val = parseFloat(inp?.value) || PRIX_DEFAUTS.fenetre[key];
    if (val !== CATALOGUE.fenetre.basePrices[key]) changed = true;
    CATALOGUE.fenetre.basePrices[key] = val;
    saved['fenetre_' + key] = val;
  });
  // Fenêtre — surplus vitrage
  SURP_KEYS.forEach(key => {
    const inp = cq('prix_fenetre_surp_' + key);
    const val = Math.max(0, parseFloat(inp?.value) || 0);
    if (val !== CATALOGUE.fenetre.surcharges[key]) changed = true;
    CATALOGUE.fenetre.surcharges[key] = val;
    saved['fenetre_surp_' + key] = val;
  });
  CATALOGUE.fenetre.basePrice = CATALOGUE.fenetre.basePrices.coulissante_deuxieme;
  // Autres produits
  ['lavarangana','porte','rideau','vitrine'].forEach(id => {
    const inp = cq('prix_' + id);
    const val = parseFloat(inp?.value) || PRIX_DEFAUTS[id];
    if (val !== CATALOGUE[id].basePrice) changed = true;
    CATALOGUE[id].basePrice = val;
    saved[id] = val;
  });
  localStorage.setItem('cmdPrices', JSON.stringify(saved));
  cmdClosePrix();
  if (changed) { cmdRenderCommande(); cmdUpdateStats(); cmdToast('success', 'Prix mis à jour ✓'); }
}

function cmdResetPrix() {
  Object.keys(PRIX_DEFAUTS.fenetre).forEach(key => {
    const inp = cq('prix_fenetre_' + key);
    if (inp) inp.value = PRIX_DEFAUTS.fenetre[key];
  });
  SURP_KEYS.forEach(key => {
    const inp = cq('prix_fenetre_surp_' + key);
    if (inp) inp.value = SURP_DEFAUT[key];
  });
  ['lavarangana','porte','rideau','vitrine'].forEach(id => {
    const inp = cq('prix_' + id);
    if (inp) inp.value = PRIX_DEFAUTS[id];
  });
}

/* ─── STEPS ───────────────────────────────────────────────── */
const STEPS = {
  fenetre: [
    { key:'typeFenetre', title:'Type de fenêtre', type:'options',
      opts:[{v:'coulissante',l:'Coulissante',d:'Glisse sur rails'},{v:'ouvrante',l:'Ouvrante',d:'Battante classique'},
            {v:'projetant',l:'Projetant',d:'Ouvre vers ext.'},{v:'naco',l:'Naco',d:'Lames jalousies'},{v:'fixe',l:'Fixe',d:'Non ouvrant'}]},
    { key:'sousType', title:'Qualité / Sous-type', type:'options', cond:{key:'typeFenetre',val:'coulissante'},
      opts:[{v:'promotion',l:'Promotion',d:'Entrée de gamme'},{v:'deuxieme',l:'2ème Choix',d:'Standard'},{v:'premier',l:'1er Choix',d:'Supérieur'}]},
    { key:'couleur', title:'Couleur du profil', type:'colors',
      opts:[{v:'blanc',l:'Blanc',c:'#F5F5F5',b:'#ccc'},{v:'noir',l:'Noir',c:'#1a1a1a'},
            {v:'marron',l:'Marron',c:'#6B3A2A'},{v:'gris',l:'Gris',c:'#808080'},{v:'fauxbois',l:'Faux Bois',c:'#8B6914'}]},
    { key:'typeVitre', title:'Type de vitrage', type:'options',
      opts:[{v:'antelio',l:'Antelio',d:'Réfléchissant'},{v:'clair',l:'Clair',d:'Transparent'},
            {v:'fume',l:'Fumé',d:'Teinté sombre'},{v:'cathedrale',l:'Cathédrale',d:'Dépoli déco'}]},
    { key:'epaisseur', title:'Épaisseur du vitrage', type:'options',
      opts:[{v:'4mm',l:'4 mm',d:'Standard'},{v:'5mm',l:'5 mm',d:'Intermédiaire'},{v:'6mm',l:'6 mm',d:'Renforcé'}]}
  ],
  lavarangana: [
    { key:'typeLava', title:'Type de lavarangana', type:'options',
      opts:[{v:'tsotra',l:'Inox Tsotra',d:'Simple / lisse'},{v:'misyvato',l:'Inox Misy Vato',d:'Avec pierres déco'}]},
    { key:'couleurPierre', title:'Couleur des pierres', type:'options', cond:{key:'typeLava',val:'misyvato'},
      opts:[{v:'mena',l:'Mena',d:'Rouge'},{v:'maintso',l:'Maintso',d:'Vert'},{v:'mavo',l:'Mavo',d:'Jaune'},{v:'fotsy',l:'Fotsy',d:'Blanc'}]},
    { key:'nbPierres', title:'Pierres par mètre', type:'options', cond:{key:'typeLava',val:'misyvato'},
      opts:[{v:'1',l:'1 / m',d:'Minimal'},{v:'2',l:'2 / m',d:'Classique'},{v:'3',l:'3 / m',d:'Dense'},{v:'4',l:'4 / m',d:'Très dense'}]}
  ],
  porte: [
    { key:'typePorte', title:'Type de porte', type:'options',
      opts:[{v:'coulissante',l:'Coulissante',d:'Glisse sur rail'},{v:'ouvrante',l:'Ouvrante',d:'Battante classique'}]},
    { key:'sousTypePorte', title:'Qualité', type:'options', cond:{key:'typePorte',val:'coulissante'},
      opts:[{v:'promotion',l:'Promotion',d:'Entrée de gamme'},{v:'premier',l:'1er Choix',d:'Supérieur'},{v:'deuxieme',l:'2ème Choix',d:'Standard'}]},
    { key:'ventaux', title:'Nombre de vantaux', type:'options',
      opts:[{v:'1',l:'1 vantail',d:'Simple'},{v:'2',l:'2 vantaux',d:'Double'},{v:'3',l:'3 vantaux',d:'Triple'},{v:'4',l:'4 vantaux',d:'Quadruple'}]},
    { key:'forme', title:'Forme', type:'options', cond:{key:'typePorte',val:'ouvrante'},
      opts:[{v:'plein_vitre',l:'Plein Vitré',d:'Tout vitrage'},{v:'demi_vitre',l:'Demi-Vitré',d:'Mi-vitre'},{v:'plein_bard',l:'Plein Bardage',d:'Sans vitrage'}]},
    { key:'typeVitre', title:'Type de vitrage', type:'options', cond:{key:'forme',notVal:'plein_bard'},
      opts:[{v:'fume',l:'Fumé',d:'Teinté'},{v:'antelio',l:'Antelio',d:'Réfléchissant'},{v:'cathedrale',l:'Cathédrale',d:'Dépoli'},{v:'clair',l:'Clair',d:'Transparent'}]},
    { key:'couleur', title:'Couleur du profil', type:'colors',
      opts:[{v:'blanc',l:'Blanc',c:'#F5F5F5',b:'#ccc'},{v:'noir',l:'Noir',c:'#1a1a1a'},
            {v:'marron',l:'Marron',c:'#6B3A2A'},{v:'gris',l:'Gris',c:'#808080'},{v:'fauxbois',l:'Faux Bois',c:'#8B6914'}]}
  ],
  rideau: [
    { key:'pose', title:'Type de pose', type:'options',
      opts:[{v:'interieure',l:'Intérieure',d:'Montage intérieur'},{v:'exterieure',l:'Extérieure',d:'Montage extérieur'}]},
    { key:'couleur', title:'Couleur', type:'colors',
      opts:[{v:'marron',l:'Marron',c:'#6B3A2A'},{v:'blanc',l:'Blanc',c:'#F5F5F5',b:'#ccc'},
            {v:'noir',l:'Noir',c:'#1a1a1a'},{v:'brut',l:'Tsy Miloko',c:'#b0b0b0'}]}
  ],
  vitrine: [
    { key:'etageres', title:"Nombre d'étagères", type:'options',
      opts:[{v:'1',l:'1 étagère',d:'Minimal'},{v:'2',l:'2 étagères',d:'Compact'},
            {v:'3',l:'3 étagères',d:'Standard'},{v:'4',l:'4 étagères',d:'Large'},{v:'5',l:'5 étagères',d:'Maximum'}]},
    { key:'couleur', title:'Couleur du cadre', type:'colors',
      opts:[{v:'marron',l:'Marron',c:'#6B3A2A'},{v:'blanc',l:'Blanc',c:'#F5F5F5',b:'#ccc'},{v:'noir',l:'Noir',c:'#1a1a1a'}]},
    { key:'typeVitrine', title:'Configuration', type:'options',
      opts:[{v:'avec_porte',l:'Misy Varavarana',d:'Avec porte'},{v:'sans_porte',l:'Tsy Misy Varavarana',d:'Sans porte'}]}
  ]
};

/* ─── ÉTAT ────────────────────────────────────────────────── */
let CMS = { productId:null, step:-1, raw:{}, lbl:{} };
let commande = [];

/* ─── UTILS ───────────────────────────────────────────────── */
function cq(id) { return document.getElementById(id); }
function cset(id, val) { const e = cq(id); if (e) e.textContent = val; }
function cfmt(v) { return Math.round(v).toLocaleString('fr-MG'); }

function cmdToast(type, msg) {
  const t = cq('cmdToast');
  cq('cmdToastMsg').textContent = msg;
  t.className = 'cmd-toast ' + type + ' show';
  setTimeout(() => t.classList.remove('show'), 2800);
}

/* ─── SKIP LOGIC ──────────────────────────────────────────── */
function cIsVisible(sd, raw) {
  if (!sd.cond) return true;
  const c = sd.cond, cur = raw[c.key];
  if (c.val    !== undefined) return cur === c.val;
  if (c.notVal !== undefined) return cur !== undefined && cur !== c.notVal;
  return true;
}
function cVisIdx(pid, raw) { return STEPS[pid].reduce((a,sd,i) => { if (cIsVisible(sd,raw)) a.push(i); return a; }, []); }
function cNextVis(pid, from, raw) { for (let i=from+1; i<STEPS[pid].length; i++) if (cIsVisible(STEPS[pid][i],raw)) return i; return -1; }
function cPrevVis(pid, from, raw) { for (let i=from-1; i>=0; i--) if (cIsVisible(STEPS[pid][i],raw)) return i; return -1; }

/* ─── MODAL PRODUIT ───────────────────────────────────────── */
function cmdOpenModal() {
  CMS = { productId:null, step:-1, raw:{}, lbl:{} };
  cq('cmdModalOverlay').classList.add('active');
  const _mb = cq('cmdModalBox'); if (_mb) _mb.scrollTop = 0;
  cmdRenderStep0();
}
function cmdCloseModal() {
  cq('cmdModalOverlay').classList.remove('active');
  CMS = { productId:null, step:-1, raw:{}, lbl:{} };
}

function cmdRenderStep0() {
  cq('cmdModalTitle').textContent    = 'Nouveau produit';
  cq('cmdModalSubtitle').textContent = 'Sélectionnez le type de produit';
  cq('cmdStepIndicator').innerHTML   = `<div class="cmd-step-item"><div class="cmd-step-dot active">1</div><span class="cmd-step-label active">Produit</span></div>`;
  cq('cmdBtnPrev').style.visibility  = 'hidden';
  cq('cmdBtnConfirm').style.display  = 'none';
  cq('cmdModalBody').innerHTML = `
    <div class="cmd-step-title">Choisissez le type de produit</div>
    <div class="cmd-options-grid">
      ${Object.values(CATALOGUE).map(p => `
        <div class="cmd-option-card" onclick="cmdPickProduct('${p.id}',this)">
          <div class="cmd-option-name">${p.label}</div>
        </div>`).join('')}
    </div>`;
}

function cmdPickProduct(id, el) {
  document.querySelectorAll('#cmdModalBody .cmd-option-card').forEach(c => c.classList.remove('selected'));
  el.classList.add('selected');
  setTimeout(() => { CMS.productId=id; CMS.raw={}; CMS.lbl={}; CMS.step=cVisIdx(id,{})[0]??0; cmdRenderCurrent(); }, 200);
}

function cmdRenderCurrent() {
  const { productId:pid, step, raw, lbl } = CMS;
  const sd = STEPS[pid][step], prod = CATALOGUE[pid];
  const vis = cVisIdx(pid, raw), pos = vis.indexOf(step);
  const last = cNextVis(pid, step, raw) === -1;

  cq('cmdModalTitle').textContent    = prod.label;
  cq('cmdModalSubtitle').textContent = `Étape ${pos+1} / ${vis.length}`;
  cmdRenderIndicator(vis, step);
  cq('cmdBtnPrev').style.visibility  = 'visible';
  // En mode édition le bouton "Confirmer" est toujours visible
  cq('cmdBtnConfirm').style.display  = (last || CMS._editId) ? 'block' : 'none';
  cq('cmdBtnConfirm').disabled       = !raw[sd.key];
  cq('cmdBtnConfirm').textContent    = CMS._editId ? '✓ Mettre à jour' : 'Ajouter à la commande';

  const tags = Object.values(lbl).map(v => `<span class="cmd-preview-tag">${v}</span>`).join('');
  let content = '';
  if (sd.type === 'options') {
    content = `<div class="cmd-options-grid">${sd.opts.map(o => `
      <div class="cmd-option-card ${raw[sd.key]===o.v?'selected':''}"
        onclick="cmdPickOption('${sd.key}','${o.v}','${o.l}',this)">
        <div class="cmd-option-name">${o.l}</div>
        <div class="cmd-option-desc">${o.d||''}</div>
      </div>`).join('')}</div>`;
  } else {
    content = `<div class="cmd-color-options">${sd.opts.map(o => `
      <div class="cmd-color-btn ${raw[sd.key]===o.v?'selected':''}"
        onclick="cmdPickColor('${sd.key}','${o.v}','${o.l}',this)">
        <div class="cmd-color-circle" style="background:${o.c};${o.b?'border-color:'+o.b:''}"></div>
        <span class="cmd-color-name">${o.l}</span>
      </div>`).join('')}</div>`;
  }
  cq('cmdModalBody').innerHTML = `
    <div class="cmd-step-title">${sd.title}</div>${content}
    ${tags ? `<div class="cmd-selection-summary"><div class="cmd-preview-title">Sélections</div><div class="cmd-preview-tags">${tags}</div></div>` : ''}`;
}

function cmdRenderIndicator(vis, cur) {
  const prod = CATALOGUE[CMS.productId], steps = STEPS[CMS.productId];
  let h = `<div class="cmd-step-item"><div class="cmd-step-dot done">✓</div><span class="cmd-step-label done">${prod.label}</span></div>`;
  vis.forEach((si, i) => {
    const done = si<cur, act = si===cur, cls = done?'done':act?'active':'';
    h += `<div class="cmd-step-line ${done?'done':''}"></div>
    <div class="cmd-step-item"><div class="cmd-step-dot ${cls}">${done?'✓':i+1}</div>
    <span class="cmd-step-label ${cls}">${steps[si].title.slice(0,12)}</span></div>`;
  });
  cq('cmdStepIndicator').innerHTML = h;
}

function cmdAfterPick(key, value, label) {
  CMS.raw[key] = value; CMS.lbl[key] = label;
  STEPS[CMS.productId].forEach(sd => { if (sd.cond && sd.cond.key===key) { delete CMS.raw[sd.key]; delete CMS.lbl[sd.key]; } });
  const nxt = cNextVis(CMS.productId, CMS.step, CMS.raw);
  if (nxt === -1) { cq('cmdBtnConfirm').disabled=false; cq('cmdBtnConfirm').style.display='block'; }
  else { setTimeout(() => { CMS.step=nxt; cmdRenderCurrent(); }, 250); }
}
function cmdPickOption(key, value, label, el) {
  document.querySelectorAll('#cmdModalBody .cmd-option-card').forEach(c => c.classList.remove('selected'));
  el.classList.add('selected'); cmdAfterPick(key, value, label);
}
function cmdPickColor(key, value, label, el) {
  document.querySelectorAll('#cmdModalBody .cmd-color-btn').forEach(c => c.classList.remove('selected'));
  el.classList.add('selected'); cmdAfterPick(key, value, label);
}
function cmdGoPrev() {
  // En mode édition : on navigue normalement, mais si plus d'étape précédente → ferme
  if (CMS._editId) {
    const prev = cPrevVis(CMS.productId, CMS.step, CMS.raw);
    if (prev === -1) { cmdCloseModal(); }
    else { CMS.step = prev; cmdRenderCurrent(); }
    return;
  }
  if (CMS.step <= 0) { CMS = {productId:null,step:-1,raw:{},lbl:{}}; cmdRenderStep0(); return; }
  const prev = cPrevVis(CMS.productId, CMS.step, CMS.raw);
  if (prev === -1) { CMS = {productId:null,step:-1,raw:{},lbl:{}}; cmdRenderStep0(); }
  else { CMS.step = prev; cmdRenderCurrent(); }
}
function cmdConfirmProduct() {
  // ── Mode édition : on met à jour le produit existant ──
  if (CMS._editId) {
    const prod = commande.find(p => p.id === CMS._editId);
    if (prod) {
      prod.config    = { ...CMS.lbl };
      prod.configRaw = { ...CMS.raw };
      // Réinitialise les prix unitaires manuels (les dimensions restent intactes)
      prod.dimensions.forEach(d => { d._pu = null; });
    }
    cmdCloseModal();
    cmdRenderCommande();
    cmdUpdateStats();
    cmdToast('success', 'Détails mis à jour ✓');
    return;
  }
  // ── Mode création ──
  const prod = CATALOGUE[CMS.productId];
  const item = {
    id:'p'+Date.now(), productId:CMS.productId, label:prod.label,
    colorClass:prod.colorClass, basePrice:prod.basePrice,
    config:{...CMS.lbl}, configRaw:{...CMS.raw}, dimensions:[]
  };
  commande.push(item);
  cmdCloseModal(); cmdRenderCommande(); cmdUpdateStats();
  cmdToast('success', prod.label + ' ajouté');
  setTimeout(() => cmdAddDimRow(item.id), 150);
}

/* ─── MODAL CLIENT ────────────────────────────────────────── */
function cmdOpenClientModal() {
  if (!commande.length) { cmdToast('error', 'Commande vide'); return; }
  const total = cmdGrandTotal();
  cq('cmdClientTotalAffiche').textContent = cfmt(total) + ' Ar';
  cq('cmdClientTotal').value = cfmt(total) + ' Ar';
  const today = new Date().toISOString().split('T')[0];
  if (!cq('cmdClientDate').value) cq('cmdClientDate').value = today;
  cq('cmdClientAvance').value = '';
  cq('cmdResteVal').textContent = '—';
  cq('cmdResteDisplay').className = 'cmd-reste-display';
  cmdCheckClientForm();
  cq('cmdClientOverlay').classList.add('active');
  const _cb = cq('cmdClientBox'); if (_cb) _cb.scrollTop = 0;
  setTimeout(() => { const n = cq('cmdClientNom'); if (n) n.focus(); }, 300);
}
function cmdCloseClientModal() { cq('cmdClientOverlay').classList.remove('active'); }

function cmdCalcReste() {
  const total = cmdGrandTotal(), avance = parseFloat(cq('cmdClientAvance').value)||0, reste = total - avance;
  const disp = cq('cmdResteDisplay'), valEl = cq('cmdResteVal');
  if (cq('cmdClientAvance').value === '') { valEl.textContent='—'; disp.className='cmd-reste-display'; }
  else if (reste <= 0) { valEl.textContent='0 Ar'; disp.className='cmd-reste-display cmd-reste-zero'; }
  else { valEl.textContent=cfmt(reste)+' Ar'; disp.className='cmd-reste-display cmd-reste-ok'; }
  cmdCheckClientForm();
}
function cmdCheckClientForm() {
  const ok = cq('cmdClientNom').value.trim() && cq('cmdClientLieu').value.trim() && cq('cmdClientDate').value && cq('cmdClientDateLiv').value;
  cq('cmdBtnConfirmerCommande').disabled = !ok;
}

/* ─── ENREGISTREMENT → InfinityFree ──────────────────────── */
async function cmdConfirmerCommande() {
  const nom     = cq('cmdClientNom').value.trim();
  const lieu    = cq('cmdClientLieu').value.trim();
  const date    = cq('cmdClientDate').value;
  const dateLiv = cq('cmdClientDateLiv').value;
  const total   = cmdGrandTotal();
  const avance  = parseFloat(cq('cmdClientAvance').value) || 0;
  const reste   = Math.max(0, total - avance);

  const payload = {
    client_nom: nom, client_lieu: lieu,
    date_commande: date, date_livraison: dateLiv,
    total, avance, reste,
    produits: commande.map(p => ({
      productId: p.productId, label: p.label, colorClass: p.colorClass,
      basePrice: p.basePrice, config: p.config,
      dimensions: p.dimensions.map(d => ({ id:d.id, h:d.h, l:d.l, p:d.p, qty:d.qty||1 }))
    }))
  };

  cmdCloseClientModal();
  cq('cmdLoadingOverlay').classList.add('active');

  try {
    const resp   = await fetch(CMD_API_URL, { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(payload) });
    const result = await resp.json();
    cq('cmdLoadingOverlay').classList.remove('active');

    if (result.success) {
      cq('cmdSuccessRef').textContent = result.reference;
      cq('cmdSuccessDetails').innerHTML = `
        <div class="cmd-confirm-row"><span class="cmd-confirm-row-lbl">👤 Client</span><span class="cmd-confirm-row-val">${nom}</span></div>
        <div class="cmd-confirm-row"><span class="cmd-confirm-row-lbl">📍 Lieu</span><span class="cmd-confirm-row-val">${lieu}</span></div>
        <div class="cmd-confirm-row"><span class="cmd-confirm-row-lbl">📅 Commande</span><span class="cmd-confirm-row-val">${cmdFmtDate(date)}</span></div>
        <div class="cmd-confirm-row"><span class="cmd-confirm-row-lbl">🚚 Livraison</span><span class="cmd-confirm-row-val">${cmdFmtDate(dateLiv)}</span></div>
        <div class="cmd-confirm-row"><span class="cmd-confirm-row-lbl">📦 Produits</span><span class="cmd-confirm-row-val">${commande.length}</span></div>
        <div class="cmd-confirm-row"><span class="cmd-confirm-row-lbl">💰 Total</span><span class="cmd-confirm-row-val">${cfmt(total)} Ar</span></div>
        <div class="cmd-confirm-row"><span class="cmd-confirm-row-lbl">✅ Avance</span><span class="cmd-confirm-row-val green">${cfmt(avance)} Ar</span></div>
        <div class="cmd-confirm-row"><span class="cmd-confirm-row-lbl">⏳ Reste</span><span class="cmd-confirm-row-val">${cfmt(reste)} Ar</span></div>`;
      cq('cmdSuccessOverlay').classList.add('active');
      cmdToast('success', 'Commande enregistrée !');
    } else {
      cmdToast('error', 'Erreur: ' + (result.error || 'Inconnue'));
      cq('cmdClientOverlay').classList.add('active');
    }
  } catch (err) {
    cq('cmdLoadingOverlay').classList.remove('active');
    cmdToast('error', 'Erreur réseau: ' + err.message);
    cq('cmdClientOverlay').classList.add('active');
  }
}

function cmdCloseSuccessModal() { cq('cmdSuccessOverlay').classList.remove('active'); }
function cmdNouvelleCommande() {
  cmdCloseSuccessModal(); commande = []; cmdRenderCommande(); cmdUpdateStats();
  ['cmdClientNom','cmdClientLieu','cmdClientDate','cmdClientDateLiv','cmdClientAvance'].forEach(id => { cq(id).value=''; });
  cmdToast('success', 'Prêt pour une nouvelle commande');
}
function cmdFmtDate(d) {
  if (!d) return '—';
  const [y,m,j] = d.split('-');
  return `${j}/${m}/${y}`;
}

/* ─── COMMANDE / CARDS ────────────────────────────────────── */
function cmdRenderCommande() {
  const list = cq('cmdProductsList'), empty = cq('cmdEmptyState'), sum = cq('cmdSummaryBar');
  if (!commande.length) {
    list.innerHTML = ''; empty.style.display = 'block';
    list.appendChild(empty); sum.style.display = 'none'; return;
  }
  empty.style.display = 'none'; list.innerHTML = '';
  commande.forEach((p,i) => {
    list.appendChild(cmdMakeCard(p, i+1));          // card dans le DOM
    p.dimensions.forEach(d => cmdAppendRow(p, d));  // tbody trouvable maintenant
  });
  list.appendChild(empty); sum.style.display = 'flex';
}

function cmdMakeCard(prod, num) {
  const schema = SCHEMAS[prod.productId], cols = schema.cols, minW = schema.minW;
  const div = document.createElement('div');
  div.className = 'cmd-product-card'; div.id = 'cmdCard_' + prod.id;
  const total = cmdCalcTotal(prod);
  const tags  = Object.entries(prod.config).map(([key, val]) =>
    `<span class="cmd-card-tag cmd-tag-editable" onclick="cmdEditDetail('${prod.id}','${key}')" title="Modifier">${val}</span>`
  ).join('');
  const thCells = [...cols.map(c => `<th>${COL_LABELS[c]}</th>`), '<th></th>'].join('');
  div.innerHTML = `
    <div class="cmd-card-header ${prod.colorClass}">
      <div class="cmd-card-header-left">
        <div class="cmd-card-num">${num}</div>
        <div style="overflow:hidden">
          <div class="cmd-card-name">${prod.label}</div>
          <div class="cmd-card-tags">${tags}</div>
        </div>
      </div>
      <div class="cmd-card-header-right">
        <div class="cmd-total-badge" id="cmdTbadge_${prod.id}">${cfmt(total)} Ar</div>
        <button class="cmd-btn-del-card" onclick="cmdDelCard('${prod.id}')">✕</button>
      </div>
    </div>
    <div class="cmd-card-body">
      <div class="cmd-table-wrapper">
        <table class="cmd-dim-table" style="min-width:${minW}px">
          <thead><tr>${thCells}</tr></thead>
          <tbody id="cmdTbody_${prod.id}"></tbody>
        </table>
      </div>
    </div>
    <div class="cmd-card-footer">
      <button class="cmd-btn-add-row" onclick="cmdAddDimRow('${prod.id}')">+ Ajouter dimension</button>
      <div class="cmd-subtotal-label">Sous-total : <strong id="cmdSub_${prod.id}">${cfmt(total)} Ar</strong></div>
    </div>`;
  // ⚠️ NE PAS appeler cmdAppendRow ici : div n'est pas encore dans le DOM.
  // Les dimensions sont rendues dans cmdRenderCommande(), après appendChild().
  return div;
}

/* ─── ÉDITION D'UN DÉTAIL D'UN PRODUIT EXISTANT ──────────── */
function cmdEditDetail(prodId, stepKey) {
  const prod = commande.find(p => p.id === prodId);
  if (!prod) return;
  const steps = STEPS[prod.productId];
  const stepIdx = steps.findIndex(sd => sd.key === stepKey);
  if (stepIdx === -1) return;
  // Ouvre la modal en mode édition : on repart des valeurs actuelles
  CMS = {
    productId: prod.productId,
    step: stepIdx,
    raw: { ...prod.configRaw },
    lbl: { ...prod.config },
    _editId: prod.id,          // indique qu'on édite, pas qu'on crée
  };
  cq('cmdModalOverlay').classList.add('active');
  const _mb = cq('cmdModalBox'); if (_mb) _mb.scrollTop = 0;
  cmdRenderCurrent();
}

/* ─── DIMENSIONS ──────────────────────────────────────────── */
function cmdAddDimRow(prodId) {
  const prod = commande.find(p => p.id===prodId); if (!prod) return;
  const dim = { id:'d'+Date.now()+'_'+Math.random().toString(36).substr(2,4), h:'',l:'',p:'',qty:1, _pu:null };
  prod.dimensions.push(dim);
  cmdAppendRow(prod, dim); cmdRefreshCard(prodId); cmdUpdateStats();
}
/* Redimensionne un input selon sa valeur */
function cmdAutoSize(inp) {
  const val = inp.value || '';
  const isPu = inp.classList.contains('cmd-input-pu');
  if (isPu) {
    // PU en texte formaté : caractères plus fins (chiffres + espaces + "Ar")
    const len = val ? val.length : Math.min((inp.placeholder||'').length, 5);
    inp.style.width = Math.max(54, len * 7 + 10) + 'px';
  } else {
    const len = val ? val.length : Math.min((inp.placeholder||'').length, 2);
    inp.style.width = Math.max(34, len * 9 + 14) + 'px';
  }
}

/* Formate un prix en "84 000 Ar" */
function cmdFmtPu(val) {
  if (!val && val !== 0) return '';
  return Math.round(val).toLocaleString('fr-MG') + ' Ar';
}

function cmdAppendRow(prod, dim) {
  const tbody = cq('cmdTbody_' + prod.id); if (!tbody) return;
  const cols = SCHEMAS[prod.productId].cols;
  const tr = document.createElement('tr'); tr.id = 'cmdDim_' + dim.id;
  let cells = '';
  cols.forEach(col => {
    if (col==='h')
      cells += `<td><input type="number" class="cmd-input-dim" placeholder="H" value="${dim.h||''}" min="1" inputmode="numeric" oninput="cmdChgDim('${prod.id}','${dim.id}','h',this.value);cmdAutoSize(this)"></td>`;
    else if (col==='l')
      cells += `<td><input type="number" class="cmd-input-dim" placeholder="L" value="${dim.l||''}" min="1" inputmode="numeric" oninput="cmdChgDim('${prod.id}','${dim.id}','l',this.value);cmdAutoSize(this)"></td>`;
    else if (col==='p')
      cells += `<td><input type="number" class="cmd-input-dim" placeholder="P" value="${dim.p||''}" min="1" inputmode="numeric" oninput="cmdChgDim('${prod.id}','${dim.id}','p',this.value);cmdAutoSize(this)"></td>`;
    else if (col==='qty')
      cells += `<td><input type="number" class="cmd-input-dim cmd-input-qty" placeholder="1" value="${dim.qty||1}" min="1" inputmode="numeric" oninput="cmdChgDim('${prod.id}','${dim.id}','qty',this.value);cmdAutoSize(this)"></td>`;
    else if (col==='pu') {
      const pu = cmdGetDimPu(prod, dim);
      const disp = pu > 0 ? cmdFmtPu(pu) : '';
      cells += `<td class="cmd-cell-pu-td"><input type="text" class="cmd-input-dim cmd-input-pu" id="cmdPu_${dim.id}" value="${disp}" inputmode="numeric" placeholder="— Ar" onfocus="cmdPuFocus(this)" onblur="cmdPuBlur(this,'${prod.id}','${dim.id}')" oninput="cmdChgPuText('${prod.id}','${dim.id}',this.value);cmdAutoSize(this)"></td>`;
    }
    else if (col==='tot')
      cells += `<td class="cmd-cell-tot" id="cmdPt_${dim.id}">${cmdCalcRowTotal(prod,dim)}</td>`;
  });
  cells += `<td><button class="cmd-btn-del-row" onclick="cmdDelRow('${prod.id}','${dim.id}')">✕</button></td>`;
  tr.innerHTML = cells;
  tbody.appendChild(tr);
  // Auto-taille initiale (number + text pour PU)
  tr.querySelectorAll('input').forEach(i => cmdAutoSize(i));
  const inp = tr.querySelector('input');
  if (inp) setTimeout(() => inp.focus(), 80);
}

/* ─── CALCULS ─────────────────────────────────────────────── */
function cmdGetPrix(prod) {
  return prod.productId === 'fenetre' ? cmdGetPrixFenetre(prod) : prod.basePrice;
}

// Prix unitaire calculé automatiquement (nombre brut)
function cmdCalcUnitPriceNum(prod, dim) {
  const pid = prod.productId, price = cmdGetPrix(prod);
  if (pid==='lavarangana') return dim.l ? (dim.l/100)*price : 0;
  if (pid==='vitrine') return (dim.h&&dim.l&&dim.p) ? (dim.h/100)*(dim.l/100)*(dim.p/100)*price : 0;
  return cmdGetEffectiveArea(prod, dim) * price;
}

// Prix unitaire effectif : override manuel si présent, sinon auto
function cmdGetDimPu(prod, dim) {
  return (dim._pu != null && dim._pu >= 0) ? dim._pu : cmdCalcUnitPriceNum(prod, dim);
}

function cmdCalcRowTotal(prod, dim) {
  const pu = cmdGetDimPu(prod, dim), qty = dim.qty || 1;
  return pu > 0 ? cfmt(pu * qty) + ' Ar' : '—';
}
function cmdCalcTotal(prod) {
  return prod.dimensions.reduce((s, d) => s + cmdGetDimPu(prod, d) * (d.qty||1), 0);
}

// Modification d'une dimension (h, l, p, qty)
function cmdChgDim(prodId, dimId, field, val) {
  const prod = commande.find(p => p.id===prodId);
  const dim  = prod && prod.dimensions.find(d => d.id===dimId); if (!dim) return;
  dim[field] = parseFloat(val)||(field==='qty'?1:0);
  // Si la dimension (pas qty) change → recalcul auto du prix U.
  if (field !== 'qty') {
    dim._pu = null;
    const puInp = cq('cmdPu_' + dimId);
    if (puInp && document.activeElement !== puInp) {
      const n = cmdCalcUnitPriceNum(prod, dim);
      puInp.value = n > 0 ? cmdFmtPu(n) : '';
      cmdAutoSize(puInp);
    }
  }
  const totCell = cq('cmdPt_' + dimId); if (totCell) totCell.textContent = cmdCalcRowTotal(prod, dim);
  cmdRefreshCard(prodId); cmdUpdateStats();
}

// Focus PU : affiche le nombre brut pour la saisie
function cmdPuFocus(inp) {
  const raw = inp.value.replace(/\s/g,'').replace(/Ar$/i,'').trim();
  inp.value = raw;
  inp.select && inp.select();
  cmdAutoSize(inp);
}
// Blur PU : reformate en "84 000 Ar" et sauvegarde
function cmdPuBlur(inp, prodId, dimId) {
  const raw = inp.value.replace(/[^\d.]/g,'').trim();
  const n   = parseFloat(raw);
  const prod = commande.find(p => p.id===prodId);
  const dim  = prod && prod.dimensions.find(d => d.id===dimId);
  if (!dim) { inp.value = ''; return; }
  dim._pu = (isFinite(n) && n >= 0) ? n : null;
  const pu = cmdGetDimPu(prod, dim);
  inp.value = pu > 0 ? cmdFmtPu(pu) : '';
  const totCell = cq('cmdPt_' + dimId);
  if (totCell) totCell.textContent = cmdCalcRowTotal(prod, dim);
  cmdRefreshCard(prodId); cmdUpdateStats();
  cmdAutoSize(inp);
}
// Saisie en cours : calcul live (avant reformatage)
function cmdChgPuText(prodId, dimId, val) {
  const raw = val.replace(/[^\d.]/g,'');
  const n   = parseFloat(raw);
  const prod = commande.find(p => p.id===prodId);
  const dim  = prod && prod.dimensions.find(d => d.id===dimId); if (!dim) return;
  dim._pu = (isFinite(n) && n >= 0) ? n : null;
  const totCell = cq('cmdPt_' + dimId);
  if (totCell) totCell.textContent = cmdCalcRowTotal(prod, dim);
  cmdRefreshCard(prodId); cmdUpdateStats();
}
function cmdGrandTotal() { return commande.reduce((s,p) => s + cmdCalcTotal(p), 0); }
function cmdRefreshCard(prodId) {
  const prod = commande.find(p => p.id===prodId), total = prod ? cmdCalcTotal(prod) : 0;
  const b = cq('cmdTbadge_'+prodId), s = cq('cmdSub_'+prodId);
  if (b) b.textContent = cfmt(total) + ' Ar';
  if (s) s.textContent = cfmt(total) + ' Ar';
}
function cmdDelRow(prodId, dimId) {
  const prod = commande.find(p => p.id===prodId); if (!prod) return;
  prod.dimensions = prod.dimensions.filter(d => d.id !== dimId);
  const tr = cq('cmdDim_' + dimId);
  if (tr) { tr.style.cssText='transition:all .2s;opacity:0;'; setTimeout(() => { tr.remove(); cmdRenum(prodId); }, 220); }
  cmdRefreshCard(prodId); cmdUpdateStats(); cmdToast('warning', 'Ligne supprimée');
}
function cmdRenum(prodId) {
  const tb = cq('cmdTbody_' + prodId);
  if (tb) tb.querySelectorAll('tr').forEach((tr,i) => { const l=tr.querySelector('.cmd-row-label'); if(l) l.textContent='L'+(i+1); });
}
function cmdDelCard(prodId) {
  const prod = commande.find(p => p.id===prodId);
  if (!prod || !confirm('Supprimer "' + prod.label + '" ?')) return;
  commande = commande.filter(p => p.id !== prodId);
  const card = cq('cmdCard_' + prodId);
  if (card) { card.style.cssText='transition:all .25s;opacity:0;transform:scale(0.96)'; setTimeout(() => { cmdRenderCommande(); cmdUpdateStats(); }, 270); }
  cmdToast('error', 'Produit supprimé');
}

/* ─── STATS ───────────────────────────────────────────────── */
function cmdUpdateStats() {
  let lignes=0, qty=0, total=0;
  commande.forEach(p => {
    lignes += p.dimensions.length;
    p.dimensions.forEach(d => { const q = parseInt(d.qty)||1; qty += q; total += cmdGetDimPu(p, d) * q; });
  });
  cset('cmdHProduits', commande.length);
  cset('cmdHLignes',   lignes);
  cset('cmdHQty',      qty);
  cset('cmdHTotal',    cfmt(total));
  cset('cmdSTotal',    cfmt(total) + ' Ar');
  cset('cmdCountBadge', commande.length + ' article(s)');
}

/* ─── FERMETURE AU CLIC OVERLAY ──────────────────────────── */
document.addEventListener('DOMContentLoaded', () => {
  cmdLoadPrix();
  const mo = cq('cmdModalOverlay');
  const co = cq('cmdClientOverlay');
  const lc = cq('lstConfirmOverlay');
  const po = cq('cmdPrixOverlay');
  if (mo) mo.addEventListener('click', e => { if (e.target===mo) cmdCloseModal(); });
  if (co) co.addEventListener('click', e => { if (e.target===co) cmdCloseClientModal(); });
  if (lc) lc.addEventListener('click', e => { if (e.target===lc) lstConfirmClose(); });
  if (po) po.addEventListener('click', e => { if (e.target===po) cmdClosePrix(); });
  cmdUpdateStats();
});
